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

import com.vmops.storage.SnapshotVO;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDao;

public interface SnapshotDao extends GenericDao<SnapshotVO, Long> {
	List<SnapshotVO> listByVolumeId(long volumeId);
	List<SnapshotVO> listByVolumeId(Filter filter, long volumeId);
	SnapshotVO findNextSnapshot(long parentSnapId);
    Long findExcessSnapshotsByVolumeAndType(Long volumeId, short type, int maxSnapshots);
	long getLastSnapshot(long volumeId, long snapId);
}
