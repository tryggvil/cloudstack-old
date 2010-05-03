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
import java.util.Random;
import java.util.Set;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
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
import com.vmops.storage.StoragePoolHostVO;
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
import com.vmops.vm.dao.VMInstanceDao;

public abstract class AbstractStoragePoolAllocator implements StoragePoolAllocator {
	private static final Logger s_logger = Logger.getLogger(FirstFitStoragePoolAllocator.class);
    String _name;
    TemplateManager _tmpltMgr;
    StorageManager _storageMgr;
    AgentManager _agentMgr;
    StoragePoolDao _storagePoolDao;
    HostDao _hostDao;
    VMTemplateHostDao _templateHostDao;
    VMTemplatePoolDao _templatePoolDao;
    VMTemplateDao _templateDao;
    VolumeDao _volumeDao;
    StoragePoolHostDao _poolHostDao;
    ConfigurationDao _configDao;
    int _storageOverprovisioningFactor;
    long _extraBytesPerVolume = 0;
    UserVmDao _userVmDao;
    VMInstanceDao _vmInstanceDao;
    Random _rand;
    boolean _dontMatter;
    double _storageUsedThreshold = 1.0d;
    
    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
    
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        
        _tmpltMgr = locator.getManager(TemplateManager.class);
        if (_tmpltMgr == null) {
            throw new ConfigurationException("Unable to get " + TemplateManager.class.getName());
        }
        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
            throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
        }
        
        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
        	throw new ConfigurationException("Unable to get " + AgentManager.class.getName());
        }
        
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
            throw new ConfigurationException("Unable to get host dao: " + StoragePoolDao.class);
        }
        
        _templateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_templateHostDao == null) {
            throw new ConfigurationException("Unable to get template host dao: " + VMTemplateHostDao.class);
        }
        
        _templatePoolDao = locator.getDao(VMTemplatePoolDao.class);
        if (_templatePoolDao == null) {
            throw new ConfigurationException("Unable to get template pool dao: " + VMTemplatePoolDao.class);
        }
        
        _templateDao = locator.getDao(VMTemplateDao.class);
        if (_templateDao == null) {
            throw new ConfigurationException("Unable to get template dao: " + VMTemplateDao.class);
        }
        
        _poolHostDao = locator.getDao(StoragePoolHostDao.class);
        if (_poolHostDao == null) {
            throw new ConfigurationException("Unable to get pool host dao: " + StoragePoolHostDao.class);
        }
        
        _volumeDao = locator.getDao(VolumeDao.class);
        if (_volumeDao == null) {
            throw new ConfigurationException("Unable to get volume dao: " + VolumeDao.class);
        }
        
        _configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get configuration dao: " + ConfigurationDao.class);
        }
        
        _userVmDao = locator.getDao(UserVmDao.class);
        if (_userVmDao == null) {
        	throw new ConfigurationException("Unable to retrieve " + UserVmDao.class);
        }
        
        _vmInstanceDao = locator.getDao(VMInstanceDao.class);
        if (_vmInstanceDao == null) {
        	throw new ConfigurationException("Unable to retrieve " + VMInstanceDao.class);
        }
        
        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null){
        	throw new ConfigurationException("Unable to retrieve " + HostDao.class);
        }

        Map<String, String> configs = _configDao.mapByComponent("StorageAllocator");
        
        String globalStorageOverprovisioningFactor = configs.get("storage.overprovisioning.factor");
        _storageOverprovisioningFactor = NumbersUtil.parseInt(globalStorageOverprovisioningFactor, 2);
        
        _extraBytesPerVolume = 0;
        
        String storageUsedThreshold = configs.get("storage.capacity.threshold");
        if (storageUsedThreshold != null) {
            _storageUsedThreshold = Double.parseDouble(storageUsedThreshold);
        }

        _rand = new Random(System.currentTimeMillis());
        
        _dontMatter = Boolean.parseBoolean(configs.get("storage.overwrite.provisioning"));
        
        return true;
    }
    
    abstract boolean allocatorIsCorrectType(VMInstanceVO vm, ServiceOffering offering);
    
	protected boolean templateAvailable(long templateId, long poolId) {
    	VMTemplateStorageResourceAssoc thvo = _templatePoolDao.findByPoolTemplate(poolId, templateId);
    	if (thvo != null) {
    		if (s_logger.isDebugEnabled()) {
    			s_logger.debug("Template id : " + templateId + " status : " + thvo.getDownloadState().toString());
    		}
    		return (thvo.getDownloadState()==Status.DOWNLOADED);
    	} else {
    		return false;
    	}
    }
	
	protected boolean localStorageAllocationNeeded(VMInstanceVO vm, ServiceOffering offering) {
		if (vm == null) {
    		// We are finding a pool for a volume, so we need a shared storage allocator
    		return false;
    	} else if (vm.getType() == VirtualMachine.Type.User) {
    		// We are finding a pool for a UserVM, so check the service offering to see if we should use local storage
    		return offering.getUseLocalStorage();
    	} else {
    		// We are finding a pool for a DomR or ConsoleProxy, so check the configuration table to see if we should use local storage
    		String configValue = _configDao.getValue("system.vm.use.local.storage");
    		return Boolean.parseBoolean(configValue);
    	}
	}
	
	protected boolean poolIsCorrectType(StoragePool pool, VMInstanceVO vm, ServiceOffering offering) {
		boolean localStorageAllocationNeeded = localStorageAllocationNeeded(vm, offering);
		return ((!localStorageAllocationNeeded && pool.isShared()) || (localStorageAllocationNeeded && pool.isLocal()));
	}
	
	protected boolean checkPool(Set<? extends StoragePool> avoid, StoragePoolVO pool, VMTemplateVO template, List<VMTemplateStoragePoolVO> templatesInPool, ServiceOffering offering,
			VMInstanceVO vm, DiskOfferingVO diskOffering, DiskOfferingVO rootDiskOffering, StatsCollector sc) {
		if (avoid.contains(pool)) {
			return false;
		}

		// Check that the pool type is correct
		if (!poolIsCorrectType(pool, vm, offering)) {
			return false;
		}

		// check the used size against the total size, skip this host if it's greater than the configured
		// capacity check "storage.capacity.threshold"
		if (sc != null) {
			long totalSize = pool.getCapacityBytes();
			StorageStats stats = sc.getStorageStats(pool.getId());
			if (stats != null) {
				double usedPercentage = ((double)stats.getByteUsed() / (double)totalSize);
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Attempting to look for pool " + pool.getId() + " for storage, totalSize: " + pool.getCapacityBytes() + ", usedBytes: " + stats.getByteUsed() + ", usedPct: " + usedPercentage + ", threshold: " + _storageUsedThreshold);
				}
				if (usedPercentage >= _storageUsedThreshold) {
					return false;
				}
			}
		}

		List<VolumeVO> volumes = _volumeDao.findByPool(pool.getId());

		long totalAllocatedSize = 0L;
		for (VolumeVO volume : volumes) {
			totalAllocatedSize += volume.getSize() + _extraBytesPerVolume;
		}

		// Iterate through all templates on this storage pool
		boolean tmpinstalled = false;
		List<VMTemplateStoragePoolVO> templatePoolVOs;
		if (templatesInPool != null) {
			templatePoolVOs = templatesInPool; 
		} else {
			templatePoolVOs = _templatePoolDao.listByPoolId(pool.getId());
		}

		for (VMTemplateStoragePoolVO templatePoolVO : templatePoolVOs) {
			VMTemplateVO templateInPool = _templateDao.findById(templatePoolVO.getTemplateId());
			int templateSizeMultiplier = 2;

			if ((template != null) && !tmpinstalled && (templateInPool.getId() == template.getId())) {
				tmpinstalled = true;
				templateSizeMultiplier = 3;
			}
			
			s_logger.debug("For template: " + templateInPool.getName() + ", using template size multiplier: " + templateSizeMultiplier);

			long templateSize = templatePoolVO.getTemplateSize();
			totalAllocatedSize += templateSizeMultiplier * (templateSize + _extraBytesPerVolume);
		}

		if ((template != null) && !tmpinstalled) {
			// If the template that was passed into this allocator is not installed in the storage pool, 
			// add 3 * (template size on secondary storage) to the running total 
			HostVO secondaryStorageHost = _storageMgr.getSecondaryStorageHost(pool.getDataCenterId());
			if (secondaryStorageHost == null) {
				return false;
			} else {
				VMTemplateHostVO templateHostVO = _templateHostDao.findByHostTemplate(secondaryStorageHost.getId(), template.getId());
				if (templateHostVO == null) {
					return false;
				} else {
					s_logger.debug("For template: " + template.getName() + ", using template size multiplier: " + 3);
					long templateSize = templateHostVO.getSize();
					totalAllocatedSize += 3 * (templateSize + _extraBytesPerVolume);	
				}
			}
		}				

		long diskSize = diskOffering.getDiskSize();

		if (rootDiskOffering != null) {
			diskSize += rootDiskOffering.getDiskSize();
		}

		long askingSize = diskSize * 1024L * 1024L;

		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Attempting to look for pool " + pool.getId() + " for storage, maxSize : " + (pool.getCapacityBytes() * _storageOverprovisioningFactor) + ", totalSize : " + totalAllocatedSize + ", askingSize : " + askingSize);
		}

		if ((pool.getCapacityBytes() * _storageOverprovisioningFactor) < (totalAllocatedSize + askingSize)) {
			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Found pool " + pool.getId() + " for storage, maxSize : " + (pool.getCapacityBytes() * _storageOverprovisioningFactor) + ", totalSize : " + totalAllocatedSize + ", askingSize : " + askingSize);
			}

			return false;
		}

		return true;
	}
	
	@Override
	public String chooseStorageIp(VirtualMachine vm, Host host, Host storage) {
		return storage.getStorageIpAddress();
	}
	
}
