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

import com.vmops.storage.StoragePoolVO;
import com.vmops.utils.db.GenericDao;
/**
 * Data Access Object for storage_pool table
 */
public interface StoragePoolDao extends GenericDao<StoragePoolVO, Long> {

	/**
	 * @param datacenterId -- the id of the datacenter (availability zone)
	 * @return the list of storage pools in the datacenter
	 */
	List<StoragePoolVO> listByDataCenterId(long datacenterId);
	
	/**
	 * @param datacenterId -- the id of the datacenter (availability zone)
	 * @param podId the id of the pod
	 * @return the list of storage pools in the datacenter
	 */
	List<StoragePoolVO> listByDataCenterPodId(long datacenterId, long podId);
    
	/**
	 * Set capacity of storage pool in bytes
	 * @param id pool id.
	 * @param capacity capacity in bytes
	 */
    void updateCapacity(long id, long capacity);
    
	/**
	 * Set available bytes of storage pool in bytes
	 * @param id pool id.
	 * @param available available capacity in bytes
	 */
    void updateAvailable(long id, long available);
        
    /**
     * Find pool by name.
     * 
     * @param name name of pool.
     * @return the single  StoragePoolVO
     */
    List<StoragePoolVO> findPoolByName(String name);
    
    
    /**
     * Find pool by UUID.
     * 
     * @param uuid uuid of pool.
     * @return the single  StoragePoolVO
     */
    StoragePoolVO findPoolByUUID(String uuid);

    List<StoragePoolVO> listByStorageHost(String hostFqdnOrIp);

    StoragePoolVO findPoolByHostPath(long dcId, Long podId, String host, String path);
    
    List<StoragePoolVO> listPoolByHostPath(long dcId,  String host, String path);
    
}
