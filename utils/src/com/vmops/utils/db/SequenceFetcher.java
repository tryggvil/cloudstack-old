/**
 * Copyright (c) 2008, 2009, VMOps Inc.
 *
 * This code is Copyrighted and must not be reused, modified, or redistributed without the explicit consent of VMOps.
 */
package com.vmops.utils.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.persistence.TableGenerator;

import org.apache.log4j.Logger;

import com.vmops.utils.concurrency.NamedThreadFactory;

/**
 * Since Mysql does not have sequence support, we have
 * to use this to create that support because if the
 * table retrieval was inside a transaction, the value
 * gets locked until the transaction is over.
 * 
 * TODO: enhance this so that it actually follows the
 * allocation size.
 *
 */
public class SequenceFetcher {
    private final static Logger s_logger = Logger.getLogger(SequenceFetcher.class);
    ExecutorService _executors;
    
    private static final String SelectSql = "SELECT ? FROM ? WHERE ?=? FOR UPDATE";
    private static final String UpdateSql = "UPDATE ? SET ?=?+? WHERE ?=?";
    
    public <T> T getNextSequence(Class<T> clazz, TableGenerator tg) {
        return getNextSequence(clazz, tg, null);
    }
    
    public <T> T getNextSequence(Class<T> clazz, TableGenerator tg, Object key) {
        Future<T> future = _executors.submit(new Fetcher<T>(clazz, tg, key));
        try {
            return future.get();
        } catch (Exception e) {
            s_logger.warn("Unable to get sequeunce for " + tg.table() + ":" + tg.pkColumnValue(), e);
            return null;
        }
    }
    
    protected SequenceFetcher() {
        _executors = new ThreadPoolExecutor(25, 25, 120l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(250), new NamedThreadFactory("SequenceFetcher"));
    }

    protected static final SequenceFetcher s_instance = new SequenceFetcher();
    public static SequenceFetcher getInstance() {
        return s_instance;
    }
    
    protected class Fetcher<T> implements Callable<T> {
        TableGenerator _tg;
        Class<T> _clazz;
        Object _key;
        
        public Fetcher(Class<T> clazz, TableGenerator tg, Object key) {
            _tg = tg;
            _clazz = clazz;
            _key = key;
        }
        
        @Override @SuppressWarnings("unchecked")
        public T call() throws Exception {
            try {
                StringBuilder sql = new StringBuilder("SELECT ");
                sql.append(_tg.valueColumnName()).append(" FROM ").append(_tg.table());
                sql.append(" WHERE ").append(_tg.pkColumnName()).append(" = ?");
                
                Transaction txn = Transaction.open("Sequence");
                
                PreparedStatement selectStmt = txn.prepareStatement(sql.toString());
                if (_key == null) {
                    selectStmt.setString(1, _tg.pkColumnValue());
                } else {
                    selectStmt.setObject(1, _key);
                }

                sql = new StringBuilder("UPDATE ");
                sql.append(_tg.table()).append(" SET ").append(_tg.valueColumnName()).append("=").append(_tg.valueColumnName()).append("+?");
                sql.append(" WHERE ").append(_tg.pkColumnName()).append("=?");
                
                PreparedStatement updateStmt = txn.prepareStatement(sql.toString());
                updateStmt.setInt(1, _tg.allocationSize());
                if (_key == null) {
                    updateStmt.setString(2, _tg.pkColumnValue());
                } else {
                    updateStmt.setObject(2, _key);
                }
                
                ResultSet rs = null;
                try {
                    txn.start();
                    
                    rs = selectStmt.executeQuery();
                    Object obj = null;
                    while (rs.next()) {
                        if (_clazz.isAssignableFrom(Long.class)) {
                            obj = rs.getLong(1);
                        } else if (_clazz.isAssignableFrom(Integer.class)) {
                            obj = rs.getInt(1);
                        } else {
                            obj = rs.getObject(1);
                        }
                    }
                    
                    if (obj == null) {
                        s_logger.warn("Unable to get a sequence: " + updateStmt.toString());
                        return null;
                    }
                    
                    int rows = updateStmt.executeUpdate();
                    assert rows == 1 : "Come on....how exactly did we update this many rows " + rows + " for " + updateStmt.toString();
                    txn.commit();
                    return (T)obj;
                } finally {
                    if (rs != null) {
                        rs.close();
                    }
                    selectStmt.close();
                    updateStmt.close();
                    txn.close();
                }
            } catch (Exception e) {
                s_logger.warn("Caught this exception when running", e);
            }
            return null;
        }
    }
    
}
