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
package com.vmops.storage;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.ModifyStoragePoolAnswer;
import com.vmops.agent.api.ModifyStoragePoolCommand;
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
import com.vmops.agent.api.storage.ShareAnswer;
import com.vmops.agent.api.storage.ShareCommand;
import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.capacity.CapacityVO;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.ResourceInUseException;
import com.vmops.exception.StorageUnavailableException;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.dao.DetailsDao;
import com.vmops.host.dao.HostDao;
import com.vmops.service.ServiceOffering;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.storage.Volume.MirrorState;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.storage.dao.DiskOfferingDao;
import com.vmops.storage.dao.SnapshotDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.dao.VmDiskDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.storage.listener.StoragePoolMonitor;
import com.vmops.storage.listener.VolumeBackupListener;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.storage.snapshot.SnapshotScheduler;
import com.vmops.template.TemplateManager;
import com.vmops.user.Account;
import com.vmops.user.AccountManager;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.ScheduledVolumeBackupDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Pair;
import com.vmops.utils.component.Adapters;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.component.Inject;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.GlobalLock;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;
import com.vmops.vm.State;
import com.vmops.vm.UserVm;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;

@Local(value={StorageManager.class})
public class StorageManagerImpl implements StorageManager {
	private static final Logger s_logger = Logger.getLogger(StorageManagerImpl.class);
	
	protected String _name;
	@Inject protected AgentManager _agentMgr;
	@Inject protected TemplateManager _tmpltMgr;
	@Inject protected AsyncJobManager _asyncMgr;
	@Inject protected SnapshotManager _snapshotMgr;
	@Inject protected SnapshotScheduler _snapshotScheduler;
	@Inject protected AccountManager _accountMgr;
	@Inject protected VolumeDao _volsDao;
	@Inject protected HostDao _hostDao;
	@Inject protected DetailsDao _detailsDao;
	@Inject protected SnapshotDao _snapshotDao;
	protected Adapters<StoragePoolAllocator> _storagePoolAllocators;
	@Inject protected VMTemplateHostDao _vmTemplateHostDao = null;
	@Inject protected VMTemplatePoolDao _vmTemplatePoolDao = null;
	@Inject protected VMTemplateDao _vmTemplateDao = null;
	@Inject protected StoragePoolHostDao _poolHostDao = null;
	@Inject protected VmDiskDao _vmDiskDao = null;
	@Inject protected UserVmDao _userVmDao;
	@Inject protected VMInstanceDao _vmInstanceDao;
	@Inject protected StoragePoolDao _storagePoolDao = null;
	@Inject protected CapacityDao _capacityDao;
	@Inject protected DiskOfferingDao _diskOfferingDao;
	@Inject protected AccountDao _accountDao;
	@Inject protected EventDao _eventDao = null;
	@Inject protected HostPodDao _podDao = null;
    
    ScheduledExecutorService _executor = null;
    boolean _storagePoolCleanupEnabled;
    int _storagePoolCleanupInterval;
    int _storagePoolAcquisitionWaitSeconds = 1800;		// 30 minutes
    protected int _retry = 2;

	protected int _overProvisioningFactor;


	@Override
	public Map<String, Integer> share(VMInstanceVO vm, List<VolumeVO> vols, HostVO host, boolean cancelPreviousShare) throws StorageUnavailableException {
		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Asking for volumes of " + vm.toString() + " to be shared to " + host.toString());
		}
		
		// FIXME: We assume all of the volumes are stored on one particular storage server but it may not be true.
		assert vols.size() > 0 : "Volumes for " + vm.toString() + " is zero.";
		
		VolumeVO vol = vols.get(0);
		// For storage pools send down the share command to the same host the vm will get started on
		if (vol.getStorageResourceType() == StorageResourceType.STORAGE_POOL) {
			vol.setHostId(host.getId());
		}
		
        ShareCommand shareCmd = new ShareCommand(vm.getInstanceName(), vols, host.getStorageUrl(), cancelPreviousShare);
        try {
    
            ShareAnswer shareAns = (ShareAnswer)_agentMgr.send(vol.getHostId(), shareCmd);
            if (!shareAns.getResult()) {
                s_logger.warn("Can not share " + vm.toString() + " on host " + vol.getHostId() + ": " + shareAns.getDetails());
                throw new StorageUnavailableException(vol.getHostId());
            }
            
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Shared successfully");
            }
            
            return shareAns.getMappings();
        } catch (AgentUnavailableException e) {
            throw new StorageUnavailableException(vol.getHostId());
        } catch (OperationTimedoutException e) {
            throw new StorageUnavailableException(vol.getHostId());
        }
	}
	
	@Override
	public List<Pair<VolumeVO, StoragePoolVO>> isStoredOn(VMInstanceVO vm) {
	    List<Pair<VolumeVO, StoragePoolVO>> lst = new ArrayList<Pair<VolumeVO, StoragePoolVO>>();
	    
	    List<VolumeVO> vols = _volsDao.findByInstance(vm.getId());
	    for (VolumeVO vol : vols) {
	        StoragePoolVO pool = _storagePoolDao.findById(vol.getPoolId());
	        lst.add(new Pair<VolumeVO, StoragePoolVO>(vol, pool));
	    }
	    
	    return lst;
	}
	
	@Override
	public List<VolumeVO> unshare(VMInstanceVO vm, HostVO host) {
        final List<VolumeVO> vols = _volsDao.findByInstance(vm.getId());
        if (vols.size() == 0) {
            return vols;
        }

        return unshare(vm, vols, host) ? vols : null;
	}
	
	@Override
    public HostVO findHost(final DataCenterVO dc, final HostPodVO pod, final ServiceOffering offering, final DiskOfferingVO dataDiskOffering, final VMTemplateVO template, final DiskOfferingVO rootDiskOffering, final Set<Host> avoid) {
        return null;
    }
	
	@Override
    public StoragePoolVO findStoragePool(final DataCenterVO dc, HostPodVO pod, final ServiceOffering offering, final DiskOfferingVO dataDiskOffering, final VMInstanceVO vm, final VMTemplateVO template, final DiskOfferingVO rootDiskOffering, final Set<StoragePool> avoid) {
		Enumeration<StoragePoolAllocator> en = _storagePoolAllocators.enumeration();
        while(en.hasMoreElements()) {
            final StoragePoolAllocator allocator = en.nextElement();
            final StoragePool pool = allocator.allocateToPool(offering, dataDiskOffering, dc, pod, vm, template, rootDiskOffering, avoid);
            if (pool != null) {
                return (StoragePoolVO)pool;
            }
        }
        return null;
    }
	
	@Override @DB
	public long create(Account account, VMInstanceVO vm, VMTemplateVO template, DataCenterVO dc, HostPodVO pod, ServiceOfferingVO offering, DiskOfferingVO diskOffering) {
        final Host [] selectedHosts = new Host[2];
        final int numVolumes = offering.isMirroredVolumes() ? 2 : 1;
        long accountId = account.getId();
        
        final MirrorState mirrorState = offering.isMirroredVolumes() ? MirrorState.ACTIVE : MirrorState.NOT_MIRRORED;
        final CreateAnswer answers[] = new CreateAnswer[numVolumes];
        
        int numCreated = 0;
        long vmId = vm.getId();
        
        String rootdiskFolder = null;

        if (numCreated != numVolumes) {
        	return createInPool(account, vm, template, dc, pod, offering, null, diskOffering, new ArrayList<StoragePoolVO>());
        }

        Map<Volume.VolumeType, VolumeVO[]> mirrVols = null;
        if (numCreated == 2) {
            mirrVols = new HashMap<VolumeType, VolumeVO[]>();
        }
        
		Transaction txn = Transaction.currentTxn();
        txn.start();

        for (int i = 0; i < numCreated; i++) {
            final CreateAnswer answer = answers[i];
            for (final VolumeVO v : answer.getVolumes()) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Saving root disk volume with folder: " + rootdiskFolder);
                }
                v.setFolder(rootdiskFolder);
                v.setHostId(selectedHosts[i].getId());
                v.setDataCenterId(dc.getId());
                v.setPodId(pod.getId());
                v.setAccountId(accountId);
                v.setInstanceId(vmId);
                v.setMirrorState(mirrorState);
                v.setHostIp(selectedHosts[i].getStorageIpAddress()); //for convenience
                v.setTemplateName(template.getUniqueName());
                v.setTemplateId(template.getId());
                final long vid = _volsDao.persist(v);
                if (mirrVols != null) { //save the mirrored set so that we can set the mirror id
                    VolumeVO[] voltypevols = mirrVols.get(v.getVolumeType());
                    if (voltypevols == null) {
                        voltypevols = new VolumeVO[2];
                        mirrVols.put(v.getVolumeType(), voltypevols);
                    }
                    voltypevols[i] = _volsDao.findById(vid);
                }
            }
        }
        if (mirrVols != null) {
            for (final Volume.VolumeType vType : mirrVols.keySet()) {
                final VolumeVO[] voltypevols = mirrVols.get(vType);
                voltypevols[0].setMirrorVolume(voltypevols[1].getId());
                voltypevols[1].setMirrorVolume(voltypevols[0].getId());
            }
            for (final Volume.VolumeType vType : mirrVols.keySet()) {
                final VolumeVO[] voltypevols = mirrVols.get(vType);
                _volsDao.update(voltypevols[0].getId(), voltypevols[0]);
                _volsDao.update(voltypevols[1].getId(), voltypevols[1]);
            }
        }

        txn.commit();

        s_logger.debug("VM Created:" + vm.toString());
        return 0;
	}
	
	
	@Override @DB
	public long createInPool(Account account, VMInstanceVO vm, VMTemplateVO template, DataCenterVO dc, HostPodVO pod, ServiceOffering offering, DiskOfferingVO rootDiskOffering, DiskOfferingVO dataDiskOffering, List<StoragePoolVO> avoids) {
        String rootdiskFolder = null;
        String datadiskFolder = null;
        String datadiskName = null;
        final int numVolumes = 1;
        
        int rootdiskSize = 0;
        int datadiskSize = 0;
        
        if (rootDiskOffering != null)
        	rootdiskSize = (int) rootDiskOffering.getDiskSize()/1024;
        
        if (dataDiskOffering != null)
        	datadiskSize = (int) dataDiskOffering.getDiskSize()/1024;
        
        final MirrorState mirrorState = MirrorState.NOT_MIRRORED;
        CreateAnswer answer = null;
        
        int retry = _retry * numVolumes;
//        long vmId = vm.getId();

        boolean created = false;
        StoragePoolVO pool = null;
        StoragePoolHostVO poolHost = null;
        final HashSet<StoragePool> avoidPools = new HashSet<StoragePool>(avoids);
        
        while ((pool = findStoragePool(dc, pod, offering, dataDiskOffering, vm, template, null, avoidPools)) != null && --retry >= 0) {
            avoidPools.add(pool);
            StoragePoolVO vo = _storagePoolDao.findById(pool.getId());
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to create " + vm.toString() + " on storage pool " + pool.getName());
            }
            
            if (!Storage.ImageFormat.ISO.equals(template.getFormat())) {
                if (_tmpltMgr.prepareTemplateForCreate(template, pool) == null) {
                	continue;
                }
            }

            List<Long> avoidHosts = new ArrayList<Long>();
            int retryHost = 2;
            while ((poolHost = chooseHostForStoragePool(pool, avoidHosts)) != null && --retryHost >= 0) {
            	avoidHosts.add(poolHost.getHostId());
            	
            	// Determine the folder to store the VM's rootdisk in
            	rootdiskFolder = getVolumeFolder(poolHost.getLocalPath() + '/' + "vm", account.getId(), vm.getInstanceName());
            	
            	if (datadiskSize != 0) {
            		// Determine the folder to store the VM's datadisk in
            		datadiskFolder = getVolumeFolder(poolHost.getLocalPath() + '/' + "vm", account.getId(), "datadisks");
            		
            		// Determine the datadisk's name
            		datadiskName = UUID.randomUUID().toString();
            	}

        		if (s_logger.isDebugEnabled()) {
        			s_logger.debug("Attempting to create VM " + vm.toString() + " on storage pool " + pool.getName() + " using host id=" + poolHost.getHostId());
        		}

            	final CreateCommand cmdCreate;

            	VMTemplateStoragePoolVO templateStoragePoolVO = null;
            	String tmpltinstallpath = null;
            	if ((template != null) && !Storage.ImageFormat.ISO.equals(template.getFormat())) {
                    List<VMTemplateStoragePoolVO> vths =
                        _vmTemplatePoolDao.listByTemplateStatus(template.getId(), VMTemplateHostVO.Status.DOWNLOADED, vo.getId());
                    if( vths.isEmpty() ) {
                        s_logger.debug("template is not installed : " + template.getName());
                        return 0;
                    }
                    templateStoragePoolVO = vths.get(0);
                    tmpltinstallpath = templateStoragePoolVO.getLocalDownloadPath();
            	}
            	
            	VMTemplateStoragePoolVO templateStoragePoolLock;
            	try {
            		if (templateStoragePoolVO != null) {
            			templateStoragePoolLock = _vmTemplatePoolDao.acquire(templateStoragePoolVO.getId(), _storagePoolAcquisitionWaitSeconds);
            				
            			if (templateStoragePoolLock == null) {
            				throw new VmopsRuntimeException("DeployVM was unable to acquire lock on VMTemplateStoragePoolVO: " + templateStoragePoolVO.getId());
            			}
            		}
            		
            		if (rootdiskSize != 0) {
            			// This command is for VMs that are being created with a blank rootdisk and no datadisk
            			//            		cmdCreate = new CreateCommand(account.getId(), vm.getName(), rootdiskSize, rootdiskFolder, vo, mntpoint);
            			cmdCreate = new CreateCommand(account.getId(), vm.getName(), rootdiskSize, rootdiskFolder, vo, null, template.getUniqueName());
            		} else {
            			// This command is for UserVMs or DomRs that are being created with a rootdisk that is cloned from a template
            			cmdCreate = new CreateCommand(account.getId(), vm.getName(), tmpltinstallpath, null, null, rootdiskFolder, datadiskFolder, datadiskName, datadiskSize, vo, null, template.getUniqueName());
            		}
            		cmdCreate.setLocalPath(poolHost.getLocalPath());

            		answer = (CreateAnswer)_agentMgr.easySend(poolHost.getHostId(), cmdCreate);
            		if (answer != null) {
            			if (s_logger.isDebugEnabled()) {
            				s_logger.debug("VM " + vm.toString() + " is created on storage pool " + pool.getName() + " using host id=" + poolHost.getHostId());
            			}
            			created = true;
            			break; //out of inner loop
            		}
            	} finally {
            		if (templateStoragePoolVO != null) {
            			_vmTemplatePoolDao.release(templateStoragePoolVO.getId());
            		}
            	}
            	
            	s_logger.warn("Unable to create volume on pool " + pool.getName() + " using host id= " + poolHost.getHostId());
            }
        	if (poolHost == null) {
        		s_logger.warn("No more hosts found in UP state for pool " + pool.getName());
        		continue;
        	} else if (created){
        		break;//out of outer loop
        	}
        }

        if (answer == null) {
        	return 0;
        }
        
		Transaction txn = Transaction.currentTxn();
        txn.start();

        for (final VolumeVO v : answer.getVolumes()) {
        	if (s_logger.isDebugEnabled()) {
        		s_logger.debug("Saving volume: " + v.getPath());
        	}
        	VolumeType vType = v.getVolumeType();
        	
        	v.setPoolId(pool.getId());
        	v.setHostId(poolHost.getHostId());
        	v.setDataCenterId(dc.getId());
        	v.setPodId(pod.getId());
        	v.setAccountId(account.getId());
        	v.setDomainId(account.getDomainId().longValue());
        	v.setInstanceId(vm.getId());
        	v.setMirrorState(mirrorState);
        	
        	// If this volume was made for an ISO boot, it will not be associated with a template
        	if (template != null && (template.getFormat() != Storage.ImageFormat.ISO) && vType == VolumeType.ROOT) {
        		v.setTemplateName(template.getUniqueName());
        		v.setTemplateId(template.getId());
        	} else {
        		v.setTemplateName("none");
        	}
        	
            long doId = -1;
            long templateId = -1;
        	
        	// If the volume is for a datadisk, set the disk offering ID to be dataDiskOffering.getId()
        	// If the volume is for a rootdisk that is for a blank VM, set the disk offering ID to be rootDiskOffering.getId()
        	// Else, set the disk offering ID to be null
        	if (v.getVolumeType() == VolumeType.DATADISK && dataDiskOffering != null) {
        		v.setDiskOfferingId(dataDiskOffering.getId());
        		doId = dataDiskOffering.getId();
        	} else if (v.getVolumeType() == VolumeType.ROOT && rootDiskOffering != null) {
        		v.setDiskOfferingId(rootDiskOffering.getId());
        		doId = rootDiskOffering.getId();
        	} else if(v.getVolumeType() == VolumeType.ROOT){
                templateId = template.getId();
            } else {
        		v.setDiskOfferingId(null);
        	}

        	long volumeId = _volsDao.persist(v);
        	// Create an event
        	String eventParams = "id=" + volumeId +"\ndoId="+doId+"\ntId="+templateId+"\ndcId="+dc.getId();
        	EventVO event = new EventVO();
        	event.setAccountId(account.getId());
        	event.setUserId(1L);
        	event.setType(EventTypes.EVENT_VOLUME_CREATE);
        	event.setParameters(eventParams);        	
        	long sizeMB = v.getSize()/(1024*1024);
        	event.setDescription("Created volume: "+ v.getName() +" with size: " + sizeMB + " MB in pool: " + pool.getName());
        	_eventDao.persist(event);
        }
        
        
        txn.commit();

        s_logger.debug("VM Created:" + vm.toString());
        return pool.getId();
	}
	
	@Override
	public long createUserVM(Account account, VMInstanceVO vm, VMTemplateVO template, DataCenterVO dc, HostPodVO pod, ServiceOffering offering, DiskOfferingVO rootDiskOffering, DiskOfferingVO dataDiskOffering, List<StoragePoolVO> avoids) {
       	return createInPool(account, vm, template, dc, pod, offering, rootDiskOffering, dataDiskOffering, avoids);
	}
	
	public StoragePoolHostVO chooseHostForStoragePool(StoragePoolVO poolVO, List<Long> avoidHosts) {
		List<StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(poolVO.getId(), Status.Up);
		Collections.shuffle(poolHosts);
		if (poolHosts != null && poolHosts.size() > 0) {
			for (StoragePoolHostVO sphvo: poolHosts){
				if (!avoidHosts.contains(sphvo.getHostId())) {
				   return sphvo;
				}
			}
		}
		return null;
	}
	
	@Override
	public Long chooseHostForVolume(Volume vol){
		if (vol.getStorageResourceType() == StorageResourceType.STORAGE_HOST){
			return vol.getHostId();
		}
		List<StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(vol.getPoolId(), Status.Up);
		if (poolHosts != null && poolHosts.size() > 0) {
			return poolHosts.get(0).getHostId();
		}
		return null;
	}
	
    @Override
    public String chooseStorageIp(VMInstanceVO vm, Host host, Host storage) {
        Enumeration<StoragePoolAllocator> en = _storagePoolAllocators.enumeration();
        while(en.hasMoreElements()) {
            StoragePoolAllocator allocator = en.nextElement();
            String ip = allocator.chooseStorageIp(vm, host, storage);
            if (ip != null) {
                return ip;
            }
        }
        
        assert false : "Hmm....fell thru the loop";
        return null;
    }

	@Override
	public boolean unshare(VMInstanceVO vm, List<VolumeVO> vols, HostVO host) {
		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Asking for volumes of " + vm.toString() + " to be unshared to " + (host != null ? host.toString() : "all"));
		}
		
		VolumeVO vol = vols.get(0);
		if (vol.getStorageResourceType() != StorageResourceType.STORAGE_HOST) {
			return true;
		}
		
		ShareCommand shareCmd = new ShareCommand(vm.getInstanceName(), vols, host != null ? host.getStorageUrl() : null);
        ShareAnswer answer = (ShareAnswer)_agentMgr.easySend(vol.getHostId(), shareCmd);
        if (answer == null) {
        	if (s_logger.isDebugEnabled()) {
        		s_logger.debug("Unable to unshare");
        	}
        	return false;
        }
        
        return true;
	}

	@Override
	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
		_name = name;
		
		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		
		ScheduledVolumeBackupDao vmBackupDao = locator.getDao(ScheduledVolumeBackupDao.class);
        if (vmBackupDao == null) {
            throw new ConfigurationException("Unable to find " + ScheduledVolumeBackupDao.class);
        }

        UserVmManager userVmMgr = locator.getManager(UserVmManager.class);
        if (userVmMgr == null) {
            throw new ConfigurationException("Unable to find " + UserVmManager.class);
        }

        ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
        if (configDao == null) {
            s_logger.error("Unable to get the configuration dao.");
            return false;
        }
        
        _storagePoolAllocators = locator.getAdapters(StoragePoolAllocator.class);
        if (!_storagePoolAllocators.isSet()) {
            throw new ConfigurationException("Unable to get any storage pool allocators.");
        }
        
        String overProvisioningFactorStr = (String) params.get("storage.overprovisioning.factor");
        if (overProvisioningFactorStr != null) {
            _overProvisioningFactor = Integer.parseInt(overProvisioningFactorStr);
        }
        
        Map<String, String> configs = configDao.getConfiguration("management-server", params);

        VolumeBackupListener backupListener = new VolumeBackupListener(_agentMgr, userVmMgr, vmBackupDao, _volsDao, _snapshotDao);

        // look for some test values in the configuration table so that snapshots can be taken more frequently (QA test code)
        int minutesPerHour = NumbersUtil.parseInt(configs.get("snapshot.test.minutes.per.hour"), 60);
        int hoursPerDay = NumbersUtil.parseInt(configs.get("snapshot.test.hours.per.day"), 24);
        int daysPerWeek = NumbersUtil.parseInt(configs.get("snapshot.test.days.per.week"), 7);
        int daysPerMonth = NumbersUtil.parseInt(configs.get("snapshot.test.days.per.month"), 30);
        int weeksPerMonth = NumbersUtil.parseInt(configs.get("snapshot.test.weeks.per.month"), 4);
        int monthsPerYear = NumbersUtil.parseInt(configs.get("snapshot.test.months.per.year"), 12);
        backupListener.init(minutesPerHour, hoursPerDay, daysPerWeek, daysPerMonth, weeksPerMonth, monthsPerYear);

        _snapshotScheduler.init(_snapshotMgr, minutesPerHour, hoursPerDay, daysPerWeek, daysPerMonth, weeksPerMonth, monthsPerYear);
        
        _retry = NumbersUtil.parseInt(configs.get("start.retry"), 2);
        
        _storagePoolAcquisitionWaitSeconds = NumbersUtil.parseInt(
        	configs.get("pool.acquisition.wait.seconds"), 1800);
        s_logger.info("pool.acquisition.wait.seconds is configured as " + _storagePoolAcquisitionWaitSeconds + " seconds");
        
        _agentMgr.registerForHostEvents(backupListener, true, false);
        _agentMgr.registerForHostEvents(new StoragePoolMonitor(this, _hostDao, _storagePoolDao), true, false);
        
        String storagePoolCleanupEnabled = configs.get("storage.pool.cleanup.enabled");
        _storagePoolCleanupEnabled = (storagePoolCleanupEnabled == null) ? true : Boolean.parseBoolean(storagePoolCleanupEnabled);
        
        String time = configs.get("storage.pool.cleanup.interval");
        _storagePoolCleanupInterval = NumbersUtil.parseInt(time, 86400);
        
        String workers = configs.get("expunge.workers");
        int wrks = NumbersUtil.parseInt(workers, 10);
        _executor = Executors.newScheduledThreadPool(wrks, new NamedThreadFactory("StorageManager-Scavenger"));

		return true;
	}

    public List<String> listIsos(String managementServerMountDir, long accountId, short accountType) {

    	List<String> isos = new ArrayList<String>();

    	// Strip off any trailing '/' in managementServerMountDir
    	if (managementServerMountDir.endsWith("/"))
    		managementServerMountDir = managementServerMountDir.substring(0, managementServerMountDir.length() - 1);
    	
    	// Create the search directory
    	String searchDir = managementServerMountDir + "/users/";
    	if (!BaseCmd.isAdmin(accountType))
    		searchDir += accountId;
    	
    	// Define the find command
    	String command = "find " + searchDir + " -name '*.iso' -print0";
    	
		Script s = new Script("bash");
    	s.add("-c");
    	s.add(command);
    	
    	OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
    	if (s.execute(parser) != null)
    		return isos;
    	
    	String result = parser.getLine();
    	if (result == null)
    		return isos;
    	
    	// Find the length of searchDir
    	// We will need this to trim the results of the find command
    	// I.e. if an ISO path returned by find is '[searchDir]/iso/a.iso', we want to obtain /iso/a.iso
    	int lengthOfSearchDirString = searchDir.length();
    	
    	String[] rawIsoList = result.split("\0");
    	for (String iso : rawIsoList) {
    		if (iso != null && !iso.trim().isEmpty())
    			isos.add(iso.substring(lengthOfSearchDirString));
    	}

    	return isos;
    }
    
    public String getVolumeFolder(String parentDir, long accountId, String diskFolderName) {
		StringBuilder diskFolderBuilder = new StringBuilder();
		Formatter diskFolderFormatter = new Formatter(diskFolderBuilder);
		diskFolderFormatter.format("%s/u%06d/%s", parentDir, accountId, diskFolderName);
		return diskFolderBuilder.toString();
    }
    
    public String getRandomVolumeName() {
    	return UUID.randomUUID().toString();
    }
    
    public String getVolumeNameLabel(VolumeVO volume, VMInstanceVO vm) {
    	// Determine the name label
		String nameLabel;
		if (vm == null) {
			nameLabel = "detached";
		} else {
			String diskType;
			
			if (volume.getVolumeType() == VolumeType.ROOT) {
				diskType = "-ROOT";
			} else if (volume.getVolumeType() == VolumeType.DATADISK) {
				diskType = "-DATA";
			} else {
				diskType = "";
			}
			
			nameLabel = vm.getInstanceName() + diskType;
		}
		
		return nameLabel;
    }
    
    public boolean volumeOnSharedStoragePool(VolumeVO volume) {
    	Long poolId = volume.getPoolId();
		if (poolId == null) {
			return false;
		} else {
			StoragePoolVO pool = _storagePoolDao.findById(poolId);

			if (pool == null) {
				return false;
			} else {
				return pool.isShared();
			}
		}
    }
    
    public boolean volumeInactive(VolumeVO volume) {
		Long vmId = volume.getInstanceId();
		if (vmId != null) {
			UserVm vm = _userVmDao.findById(vmId);

			if (vm == null) {
				return false;
			}

			if (!vm.getState().equals(State.Stopped)) {
				return false;
			}
		}
		
		return true;
    }
    
    public String getAbsoluteIsoPath(long templateId, long dataCenterId) {
    	String isoPath = null;

	    List<HostVO> storageHosts = _hostDao.listBy(Host.Type.SecondaryStorage, dataCenterId);
	    if (storageHosts != null) {
	        for (HostVO storageHost : storageHosts) {
                VMTemplateHostVO templateHostVO = _vmTemplateHostDao.findByHostTemplate(storageHost.getId(), templateId);
                if (templateHostVO != null) {
                    isoPath = storageHost.getStorageUrl() + "/" + templateHostVO.getInstallPath();
                    break;
                }
	        }
	    }
	    
	    return isoPath;
    }
    
    public String getSecondaryStorageURL(long zoneId) {
    	// Determine the secondary storage URL
        HostVO secondaryStorageHost = _hostDao.findSecondaryStorageHost(zoneId);
        
        if (secondaryStorageHost == null) {
        	return null;
        }
        
        return secondaryStorageHost.getStorageUrl();
    }
    
    public HostVO getSecondaryStorageHost(long zoneId) {
    	return _hostDao.findSecondaryStorageHost(zoneId);
    }
    
	@Override
	public String getName() {
		return _name;
	}

	@Override
	public boolean start() {
		if (_storagePoolCleanupEnabled) {
			_executor.scheduleWithFixedDelay(new StoragePoolGarbageCollector(this), _storagePoolCleanupInterval, _storagePoolCleanupInterval, TimeUnit.SECONDS);
		} else {
			s_logger.debug("Storage pool cleanup is not enabled, so the storage pool cleanup thread is not being scheduled.");
		}
		
		return true;
	}

	@Override
	public boolean stop() {
		if (_storagePoolCleanupEnabled) {
			_executor.shutdown();
		}
		
		return true;
	}

	protected StorageManagerImpl() {
	}

    public StoragePoolVO createPool(long zoneId, long podId, String poolName, URI uri) throws ResourceInUseException, IllegalArgumentException {
        StoragePoolType type;
        String scheme = uri.getScheme();
        String storageHost = uri.getHost();
        String hostPath = uri.getPath();
        int port = uri.getPort();
        if (scheme.equalsIgnoreCase("nfs")) {
            type = StoragePoolType.NetworkFilesystem;
            if (port == -1) {
                port = 111;
            }
        } else if (scheme.equalsIgnoreCase("file")) {
            type = StoragePoolType.Filesystem;
            if (port == -1) {
                port = 0;
            }
        } else if (scheme.equalsIgnoreCase("iscsi")) {
            type = StoragePoolType.IscsiLUN;
            hostPath.replaceFirst("/", "");
            if (port == -1) {
                port = 3260;
            }
        } else if (scheme.equalsIgnoreCase("iso")) {
            type = StoragePoolType.ISO;
            if (port == -1) {
                port = 111;
            }
        } else {
            s_logger.warn("Unable to figure out the scheme for URI: " + uri);
            throw new IllegalArgumentException("Unable to figure out the scheme for URI: " + uri);
        }
               
        
        List<StoragePoolVO> pools = _storagePoolDao.listPoolByHostPath(zoneId, storageHost, hostPath);
        StoragePoolVO pool = _storagePoolDao.findPoolByHostPath(zoneId, podId, storageHost, hostPath);
        if (!pools.isEmpty()) {
        	Long oldPodId = pools.get(0).getPodId();
        	throw new ResourceInUseException("Storage pool " + uri + " already in use by another pod (id=" + oldPodId + ")", "StoragePool", uri.toASCIIString());
        }
        
        if (pool == null) {
            long poolId = _storagePoolDao.getNextInSequence(Long.class, "id");
            String uuid = UUID.nameUUIDFromBytes(new String(storageHost+hostPath).getBytes()).toString();
            pool = new StoragePoolVO(poolId, poolName, uuid, type, zoneId, podId, 0, 0, storageHost, port, hostPath);
            Long id =_storagePoolDao.persist(pool);
            pool = _storagePoolDao.findById(id);
        }
        //iterate through all the hosts and ask them to mount the filesystem.
        //FIXME Not a very scalable implementation. Need an async listener, or perhaps do this on demand, or perhaps mount on a couple of hosts per pod
        List<HostVO> allHosts = _hostDao.listBy(Host.Type.Computing, podId, zoneId);
        allHosts.addAll(_hostDao.listBy(Host.Type.Routing, podId, zoneId));
        boolean result = false;
        for (HostVO h: allHosts) {
            boolean success = addPoolToHost(h.getId(), pool);
            result = result || success;
        }
        
        if (!allHosts.isEmpty() && ! result) {
            _storagePoolDao.delete(pool.getId());
            pool = null;
        }

        return pool;
    }
    
	

	@Override
	public boolean addPoolToHost(long hostId, StoragePoolVO pool) {
		s_logger.debug("Adding pool " + pool.getName() + " to  host " + hostId);
		if (pool.getPoolType() != StoragePoolType.NetworkFilesystem
		        && pool.getPoolType() != StoragePoolType.Filesystem
		        &&pool.getPoolType() != StoragePoolType.IscsiLUN ) {
		    return true;
		}
		ModifyStoragePoolCommand cmd = new ModifyStoragePoolCommand(true, pool);
		final Answer answer = _agentMgr.easySend(hostId, cmd);
		
		if (answer != null) {
			if (answer.getResult() == false) {
				return false;
			}
			if (answer instanceof ModifyStoragePoolAnswer ){
				ModifyStoragePoolAnswer mspAnswer = (ModifyStoragePoolAnswer) answer;

				StoragePoolHostVO poolHost = _poolHostDao.findByPoolHost(pool.getId(), hostId);
				if (poolHost == null) {
					poolHost = new StoragePoolHostVO(pool.getId(), hostId, mspAnswer.getPoolInfo().getLocalPath() );
					_poolHostDao.persist(poolHost);
				} else {
					poolHost.setLocalPath(mspAnswer.getPoolInfo().getLocalPath());
				}
				pool.setAvailableBytes(mspAnswer.getPoolInfo().getAvailableBytes());
				pool.setCapacityBytes(mspAnswer.getPoolInfo().getCapacityBytes());
				_storagePoolDao.update(pool.getId(), pool);
				return true;
			}
			
		} else {
			return false;
		}
		return false;
	}
	
	@Override
	@DB
	public VolumeVO createVolumeInPool(long accountId, long userId, String userSpecifiedName, DataCenterVO dc, DiskOfferingVO diskOffering) {
		VolumeVO createdVolume = null;
		Long volumeId = null;
		
		// Determine the volume's name
		String volumeName = getRandomVolumeName();
		
		long volumeSize = diskOffering.getDiskSize()/1024;
		String volumeFolder = null;
		String volumePath = null;
		
		// Create the Volume object and save it so that we can return it to the user
		Account account = _accountDao.findById(accountId);
		VolumeVO volume = new VolumeVO(null, userSpecifiedName, -1, -1, -1, -1, -1, new Long(-1), null, null, 0, Volume.VolumeType.DATADISK);
		volume.setPoolId(null);
    	volume.setHostId(null);
    	volume.setDataCenterId(dc.getId());
    	volume.setPodId(null);
    	volume.setAccountId(accountId);
    	volume.setDomainId(account.getDomainId().longValue());
    	volume.setMirrorState(MirrorState.NOT_MIRRORED);
    	volume.setDiskOfferingId(diskOffering.getId());
    	volume.setStorageResourceType(StorageResourceType.STORAGE_POOL);
    	volume.setInstanceId(null);
    	volume.setNameLabel("detached");
    	volume.setStatus(AsyncInstanceCreateStatus.Creating);
    	volumeId = _volsDao.persist(volume);
		
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if(asyncExecutor != null) {
        	AsyncJobVO job = asyncExecutor.getJob();

        	if(s_logger.isInfoEnabled())
        		s_logger.info("CreateVolume created a new instance " + volumeId + ", update async job-" + job.getId() + " progress status");
        	
        	_asyncMgr.updateAsyncJobAttachment(job.getId(), "volume", volumeId);
        	_asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, volumeId);
        }
		
        final HashSet<StoragePool> poolsToAvoid = new HashSet<StoragePool>();
		StoragePoolVO pool = null;
		int retry = _retry;
		StoragePoolHostVO poolHost = null;
		Long createdSize = null;
		boolean success = false;
        Set<Long> podsToAvoid = new HashSet<Long>();
		HostPodVO pod = null;
		
		// Determine what pod to store the volume in
		while ((pod = _agentMgr.findPod(null, null, dc, account.getId(), podsToAvoid)) != null) {
			// Determine what storage pool to store the volume in
			while ((pool = findStoragePool(dc, pod, null, diskOffering, null, null, null, poolsToAvoid)) != null && --retry >= 0) {
	            
	            List<Long> avoidHosts = new ArrayList<Long>();
	            int retryHost = 2;
	            while ((poolHost = chooseHostForStoragePool(pool, avoidHosts)) != null && --retryHost >= 0) {
	            	avoidHosts.add(poolHost.getHostId());
	            	
	            	HostVO host = _hostDao.findById(poolHost.getHostId());
	            	
	            	if (host == null)
	            		continue;
	
	            	// Determine the folder to store the volume in
	            	String localPath = poolHost.getLocalPath() + '/' + "vm";
	            	
	            	if (localPath == null)
	            		continue;
	            	
	        		volumeFolder = getVolumeFolder(localPath, accountId, "datadisks");
	        		volumePath = volumeFolder + "/" + volumeName;
	
	        		if (s_logger.isDebugEnabled()) {
	        			s_logger.debug("Attempting to create volume with path " + volumePath + " on storage pool " + pool.getName() + " using host id=" + poolHost.getHostId());
	        		}
	        		
	            	final ManageVolumeCommand cmd = new ManageVolumeCommand(true, volumeSize, volumeFolder, volumePath, volumeName, null, pool);
	            	ManageVolumeAnswer answer = (ManageVolumeAnswer) _agentMgr.easySend(poolHost.getHostId(), cmd);
	            	
	            	if (answer != null && answer.getResult() && answer.getSize() != null) {
	            		if (s_logger.isDebugEnabled()) {
	            			s_logger.debug("Volume with path " + volumePath + " was created on storage pool " + pool.getName() + " using host id=" + poolHost.getHostId());
	            		}
	            		
	            		createdSize = answer.getSize();
	            		if (answer.getUuid() != null) {
	            			// This command was executed on XenServer
	            			volumeFolder = answer.getSrName();
	            			volumePath = answer.getUuid();
	            		}
	            		success = true;
	            		break; // break out of the "choose host" loop
	            	}
	
	            	s_logger.warn("Unable to create volume on pool " + pool.getName() + " using host id= " + poolHost.getHostId());
	            }
	            
	        	if (poolHost == null) {
	        		s_logger.warn("No more hosts found in UP state for pool " + pool.getName());
	        		poolsToAvoid.add(pool);
	        		continue;
	        	} else if (success){
	        		break; // break out of the "find storage pool" loop
	        	}
	        }
			
			if (success) {
				break; // break out of the "find pod" loop
			} else {
				podsToAvoid.add(pod.getId());
			}
		}
		
        // Create an event
        EventVO event = new EventVO();
        event.setAccountId(accountId);
        event.setUserId(userId);
        event.setType(EventTypes.EVENT_VOLUME_CREATE);
			
        // Update the volume in the database
        Transaction txn = Transaction.currentTxn();
        try {
        	txn.start();
        	createdVolume = _volsDao.findById(volumeId);
        	
        	if (success) {
        		// Increment the number of volumes
        		_accountMgr.incrementResourceCount(accountId, ResourceType.volume);
        		
        		createdVolume.setStatus(AsyncInstanceCreateStatus.Created);
        		createdVolume.setHostId(poolHost.getHostId());
        		createdVolume.setPodId(pod.getId());
        		createdVolume.setPoolId(pool.getId());
        		createdVolume.setFolder(volumeFolder);
        		createdVolume.setPath(volumePath);
        		createdVolume.setSize(createdSize);
        		createdVolume.setDomainId(account.getDomainId().longValue());
        		long sizeMB = createdVolume.getSize()/(1024*1024);
        		event.setDescription("Created volume: "+ createdVolume.getName() +" with size: " + sizeMB + " MB in pool: " + pool.getName());
        		event.setLevel(EventVO.LEVEL_INFO);
                long templateId = -1;
                String eventParams = "id=" + volumeId +"\ndoId="+diskOffering.getId()+"\ntId="+templateId+"\ndcId="+dc.getId();
                event.setParameters(eventParams);
        	} else {
        		createdVolume.setStatus(AsyncInstanceCreateStatus.Corrupted);
        		createdVolume.setDestroyed(true);
        		event.setLevel(EventVO.LEVEL_ERROR);
        		event.setDescription("Failed to create volume with size: " + volumeSize);
        	}
        	
        	_volsDao.update(volumeId, createdVolume);
        	_eventDao.persist(event);
        	txn.commit();
        } catch (Exception e) {
        	s_logger.error("Unhandled exception while saving volume " + volumeName, e);
        }
        return createdVolume;
	}

	

	@Override
	public void deleteVolumeInPool(VolumeVO volume) throws InternalErrorException {
		Long poolId = volume.getPoolId();
		
		StoragePoolVO pool = _storagePoolDao.findById(poolId);
		List <StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(pool.getId(), Status.Up);
		
		if (poolHosts.size() == 0)
			throw new InternalErrorException("Did not find any hosts where the volume's pool is visible.");
		
		// We only need to send the command to one host
		Long hostId = poolHosts.get(0).getHostId();
		
		// Determine the name label
		String volumeNameLabel = getVolumeNameLabel(volume, null);
		
		final ManageVolumeCommand cmd = new ManageVolumeCommand(false, volume.getSize(), volume.getFolder(), volume.getPath(), volume.getName(), volumeNameLabel, pool);
    	ManageVolumeAnswer answer = (ManageVolumeAnswer) _agentMgr.easySend(hostId, cmd);
    	
    	if (answer != null && answer.getResult()) {
    		// Mark the volume as destroyed
    		_volsDao.destroyVolume(volume.getId());
    		
    		// Mark the volume as removed
    		_volsDao.remove(volume.getId());
    		
    		// Decrement the number of volumes
    		_accountMgr.decrementResourceCount(volume.getAccountId(), ResourceType.volume);
    		
    		// Create an event
            String eventParams = "id=" + volume.getId();
            EventVO event = new EventVO();
            event.setAccountId(volume.getAccountId());
            event.setUserId(1L);
            event.setType(EventTypes.EVENT_VOLUME_DELETE);
            event.setParameters(eventParams);
            event.setDescription("Volume deleted");
            event.setLevel(EventVO.LEVEL_INFO);
            _eventDao.persist(event);
    	} else {
    		String msg = "Failed to delete volume";
    		if (answer != null) {
    			String details = answer.getDetails();
    			if (details != null && !details.isEmpty())
    				msg += "; " + details;
    		}
    		throw new InternalErrorException(msg);
    	}
	}
	
	@Override
    public void createCapacityEntry(StoragePoolVO storagePool ) {
        SearchCriteria capacitySC = _capacityDao.createSearchCriteria();
        capacitySC.addAnd("hostOrPoolId", SearchCriteria.Op.EQ, storagePool.getId());
        capacitySC.addAnd("dataCenterId", SearchCriteria.Op.EQ, storagePool.getDataCenterId());
        capacitySC.addAnd("capacityType", SearchCriteria.Op.EQ, CapacityVO.CAPACITY_TYPE_STORAGE);

        List<CapacityVO> capacities = _capacityDao.search(capacitySC, null);
        
        if (capacities.size() == 0) {
        	CapacityVO capacity = new CapacityVO(storagePool.getId(), storagePool.getDataCenterId(), storagePool.getPodId(), 0L, storagePool.getCapacityBytes(), CapacityVO.CAPACITY_TYPE_STORAGE);
        	_capacityDao.persist(capacity);
        } else {
        	CapacityVO capacity = capacities.get(0);
        	if (capacity.getTotalCapacity() != storagePool.getCapacityBytes()) {
        		capacity.setTotalCapacity(storagePool.getCapacityBytes());
        		_capacityDao.update(capacity.getId(), capacity);
        	}
        }
        capacitySC = _capacityDao.createSearchCriteria();
        capacitySC.addAnd("hostOrPoolId", SearchCriteria.Op.EQ, storagePool.getId());
        capacitySC.addAnd("dataCenterId", SearchCriteria.Op.EQ, storagePool.getDataCenterId());
        capacitySC.addAnd("capacityType", SearchCriteria.Op.EQ, CapacityVO.CAPACITY_TYPE_STORAGE_ALLOCATED);

       capacities = _capacityDao.search(capacitySC, null);
        
        if (capacities.size() == 0) {
        	CapacityVO capacity = new CapacityVO(storagePool.getId(), storagePool.getDataCenterId(), storagePool.getPodId(), 0L, storagePool.getCapacityBytes()*_overProvisioningFactor, CapacityVO.CAPACITY_TYPE_STORAGE_ALLOCATED);
        	_capacityDao.persist(capacity);
        } else {
        	CapacityVO capacity = capacities.get(0);
        	long currCapacity = _overProvisioningFactor*storagePool.getCapacityBytes();
        	if (capacity.getTotalCapacity() != currCapacity) {
        		capacity.setTotalCapacity(currCapacity);
        		_capacityDao.update(capacity.getId(), capacity);
        	}
        }
    }

	@Override
	public Long chooseHostForStoragePool(Long poolId) {
		List<StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(poolId, Status.Up);
		Collections.shuffle(poolHosts);
		if (poolHosts != null && poolHosts.size() > 0) {
			return poolHosts.get(0).getHostId();
		}
		return null;
	}
	
	protected class StoragePoolGarbageCollector implements Runnable {
    	StorageManagerImpl _storageMgr;
    	public StoragePoolGarbageCollector(StorageManagerImpl storageMgr) {
    		_storageMgr = storageMgr;
    	}
    	
    	public void run() {
    		try {
    			s_logger.info("Storage Pool Garbage Collection Thread is running.");

    			GlobalLock scanLock = GlobalLock.getInternLock(this.getClass().getName());
    			try {
    				if(scanLock.lock(3)) {
    					try {
    		    			_storageMgr.cleanupStoragePools(true);
    					} finally {
    						scanLock.unlock();
    					}
    				}
    			} finally {
    				scanLock.releaseRef();
    			}
    			
    		} catch (Exception e) {
    			s_logger.error("Caught the following Exception", e);
    		}
    	}
    }

	public void cleanupStoragePools(boolean recurring) {
		List<StoragePoolVO> storagePools = _storagePoolDao.listAllActive();
		
		for (StoragePoolVO pool : storagePools) {
			if (recurring && pool.isLocal()) {
				continue;
			}
			
			List<VMTemplateStoragePoolVO> unusedTemplatesInPool = _tmpltMgr.getUnusedTemplatesInPool(pool);
			s_logger.debug("Storage pool garbage collector found " + unusedTemplatesInPool.size() + " templates to clean up in storage pool: " + pool.getName());
			for (VMTemplateStoragePoolVO templatePoolVO : unusedTemplatesInPool) {
				if (templatePoolVO.getDownloadState() != VMTemplateStorageResourceAssoc.Status.DOWNLOADED) {
					s_logger.debug("Storage pool garbage collector is skipping templatePoolVO with ID: " + templatePoolVO.getId() + " because it is not completely downloaded.");
					continue;
				}
				
				if (!templatePoolVO.getMarkedForGC()) {
					templatePoolVO.setMarkedForGC(true);
					_vmTemplatePoolDao.update(templatePoolVO.getId(), templatePoolVO);
					s_logger.debug("Storage pool garbage collector has marked templatePoolVO with ID: " + templatePoolVO.getId() + " for garbage collection.");
					continue;
				}
				
				VMTemplateStoragePoolVO lock = null;
				try {
					lock = _vmTemplatePoolDao.acquire(templatePoolVO.getId());
					
					if (lock == null) {
						s_logger.debug("Storage pool garbage collector failed to acquire lock for templatePoolVO with ID: " + templatePoolVO.getId());
						continue;
					}
					
					_tmpltMgr.evictTemplateFromStoragePool(templatePoolVO);
				} finally {
					_vmTemplatePoolDao.release(templatePoolVO.getId());
				}
			}
		}
	}
	
	
}
