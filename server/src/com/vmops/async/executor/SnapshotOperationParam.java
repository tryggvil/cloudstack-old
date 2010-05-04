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

package com.vmops.async.executor;

import java.util.List;

public class SnapshotOperationParam extends VolumeOperationParam {
    public enum SnapshotOp {Create, Delete, CreateVolume, RollbackToSnapshot};
    
	private long snapshotId = 0;
	private List<Long> policyIds = null;
	private long policyId = 0;
	
	public SnapshotOperationParam() {
	}
	
	// Used for delete snapshot
	public SnapshotOperationParam(long userId, long volumeId, long snapshotId, long policyId) {
		setUserId(userId);
		setVolumeId(volumeId);
		this.snapshotId = snapshotId;
		this.policyId = policyId;
	}
	
	// Used to create a snapshot
    public SnapshotOperationParam(long userId, long volumeId, List<Long> policyIds) {
        setUserId(userId);
        setVolumeId(volumeId);
        this.policyIds = policyIds;
    }
    
    // Used for CreateVolumeFromSnapshot
	public SnapshotOperationParam(long accountId, long userId, long volumeId, long snapshotId, String volumeName) {
	    setAccountId(accountId);
	    setUserId(userId);
        setVolumeId(volumeId);
        this.snapshotId = snapshotId;
	    setName(volumeName);
	}

    public long getSnapshotId() {
		return snapshotId;
	}
	
	public void setSnapshotId(long snapshotId) {
		this.snapshotId = snapshotId;
	}
	
	public List<Long> getPolicyIds() {
	    return policyIds;
	}
	
	public long getPolicyId() {
	    return policyId;
	}
	
}
