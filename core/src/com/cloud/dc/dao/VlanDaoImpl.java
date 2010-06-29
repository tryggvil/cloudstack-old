/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
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

package com.cloud.dc.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.PodVlanMapVO;
import com.cloud.dc.Vlan;
import com.cloud.dc.VlanVO;
import com.cloud.dc.Vlan.VlanType;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;

@Local(value={VlanDao.class})
public class VlanDaoImpl extends GenericDaoBase<VlanVO, Long> implements VlanDao {
    
	private static final Logger s_logger = Logger.getLogger(VlanDaoImpl.class);
	protected SearchBuilder<VlanVO> ZoneVlanIdSearch;
	protected SearchBuilder<VlanVO> ZoneSearch;
	protected SearchBuilder<VlanVO> ZoneTypeSearch;
	protected PodVlanMapDaoImpl _podVlanMapDao = new PodVlanMapDaoImpl();
	protected IPAddressDao _ipAddressDao = null;
	
    @Override
    public VlanVO findByZoneAndVlanId(long zoneId, String vlanId) {
    	SearchCriteria sc = ZoneVlanIdSearch.create();
    	sc.setParameters("zoneId", zoneId);
    	sc.setParameters("vlanId", vlanId);
        return findOneActiveBy(sc);
    }
    
    @Override
    public List<VlanVO> findByZone(long zoneId) {
    	SearchCriteria sc = ZoneSearch.create();
    	sc.setParameters("zoneId", zoneId);
    	return listBy(sc);
    }
	
    public VlanDaoImpl() {
    	ZoneVlanIdSearch = createSearchBuilder();
    	ZoneVlanIdSearch.and("zoneId", ZoneVlanIdSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        ZoneVlanIdSearch.and("vlanId", ZoneVlanIdSearch.entity().getVlanId(), SearchCriteria.Op.EQ);
        ZoneVlanIdSearch.done();
        
        ZoneSearch = createSearchBuilder();
        ZoneSearch.and("zoneId", ZoneSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        ZoneSearch.done();
        
        ZoneTypeSearch = createSearchBuilder();
        ZoneTypeSearch.and("zoneId", ZoneTypeSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        ZoneTypeSearch.and("vlanType", ZoneTypeSearch.entity().getVlanType(), SearchCriteria.Op.EQ);
        ZoneTypeSearch.done();
    }

	@Override
	public List<VlanVO> listByZoneAndType(long zoneId, VlanType vlanType) {
		SearchCriteria sc = ZoneTypeSearch.create();
    	sc.setParameters("zoneId", zoneId);
    	sc.setParameters("vlanType", vlanType);
        return listBy(sc);
	}

	@Override
	public List<VlanVO> listVlansForPod(long podId) {
		//FIXME: use a join statement to improve the performance (should be minor since we expect only one or two
		List<PodVlanMapVO> vlanMaps = _podVlanMapDao.listPodVlanMaps(podId);
		List<VlanVO> result  = new ArrayList<VlanVO>();
		for (PodVlanMapVO pvmvo: vlanMaps) {
			result.add(findById(pvmvo.getVlanDbId()));
		}
		return result;
	}

	@Override
	public List<VlanVO> listVlansForPodByType(long podId, VlanType vlanType) {
		//FIXME: use a join statement to improve the performance (should be minor since we expect only one or two)
		List<PodVlanMapVO> vlanMaps = _podVlanMapDao.listPodVlanMaps(podId);
		List<VlanVO> result  = new ArrayList<VlanVO>();
		for (PodVlanMapVO pvmvo: vlanMaps) {
			VlanVO vlan =findById(pvmvo.getVlanDbId());
			if (vlan.getVlanType() == vlanType) {
				result.add(vlan);
			}
		}
		return result;
	}

	@Override
	public void addToPod(long podId, long vlanDbId) {
		PodVlanMapVO pvmvo = new PodVlanMapVO(podId, vlanDbId);
		_podVlanMapDao.persist(pvmvo);
		
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		boolean result = super.configure(name, params);
		if (result) {
	        final ComponentLocator locator = ComponentLocator.getCurrentLocator();
			_ipAddressDao = locator.getDao(IPAddressDao.class);
			if (_ipAddressDao == null) {
				throw new ConfigurationException("Unable to get " + IPAddressDao.class.getName());
			}
		}
		return result;
	}
	
	private VlanVO findNextVlan(long zoneId, Vlan.VlanType vlanType) {
		List<VlanVO> vlans = listByZoneAndType(zoneId, vlanType);
		VlanVO result = null;
		for (VlanVO vlan : vlans) {
			long vlanDbId = vlan.getId();
			
			int countOfAllocatedIps = _ipAddressDao.countIPs(zoneId, vlanDbId, -1, true);
			int countOfAllIps = _ipAddressDao.countIPs(zoneId, vlanDbId, -1, false);
			
			if ((countOfAllocatedIps > 0) && (countOfAllocatedIps < countOfAllIps)) {
				result = vlan;
				break;
			}
		}
		
		if (result == null) {
			for (VlanVO vlan : vlans) {
				long vlanDbId = vlan.getId();
				
				int countOfAllocatedIps = _ipAddressDao.countIPs(zoneId, vlanDbId, -1, true);
				if (countOfAllocatedIps == 0) {
					result = vlan;
					break;
				}
			}
		}
		
		return result;
	}

	@Override
	public Pair<String, VlanVO> assignIpAddress(long zoneId, long accountId, long domainId, VlanType vlanType, boolean sourceNat) {
		VlanVO vlan = findNextVlan(zoneId, vlanType);
		if (vlan == null) {
			return null;
		}
		String ipAddress = _ipAddressDao.assignIpAddress(accountId, domainId, vlan.getId(), sourceNat);
		if (ipAddress == null) {
			return null;
		}
		return new Pair<String, VlanVO>(ipAddress, vlan);
	}
    
}