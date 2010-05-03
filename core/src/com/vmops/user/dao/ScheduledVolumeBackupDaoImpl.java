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

package com.vmops.user.dao;

import javax.ejb.Local;

import com.vmops.user.ScheduledVolumeBackup;
import com.vmops.user.ScheduledVolumeBackupVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;

@Local (value={ScheduledVolumeBackupDao.class})
public class ScheduledVolumeBackupDaoImpl extends GenericDaoBase<ScheduledVolumeBackupVO, Long> implements ScheduledVolumeBackupDao {
    protected final SearchBuilder<ScheduledVolumeBackupVO> VolumeIdSearch;

    public ScheduledVolumeBackupDaoImpl() {
        VolumeIdSearch = createSearchBuilder();
        VolumeIdSearch.addAnd("volumeId", VolumeIdSearch.entity().getVolumeId(), SearchCriteria.Op.EQ);
        VolumeIdSearch.done();
    }

    public ScheduledVolumeBackup findByVolumeId(long volumeId) {
        SearchCriteria sc = VolumeIdSearch.create("volumeId", volumeId);
        return findOneActiveBy(sc);
    }
}
