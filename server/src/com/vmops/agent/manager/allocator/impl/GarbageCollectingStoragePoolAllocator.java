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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.configuration.ConfigurationManager;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.vm.VMInstanceVO;

@Local(value=StoragePoolAllocator.class)
public class GarbageCollectingStoragePoolAllocator extends AbstractStoragePoolAllocator {
    private static final Logger s_logger = Logger.getLogger(LocalStoragePoolAllocator.class);
    
    StoragePoolAllocator _firstFitStoragePoolAllocator;
    StoragePoolAllocator _localStoragePoolAllocator;
    StorageManager _storageMgr;
    ConfigurationDao _configDao;
    boolean _storagePoolCleanupEnabled;
    
    public boolean allocatorIsCorrectType(VMInstanceVO vm, ServiceOffering offering) {
    	return true;
    }
    
    public Integer getStorageOverprovisioningFactor() {
    	return null;
    }
    
    public Long getExtraBytesPerVolume() {
    	return null;
    }
    
    public StoragePool allocateToPool(ServiceOffering offering,
                                      DiskOfferingVO dataDiskOffering,
                                      DataCenterVO dc,
                                      HostPodVO pod,
                                      VMInstanceVO vm,
                                      VMTemplateVO template,
                                      DiskOfferingVO rootDiskOffering,
                                      Set<? extends StoragePool> avoid) {
    	
    	if (!_storagePoolCleanupEnabled) {
    		s_logger.debug("Storage pool cleanup is not enabled, so GarbageCollectingStoragePoolAllocator is being skipped.");
    		return null;
    	}
    	
    	// Clean up all storage pools
    	_storageMgr.cleanupStoragePools(false);
    	
    	// Determine what allocator to use
    	StoragePoolAllocator allocator;
    	if (localStorageAllocationNeeded(vm, offering)) {
    		allocator = _localStoragePoolAllocator;
    	} else {
    		allocator = _firstFitStoragePoolAllocator;
    	}

    	// Try to find a storage pool after cleanup
        Set<StoragePool> myAvoids = new HashSet<StoragePool>();
        for (StoragePool pool : avoid) {
            myAvoids.add(pool); 
        }
        
        return allocator.allocateToPool(offering, dataDiskOffering, dc, pod, vm, template, rootDiskOffering, myAvoids);        
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        
        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        
        _firstFitStoragePoolAllocator = new FirstFitStoragePoolAllocator();
        _firstFitStoragePoolAllocator.configure("GCFirstFitStoragePoolAllocator", params);
        _localStoragePoolAllocator = new LocalStoragePoolAllocator();
        _localStoragePoolAllocator.configure("GCLocalStoragePoolAllocator", params);
        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
        	throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
        }
        
        _configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }
        
        String storagePoolCleanupEnabled = _configDao.getValue("storage.pool.cleanup.enabled");
        _storagePoolCleanupEnabled = (storagePoolCleanupEnabled == null) ? true : Boolean.parseBoolean(storagePoolCleanupEnabled);
        
        return true;
    }
    
    public GarbageCollectingStoragePoolAllocator() {
    }
    
}
