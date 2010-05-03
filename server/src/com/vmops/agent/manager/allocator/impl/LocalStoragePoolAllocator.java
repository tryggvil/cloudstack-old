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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.utils.NumbersUtil;
import com.vmops.vm.VMInstanceVO;

@Local(value=StoragePoolAllocator.class)
public class LocalStoragePoolAllocator extends FirstFitStoragePoolAllocator {
    private static final Logger s_logger = Logger.getLogger(LocalStoragePoolAllocator.class);
    
    FirstFitAllocator _allocator;
    
    public boolean allocatorIsCorrectType(VMInstanceVO vm, ServiceOffering offering) {
    	return localStorageAllocationNeeded(vm, offering);
    }
    
    public StoragePool allocateToPool(ServiceOffering offering,
                                      DiskOfferingVO dataDiskOffering,
                                      DataCenterVO dc,
                                      HostPodVO pod,
                                      VMInstanceVO vm,
                                      VMTemplateVO template,
                                      DiskOfferingVO rootDiskOffering,
                                      Set<? extends StoragePool> avoid) {
    	
    	// Check that the allocator type is correct
        if (!allocatorIsCorrectType(vm, offering)) {
        	return null;
        }

        Set<StoragePool> myAvoids = new HashSet<StoragePool>();
        for (StoragePool pool : avoid) {
            myAvoids.add(pool); 
        }
        
        StoragePool pool = null;
        while ((pool = super.allocateToPool(offering, dataDiskOffering, dc, pod, vm, template, rootDiskOffering, myAvoids)) != null) {
            // This means this pool is local storage.  We better check the cpus and stuff to make sure it all works.
            Host host = _allocator.allocateTo(offering, rootDiskOffering, Host.Type.Routing, dc, pod, (StoragePoolVO)pool, template, new HashSet<Host>());
            if (host != null) {
                return pool;
            }
            s_logger.debug("Found pool " + pool.getId() + " but host doesn't fit.");
            
            myAvoids.add(pool);
        }
        
        s_logger.debug("Unable to find storage pool to fit the vm");
        return null;
        
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        
        _storageOverprovisioningFactor = NumbersUtil.parseInt((String) params.get("storage.overprovisioning.factor"), 1);
        _extraBytesPerVolume = NumbersUtil.parseLong((String) params.get("extra.bytes.per.volume"), 50 * 1024L * 1024L);
        
        _allocator = new FirstFitAllocator();
        _allocator.configure("FirstFit", params);
        return true;
    }
    
    public LocalStoragePoolAllocator() {
    }
}
