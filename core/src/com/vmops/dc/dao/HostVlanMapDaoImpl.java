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

package com.vmops.dc.dao;

import java.util.List;

import javax.ejb.Local;

import com.vmops.dc.HostVlanMapVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;

@Local(value={HostVlanMapDao.class})
public class HostVlanMapDaoImpl extends GenericDaoBase<HostVlanMapVO, Long> implements HostVlanMapDao {
    
	protected SearchBuilder<HostVlanMapVO> HostSearch;
	protected SearchBuilder<HostVlanMapVO> HostVlanSearch;
	
	@Override
	public List<HostVlanMapVO> getHostVlanMaps(long hostId) {
		SearchCriteria sc = HostSearch.create();
    	sc.setParameters("hostId", hostId);
    	return listBy(sc);
	}
	
	@Override
	public HostVlanMapVO findHostVlanMap(long hostId, String vlanId) {
		SearchCriteria sc = HostVlanSearch.create();
		sc.setParameters("hostId", hostId);
		sc.setParameters("vlanId", vlanId);
		return findOneBy(sc);
	}
	
    public HostVlanMapDaoImpl() {
    	HostSearch = createSearchBuilder();
    	HostSearch.addAnd("hostId", HostSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostSearch.done();
        
        HostVlanSearch = createSearchBuilder();
        HostVlanSearch.addAnd("hostId", HostVlanSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostVlanSearch.addAnd("vlanId", HostVlanSearch.entity().getVlanId(), SearchCriteria.Op.EQ);
        HostVlanSearch.done();
    }
    
}
