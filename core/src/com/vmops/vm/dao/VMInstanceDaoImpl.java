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
import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.db.Attribute;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.db.UpdateBuilder;
import com.vmops.vm.State;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;

@Local(value={VMInstanceDao.class})
public class VMInstanceDaoImpl extends GenericDaoBase<VMInstanceVO, Long> implements VMInstanceDao {
    
    public static final Logger s_logger = Logger.getLogger(VMInstanceDaoImpl.class.getName());

    private static final String COUNT_ROUTERS_AND_PROXIES = "SELECT count(*) from `vmops`.`vm_instance` where host_id = ? AND type = 'DomainRouter'" +
                                                            " UNION ALL" +
                                                            " SELECT count(*) from `vmops`.`vm_instance` where host_id = ? AND type = 'ConsoleProxy'";

    protected final SearchBuilder<VMInstanceVO> IdStatesSearch;
    protected final SearchBuilder<VMInstanceVO> HostSearch;
    protected final SearchBuilder<VMInstanceVO> ZoneSearch;
    protected final SearchBuilder<VMInstanceVO> PoolTemplateActiveSearch;
    protected final SearchBuilder<VMInstanceVO> PoolISOActiveSearch;
    protected final SearchBuilder<VMInstanceVO> NameLikeSearch;
    protected final SearchBuilder<VMInstanceVO> StateChangeSearch;
    protected final SearchBuilder<VMInstanceVO> TransitionSearch;
    
    protected final Attribute _updateTimeAttr;
    
	protected VMInstanceDaoImpl() {
        IdStatesSearch = createSearchBuilder();
        IdStatesSearch.addAnd("id", IdStatesSearch.entity().getId(), SearchCriteria.Op.EQ);
        IdStatesSearch.addAnd("states", IdStatesSearch.entity().getState(), SearchCriteria.Op.IN);
        IdStatesSearch.done();
        
        HostSearch = createSearchBuilder();
        HostSearch.addAnd("host", HostSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostSearch.done();
        
        ZoneSearch = createSearchBuilder();
        ZoneSearch.addAnd("zone", ZoneSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        ZoneSearch.done();
        
        PoolTemplateActiveSearch = createSearchBuilder();
        PoolTemplateActiveSearch.addAnd("pool", PoolTemplateActiveSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
        PoolTemplateActiveSearch.addAnd("template", PoolTemplateActiveSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        PoolTemplateActiveSearch.addAnd("removed", PoolTemplateActiveSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        PoolTemplateActiveSearch.done();
        
        PoolISOActiveSearch = createSearchBuilder();
        PoolISOActiveSearch.addAnd("pool", PoolISOActiveSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
        PoolISOActiveSearch.addAnd("iso", PoolISOActiveSearch.entity().getIsoId(), SearchCriteria.Op.EQ);
        PoolISOActiveSearch.addAnd("removed", PoolISOActiveSearch.entity().getRemoved(), SearchCriteria.Op.NULL);
        PoolISOActiveSearch.done();
        
        NameLikeSearch = createSearchBuilder();
        NameLikeSearch.addAnd("name", NameLikeSearch.entity().getName(), SearchCriteria.Op.LIKE);
        NameLikeSearch.done();
        
        StateChangeSearch = createSearchBuilder();
        StateChangeSearch.addAnd("id", StateChangeSearch.entity().getId(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("states", StateChangeSearch.entity().getState(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("host", StateChangeSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        StateChangeSearch.addAnd("update", StateChangeSearch.entity().getUpdated(), SearchCriteria.Op.EQ);
        StateChangeSearch.done();
        
        TransitionSearch = createSearchBuilder();
        TransitionSearch.addAnd("updateTime", TransitionSearch.entity().getUpdateTime(), SearchCriteria.Op.LT);
        TransitionSearch.addAnd("states", TransitionSearch.entity().getState(), SearchCriteria.Op.IN);
        TransitionSearch.done();
        
        _updateTimeAttr = _allAttributes.get("updateTime");
        assert _updateTimeAttr != null : "Couldn't get this updateTime attribute";
    }
	
	@Override
	public List<VMInstanceVO> findVMInstancesLike(String name) {
        SearchCriteria sc = NameLikeSearch.create();
        sc.setParameters("name", "%" + name + "%");
        return listActiveBy(sc);
    }
	
    @Override
    public boolean updateIf(VMInstanceVO vm, VirtualMachine.Event event, Long hostId) {
    	
    	State oldState = vm.getState();
    	State newState = oldState.getNextState(event);
    	
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
        ub.set(vm, "state", newState);
        ub.set(vm, "hostId", hostId);
        ub.set(vm, _updateTimeAttr, new Date());
        
        int result = update(vm, sc);
        if (result == 0 && s_logger.isDebugEnabled()) {
        	VMInstanceVO vo = findById(vm.getId());
        	StringBuilder str = new StringBuilder("Unable to update ").append(vo.toString());
        	str.append(": DB Data={Host=").append(vo.getHostId()).append("; State=").append(vo.getState().toString()).append("; updated=").append(vo.getUpdated());
        	str.append("} Stale Data: {Host=").append(vm.getHostId()).append("; State=").append(vm.getState().toString()).append("; updated=").append(vm.getUpdated()).append("}");
        	s_logger.debug(str.toString());
        }
        return result > 0;
    }
    
    @Override
    public void updateVM(long id, String displayName, String group, boolean enable) {
        VMInstanceVO vo = createForUpdate();
        vo.setDisplayName(displayName);
        vo.setGroup(group);
        vo.setHaEnabled(enable);
        update(id, vo);
    }
    
    @Override
	public List<VMInstanceVO> listByHostId(long hostid) {
	    SearchCriteria sc = HostSearch.create();
	    sc.setParameters("host", hostid);
	    
	    return listActiveBy(sc);
	}
    
    @Override
	public List<VMInstanceVO> listByZoneId(long zoneId) {
	    SearchCriteria sc = ZoneSearch.create();
	    sc.setParameters("zone", zoneId);
	    
	    return listActiveBy(sc);
	}
    
    @Override
    public List<VMInstanceVO> listByPoolAndTemplateActive(long poolId, long templateId) {
    	SearchCriteria sc = PoolTemplateActiveSearch.create();
    	sc.setParameters("pool", poolId);
    	sc.setParameters("template", templateId);
    	
    	return listActiveBy(sc);
    }
    
    @Override
    public List<VMInstanceVO> listByPoolAndISOActive(long poolId, long isoId) {
    	SearchCriteria sc1 = PoolTemplateActiveSearch.create();
    	sc1.setParameters("pool", poolId);
    	sc1.setParameters("template", isoId);
    	
    	SearchCriteria sc2 = PoolISOActiveSearch.create();
    	sc2.setParameters("pool", poolId);
    	sc2.setParameters("iso", isoId);
    	
    	List<VMInstanceVO> vmInstances = listActiveBy(sc1);
    	vmInstances.addAll(listActiveBy(sc2));
    	
    	return vmInstances;
    }
    
    @Override
    public List<VMInstanceVO> findVMInTransition(Date time, State... states) {
    	SearchCriteria sc = TransitionSearch.create();
    	
    	sc.setParameters("states", (Object[])states);
    	sc.setParameters("updateTime", time);
    	
    	return search(sc, null);
    }

    public Integer[] countRoutersAndProxies(Long hostId) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        Integer[] routerAndProxyCount = new Integer[] {null, null};
        try {
            String sql = COUNT_ROUTERS_AND_PROXIES;
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, hostId);
            pstmt.setLong(2, hostId);
            ResultSet rs = pstmt.executeQuery();
            int i = 0;
            while (rs.next()) {
                routerAndProxyCount[i++] = rs.getInt(1);
            }
        } catch (Exception e) {
            s_logger.warn("Exception searching for routers and proxies", e);
        }
        return routerAndProxyCount;
    }
}
