/**
 *  Copyright (C) 2010 VMOps, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.  
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.vmops.utils.db;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp.ConnectionFactory;
import org.apache.commons.dbcp.DriverManagerConnectionFactory;
import org.apache.commons.dbcp.PoolableConnectionFactory;
import org.apache.commons.dbcp.PoolingDataSource;
import org.apache.commons.pool.KeyedObjectPoolFactory;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.commons.pool.impl.StackKeyedObjectPoolFactory;
import org.apache.log4j.Logger;

import com.vmops.utils.Pair;
import com.vmops.utils.PropertiesUtil;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 * Transaction abstracts away the Connection object in JDBC.  It allows the
 * following things that the Connection object does not.
 * 
 *   1. Transaction can be started at an entry point and whether the DB
 *      actions should be auto-commit or not determined at that point.
 *   2. DB Connection is allocated only when it is needed.
 *   3. Code does not need to know if a transaction has been started or not.
 *      It just starts/ends a transaction and we resolve it correctly with
 *      the previous actions.
 *
 * Note that this class is not synchronous.
 */
public class Transaction {
    private static final Logger s_logger = Logger.getLogger(Transaction.class.getName() + "." + "Transaction");
    private static final Logger s_stmtLogger = Logger.getLogger(Transaction.class.getName() + "." + "Statement");
    private static final Logger s_lockLogger = Logger.getLogger(Transaction.class.getName() + "." + "Lock");

    private static final ThreadLocal<Transaction> tls = new ThreadLocal<Transaction>();
    private static final String START_TXN = "start_txn";
    private static final String CURRENT_TXN = "current_txn";
    private static final String CREATE_TXN = "create_txn";
    private static final String CREATE_CONN = "create_conn";

    public static final short VMOPS_DB = 0;
    public static final short USAGE_DB = 1;

    private final LinkedList<StackElement> _stack;
    
    private final LinkedList<Pair<String, Long>> _lockTimes = new LinkedList<Pair<String, Long>>();

    private String _name;
    private Connection _conn;
    private boolean _txn;
    private final short _dbId;
    private long _txnTime;
    private Statement _stmt;
    private final Merovingian _lockMaster;
 
    public static Transaction currentTxn() {
        Transaction txn = tls.get();
        assert txn != null : "No Transaction on stack.  Did you mark the method with @DB?";
        assert checkAnnotation(3, txn) : "Did you even read the guide to use Transaction...IOW...other people's code? Try method can't be private.  What about @DB? hmmm... could that be it? " + txn.toString();
        return txn;
    }
    
    public static Transaction open(final short databaseId) {
        String name = buildName();
        if (name == null) {
            name = CURRENT_TXN;
        }
        return open(name, databaseId, true);
    }
    
    public static Transaction open(final String name) {
        return open(name, VMOPS_DB, false);
    }
    
    public static Transaction open(final String name, final short databaseId, final boolean forceDbChange) {
        Transaction txn = tls.get();
        if (txn == null) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Creating the transaction: " + name);
            }
            txn = new Transaction(name, false, databaseId);
            tls.set(txn);
        } else if (forceDbChange) {
            final short currentDbId = txn.getDatabaseId();
            if (currentDbId != databaseId) {
                // we need to end the current transaction and switch databases
                txn.close(txn.getName());

                txn = new Transaction(name, false, databaseId);
                tls.set(txn);
            }
        }

        txn.takeOver(name, false);

        return txn;
    }
    
    protected StackElement peekInStack(Object obj) {
        final Iterator<StackElement> it = _stack.iterator();
        while (it.hasNext()) {
        	StackElement next = it.next();
            if (next.type == obj) {
                return next;
            }
        }
        return null;
    }
    
    public void registerLock(String sql) {
    	if (_txn && s_lockLogger.isDebugEnabled()) {
	    	Pair<String, Long> time = new Pair<String, Long>(sql, System.currentTimeMillis());
	    	_lockTimes.add(time);
    	}
    }
    
    public static Connection getStandaloneConnection() {
    	try {
			return s_ds.getConnection();
		} catch (SQLException e) {
			s_logger.warn("Unexpected exception: ", e);
			return null;
		}
    }
    
    protected static boolean checkAnnotation(int stack, Transaction txn) {
        final StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
        StackElement se = txn.peekInStack(CURRENT_TXN);
        if (se == null) {
            return false;
        }
        for (; stack < stacks.length; stack++) {
            String methodName = stacks[stack].getMethodName();
            if (methodName.equals(se.ref)){
                return true;
            }
        }
        return false;
    }

    protected static String buildName() {
        if (s_logger.isDebugEnabled()) {
            final StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
            final StringBuilder str = new StringBuilder();
            for (int i = 3, max = stacks.length > 7 ? 7 : stacks.length; i < max; i++) {
                str.append("-").append(stacks[i].getClassName().substring(stacks[i].getClassName().lastIndexOf(".") + 1)).append(".").append(stacks[i].getMethodName()).append(":").append(stacks[i].getLineNumber());
            }
            return str.toString();
        }

        return "";
    }

    public Transaction(final String name, final boolean forLocking, final short databaseId) {
        _name = name;
        _conn = null;
        _stack = new LinkedList<StackElement>();
        _txn = false;
        _dbId = databaseId;
        _lockMaster = forLocking ? null : new Merovingian(_dbId);
    }

    public String getName() {
        return _name;
    }

    public Short getDatabaseId() {
        return _dbId;
    }

    @Override
    public String toString() {
        final StringBuilder str = new StringBuilder((_name != null ? _name : ""));
        int count = 0;
        str.append(" : ");
        for (final StackElement se : _stack) {
            if (se.type == CURRENT_TXN) {
                str.append(se.ref).append(", ");
            }
        }

        return str.toString();
    }

    protected void mark(final String name) {
        _stack.push(new StackElement(CURRENT_TXN, name));
    }

    public boolean lock(final String name, final int timeoutSeconds) {
    	assert (_lockMaster != null) : "Nah nah nah....you can't call lock if you are the lock!";
    		
    	return _lockMaster.acquire(name, timeoutSeconds);
    }

    public boolean release(final String name) {
    	assert (_lockMaster != null) : "Nah nah nah....you can't call lock if you are the lock!";
    	
    	return _lockMaster.release(name);
    }

    public void start() {
    	closePreviousStatement();
    	
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("txn: start requested by: " + buildName());
        }

        _stack.push(new StackElement(START_TXN, null));

        if (_txn) {
            s_logger.trace("txn: has already been started.");
            return;
        }

        _txn = true;
        _txnTime = System.currentTimeMillis();
        if (_conn != null) {
            try {
                s_logger.trace("txn: set auto commit to false");
                _conn.setAutoCommit(false);
            } catch (final SQLException e) {
                s_logger.warn("Unable to set auto commit: ", e);
                throw new VmopsRuntimeException("Unable to set auto commit: ", e);
            }
        }
    }
    
    protected void closePreviousStatement() {
    	if (_stmt != null) {
	        try {
	            if (s_stmtLogger.isTraceEnabled()) {
	                s_stmtLogger.trace("Closing: " + _stmt.toString());
	            }
	        	try {
	            	ResultSet rs = _stmt.getResultSet();
	            	if (rs != null && _stmt.getResultSetHoldability() != ResultSet.HOLD_CURSORS_OVER_COMMIT) {
	            		rs.close();
	            	}
	        	} catch(SQLException e) {
	        		s_stmtLogger.trace("Unable to close resultset");
	        	}
	            _stmt.close();
	        } catch (final SQLException e) {
	            s_stmtLogger.trace("Unable to close statement: " + _stmt.toString());
	        } finally {
	        	_stmt = null;
	        }
    	}
    }

    /**
     * Prepares an auto close statement.  The statement is closed automatically if it is
     * retrieved with this method.
     * 
     * @param sql sql String
     * @return PreparedStatement
     * @throws SQLException if problem with JDBC layer.
     * 
     * @see java.sql.Connection
     */
    public PreparedStatement prepareAutoCloseStatement(final String sql) throws SQLException {
    	PreparedStatement stmt = prepareStatement(sql);
    	_stmt = stmt;
    	return stmt;
    }
    
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        final Connection conn = getConnection();
        final PreparedStatement pstmt = conn.prepareStatement(sql);
        if (s_stmtLogger.isTraceEnabled()) {
        	s_stmtLogger.trace("Preparing: " + sql);
        }
        return pstmt;
    }

    /**
     * Prepares an auto close statement.  The statement is closed automatically if it is
     * retrieved with this method.
     * 
     * @param sql sql String
     * @param autoGeneratedKeys keys that are generated
     * @return PreparedStatement
     * @throws SQLException if problem with JDBC layer.
     * 
     * @see java.sql.Connection
     */
    public PreparedStatement prepareAutoCloseStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        final Connection conn = getConnection();
        final PreparedStatement pstmt = conn.prepareStatement(sql, autoGeneratedKeys);
        if (s_stmtLogger.isTraceEnabled()) {
        	s_stmtLogger.trace("Preparing: " + sql);
        }
        _stmt = pstmt;
        return pstmt;
    }

    /**
     * Prepares an auto close statement.  The statement is closed automatically if it is
     * retrieved with this method.
     * 
     * @param sql sql String
     * @param columnNames names of the columns
     * @return PreparedStatement
     * @throws SQLException if problem with JDBC layer.
     * 
     * @see java.sql.Connection
     */
    public PreparedStatement prepareAutoCloseStatement(final String sql, final String[] columnNames) throws SQLException {
        final Connection conn = getConnection();
        final PreparedStatement pstmt = conn.prepareStatement(sql, columnNames);
        if (s_stmtLogger.isTraceEnabled()) {
        	s_stmtLogger.trace("Preparing: " + sql);
        }
        _stmt = pstmt;
        return pstmt;
    }
    
    /**
     * Prepares an auto close statement.  The statement is closed automatically if it is
     * retrieved with this method.
     * 
     * @param sql sql String
     * @return PreparedStatement
     * @throws SQLException if problem with JDBC layer.
     * 
     * @see java.sql.Connection
     */
    public PreparedStatement prepareAutoCloseStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        final Connection conn = getConnection();
        final PreparedStatement pstmt = conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
        if (s_stmtLogger.isTraceEnabled()) {
        	s_stmtLogger.trace("Preparing: " + sql);
        }
        _stmt = pstmt;
        return pstmt;
    }

    /**
     * Returns the db connection.
     * 
     * Note: that you can call getConnection() but beaware that
     * all prepare statements from the Connection are not garbage
     * collected!
     * 
     * @return DB Connection but make sure you understand that
     *         you are responsible for closing the PreparedStatement.
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException {
    	closePreviousStatement();
        if (_conn == null) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("conn: Creating a DB connection with " + (_txn ? " txn: " : " no txn: ")  + buildName());
            }
            switch (_dbId) {
            case VMOPS_DB:
            	if(s_ds != null) {
            		_conn = s_ds.getConnection();
            	} else {
            		s_logger.warn("A static-initialized variable becomes null, process is dying?");
                    throw new VmopsRuntimeException("Database is not initialized, process is dying?");
            	}
                break;
            case USAGE_DB:
            	if(s_usageDS != null) {
            		_conn = s_usageDS.getConnection();
            	} else {
            		s_logger.warn("A static-initialized variable becomes null, process is dying?");
                    throw new VmopsRuntimeException("Database is not initialized, process is dying?");
            	}
                break;
            default:
                throw new VmopsRuntimeException("No database selected for the transaction");
            }
            _conn.setAutoCommit(!_txn);
            
            //
            // MySQL default transaction isolation level is REPEATABLE READ,
            // to reduce chances of DB deadlock, we will use READ COMMITED isolation level instead
            // see http://dev.mysql.com/doc/refman/5.0/en/innodb-deadlocks.html
            //
            _conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            _stack.push(new StackElement(CREATE_CONN, null));
        } else {
            s_logger.trace("conn: Using existing DB connection");
        }

        return _conn;
    }

    protected boolean takeOver(final String name, final boolean create) {
        if (_stack.size() != 0) {
            if (!create) {
                // If it is not a create transaction, then let's just use the current one.
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Using current transaction: " + toString());
                }
                mark(name);
                return false;
            }

            final StackElement se = _stack.getFirst();
            if (se.type == CREATE_TXN) {
                // This create is called inside of another create.  Which is ok?
                // We will let that create be responsible for cleaning up.
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Create using current transaction: " + toString());
                }
                mark(name);
                return false;
            }

            s_logger.warn("Encountered a transaction that has leaked.  Cleaning up. " + toString());
            cleanup();
        }

        if (s_logger.isTraceEnabled()) {
            s_logger.trace("Took over the transaction: " + name);
        }
        _stack.push(new StackElement(create ? CREATE_TXN : CURRENT_TXN, name));
        _name = name;
        return true;
    }

    public void cleanup() {
    	closePreviousStatement();
    	
        removeUpTo(null);
        if (_txn) {
            rollbackTransaction();
        }
        _txn = false;
        _name = null;

        closeConnection();
        
        _stack.clear();
        
        if (_lockMaster != null) {
        	_lockMaster.clear();
        }
    }

    public void close() {
        removeUpTo(CURRENT_TXN);
        
        if (_stack.size() == 0) {
            s_logger.trace("Transaction is done");
            cleanup();
        }
    }

    /**
     * close() is used by endTxn to close the connection.  This method only
     * closes the connection if the name is the same as what's stored.
     * 
     * @param name
     * @return true if this close actually closes the connection.  false if not.
     */
    protected boolean close(final String name) {
        if (_name == null) {    // Already cleaned up.
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Already cleaned up." + buildName());
            }
            return true;
        }

        if (!_name.equals(name)) {
            close();
            return false;
        }

        if (s_logger.isDebugEnabled() && _stack.size() > 2) {
            s_logger.debug("Transaction is not closed properly: " + toString() + ".  Called by " + buildName());
        }

        cleanup();

        s_logger.trace("All done");
        return true;
    }

    protected boolean hasTxnInStack() {
    	return peekInStack(START_TXN) != null;
    }
    
    protected void clearLockTimes() {
    	if (s_lockLogger.isDebugEnabled()) {
	    	for (Pair<String, Long> time : _lockTimes) {
	    		s_lockLogger.trace("SQL " + time.first() + " took " + (System.currentTimeMillis() - time.second()));
	    	}
	    	_lockTimes.clear();
    	}
    }

    public boolean commit() {
        if (!_txn) {
            s_logger.warn("txn: Commit called when it is not a transaction: " + buildName());
            return false;
        }

        removeUpTo(START_TXN);

        if (hasTxnInStack()) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("txn: Not committing because transaction started elsewhere: " + buildName() + " / " + toString());
            }
            return false;
        }

        _txn = false;
        try {
            if (_conn != null) {
                _conn.commit();
                s_logger.trace("txn: DB Changes committed. Time = " + (System.currentTimeMillis() - _txnTime));
                clearLockTimes();
                closeConnection();
            }
            return true;
        } catch (final SQLException e) {
            rollbackTransaction();
            throw new VmopsRuntimeException("Unable to commit or close the connection. ", e);
        }
    }

    protected void closeConnection() {
    	closePreviousStatement();
    	
        if (_conn == null) {
            return;
        }

        if (_txn) {
            s_logger.trace("txn: Not closing DB connection because we're still in a transaction.");
            return;
        }

        try {
            s_logger.trace("conn: Closing DB connection");
            _conn.close();
            _conn = null;
        } catch (final SQLException e) {
            s_logger.warn("Unable to close connection", e);
        }
    }

    protected void removeUpTo(final Object obj) {
        StackElement item;
        while (_stack.size() > 0 && (item = _stack.pop()).type != obj) {
            if (item.type == CURRENT_TXN) {
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("Releasing the current txn: " + (item.ref != null ? item.ref : ""));
                }
            } else if (item.type == CREATE_CONN) {
                closeConnection();
            } else if (item.type instanceof Savepoint) {
                if (_conn != null) {
                    try {
                        _conn.rollback((Savepoint)item);
                    } catch (final SQLException e) {
                        s_logger.warn("Unable to rollback Txn.", e);
                    }
                }
            } else if (item.type == START_TXN) {
                rollbackTransaction();
                closeConnection();
            } else if (item.type instanceof Statement) {
                try {
                    if (s_stmtLogger.isTraceEnabled()) {
                        s_stmtLogger.trace("Closing: " + item.toString());
                    }
                    Statement stmt = (Statement)item;
                	try {
                    	ResultSet rs = stmt.getResultSet();
                    	if (rs != null) {
                    		rs.close();
                    	}
                	} catch(SQLException e) {
                		s_stmtLogger.trace("Unable to close resultset");
                	}
                    stmt.close();
                } catch (final SQLException e) {
                    s_stmtLogger.trace("Unable to close statement: " + item.toString());
                }
            }
        }
    }

    protected void rollbackTransaction() {
    	closePreviousStatement();
        if (!_txn) {
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("Rollback called when there's no transaction: " + buildName());
            }
            return;
        }
        _txn = false;
        try {
            if (_conn != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Rolling back the transaction: Time = " + (System.currentTimeMillis() - _txnTime) + " Name =  "+ buildName());
                }
                _conn.rollback();
            }
            clearLockTimes();
            closeConnection();
        } catch(final SQLException e) {
            s_logger.warn("Unable to rollback", e);
        }
    }

    public void rollback() {
        rollbackTransaction();

        while (hasTxnInStack()) {
            removeUpTo(START_TXN);
        }
    }

    public Savepoint setSavepoint() throws SQLException {
        final Connection conn = getConnection();
        final Savepoint sp = conn.setSavepoint();
        _stack.push(new StackElement(sp, null));

        return sp;
    }

    public Savepoint setSavepoint(final String name) throws SQLException {
        final Connection conn = getConnection();
        final Savepoint sp = conn.setSavepoint(name);
        _stack.push(new StackElement(sp, null));

        return sp;
    }

    public void releaseSavepoint(final Savepoint sp) throws SQLException {
        if (_conn != null) {
            _conn.releaseSavepoint(sp);
        }
        removeUpTo(sp);
    }

    public void rollback(final Savepoint sp) {
        removeUpTo(sp);

        if (_conn != null) {
            try {
                _conn.rollback(sp);
            } catch (final SQLException e) {
                s_logger.warn("Unable to rollback Txn.", e);
            }
        }
    }
    
    protected Transaction() {
            _name = null;
            _conn = null;
            _stack = null;
            _txn = false;
            _dbId = -1;
            _lockMaster = null;
    }
    
    protected class StackElement {
        public Object type;
        public Object ref;
        
        public StackElement (Object type, Object ref) {
            this.type = type;
            this.ref = ref;
        }
    }
    
    private static DataSource s_ds;
    private static DataSource s_usageDS;
    static {
        try {
            final File dbPropsFile = PropertiesUtil.findConfigFile("db.properties");
            final Properties dbProps = new Properties();
            dbProps.load(new FileInputStream(dbPropsFile));

            // FIXME:  If params are missing...default them????
            final int vmopsMaxActive = Integer.parseInt(dbProps.getProperty("db.vmops.maxActive"));
            final int vmopsMaxIdle = Integer.parseInt(dbProps.getProperty("db.vmops.maxIdle"));
            final long vmopsMaxWait = Long.parseLong(dbProps.getProperty("db.vmops.maxWait"));
            final String vmopsUsername = dbProps.getProperty("db.vmops.username");
            final String vmopsPassword = dbProps.getProperty("db.vmops.password");
            final String vmopsHost = dbProps.getProperty("db.vmops.host");
            final int vmopsPort = Integer.parseInt(dbProps.getProperty("db.vmops.port"));
            final String vmopsDbName = dbProps.getProperty("db.vmops.name");
            final boolean vmopsAutoReconnect = Boolean.parseBoolean(dbProps.getProperty("db.vmops.autoReconnect"));
            final String vmopsValidationQuery = dbProps.getProperty("db.vmops.validationQuery");
            final boolean vmopsTestOnBorrow = Boolean.parseBoolean(dbProps.getProperty("db.vmops.testOnBorrow"));
            final boolean vmopsTestWhileIdle = Boolean.parseBoolean(dbProps.getProperty("db.vmops.testWhileIdle"));
            final long vmopsTimeBtwEvictionRunsMillis = Long.parseLong(dbProps.getProperty("db.vmops.timeBetweenEvictionRunsMillis"));
            final long vmopsMinEvcitableIdleTimeMillis = Long.parseLong(dbProps.getProperty("db.vmops.minEvictableIdleTimeMillis"));
            final boolean vmopsRemoveAbandoned = Boolean.parseBoolean(dbProps.getProperty("db.vmops.removeAbandoned"));
            final int vmopsRemoveAbandonedTimeout = Integer.parseInt(dbProps.getProperty("db.vmops.removeAbandonedTimeout"));
            final boolean vmopsLogAbandoned = Boolean.parseBoolean(dbProps.getProperty("db.vmops.logAbandoned"));
            final boolean vmopsPoolPreparedStatements = Boolean.parseBoolean(dbProps.getProperty("db.vmops.poolPreparedStatements"));
            final String url = dbProps.getProperty("db.vmops.url.params");

            final GenericObjectPool vmopsConnectionPool = new GenericObjectPool(null, vmopsMaxActive, GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION,
                    vmopsMaxWait, vmopsMaxIdle, vmopsTestOnBorrow, false, vmopsTimeBtwEvictionRunsMillis, 1, vmopsMinEvcitableIdleTimeMillis, vmopsTestWhileIdle);
            final ConnectionFactory vmopsConnectionFactory = new DriverManagerConnectionFactory("jdbc:mysql://"+vmopsHost + ":" + vmopsPort + "/" + vmopsDbName +
                    "?autoReconnect="+vmopsAutoReconnect + (url != null ? "&" + url : ""), vmopsUsername, vmopsPassword);
            final KeyedObjectPoolFactory poolableObjFactory = (vmopsPoolPreparedStatements ? new StackKeyedObjectPoolFactory() : null);
            final PoolableConnectionFactory vmopsPoolableConnectionFactory = new PoolableConnectionFactory(vmopsConnectionFactory, vmopsConnectionPool, poolableObjFactory,
                    vmopsValidationQuery, false, false);
            s_ds = new PoolingDataSource(vmopsPoolableConnectionFactory.getPool());

            // configure the usage db
            final int usageMaxActive = Integer.parseInt(dbProps.getProperty("db.usage.maxActive"));
            final int usageMaxIdle = Integer.parseInt(dbProps.getProperty("db.usage.maxIdle"));
            final long usageMaxWait = Long.parseLong(dbProps.getProperty("db.usage.maxWait"));
            final String usageUsername = dbProps.getProperty("db.usage.username");
            final String usagePassword = dbProps.getProperty("db.usage.password");
            final String usageHost = dbProps.getProperty("db.usage.host");
            final int usagePort = Integer.parseInt(dbProps.getProperty("db.usage.port"));
            final String usageDbName = dbProps.getProperty("db.usage.name");
            final boolean usageAutoReconnect = Boolean.parseBoolean(dbProps.getProperty("db.usage.autoReconnect"));
            final GenericObjectPool usageConnectionPool = new GenericObjectPool(null, usageMaxActive, GenericObjectPool.DEFAULT_WHEN_EXHAUSTED_ACTION,
                    usageMaxWait, usageMaxIdle);
            final ConnectionFactory usageConnectionFactory = new DriverManagerConnectionFactory("jdbc:mysql://"+usageHost + ":" + usagePort + "/" + usageDbName +
                    "?autoReconnect="+usageAutoReconnect, usageUsername, usagePassword);
            final PoolableConnectionFactory usagePoolableConnectionFactory = new PoolableConnectionFactory(usageConnectionFactory, usageConnectionPool,
                    new StackKeyedObjectPoolFactory(), null, false, false);
            s_usageDS = new PoolingDataSource(usagePoolableConnectionFactory.getPool());
        } catch (final Exception e) {
            final GenericObjectPool connectionPool = new GenericObjectPool(null, 5);
            final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory("jdbc:mysql://localhost:3306/vmops", "vmops", "vmops");
            final PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, connectionPool, null, null, false, true);
            s_ds = new PoolingDataSource(/*connectionPool*/ poolableConnectionFactory.getPool());

            final GenericObjectPool connectionPoolUsage = new GenericObjectPool(null, 5);
            final ConnectionFactory connectionFactoryUsage = new DriverManagerConnectionFactory("jdbc:mysql://localhost:3306/vmops_usage", "vmops", "vmops");
            final PoolableConnectionFactory poolableConnectionFactoryUsage = new PoolableConnectionFactory(connectionFactoryUsage, connectionPoolUsage, null, null, false, true);
            s_usageDS = new PoolingDataSource(poolableConnectionFactoryUsage.getPool());
            s_logger.warn("Unable to load db configuration, using defaults with 5 connections.  Please check your configuration", e);
        }
    }
}
