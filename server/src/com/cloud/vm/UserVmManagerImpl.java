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
package com.cloud.vm;

import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.AttachIsoCommand;
import com.cloud.agent.api.AttachVolumeCommand;
import com.cloud.agent.api.CheckVirtualMachineAnswer;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreatePrivateTemplateFromSnapshotCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.ManageSnapshotAnswer;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.PrepareForMigrationCommand;
import com.cloud.agent.api.RebootAnswer;
import com.cloud.agent.api.RebootCommand;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.storage.CreatePrivateTemplateAnswer;
import com.cloud.agent.api.storage.CreatePrivateTemplateCommand;
import com.cloud.alert.AlertManager;
import com.cloud.api.BaseCmd;
import com.cloud.async.AsyncJobExecutor;
import com.cloud.async.AsyncJobManager;
import com.cloud.async.AsyncJobResult;
import com.cloud.async.AsyncJobVO;
import com.cloud.async.BaseAsyncJobExecutor;
import com.cloud.async.executor.DestroyVMExecutor;
import com.cloud.async.executor.RebootVMExecutor;
import com.cloud.async.executor.StartVMExecutor;
import com.cloud.async.executor.StopVMExecutor;
import com.cloud.async.executor.VMExecutorHelper;
import com.cloud.async.executor.VMOperationListener;
import com.cloud.async.executor.VMOperationParam;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.ResourceCount.ResourceType;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.configuration.dao.ResourceLimitDao;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.VlanVO;
import com.cloud.dc.Vlan.VlanType;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.dc.dao.VlanDao;
import com.cloud.domain.dao.DomainDao;
import com.cloud.event.EventTypes;
import com.cloud.event.EventVO;
import com.cloud.event.dao.EventDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InternalErrorException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.ha.HighAvailabilityManager;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.dao.DetailsDao;
import com.cloud.host.dao.HostDao;
import com.cloud.network.FirewallRuleVO;
import com.cloud.network.LoadBalancerVMMapVO;
import com.cloud.network.NetworkManager;
import com.cloud.network.SecurityGroupVMMapVO;
import com.cloud.network.dao.FirewallRulesDao;
import com.cloud.network.dao.IPAddressDao;
import com.cloud.network.dao.LoadBalancerDao;
import com.cloud.network.dao.LoadBalancerVMMapDao;
import com.cloud.network.dao.SecurityGroupDao;
import com.cloud.network.dao.SecurityGroupVMMapDao;
import com.cloud.pricing.dao.PricingDao;
import com.cloud.service.ServiceOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.service.ServiceOffering.GuestIpType;
import com.cloud.service.dao.ServiceOfferingDao;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.storage.GuestOSVO;
import com.cloud.storage.Snapshot;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.Storage;
import com.cloud.storage.StorageManager;
import com.cloud.storage.StoragePoolVO;
import com.cloud.storage.VMTemplateHostVO;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VmDiskVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.Snapshot.SnapshotType;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.VMTemplateStorageResourceAssoc.Status;
import com.cloud.storage.VirtualMachineTemplate.BootloaderType;
import com.cloud.storage.Volume.VolumeType;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.DiskTemplateDao;
import com.cloud.storage.dao.GuestOSCategoryDao;
import com.cloud.storage.dao.GuestOSDao;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.StoragePoolDao;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateHostDao;
import com.cloud.storage.dao.VmDiskDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.user.AccountManager;
import com.cloud.user.AccountVO;
import com.cloud.user.UserVO;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.user.dao.UserStatisticsDao;
import com.cloud.utils.DateUtil;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.component.Inject;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.exception.ExecutionException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.VirtualMachine.Event;
import com.cloud.vm.VirtualMachine.Type;
import com.cloud.vm.dao.DomainRouterDao;
import com.cloud.vm.dao.UserVmDao;

@Local(value={UserVmManager.class})
public class UserVmManagerImpl implements UserVmManager {
    private static final Logger s_logger = Logger.getLogger(UserVmManagerImpl.class);
	private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 3; 	// 3 seconds
   
    @Inject HostDao _hostDao = null;
    @Inject DetailsDao _detailsDao = null;
    @Inject DomainRouterDao _routerDao = null;
    @Inject ServiceOfferingDao _offeringDao = null;
    @Inject DiskOfferingDao _diskOfferingDao = null;
    @Inject VmDiskDao _vmDiskDao = null;
    @Inject UserStatisticsDao _userStatsDao = null;
    @Inject VMTemplateDao _templateDao =  null;
    @Inject VMTemplateHostDao _templateHostDao = null;
    @Inject DiskTemplateDao _diskDao = null;
    @Inject DomainDao _domainDao = null;
    @Inject ResourceLimitDao _limitDao = null;
    @Inject UserVmDao _vmDao = null;
    @Inject VolumeDao _volsDao = null;
    @Inject DataCenterDao _dcDao = null;
    @Inject FirewallRulesDao _rulesDao = null;
    @Inject SecurityGroupDao _securityGroupDao = null;
    @Inject SecurityGroupVMMapDao _securityGroupVMMapDao = null;
    @Inject LoadBalancerVMMapDao _loadBalancerVMMapDao = null;
    @Inject LoadBalancerDao _loadBalancerDao = null;
    @Inject IPAddressDao _ipAddressDao = null;
    @Inject HostPodDao _podDao = null;
    @Inject PricingDao _pricingDao = null;
    @Inject CapacityDao _capacityDao = null;
    @Inject NetworkManager _networkMgr = null;
    @Inject StorageManager _storageMgr = null;
    @Inject AgentManager _agentMgr = null;
    @Inject AccountDao _accountDao = null;
    @Inject UserDao _userDao = null;
    @Inject SnapshotDao _snapshotDao = null;
    @Inject GuestOSDao _guestOSDao = null;
    @Inject GuestOSCategoryDao _guestOSCategoryDao = null;
    @Inject HighAvailabilityManager _haMgr = null;
    @Inject AlertManager _alertMgr = null;
    @Inject AccountManager _accountMgr;
    @Inject AsyncJobManager _asyncMgr;
    @Inject protected StoragePoolHostDao _storagePoolHostDao;
    @Inject VlanDao _vlanDao;
    @Inject StoragePoolDao _storagePoolDao;
    @Inject VMTemplateHostDao _vmTemplateHostDao;

    @Inject EventDao _eventDao = null;
    ScheduledExecutorService _executor = null;
    int _expungeInterval;
    int _expungeDelay;
    int _retry = 2;

    String _name;
    String _instance;
    String _zone;

    Random _rand = new Random(System.currentTimeMillis());

    private ConfigurationDao _configDao;

	int _userVMCap = 0;
    final int _maxWeight = 256;

    @Override
    public UserVmVO getVirtualMachine(long vmId) {
        return _vmDao.findById(vmId);
    }

    @Override
    public List<? extends UserVm> getVirtualMachines(long hostId) {
        return _vmDao.listByHostId(hostId);
    }

    @Override
    public boolean resetVMPassword(long userId, long vmId, String password) {
        UserVmVO vm = _vmDao.findById(vmId);
        VMTemplateVO template = _templateDao.findById(vm.getTemplateId());
        if (template.getEnablePassword()) {
	        if (_networkMgr.savePasswordToRouter(vm.getDomainRouterId(), vm.getPrivateIpAddress(), password)) {
	            // Need to reboot the virtual machine so that the password gets redownloaded from the DomR, and reset on the VM
	        	if (!rebootVirtualMachine(userId, vmId)) {
	        		if (vm.getState() == State.Stopped) {
	        			return true;
	        		}
	        		return false;
	        	} else {
	        		return true;
	        	}
	        } else {
	        	return false;
	        }
        } else {
        	if (s_logger.isDebugEnabled()) {
        		s_logger.debug("Reset password called for a vm that is not using a password enabled template");
        	}
        	return false;
        }
    }
    
    @Override
    public void attachVolumeToVM(long vmId, long volumeId) throws InternalErrorException {
    	VolumeVO volume = _volsDao.findById(volumeId);
    	UserVmVO vm = _vmDao.findById(vmId);
    	
    	if (volume.getPodId() != vm.getPodId()) {
    		// Move the volume to the VM's pod
    		StoragePoolVO pool = _storagePoolDao.findById(vm.getPoolId());
    		volume = _storageMgr.moveVolume(volume, pool);
    	}
    	
    	String errorMsg = "Failed to attach volume: " + volume.getName() + " to VM: " + vm.getName();
    	boolean sendCommand = (vm.getState() == State.Running);
    	Answer answer = null;
    	String volumeNameLabel = null;
    	Long hostId = vm.getHostId();
    	if (sendCommand) {
    	    // => hostId != null
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, null);
    		
    		AttachVolumeCommand cmd = new AttachVolumeCommand(true, vm.getInstanceName(), volume.getFolder(), volume.getPath(), volume.getName(), volumeNameLabel);
    		
    		try {
    			answer = _agentMgr.send(hostId, cmd);
    		} catch (Exception e) {
    			throw new InternalErrorException(errorMsg + " due to: " + e.getMessage());
    		}
    	}

    	EventVO event = new EventVO();
        event.setAccountId(volume.getAccountId());
        event.setUserId(1L);
        event.setType(EventTypes.EVENT_VOLUME_ATTACH);
        if (!sendCommand || (answer != null && answer.getResult())) {
    		// Mark the volume as attached
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, vm);
    		_volsDao.attachVolume(volume.getId(), vmId, volumeNameLabel);
            event.setDescription("Volume: " +volume.getName()+ " successfully attached to VM: "+vm.getDisplayName());
            event.setLevel(EventVO.LEVEL_INFO);
            _eventDao.persist(event);
    	} else {
    		if (answer != null) {
    			String details = answer.getDetails();
    			if (details != null && !details.isEmpty())
    				errorMsg += "; " + details;
    		}
            event.setDescription(errorMsg);
            event.setLevel(EventVO.LEVEL_ERROR);
            _eventDao.persist(event);
    		throw new InternalErrorException(errorMsg);
    	}
    }
    
    @Override
    public void detachVolumeFromVM(long volumeId) throws InternalErrorException {
    	VolumeVO volume = _volsDao.findById(volumeId);
    	
    	Long vmId = volume.getInstanceId();
    	
    	if (vmId == null) {
    		return;
    	}
    	
    	UserVmVO vm = _vmDao.findById(vmId);
    	String errorMsg = "Failed to detach volume: " + volume.getName() + " from VM: " + vm.getName();
    	boolean sendCommand = (vm.getState() == State.Running);
    	Answer answer = null;
    	String volumeNameLabel = null;
    	
    	if (sendCommand) {
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, vm);
    		
			AttachVolumeCommand cmd = new AttachVolumeCommand(false, vm.getInstanceName(), volume.getFolder(), volume.getPath(), volume.getName(), volumeNameLabel);
			
			try {
    			answer = _agentMgr.send(vm.getHostId(), cmd);
    		} catch (Exception e) {
    			throw new InternalErrorException(errorMsg + " due to: " + e.getMessage());
    		}
    	}
    	
        EventVO event = new EventVO();
        event.setAccountId(volume.getAccountId());
        event.setUserId(1L);
        event.setType(EventTypes.EVENT_VOLUME_DETACH);
		if (!sendCommand || (answer != null && answer.getResult())) {
			// Mark the volume as detached
    		_volsDao.detachVolume(volume.getId());
            event.setDescription("Volume: " +volume.getName()+ " successfully detached from VM: "+vm.getDisplayName());
            event.setLevel(EventVO.LEVEL_INFO);
            _eventDao.persist(event);
    	} else {
    		
    		if (answer != null) {
    			String details = answer.getDetails();
    			if (details != null && !details.isEmpty())
    				errorMsg += "; " + details;
    		}
    		
            event.setDescription(errorMsg);
            event.setLevel(EventVO.LEVEL_ERROR);
            _eventDao.persist(event);
    		throw new InternalErrorException(errorMsg);
    	}
    }
    
    @Override
    public boolean attachISOToVM(long vmId, long isoId, boolean attach) {
    	UserVmVO vm = _vmDao.findById(vmId);
    	
    	if (vm == null) {
            return false;
    	} else if (vm.getState() != State.Running) {
    		return true;
    	}

        // Get the path of the ISO
    	String isoPath = _storageMgr.getAbsoluteIsoPath(isoId, vm.getDataCenterId());
    	String isoName = _templateDao.findById(isoId).getName();
    	
	    if (isoPath == null) {
	        // we can't send a null path to the ServerResource, so return false if we are unable to find the isoPath
	    	if (isoName.startsWith("xs-tools"))
	    		isoPath = isoName;
	    	else
	    		return false;
	    }

    	String vmName = vm.getInstanceName();

    	HostVO host = _hostDao.findById(vm.getHostId());
    	if (host == null)
    		return false;

    	AttachIsoCommand cmd = new AttachIsoCommand(vmName, isoPath, attach);
    	Answer a = _agentMgr.easySend(vm.getHostId(), cmd);
    	return (a != null);
    }

    @Override
    public UserVmVO startVirtualMachine(long userId, long vmId, String isoPath) {
        return startVirtualMachine(userId, vmId, null, isoPath);
    }
    
    @Override
    public boolean executeStartVM(StartVMExecutor executor, VMOperationParam param) {
    	// TODO following implementation only do asynchronized operation at API level
        try {
            UserVmVO vm = start(param.getUserId(), param.getVmId(), null, param.getIsoPath());
            if(vm != null)
	        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
	        		AsyncJobResult.STATUS_SUCCEEDED, 0, VMExecutorHelper.composeResultObject(
	        			executor.getAsyncJobMgr().getExecutorContext().getManagementServer(), vm));
            else
            	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "Unable to start vm");
        } catch (StorageUnavailableException e) {
            s_logger.debug("Unable to start vm because storage is unavailable: " + e.getMessage());
            
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, BaseCmd.VM_ALLOCATION_ERROR, "Unable to start vm because storage is unavailable");
        } catch (ConcurrentOperationException e) {
        	s_logger.debug(e.getMessage());
        	
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
		}
        return true;
    }

    @Override
    public UserVmVO startVirtualMachine(long userId, long vmId, String password, String isoPath) {
        try {
            return start(userId, vmId, password, isoPath);
        } catch (StorageUnavailableException e) {
            s_logger.debug("Unable to start vm because storage is unavailable: " + e.getMessage());
            return null;
        } catch (ConcurrentOperationException e) {
        	s_logger.debug(e.getMessage());
        	return null;
		}
    }

    @DB
    protected UserVmVO start(long userId, long vmId, String password, String isoPath) throws StorageUnavailableException, ConcurrentOperationException {
        UserVmVO vm = _vmDao.findById(vmId);
        if (vm == null || vm.getRemoved() != null) {
            s_logger.debug("Unable to find " + vmId);
            return null;
        }
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Starting VM: " + vmId);
        }

        State state = vm.getState();
        if (state == State.Running) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Starting an already started VM: " + vm.getId() + " - " + vm.getName() + "; state = " + vm.getState().toString());
            }
            return vm;
        }

        if (state.isTransitional()) {
        	throw new ConcurrentOperationException("Concurrent operations on the vm " + vm.getId() + " - " + vm.getName() + "; state = " + state.toString());
        }
        
        DataCenterVO dc = _dcDao.findById(vm.getDataCenterId());
        HostPodVO pod = _podDao.findById(vm.getPodId());
        StoragePoolVO sp = _storagePoolDao.findById(vm.getPoolId());

        VMTemplateVO template = _templateDao.findById(vm.getTemplateId());
        ServiceOffering offering = _offeringDao.findById(vm.getServiceOfferingId());
        // FIXME:  Do I need this here?  Maybe the allocator should just take "Offering" which ServiceOffering and DiskOffering extend?
        //         If that's the case, how does FirstFitStorageAllocator figure out if enough memory/cpu exists on the pod before creating the VM?
        List<DiskOfferingVO> diskOfferings = _diskOfferingDao.listByInstanceId(vm.getId());
        
        DiskOfferingVO diskOffering = null;
        long diskOfferingId = -1;
        if (diskOfferings.size() > 0) {
        	diskOffering = diskOfferings.get(0);
        	diskOfferingId = diskOffering.getId();
        }
        
        // If an ISO path is passed in, boot from that ISO
        // Else, check if the VM already has an ISO attached to it. If so, start the VM with that ISO inserted, but don't boot from it.
        boolean bootFromISO = false;
        if (isoPath != null) {
        	bootFromISO = true;
        } else {
            Long isoId = vm.getIsoId();
            if (isoId != null) {
                isoPath = _storageMgr.getAbsoluteIsoPath(isoId, vm.getDataCenterId());
            }
        }
        
        // Determine the VM's OS description
        String guestOSDescription;
        GuestOSVO guestOS = _guestOSDao.findById(vm.getGuestOSId());
        if (guestOS == null) {
        	s_logger.debug("Could not find guest OS description for vm: " + vm.getName());
        	return null;
        } else {
        	guestOSDescription = guestOS.getName();
        }

        HashSet<Host> avoid = new HashSet<Host>();

        HostVO host = (HostVO) _agentMgr.findHost(Host.Type.Computing, dc, pod, sp, offering, diskOffering, template, null, null, avoid);

        if (host == null) {
            s_logger.error("Unable to find any host for " + vm.toString());
            return null;
        }

        if (!_vmDao.updateIf(vm, Event.StartRequested, host.getId())) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to start VM " + vm.toString() + " because the state is not correct.");
            }
            return null;
        }
        
        boolean started = false;
        Transaction txn = Transaction.currentTxn();
        try {
            String eventParams = "id=" + vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ndoId=" + diskOfferingId + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId();
            EventVO event = new EventVO();
            event.setType(EventTypes.EVENT_VM_START);
            event.setUserId(userId);
            event.setAccountId(vm.getAccountId());
            event.setParameters(eventParams);
    
	        DomainRouterVO router = _networkMgr.addVirtualMachineToGuestNetwork(vm, password);
	        if (router == null) {
	        	s_logger.error("Unable to add vm " + vm.getId() + " - " + vm.getName());
	        	_vmDao.updateIf(vm, Event.OperationFailed, null);
                event.setDescription("unable to start VM: " + vm.getName() + "; unable to add VM to guest network");
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
	        	return null;
	        }
	        
	        String vnet = router.getVnet();
	        if(NetworkManager.USE_POD_VLAN){
	            if(vm.getPodId() != router.getPodId()){
	                //VM is in a different Pod
	                if(router.getZoneVlan() == null){
	                    //Create Zone Vlan if not created already
	                    vnet = _networkMgr.createZoneVlan(router);
	                    if (vnet == null) {
	                        s_logger.error("Vlan creation failed. Unable to add vm " + vm.getId() + " - " + vm.getName());
	                        return null;
	                    }
	                } else {
	                    //Use existing zoneVlan
	                    vnet = router.getZoneVlan();
	                }
	            }
	        }
	
	        boolean mirroredVols = vm.isMirroredVols();
	        
	        List<VolumeVO> rootVols = _volsDao.findByInstanceAndType(vm.getId(), VolumeType.ROOT);
	        assert rootVols.size() == 1 : "How can we get " + rootVols.size() + " root volume for " + vm.getId();
	        
	        String [] storageIps = new String[2];
	        VolumeVO vol = rootVols.get(0);

	        List<VolumeVO> vols = _volsDao.findByInstance(vm.getId());

            Answer answer = null;
            int retry = _retry;

            do {

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Trying to start vm " + vm.getName() + " on host " + host.toString());
                }
                txn.start();

                vm.setVnet(vnet);
                vm.setInstanceName(VirtualMachineName.attachVnet(vm.getName(), vm.getVnet()));
                vm.setStorageIp(storageIps[0]);

                if( retry < _retry) {
                    if (!_vmDao.updateIf(vm, Event.OperationRetry, host.getId())) {
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Unable to start VM " + vm.toString() + " because the state is not correct.");
                        }
                        return null;
                    }
                }

                txn.commit();

                if( !_storageMgr.share(vm, vols, host, true) ) {
                	s_logger.debug("Unable to share volumes to host " + host.toString());
                	continue;
                }

                int utilization = _userVMCap; //cpu_cap
                //Configuration cpu.uservm.cap is not available in default installation. Using this parameter is not encouraged
                
                int cpuWeight = _maxWeight; //cpu_weight
                
                // weight based allocation
                cpuWeight = (int)((offering.getSpeed()*0.99) / (float)host.getSpeed() * _maxWeight);
                if (cpuWeight > _maxWeight) {
                	cpuWeight = _maxWeight;
                }

                int bits;
                if (template == null) {
                	bits = 64;
                } else {
                	bits = template.getBits();
                }

                StartCommand cmdStart = new StartCommand(vm, vm.getInstanceName(), offering, offering.getRateMbps(), offering.getMulticastRateMbps(), router, storageIps, vol.getFolder(), vm.getVnet(), utilization, cpuWeight, vols, mirroredVols, bits, isoPath, bootFromISO, guestOSDescription);
                if (Storage.ImageFormat.ISO.equals(template.getFormat()) || template.isRequiresHvm()) {
                	cmdStart.setBootloader(BootloaderType.HVM);
                }

                if (vm.getExternalVlanDbId() != null) {
                	final VlanVO externalVlan = _vlanDao.findById(vm.getExternalVlanDbId());
                	cmdStart.setExternalVlan(externalVlan.getVlanId());
                	cmdStart.setExternalMacAddress(vm.getExternalMacAddress());
                }

                try {
	                answer = _agentMgr.send(host.getId(), cmdStart);
	                if (answer.getResult()) {
	                    if (s_logger.isDebugEnabled()) {
	                        s_logger.debug("Started vm " + vm.getName() + " on host " + host.toString());
	                    }
	                    started = true;
	                    break;
	                }
	                
	                s_logger.debug("Unable to start " + vm.toString() + " on host " + host.toString() + " due to " + answer.getDetails());
                } catch (OperationTimedoutException e) {
                	if (e.isActive()) {
                		s_logger.debug("Unable to start vm " + vm.getName() + " due to operation timed out and it is active so scheduling a restart.");
                		_haMgr.scheduleRestart(vm, true);
                		host = null;
                		return null;
                	}
                } catch (AgentUnavailableException e) {
                	s_logger.debug("Agent " + host.toString() + " was unavailable to start VM " + vm.getName());
                }

                avoid.add(host);

                _storageMgr.unshare(vm, vols, host);
            } while (--retry > 0 && (host = (HostVO)_agentMgr.findHost(Host.Type.Computing, dc, pod, sp, offering, diskOffering, template, null, null, avoid)) != null);

            if (host == null || retry <= 0) {
                event.setDescription("unable to start VM: " + vm.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                throw new ExecutionException("Unable to start VM: " + vm.getName());
            }

            if (!_vmDao.updateIf(vm, Event.OperationSucceeded, host.getId())) {
                event.setDescription("unable to start VM: " + vm.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
            	throw new ConcurrentOperationException("Starting vm " + vm.getName() + " didn't work.");
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Started vm " + vm.getName());
            }

            event.setDescription("successfully started VM: " + vm.getName());
            _eventDao.persist(event);

            return _vmDao.findById(vm.getId());
        } catch (Throwable th) {
            txn.rollback();
            s_logger.error("While starting vm " + vm.getName() + ", caught throwable: ", th);

            if (!started) {
	            vm.setVnet(null);
	            vm.setStorageIp(null);

	            txn.start();
	            if (_vmDao.updateIf(vm, Event.OperationFailed, null)) {
		            txn.commit();
	            }
            }

            if (th instanceof StorageUnavailableException) {
            	throw (StorageUnavailableException)th;
            }
            if (th instanceof ConcurrentOperationException) {
            	throw (ConcurrentOperationException)th;
            }
            if (th instanceof ExecutionException) {
            	s_logger.warn(th.getMessage());
            	return null;
            }
            return null;
        }
    }

    @Override
    public boolean stopVirtualMachine(long userId, long vmId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Stopping vm=" + vmId);
        }

        UserVmVO vm = _vmDao.findById(vmId);
        if (vm == null || vm.getRemoved() != null) {
        	if (s_logger.isDebugEnabled()) {
        		s_logger.debug("VM is either removed or deleted.");
        	}
    		return true;
        }
        
        return stop(userId, vm);
    }
    
    @Override
    public boolean executeStopVM(final StopVMExecutor executor, final VMOperationParam param) {
        final UserVmVO vm = _vmDao.findById(param.getVmId());
        if (vm == null || vm.getRemoved() != null) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_SUCCEEDED, 0, "VM is either removed or deleted");
            	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: VM is either removed or deleted");
        	return true;
        }
        
        State state = vm.getState();
        if (state == State.Stopped) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_SUCCEEDED, 0, "VM is already stopped");
        	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: VM is already stopped");
            return true;
        }
        
        if (state == State.Creating || state == State.Destroyed || state == State.Expunging) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_SUCCEEDED, 0, "VM is not in a stoppable state");
            	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: VM is not in a stoppable state");
        	return true;
        }
        
        if (!_vmDao.updateIf(vm, Event.StopRequested, vm.getHostId())) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "VM is not in a state to stop");
                	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: VM is not in a state to stop");
            return true;
        }
        
        if (vm.getHostId() == null) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "VM host is null (invalid VM)");
                	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: VM host is null (invalid VM)");
            return true;
        }
        
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if(asyncExecutor != null) {
        	AsyncJobVO job = asyncExecutor.getJob();
        	_asyncMgr.updateAsyncJobAttachment(job.getId(), "vm_instance", vm.getId());
        }
        
        StopCommand cmd = new StopCommand(vm, vm.getInstanceName(), vm.getVnet());
        try {
			long seq = _agentMgr.send(vm.getHostId(), new Command[] {cmd}, true,
				new VMOperationListener(executor, param, vm, 0));
			
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize stop VM command: sending command to agent, seq - " + seq);
			
			return false;
		} catch (AgentUnavailableException e) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, 0, "Agent is not available");
            _vmDao.updateIf(vm, Event.OperationFailed, vm.getHostId());
        	return true;
		}
    }

    @Override
    public boolean rebootVirtualMachine(long userId, long vmId) {
        UserVmVO vm = _vmDao.findById(vmId);

        if (vm == null || vm.getState() == State.Destroyed || vm.getState() == State.Expunging || vm.getRemoved() != null) {
            return false;
        }

        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_REBOOT);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());

        if (vm.getState() == State.Running && vm.getHostId() != null) {
            RebootCommand cmd = new RebootCommand(vm.getInstanceName());
            RebootAnswer answer = (RebootAnswer)_agentMgr.easySend(vm.getHostId(), cmd);
           
            if (answer != null) {
                event.setDescription("successfully rebooted VM instance : " + vm.getName());
                _eventDao.persist(event);
                return true;
            } else {
                event.setDescription("failed to reboot VM instance : " + vm.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public boolean executeRebootVM(RebootVMExecutor executor, VMOperationParam param) {
    	
        final UserVmVO vm = _vmDao.findById(param.getVmId());
        if (vm == null || vm.getState() == State.Destroyed || vm.getState() == State.Expunging || vm.getRemoved() != null) {
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, 0, "VM does not exist or in destroying state");
        	
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Execute asynchronize Reboot VM command: VM does not exist or in destroying state");
        	return true;
        }
        
        if (vm.getState() == State.Running && vm.getHostId() != null) {
            RebootCommand cmd = new RebootCommand(vm.getInstanceName());
            try {
				long seq = _agentMgr.send(vm.getHostId(), new Command[] {cmd}, true,
					new VMOperationListener(executor, param, vm, 0));
				
            	if(s_logger.isDebugEnabled())
            		s_logger.debug("Execute asynchronize Reboot VM command: sending command to agent, seq - " + seq);
				return false;
			} catch (AgentUnavailableException e) {
	        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "Agent is not available");
	        	return true;
			}
        }
        
    	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
    		AsyncJobResult.STATUS_FAILED, 0, "VM is not running or agent host is disconnected");
    	return true;
    }

    @Override
    @DB
    public String upgradeVirtualMachine(long vmId, long serviceOfferingId) {
        UserVmVO vm = _vmDao.findById(vmId);
        ServiceOfferingVO offering = _offeringDao.findById(serviceOfferingId);

        if (vm == null) {
            return "Unable to find virtual machine " + vmId;
        }
        if (offering == null) {
            return "Unable to find service offering " + serviceOfferingId;
        }

        if (vm.getServiceOfferingId() == offering.getId().longValue()) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("Not upgrading vm " + vm.toString() + " since it already has the requested service offering id (" + offering.getId() + ")");
            }
            return "Not upgrading vm " + vm.toString() + " since it already has the requested service offering id (" + offering.getId() + ")";
        }

        if (!_agentMgr.isVirtualMachineUpgradable(vm, offering)) {
            return "Unable to upgrade virtual machine, not enough resources available for an offering of " +
                   offering.getCpu() + " cpu(s) at " + offering.getSpeed() + " Mhz, and " + offering.getRamSize() + " MB of memory";
        }

        if (!vm.getState().equals(State.Stopped)) {
            s_logger.warn("Unable to upgrade virtual machine " + vm.toString() + " in state " + vm.getState());
            return "Unable to upgrade virtual machine " + vm.toString() + " in state " + vm.getState() + "; make sure the virtual machine is stopped and not in an error state before upgrading.";
        }

        ServiceOfferingVO currentOffering = _offeringDao.findById(vm.getServiceOfferingId());
        if (currentOffering.getUseLocalStorage() != offering.getUseLocalStorage()) {
            return "Unable to upgrade virtual machine " + vm.toString() + ", cannot switch between local storage and shared storage service offerings.  Current offering useLocalStorage=" +
                   currentOffering.getUseLocalStorage() + ", target offering useLocalStorage=" + offering.getUseLocalStorage();
        }

        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            vm.setServiceOfferingId(offering.getId().longValue());
            _vmDao.update(vmId, vm);
            txn.commit();
        } catch (Exception e) {
            txn.rollback();
            s_logger.warn("Unable to persist", e);
            return "Upgrade unsuccessful";
        }
        return "Upgrade successful";
    }

    @Override
    public HashMap<Long, VmStatsEntry> getVirtualMachineStatistics(long hostId, List<Long> vmIds) throws InternalErrorException {
    	HashMap<Long, VmStatsEntry> vmStatsById = new HashMap<Long, VmStatsEntry>();
    	
    	if (vmIds.isEmpty()) {
    		return vmStatsById;
    	}
    	
    	List<String> vmNames = new ArrayList<String>();
    	
    	for (Long vmId : vmIds) {
    		UserVmVO vm = _vmDao.findById(vmId);
    		vmNames.add(vm.getInstanceName());
    	}
    	
    	Answer answer = _agentMgr.easySend(hostId, new GetVmStatsCommand(vmNames));
    	if (answer == null || !answer.getResult()) {
    		throw new InternalErrorException("Unable to obtain VM statistics.");
    	} else {
    		HashMap<String, VmStatsEntry> vmStatsByName = ((GetVmStatsAnswer) answer).getVmStatsMap();
    		
    		for (String vmName : vmStatsByName.keySet()) {
    			vmStatsById.put(vmIds.get(vmNames.indexOf(vmName)), vmStatsByName.get(vmName));
    		}
    	}
    	
    	return vmStatsById;
    }
    
    @DB
    protected String acquireGuestIpAddress(long dcId, long accountId, UserVmVO userVm) throws InternalErrorException {
    	boolean routerLock = false;
        DomainRouterVO router = _routerDao.findBy(accountId, dcId);
        long routerId = router.getId();
        Transaction txn = Transaction.currentTxn();
    	try {
    		txn.start();
        	router = _routerDao.acquire(routerId);
        	if (router == null) {
        		throw new InternalErrorException("Unable to lock up the router:" + routerId);
        	}
        	routerLock = true;
        	List<UserVmVO> userVms = _vmDao.listByAccountAndDataCenter(accountId, dcId);
        	Set<Long> allPossibleIps = NetUtils.getAllIpsFromCidr(router.getGuestIpAddress(), NetUtils.getCidrSize(router.getGuestNetmask()));
        	Set<Long> usedIps = new TreeSet<Long> ();
        	for (UserVmVO vm: userVms) {
        		if (vm.getGuestIpAddress() != null) {
        			usedIps.add(NetUtils.ip2Long(vm.getGuestIpAddress()));
        		}
        	}
        	if (usedIps.size() != 0) {
        		allPossibleIps.removeAll(usedIps);
        	}
        	if (allPossibleIps.isEmpty()) {
        		return null;
        	}
        	Iterator<Long> iterator = allPossibleIps.iterator();
        	long ipAddress = iterator.next().longValue();
        	String ipAddressStr = NetUtils.long2Ip(ipAddress);
        	userVm.setGuestIpAddress(ipAddressStr);
        	userVm.setGuestNetmask(router.getGuestNetmask());
            String vmMacAddress = NetUtils.long2Mac(
                	(NetUtils.mac2Long(router.getGuestMacAddress()) & 0xffffffff0000L) | (ipAddress & 0xffff)
                );
            userVm.setGuestMacAddress(vmMacAddress);
        	_vmDao.update(userVm.getId(), userVm);
        	if (routerLock) {
        		_routerDao.release(routerId);
        		routerLock = false;
        	}
        	txn.commit();
        	return ipAddressStr;
        }finally {
        	if (routerLock) {
        		_routerDao.release(routerId);
        	}
        }
     }
    
    
    private void releaseGuestIpAddress( UserVmVO userVm)  {
    	//no-op
    }

    @Override @DB
    public UserVmVO createVirtualMachine(Long vmId, long userId, AccountVO account, DataCenterVO dc, ServiceOfferingVO offering, DiskOfferingVO dataDiskOffering, VMTemplateVO template, DiskOfferingVO rootDiskOffering, String displayName, String group, String userData, List<StoragePoolVO> avoids) throws InternalErrorException, ResourceAllocationException {
        long accountId = account.getId();
        long dataCenterId = dc.getId();
        long serviceOfferingId = offering.getId();
        long templateId = -1;
        UserVmVO vm = null;

        if (template != null)
        	templateId = template.getId();
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating vm for account id=" + account.getId() +
            	", name="+ account.getAccountName() + "; dc=" + dc.getName() +
            	"; offering=" + offering.getId() + "; diskOffering=" + dataDiskOffering.getId() +
            	"; template=" + templateId);
        }

        DomainRouterVO router = _routerDao.findBy(accountId, dataCenterId);
        if (router == null) {
            throw new InternalErrorException("Cannot find a router for account (" + accountId + "/" +
            	account.getAccountName() + ") in " + dataCenterId);
        }
        
        // Determine the Guest OS Id
        long guestOSId;
        if (template != null) {
        	guestOSId = template.getGuestOSId();
        } else {
        	throw new InternalErrorException("No template or ISO was specified for the VM.");
        }
        long numVolumes = -1;
        Transaction txn = Transaction.currentTxn();
        long routerId = router.getId();
        
        String name;
        txn.start();
        
        account = _accountDao.lock(accountId, true);
        if (account == null) {
            throw new InternalErrorException("Unable to lock up the account: " + accountId);
        }

        // First check that the maximum number of UserVMs for the given accountId will not be exceeded
        if (_accountMgr.resourceLimitExceeded(account, ResourceType.user_vm)) {
            ResourceAllocationException rae = new ResourceAllocationException("Maximum number of virtual machines for account: " + account.getAccountName() + " has been exceeded.");
            rae.setResourceType("vm");
            throw rae;
        }
        numVolumes = (dataDiskOffering.getDiskSize() == 0) ? 1 : 2;
        _accountMgr.incrementResourceCount(account.getId(), ResourceType.user_vm);
        _accountMgr.incrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
        txn.commit();
        
        name = VirtualMachineName.getVmName(vmId, accountId, _instance);

        String eventParams = "id=" + vmId + "\nvmName=" + name + "\nsoId=" + serviceOfferingId + "\ndoId=" + dataDiskOffering.getId() + "\ntId=" + templateId + "\ndcId=" + dataCenterId;
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(EventTypes.EVENT_VM_CREATE);
        event.setParameters(eventParams);

        try {
            HostPodVO pod = null;
            long poolid = 0;
            Set<Long> podsToAvoid = new HashSet<Long>();

            while ((pod = _agentMgr.findPod(template, offering, dc, account.getId(), podsToAvoid)) != null) {
                if (vm == null) {
                    vm = new UserVmVO(vmId, name, templateId, guestOSId, accountId, account.getDomainId().longValue(),
                    		serviceOfferingId, null, null, router.getGuestNetmask(),
                    		null,null,null,
                    		routerId, pod.getId(), dataCenterId,
                    		offering.getOfferHA(), displayName, group, userData);
                    vm.setMirroredVols(dataDiskOffering.getMirrored());

                    vm = _vmDao.persist(vm);
                } else {
                    vm.setPodId(pod.getId());
                    _vmDao.updateIf(vm, Event.OperationRetry, null);
                }
                
                String ipAddressStr = acquireGuestIpAddress(dataCenterId, accountId, vm);
                if (ipAddressStr == null) {
                	s_logger.warn("Failed user vm creation : no guest ip address available");
                 	releaseGuestIpAddress(vm);
                 	ResourceAllocationException rae = new ResourceAllocationException("No guest ip addresses available for " + account.getAccountName() + " (try destroying some instances)");
                	rae.setResourceType("vm");
                	throw rae;
                }

            	poolid = _storageMgr.createUserVM(account, vm, template, dc, pod, offering,  rootDiskOffering,  dataDiskOffering, avoids);
                if ( poolid != 0) {
                    break;
                }
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Unable to find storage host in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
                }
                
                podsToAvoid.add(pod.getId());
            }

            if ((vm == null) || (poolid == 0)) {
                throw new ResourceAllocationException("Create VM " + vmId + " failed due to no Storage Pool is available");
            }

            txn.start();

            event.setDescription("successfully created VM instance : " + vm.getName());
            _eventDao.persist(event);
            
            if (dataDiskOffering.getId() > 0) {
            	// save off the vm_disk entry
            	VmDiskVO vmDisk = new VmDiskVO(vm.getId(), dataDiskOffering.getId());
				_vmDiskDao.persist(vmDisk);
        	}
            vm.setPoolId(poolid);
            _vmDao.updateIf(vm, Event.OperationSucceeded, null);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("vm created " + vmId);
            }
            txn.commit();

            return _vmDao.findById(vmId);
        } catch (Throwable th) {
            s_logger.error("Unable to create vm", th);
            if (vm != null) {
            	_vmDao.delete(vmId);
            }
            _accountMgr.decrementResourceCount(account.getId(), ResourceType.user_vm);
            _accountMgr.decrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
            event.setDescription("failed to create VM instance : " + ((vm == null) ? ((name != null) ? name : "new instance") : vm.getName()));
            event.setLevel(EventVO.LEVEL_ERROR);
            _eventDao.persist(event);

            if (th instanceof ResourceAllocationException) {
                throw (ResourceAllocationException)th;
            }
            throw new CloudRuntimeException("Unable to create vm", th);
        }
    }

    @Override @DB
    public boolean destroyVirtualMachine(long userId, long vmId) {
        UserVmVO vm = _vmDao.findById(vmId);
        if (vm == null || vm.getState() == State.Destroyed || vm.getState() == State.Expunging || vm.getRemoved() != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find vm or vm is destroyed: " + vmId);
            }
            return true;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Destroying vm " + vmId);
        }
        
        if (!stop(userId, vm)) {
        	s_logger.error("Unable to stop vm so we can't destroy it: " + vmId);
        	return false;
        }
        
        Transaction txn = Transaction.currentTxn();
        txn.start();
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_DESTROY);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
        event.setDescription("successfully destroyed VM instance : " + vm.getName());
        _eventDao.persist(event);

        _accountMgr.decrementResourceCount(vm.getAccountId(), ResourceType.user_vm);

        if (!destroy(vm)) {
        	return false;
        }
        
        cleanNetworkRules(userId, vmId);
        
        // Mark the VM's disks as destroyed
        List<VolumeVO> volumes = _volsDao.findByInstance(vmId);
        for (VolumeVO volume : volumes) {
        	_volsDao.destroyVolume(volume.getId());
            String eventParams = "id=" + volume.getId();
            event = new EventVO();
            event.setAccountId(volume.getAccountId());
            event.setUserId(1L);
            event.setType(EventTypes.EVENT_VOLUME_DELETE);
            event.setParameters(eventParams);
            event.setDescription("Volume deleted");
            event.setLevel(EventVO.LEVEL_INFO);
            _eventDao.persist(event);
        }
        
        _accountMgr.decrementResourceCount(vm.getAccountId(), ResourceType.volume, new Long(volumes.size()));

        txn.commit();
        return true;
    }

    @Override @DB
    public boolean executeDestroyVM(DestroyVMExecutor executor, VMOperationParam param) {
        UserVmVO vm = _vmDao.findById(param.getVmId());
        State state = vm.getState();
        if (vm == null || state == State.Destroyed || state == State.Expunging || vm.getRemoved() != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find vm or vm is destroyed: " + param.getVmId());
            }
            
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, 0, "VM does not exist or already in destroyed state");
        	return true;
        }
        
        if(state == State.Stopping) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("VM is being stopped: " + param.getVmId());
            }
            
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, 0, "VM is being stopped, please re-try later");
        	return true;
        }

        if (state == State.Running) {
            if (vm.getHostId() == null) {
            	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
                		AsyncJobResult.STATUS_FAILED, 0, "VM host is null (invalid VM)");
                    	
            	if(s_logger.isDebugEnabled())
            		s_logger.debug("Execute asynchronize destroy VM command: VM host is null (invalid VM)");
                return true;
            }
        	
            if (!_vmDao.updateIf(vm, Event.StopRequested, vm.getHostId())) {
            	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
                		AsyncJobResult.STATUS_FAILED, 0, "Failed to issue stop command, please re-try later");
                    	
            	if(s_logger.isDebugEnabled())
            		s_logger.debug("Execute asynchronize destroy VM command: failed to issue stop command, please re-try later");
                return true;
            }
            
            StopCommand cmd = new StopCommand(vm, vm.getInstanceName(), vm.getVnet());
            try {
    			long seq = _agentMgr.send(vm.getHostId(), new Command[] {cmd}, true,
    				new VMOperationListener(executor, param, vm, 0));
    			
            	if(s_logger.isDebugEnabled())
            		s_logger.debug("Execute asynchronize destroy VM command: sending stop command to agent, seq - " + seq);
            	
            	return false;
    		} catch (AgentUnavailableException e) {
            	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "Agent is not available");
            	return true;
    		}
        }
        
        Transaction txn = Transaction.currentTxn();
        txn.start();
        
        EventVO event = new EventVO();
        event.setUserId(param.getUserId());
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_DESTROY);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
        event.setDescription("successfully destroyed VM instance : " + vm.getName());
        _eventDao.persist(event);
        
        _accountMgr.decrementResourceCount(vm.getAccountId(), ResourceType.user_vm);
        if (!_vmDao.updateIf(vm, VirtualMachine.Event.DestroyRequested, vm.getHostId())) {
            s_logger.debug("Unable to destroy the vm because it is not in the correct state: " + vm.toString());
            
            txn.rollback();
        	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "Unable to destroy the vm because it is not in the correct state");
            return true;
        }

        // Now that the VM is destroyed, clean the network rules associated with it.
        cleanNetworkRules(param.getUserId(), vm.getId().longValue());

        // Mark the VM's disks as destroyed
        List<VolumeVO> volumes = _volsDao.findByInstance(param.getVmId());
        for (VolumeVO volume : volumes) {
        	_volsDao.destroyVolume(volume.getId());
        	 String eventParams = "id=" + volume.getId();
             event = new EventVO();
             event.setAccountId(volume.getAccountId());
             event.setUserId(1L);
             event.setType(EventTypes.EVENT_VOLUME_DELETE);
             event.setParameters(eventParams);
             event.setDescription("Volume deleted");
             event.setLevel(EventVO.LEVEL_INFO);
             _eventDao.persist(event);
        }
        
        _accountMgr.decrementResourceCount(vm.getAccountId(), ResourceType.volume, new Long(volumes.size()));
        
        txn.commit();
    	executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
    		AsyncJobResult.STATUS_SUCCEEDED, 0, "success");
    	return true;
    }
    
    @Override @DB
    public boolean recoverVirtualMachine(long vmId) throws ResourceAllocationException {
        UserVmVO vm = _vmDao.findById(vmId);
        if (vm == null || vm.getRemoved() != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find vm or vm is removed: " + vmId);
            }
            return false;
        }
        
        if (vm.getState() != State.Destroyed) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("vm is not in the right state: " + vmId);
            }
        	return true;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Recovering vm " + vmId);
        }

        EventVO event = new EventVO();
        event.setUserId(1L);
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_CREATE);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
        
        Transaction txn = Transaction.currentTxn();
        AccountVO account = null;
    	txn.start();

        account = _accountDao.lock(vm.getAccountId(), true);
        
        _haMgr.cancelDestroy(vm, vm.getHostId());
        
    	// First check that the maximum number of UserVMs for the given accountId will not be exceeded
        if (_accountMgr.resourceLimitExceeded(account, ResourceType.user_vm)) {
        	ResourceAllocationException rae = new ResourceAllocationException("Maximum number of virtual machines for account: " + account.getAccountName() + " has been exceeded.");
        	rae.setResourceType("vm");
        	throw rae;
        }

        _accountMgr.incrementResourceCount(account.getId(), ResourceType.user_vm);

        if (!_vmDao.updateIf(vm, Event.RecoveryRequested, null)) {
            s_logger.debug("Unable to recover the vm because it is not in the correct state: " + vmId);
            return false;
        }
        
        // Recover the VM's disks
        List<VolumeVO> volumes = _volsDao.findByInstanceIdDestroyed(vmId);
        for (VolumeVO volume : volumes) {
        	_volsDao.recoverVolume(volume.getId());
            // Create an event
            long templateId = -1;
            long diskOfferingId = -1;
            if(volume.getTemplateId() !=null){
                templateId = volume.getTemplateId();
            }
            if(volume.getDiskOfferingId() !=null){
                diskOfferingId = volume.getDiskOfferingId();
            }
            long sizeMB = volume.getSize()/(1024*1024);
            String eventParams = "id=" + volume.getId() +"\ndoId="+diskOfferingId+"\ntId="+templateId+"\ndcId="+volume.getDataCenterId()+"\nsize="+sizeMB;
            EventVO volEvent = new EventVO();
            volEvent.setAccountId(volume.getAccountId());
            volEvent.setUserId(1L);
            volEvent.setType(EventTypes.EVENT_VOLUME_CREATE);
            volEvent.setParameters(eventParams);
            StoragePoolVO pool = _storagePoolDao.findById(volume.getPoolId());
            volEvent.setDescription("Created volume: "+ volume.getName() +" with size: " + sizeMB + " MB in pool: " + pool.getName());
            _eventDao.persist(volEvent);
        }
        
        _accountMgr.incrementResourceCount(account.getId(), ResourceType.volume, new Long(volumes.size()));
        
        event.setDescription("successfully recovered VM instance : " + vm.getName());
        _eventDao.persist(event);
        
        txn.commit();
        
        return true;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;

        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        _configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }

        Map<String, String> configs = _configDao.getConfiguration("AgentManager", params);

        String value = configs.get("start.retry");
        _retry = NumbersUtil.parseInt(value, 2);

        _instance = configs.get("instance.name");
        if (_instance == null) {
            _instance = "DEFAULT";
        }
        
        String workers = configs.get("expunge.workers");
        int wrks = NumbersUtil.parseInt(workers, 10);
        
        String time = configs.get("expunge.interval");
        _expungeInterval = NumbersUtil.parseInt(time, 86400);
        
        time = configs.get("expunge.delay");
        _expungeDelay = NumbersUtil.parseInt(time, _expungeInterval);
        
        String maxCap = configs.get("cpu.uservm.cap");
        _userVMCap = NumbersUtil.parseInt(maxCap, 0);
        
        _executor = Executors.newScheduledThreadPool(wrks, new NamedThreadFactory("UserVm-Scavenger"));
        
        _haMgr.registerHandler(Type.User, this);
        
        s_logger.info("User VM Manager is configured.");

        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
    	_executor.scheduleWithFixedDelay(new ExpungeTask(this), _expungeInterval, _expungeInterval, TimeUnit.SECONDS);
        return true;
    }

    @Override
    public boolean stop() {
    	_executor.shutdown();
        return true;
    }

    protected UserVmManagerImpl() {
    }

    @Override
    public Command cleanup(UserVmVO vm, String vmName) {
        if (vmName != null) {
            return new StopCommand(vm, vmName, VirtualMachineName.getVnet(vmName));
        } else if (vm != null) {
            return new StopCommand(vm, vm.getVnet());
        } else {
            throw new CloudRuntimeException("Shouldn't even be here!");
        }
    }

    @Override
    public void completeStartCommand(UserVmVO vm) {
    	_vmDao.updateIf(vm, Event.AgentReportRunning, vm.getHostId());
    }
    
    @Override
    public void completeStopCommand(UserVmVO instance) {
    	completeStopCommand(1L, instance, Event.AgentReportStopped);
    }
    
    @Override
    @DB
    public void completeStopCommand(long userId, UserVmVO vm, Event e) {
        Transaction txn = Transaction.currentTxn();
        try {
            vm.setVnet(null);
            vm.setProxyAssignTime(null);
            vm.setProxyId(null);
            vm.setStorageIp(null);

            txn.start();
            if (!_vmDao.updateIf(vm, e, null)) {
            	s_logger.debug("Unable to update ");
            	return;
            }
            txn.commit();

        } catch (Throwable th) {
            s_logger.error("Error during stop: ", th);
            throw new CloudRuntimeException("Error during stop: ", th);
        }

        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_STOP);
        event.setParameters("id="+vm.getId() + "\n" + "vmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
        event.setDescription("Successfully stopped VM instance : " + vm.getName());
        _eventDao.persist(event);

        if (_storageMgr.unshare(vm, null) == null) {
            s_logger.warn("Unable to set share to false for " + vm.toString());
        }
    }

    @Override
    public UserVmVO get(long id) {
        return getVirtualMachine(id);
    }
    
    public String getRandomPrivateTemplateName() {
    	return UUID.randomUUID().toString();
    }

    @Override
    public Long convertToId(String vmName) {
        if (!VirtualMachineName.isValidVmName(vmName, _instance)) {
            return null;
        }
        return VirtualMachineName.getVmId(vmName);
    }

    @Override
    public UserVmVO start(long vmId) throws StorageUnavailableException, ConcurrentOperationException {
        return start(1L, vmId, null, null);
    }

    @Override
    public boolean stop(UserVmVO vm) {
        return stop(1L, vm);
    }

    private boolean stop(long userId, UserVmVO vm) {
        State state = vm.getState();
        if (state == State.Stopped) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("VM is already stopped: " + vm.toString());
            }
            return true;
        }
        
        if (state == State.Creating || state == State.Destroyed || state == State.Expunging) {
        	s_logger.warn("Stopped called on " + vm.toString() + " but the state is " + state.toString());
        	return true;
        }
        
        if (!_vmDao.updateIf(vm, Event.StopRequested, vm.getHostId())) {
            s_logger.debug("VM is not in a state to stop: " + vm.getState().toString());
            return false;
        }
        
        if (vm.getHostId() == null) {
        	s_logger.debug("Host id is null so we can't stop it.  How did we get into here?");
        	return false;
        }

        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_STOP);
        event.setParameters("id="+vm.getId() + "\n" + "vmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());

        StopCommand stop = new StopCommand(vm, vm.getInstanceName(), vm.getVnet());

        boolean stopped = false;
        try {
            Answer answer = _agentMgr.send(vm.getHostId(), stop);
            if (!answer.getResult()) {
                s_logger.warn("Unable to stop vm " + vm.getName() + " due to " + answer.getDetails());
            } else {
            	stopped = true;
            }
        } catch(AgentUnavailableException e) {
            s_logger.warn("Agent is not available to stop vm " + vm.toString());
        } catch(OperationTimedoutException e) {
        	s_logger.warn("operation timed out " + vm.toString());
        }

        if (stopped) {
        	completeStopCommand(userId, vm, Event.OperationSucceeded);
        } else {
            event.setDescription("failed to stop VM instance : " + vm.getName());
            event.setLevel(EventVO.LEVEL_ERROR);
            _eventDao.persist(event);
            _vmDao.updateIf(vm, Event.OperationFailed, vm.getHostId());
            s_logger.error("Unable to stop vm " + vm.getName());
        }

        return stopped;
    }

    @Override @DB
    public boolean destroy(UserVmVO vm) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Destroying vm " + vm.toString());
        }
        if (!_vmDao.updateIf(vm, VirtualMachine.Event.DestroyRequested, vm.getHostId())) {
            s_logger.debug("Unable to destroy the vm because it is not in the correct state: " + vm.toString());
            return false;
        }

        return true;
    }

    @Override
    public HostVO prepareForMigration(UserVmVO vm) throws StorageUnavailableException {
        long vmId = vm.getId();
        boolean mirroredVols = vm.isMirroredVols();
        DataCenterVO dc = _dcDao.findById(vm.getDataCenterId());
        HostPodVO pod = _podDao.findById(vm.getPodId());
        ServiceOfferingVO offering = _offeringDao.findById(vm.getServiceOfferingId());
        VMTemplateVO template = _templateDao.findById(vm.getTemplateId());
        StoragePoolVO sp = _storagePoolDao.findById(vm.getPoolId());
       

        List<VolumeVO> vols = _volsDao.findByInstance(vmId);

        String [] storageIps = new String[2];
        VolumeVO vol = vols.get(0);
        storageIps[0] = vol.getHostIp();
        if (mirroredVols && (vols.size() == 2)) {
            storageIps[1] = vols.get(1).getHostIp();
        }

        PrepareForMigrationCommand cmd = new PrepareForMigrationCommand(vm.getInstanceName(), vm.getVnet(), storageIps, vols, mirroredVols);

        HostVO vmHost = null;
        HashSet<Host> avoid = new HashSet<Host>();

        HostVO fromHost = _hostDao.findById(vm.getHostId());
        avoid.add(fromHost);

        while ((vmHost = (HostVO)_agentMgr.findHost(Host.Type.Computing, dc, pod, sp, offering, null, template, null, null, avoid)) != null) {
            avoid.add(vmHost);

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to migrate router to host " + vmHost.getName());
            }
            
            if( !_storageMgr.share(vm, vols, vmHost, false) ) {
                s_logger.warn("Can not share " + vm.toString() + " on host " + vmHost.getId());
                throw new StorageUnavailableException(vmHost.getId());
            }

            Answer answer = _agentMgr.easySend(vmHost.getId(), cmd);
            if (answer != null && answer.getResult()) {
                return vmHost;
            }

            _storageMgr.unshare(vm, vols, vmHost);

        }

        return null;
    }

    @Override
    public boolean migrate(UserVmVO vm, HostVO host) throws AgentUnavailableException, OperationTimedoutException {
        HostVO fromHost = _hostDao.findById(vm.getHostId());

    	if (!_vmDao.updateIf(vm, Event.MigrationRequested, vm.getHostId())) {
    		s_logger.debug("State for " + vm.toString() + " has changed so migration can not take place.");
    		return false;
    	}
        boolean isWindows = _guestOSCategoryDao.findById(_guestOSDao.findById(vm.getGuestOSId()).getCategoryId()).getName().equalsIgnoreCase("Windows");
        MigrateCommand cmd = new MigrateCommand(vm.getInstanceName(), host.getPrivateIpAddress(), isWindows);
        Answer answer = _agentMgr.send(fromHost.getId(), cmd);
        if (answer == null) {
            return false;
        }

        List<VolumeVO> vols = _volsDao.findByInstance(vm.getId());
        if (vols.size() == 0) {
            return true;
        }

        _storageMgr.unshare(vm, vols, fromHost);

        return true;
    }

    @DB
    public void expunge() {
    	List<UserVmVO> vms = _vmDao.findDestroyedVms(new Date(System.currentTimeMillis() - ((long)_expungeDelay << 10)));
    	s_logger.info("Found " + vms.size() + " vms to expunge.");
    	for (UserVmVO vm : vms) {
    		long vmId = vm.getId();
            vm.setGuestIpAddress(null);
            vm.setGuestNetmask(null);
            vm.setGuestMacAddress(null);
    		if (!_vmDao.updateIf(vm, Event.ExpungeOperation, null)) {
    			s_logger.info("vm " + vmId + " is skipped because it is no longer in Destroyed state");
    			continue;
    		}
    		
            Transaction txn = Transaction.currentTxn();

            List<VolumeVO> vols = null;
            try {
                vols = _volsDao.findByInstanceIdDestroyed(vmId);
                if (vols.size() != 0) {
                    _storageMgr.destroy(vm, vols);                    
                }

                txn.start();

                if (vm != null) {
                    // remove the vm_disk references to the disk offerings
                    List<VmDiskVO> vmDisks = _vmDiskDao.findByInstanceId(vm.getId());
                    if (vmDisks != null) {
                        for (VmDiskVO vmDisk : vmDisks) {
                            _vmDiskDao.remove(vmDisk.getId());
                        }
                    }

                    _vmDao.remove(vm.getId());
                }
                txn.commit();
                s_logger.debug("vm is destroyed");
            } catch (Exception e) {
            	s_logger.info("VM " + vmId +" expunge failed due to " + e.getMessage());
			}
    	}
    	
    	List<VolumeVO> destroyedVolumes = _volsDao.findByDetachedDestroyed();
    	if (destroyedVolumes.size() != 0) {
    		s_logger.info("Found " + destroyedVolumes.size() + " detached volumes to expunge.");
    		_storageMgr.destroy(null, destroyedVolumes);
    	}
    }

    @Override @DB
    public boolean completeMigration(UserVmVO vm, HostVO host) throws AgentUnavailableException, OperationTimedoutException {
        CheckVirtualMachineCommand cvm = new CheckVirtualMachineCommand(vm.getInstanceName());
        CheckVirtualMachineAnswer answer = (CheckVirtualMachineAnswer)_agentMgr.send(host.getId(), cvm);
        if (!answer.getResult()) {
            s_logger.debug("Unable to complete migration for " + vm.toString());
            _vmDao.updateIf(vm, Event.AgentReportStopped, null);
            return false;
        }

        State state = answer.getState();
        if (state == State.Stopped) {
            s_logger.warn("Unable to complete migration as we can not detect it on " + host.toString());
            _vmDao.updateIf(vm, Event.AgentReportStopped, null);
            return false;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Marking port " + answer.getVncPort() + " on " + host.getId());
        }

        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            _vmDao.updateIf(vm, Event.OperationSucceeded, host.getId());
            txn.commit();
            
            return true;
        } catch(Exception e) {
            s_logger.warn("Exception during completion of migration process " + vm.toString());
            return false;
        }
    }

    @Override
    public boolean destroyTemplateSnapshot(Long userId, long snapshotId) {
        // Long winded way of doing this.
        _snapshotDao.remove(snapshotId);
        return true;
    }
    
    @Override @DB
    public SnapshotVO createTemplateSnapshot(long userId, long volumeId) {
        SnapshotVO createdSnapshot = null;
        VolumeVO volume = _volsDao.findById(volumeId);
        
        Long id = null;
        
        // Determine the name for this snapshot
        String timeString = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, new Date(), DateUtil.YYYYMMDD_FORMAT);
        String snapshotName = volume.getName() + "_" + timeString;
        // Create the Snapshot object and save it so we can return it to the user
        SnapshotType snapshotType = SnapshotType.TEMPLATE;
        SnapshotVO snapshot = new SnapshotVO(volume.getAccountId(), volume.getId(), null, snapshotName, (short)snapshotType.ordinal(), snapshotType.name());
        snapshot = _snapshotDao.persist(snapshot);
        id = snapshot.getId();

        // Send a ManageSnapshotCommand to the agent
        ManageSnapshotCommand cmd = new ManageSnapshotCommand(ManageSnapshotCommand.CREATE_SNAPSHOT, id, volume.getPath(), snapshotName);
        
        String basicErrMsg = "Failed to create snapshot for volume: " + volume.getId();
        ManageSnapshotAnswer answer = (ManageSnapshotAnswer) _storageMgr.sendToStorageHostsOnPool(volume.getPoolId(), cmd, basicErrMsg);
        
        // Update the snapshot in the database
        if ((answer != null) && answer.getResult()) {
            // The snapshot was successfully created
            
            Transaction txn = Transaction.currentTxn();
            txn.start();
            createdSnapshot = _snapshotDao.findById(id);
            createdSnapshot.setPath(answer.getSnapshotPath());
            createdSnapshot.setStatus(Snapshot.Status.CreatedOnPrimary);
            _snapshotDao.update(id, createdSnapshot);
            txn.commit();
            
            // Don't Create an event for Template Snapshots for now.
        } else {
        	if (answer != null) {
        		s_logger.error(answer.getDetails());
        	}
            // The snapshot was not successfully created
            Transaction txn = Transaction.currentTxn();
            txn.start();
            createdSnapshot = _snapshotDao.findById(id);
            _snapshotDao.delete(id);
            txn.commit();
            
            createdSnapshot = null;
        }

        return createdSnapshot;
    }

    
    @Override
    public void cleanNetworkRules(long userId, long instanceId) {
        UserVmVO vm = _vmDao.findById(instanceId);
        String guestIpAddr = vm.getGuestIpAddress();
        long accountId = vm.getAccountId();

        // clean up any load balancer rules and security group mappings for this VM
        List<SecurityGroupVMMapVO> securityGroupMappings = _securityGroupVMMapDao.listByInstanceId(vm.getId());
        for (SecurityGroupVMMapVO securityGroupMapping : securityGroupMappings) {
            String ipAddress = securityGroupMapping.getIpAddress();

            // find the router from the ipAddress
            DomainRouterVO router = _routerDao.findById(vm.getDomainRouterId());

            // grab all the firewall rules
            List<FirewallRuleVO> fwRules = _rulesDao.listForwardingByPubAndPrivIp(true, ipAddress, vm.getGuestIpAddress());
            for (FirewallRuleVO fwRule : fwRules) {
                fwRule.setEnabled(false);
            }

            List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(ipAddress, fwRules, router);

            // Save and create the event
            String description;
            String type = EventTypes.EVENT_NET_RULE_DELETE;
            String ruleName = "ip forwarding";
            String level = EventVO.LEVEL_INFO;

            if (updatedRules != null) {
                _securityGroupVMMapDao.remove(securityGroupMapping.getId());
                for (FirewallRuleVO updatedRule : updatedRules) {
                _rulesDao.remove(updatedRule.getId());

                    description = "deleted " + ruleName + " rule [" + updatedRule.getPublicIpAddress() + ":" + updatedRule.getPublicPort() +
                              "]->[" + updatedRule.getPrivateIpAddress() + ":" + updatedRule.getPrivatePort() + "]" + " " + updatedRule.getProtocol();

                EventVO fwRuleEvent = new EventVO();
                fwRuleEvent.setUserId(userId);
                fwRuleEvent.setAccountId(accountId);
                fwRuleEvent.setType(type);
                fwRuleEvent.setDescription(description);
                    fwRuleEvent.setLevel(level);
                _eventDao.persist(fwRuleEvent);
            }
            // save off an event for removing the security group
            EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(vm.getAccountId());
            event.setType(EventTypes.EVENT_PORT_FORWARDING_SERVICE_REMOVE);
            event.setDescription("Successfully removed port forwarding service " + securityGroupMapping.getSecurityGroupId() + " from virtual machine " + vm.getName());
            event.setLevel(EventVO.LEVEL_INFO);
            String params = "sgId="+securityGroupMapping.getSecurityGroupId()+"\nvmId="+vm.getId();
            event.setParameters(params);
            _eventDao.persist(event);
            }
        }

        List<LoadBalancerVMMapVO> loadBalancerMappings = _loadBalancerVMMapDao.listByInstanceId(vm.getId());
        for (LoadBalancerVMMapVO loadBalancerMapping : loadBalancerMappings) {
            List<FirewallRuleVO> lbRules = _rulesDao.listByLoadBalancerId(loadBalancerMapping.getLoadBalancerId());
            FirewallRuleVO targetLbRule = null;
            for (FirewallRuleVO lbRule : lbRules) {
                if (lbRule.getPrivateIpAddress().equals(guestIpAddr)) {
                    targetLbRule = lbRule;
                    targetLbRule.setEnabled(false);
                    break;
                }
            }

            if (targetLbRule != null) {
                String ipAddress = targetLbRule.getPublicIpAddress();
                DomainRouterVO router = _routerDao.findById(vm.getDomainRouterId());
                _networkMgr.updateFirewallRules(ipAddress, lbRules, router);

                // now that the rule has been disabled, delete it, also remove the mapping from the load balancer mapping table
                _rulesDao.remove(targetLbRule.getId());
                _loadBalancerVMMapDao.remove(loadBalancerMapping.getId());

                // save off the event for deleting the LB rule
                EventVO lbRuleEvent = new EventVO();
                lbRuleEvent.setUserId(userId);
                lbRuleEvent.setAccountId(accountId);
                lbRuleEvent.setType(EventTypes.EVENT_NET_RULE_DELETE);
                lbRuleEvent.setDescription("deleted load balancer rule [" + targetLbRule.getPublicIpAddress() + ":" + targetLbRule.getPublicPort() +
                        "]->[" + targetLbRule.getPrivateIpAddress() + ":" + targetLbRule.getPrivatePort() + "]" + " " + targetLbRule.getAlgorithm());
                lbRuleEvent.setLevel(EventVO.LEVEL_INFO);
                _eventDao.persist(lbRuleEvent);
            }
        }
    }
    
    public VMTemplateVO createPrivateTemplateRecord(Long userId, long volumeId, String name, String description, long guestOSId, Boolean requiresHvm, Integer bits, Boolean passwordEnabled, boolean isPublic, boolean featured)
    	throws InvalidParameterValueException {

    	VMTemplateVO privateTemplate = null;

    	UserVO user = _userDao.findById(userId);
    	
    	if (user == null) {
    		throw new InvalidParameterValueException("User " + userId + " does not exist");
    	}

    	VolumeVO volume = _volsDao.findById(volumeId);
    	if (volume == null) {
            throw new InvalidParameterValueException("Volume with ID: " + volumeId + " does not exist");
    	}

    	int bitsValue = ((bits == null) ? 64 : bits.intValue());
    	boolean requiresHvmValue = ((requiresHvm == null) ? true : requiresHvm.booleanValue());
    	boolean passwordEnabledValue = ((passwordEnabled == null) ? false : passwordEnabled.booleanValue());

    	// if the volume is a root disk, try to find out requiresHvm and bits if possible
    	if (Volume.VolumeType.ROOT.equals(volume.getVolumeType())) {
    	    Long instanceId = volume.getInstanceId();
    	    if (instanceId != null) {
    	        UserVm vm = _vmDao.findById(instanceId);
    	        if (vm != null) {
    	            VMTemplateVO origTemplate = _templateDao.findById(vm.getTemplateId());
    	            if (!ImageFormat.ISO.equals(origTemplate.getFormat()) && !ImageFormat.RAW.equals(origTemplate.getFormat())) {
    	                bitsValue = origTemplate.getBits();
    	                requiresHvmValue = origTemplate.requiresHvm();
    	            }
    	        }
    	    }
    	}

    	GuestOSVO guestOS = _guestOSDao.findById(guestOSId);
    	if (guestOS == null) {
    		throw new InvalidParameterValueException("GuestOS with ID: " + guestOSId + " does not exist.");
    	}

        String uniqueName = Long.valueOf((userId == null)?1:userId).toString() + Long.valueOf(volumeId).toString() + UUID.nameUUIDFromBytes(name.getBytes()).toString();
    	Long nextTemplateId = _templateDao.getNextInSequence(Long.class, "id");

        privateTemplate = new VMTemplateVO(nextTemplateId,
                                           uniqueName,
                                           name,
                                           ImageFormat.RAW,
                                           isPublic,
                                           featured,
                                           null,
                                           null,
                                           null,
                                           requiresHvmValue,
                                           bitsValue,
                                           volume.getAccountId(),
                                           null,
                                           description,
                                           passwordEnabledValue,
                                           guestOS.getId(),
                                           true);

        return _templateDao.persist(privateTemplate);
    }

    @Override @DB
    public VMTemplateVO createPrivateTemplate(VMTemplateVO template, Long userId, long snapshotId, String name, String description) {
    	VMTemplateVO privateTemplate = null;
    	long templateId = template.getId();
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);        
        if (snapshot != null) {        	
        	Long volumeId = snapshot.getVolumeId();
            VolumeVO volume = _volsDao.findById(volumeId);
            StringBuilder userFolder = new StringBuilder();
            Formatter userFolderFormat = new Formatter(userFolder);
            userFolderFormat.format("u%06d", snapshot.getAccountId());

            String uniqueName = getRandomPrivateTemplateName();

            long zoneId = volume.getDataCenterId();
            HostVO secondaryStorageHost = _storageMgr.getSecondaryStorageHost(zoneId);
            String secondaryStorageURL = _storageMgr.getSecondaryStorageURL(zoneId);

            if (secondaryStorageHost == null || secondaryStorageURL == null) {
            	s_logger.warn("Did not find the secondary storage URL in the database.");
            	return null;
            }
            
            Command cmd = null;
            String backupSnapshotUUID = snapshot.getBackupSnapshotId();
            if (backupSnapshotUUID != null) {
                // We are creating a private template from a snapshot which has been backed up to secondary storage.
                Long dcId = volume.getDataCenterId();
                Long accountId = volume.getAccountId();
                // The volume is a ROOT DISK and has a template.
                Long origTemplateId = volume.getTemplateId();
                // This has been checked at the API level itself.
                assert origTemplateId != null;
                VMTemplateHostVO vmTemplateHostVO = _templateHostDao.findByHostTemplate(secondaryStorageHost.getId(), origTemplateId);
                assert vmTemplateHostVO != null;
                String origTemplateInstallPath = vmTemplateHostVO.getInstallPath();
                cmd = new CreatePrivateTemplateFromSnapshotCommand(volume.getFolder(),
                                                                   secondaryStorageURL,
                                                                   dcId,
                                                                   accountId,
                                                                   volumeId,
                                                                   backupSnapshotUUID,
                                                                   origTemplateInstallPath,
                                                                   templateId,
                                                                   name);
            }
            else {
                cmd = new CreatePrivateTemplateCommand(secondaryStorageURL,
                                                       templateId,
                                                       volume.getAccountId(),
                                                       name,
                                                       uniqueName,
                                                       volume.getFolder(),
                                                       snapshot.getPath(),
                                                       snapshot.getName(),
                                                       userFolder.toString());
            }
            
            // FIXME: before sending the command, check if there's enough capacity on the storage server to create the template

            String basicErrMsg = "Failed to create template from snapshot: " + snapshot.getName();
            
            CreatePrivateTemplateAnswer answer = (CreatePrivateTemplateAnswer) _storageMgr.sendToStorageHostsOnPool(volume.getPoolId(), cmd, basicErrMsg);
            // Don't proceed further if there was an exception above.
            if (answer == null) {
                return null;
            }
            
            String eventParams = "id="+templateId+"\nname=" + name + "\ndcId=" + zoneId +"\nsize="+volume.getSize();
            EventVO event = new EventVO();
            event.setUserId(userId.longValue());
            event.setAccountId(snapshot.getAccountId());
            event.setType(EventTypes.EVENT_TEMPLATE_CREATE);
            event.setParameters(eventParams);

            if ((answer != null) && answer.getResult()) {
                
                // save the snapshot in the database
                Transaction txn = Transaction.currentTxn();
                txn.start();
                
                privateTemplate = _templateDao.findById(templateId);
                Long origTemplateId = volume.getTemplateId();
                VMTemplateVO origTemplate = null;
                if (origTemplateId != null) {
                	origTemplate = _templateDao.findById(templateId);
                }

                if ((origTemplate != null) && !Storage.ImageFormat.ISO.equals(origTemplate.getFormat())) {
                	// We made a template from a root volume that was cloned from a template
                	privateTemplate.setDiskType(origTemplate.getDiskType());
                	privateTemplate.setRequiresHvm(origTemplate.requiresHvm());
                	privateTemplate.setBits(origTemplate.getBits());
                } else {
                	// We made a template from a root volume that was not cloned from a template, or a data volume
                	privateTemplate.setDiskType("none");
                	privateTemplate.setRequiresHvm(true);
                	privateTemplate.setBits(64);
                }

                String answerUniqueName = answer.getUniqueName();
                if (answerUniqueName != null) {
                	privateTemplate.setUniqueName(answerUniqueName);
                } else {
                	privateTemplate.setUniqueName(uniqueName);
                }
                ImageFormat format = answer.getImageFormat();
                if (format != null) {
                	privateTemplate.setFormat(format);
                }
                else {
                	// This never occurs.
                	// Specify RAW format makes it unusable for snapshots.
                	privateTemplate.setFormat(ImageFormat.RAW);
                }

                _templateDao.update(templateId, privateTemplate);

                // add template zone ref for this template
                _templateDao.addTemplateToZone(privateTemplate, zoneId);
                VMTemplateHostVO templateHostVO = new VMTemplateHostVO(secondaryStorageHost.getId(), templateId);
                templateHostVO.setDownloadPercent(100);
                templateHostVO.setDownloadState(Status.DOWNLOADED);
                templateHostVO.setInstallPath(answer.getPath());
                templateHostVO.setLastUpdated(new Date());
                templateHostVO.setSize(answer.getVirtualSize());
                _templateHostDao.persist(templateHostVO);
                
                event.setDescription("Created private template " + name + " from snapshot " + snapshotId);
                event.setLevel(EventVO.LEVEL_INFO);
                _eventDao.persist(event);
                
                // Increment the number of templates
                _accountMgr.incrementResourceCount(volume.getAccountId(), ResourceType.template);
                
                txn.commit();
                
            } else {
            	Transaction txn = Transaction.currentTxn();
                txn.start();
            	
                event.setDescription("Failed to create private template " + name + " from snapshot " + snapshotId);
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                
                // Remove the template record
                _templateDao.remove(templateId);
                
                txn.commit();
            }

            
        }
        return privateTemplate;
    }
    
    @DB
	public UserVmVO createDirectlyAttachedVM(Long vmId, long userId, AccountVO account, DataCenterVO dc, ServiceOfferingVO offering, DiskOfferingVO dataDiskOffering, VMTemplateVO template, DiskOfferingVO rootDiskOffering, String displayName, String group, String userData, List<StoragePoolVO> a) throws InternalErrorException, ResourceAllocationException {
	    long accountId = account.getId();
	    long dataCenterId = dc.getId();
	    long serviceOfferingId = offering.getId();
	    long templateId = -1;
	    if (template != null)
	    	templateId = template.getId();
	    
	    if (s_logger.isDebugEnabled()) {
	        s_logger.debug("Creating directly attached vm for account id=" + account.getId() +
	        	", name="+ account.getAccountName() + "; dc=" + dc.getName() +
	        	"; offering=" + offering.getId() + "; diskOffering=" + dataDiskOffering.getId() +
	        	"; template=" + templateId);
	    }
	    
	    // Determine the Guest OS Id
        long guestOSId;
        if (template != null) {
        	guestOSId = template.getGuestOSId();
        } else {
        	throw new InternalErrorException("No template or ISO was specified for the VM.");
        }
	    
	    Transaction txn = Transaction.currentTxn();
	    try {
	        if (template != null)
	        	_diskDao.findByTypeAndSize(template.getDiskType(), dataDiskOffering.getDiskSize());
	        
	        UserVmVO vm = null;
            String externalIp = null;
	        txn.start();
	        
	    	account = _accountDao.lock(accountId, true);
	    	if (account == null) {
	    		throw new InternalErrorException("Unable to lock up the account: " + accountId);
	    	}
	
	        // First check that the maximum number of UserVMs for the given accountId will not be exceeded
	        if (_accountMgr.resourceLimitExceeded(account, ResourceType.user_vm)) {
	        	ResourceAllocationException rae = new ResourceAllocationException("Maximum number of virtual machines for account: " + account.getAccountName() + " has been exceeded.");
	        	rae.setResourceType("vm");
	        	throw rae;
	        }
	    	
	    	final String name = VirtualMachineName.getVmName(vmId, accountId, _instance);
	
	        final String[] macAddresses = _dcDao.getNextAvailableMacAddressPair(dc.getId());
	        long routerId = -1;
	        long poolId = 0;
	        HostPodVO pod = null;
	        DomainRouterVO router = null;
            Set<Long> avoids = new HashSet<Long>();
            while ((pod = _agentMgr.findPod(template, offering, dc, account.getId(), avoids)) != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Attempting to create direct attached vm in pod " + pod.getName());
                }
                List<VlanVO> vlansForPod = _vlanDao.listVlansForPodByType(pod.getId(), VlanType.DcInternal);
                if (vlansForPod.size() < 1) {
                	avoids.add(pod.getId());
	                if (s_logger.isDebugEnabled()) {
	                    s_logger.debug("No internal vlans available in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
	                }
                	continue;
                }
                VlanVO guestVlan = vlansForPod.get(0);//FIXME: iterate over all vlans
                List<DomainRouterVO> rtrs = _routerDao.listByVlanDbId(guestVlan.getId());
                assert rtrs.size() < 2 : "How did we get more than one router per vlan?";
                if (rtrs.size() > 0) {
                	router =  rtrs.get(0);
                	routerId = router.getId();
                } else if (rtrs.size() == 0) {
                	router = _networkMgr.createDhcpServerForDirectlyAttachedGuests(dc, pod, guestVlan);
                	if (router == null) {
                		avoids.add(pod.getId());
    	                if (s_logger.isDebugEnabled()) {
    	                    s_logger.debug("Unable to create DHCP server in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
    	                }
                		continue;
                	}
                	routerId = router.getId();
                }
                String guestIp = _ipAddressDao.assignIpAddress(accountId, account.getDomainId().longValue(), guestVlan.getId(), false);
                if (guestIp == null) {
                	avoids.add(pod.getId());
                	continue;
                }
                String guestMacAddress = macAddresses[0];
                String externalMacAddress = macAddresses[1];
                Long externalVlanDbId = null;
                Pair<String, VlanVO> externalIpAndVlan = null;
                if (offering.getGuestIpType() == GuestIpType.DirectDual) {
                	externalIpAndVlan = _vlanDao.assignIpAddress(dc.getId(), accountId, account.getDomainId().longValue(), VlanType.DcExternal, false);
                    if (externalIpAndVlan == null) {
                    	avoids.add(pod.getId());
    	                if (s_logger.isDebugEnabled()) {
    	                    s_logger.debug("Unable to allocate second (public) ip in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
    	                }
                    	continue;
                    }
                    externalIp = externalIpAndVlan.first();
                    final VlanVO externalVlan = externalIpAndVlan.second();
                    externalVlanDbId = externalVlan.getId();
                }
            
	            vm = new UserVmVO(vmId, name, templateId, guestOSId, accountId, account.getDomainId().longValue(),
	            		serviceOfferingId, guestMacAddress, guestIp, guestVlan.getVlanNetmask(),
	            		externalIp, externalMacAddress, externalVlanDbId,
	            		routerId, pod.getId(), dataCenterId,
	            		offering.getOfferHA(), displayName, group, userData);
	            
	            vm.setMirroredVols(dataDiskOffering.getMirrored());
	
	            _vmDao.persist(vm);
	            
	            _accountMgr.incrementResourceCount(account.getId(), ResourceType.user_vm);
	            long numVolumes = (dataDiskOffering.getDiskSize() == 0) ? 1 : 2;
	            _accountMgr.incrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
	            txn.commit();
	
	            vm = _vmDao.findById(vmId);
	        	poolId = _storageMgr.createUserVM(account,  vm, template, dc, pod, offering,  rootDiskOffering,  dataDiskOffering, a);
	            if (poolId == 0) {
	                _vmDao.delete(vmId);
	                _accountMgr.decrementResourceCount(account.getId(), ResourceType.user_vm);
	                _accountMgr.decrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
	                _ipAddressDao.unassignIpAddress(externalIp);
	                externalIp = null;
	                _ipAddressDao.unassignIpAddress(guestIp);
	                guestIp = null;
	                if (s_logger.isDebugEnabled()) {
	                    s_logger.debug("Unable to find storage host in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
	                }
                	avoids.add(pod.getId());
	                continue; // didn't find a storage host in pod, go to the next pod
	            }
	            break; // if we got here, we found a host and can stop searching the pods
	        }
	
	        txn.start();
	
	        EventVO event = new EventVO();
	        event.setUserId(userId);
	        event.setAccountId(accountId);
	        event.setType(EventTypes.EVENT_VM_CREATE);
	
	        if (poolId == 0) {
	            event.setDescription("failed to create VM instance : " + name);
	            event.setLevel(EventVO.LEVEL_ERROR);
	            _eventDao.persist(event);
		        String eventParams = "\nvmName=" + name + "\nsoId=" + serviceOfferingId + "\ndoId=" + dataDiskOffering.getId() + "\ntId=" + templateId + "\ndcId=" + dataCenterId;
		        event.setParameters(eventParams);
	            txn.commit();
	            return null;
	        }
	        String eventParams = "id=" + vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ndoId=" + dataDiskOffering.getId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId();
	        event.setParameters(eventParams);
	        event.setDescription("successfully created VM instance : " + vm.getName());
	        _eventDao.persist(event);
	        
	        if (externalIp != null) {
	        	event = new EventVO();
		        event.setUserId(userId);
		        event.setAccountId(accountId);
		        event.setType(EventTypes.EVENT_NET_IP_ASSIGN);
		        event.setParameters("external IP address=" + externalIp + "\nvmName=" + vm.getName() +  "\ndcId=" + vm.getDataCenterId());
                event.setDescription("acquired a public ip: " + externalIp);

	        }
	        
	        if (dataDiskOffering.getId() > 0) {
	        	// save off the vm_disk entry
	        	VmDiskVO vmDisk = new VmDiskVO(vm.getId(), dataDiskOffering.getId());
				_vmDiskDao.persist(vmDisk);
	    	}
	        vm.setPoolId(poolId);
	        _vmDao.updateIf(vm, Event.OperationSucceeded, null);
	        if (s_logger.isDebugEnabled()) {
	            s_logger.debug("vm created " + vmId);
	        }
	        txn.commit();
	
	        return _vmDao.findById(vmId);
	    } catch (ResourceAllocationException rae) {
	        if (s_logger.isInfoEnabled()) {
	            s_logger.info("Failed to create VM for account " + accountId + " due to maximum number of virtual machines exceeded.");
	        }
	    	throw rae;
	    } catch (Throwable th) {
	        s_logger.error("Unable to create vm", th);
	        throw new CloudRuntimeException("Unable to create vm", th);
	    }
	}

	protected class ExpungeTask implements Runnable {
    	UserVmManagerImpl _vmMgr;
    	public ExpungeTask(UserVmManagerImpl vmMgr) {
    		_vmMgr = vmMgr;
    	}

		public void run() {
			GlobalLock scanLock = GlobalLock.getInternLock("UserVMExpunge");
			try {
				if(scanLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION)) {
					try {
						reallyRun();
					} finally {
						scanLock.unlock();
					}
				} 
			} finally {
				scanLock.releaseRef();
			}
		}
    	
    	public void reallyRun() {
    		try {
    			s_logger.info("UserVm Expunge Thread is running.");
				_vmMgr.expunge();
    		} catch (Exception e) {
    			s_logger.error("Caught the following Exception", e);
    		}
    	}
    }
}
