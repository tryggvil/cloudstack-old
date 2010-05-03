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
package com.vmops.storage.download;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.Listener;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.storage.DownloadCommand;
import com.vmops.agent.api.storage.DownloadProgressCommand;
import com.vmops.agent.api.storage.DownloadProgressCommand.RequestType;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateStorageResourceAssoc;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.VMTemplateStorageResourceAssoc.Status;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.utils.component.Inject;
import com.vmops.utils.exception.VmopsRuntimeException;



/**
 * @author chiradeep
 *
 */
@Local(value={DownloadMonitor.class})
public class DownloadMonitorImpl implements  DownloadMonitor {
    static final Logger s_logger = Logger.getLogger(DownloadMonitorImpl.class);
	
    @Inject
	private VMTemplateHostDao _vmTemplateHostDao;
    @Inject
    private VMTemplateDao _vmTemplateDao;
    @Inject
	VMTemplatePoolDao _vmTemplatePoolDao;
    @Inject
    StoragePoolHostDao _poolHostDao;


    @Inject
    HostDao _serverDao = null;
    @Inject
    private final DataCenterDao _dcDao = null;
    @Inject
    VMTemplateDao _templateDao =  null;
    @Inject
	private final EventDao _eventDao = null;
    @Inject
	private StoragePoolDao _storagePoolDao;

	
    @Inject
	private AgentManager _agentMgr;

	private String _name;

	private Timer _timer;

	private final Map<VMTemplateHostVO, DownloadListener> _listenerMap = new ConcurrentHashMap<VMTemplateHostVO, DownloadListener>();


	public long send(Long hostId, Command cmd, Listener listener) {
		return _agentMgr.gatherStats(hostId, cmd, listener);
	}

	public void logEvent(String evtType, String description, String level) {
		EventVO event = new EventVO();
		event.setUserId(1);
		event.setAccountId(1);
		event.setType(evtType);
		event.setDescription(description);
		event.setLevel(level);
		_eventDao.persist(event);
		
	}

	@Override
	public boolean configure(String name, Map<String, Object> params) {
		_name = name;
		/*
	    ComponentLocator locator = ComponentLocator.getCurrentLocator();
        _vmTemplateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_vmTemplateHostDao == null) {
        	s_logger.warn("Unable to get " + VMTemplateHostDao.class.getName());
        	return false;
        }
        _vmTemplateDao = locator.getDao(VMTemplateDao.class);
        if (_vmTemplateDao == null) {
            s_logger.warn("Unable to get " + VMTemplateDao.class.getName());
            return false;
        }
        _serverDao = locator.getDao(HostDao.class);
        if (_serverDao == null) {
        	s_logger.warn("Unable to get " + HostDao.class.getName());
        	return false;
        }
        _dcDao = locator.getDao(DataCenterDao.class);
        if (_dcDao == null) {
        	s_logger.warn("Unable to get " + DataCenterDao.class.getName());
        	return false;
        }
        _templateDao = locator.getDao(VMTemplateDao.class);
        if (_templateDao == null) {
        	s_logger.warn("Unable to get " + VMTemplateDao.class.getName());
        	return false;
        }
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
        	s_logger.warn("Unable to get " + StoragePoolDao.class.getName());
        	return false;
        }
        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
        	s_logger.warn("Unable to get " + EventDao.class.getName());
        	return false;
        }
        
        _vmTemplatePoolDao = locator.getDao(VMTemplatePoolDao.class);
        if ( _vmTemplatePoolDao == null) {
        	s_logger.warn("Unable to get " + VMTemplatePoolDao.class.getName());
        	return false;
        }
        _poolHostDao = locator.getDao(StoragePoolHostDao.class);
        if (_poolHostDao == null) {
        	s_logger.warn("Unable to get " + StoragePoolHostDao.class.getName());
        	return false;
        }
        _agentMgr = locator.getManager(AgentManager.class);
        
        */
        _agentMgr.registerForHostEvents(new DownloadListener(this), true, false);
		return true;
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public boolean start() {
		_timer = new Timer();
		return true;
	}

	@Override
	public boolean stop() {
		return true;
	}
	
	
	public boolean isTemplateUpdateable(Long templateId) {
		List<VMTemplateHostVO> downloadsInProgress =
			_vmTemplateHostDao.listByTemplateStatus(templateId, VMTemplateHostVO.Status.DOWNLOAD_IN_PROGRESS);
		return (downloadsInProgress.size() == 0);
	}
	
	
	public boolean isTemplateUpdateable(Long templateId, Long datacenterId) {
		List<VMTemplateHostVO> downloadsInProgress =
			_vmTemplateHostDao.listByTemplateStatus(templateId, datacenterId, VMTemplateHostVO.Status.DOWNLOAD_IN_PROGRESS);
		return (downloadsInProgress.size() == 0);
	}
	
	
	private void downloadTemplateToStorage(VMTemplateVO template, HostVO sserver) {
		boolean downloadJobExists = false;
        VMTemplateHostVO vmTemplateHost = null;

        vmTemplateHost = _vmTemplateHostDao.findByHostTemplate(sserver.getId(), template.getId());
        if (vmTemplateHost == null) {
            vmTemplateHost = new VMTemplateHostVO(sserver.getId(), template.getId(), new Date(), 0, VMTemplateStorageResourceAssoc.Status.NOT_DOWNLOADED, null, null, "jobid0000", null);
            _vmTemplateHostDao.persist(vmTemplateHost);
        } else if ((vmTemplateHost.getJobId() != null) && (vmTemplateHost.getJobId().length() > 2)) {
            downloadJobExists = true;
        }

		if(vmTemplateHost != null) {
		    start();
			DownloadCommand dcmd = new DownloadCommand(template);
			DownloadListener dl = new DownloadListener(sserver, template, _timer, _vmTemplateHostDao, vmTemplateHost.getId(), this, dcmd);
			if (downloadJobExists) {
				dcmd = new DownloadProgressCommand(dcmd, vmTemplateHost.getJobId(), RequestType.GET_OR_RESTART);
				dl.setCurrState(vmTemplateHost.getDownloadState());
	 		}

			_listenerMap.put(vmTemplateHost, dl);

			long result = send(sserver.getId(), dcmd, dl);
			if (result == -1) {
				s_logger.warn("Unable to start /resume download of template " + template.getUniqueName() + " to " + sserver.getName());
				dl.setDisconnected();
				dl.scheduleStatusCheck(RequestType.GET_OR_RESTART);
			}
		}
	}

	protected StoragePoolHostVO chooseHostForStoragePool(StoragePoolVO poolVO) {
		List<StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(poolVO.getId(), com.vmops.host.Status.Up);
		if (poolHosts != null && poolHosts.size() > 0) {
			Collections.shuffle(poolHosts);
			return poolHosts.get(0);
		}
		return null;
	}

	@Override
	public boolean downloadTemplateToStorage(Long templateId) {
		if (isTemplateUpdateable(templateId)) {
			List<DataCenterVO> dcs = _dcDao.listAll();

			for (DataCenterVO dc: dcs) {
				downloadTemplateToStorage(templateId, dc.getId());
			}
			return true;
		} else {
			return false;
		}
	}

	private void downloadTemplateToStorage(Long templateId, Long dataCenterId) {
		VMTemplateVO template = _templateDao.findById(templateId);
		if (template != null && (template.getUrl() != null)) {
			//find all storage hosts and tell them to initiate download
			List<HostVO> storageServers = _serverDao.listByTypeDataCenter(Host.Type.SecondaryStorage, dataCenterId);
			for (HostVO sserver: storageServers) {
				downloadTemplateToStorage(template, sserver);
			}
		}
		
	}

	public void handleDownloadEvent(HostVO host, VMTemplateVO template, Status dnldStatus) {
		perhapsSetTemplateReady(template.getId());
		if ((dnldStatus == VMTemplateStorageResourceAssoc.Status.DOWNLOADED) || (dnldStatus==Status.ABANDONED)){
			VMTemplateHostVO vmTemplateHost = new VMTemplateHostVO(host.getId(), template.getId());
			DownloadListener oldListener = _listenerMap.get(vmTemplateHost);
			if (oldListener != null) {
				_listenerMap.remove(vmTemplateHost);
			}
		}
		if (dnldStatus == VMTemplateStorageResourceAssoc.Status.DOWNLOADED) {
			logEvent(EventTypes.EVENT_TEMPLATE_DOWNLOAD_SUCCESS, template.getName() + " successfully downloaded to storage server " + host.getName(), EventVO.LEVEL_INFO);
		}
		if (dnldStatus == Status.DOWNLOAD_ERROR) {
			logEvent(EventTypes.EVENT_TEMPLATE_DOWNLOAD_FAILED, template.getName() + " failed to download to storage server " + host.getName(), EventVO.LEVEL_ERROR);
		}
		if (dnldStatus == Status.ABANDONED) {
			logEvent(EventTypes.EVENT_TEMPLATE_DOWNLOAD_FAILED, template.getName() + " :aborted download to storage server " + host.getName(), EventVO.LEVEL_WARN);
		}
		
		VMTemplateHostVO vmTemplateHost = _vmTemplateHostDao.findByHostTemplate(host.getId(), template.getId());
		
        // if the template is downloaded, change the template create_status to Created
        // if the template is abandoned or download_error, set the template create_status to Corrupted
        if (dnldStatus == Status.DOWNLOADED) {
            VMTemplateVO updatedTemplate = _vmTemplateDao.createForUpdate();
            updatedTemplate.setCreateStatus(AsyncInstanceCreateStatus.Created);
            _vmTemplateDao.update(template.getId(), updatedTemplate);
            long size = -1;
            if(vmTemplateHost!=null){
            	size = vmTemplateHost.getSize();
            }
            else{
            	s_logger.warn("Failed to get size for template" + template.getName());
            }
			String eventParams = "id=" + template.getId() + "\ndcId="+host.getDataCenterId()+"\nsize="+size;
            EventVO event = new EventVO();
            event.setUserId(1L);
            event.setAccountId(template.getAccountId());
            if((template.getFormat()).equals(ImageFormat.ISO)){
            	event.setType(EventTypes.EVENT_ISO_CREATE);
            	event.setDescription("Successfully created ISO " + template.getName());
            }
            else{
            	event.setType(EventTypes.EVENT_TEMPLATE_CREATE);
            	event.setDescription("Successfully created template " + template.getName());
            }
            event.setParameters(eventParams);
            event.setLevel(EventVO.LEVEL_INFO);
            _eventDao.persist(event);
        } else if ((dnldStatus == Status.ABANDONED) || (dnldStatus == Status.DOWNLOAD_ERROR)) {
            VMTemplateVO updatedTemplate = _vmTemplateDao.createForUpdate();
            updatedTemplate.setCreateStatus(AsyncInstanceCreateStatus.Corrupted);
            _vmTemplateDao.update(template.getId(), updatedTemplate);
        }
        
		if (vmTemplateHost != null) {
			Long poolId = vmTemplateHost.getPoolId();
			if (poolId != null) {
				VMTemplateStoragePoolVO vmTemplatePool = _vmTemplatePoolDao.findByPoolTemplate(poolId, template.getId());
				StoragePoolHostVO poolHost = _poolHostDao.findByPoolHost(poolId, host.getId());
				if (vmTemplatePool != null && poolHost != null) {
					vmTemplatePool.setDownloadPercent(vmTemplateHost.getDownloadPercent());
					vmTemplatePool.setDownloadState(vmTemplateHost.getDownloadState());
					vmTemplatePool.setErrorString(vmTemplateHost.getErrorString());
					String localPath = poolHost.getLocalPath();
					String installPath = vmTemplateHost.getInstallPath();
					if (installPath != null) {
						if (!installPath.startsWith("/")) {
							installPath = "/" + installPath;
						}
						if (!(localPath == null) && !installPath.startsWith(localPath)) {
							localPath = localPath.replaceAll("/\\p{Alnum}+/*$", ""); //remove instance if necessary
						}
						if (!(localPath == null) && installPath.startsWith(localPath)) {
							installPath = installPath.substring(localPath.length());
						}
					}
					vmTemplatePool.setInstallPath(installPath);
					vmTemplatePool.setLastUpdated(vmTemplateHost.getLastUpdated());
					vmTemplatePool.setJobId(vmTemplateHost.getJobId());
					vmTemplatePool.setLocalDownloadPath(vmTemplateHost.getLocalDownloadPath());
					_vmTemplatePoolDao.update(vmTemplatePool.getId(),vmTemplatePool);
				}
			}
		}

	}
	
	void perhapsSetTemplateReady(long templateId) {
		List<VMTemplateHostVO> templateHosts = _vmTemplateHostDao.listByTemplateStatus(templateId, Status.DOWNLOADED);
		List<VMTemplateStoragePoolVO> templatePools = _vmTemplatePoolDao.listByTemplateStatus(templateId, Status.DOWNLOADED);

		VMTemplateVO ub = _templateDao.createForUpdate();
		boolean ready=false;
		if ((templateHosts.size() > 0)||(templatePools.size() > 0)) {
			// we also update AsyncInstanceCreateStatus when setting template as ready
			ub.setCreateStatus(AsyncInstanceCreateStatus.Created);
			ready=true;
		}

		ub.setReady(ready);
		_templateDao.update(templateId, ub);
	}

	@Override
	public void handleTemplateSync(long sserverId, Map<String, TemplateInfo> templateInfo) {
		Set<VMTemplateVO> toBeDownloaded = new HashSet<VMTemplateVO>();
		List<VMTemplateVO> allPublicTemplates = _templateDao.listByPublic();
		VMTemplateVO rtngTmplt = _templateDao.findRoutingTemplate();

		if (rtngTmplt != null)
			allPublicTemplates.add(rtngTmplt);
		
		for (Iterator<VMTemplateVO> i = allPublicTemplates.iterator();i.hasNext();) {
			if (i.next().getName().startsWith("xs-tools")) {
				i.remove();
			}
		}
		
        toBeDownloaded.addAll(allPublicTemplates);
        
		for (VMTemplateVO tmplt: allPublicTemplates) {
			String uniqueName = tmplt.getUniqueName();
			VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(sserverId, tmplt.getId());
			if (templateInfo.containsKey(uniqueName)) {
				toBeDownloaded.remove(tmplt);
				if (tmpltHost != null) {
					s_logger.info("Template Sync found " + uniqueName + " already in the template host table");
                    if (tmpltHost.getDownloadState() != Status.DOWNLOADED) {
                    	tmpltHost.setErrorString("");
                    }
                    tmpltHost.setDownloadPercent(100);
                    tmpltHost.setDownloadState(Status.DOWNLOADED);
                    tmpltHost.setInstallPath(templateInfo.get(uniqueName).getInstallPath());
                    tmpltHost.setLastUpdated(new Date());
					_vmTemplateHostDao.update(tmpltHost.getId(), tmpltHost);
				} else {
					VMTemplateHostVO templtHost = new VMTemplateHostVO(sserverId, tmplt.getId(), new Date(), 100, Status.DOWNLOADED, null, null, null, templateInfo.get(uniqueName).getInstallPath());
					templtHost.setSize(templateInfo.get(uniqueName).getSize());
					_vmTemplateHostDao.persist(templtHost);
				}
				perhapsSetTemplateReady(tmplt.getId());
				templateInfo.remove(uniqueName);
				continue;
			}
			if (tmpltHost != null && tmpltHost.getDownloadState() != Status.DOWNLOADED) {
				s_logger.info("Template Sync did not find " + uniqueName + " ready on server " + sserverId + ", will request download shortly");

			} else {
				s_logger.info("Template Sync did not find " + uniqueName + " on the server " + sserverId + ", will request download shortly");
				VMTemplateHostVO templtHost = new VMTemplateHostVO(sserverId, tmplt.getId(), new Date(), 100, Status.NOT_DOWNLOADED, null, null, null, null);
				_vmTemplateHostDao.persist(templtHost);
			}
			perhapsSetTemplateReady(tmplt.getId());

		}
		
		if (toBeDownloaded.size()>0) {
			HostVO sserver = _serverDao.findById(sserverId);
			if (sserver == null) {
				throw new VmopsRuntimeException("Unable to find host from id");
			}
			for (VMTemplateVO tmplt: toBeDownloaded) {
				s_logger.debug("Template " + tmplt.getName() + " needs to be downloaded to " + sserver.getName());
				downloadTemplateToStorage(tmplt, sserver);
				perhapsSetTemplateReady(tmplt.getId());
			}
		}

	}

	@Override
	public void cancelAllDownloads(Long templateId) {
		List<VMTemplateHostVO> downloadsInProgress =
			_vmTemplateHostDao.listByTemplateStates(templateId, VMTemplateHostVO.Status.DOWNLOAD_IN_PROGRESS, VMTemplateHostVO.Status.NOT_DOWNLOADED);
		if (downloadsInProgress.size() > 0){
			for (VMTemplateHostVO vmthvo: downloadsInProgress) {
				DownloadListener dl = _listenerMap.get(vmthvo);
				if (dl != null) {
					dl.abandon();
					s_logger.info("Stopping download of template " + templateId + " to storage server " + vmthvo.getHostId());
				}
			}
		}
	}
	
}
	
