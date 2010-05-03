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

/**
 * 
 */
package com.vmops.storage.dao;

import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import com.vmops.storage.SnapshotScheduleVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;


@Local (value={SnapshotScheduleDao.class})
public class SnapshotScheduleDaoImpl extends GenericDaoBase<SnapshotScheduleVO, Long> implements SnapshotScheduleDao {
	protected final SearchBuilder<SnapshotScheduleVO> executableSchedulesSearch;
	protected final SearchBuilder<SnapshotScheduleVO> coincidingSchedulesSearch;
	protected final SearchBuilder<SnapshotScheduleVO> volumeSearch;
	protected final SearchBuilder<SnapshotScheduleVO> policyInstanceSearch;
	
	
	protected SnapshotScheduleDaoImpl() {
		
	    executableSchedulesSearch = createSearchBuilder();
        executableSchedulesSearch.addAnd("scheduledTimestamp", executableSchedulesSearch.entity().getScheduledTimestamp(), SearchCriteria.Op.LT);
        executableSchedulesSearch.done();
        
        coincidingSchedulesSearch = createSearchBuilder();
        coincidingSchedulesSearch.addAnd("volumeId", coincidingSchedulesSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        coincidingSchedulesSearch.addAnd("scheduledTimestamp", coincidingSchedulesSearch.entity().getScheduledTimestamp(), SearchCriteria.Op.LT);
        coincidingSchedulesSearch.done();
		
        volumeSearch = createSearchBuilder();
        volumeSearch.addAnd("volumeId", volumeSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        volumeSearch.done();
        
        policyInstanceSearch = createSearchBuilder();
        policyInstanceSearch.addAnd("volumeId", policyInstanceSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        policyInstanceSearch.addAnd("policyId", policyInstanceSearch.entity().getPolicyId(), SearchCriteria.Op.EQ);
        policyInstanceSearch.done();
        
	}
	
	/**
	 * {@inheritDoc} 
	 */
	@Override
	public List<SnapshotScheduleVO> getCoincidingSnapshotSchedules(long volumeId, Date date) {
		SearchCriteria sc = coincidingSchedulesSearch.create();
	    sc.setParameters("volumeId", volumeId);
	    sc.setParameters("scheduledTimestamp", date);
	    return listActiveBy(sc);
	}

	/**
     * {@inheritDoc} 
     */
    @Override
    public List<SnapshotScheduleVO> getSchedulesToExecute(Date currentTimestamp) {
        SearchCriteria sc = executableSchedulesSearch.create();
        sc.setParameters("scheduledTimestamp", currentTimestamp);
        return listActiveBy(sc);
    }
    
    /**
     * {@inheritDoc} 
     */
    @Override
    public List<SnapshotScheduleVO> listSchedules(Long volumeId, Long policyId) {
        assert volumeId != null;
        SearchCriteria sc = null;
        if (policyId == null) {
            sc = volumeSearch.create();
            sc.setParameters("volumeId", volumeId);
        }
        else {
            sc = policyInstanceSearch.create();
            sc.setParameters("volumeId", volumeId);
            sc.setParameters("policyId", policyId);
        }
        return listActiveBy(sc);
    }

}