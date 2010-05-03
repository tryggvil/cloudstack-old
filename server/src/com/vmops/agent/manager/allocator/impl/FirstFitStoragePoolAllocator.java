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
package com.vmops.agent.manager.allocator.impl;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.server.StatsCollector;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.StorageStats;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateStorageResourceAssoc;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.VMTemplateStorageResourceAssoc.Status;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.template.TemplateManager;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.dao.UserVmDao;

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
