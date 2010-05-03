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
package com.vmops.storage;

import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.storage.StoragePool.StoragePoolType;

public interface Volume {
	enum VolumeType {UNKNOWN, ROOT, SWAP, DATADISK};
	
	enum MirrorState {NOT_MIRRORED, ACTIVE, DEFUNCT};
	
	enum StorageResourceType {STORAGE_POOL, STORAGE_HOST, SECONDARY_STORAGE};

    /**
     * @return the volume name
     */
    String getName();
    
    /**
     * @return the volume name label
     */
    String getNameLabel();

    /**
     * @return owner's account id
     */
    long getAccountId();

    /**
     * @return id of the owning account's domain
     */
    long getDomainId();
    
    /**
     * @return total size of the partition
     */
    long getSize();
    
    void setSize(long size);
    
    /**
     * @return server hosting the storage.
     */
    Long getHostId();
    
    void setHostId(Long hostId);
    
    /**
     * @return the vm instance id
     */
    Long getInstanceId();
    
    /**
     * @return the folder of the volume
     */
    String getFolder();
    
    /**
     * @return the path created.
     */
    String getPath();
    
    Long getPodId();
    
    long getDataCenterId();
    
    VolumeType getVolumeType();
    
    MirrorState getMirrorState();
    
    StorageResourceType getStorageResourceType();
    
    long getStorageResourceId();

	Long getPoolId();
	
	void setPoolId(Long poolId);
	
    public AsyncInstanceCreateStatus getStatus();
	
	public void setStatus(AsyncInstanceCreateStatus status);
	
}
