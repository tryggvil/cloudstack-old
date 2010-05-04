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
package com.vmops.storage.monitor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.MirrorAnswer;
import com.vmops.agent.api.MirrorCommand;
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.Host.Type;
import com.vmops.host.dao.HostDao;
import com.vmops.network.NetworkManager;
import com.vmops.server.ManagementServer;
import com.vmops.service.ServiceOffering;
import com.vmops.service.dao.ServiceOfferingDao;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.MirrorState;
import com.vmops.storage.dao.DiskOfferingDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.Account;
import com.vmops.user.dao.AccountDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.State;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;

/**
 * @author chiradeep
 *
 */
@Local(value={StorageHostMonitor.class})
public class StorageHostMonitorImpl implements StorageHostMonitor, Runnable{
	private static final Logger s_logger = Logger.getLogger(StorageHostMonitorImpl.class);
	
	private static final int DEFAULT_CHECK_INTERVAL = 30;
	private static final int DEFAULT_DEAD_INTERVAL = 1*60;
	private static final int DEFAULT_MAX_RETRIES = 5;
	
    String _name;
    boolean _stopped;
    VMInstanceDao _instanceDao;
    DomainRouterDao _routerDao;
    HostDao _hostDao;
    UserVmDao _vmDao;
    VolumeDao _volumeDao;
    ServiceOfferingDao _serviceOfferingDao;
    DiskOfferingDao _diskOfferingDao;
    VMTemplateDao _templatesDao;
    DataCenterDao _dataCenterDao;
    HostPodDao _hostPodDao;
	VMTemplateHostDao _vmTemplateHostDao;
	EventDao _eventDao;
	AccountDao _accountDao;
	VMInstanceDao _vmInstanceDao;

    Thread _thread;

    AgentManager _agentMgr;
    UserVmManager _vmMgr;
    NetworkManager _networkMgr;
    StorageManager _storageMgr;
    int _maxRetries;
    long _checkInterval;
    long _deadInterval;
    Timer _timer;

    @Override @DB
	public void failoverVolumes(Host fromHost) {
    	//find active mirrors on this host and find alternate hosts for them.
    	DataCenterVO dc = _dataCenterDao.findById(fromHost.getDataCenterId());
    	HostPodVO pod = _hostPodDao.findById(fromHost.getPodId());
        VMTemplateVO routingTmplt = _templatesDao.findRoutingTemplate();
    	List<VolumeVO> deadVolumes = _volumeDao.findByHostAndMirrorState(fromHost.getId(), Volume.MirrorState.ACTIVE);
    	//TODO: this query may not scale if the storage host is very large and has thousands of volumes.
    	 
    	Set<Long> vmIds = new HashSet<Long>();
    	Set<Long> failedVms = new HashSet<Long>();

    	Transaction txn = Transaction.currentTxn();
		for (VolumeVO vol: deadVolumes) {
			if (vmIds.contains(vol.getInstanceId())) {
				continue; //we've already dealt with this one.
			}
			if (failedVms.contains(vol.getInstanceId())) {
				continue; //already dealt with this one
			}

			UserVmVO userVM = _vmDao.findById(vol.getInstanceId());
            DiskOfferingVO diskOffering = null;
			ServiceOffering so = null;
            if (vol.getDiskOfferingId() != null) {
                diskOffering = _diskOfferingDao.findById(vol.getDiskOfferingId());
            }
            if (userVM != null) {
                so = _serviceOfferingDao.findById(Long.valueOf(userVM.getServiceOfferingId()));
            }
			VMTemplateVO template = null;
			if (vol.getTemplateName() != null) {
				template = _templatesDao.findByName(vol.getTemplateName());
			}
			VMInstanceVO vm = _vmInstanceDao.findById(vol.getInstanceId());

			Set<Host> avoid = new HashSet<Host>();
			avoid.add(fromHost);
			VolumeVO mirrorVol = null;
			if (vol.getMirrorVolume() != null) {
				mirrorVol = _volumeDao.findById(vol.getMirrorVolume());
				if (mirrorVol != null) {
					Host mirrorHost = _hostDao.findById(mirrorVol.getHostId());
					avoid.add(mirrorHost);
				}
			}
			if (mirrorVol == null) {
				//No point continuing with the mirroring process as both copies are gone.
				s_logger.warn("StorageHostMonitor: both mirrors in mirror set for VM " + vm.getName() + " " + vol.getVolumeType().toString() + " have failed");
				failedVms.add(vm.getId());
				continue;
			}
			Host alternateHost = null;
			int numRetry = _maxRetries;
			CreateAnswer answer = null;

			while ((alternateHost = _agentMgr.findHost(Host.Type.Storage, dc, pod, null, so, diskOffering, template, null, null, avoid)) !=null && (numRetry > 0)) {
				numRetry--;
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Attempting to create volume for  + " + vol.getFolder() + " on " + alternateHost.getName());
				}
				String path = vol.getFolder();
				CreateCommand cmdCreate = null;
				if (vm.getType() == VirtualMachine.Type.DomainRouter) {
    				VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(alternateHost.getId(), routingTmplt.getId());
					cmdCreate = new CreateCommand(vol.getAccountId(), null, tmpltHost.getInstallPath(), path, null, null, template.getUniqueName());
				} else if (vm.getType() == VirtualMachine.Type.User) {
    				VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(alternateHost.getId(), template.getId());
    				cmdCreate = new CreateCommand(vol.getAccountId(), null, tmpltHost.getInstallPath(), null, null, path, null, null, ((int)diskOffering.getDiskSize()/1024), null, null, template.getUniqueName());
				}
				answer = (CreateAnswer)_agentMgr.easySend(alternateHost.getId(), cmdCreate);
				if (answer != null) {
					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Created volume " + vol.getFolder() + " on storage host " + alternateHost.getPrivateIpAddress());
					}
					break;
				}
				avoid.add(alternateHost);
			}
			txn.start();
			EventVO event = new EventVO();
			event.setUserId(1L); // a userId of 0 indicates that the event happened on behalf of the user (either an admin did something, or the system itself did something)
			event.setAccountId(vol.getAccountId());
			event.setType(EventTypes.EVENT_VM_CREATE);

			if (alternateHost == null || numRetry == 0 || answer == null) {
				event.setDescription("failed to create volume mirror for : " + vm.getName());
				event.setLevel(EventVO.LEVEL_WARN);
				_eventDao.persist(event);
				List<VolumeVO> vmVols = _volumeDao.findByHostIdAndInstance(fromHost.getId(), vm.getId());
				for (VolumeVO v: vmVols) {
					v.setMirrorState(MirrorState.DEFUNCT);
					VolumeVO mirr = _volumeDao.findById(v.getMirrorVolume());
					if (mirr != null) {
						mirr.setMirrorVolume(null);
						_volumeDao.update(mirr.getId(), mirr);
					}
					v.setMirrorVolume(null);
					_volumeDao.update(v.getId(), v);
				}
				s_logger.debug("Failed to create a new mirror for : " + vm.getName() + " (" + vol.getVolumeType().toString() + ") ");
				txn.commit();
				vmIds.add(vm.getId());
				continue;
			}

			event.setDescription("successfully created volume mirrors for : " + vm.getName() + " on host " + alternateHost.getName() );
			_eventDao.persist(event);
			s_logger.info("successfully created volume mirrors for : " + vm.getName() + " on host " + alternateHost.getName() );

			Account account = _accountDao.findById(Long.valueOf(vol.getAccountId()));
            if (account == null) {
                txn.rollback();
                throw new VmopsRuntimeException("unable to created mirror volume, stranded volumes account does not exist, accountId: " + vol.getAccountId());
            }

			for (VolumeVO newMirr: answer.getVolumes()) {
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Saving volume: " + newMirr.getPath());
				}

				newMirr.setHostId(alternateHost.getId());
				newMirr.setDataCenterId(dc.getId());
				newMirr.setPodId(pod.getId());
				newMirr.setAccountId(vol.getAccountId());
				newMirr.setDomainId(account.getDomainId());
				newMirr.setInstanceId(vol.getInstanceId());
				newMirr.setMirrorState(Volume.MirrorState.ACTIVE);
				newMirr.setHostIp(alternateHost.getPrivateIpAddress()); //for convenience
				newMirr.setTemplateName(template.getUniqueName());
				newMirr.setTemplateId(template.getId());
				newMirr.setDiskOfferingId(vol.getDiskOfferingId());
				VolumeVO failedMirr = _volumeDao.findMirrorVolumeByTypeAndInstance(fromHost.getId(), vol.getInstanceId(), newMirr.getVolumeType());
				VolumeVO goodMirr = null;
				if (failedMirr != null){
    				failedMirr.setMirrorState(Volume.MirrorState.DEFUNCT);
					goodMirr = _volumeDao.findById(failedMirr.getMirrorVolume());
					if (goodMirr != null)
						newMirr.setMirrorVolume(goodMirr.getId());
					failedMirr.setMirrorVolume(null);
    				_volumeDao.update(failedMirr.getId(), failedMirr);

				}
				_volumeDao.persist(newMirr);
				if (goodMirr != null) {
					goodMirr.setMirrorVolume(newMirr.getId());
					_volumeDao.update(goodMirr.getId(), goodMirr);
				}
			}
			txn.commit();
			vmIds.add(vm.getId());
		}
    	//for all affected VMs, replace their failed mirrors
    	for (long vmId: vmIds){
    		replaceVMVolumes(vmId);
    	}

    }

	private void replaceVMVolumes(long vmId) {
		VMInstanceVO vm = _vmInstanceDao.findById(vmId);
        List<VolumeVO> vols = _volumeDao.findByInstance(vmId);
        List<VolumeVO> removeVols = new ArrayList<VolumeVO>();
        List<VolumeVO> addVols = new ArrayList<VolumeVO>();
        String addHost = null;
        String removeHost = null;
		s_logger.warn("Virtual Machine " + vm.getName() + " : replacing mirrored volumes");
        for (VolumeVO vol: vols) {
        	if (vol.getMirrorState() == MirrorState.ACTIVE) {
        		addVols.add(vol);
        		addHost = vol.getHostIp();
        	} else if (vol.getMirrorState() == MirrorState.DEFUNCT){
        		removeVols.add(vol);
        		removeHost = vol.getHostIp();
        	}
        }
        String vmName = vm.getName();
        if (vm.getType() == VirtualMachine.Type.DomainRouter){
        	DomainRouterVO router = _routerDao.findById(vmId);
        	vmName = VirtualMachineName.attachVnet(router.getName(), router.getVnet());
        } else if (vm.getType() == VirtualMachine.Type.User){
        	UserVmVO userVM = _vmDao.findById(vmId);
        	vmName = VirtualMachineName.attachVnet(userVM.getName(), userVM.getVnet());
        }

        if (vm.getState() == State.Running || vm.getState() == State.Starting) {
        	MirrorCommand mirrCmd = new MirrorCommand(vmName, removeHost, addHost, removeVols, addVols);
        	MirrorAnswer answer = (MirrorAnswer)_agentMgr.easySend(vm.getHostId(), mirrCmd);
            if (answer != null) {
            	s_logger.info("Replaced mirror for " + vmName + " on compute host " + vm.getHostId());
            } else {
            	s_logger.warn("Failed to replace mirror for " + vmName + " on compute host " + vm.getHostId());
            }
        	
        }
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		_name = name;
        ComponentLocator locator = ComponentLocator.getLocator(ManagementServer.Name);
        
        _instanceDao = locator.getDao(VMInstanceDao.class);
        if (_instanceDao == null) {
            throw new ConfigurationException("Unable to get vm dao");
        }
        
        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("Unable to get uservm dao");
        }
        
        _routerDao = locator.getDao(DomainRouterDao.class);
        if (_routerDao == null) {
            throw new ConfigurationException("Unable to get routerDao");
        }

        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("unable to get host dao");
        }

        _volumeDao = locator.getDao(VolumeDao.class);
        if (_volumeDao == null) {
            throw new ConfigurationException("unable to get volumes dao");
        }
        
        _serviceOfferingDao = locator.getDao(ServiceOfferingDao.class);
        if (_serviceOfferingDao == null) {
            throw new ConfigurationException("unable to get service off dao");
        }
        _diskOfferingDao = locator.getDao(DiskOfferingDao.class);
        if (_diskOfferingDao == null) {
            throw new ConfigurationException("unable to get disk offering dao");
        }
        _templatesDao = locator.getDao(VMTemplateDao.class);
        if (_templatesDao == null) {
            throw new ConfigurationException("unable to get templates dao");
        }
        _dataCenterDao = locator.getDao(DataCenterDao.class);
        if (_dataCenterDao == null) {
            throw new ConfigurationException("unable to get data center dao");
        }
        _hostPodDao = locator.getDao(HostPodDao.class);
        if (_hostPodDao == null) {
            throw new ConfigurationException("unable to get hostpod dao");
        }
        _vmTemplateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_vmTemplateHostDao == null) {
        	throw new ConfigurationException("unable to get template host dao");
        }
        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
        	throw new ConfigurationException("unable to get event dao");
        }
        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("unable to get account dao");
        }
        _vmInstanceDao = locator.getDao(VMInstanceDao.class);
        if (_vmInstanceDao == null) {
        	throw new ConfigurationException("unable to get VM Instance dao");
        }

        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
        	throw new ConfigurationException("Unable to find " + AgentManager.class.getName());
        }
        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
            throw new ConfigurationException("Unable to find " + StorageManager.class.getName());
        }
        
        _vmMgr = locator.getManager(UserVmManager.class);
        if (_vmMgr == null) {
            throw new ConfigurationException("Unable to find " + UserVmManager.class.getName());
        }
        
        _networkMgr = locator.getManager(NetworkManager.class);
        if (_networkMgr == null) {
            throw new ConfigurationException("Unable to find " + NetworkManager.class.getName());
        }
        
        _thread = new Thread(this, "StorageHostMonitor");
        
        String value = (String)params.get("check.interval");
        _checkInterval = NumbersUtil.parseInt(value, DEFAULT_CHECK_INTERVAL) * 1000;
        
        value = (String)params.get("dead.interval");
        _deadInterval = NumbersUtil.parseInt(value, DEFAULT_DEAD_INTERVAL) ;
        
        value = (String)params.get("max.retries");
        _maxRetries = NumbersUtil.parseInt(value, DEFAULT_MAX_RETRIES);
        
        s_logger.info("StorageHostMonitor: check interval=" + _checkInterval +", dead interval=" + _deadInterval + ", max retry="+_maxRetries);
        
        return true;
	}

	@Override
	public String getName() {
		 return _name;
	}

	@Override
	public boolean start() {
	    _thread.start();
		return true;
	}

	@Override
	public boolean stop() {
       _thread.interrupt();
       return true;
	}

	@Override
	public void run() {
		List<HostVO> prevDeadHosts = new ArrayList<HostVO>();
		List<HostVO> candidateDeadHosts = new ArrayList<HostVO>();
		try {
			while (true) {
			    try {
	                //List<HostVO> allStorageHosts = _hostDao.listByType(Type.Storage);
	                List<HostVO> deadHosts = findDeadStorageHosts();
	                List<HostVO> reawakenedHosts = new ArrayList<HostVO>(prevDeadHosts);
	                reawakenedHosts.removeAll(deadHosts);
	                prevDeadHosts.removeAll(reawakenedHosts);
	                if (reawakenedHosts.size() > 0) {
	                    for (HostVO r: reawakenedHosts)
	                        s_logger.info("Storage host " + r.getName() + " is alive again");
	                }
	                
	                deadHosts.removeAll(prevDeadHosts);
	                candidateDeadHosts.retainAll(deadHosts);
	                if (candidateDeadHosts.size() > 0) {
	                    for (HostVO h: deadHosts) {
	                        s_logger.warn("Storage host " + h.getName() + " is dead, now failing over mirrored volumes");
	                        failoverVolumes(h);
	                        prevDeadHosts.add(h);
	                    }
	                }
	                candidateDeadHosts = deadHosts;
	                List<VolumeVO> strandedVolumes = _volumeDao.findStrandedMirrorVolumes();
	                if (strandedVolumes.size() > 0 ) {
	                    s_logger.warn("Found " + strandedVolumes.size() + " stranded volumes");
	                    Set<Host> avoid = new HashSet<Host>();
	                    avoid.addAll(deadHosts);
	                    createMirrorVolumes(strandedVolumes, avoid);
	                }
			    } catch (VmopsRuntimeException vmopsEx) {
                    s_logger.error("Error in storage host monitoring", vmopsEx);
                }
				Thread.sleep(_checkInterval);
			}
		} catch (InterruptedException e) {
		    s_logger.info("Interrupted while doing monitoring storage hosts ");
		} catch (Throwable t) {
            s_logger.error("Caught exception while doing monitoring storage hosts ", t );
        }
        s_logger.info("Storage Host Monitor -- done!");
	}

	private List<HostVO> findDeadStorageHosts() {
		long time = (System.currentTimeMillis() >> 10) - _deadInterval;
        List<HostVO> hosts = _hostDao.findLostHosts2(time, Host.Type.Storage);
        List<HostVO> result = new ArrayList<HostVO>();
        for (HostVO h: hosts) {
        	Status state = h.getStatus();
        	boolean disconnected = (state == Status.Disconnected) || (state == Status.Down) || (state == Status.Alert);
        	if ((h.getType() == Type.Storage) && disconnected){
        		result.add(h);
        	}
        }
        return result;
	}

	@DB
	public void createMirrorVolumes(List<VolumeVO> strandedVolumes, Set<Host> avoid) {
		//find active mirrors on this host and find alternate hosts for them.

	    VMTemplateVO routingTmplt = _templatesDao.findRoutingTemplate();

		Set<Long> vmIds = new HashSet<Long>();

		Transaction txn = Transaction.currentTxn();
			for (VolumeVO vol: strandedVolumes) {
				if (vmIds.contains(vol.getInstanceId())) {
					continue; //we've already dealt with this one.
				}
				DataCenterVO dc = _dataCenterDao.findById(vol.getDataCenterId());
				HostPodVO pod = _hostPodDao.findById(vol.getPodId());
				UserVmVO userVM = _vmDao.findById(vol.getInstanceId());
				ServiceOffering so = null;
				DiskOfferingVO diskOffering = null;
				if (vol.getDiskOfferingId() != null) {
	                diskOffering = _diskOfferingDao.findById(vol.getDiskOfferingId());
				}
				if (userVM != null) {
				    so = _serviceOfferingDao.findById(Long.valueOf(userVM.getServiceOfferingId()));
				}
				VMTemplateVO template = null;
				if (vol.getTemplateName() != null) {
					template = _templatesDao.findByName(vol.getTemplateName());
				}
				VMInstanceVO vm = _vmInstanceDao.findById(vol.getInstanceId());

				avoid.add(_hostDao.findById(vol.getHostId()));

				if (vol.getMirrorVolume() != null) {
					//Hmmm.. shouldn't be here
					s_logger.error("Trying to create a mirror volume for an already mirrored volume?");
					continue;
				}
 
				Host host = null;
				int numRetry = _maxRetries;
				CreateAnswer answer = null;

				while ((host = _storageMgr.findHost(dc, pod, so, diskOffering, template, null, avoid)) !=null && (numRetry > 0)) {
					numRetry--;
					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Attempting to create volume for  + " + vol.getFolder() + " on " + host.getName());
					}
					String path = vol.getPath();
					CreateCommand cmdCreate = null;
					if (vm.getType() == VirtualMachine.Type.DomainRouter) {
	    				VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(host.getId(), routingTmplt.getId());
						cmdCreate = new CreateCommand(vol.getAccountId(), null, tmpltHost.getInstallPath(), path, null, null, template.getUniqueName());
					} else if (vm.getType() == VirtualMachine.Type.User) {
	    				VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(host.getId(), template.getId());
						cmdCreate = new CreateCommand(vol.getAccountId(), null, tmpltHost.getInstallPath(), null, null, path, null, null, ((int)diskOffering.getDiskSize()/1024), null, null, template.getUniqueName());
					}
					answer = (CreateAnswer)_agentMgr.easySend(host.getId(), cmdCreate);
					if (answer != null) {
						if (s_logger.isDebugEnabled()) {
							s_logger.debug("Created volume " + vol.getFolder() + " on storage host " + host.getPrivateIpAddress());
						}
						break;
					}
					avoid.add(host);
				}
				txn.start();
				EventVO event = new EventVO();
				event.setUserId(1L); // a userId of 1 indicates that the event happened on behalf of the user (either an admin did something, or the system itself did something)
				event.setAccountId(vol.getAccountId());
				event.setType(EventTypes.EVENT_VM_CREATE);
	
				if (host == null || numRetry == 0 || answer == null) {
					s_logger.warn("Failed to create a new mirror for : " + vm.getName() + " (" + vol.getVolumeType().toString() + ") ");
					txn.commit();
					vmIds.add(vm.getId());
					continue;
				}
	
				event.setDescription("successfully created volume mirrors for : " + vm.getName() + " on host " + host.getName() );
				_eventDao.persist(event);
				s_logger.info("successfully created volume mirrors for : " + vm.getName() + " on host " + host.getName() );

				Account account = _accountDao.findById(Long.valueOf(vol.getAccountId()));
				if (account == null) {
				    txn.rollback();
				    throw new VmopsRuntimeException("unable to created mirror volume, stranded volumes account does not exist, accountId: " + vol.getAccountId());
				}

				for (VolumeVO newMirr: answer.getVolumes()) {
					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Saving volume: " + newMirr.getPath());
					}

					newMirr.setHostId(host.getId());
					newMirr.setDataCenterId(dc.getId());
					newMirr.setPodId(pod.getId());
					newMirr.setAccountId(vol.getAccountId());
					newMirr.setDomainId(account.getDomainId());
					newMirr.setInstanceId(vol.getInstanceId());
					newMirr.setMirrorState(Volume.MirrorState.ACTIVE);
					newMirr.setHostIp(host.getPrivateIpAddress()); //for convenience
					newMirr.setTemplateName(template.getUniqueName());
					newMirr.setTemplateId(template.getId());
					newMirr.setDiskOfferingId(vol.getDiskOfferingId());
    				VolumeVO goodMirr = _volumeDao.findMirrorVolumeByTypeAndInstance(vol.getHostId(), vol.getInstanceId(), newMirr.getVolumeType());
    				if (goodMirr == null) {
    					//Uh-oh
    					s_logger.error("Unable to find good volume for vm " + vm.getId() + ", disktype " + newMirr.getVolumeType().toString());
    					continue;
    				}
					newMirr.setMirrorVolume(goodMirr.getId());
					_volumeDao.persist(newMirr);
					goodMirr.setMirrorVolume(newMirr.getId());
					_volumeDao.update(goodMirr.getId(), goodMirr);
				}
				txn.commit();
				vmIds.add(vm.getId());
			}

		//for all affected VMs, replace their failed mirrors
		for (long vmId: vmIds){
			replaceVMVolumes(vmId);
		}
	}
}
