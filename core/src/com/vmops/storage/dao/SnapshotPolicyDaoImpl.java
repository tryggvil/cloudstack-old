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

package com.vmops.storage.dao;


import java.util.List;

import javax.ejb.Local;

import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;

@Local (value={SnapshotPolicyDao.class})
public class SnapshotPolicyDaoImpl extends GenericDaoBase<SnapshotPolicyVO, Long> implements SnapshotPolicyDao {
	private final SearchBuilder<SnapshotPolicyVO> VolumeIdSearch;
	private final SearchBuilder<SnapshotPolicyVO> VolumeIdIntervalSearch;
	
	@Override
	public SnapshotPolicyVO findOneByVolumeInterval(long volumeId, short interval) {
		SearchCriteria sc = VolumeIdIntervalSearch.create();
        sc.setParameters("volumeId", volumeId);
        sc.setParameters("interval", interval);
		return findOneBy(sc);
	}
	
	@Override
	public List<SnapshotPolicyVO> listByVolumeId(long volumeId) {
		return listByVolumeId(volumeId, null);
	}
	
    @Override
    public List<SnapshotPolicyVO> listByVolumeId(long volumeId, Filter filter) {
        SearchCriteria sc = VolumeIdSearch.create();
        sc.setParameters("volumeId", volumeId);
        return listActiveBy(sc, filter);
    }
	
    protected SnapshotPolicyDaoImpl() {
        VolumeIdSearch = createSearchBuilder();
        VolumeIdSearch.addAnd("volumeId", VolumeIdSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        VolumeIdSearch.done();
        
        VolumeIdIntervalSearch = createSearchBuilder();
        VolumeIdIntervalSearch.addAnd("volumeId", VolumeIdIntervalSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        VolumeIdIntervalSearch.addAnd("interval", VolumeIdIntervalSearch.entity().getInterval(), SearchCriteria.Op.EQ);
        VolumeIdIntervalSearch.done();
    }	
}