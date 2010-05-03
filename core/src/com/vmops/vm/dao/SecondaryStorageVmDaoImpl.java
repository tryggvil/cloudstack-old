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
package com.vmops.vm.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.utils.db.Attribute;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.db.UpdateBuilder;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachine;

@Local(value={SecondaryStorageVmDao.class})
public class SecondaryStorageVmDaoImpl extends GenericDaoBase<SecondaryStorageVmVO, Long> implements SecondaryStorageVmDao {
    private static final Logger s_logger = Logger.getLogger(SecondaryStorageVmDaoImpl.class);
    
    protected SearchBuilder<SecondaryStorageVmVO> DataCenterStatusSearch;
    protected SearchBuilder<SecondaryStorageVmVO> StateSearch;
    protected SearchBuilder<SecondaryStorageVmVO> HostSearch;
    protected SearchBuilder<SecondaryStorageVmVO> StateChangeSearch;
    
    protected final Attribute _updateTimeAttr;
    
    public SecondaryStorageVmDaoImpl() {
        DataCenterStatusSearch = createSearchBuilder();
        DataCenterStatusSearch.addAnd("dc", DataCenterStatusSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DataCenterStatusSearch.addAnd("states", DataCenterStatusSearch.entity().getState(), SearchCriteria.Op.IN);
        DataCenterStatusSearch.done();
        
        StateSearch = createSearchBuilder();
        StateSearch.addAnd("states", StateSearch.entity().getState(), SearchCriteria.Op.IN);
        StateSearch.done();
        
        HostSearch = createSearchBuilder();
        HostSearch.addAnd("host", HostSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostSearch.done();
        
        StateChangeSearch = createSearchBuilder();
        StateChangeSearch.addAnd("id", StateChangeSearch.entity().getId(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("states", StateChangeSearch.entity().getState(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("host", StateChangeSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("update", StateChangeSearch.entity().getUpdated(), SearchCriteria.Op.EQ);
        StateChangeSearch.done();
        
        _updateTimeAttr = _allAttributes.get("updateTime");
        assert _updateTimeAttr != null : "Couldn't get this updateTime attribute";
    }
    
    @Override
    public boolean updateIf(SecondaryStorageVmVO vm, VirtualMachine.Event event, Long hostId) {
    	State oldState = vm.getState();
    	State newState = oldState.getNextState(event);
    	
    	Long oldHostId = vm.getHostId();
    	long oldDate = vm.getUpdated();
    	
    	if (newState == null) {
    		if (s_logger.isDebugEnabled()) {
    	    	s_logger.debug("There's no way to transition from old state: " + oldState.toString() + " event: " + event.toString());
    		}
    		return false;
    	}
    		
    	SearchCriteria sc = StateChangeSearch.create();
    	sc.setParameters("id", vm.getId());
    	sc.setParameters("states", oldState);
    	sc.setParameters("host", vm.getHostId());
    	sc.setParameters("update", vm.getUpdated());
    	
    	vm.incrUpdated();
        UpdateBuilder ub = getUpdateBuilder(vm);
        ub.set(vm, _updateTimeAttr, new Date());
        ub.set(vm, "state", newState);
        ub.set(vm, "hostId", hostId);
        if (newState == State.Stopped) {
        	ub.set(vm, "hostId", null);
        }
        
        int result = update(vm, sc);
        
        if (result == 0 && s_logger.isDebugEnabled()) {
        	SecondaryStorageVmVO vo = findById(vm.getId());
        	StringBuilder str = new StringBuilder("Unable to update ").append(vo.toString());
        	str.append(": DB Data={Host=").append(vo.getHostId()).append("; State=").append(vo.getState().toString()).append("; updated=").append(vo.getUpdated());
        	str.append("} New Data: {Host=").append(vm.getHostId()).append("; State=").append(vm.getState().toString()).append("; updated=").append(vm.getUpdated());
        	str.append("} Stale Data: {Host=").append(oldHostId).append("; State=").append(oldState.toString()).append("; updated=").append(oldDate).append("}");
        	s_logger.debug(str.toString());
        }
        
        return result > 0;
    }
    

    @Override
    public boolean remove(Long id) {
        Transaction txn = Transaction.currentTxn();
        txn.start();
        SecondaryStorageVmVO proxy = createForUpdate();
        proxy.setPublicIpAddress(null);
        proxy.setPrivateIpAddress(null);
        
        UpdateBuilder ub = getUpdateBuilder(proxy);
        ub.set(proxy, "state", State.Destroyed);
        ub.set(proxy, "privateIpAddress", null);
        update(id, ub);
        
        boolean result = super.remove(id);
        txn.commit();
        return result;
    }
    
    @Override
    public List<SecondaryStorageVmVO> getSecStorageVmListInStates(long dataCenterId, State... states) {
        SearchCriteria sc = DataCenterStatusSearch.create();
        sc.setParameters("states", (Object[])states);
        sc.setParameters("dc", dataCenterId);
        return listActiveBy(sc);
    }

    @Override
    public List<SecondaryStorageVmVO> getSecStorageVmListInStates(State... states) {
        SearchCriteria sc = StateSearch.create();
        sc.setParameters("states", (Object[])states);
        return listActiveBy(sc);
    }
    
    @Override
    public List<SecondaryStorageVmVO> listByHostId(long hostId) {
        SearchCriteria sc = HostSearch.create();
        sc.setParameters("host", hostId);
        return listActiveBy(sc);
    }
    
    
    @Override
    public List<Long> getRunningSecStorageVmListByMsid(long msid) {
    	List<Long> l = new ArrayList<Long>();
        Transaction txn = Transaction.currentTxn();;
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(
            		"SELECT s.id FROM secondary_storage_vm s, vm_instance v, host h " +
            		"WHERE s.id=v.id AND v.state='Running' AND v.host_id=h.id AND h.mgmt_server_id=?");
            
            pstmt.setLong(1, msid);
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) {
            	l.add(rs.getLong(1));
            }
        } catch (SQLException e) {
        } catch (Throwable e) {
        }
        return l;
    }
}
