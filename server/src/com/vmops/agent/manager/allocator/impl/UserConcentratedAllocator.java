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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.manager.allocator.PodAllocator;
import com.vmops.capacity.CapacityVO;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.VirtualMachineTemplate;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.GlobalLock;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.dao.UserVmDao;

@Local(value=PodAllocator.class)
public class UserConcentratedAllocator implements PodAllocator {
    private final static Logger s_logger = Logger.getLogger(UserConcentratedAllocator.class);
    
    String _name;
    UserVmDao _vmDao;
    VolumeDao _volumeDao;
    HostPodDao _podDao;
    VMTemplateHostDao _templateHostDao;
    VMTemplatePoolDao _templatePoolDao;
    Random _rand = new Random(System.currentTimeMillis());
    CapacityDao _capacityDao;
    private final GlobalLock m_capacityCheckLock = GlobalLock.getInternLock("capacity.check");

    @Override
    public HostPodVO allocateTo(VirtualMachineTemplate template, ServiceOfferingVO offering, DataCenterVO dc, long accountId, Set<Long> avoids) {
        long dcId = dc.getId();
        
        // If there are existing VMs for the account in the specified zone, try to allocate the new VM/volume in the same pod as one of the VMs
        List<UserVmVO> vms = _vmDao.listByAccountAndDataCenter(accountId, dcId);
        if (vms.size() != 0) {
            s_logger.debug("Found previous vms for the user.  Will attempt to allocate to the same pod.");
        	for (UserVmVO uvvo: vms) {
        		long podId = uvvo.getPodId();
        		if (!avoids.contains(podId)) {
        			if (template != null && !templateAvailableInPod(template.getId(), dcId, podId)) {
        				continue;
        			}
        			
        		    s_logger.debug("Selected pod: " + podId);
        			return _podDao.findById(podId);
        		}
        	}
        }
        
        // If there are existing volumes for the account in the specified zone, try to allocate the new VM/volume in the same pod as one of the volumes
        List<VolumeVO> volumes = _volumeDao.findByAccountAndDataCenter(accountId, dcId);
        if (volumes.size() != 0) {
        	s_logger.debug("Found previous volumes for the user. Will attempt to allocate to the same pod.");
        	for (VolumeVO volume : volumes) {
        		Long podId = volume.getPodId();
        		if (podId != null && !avoids.contains(podId)) {
        			if (template != null && !templateAvailableInPod(template.getId(), dcId, podId)) {
        				continue;
        			}
        			
        			s_logger.debug("Selected pod: " + podId);
        			return _podDao.findById(podId);
        		}
        	}
        }
        
        // There were no existing VMs or volumes, so try to allocate the new VM/volume to any pod that has enough capacity
        List<HostPodVO> pods = _podDao.listByDataCenterId(dcId);
        if (pods.size() == 0) {
            s_logger.debug("No pods found in dc " + dcId);
            return null;
        }
        
        List<HostPodVO> availablePods = new ArrayList<HostPodVO>();
        for (HostPodVO pod: pods) {
            long podId = pod.getId();
        	if (!avoids.contains(podId)) {
        		if (template != null && !templateAvailableInPod(template.getId(), pod.getDataCenterId(), podId)) {
        			continue;
        		}
        		
        		if (offering != null) {
        			// test for enough memory in the pod (make sure to check for enough memory for the service offering, plus some extra padding for xen overhead
        			boolean enoughCapacity = dataCenterAndPodHasEnoughCapacity(dcId, podId, (offering.getRamSize()) * 1024L * 1024L, CapacityVO.CAPACITY_TYPE_MEMORY);

        			if (!enoughCapacity) {
        				if (s_logger.isDebugEnabled()) {
        					s_logger.debug("Not enough RAM available in zone/pod to allocate storage for user VM (zone: " + dcId + ", pod: " + podId + ")");
        				}
        				continue;
        			}

        			// test for enough CPU in the pod
        			enoughCapacity = dataCenterAndPodHasEnoughCapacity(dcId, podId, (offering.getCpu()*offering.getSpeed()), CapacityVO.CAPACITY_TYPE_CPU);
        			if (!enoughCapacity) {
        				if (s_logger.isDebugEnabled()) {
        					s_logger.debug("Not enough cpu available in zone/pod to allocate storage for user VM (zone: " + dcId + ", pod: " + podId + ")");
        				}
        				continue;
        			}
        		}

        		availablePods.add(pod);
        	}
        }
        
        if (availablePods.size() == 0) {
            s_logger.debug("There are no available pods.");
            return null;
        }
        
        int next = _rand.nextInt(availablePods.size());
        HostPodVO pod =  availablePods.get(next);
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Found pod " + pod.getName());
        }
        return pod;
    }

    private boolean dataCenterAndPodHasEnoughCapacity(long dataCenterId, long podId, long capacityNeeded, short capacityType) {
        if (m_capacityCheckLock.lock(10)) { // ten second timeout
            try {
                SearchCriteria sc = _capacityDao.createSearchCriteria();
                sc.addAnd("capacityType", SearchCriteria.Op.EQ, capacityType);
                sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, dataCenterId);
                sc.addAnd("podId", SearchCriteria.Op.EQ, podId);
                List<CapacityVO> capacities = _capacityDao.search(sc, null);
                boolean enoughCapacity = false;
                if (capacities != null) {
                    for (CapacityVO capacity : capacities) {
                        if ((capacity.getTotalCapacity() - capacity.getUsedCapacity()) >= capacityNeeded) {
                            enoughCapacity = true;
                            break;
                        }
                    }
                }
                return enoughCapacity;
            } finally {
                m_capacityCheckLock.unlock();
            }
        } else {
            // If we can't lock the table, just return that there is enough capacity and allow instance creation to fail on the agent
            // if there is not enough capacity.  All that does is skip the optimization of checking for capacity before sending the
            // command to the agent.
            return true;
        }
    }

    private boolean templateAvailableInPod(long templateId, long dcId, long podId) {
        return true;
        /*
    	List<VMTemplateHostVO> thvoList = _templateHostDao.listByTemplateStatus(templateId, dcId, podId, Status.DOWNLOADED);
    	List<VMTemplateStoragePoolVO> tpvoList = _templatePoolDao.listByTemplateStatus(templateId, dcId, podId, Status.DOWNLOADED);

    	if (thvoList != null && thvoList.size() > 0) {
    		if (s_logger.isDebugEnabled()) {
    			s_logger.debug("Found " + thvoList.size() + " storage hosts in pod " + podId + " with template " + templateId);
    		}
    		return true;
    	} else  if (tpvoList != null && tpvoList.size() > 0) {
    		if (s_logger.isDebugEnabled()) {
    			s_logger.debug("Found " + tpvoList.size() + " storage pools in pod " + podId + " with template " + templateId);
    		}
    		return true;
    	}else {
    		return false;
    	}
    	*/
    }

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
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;
        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("Unable to find UserVMDao.");
        }
        
        _volumeDao = locator.getDao(VolumeDao.class);
        if (_volumeDao == null) {
        	throw new ConfigurationException("Unable to find VolumeDao.");
        }
        
        _templateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_templateHostDao == null) {
            throw new ConfigurationException("Unable to get template host dao.");
        }
        
        _templatePoolDao = locator.getDao(VMTemplatePoolDao.class);
        if (_templatePoolDao == null) {
            throw new ConfigurationException("Unable to get template pool dao.");
        }
        
        _podDao = locator.getDao(HostPodDao.class);
        if (_podDao == null) {
            throw new ConfigurationException("Unable to find HostPodDao.");
        }
        
        _capacityDao = locator.getDao(CapacityDao.class);
        if (_capacityDao == null) {
            throw new ConfigurationException("Unable to retrieve " + CapacityDao.class);
        }
        
        return true;
    }
}
