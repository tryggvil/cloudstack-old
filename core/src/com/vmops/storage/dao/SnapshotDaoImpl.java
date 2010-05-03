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

import java.math.BigInteger;
import java.util.List;

import javax.ejb.Local;

import com.vmops.storage.SnapshotVO;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;

@Local (value={SnapshotDao.class})
public class SnapshotDaoImpl extends GenericDaoBase<SnapshotVO, Long> implements SnapshotDao {
    
    private final SearchBuilder<SnapshotVO> VolumeIdSearch;
    private final SearchBuilder<SnapshotVO> ParentIdSearch;
    private final SearchBuilder<SnapshotVO> lastSnapSearch;

    @Override
    public SnapshotVO findNextSnapshot(long parentSnapId) {
        SearchCriteria sc = ParentIdSearch.create();
        sc.setParameters("prevSnapshotId", parentSnapId);
        return findOneActiveBy(sc);
    }

    @Override
    public List<SnapshotVO> listByVolumeId(long volumeId) {
        return listByVolumeId(null, volumeId);
    }
    
    @Override
    public List<SnapshotVO> listByVolumeId(Filter filter, long volumeId ) {
        SearchCriteria sc = VolumeIdSearch.create();
        sc.setParameters("volumeId", volumeId);
        return listActiveBy(sc, filter);
    }

    protected SnapshotDaoImpl() {
        VolumeIdSearch = createSearchBuilder();
        VolumeIdSearch.addAnd("volumeId", VolumeIdSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        VolumeIdSearch.done();
        
        ParentIdSearch = createSearchBuilder();
        ParentIdSearch.addAnd("prevSnapshotId", ParentIdSearch.entity().getPrevSnapshotId(), SearchCriteria.Op.EQ);
        ParentIdSearch.done();
        
        lastSnapSearch = createSearchBuilder();
        lastSnapSearch.select(SearchCriteria.Func.MAX, lastSnapSearch.entity().getId());
        lastSnapSearch.addAnd("volumeId", lastSnapSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        lastSnapSearch.addAnd("snapId", lastSnapSearch.entity().getId(), SearchCriteria.Op.NEQ);
        lastSnapSearch.done();
    }

    @Override
    public Long findExcessSnapshotsByVolumeAndType(Long volumeId, short type, int maxSnapshots) {
        return null;
    }

	@Override
	public long getLastSnapshot(long volumeId, long snapId) {
		SearchCriteria sc = lastSnapSearch.create();
		sc.setParameters("volumeId", volumeId);
		sc.setParameters("snapId", snapId);
		List<Object[]> abc = searchAll(sc, null);
		if( abc != null && abc.size() > 0 && abc.get(0).length > 0){
			return ((BigInteger)abc.get(0)[0]).longValue();
		}
		return 0;
	}
}