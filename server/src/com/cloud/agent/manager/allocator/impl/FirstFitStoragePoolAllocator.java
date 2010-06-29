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
package com.cloud.agent.manager.allocator.impl;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.manager.allocator.StoragePoolAllocator;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.server.StatsCollector;
import com.cloud.service.ServiceOffering;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolVO;
import com.cloud.storage.StorageStats;
import com.cloud.storage.VMTemplateHostVO;
import com.cloud.storage.VMTemplateStoragePoolVO;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.storage.dao.StoragePoolDao;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateHostDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.template.TemplateManager;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachine;
import com.cloud.vm.dao.UserVmDao;

@Local(value=StoragePoolAllocator.class)
public class FirstFitStoragePoolAllocator extends AbstractStoragePoolAllocator {
    private static final Logger s_logger = Logger.getLogger(FirstFitStoragePoolAllocator.class);

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        return super.configure(name, params);
    }
    
    public boolean allocatorIsCorrectType(VMInstanceVO vm, ServiceOffering offering) {
    	return !localStorageAllocationNeeded(vm, offering);
    }

	public StoragePool allocateToPool(ServiceOffering offering, DiskOfferingVO diskOffering, DataCenterVO dc, HostPodVO pod,
									  VMInstanceVO vm, VMTemplateVO template, DiskOfferingVO rootDiskOffering, Set<? extends StoragePool> avoid) {
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

        for (StoragePoolVO pool: pools) {
        	if (checkPool(avoid, pool, template, null, offering, vm, diskOffering, rootDiskOffering, sc)) {
        		return pool;
        	}
        }
        
        if (s_logger.isDebugEnabled()) {
			s_logger.debug("Unable to find any storage pool");
		}
        
        if (_dontMatter && pools.size() > 0) {
        	return pools.get(0);
        } else {
        	return null;
        }
		
	}

}
