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
package com.vmops.ha.dao;

import java.sql.PreparedStatement;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.ha.HighAvailabilityManager;
import com.vmops.ha.WorkVO;
import com.vmops.ha.HighAvailabilityManager.Step;
import com.vmops.ha.WorkVO.WorkType;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;

@Local(value={HighAvailabilityDao.class})
public class HighAvailabilityDaoImpl extends GenericDaoBase<WorkVO, Long> implements HighAvailabilityDao {
    private static final Logger s_logger = Logger.getLogger(HighAvailabilityDaoImpl.class);
	
    private final SearchBuilder<WorkVO> TBASearch;
    private final SearchBuilder<WorkVO> PreviousInstanceSearch;
    private final SearchBuilder<WorkVO> UntakenMigrationSearch;
    private final SearchBuilder<WorkVO> CleanupSearch;
    private final SearchBuilder<WorkVO> PreviousWorkSearch;
    private final SearchBuilder<WorkVO> TakenWorkSearch;

    protected HighAvailabilityDaoImpl() {
        super();
        
        CleanupSearch = createSearchBuilder();
        CleanupSearch.addAnd("time", CleanupSearch.entity().getTimeToTry(), SearchCriteria.Op.LTEQ);
        CleanupSearch.addAnd("step", CleanupSearch.entity().getStep(), SearchCriteria.Op.IN);
        CleanupSearch.done();
        
        TBASearch = createSearchBuilder();
        TBASearch.addAnd("server", TBASearch.entity().getServerId(), SearchCriteria.Op.NULL);
        TBASearch.addAnd("taken", TBASearch.entity().getDateTaken(), SearchCriteria.Op.NULL);
        TBASearch.addAnd("time", TBASearch.entity().getTimeToTry(), SearchCriteria.Op.LTEQ);
        TBASearch.done();
        
        PreviousInstanceSearch = createSearchBuilder();
        PreviousInstanceSearch.addAnd("instance", PreviousInstanceSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        PreviousInstanceSearch.done();
        
        UntakenMigrationSearch = createSearchBuilder();
        UntakenMigrationSearch.addAnd("host", UntakenMigrationSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        UntakenMigrationSearch.addAnd("type", UntakenMigrationSearch.entity().getWorkType(), SearchCriteria.Op.EQ);
        UntakenMigrationSearch.addAnd("server", UntakenMigrationSearch.entity().getServerId(), SearchCriteria.Op.NULL);
        UntakenMigrationSearch.addAnd("taken", UntakenMigrationSearch.entity().getDateTaken(), SearchCriteria.Op.NULL);
        UntakenMigrationSearch.done();
        
        TakenWorkSearch = createSearchBuilder();
        TakenWorkSearch.addAnd("type", TakenWorkSearch.entity().getWorkType(), SearchCriteria.Op.EQ);
        TakenWorkSearch.addAnd("server", TakenWorkSearch.entity().getServerId(), SearchCriteria.Op.NNULL);
        TakenWorkSearch.addAnd("taken", TakenWorkSearch.entity().getDateTaken(), SearchCriteria.Op.NNULL);
        TakenWorkSearch.addAnd("step", TakenWorkSearch.entity().getStep(), SearchCriteria.Op.NIN);
        TakenWorkSearch.done();
        
        PreviousWorkSearch = createSearchBuilder();
        PreviousWorkSearch.addAnd("instance", PreviousWorkSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        PreviousWorkSearch.addAnd("type", PreviousWorkSearch.entity().getWorkType(), SearchCriteria.Op.EQ);
        PreviousWorkSearch.addAnd("taken", PreviousWorkSearch.entity().getDateTaken(), SearchCriteria.Op.NULL);
        PreviousWorkSearch.done();
    }

    @Override
    public WorkVO take(final long serverId) {
        final Transaction txn = Transaction.currentTxn();
        try {
            final SearchCriteria sc = TBASearch.create();
            sc.setParameters("time", System.currentTimeMillis() >> 10);

            final Filter filter = new Filter(WorkVO.class, null, true, 0l, 1l);

            txn.start();
            final List<WorkVO> vos = lock(sc, filter, true);
            if (vos.size() == 0) {
                txn.commit();
                return null;
            }

            final WorkVO work = vos.get(0);
            work.setServerId(serverId);
            work.setDateTaken(new Date());

            update(work.getId(), work);

            txn.commit();

            return work;

        } catch (final Throwable e) {
            throw new VmopsRuntimeException("Unable to execute take", e);
        }
    }

    @Override
    public List<WorkVO> findPreviousHA(final long instanceId) {
        final SearchCriteria sc = PreviousInstanceSearch.create();
        sc.setParameters("instance", instanceId);
        return listBy(sc);
    }

    @Override
    public void cleanup(final long time) {
        final SearchCriteria sc = CleanupSearch.create();
        sc.setParameters("time", time);
        sc.setParameters("step", HighAvailabilityManager.Step.Done, HighAvailabilityManager.Step.Cancelled);
        delete(sc);
    }

    @Override
    public void deleteMigrationWorkItems(final long hostId, final WorkType type, final long serverId) {
        final SearchCriteria sc = UntakenMigrationSearch.create();
        sc.setParameters("host", hostId);
        sc.setParameters("type", type.toString());

        WorkVO work = createForUpdate();
        Date date = new Date();
        work.setDateTaken(date);
        work.setServerId(serverId);
        work.setStep(HighAvailabilityManager.Step.Cancelled);
        
        update(work, sc);
    }

    @Override
    public List<WorkVO> findTakenWorkItems(WorkType type) {
    	SearchCriteria sc = TakenWorkSearch.create();
    	sc.setParameters("type", type);
    	sc.setParameters("step", Step.Done, Step.Cancelled);
    	
    	return listActiveBy(sc);
    }
    
    
    @Override
    public void deleteWorkItemsByInstance(long instanceId) {
        Transaction txn = Transaction.currentTxn();
        try {
        	PreparedStatement stmt = txn.prepareAutoCloseStatement("delete from op_ha_work where instance_id=?");
        	stmt.setLong(1, instanceId);
        	stmt.execute();
        } catch(Exception e) {
        	s_logger.error("Unable to delete HA work items by VM instance", e);
        }
    }
    
    @Override
    public boolean delete(long instanceId, WorkType type) {
    	SearchCriteria sc = PreviousWorkSearch.create();
    	sc.setParameters("instance", instanceId);
    	sc.setParameters("type", type);
    	return delete(sc) > 0;
    }
    
    @Override
    public boolean hasBeenScheduled(long instanceId, WorkType type) {
    	SearchCriteria sc = PreviousWorkSearch.create();
    	sc.setParameters("instance", instanceId);
    	sc.setParameters("type", type);
    	return listActiveBy(sc, null).size() > 0;
    }
}