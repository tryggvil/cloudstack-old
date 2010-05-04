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
package com.vmops.agent.manager.allocator.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.server.StatsCollector;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.vm.VMInstanceVO;

@Local(value=StoragePoolAllocator.class)
public class RandomStoragePoolAllocator extends AbstractStoragePoolAllocator {
    private static final Logger s_logger = Logger.getLogger(RandomStoragePoolAllocator.class);
    
    public boolean allocatorIsCorrectType(VMInstanceVO vm, ServiceOffering offering) {
    	return true;
    }
    
    @Override
    public StoragePool allocateToPool(ServiceOffering offering,
			DiskOfferingVO diskOffering, DataCenterVO dc, HostPodVO pod, VMInstanceVO vm, 
			VMTemplateVO template, DiskOfferingVO rootDiskOffering, Set<? extends StoragePool> avoid) {
    	
    	// Check that the allocator type is correct
        if (!allocatorIsCorrectType(vm, offering)) {
        	return null;
        }
    	
    	List<StoragePoolVO> pools = _storagePoolDao.listByDataCenterPodId( dc.getId(), pod.getId());
        if (pools.size() == 0) {
        	if (s_logger.isDebugEnabled()) {
    			s_logger.debug("No storage pools available for pod id : " + pod.getId());
    		}
            return null;
        }
        
        StatsCollector sc = StatsCollector.getInstance();
        
        Collections.shuffle(pools);

        for (StoragePoolVO pool: pools) {
        	if (checkPool(avoid, pool, template, null, offering, vm, diskOffering, rootDiskOffering, sc)) {
        		return pool;
        	}
        }
        
        return null;
    }
}
