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
package com.vmops.template;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.storage.DeleteTemplateCommand;
import com.vmops.agent.api.storage.DownloadAnswer;
import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
import com.vmops.agent.api.storage.PrimaryStorageDownloadCommand;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.InternalErrorException;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.Storage.FileSystem;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.VMTemplateStorageResourceAssoc.Status;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.download.DownloadMonitor;
import com.vmops.user.Account;
import com.vmops.user.AccountManager;
import com.vmops.user.UserAccount;
import com.vmops.user.UserVO;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserAccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.vm.dao.VMInstanceDao;
import com.vmops.vm.VMInstanceVO;

@Local(value=TemplateManager.class)
public class TemplateManagerImpl implements TemplateManager {
    private final static Logger s_logger = Logger.getLogger(TemplateManagerImpl.class);
    String _name;
    VMTemplateDao _tmpltDao;
    VMTemplateHostDao _tmpltHostDao;
    VMTemplatePoolDao _tmpltPoolDao;
    VMInstanceDao _vmInstanceDao;
    StoragePoolDao _poolDao;
    StoragePoolHostDao _poolHostDao;
    EventDao _eventDao;
    DownloadMonitor _downloadMonitor;
    UserAccountDao _userAccountDao;
    AccountDao _accountDao;
    UserDao _userDao;
    AgentManager _agentMgr;
    AccountManager _accountMgr;
    HostDao _hostDao;
    protected SearchBuilder<VMTemplateHostVO> HostTemplateStatesSearch;
    

    @Override
    public Long create(long userId, String displayText, boolean isPublic, ImageFormat format, FileSystem fs, URI url, String chksum, boolean requiresHvm, int bits, boolean enablePassword, long guestOSId, boolean bootable) {
        Long id = _tmpltDao.getNextInSequence(Long.class, "id");
        
        UserVO user = _userDao.findById(userId);
        long accountId = user.getAccountId();
        
        VMTemplateVO template = new VMTemplateVO(id, displayText, format, isPublic, fs, url.toString(), requiresHvm, bits, accountId, chksum, displayText, enablePassword, guestOSId, bootable);
        
        Long templateId = _tmpltDao.persist(template);
        UserAccount userAccount = _userAccountDao.findById(userId);
        saveEvent(userId, userAccount.getAccountId(), userAccount.getDomainId(), EventTypes.EVENT_TEMPLATE_DOWNLOAD_START,
                  "Started download of template:  " + template.getName());
        
        _downloadMonitor.downloadTemplateToStorage(templateId);
        
        _accountMgr.incrementResourceCount(userAccount.getAccountId(), ResourceType.template);
        
        return templateId;
    }
    
    @Override @DB
    public VMTemplateStoragePoolVO prepareTemplateForCreate(VMTemplateVO template, StoragePoolVO pool) {
    	// If template is not ready, return failure
    	template = _tmpltDao.findById(template.getId(), true);
    	if (!template.isReady()) {
    		throw new VmopsRuntimeException("Attempting to deploy a VM from a template that is not ready.");
    	}
    	
        long poolId = pool.getId();
        long templateId = template.getId();
        VMTemplateStoragePoolVO templateStoragePoolRef = null;
        VMTemplateHostVO templateHostRef = null;
        long templateStoragePoolRefId; 
        String origUrl = null;
        
        synchronized ( this ) {
	        templateStoragePoolRef = _tmpltPoolDao.findByPoolTemplate(poolId, templateId);
	        if (templateStoragePoolRef != null) {
	        	templateStoragePoolRef.setMarkedForGC(false);
	            _tmpltPoolDao.update(templateStoragePoolRef.getId(), templateStoragePoolRef);
	            
	            if (templateStoragePoolRef.getDownloadState() == Status.DOWNLOADED) {
		            if (s_logger.isDebugEnabled()) {
		                s_logger.debug("Template " + templateId + " has already been downloaded to pool " + poolId);
		            }
		            
		            return templateStoragePoolRef;
		        }
	        }
	        
	        SearchCriteria sc = HostTemplateStatesSearch.create();
	        sc.setParameters("id", templateId);
	        sc.setParameters("state", Status.DOWNLOADED);
	        sc.setJoinParameters("host", "dcId", pool.getDataCenterId());
	        List<VMTemplateHostVO> templateHostRefs = _tmpltHostDao.search(sc, null);
	        
	        if (templateHostRefs.size() == 0) {
	            s_logger.debug("Unable to find a secondary storage host who has completely downloaded the template.");
	            return null;
	        }

	        templateHostRef = _tmpltHostDao.acquire(templateHostRefs.get(0).getId(), 20 * 60);
	        
	        if (templateHostRef == null) {
	        	s_logger.debug("Unable to acquire templateHostVO lock when deploying new VM for template: " + template.getName());
	        	return null;
	        }
	        
	        HostVO sh = _hostDao.findById(templateHostRef.getHostId());
	        origUrl = sh.getStorageUrl();
	        if (origUrl == null) {
	            s_logger.debug("Unable to find the orig.url from host " + sh.toString());
	            return null;
	        }
	        
	        if (templateStoragePoolRef == null) {
	            if (s_logger.isDebugEnabled()) {
	                s_logger.debug("Downloading template " + templateId + " to pool " + poolId);
	            }
	            templateStoragePoolRef = new VMTemplateStoragePoolVO(poolId, templateId);
	            try {
	                templateStoragePoolRefId = _tmpltPoolDao.persist(templateStoragePoolRef);
	            } catch (Exception e) {
	                s_logger.debug("Assuming we're in a race condition: " + e.getMessage());
	                templateStoragePoolRef = _tmpltPoolDao.findByPoolTemplate(poolId, templateId);
	                if (templateStoragePoolRef == null) {
	                    throw new VmopsRuntimeException("Unable to persist a reference for pool " + poolId + " and template " + templateId);
	                }
	                templateStoragePoolRefId = templateStoragePoolRef.getId();
	            }
	        } else {
	            templateStoragePoolRefId = templateStoragePoolRef.getId();
	        }
        }
        
        try {
            if (templateStoragePoolRef.getDownloadState() == Status.DOWNLOADED) {
                return templateStoragePoolRef;
            }
            String url = origUrl + "/" + templateHostRef.getInstallPath();
            PrimaryStorageDownloadCommand dcmd = new PrimaryStorageDownloadCommand(template.getUniqueName(), url, template.getFormat(), template.getAccountId(), pool.getId(), pool.getUuid());
            
            List<StoragePoolHostVO> vos = _poolHostDao.listByPoolId(poolId);
            for (StoragePoolHostVO vo : vos) {
            	dcmd.setLocalPath(vo.getLocalPath());
                DownloadAnswer answer = (DownloadAnswer)_agentMgr.easySend(vo.getHostId(), dcmd);
                if (answer != null) {
                	try {
                		templateStoragePoolRef = _tmpltPoolDao.acquire(templateStoragePoolRefId, 1200);

                		if (templateStoragePoolRef == null) {
                			throw new VmopsRuntimeException("Unable to acquire lock on VMTemplateStoragePool: " + templateStoragePoolRefId);
                		}

                		templateStoragePoolRef.setDownloadPercent(templateStoragePoolRef.getDownloadPercent());
                		templateStoragePoolRef.setDownloadState(answer.getDownloadStatus());
                		templateStoragePoolRef.setLocalDownloadPath(answer.getDownloadPath());
                		templateStoragePoolRef.setInstallPath(answer.getInstallPath());
                		templateStoragePoolRef.setTemplateSize(answer.getTemplateSize());
                		_tmpltPoolDao.update(templateStoragePoolRef.getId(), templateStoragePoolRef);
                		if (s_logger.isDebugEnabled()) {
                			s_logger.debug("Template " + templateId + " is downloaded via " + vo.getHostId());
                		}
                		return templateStoragePoolRef;
                	} finally {
                		_tmpltPoolDao.release(templateStoragePoolRef.getId());
                	}
                }
            }
            
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Template " + templateId + " is not found on and can not be downloaded to pool " + poolId);
            }
            return null;
        } finally {
            _tmpltHostDao.release(templateHostRef.getId());
        }
    }
    
    @Override
    public boolean copy(long templateId, long zoneId) {
    	return true;
    }
    
    @Override 
    public synchronized boolean delete(long templateId, Long zoneId) throws InternalErrorException {
    	boolean success = true;
    	VMTemplateVO template = _tmpltDao.findById(templateId);

    	if (template == null || template.getRemoved() != null) {
    		throw new InternalErrorException("Please specify a valid template.");
    	}
    	
    	// Mark the template as not ready
    	template.setReady(false);
    	_tmpltDao.update(template.getId(), template);
    	
		List<HostVO> secondaryStorageHosts;
		if (!template.isPublicTemplate() && zoneId != null) {
			secondaryStorageHosts = new ArrayList<HostVO>();
			secondaryStorageHosts.add(_hostDao.findSecondaryStorageHost(zoneId));
		} else {
			secondaryStorageHosts = _hostDao.listSecondaryStorageHosts();
		}
		
		// Make sure the template is downloaded to all the necessary secondary storage hosts
		for (HostVO secondaryStorageHost : secondaryStorageHosts) {
			long hostId = secondaryStorageHost.getId();
			List<VMTemplateHostVO> templateHostVOs = _tmpltHostDao.listByHostTemplate(hostId, templateId);
			for (VMTemplateHostVO templateHostVO : templateHostVOs) {
				if (templateHostVO.getDownloadState() != Status.DOWNLOADED) {
					String errorMsg = "Please specify a template that is downloaded to ";
					if (zoneId != null) {
						errorMsg += "the secondary storage server: " + secondaryStorageHost.getName() + ".";
					} else {
						errorMsg += "all secondary storage servers.";
					}
					
					throw new InternalErrorException(errorMsg);
					
					// Release flag
				} 
			}
		}
		
		// Iterate through all necessary secondary storage hosts and delete the template from those hosts
		for (HostVO secondaryStorageHost : secondaryStorageHosts) {
			long hostId = secondaryStorageHost.getId();
			List<VMTemplateHostVO> templateHostVOs = _tmpltHostDao.listByHostTemplate(hostId, templateId);
			for (VMTemplateHostVO templateHostVO : templateHostVOs) {
				long templateHostId = templateHostVO.getId();
				VMTemplateHostVO lock = _tmpltHostDao.acquire(templateHostId);
				
				if (lock == null) {
					s_logger.debug("Failed to acquire lock for template host VO with ID: " + templateHostId);
					continue;
				}
				
				try {
					Answer answer = _agentMgr.easySend(hostId, new DeleteTemplateCommand(templateHostVO.getInstallPath()));
					if (answer != null && answer.getResult()) {
						_tmpltHostDao.remove(templateHostId);
					} else {
						success = false;
						break;
					}
				} finally {
					_tmpltHostDao.release(templateHostId);
				}
			}
			
			if (!success) {
				break;
			}
		}

		boolean templateReady = true;
		
		// If there are no more template host entries for this template, delete it
		if (success && (_tmpltHostDao.listByTemplateId(templateId).size() == 0)) {
			long accountId = template.getAccountId();
			if (_tmpltDao.remove(templateId)) {
				// Decrement the number of templates
				_accountMgr.decrementResourceCount(accountId, ResourceType.template);
				
				templateReady = false;
			}
		}
		
		if (templateReady) {
			template.setReady(true);
			_tmpltDao.update(template.getId(), template);
		}
		
		return success;
    }
    
    public List<VMTemplateStoragePoolVO> getUnusedTemplatesInPool(StoragePoolVO pool) {
		List<VMTemplateStoragePoolVO> unusedTemplatesInPool = new ArrayList<VMTemplateStoragePoolVO>();
		List<VMTemplateStoragePoolVO> allTemplatesInPool = _tmpltPoolDao.listByPoolId(pool.getId());
		
		for (VMTemplateStoragePoolVO templatePoolVO : allTemplatesInPool) {
			VMTemplateVO template = _tmpltDao.findById(templatePoolVO.getTemplateId());
			
			// If this is a routing template, consider it in use
			if (template.getUniqueName().equals("routing")) {
				continue;
			}
			
			// If the template is not yet downloaded to the pool, consider it in use
			if (templatePoolVO.getDownloadState() != Status.DOWNLOADED) {
				continue;
			}

			List<VMInstanceVO> vmInstances;
			if (template.getFormat() == ImageFormat.ISO) {
				vmInstances = _vmInstanceDao.listByPoolAndISOActive(pool.getId(), template.getId());		
			} else {
				vmInstances = _vmInstanceDao.listByPoolAndTemplateActive(pool.getId(), template.getId());
			}

			// If no VMs are using the template/ISO, consider it unused
			if (vmInstances.isEmpty()) {
				unusedTemplatesInPool.add(templatePoolVO);
			}
		}
		
		return unusedTemplatesInPool;
	}
    
    public void evictTemplateFromStoragePool(VMTemplateStoragePoolVO templatePoolVO) {
		StoragePoolVO pool = _poolDao.findById(templatePoolVO.getPoolId());
		VMTemplateVO template = _tmpltDao.findById(templatePoolVO.getTemplateId());
		
		long hostId;
		List<StoragePoolHostVO> poolHostVOs = _poolHostDao.listByPoolId(pool.getId());
		if (poolHostVOs.isEmpty()) {
			return;
		} else {
			hostId = poolHostVOs.get(0).getHostId();
		}
		
		final ManageVolumeCommand cmd = new ManageVolumeCommand(false, templatePoolVO.getTemplateSize(), pool.getUuid(), templatePoolVO.getInstallPath(), template.getName(), template.getName(), pool);
		ManageVolumeAnswer answer = (ManageVolumeAnswer) _agentMgr.easySend(hostId, cmd);
    	
    	if (answer != null && answer.getResult()) {
    		// Remove the templatePoolVO
    		if (_tmpltPoolDao.remove(templatePoolVO.getId())) {
    			s_logger.debug("Successfully evicted template: " + template.getName() + " from storage pool: " + pool.getName());
    		}
    	}
	}
    
    private Long saveEvent(Long userId, Long accountId, Long domainId, String type, String description) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setDescription(description);
        return _eventDao.persist(event);
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
        _tmpltDao = locator.getDao(VMTemplateDao.class);
        if (_tmpltDao == null) {
            throw new ConfigurationException("Unable to find VMTemplateDao");
        }
        
        _tmpltHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_tmpltHostDao == null) {
            throw new ConfigurationException("Unable to find VMTemplateHostDao");
        }
        
        _tmpltPoolDao = locator.getDao(VMTemplatePoolDao.class);
        if (_tmpltPoolDao == null) {
            throw new ConfigurationException("Unable to find VMTemplatePoolDao");
        }
        
        _vmInstanceDao = locator.getDao(VMInstanceDao.class);
        if (_vmInstanceDao == null) {
            throw new ConfigurationException("Unable to find VMInstanceDao");
        }
        
        _poolHostDao = locator.getDao(StoragePoolHostDao.class);
        if (_poolHostDao == null) {
            throw new ConfigurationException("Unable to find StoragePoolDao");
        }
        
        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
            throw new ConfigurationException("Unable to find EventDao");
        }
        
        _poolDao = locator.getDao(StoragePoolDao.class);
        if (_poolDao == null) {
            throw new ConfigurationException("Unable to find StoragePoolDao");
        }
        
        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to find HostDao");
        }
        
        _downloadMonitor = locator.getManager(DownloadMonitor.class);
        if (_downloadMonitor == null) {
            throw new ConfigurationException("Unable to find DownloadMonitor");
        }
        
        _userAccountDao = locator.getDao(UserAccountDao.class);
        if (_userAccountDao == null) {
            throw new ConfigurationException("Unable to find UserAccountDao");
        }
        
        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to find AccountDao");
        }
        
        _userDao = locator.getDao(UserDao.class);
        if (_userDao == null) {
            throw new ConfigurationException("Unable to find UserDao");
        }
        
        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
            throw new ConfigurationException("Unable to find AgentManager");
        }
        
        _accountMgr = locator.getManager(AccountManager.class);
        if (_accountMgr == null) {
            throw new ConfigurationException("Unable to find AccountManager");
        }
        
        HostTemplateStatesSearch = _tmpltHostDao.createSearchBuilder();
        HostTemplateStatesSearch.addAnd("id", HostTemplateStatesSearch.entity().getTemplateId(), SearchCriteria.Op.EQ);
        HostTemplateStatesSearch.addAnd("state", HostTemplateStatesSearch.entity().getDownloadState(), SearchCriteria.Op.EQ);
        
        SearchBuilder<HostVO> HostSearch = _hostDao.createSearchBuilder();
        HostSearch.addAnd("dcId", HostSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        
        HostTemplateStatesSearch.join("host", HostSearch, HostSearch.entity().getId(), HostTemplateStatesSearch.entity().getHostId());
        HostSearch.done();
        HostTemplateStatesSearch.done();
        
        return false;
    }
    
    protected TemplateManagerImpl() {
    }
}
