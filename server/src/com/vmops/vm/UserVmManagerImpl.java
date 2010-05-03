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
package com.vmops.vm;

import java.util.ArrayList;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.AttachDiskCommand;
import com.vmops.agent.api.AttachIsoCommand;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetVmStatsAnswer;
import com.vmops.agent.api.GetVmStatsCommand;
import com.vmops.agent.api.ManageSnapshotAnswer;
import com.vmops.agent.api.ManageSnapshotCommand;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.RebootAnswer;
import com.vmops.agent.api.RebootCommand;
import com.vmops.agent.api.ScheduleVolumeSnapshotCommand;
import com.vmops.agent.api.StartCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.storage.CreatePrivateTemplateAnswer;
import com.vmops.agent.api.storage.CreatePrivateTemplateCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.alert.AlertManager;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.async.executor.DestroyVMExecutor;
import com.vmops.async.executor.RebootVMExecutor;
import com.vmops.async.executor.StartVMExecutor;
import com.vmops.async.executor.StopVMExecutor;
import com.vmops.async.executor.VMExecutorHelper;
import com.vmops.async.executor.VMOperationListener;
import com.vmops.async.executor.VMOperationParam;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.configuration.dao.ResourceLimitDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.Vlan.VlanType;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.domain.dao.DomainDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.ConcurrentOperationException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.exception.StorageUnavailableException;
import com.vmops.ha.HighAvailabilityManager;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.dao.DetailsDao;
import com.vmops.host.dao.HostDao;
import com.vmops.network.FirewallRuleVO;
import com.vmops.network.LoadBalancerVMMapVO;
import com.vmops.network.NetworkManager;
import com.vmops.network.SecurityGroupVMMapVO;
import com.vmops.network.dao.FirewallRulesDao;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.network.dao.LoadBalancerDao;
import com.vmops.network.dao.LoadBalancerVMMapDao;
import com.vmops.network.dao.SecurityGroupDao;
import com.vmops.network.dao.SecurityGroupVMMapDao;
import com.vmops.pricing.dao.PricingDao;
import com.vmops.server.ManagementServer;
import com.vmops.service.ServiceOffering;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.service.ServiceOffering.GuestIpType;
import com.vmops.service.dao.ServiceOfferingDao;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.GuestOSVO;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.Storage;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VmDiskVO;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.VMTemplateStorageResourceAssoc.Status;
import com.vmops.storage.VirtualMachineTemplate.BootloaderType;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.storage.dao.DiskOfferingDao;
import com.vmops.storage.dao.DiskTemplateDao;
import com.vmops.storage.dao.GuestOSDao;
import com.vmops.storage.dao.SnapshotDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.dao.VmDiskDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.AccountManager;
import com.vmops.user.AccountVO;
import com.vmops.user.ScheduledVolumeBackup;
import com.vmops.user.ScheduledVolumeBackupVO;
import com.vmops.user.UserVO;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.ScheduledVolumeBackupDao;
import com.vmops.user.dao.UserDao;
import com.vmops.user.dao.UserStatisticsDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Pair;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.ExecutionException;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.VirtualMachine.Event;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;

@Local(value={UserVmManager.class})
public class UserVmManagerImpl implements UserVmManager {
    private static final Logger s_logger = Logger.getLogger(UserVmManagerImpl.class);
   
    HostDao _hostDao = null;
    DetailsDao _detailsDao = null;
    DomainRouterDao _routerDao = null;
    ServiceOfferingDao _offeringDao = null;
    DiskOfferingDao _diskOfferingDao = null;
    VmDiskDao _vmDiskDao = null;
    UserStatisticsDao _userStatsDao = null;
    VMTemplateDao _templateDao =  null;
    VMTemplateHostDao _templateHostDao = null;
    DiskTemplateDao _diskDao = null;
    DomainDao _domainDao = null;
    ResourceLimitDao _limitDao = null;
    UserVmDao _vmDao = null;
    ScheduledVolumeBackupDao _volumeBackupDao = null;
    VolumeDao _volsDao = null;
    DataCenterDao _dcDao = null;
    FirewallRulesDao _rulesDao = null;
    SecurityGroupDao _securityGroupDao = null;
    SecurityGroupVMMapDao _securityGroupVMMapDao = null;
    LoadBalancerVMMapDao _loadBalancerVMMapDao = null;
    LoadBalancerDao _loadBalancerDao = null;
    IPAddressDao _ipAddressDao = null;
    HostPodDao _podDao = null;
    PricingDao _pricingDao = null;
    CapacityDao _capacityDao = null;
    NetworkManager _networkMgr = null;
    StorageManager _storageMgr = null;
    AgentManager _agentMgr = null;
    AccountDao _accountDao = null;
    UserDao _userDao = null;
    SnapshotDao _snapshotDao = null;
    GuestOSDao _guestOSDao = null;
    HighAvailabilityManager _haMgr = null;
    AlertManager _alertMgr = null;
    AccountManager _accountMgr;
	AsyncJobManager _asyncMgr;
	VlanDao _vlanDao;
	StoragePoolDao _storagePoolDao;

    EventDao _eventDao = null;
    ScheduledExecutorService _executor = null;
    int _expungeInterval;
    int _expungeDelay;
    int _retry = 2;

    String _name;
    String _instance;
    String _zone;

    Random _rand = new Random(System.currentTimeMillis());

    private VMTemplateHostDao _vmTemplateHostDao;
	private VMTemplatePoolDao _vmTemplatePoolDao;

    private ConfigurationDao _configDao;

	private final DeleteSnapshotListener _deleteSnapshotListener = new DeleteSnapshotListener();
	private final Map<Long, Map<Long, Long>> _deleteSnapshotEventMap = new HashMap<Long, Map<Long, Long>>();
	
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
    	
    	boolean sendCommand = (vm.getState() == State.Running);
    	Answer answer = null;
    	String volumeNameLabel = null;
    	
    	if (sendCommand) {
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, null);
    		
    		AttachDiskCommand cmd = new AttachDiskCommand(true, vm.getInstanceName(), volume.getFolder(), volume.getPath(), volume.getName(), volumeNameLabel);
    		answer = _agentMgr.easySend(vm.getHostId(), cmd);
    	}
    	
    	if (!sendCommand || (answer != null && answer.getResult())) {
    		// Mark the volume as attached
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, vm);
    		_volsDao.attachVolume(volume.getId(), vmId, volumeNameLabel);
    	} else {
    		String msg = "Failed to attach volume to VM: " + vm.getName();
    		if (answer != null) {
    			String details = answer.getDetails();
    			if (details != null && !details.isEmpty())
    				msg += "; " + details;
    		}
    		throw new InternalErrorException(msg);
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
    	
    	boolean sendCommand = (vm.getState() == State.Running);
    	Answer answer = null;
    	String volumeNameLabel = null;
    	
    	if (sendCommand) {
    		volumeNameLabel = _storageMgr.getVolumeNameLabel(volume, vm);
    		
			AttachDiskCommand cmd = new AttachDiskCommand(false, vm.getInstanceName(), volume.getFolder(), volume.getPath(), volume.getName(), volumeNameLabel);
			answer = _agentMgr.easySend(vm.getHostId(), cmd);
    	}
    	
		if (!sendCommand || (answer != null && answer.getResult())) {
			// Mark the volume as detached
    		_volsDao.detachVolume(volume.getId());
    	} else {
    		String msg = "Failed to detach volume from VM: " + vm.getName();
    		if (answer != null) {
    			String details = answer.getDetails();
    			if (details != null && !details.isEmpty())
    				msg += "; " + details;
    		}
    		throw new InternalErrorException(msg);
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
	        DomainRouterVO router = _networkMgr.addVirtualMachineToGuestNetwork(vm, password);
	        if (router == null) {
	        	s_logger.error("Unable to add vm " + vm.getId() + " - " + vm.getName());
	        	_vmDao.updateIf(vm, Event.OperationFailed, null);
	        	return null;
	        }
	
	        String eventParams = "id=" + vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ndoId=" + diskOfferingId + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId();
	        EventVO event = new EventVO();
	        event.setType(EventTypes.EVENT_VM_START);
	        event.setUserId(userId);
	        event.setAccountId(vm.getAccountId());
	        event.setParameters(eventParams);
	
	        boolean mirroredVols = vm.isMirroredVols();
	        
	        List<VolumeVO> rootVols = _volsDao.findByInstanceAndType(vm.getId(), VolumeType.ROOT);
	        assert rootVols.size() == 1 : "How can we get " + rootVols.size() + " root volume for " + vm.getId();
	        
	        String [] storageIps = new String[2];
	        VolumeVO vol = rootVols.get(0);
	        HostVO stor1 = _hostDao.findById(vol.getHostId());
	        HostVO stor2 = null;
	        if (mirroredVols && (rootVols.size() == 2)) {
	        	stor2 = _hostDao.findById(rootVols.get(1).getHostId());
	        }

	        List<VolumeVO> vols = _volsDao.findByInstance(vm.getId());

            Answer answer = null;
            int retry = _retry;

            do {

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Trying to start vm " + vm.getName() + " on host " + host.getName());
                }
                txn.start();

                storageIps[0] = _storageMgr.chooseStorageIp(vm, host, stor1);
                if (stor2 != null) {
                	storageIps[1] = _storageMgr.chooseStorageIp(vm, host, stor2);
                }
                
                vm.setVnet(router.getVnet());
                vm.setInstanceName(VirtualMachineName.attachVnet(vm.getName(), router.getVnet()));
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

                Map<String, Integer> mappings = _storageMgr.share(vm, vols, host, true);
                if (mappings == null) {
                	s_logger.debug("Unable to share volumes to host " + host.getId());
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

                StartCommand cmdStart = new StartCommand(vm, vm.getInstanceName(), offering, offering.getRateMbps(), offering.getMulticastRateMbps(), router, storageIps, vol.getFolder(), router.getVnet(), utilization, cpuWeight, vols, mappings, mirroredVols, bits, isoPath, bootFromISO, guestOSDescription);
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
	                        s_logger.debug("Started vm " + vm.getName() + " on host " + host.getName());
	                    }
	                    started = true;
	                    break;
	                }
	                
	                s_logger.debug("Unable to start " + vm.toString() + " on host " + host.toString() + " due to " + answer.getDetails());
                } catch (OperationTimedoutException e) {
                	if (e.isActive()) {
                		s_logger.debug("Unable to start the vm due to operation timed out and it is active so scheduling a restart.");
                		_haMgr.scheduleRestart(vm, true);
                		host = null;
                		return null;
                	}
                } catch (AgentUnavailableException e) {
                	s_logger.debug("Agent was unavailable");
                }
                
                avoid.add(host);

                _storageMgr.unshare(vm, vols, host);
            } while (--retry > 0 && (host = (HostVO)_agentMgr.findHost(Host.Type.Computing, dc, pod, sp, offering, diskOffering, template, null, null, avoid)) != null);

            if (host == null || retry < 0) {
                event.setDescription("unable to start VM: " + vm.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                throw new ExecutionException("Unable to start VM: " + vm.getName());
            }
            
            if (!_vmDao.updateIf(vm, Event.OperationSucceeded, host.getId())) {
            	throw new ConcurrentOperationException("Starting didn't work.");
            }
            
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Started vm " + vm.getId());
            }
            
            event.setDescription("successfully started VM: " + vm.getName());
            _eventDao.persist(event);

            return _vmDao.findById(vm.getId());
        } catch (Throwable th) {
            txn.rollback();
            s_logger.error("Caught Throwable: ", th);
            
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
                s_logger.info("Not upgrading vm " + vmId + " since it already has the requested service offering id (" + offering.getId() + ")");
            }
            return "Not upgrading vm " + vmId + " since it already has the requested service offering id (" + offering.getId() + ")";
        }

        if (!_agentMgr.isVirtualMachineUpgradable(vm, offering)) {
            return "Unable to upgrade virtual machine, not enough resources available for an offering of " +
                   offering.getCpu() + " cpu(s) at " + offering.getSpeed() + " Mhz, and " + offering.getRamSize() + " MB of memory";
        }

        if (!vm.getState().equals(State.Stopped)) {
            s_logger.warn("Unable to upgrade virtual machine " + vmId + " in state " + vm.getState());
            return "Unable to upgrade virtual machine " + vmId + " in state " + vm.getState() + "; make sure the virtual machine is stopped and not in an error state before upgrading.";
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
    public List<VmStats> listVirtualMachineStatistics(List<Long> vmIds) throws InternalErrorException {
    	List<VmStats> vmStatsList = new ArrayList<VmStats>();
    	
    	for (Long vmId : vmIds) {
    		UserVmVO vm = _vmDao.findById(vmId);
    		GetVmStatsCommand cmd = new GetVmStatsCommand(vm.getInstanceName());
    		Answer answer = _agentMgr.easySend(vm.getHostId(), cmd);
    		if (answer == null || !answer.getResult()) {
    			throw new InternalErrorException("Unable to obtain VM statistics.");
    		} else {
    			vmStatsList.add((GetVmStatsAnswer) answer);
    		}
    	}

        return vmStatsList;
    }

    @Override @DB
    public UserVmVO createVirtualMachine(Long vmId, long userId, AccountVO account, DataCenterVO dc, ServiceOfferingVO offering, DiskOfferingVO dataDiskOffering, VMTemplateVO template, DiskOfferingVO rootDiskOffering, String displayName, String group, String userData, List<StoragePoolVO> avoids) throws InternalErrorException, ResourceAllocationException {
        long accountId = account.getId();
        long dataCenterId = dc.getId();
        long serviceOfferingId = offering.getId();
        long templateId = -1;
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

        Transaction txn = Transaction.currentTxn();
        try {
            if (template != null)
            	_diskDao.findByTypeAndSize(template.getDiskType(), dataDiskOffering.getDiskSize());

            long routerId = router.getId();

            Long ipAddress = _routerDao.getNextDhcpIpAddress(routerId);
            ipAddress = NetUtils.ip2Long(NetUtils.getSubNet(router.getGuestIpAddress(), router.getGuestNetmask())) | ipAddress;

            UserVmVO vm = null;
            String ipAddressStr;
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
        	
        	ipAddressStr = NetUtils.long2Ip(ipAddress);
        	name = VirtualMachineName.getVmName(vmId, accountId, _instance);
            String vmMacAddress = NetUtils.long2Mac(
            	(NetUtils.mac2Long(router.getGuestMacAddress()) & 0xffffffff0000L) | (ipAddress & 0xffff)
            );

            HostPodVO pod = null;
            long poolid = 0;
            Set<Long> podsToAvoid = new HashSet<Long>();
            while ((pod = _agentMgr.findPod(template, offering, dc, account.getId(), podsToAvoid)) != null) {

                vm = new UserVmVO(vmId, name, templateId, guestOSId, accountId, account.getDomainId().longValue(),
                		serviceOfferingId, vmMacAddress, ipAddressStr, router.getGuestNetmask(),
                		null,null,null,
                		routerId, pod.getId(), dataCenterId,
                		Long.toHexString(_rand.nextLong()),
                		offering.getOfferHA(), displayName, group, userData);
                
                vm.setMirroredVols(dataDiskOffering.getMirrored());

                _vmDao.persist(vm);
                
                _accountMgr.incrementResourceCount(account.getId(), ResourceType.user_vm);
                long numVolumes = (dataDiskOffering.getDiskSize() == 0) ? 1 : 2;
	            _accountMgr.incrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
                txn.commit();
                
                vm = _vmDao.findById(vmId);

            	poolid = _storageMgr.createUserVM(account,  vm, template, dc, pod, offering,  rootDiskOffering,  dataDiskOffering, avoids);
                if ( poolid == 0) {
                    _vmDao.delete(vmId);
                    _accountMgr.decrementResourceCount(account.getId(), ResourceType.user_vm);
                    _accountMgr.decrementResourceCount(account.getId(), ResourceType.volume, numVolumes);
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Unable to find storage host in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
                    }
                    
                    podsToAvoid.add(pod.getId());
                    continue; // didn't find a storage host in pod, go to the next pod
                }
                break; // if we got here, we found a host and can stop searching the pods
            }

            txn.start();

            String eventParams = "id=" + vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ndoId=" + dataDiskOffering.getId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId();
            EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(accountId);
            event.setType(EventTypes.EVENT_VM_CREATE);
            event.setParameters(eventParams);

            if (poolid == 0) {
                event.setDescription("failed to create VM instance : " + vm.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                txn.commit();
                return null;
            }

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
        } catch (ResourceAllocationException rae) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("Failed to create VM for account " + accountId + " due to maximum number of virtual machines exceeded.");
            }
        	throw rae;
        } catch (Throwable th) {
            s_logger.error("Unable to create vm", th);
            throw new VmopsRuntimeException("Unable to create vm", th);
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
        
        UserVO user = _userDao.findById(userId);
        _accountMgr.decrementResourceCount(user.getAccountId(), ResourceType.user_vm);
        
        if (!destroy(vm)) {
        	return false;
        }

        cleanNetworkRules(userId, vmId);
        
        // Mark the VM's disks as destroyed
        List<VolumeVO> volumes = _volsDao.findByInstance(vmId);
        for (VolumeVO volume : volumes) {
        	_volsDao.destroyVolume(volume.getId());
        }
        
        _accountMgr.decrementResourceCount(user.getAccountId(), ResourceType.volume, new Long(volumes.size()));

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
        
        // Mark the VM's disks as destroyed
        List<VolumeVO> volumes = _volsDao.findByInstance(param.getVmId());
        for (VolumeVO volume : volumes) {
        	_volsDao.destroyVolume(volume.getId());
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

        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + HostDao.class.getName());
        }
        
        _detailsDao = locator.getDao(DetailsDao.class);
        if (_detailsDao == null) {
        	throw new ConfigurationException("Unable to get " + DetailsDao.class.getName());
        }

        _routerDao = locator.getDao(DomainRouterDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + DomainRouterDao.class.getName());
        }

        _podDao = locator.getDao(HostPodDao.class);
        if (_podDao == null) {
            throw new ConfigurationException("Unable to get " + HostPodDao.class.getName());
        }
        
        _offeringDao = locator.getDao(ServiceOfferingDao.class);
        if (_offeringDao == null) {
            throw new ConfigurationException("Unable to get " + ServiceOfferingDao.class.getName());
        }
        
        _domainDao = locator.getDao(DomainDao.class);
        if (_domainDao == null) {
            throw new ConfigurationException("Unable to get " + DomainDao.class.getName());
        }
        
        _limitDao = locator.getDao(ResourceLimitDao.class);
        if (_limitDao == null) {
            throw new ConfigurationException("Unable to get " + ResourceLimitDao.class.getName());
        }

        _diskOfferingDao = locator.getDao(DiskOfferingDao.class);
        if (_diskOfferingDao == null) {
            throw new ConfigurationException("Unable to get " + DiskOfferingDao.class.getName());
        }

        _vmDiskDao = locator.getDao(VmDiskDao.class);
        if (_vmDiskDao == null) {
            throw new ConfigurationException("Unable to get " + VmDiskDao.class.getName());
        }

        _userStatsDao = locator.getDao(UserStatisticsDao.class);
        if (_userStatsDao == null) {
            throw new ConfigurationException("Unable to get " + UserStatisticsDao.class.getName());
        }

        _rulesDao = locator.getDao(FirewallRulesDao.class);
        if (_rulesDao == null) {
            throw new ConfigurationException("Unable to get " + FirewallRulesDao.class.getName());
        }

        _securityGroupDao = locator.getDao(SecurityGroupDao.class);
        if (_securityGroupDao == null) {
            throw new ConfigurationException("Unable to get " + SecurityGroupDao.class.getName());
        }

        _securityGroupVMMapDao = locator.getDao(SecurityGroupVMMapDao.class);
        if (_securityGroupVMMapDao == null) {
            throw new ConfigurationException("Unable to get " + SecurityGroupVMMapDao.class.getName());
        }

        _loadBalancerDao = locator.getDao(LoadBalancerDao.class);
        if (_loadBalancerDao == null) {
            throw new ConfigurationException("Unable to get " + LoadBalancerDao.class.getName());
        }

        _loadBalancerVMMapDao = locator.getDao(LoadBalancerVMMapDao.class);
        if (_loadBalancerVMMapDao == null) {
            throw new ConfigurationException("Unable to get " + LoadBalancerVMMapDao.class.getName());
        }

        _ipAddressDao = locator.getDao(IPAddressDao.class);
        if (_ipAddressDao == null) {
            throw new ConfigurationException("Unable to get " + IPAddressDao.class.getName());
        }

        _dcDao = locator.getDao(DataCenterDao.class);
        if (_dcDao == null) {
            throw new ConfigurationException("Unable to get " + DataCenterDao.class.getName());
        }

        _volsDao = locator.getDao(VolumeDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + VolumeDao.class.getName());
        }

        _templateDao = locator.getDao(VMTemplateDao.class);
        if (_templateDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplateDao.class.getName());
        }

        _templateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_templateHostDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplateHostDao.class.getName());
        }

        _diskDao = locator.getDao(DiskTemplateDao.class);
        if (_diskDao == null) {
            throw new ConfigurationException("Unable to get " + DiskTemplateDao.class.getName());
        }

        _vmTemplateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_vmTemplateHostDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplateHostDao.class.getName());
        }
        
        _vmTemplatePoolDao = locator.getDao(VMTemplatePoolDao.class);
        if (_vmTemplatePoolDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplatePoolDao.class.getName());
        }

        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
            throw new ConfigurationException("unable to get " + EventDao.class.getName());
        }

        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("Unable to get " + UserVmDao.class.getName());
        }

        _volumeBackupDao = locator.getDao(ScheduledVolumeBackupDao.class);
        if (_volumeBackupDao == null) {
            throw new ConfigurationException("Unable to get " + ScheduledVolumeBackupDao.class.getName());
        }

        _pricingDao = locator.getDao(PricingDao.class);
        if (_pricingDao == null) {
            throw new ConfigurationException("Unable to get " + PricingDao.class.getName());
        }

        _capacityDao = locator.getDao(CapacityDao.class);
        if (_capacityDao == null) {
            throw new ConfigurationException("Unable to get " + CapacityDao.class.getName());
        }

        _snapshotDao = locator.getDao(SnapshotDao.class);
        if (_snapshotDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotDao.class.getName());
        }
        
        _guestOSDao = locator.getDao(GuestOSDao.class);
        if (_guestOSDao == null) {
        	throw new ConfigurationException("Unable to get " + GuestOSDao.class.getName());
        }
        
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
        	throw new ConfigurationException("Unable to get " + StoragePoolDao.class.getName());
        }

        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
            throw new ConfigurationException("Unable to get " + AgentManager.class.getName());
        }

        _networkMgr = locator.getManager(NetworkManager.class);
        if (_networkMgr == null) {
            throw new ConfigurationException("Unable to get " + NetworkManager.class.getName());
        }
        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
        	throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
        }

        _haMgr = locator.getManager(HighAvailabilityManager.class);
        if (_haMgr == null) {
        	throw new ConfigurationException("Unable to get " + HighAvailabilityManager.class.getName());
        }
        _haMgr.registerHandler(VirtualMachine.Type.User, this);
        
        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to get " + AccountDao.class.getName());
        }
        
        _userDao = locator.getDao(UserDao.class);
        if (_userDao == null) {
        	throw new ConfigurationException("Unable to get " + UserDao.class.getName());
        }
        
        
        _vlanDao = locator.getDao(VlanDao.class);
        if (_vlanDao == null) {
        	throw new ConfigurationException("Unable to get " + VlanDao.class.getName());
        }
        
        _alertMgr = locator.getManager(AlertManager.class);
        if (_alertMgr == null) {
        	throw new ConfigurationException("Unable to get " + AlertManager.class.getName());
        }
        
        _accountMgr = locator.getManager(AccountManager.class);
        if (_accountMgr == null) {
        	throw new ConfigurationException("Unable to get " + AccountManager.class.getName());
        }
        
        _asyncMgr = locator.getManager(AsyncJobManager.class);
        if (_asyncMgr == null) {
        	throw new ConfigurationException("Unable to get " + AsyncJobManager.class.getName());
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
            throw new VmopsRuntimeException("Shouldn't even be here!");
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
        int i = 0;
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
            throw new VmopsRuntimeException("Error during stop: ", th);
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
        
        Transaction txn = Transaction.currentTxn();
        if (!_vmDao.updateIf(vm, VirtualMachine.Event.DestroyRequested, vm.getHostId())) {
            s_logger.debug("Unable to destroy the vm because it is not in the correct state: " + vm.toString());
            return false;
        }
        
        return true;
    }
    

    @Override
    public HostVO prepareForMigration(UserVmVO vm) throws StorageUnavailableException {
        long routerId = vm.getId();
        boolean mirroredVols = vm.isMirroredVols();
        DataCenterVO dc = _dcDao.findById(vm.getDataCenterId());
        HostPodVO pod = _podDao.findById(vm.getPodId());
        ServiceOfferingVO offering = _offeringDao.findById(vm.getServiceOfferingId());
        List<DiskOfferingVO> diskOfferings = _diskOfferingDao.listByInstanceId(vm.getId());
        VMTemplateVO template = _templateDao.findById(vm.getTemplateId());
        StoragePoolVO sp = _storagePoolDao.findById(vm.getPoolId());
       

        List<VolumeVO> vols = _volsDao.findByInstance(routerId);

        String [] storageIps = new String[2];
        VolumeVO vol = vols.get(0);
        storageIps[0] = vol.getHostIp();
        if (mirroredVols && (vols.size() == 2)) {
            storageIps[1] = vols.get(1).getHostIp();
        }

        PrepareForMigrationCommand cmd = new PrepareForMigrationCommand(vm.getInstanceName(), vm.getVnet(), storageIps, vols, null, mirroredVols);

        HostVO vmHost = null;
        HashSet<Host> avoid = new HashSet<Host>();

        HostVO fromHost = _hostDao.findById(vm.getHostId());
        avoid.add(fromHost);

        while ((vmHost = (HostVO)_agentMgr.findHost(Host.Type.Computing, dc, pod, sp, offering, diskOfferings.get(0), template, null, null, avoid)) != null) {
            avoid.add(vmHost);

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to migrate router to host " + vmHost.getName());
            }
            
            Map<String, Integer> mappings = _storageMgr.share(vm, vols, vmHost, false);
            if (mappings == null) {
                s_logger.warn("Can not share " + vm.toString() + " on host " + vol.getHostId());
                throw new StorageUnavailableException(vol.getHostId());
            }
            cmd.setMappings(mappings);

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
    	
        MigrateCommand cmd = new MigrateCommand(vm.getInstanceName(), host.getPrivateIpAddress());
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
    		
    		if (!_vmDao.updateIf(vm, Event.ExpungeOperation, null)) {
    			s_logger.info("vm " + vmId + " is skipped because it is no longer in Destroyed state");
    			continue;
    		}
    		
            Transaction txn = Transaction.currentTxn();

            List<VolumeVO> vols = null;
            try {
                vols = _volsDao.findByInstanceIdDestroyed(vmId);
                if (!vm.isMirroredVols()) {
                    if (vols.size() != 0) {
                        VolumeVO vol = vols.get(0);  // we only need one.
                        DestroyCommand cmd = new DestroyCommand(vol.getFolder(), vols);
                        long hostId = vol.getHostId();
                        Answer answer;
						answer = _agentMgr.send(hostId, cmd);
                        if (!answer.getResult()) {
                        	
                            HostVO host = _hostDao.findById(hostId);
                            DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                            HostPodVO podVO = _podDao.findById(host.getPodId());
                            String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                        	
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_STORAGE_MISC, vol.getDataCenterId(), vol.getPodId(), "Storage cleanup required for " + vol.getFolder() + " on storage host [" + hostDesc + "]", "Delete failed for volume " + vol.getFolder() + ", action required on the storage server with " + hostDesc);
                            s_logger.warn("Cleanup required on storage server " + host.getPrivateIpAddress() + " for " + vol.getFolder() + " due to " + answer.getDetails());
                        }
                    }
                } else {
                    if (vols.size() != 0) {
                        Map<Long, List<VolumeVO>> hostVolMap = new HashMap<Long, List<VolumeVO>>();
                        for (VolumeVO vol: vols){
                            List<VolumeVO> vollist = hostVolMap.get(vol.getHostId());
                            if (vollist == null) {
                                vollist = new ArrayList<VolumeVO>();
                                hostVolMap.put(vol.getHostId(), vollist);
                            }
                            vollist.add(vol);
                        }
                        for (Long hostId: hostVolMap.keySet()){
                            List<VolumeVO> volumes = hostVolMap.get(hostId);
                            String path = volumes.get(0)==null?null:volumes.get(0).getFolder();
                            DestroyCommand cmd = new DestroyCommand(path, volumes);
                            Answer answer;
							answer = _agentMgr.send(hostId, cmd);
                            if (!answer.getResult()) {
                                HostVO host = _hostDao.findById(hostId);
                                DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                                HostPodVO podVO = _podDao.findById(host.getPodId());
                                String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                            	
                                _alertMgr.sendAlert(AlertManager.ALERT_TYPE_STORAGE_MISC, host.getDataCenterId(), host.getPodId(), "Storage cleanup required for host [" + hostDesc + "]", "Failed to destroy volumes, action required on the storage server with " + hostDesc);
                                
                                s_logger.warn("Cleanup required on storage server " + host.getPrivateIpAddress() + " due to " + answer.getDetails());
                            }
                        }
                    }
                }
                
                txn.start();
                if (vols != null) {
                    for (VolumeVO vol : vols) {
                        _volsDao.remove(vol.getId());
                    }
                }

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
            } catch (AgentUnavailableException e) {
            	s_logger.info("Agent was unavailable to destroy vm " + vmId);
            	_vmDao.updateIf(vm, Event.OperationFailed, null);
			} catch (OperationTimedoutException e) {
            	s_logger.info("Operation for the agent to destroy the VM timedout: " + vmId);
				_vmDao.updateIf(vm, Event.OperationFailed, null);
			}
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
    public void scheduleRecurringSnapshot(Long userId, long volumeId, int hourlyMax, int dailyMax, int weeklyMax, int monthlyMax) {
    	// Precondition: volume is guaranteed to be non-null
    	VolumeVO volume = _volsDao.findById(volumeId);
    	
    	Host host = null;
    	Long hostId = getHostForVolume(volume);
    	host = _hostDao.findById(hostId);
    	if ((host != null) && (host.getHypervisorType() == Host.HypervisorType.KVM)) {
            String msg = "Trying to schedule recurring snapshots for volume: " + volumeId + "; unable to schedule snapshots for a VM running on KVM";
            if (s_logger.isInfoEnabled()) {
                s_logger.info(msg);
            }
            throw new VmopsRuntimeException(msg);
        }

        short interval = Snapshot.TYPE_NONE;
        if (hourlyMax > 0) {
            interval = Snapshot.TYPE_HOURLY;
        } else if (dailyMax > 0) {
            interval = Snapshot.TYPE_DAILY;
        } else if (weeklyMax > 0) {
            interval = Snapshot.TYPE_WEEKLY;
        }

        String path = (interval == Snapshot.TYPE_NONE) ? null : volume.getPath();
        ScheduleVolumeSnapshotCommand cmd = new ScheduleVolumeSnapshotCommand(volumeId, path, interval);
        
        boolean success = true;
        ScheduledVolumeBackup currentBackupSchedule = _volumeBackupDao.findByVolumeId(volumeId);
        if ((currentBackupSchedule != null) && (interval != Snapshot.TYPE_NONE) && (interval != currentBackupSchedule.getInterval())) {
            // we had an old backup schedule, we aren't removing the old schedule, and we are changing the interval, so remove the Volume from
            // the old schedule
            ScheduleVolumeSnapshotCommand removeOldScheduleCmd = new ScheduleVolumeSnapshotCommand(volumeId, null, currentBackupSchedule.getInterval());
            Answer removeOldScheduleAnswer = null;
            try {
            	removeOldScheduleAnswer = _agentMgr.send(hostId, removeOldScheduleCmd);
            } catch (AgentUnavailableException e1) {
                s_logger.warn("Failed to cancel the existing recurring snapshots schedule for volume: " + volumeId + ", reason: " + e1);
            } catch (OperationTimedoutException e1) {
                s_logger.warn("Failed to cancel the existing recurring snapshots schedule for volume: " + volumeId, e1);
            }

            if ((removeOldScheduleAnswer != null) && removeOldScheduleAnswer.getResult()) {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("removed old schedule of recurring backups for volume: " + volumeId);
                }
            } else {
                success = false;
            }
        }

        Answer answer = null;
        if (success) {
            try {
            	answer = _agentMgr.send(hostId, cmd);
            } catch (AgentUnavailableException e1) {
                s_logger.warn("Failed to schedule recurring snapshots for volume: " + volumeId + ", reason: " + e1);
            } catch (OperationTimedoutException e1) {
                s_logger.warn("Failed to schedule recurring snapshots for volume: " + volumeId, e1);
            }
        }

        String eventParams = "id=" + volumeId + "\ninterval=" + interval + "\nh=" + hourlyMax + "\nd=" + dailyMax + "\nw=" + weeklyMax + "\nm=" + monthlyMax;
        EventVO event = new EventVO();
        event.setUserId(userId.longValue());
        event.setAccountId(volume.getAccountId());
        event.setType(EventTypes.EVENT_SNAPSHOT_SCHEDULE);
        event.setDescription("Schedulling recurring snapshots (interval: " + interval + ") for volume " + volumeId);
        event.setParameters(eventParams);

        if ((answer != null) && answer.getResult()) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("scheduled recurring backup for vm: " + volumeId);
            }

            // save the backup entry
            if (currentBackupSchedule != null) {
                ScheduledVolumeBackupVO volumeBackup = _volumeBackupDao.createForUpdate();
                volumeBackup.setInterval(interval);
                volumeBackup.setMaxHourly(hourlyMax);
                volumeBackup.setMaxDaily(dailyMax);
                volumeBackup.setMaxWeekly(weeklyMax);
                volumeBackup.setMaxMonthly(monthlyMax);
                _volumeBackupDao.update(currentBackupSchedule.getId(), volumeBackup);
            } else {
                ScheduledVolumeBackupVO volumeBackup = new ScheduledVolumeBackupVO(volumeId, interval, hourlyMax, dailyMax, weeklyMax, monthlyMax);
                _volumeBackupDao.persist(volumeBackup);
            }
        } else {
            event.setLevel(EventVO.LEVEL_ERROR);
        }

        _eventDao.persist(event);
    }

    @Override
    public ScheduledVolumeBackup findRecurringSnapshotSchedule(long volumeId) {
        return _volumeBackupDao.findByVolumeId(volumeId);
    }

    @Override @DB
    public boolean destroySnapshot(Long userId, long snapshotId) {
        boolean success = true;
        SnapshotVO snapshot = _snapshotDao.findById(Long.valueOf(snapshotId));
        if (snapshot != null) {
        	VolumeVO volume = _volsDao.findById(snapshot.getVolumeId());
            ManageSnapshotCommand cmd = new ManageSnapshotCommand(ManageSnapshotCommand.DESTROY_SNAPSHOT, snapshotId, volume.getFolder(), snapshot.getPath(), snapshot.getName());

            Answer answer = null;
            answer = sendToStorageHostOrPool(snapshot, cmd);

            if ((answer != null) && answer.getResult()) {
                // delete the snapshot from the database
                Transaction txn = Transaction.currentTxn();
                try {
                    _snapshotDao.remove(snapshotId);
                } catch(Exception e) {
                    s_logger.warn("Exception while deleting snapshot: " + snapshotId);
                    success = false;
                }
            } else {
                success = false;
            }
        } else {
            success = false;
        }

        // create the event
        String eventParams = "id=" + snapshotId;
        EventVO event = new EventVO();
        if (userId != null) {
            event.setUserId(userId.longValue());
        }
        event.setAccountId((snapshot != null) ? snapshot.getAccountId() : 0);
        event.setType(EventTypes.EVENT_SNAPSHOT_DELETE);
        event.setDescription("Deleted snapshot " + snapshotId);
        event.setParameters(eventParams);
        event.setLevel(success ? EventVO.LEVEL_INFO : EventVO.LEVEL_ERROR);
        _eventDao.persist(event);

        return success;
    }

    @Override
    public boolean destroyRecurringSnapshot(Long userId, long snapshotId) {
        SnapshotVO snapshot = _snapshotDao.findById(Long.valueOf(snapshotId));
        if (snapshot != null) {
        	Long hostId = snapshot.getHostId();
            ManageSnapshotCommand cmd = new ManageSnapshotCommand(ManageSnapshotCommand.DESTROY_SNAPSHOT, snapshotId, snapshot.getPath(), snapshot.getName());
            if (snapshot.getPoolId() != null) {
            	hostId = _storageMgr.chooseHostForStoragePool(snapshot.getPoolId());
            }
            if (hostId == null) {
                s_logger.warn("Failed to destroy snapshot: " + snapshotId + ", reason: " + " no host found to send delete command");
            	return false;
            }
            try {
                long seqId = _agentMgr.send(hostId, new Command[] { cmd }, true, _deleteSnapshotListener);
                // FIXME: we need to make sure to clean this up after a time...at some point a processTimeout() will be added to the Listener interface, and we can clean up entries on timeout
                synchronized(_deleteSnapshotEventMap) {
                    Map<Long, Long> sequenceMapForAgent = _deleteSnapshotEventMap.get(hostId);
                    if (sequenceMapForAgent == null) {
                        sequenceMapForAgent = new HashMap<Long, Long>();
                    }
                    sequenceMapForAgent.put(Long.valueOf(seqId), userId);
                    _deleteSnapshotEventMap.put(hostId, sequenceMapForAgent);
                }
            } catch (AgentUnavailableException e1) {
                s_logger.warn("Failed to destroy snapshot: " + snapshotId + ", reason: " + e1);
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean rollbackToSnapshot(Long userId, long snapshotId) {
        SnapshotVO snapshot = _snapshotDao.findById(Long.valueOf(snapshotId));
        boolean success = true;

        if (snapshot != null) {
        	VolumeVO volume = _volsDao.findById(snapshot.getVolumeId());
        	Long vmId = volume.getInstanceId();
            UserVm userVM = _vmDao.findById(vmId);
            if (userVM != null) {
                State vmState = userVM.getState();
                if (!vmState.equals(State.Stopped)) {
                    if (s_logger.isInfoEnabled()) {
                        s_logger.info("Guest vm: " + vmId + " is not in Stopped state, unable to rollback to snapshot: " + snapshotId);
                    }
                    return false;
                }
            } else {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Unable to find guest vm: " + vmId + " is not in Stopped state, unable to rollback to snapshot: " + snapshotId);
                }
                return false;
            }

            ManageSnapshotCommand cmd = new ManageSnapshotCommand(ManageSnapshotCommand.ROLLBACK_SNAPSHOT, snapshotId, snapshot.getPath(), snapshot.getName());

            Answer answer = null;
            answer = sendToStorageHostOrPool(snapshot, cmd);

            String eventParams = "id=" + snapshotId;
            EventVO event = new EventVO();
            event.setUserId(userId.longValue());
            event.setAccountId(userVM.getAccountId());
            event.setType(EventTypes.EVENT_SNAPSHOT_ROLLBACK);
            event.setDescription("Rolling back guest vm " + userVM.getInstanceName() + " (id=" + userVM.getId() + ") to snapshot " + snapshotId);
            event.setParameters(eventParams);

            // delete any snapshots after the current one
            if (((answer != null) && !answer.getResult()) || answer == null) {
                event.setLevel(EventVO.LEVEL_ERROR);
                success = false;
            }

            _eventDao.persist(event);
        } else {
            success = false;
        }
        return success;
    }
    
    /**
     * Get a possible host on which the volume can be mounted. Could be either
     * the current host or one of the hosts on which the storage pool is mounted.
     * @param volume The volume whose host is required.
     * @return A possible host on which the volume can be mounted.
     */
    private Long getHostForVolume(VolumeVO volume) {
    	Long hostId = volume.getHostId();
    	if (volume.getPoolId() != null) {
    		hostId = _storageMgr.chooseHostForStoragePool(volume.getPoolId());
    	}
    	return hostId;
    }
    
    private Answer sendToStorageHostOrPool(SnapshotVO snapshot, Command cmd) {
    	Answer answer = null;
    	Long hostId = snapshot.getHostId();
    	if (snapshot.getPoolId() != null) {
    		hostId = _storageMgr.chooseHostForStoragePool(snapshot.getPoolId());
    	}
    	if (hostId == null) {
    		return answer;
    	}
        try {
            answer = _agentMgr.send(hostId, cmd);
        } catch (AgentUnavailableException e1) {
            s_logger.warn("Failed to create template from snapshot: " + snapshot.getName() + ", reason: " + e1);
        } catch (OperationTimedoutException e1) {
            s_logger.warn("Failed to create template from snapshot: " + snapshot.getName() + ", reason: " + e1);
        }
        return answer;
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
    
    public VMTemplateVO createPrivateTemplateRecord(Long userId, long volumeId, String name, String description, long guestOSId, Boolean requiresHvm, Integer bits, Boolean passwordEnabled, boolean isPublic)
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

    	privateTemplate = new VMTemplateVO(nextTemplateId, uniqueName, name, ImageFormat.RAW, isPublic, null,
    			null, null, requiresHvmValue, bitsValue, volume.getAccountId(),
    			null, description, passwordEnabledValue, guestOS.getId(), true);
        privateTemplate.setCreateStatus(AsyncInstanceCreateStatus.Creating);

        Long templateId = _templateDao.persist(privateTemplate);
        if(templateId != null)
        	return privateTemplate;

        s_logger.error("Unable to create private template record. uid: " + userId + ", volumeId: " + volumeId + ", name: " + name + ", description: " + description);
        return null;
    }

    @Override @DB
    public VMTemplateVO createPrivateTemplate(VMTemplateVO template, Long userId, long snapshotId, String name, String description) {
    	VMTemplateVO privateTemplate = null;
    	long templateId = template.getId();
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
        VolumeVO volume = _volsDao.findById(snapshot.getVolumeId());
        if (snapshot != null) {
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
            
            CreatePrivateTemplateCommand cmd = new CreatePrivateTemplateCommand(secondaryStorageURL, templateId, name, uniqueName, volume.getFolder(),
            																	snapshot.getPath(), snapshot.getName(), userFolder.toString());

            Answer answer = null;

            // FIXME: before sending the command, check if there's enough capacity on the storage server to create the template
            answer = sendToStorageHostOrPool(snapshot, cmd);
            
            String eventParams = "id="+templateId+"\nname=" + name + "\ndcId=" + zoneId +"\nsize="+volume.getSize();
            EventVO event = new EventVO();
            event.setUserId(userId.longValue());
            event.setAccountId(snapshot.getAccountId());
            event.setType(EventTypes.EVENT_TEMPLATE_CREATE);
            event.setDescription("Creating private template " + name + " from snapshot " + snapshotId);
            event.setParameters(eventParams);

            if ((answer != null) && answer.getResult()) {
                CreatePrivateTemplateAnswer ptAnswer = (CreatePrivateTemplateAnswer)answer;

                // save the snapshot in the database
                Transaction txn = Transaction.currentTxn();
                try {
                    txn.start();
                    
                    privateTemplate = _templateDao.findById(templateId);
                    Long instanceId = volume.getInstanceId();
                    VMTemplateVO origTemplate = null;
                    if (instanceId != null) {
                    	UserVm userVM = _vmDao.findById(instanceId);
                    	origTemplate = _templateDao.findById(userVM.getTemplateId());
                    }

                    if ((origTemplate != null) && !Storage.ImageFormat.ISO.equals(origTemplate.getFormat())) {
                    	// We made a private template from a root volume that was cloned from a template
                    	privateTemplate.setPublicTemplate(false);
                    	privateTemplate.setDiskType(origTemplate.getDiskType());
                    	privateTemplate.setRequiresHvm(origTemplate.requiresHvm());
                    	privateTemplate.setBits(origTemplate.getBits());
                    	privateTemplate.setEnablePassword(origTemplate.getEnablePassword());
                        privateTemplate.setFormat(ImageFormat.RAW);
                    } else {
                    	// We made a private template from a root volume that was not cloned from a template, or a data volume
                    	privateTemplate.setPublicTemplate(false);
                    	privateTemplate.setDiskType("none");
                    	privateTemplate.setRequiresHvm(true);
                    	privateTemplate.setBits(64);
                    	privateTemplate.setEnablePassword(false);
                    	privateTemplate.setFormat(ImageFormat.RAW);
                    }

                    privateTemplate.setUniqueName(uniqueName);
                    privateTemplate.setReady(true);
                    privateTemplate.setCreateStatus(AsyncInstanceCreateStatus.Created);

                    _templateDao.update(templateId, privateTemplate);

                    if (snapshot.getHostId() != null) {
                        VMTemplateHostVO templateHostVO = new VMTemplateHostVO(secondaryStorageHost.getId(), templateId);
                        templateHostVO.setDownloadPercent(100);
                        templateHostVO.setDownloadState(Status.DOWNLOADED);
                        templateHostVO.setInstallPath(ptAnswer.getPath());
                        templateHostVO.setLastUpdated(new Date());
                        _vmTemplateHostDao.persist(templateHostVO);
                    } else if (snapshot.getPoolId() != null){
                        VMTemplateStoragePoolVO templatePoolVO = new VMTemplateStoragePoolVO(snapshot.getPoolId(), templateId);
                        templatePoolVO.setDownloadPercent(100);
                        templatePoolVO.setDownloadState(Status.DOWNLOADED);
                        templatePoolVO.setInstallPath(ptAnswer.getPath());
                        templatePoolVO.setLastUpdated(new Date());
                        _vmTemplatePoolDao.persist(templatePoolVO);
                    } else {
                        throw new VmopsRuntimeException("Unable to save private template entry for snapshot: " + snapshot.getName() + " in the database");
                    }
                    event.setLevel(EventVO.LEVEL_INFO);
                    _eventDao.persist(event);
                    
                    // Increment the number of templates
                    _accountMgr.incrementResourceCount(volume.getAccountId(), ResourceType.template);
                    
                    txn.commit();
                } catch(Exception e) {
                    s_logger.warn("Exception while saving template for snapshot: " + snapshotId, e);
                }
            } else {
                // save the snapshot in the database
                Transaction txn = Transaction.currentTxn();
                try {
                    txn.start();
                    privateTemplate = _templateDao.findById(templateId);
                    privateTemplate.setCreateStatus(AsyncInstanceCreateStatus.Corrupted);
                    _templateDao.update(templateId, privateTemplate);
                    event.setLevel(EventVO.LEVEL_ERROR);
                    _eventDao.persist(event);
                    txn.commit();

                    // now that there was an error, return null for the template so that the failure bubbles up to the caller
                    privateTemplate = null;
                } catch (Exception e) {
                    s_logger.error("Unhandled exception while updating template record, template id: " + templateId);
                }
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
	            		Long.toHexString(_rand.nextLong()),
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
	        throw new VmopsRuntimeException("Unable to create vm", th);
	    }
	}

	protected class ExpungeTask implements Runnable {
    	UserVmManagerImpl _vmMgr;
    	public ExpungeTask(UserVmManagerImpl vmMgr) {
    		_vmMgr = vmMgr;
    	}
    	
    	public void run() {
    		try {
    			s_logger.info("UserVm Expunge Thread is running.");
    			_vmMgr.expunge();
    		} catch (Exception e) {
    			s_logger.error("Caught the following Exception", e);
    		}
    	}
    }
	
    private class DeleteSnapshotListener implements Listener {
        public boolean processAnswer(long agentId, long seq, Answer[] answers) {
            boolean success = true;
            if (answers != null) {
                for (Answer answer : answers) {
                    if (answer instanceof ManageSnapshotAnswer) {
                        long snapshotId = ((ManageSnapshotAnswer)answer).getSnapshotId();

                        Long userId = null;
                        synchronized(_deleteSnapshotEventMap) {
                            Map<Long, Long> sequenceMapForAgent = _deleteSnapshotEventMap.get(Long.valueOf(agentId));
                            if (sequenceMapForAgent != null) {
                                userId = sequenceMapForAgent.remove(Long.valueOf(seq));
                            }
                        }

                        Snapshot snapshot = _snapshotDao.findById(snapshotId);

                        String eventParams = "id=" + snapshotId;
                        EventVO event = new EventVO();
                        if (userId != null) {
                            event.setUserId(userId.longValue());
                        }
                        event.setAccountId((snapshot == null) ? 0 : snapshot.getAccountId());
                        event.setType(EventTypes.EVENT_SNAPSHOT_DELETE);
                        event.setDescription("Deleted snapshot " + snapshotId);
                        event.setParameters(eventParams);

                        if (answer.getResult()) {
                            // delete the snapshot from the database
                            try {
                                _snapshotDao.remove(snapshotId);
                            } catch(Exception e) {
                                s_logger.warn("Exception while deleting snapshot: " + snapshotId);
                                success = false;
                            }
                        } else {
                            event.setLevel(EventVO.LEVEL_ERROR);
                            success = false;
                        }

                        _eventDao.persist(event);
                    } else if (!answer.getResult()) {
                        s_logger.warn("sequence " + seq + ", failure, details = " + answer.getResult());
                    }
                }
            }
            return success;
        }

        public boolean processCommand(long agentId, long seq, Command[] commands) {
            return false;
        }
        
        public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
        	return null;
        }

        public boolean processConnect(long agentId, StartupCommand cmd) {
            return false;
        }

        // Do we expect more?  This determines if the listener is removed after it has been called.
        public boolean isRecurring() {
            return false;
        }

        @Override
        public boolean processDisconnect(long agentId, com.vmops.host.Status state) {
            _deleteSnapshotEventMap.remove(Long.valueOf(agentId));
            return false;
        }
        
        @Override
        public boolean processTimeout(long agentId, long seq) {
        	return true;
        }
        
        @Override
        public int getTimeout() {
        	return -1;
        }
    }

    
}
