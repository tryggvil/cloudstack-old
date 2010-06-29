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
package com.cloud.storage;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.DeleteStoragePoolCommand;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.storage.CopyVolumeAnswer;
import com.cloud.agent.api.storage.CopyVolumeCommand;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.DeleteTemplateCommand;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.ManageVolumeAnswer;
import com.cloud.agent.api.storage.ManageVolumeCommand;
import com.cloud.agent.manager.allocator.StoragePoolAllocator;
import com.cloud.alert.AlertManager;
import com.cloud.api.BaseCmd;
import com.cloud.async.AsyncInstanceCreateStatus;
import com.cloud.async.AsyncJobExecutor;
import com.cloud.async.AsyncJobManager;
import com.cloud.async.AsyncJobVO;
import com.cloud.async.BaseAsyncJobExecutor;
import com.cloud.capacity.CapacityVO;
import com.cloud.capacity.dao.CapacityDao;
import com.cloud.configuration.Config;
import com.cloud.configuration.ResourceCount.ResourceType;
import com.cloud.configuration.dao.ConfigurationDao;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.event.EventTypes;
import com.cloud.event.EventVO;
import com.cloud.event.dao.EventDao;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.InternalErrorException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceInUseException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.host.Status;
import com.cloud.host.dao.DetailsDao;
import com.cloud.host.dao.HostDao;
import com.cloud.service.ServiceOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.storage.StoragePool.StoragePoolType;
import com.cloud.storage.Volume.MirrorState;
import com.cloud.storage.Volume.StorageResourceType;
import com.cloud.storage.Volume.VolumeType;
import com.cloud.storage.dao.DiskOfferingDao;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.StoragePoolDao;
import com.cloud.storage.dao.StoragePoolHostDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateHostDao;
import com.cloud.storage.dao.VMTemplatePoolDao;
import com.cloud.storage.dao.VmDiskDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.listener.StoragePoolMonitor;
import com.cloud.storage.snapshot.SnapshotManager;
import com.cloud.storage.snapshot.SnapshotScheduler;
import com.cloud.template.TemplateManager;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.NumbersUtil;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Adapters;
import com.cloud.utils.component.ComponentLocator;
import com.cloud.utils.component.Inject;
import com.cloud.utils.concurrency.NamedThreadFactory;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GlobalLock;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;
import com.cloud.vm.State;
import com.cloud.vm.UserVm;
import com.cloud.vm.UserVmManager;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.dao.UserVmDao;
import com.cloud.vm.dao.VMInstanceDao;

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
	@Inject protected StorageManager _storageMgr;
	@Inject protected VolumeDao _volsDao;
	@Inject protected HostDao _hostDao;
	@Inject protected DetailsDao _detailsDao;
	@Inject protected SnapshotDao _snapshotDao;
	protected Adapters<StoragePoolAllocator> _storagePoolAllocators;
    @Inject protected StoragePoolHostDao _storagePoolHostDao;
    @Inject protected AlertManager _alertMgr;
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
	@Inject protected DataCenterDao _dcDao = null;
	@Inject protected HostPodDao _podDao = null;
    
    ScheduledExecutorService _executor = null;
    boolean _storageCleanupEnabled;
    int _storageCleanupInterval;
    int _storagePoolAcquisitionWaitSeconds = 1800;		// 30 minutes
    protected int _retry = 2;

	protected int _overProvisioningFactor;


	@Override
	public boolean share(VMInstanceVO vm, List<VolumeVO> vols, HostVO host, boolean cancelPreviousShare) {
		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Asking for volumes of " + vm.toString() + " to be shared");
		}
		return true;
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
	
	@Override
	public Long findHostIdForStoragePool(StoragePoolVO pool) {
		List <StoragePoolHostVO> poolHosts = _poolHostDao.listByHostStatus(pool.getId(), Status.Up);
		
		if (poolHosts.size() == 0) {
			return null;
		} else {
			return poolHosts.get(0).getHostId();
		}
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
            for (VolumeVO v : answer.getVolumes()) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Saving root disk volume with folder: " + rootdiskFolder);
                }
                v.setFolder(rootdiskFolder);
                v.setDataCenterId(dc.getId());
                v.setPodId(pod.getId());
                v.setAccountId(accountId);
                v.setInstanceId(vmId);
                v.setMirrorState(mirrorState);
                v.setHostIp(selectedHosts[i].getStorageIpAddress()); //for convenience
                v.setTemplateName(template.getUniqueName());
                v.setTemplateId(template.getId());
                v = _volsDao.persist(v);
                final long vid = v.getId();
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
            VMTemplateStoragePoolVO templateStoragePoolVO = null;
            String tmpltinstallpath = null;
            if ((template != null) && !Storage.ImageFormat.ISO.equals(template.getFormat())) {
                List<VMTemplateStoragePoolVO> vths =
                    _vmTemplatePoolDao.listByTemplateStatus(template.getId(), VMTemplateHostVO.Status.DOWNLOADED, vo.getId());
                if( vths.isEmpty() ) {
                    s_logger.debug("template is not installed : " + template.getName());
                    continue;
                }
                templateStoragePoolVO = vths.get(0);
                tmpltinstallpath = templateStoragePoolVO.getLocalDownloadPath();
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

        for (VolumeVO v : answer.getVolumes()) {
        	if (s_logger.isDebugEnabled()) {
        		s_logger.debug("Saving volume: " + v.getPath());
        	}
        	VolumeType vType = v.getVolumeType();
        	
        	v.setPoolId(pool.getId());
        	v.setDataCenterId(dc.getId());
        	v.setPodId(pod.getId());
        	v.setAccountId(account.getId());
        	v.setDomainId(account.getDomainId().longValue());
        	v.setInstanceId(vm.getId());
        	v.setMirrorState(mirrorState);
        	
        	if (template != null && vType == VolumeType.ROOT) {
        	    if ((template.getFormat() != Storage.ImageFormat.ISO)) {
        	        // XXX: Why can't we set the unique name for ISOs
        	        v.setTemplateName(template.getUniqueName());
        	    }
        	    // This is needed for creating snapshots from root disks.
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
        		// System VM uses fake disk offering object which may not include offering id info
        		if(dataDiskOffering.getId() != null) {
	        		v.setDiskOfferingId(dataDiskOffering.getId());
	        		doId = dataDiskOffering.getId();
        		}
        	} else if (v.getVolumeType() == VolumeType.ROOT && rootDiskOffering != null) {
        		// System VM uses fake disk offering object which may not include offering id info
        		if(rootDiskOffering.getId() != null) {
	        		v.setDiskOfferingId(rootDiskOffering.getId());
	        		doId = rootDiskOffering.getId();
        		}
        	} else if(v.getVolumeType() == VolumeType.ROOT){
                templateId = template.getId();
            } else {
        		v.setDiskOfferingId(null);
        	}
        	
        	v = _volsDao.persist(v);
        	//Add event only for volumes of user Vm
        	if(vm instanceof UserVm){
        	    long volumeId = v.getId();
        	    // Create an event
        	    long sizeMB = v.getSize()/(1024*1024);
        	    String eventParams = "id=" + volumeId +"\ndoId="+doId+"\ntId="+templateId+"\ndcId="+dc.getId()+"\nsize="+sizeMB;
        	    EventVO event = new EventVO();
        	    event.setAccountId(account.getId());
        	    event.setUserId(1L);
        	    event.setType(EventTypes.EVENT_VOLUME_CREATE);
        	    event.setParameters(eventParams);
        	    event.setDescription("Created volume: "+ v.getName() +" with size: " + sizeMB + " MB in pool: " + pool.getName());
        	    _eventDao.persist(event);
        	}
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
       
        return true;
	}

    public void destroy(VMInstanceVO vm, List<VolumeVO> vols) {
        if (s_logger.isDebugEnabled() && vm != null) {
            s_logger.debug("Destroying volumes of " + vm.toString());
        }
        
        for(VolumeVO vol : vols ) {
        	_volsDao.destroyVolume(vol.getId());
        	
            // First delete the entries in the snapshot_policy and snapshot_schedule table for the volume.
            // They should not get executed after the volume is destroyed.
            _snapshotMgr.deletePoliciesForVolume(vol.getId());
            
            Long poolId = vol.getPoolId();            
            if (poolId != null) {            
            	Answer answer = null;
            	final DestroyCommand cmd = new DestroyCommand(vol.getFolder(), vol);            	            
            	boolean removed = false;
            	List<StoragePoolHostVO> poolhosts = _storagePoolHostDao.listByPoolId(poolId);            	
            	for(StoragePoolHostVO poolhost : poolhosts) {
            		answer = _agentMgr.easySend(poolhost.getHostId(), cmd);
            		if (answer != null && answer.getResult()) {        
            			removed = true;
            			break;
            		}
            	}
            	
            	if (removed) {
            		_volsDao.remove(vol.getId());            		                	
                } else {
                	StoragePoolVO pool = _storagePoolDao.findById(poolId);
                	_alertMgr.sendAlert(AlertManager.ALERT_TYPE_STORAGE_MISC, vol.getDataCenterId(), vol.getPodId(), "Storage cleanup required for storage pool: " + pool.getName(), "Volume folder: " + vol.getFolder() + ", volume path: " + vol.getPath());
                	s_logger.warn("destroy volume " + vol.getFolder() + " : " + vol.getPath() + " failed ");
                }
            } else {
            	_volsDao.remove(vol.getId());
            }                                                         
        }
        
    }
	
	@Override
	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
		_name = name;
		
		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		
        UserVmManager userVmMgr = locator.getManager(UserVmManager.class);
        if (userVmMgr == null) {
            throw new ConfigurationException("Unable to find " + UserVmManager.class);
        }

        ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
        if (configDao == null) {
            s_logger.error("Unable to get the configuration dao.");
            return false;
        }
        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
        	throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
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

        
        _retry = NumbersUtil.parseInt(configs.get(Config.StartRetry.key()), 2);
        
        _storagePoolAcquisitionWaitSeconds = NumbersUtil.parseInt(
        	configs.get("pool.acquisition.wait.seconds"), 1800);
        s_logger.info("pool.acquisition.wait.seconds is configured as " + _storagePoolAcquisitionWaitSeconds + " seconds");
        
        _agentMgr.registerForHostEvents(new StoragePoolMonitor(this, _hostDao, _storagePoolDao), true, false, true);
        
        String storageCleanupEnabled = configs.get("storage.cleanup.enabled");
        _storageCleanupEnabled = (storageCleanupEnabled == null) ? true : Boolean.parseBoolean(storageCleanupEnabled);
        
        String time = configs.get("storage.cleanup.interval");
        _storageCleanupInterval = NumbersUtil.parseInt(time, 86400);
        
        String workers = configs.get("expunge.workers");
        int wrks = NumbersUtil.parseInt(workers, 10);
        _executor = Executors.newScheduledThreadPool(wrks, new NamedThreadFactory("StorageManager-Scavenger"));
        
        boolean localStorage = Boolean.parseBoolean(configs.get(Config.UseLocalStorage.key()));
        if (localStorage) {
            _agentMgr.registerForHostEvents(ComponentLocator.inject(LocalStoragePoolListener.class), true, false, false);
        }

		return true;
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

		if (_storageCleanupEnabled) {
			_executor.scheduleWithFixedDelay(new StorageGarbageCollector(this), _storageCleanupInterval, _storageCleanupInterval, TimeUnit.SECONDS);
		} else {
			s_logger.debug("Storage cleanup is not enabled, so the storage cleanup thread is not being scheduled.");
		}
		
		return true;
	}

	@Override
	public boolean stop() {
		if (_storageCleanupEnabled) {
			_executor.shutdown();
		}
		
		return true;
	}

	protected StorageManagerImpl() {
	}

    public StoragePoolVO createPool(long zoneId, long podId, String poolName, URI uri) throws ResourceInUseException, IllegalArgumentException, UnknownHostException, ResourceAllocationException {
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

        List<StoragePoolVO> pools = _storagePoolDao.listPoolByHostPath(storageHost, hostPath);
        if (!pools.isEmpty()) {
        	Long oldPodId = pools.get(0).getPodId();
        	throw new ResourceInUseException("Storage pool " + uri + " already in use by another pod (id=" + oldPodId + ")", "StoragePool", uri.toASCIIString());
        }
        
        //iterate through all the hosts and ask them to mount the filesystem.
        //FIXME Not a very scalable implementation. Need an async listener, or perhaps do this on demand, or perhaps mount on a couple of hosts per pod
        List<HostVO> allHosts = _hostDao.listBy(Host.Type.Computing, podId, zoneId);
        allHosts.addAll(_hostDao.listBy(Host.Type.Routing, podId, zoneId));
        if(allHosts.isEmpty())
        {
            throw new ResourceAllocationException("No host exists to associate a storage pool with");
        }
        long poolId = _storagePoolDao.getNextInSequence(Long.class, "id");
        String uuid = UUID.nameUUIDFromBytes(new String(storageHost + hostPath).getBytes()).toString();
        StoragePoolVO pool = new StoragePoolVO(poolId, poolName, uuid, type, zoneId, podId, 0, 0, storageHost, port, hostPath);
        pool = _storagePoolDao.persist(pool);

        
        List<HostVO> poolHosts = new ArrayList<HostVO>();
        for (HostVO h: allHosts) {
            boolean success = addPoolToHost(h.getId(), pool);
            if( success ) {
                poolHosts.add(h);
            }
        }

        if (poolHosts.isEmpty()) {
            _storagePoolDao.delete(pool.getId());
            pool = null;
        } else {
            createCapacityEntry(pool);
        }
        return pool;
    }
    
    @DB
	public boolean deletePool(long id)
	{
		boolean deleteFlag = false;
		
		//get the pool to delete
		StoragePoolVO sPool = _storagePoolDao.findById(id);
		
		if(sPool==null)
			return false;
		
		//for the given pool id, find all records in the storage_pool_host_ref
		List<StoragePoolHostVO> hostPoolRecords = _storagePoolHostDao.listByPoolId(id);
		
		//if not records exist, delete the given pool (base case)
		if(hostPoolRecords.size()==0)
		{
			_storagePoolDao.delete(id);
			return true;
		}
		else
		{
			//1. Check if the pool has associated volumes in the volumes table
			//2. If it does, then you cannot delete the pool
			List<VolumeVO> volumeRecords = null;
			volumeRecords = _volsDao.findByPool(id);
			
			if(volumeRecords.size() != 0)
			{
				return false; //cannot delete as there are associated vols
			}
			//3. Else part, remove the SR associated with the Xenserver
			else
			{
				//First get the host_id from storage_pool_host_ref for given pool id
				StoragePoolVO lock = _storagePoolDao.acquire(sPool.getId());
				try
				{
					if (lock == null)
					{
						s_logger.debug("Failed to acquire lock when deleting StoragePool with ID: " + sPool.getId());
						return false;
					}
					
					for(StoragePoolHostVO host: hostPoolRecords)
					{
						DeleteStoragePoolCommand cmd = new DeleteStoragePoolCommand(sPool);
						final Answer answer = _agentMgr.easySend(host.getHostId(), cmd);
						
						if(answer != null)
						{
							if(answer.getResult() == true)
							{
								deleteFlag=true;
								break;
							}
						}
					}
					
				}
				finally
				{
					if (lock != null)
					{
						_storagePoolDao.release(lock.getId());
					}
				}
				
				if(deleteFlag)
				{
					//now delete the storage_pool_host_ref and storage_pool records
					for(StoragePoolHostVO host: hostPoolRecords)
					{
						_storagePoolHostDao.deletePrimaryRecordsForHost(host.getHostId());
					}
				
					_storagePoolDao.delete(id);
					return true;
				}
			}
		}
		return false;
		
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
			    String msg = "Add host failed due to ModifyStoragePoolCommand failed" + answer.getDetails();
                _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, pool.getDataCenterId(), pool.getPodId(), msg, msg);
                s_logger.warn(msg);
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
	
	public VolumeVO moveVolume(VolumeVO volume, StoragePoolVO destPool) throws InternalErrorException {
		String secondaryStorageURL = _storageMgr.getSecondaryStorageURL(volume.getDataCenterId());
		String secondaryStorageVolumePath = null;
		
		// Find hosts where the source and destination storage pools are visible
		StoragePoolVO srcPool = _storagePoolDao.findById(volume.getPoolId());
		Long sourceHostId = findHostIdForStoragePool(srcPool);
		Long destHostId = findHostIdForStoragePool(destPool);
		
		if (sourceHostId == null) {
			throw new InternalErrorException("Failed to find a host where the source storage pool is visible.");
		} else if (destHostId == null) {
			throw new InternalErrorException("Failed to find a host where the dest storage pool is visible.");
		}
		
		// Copy the volume from the source storage pool to secondary storage
		CopyVolumeCommand cvCmd = new CopyVolumeCommand(volume.getId(), volume.getPath(), srcPool, secondaryStorageURL, true);
		CopyVolumeAnswer cvAnswer = (CopyVolumeAnswer) _agentMgr.easySend(sourceHostId, cvCmd);
		
		if (cvAnswer == null || !cvAnswer.getResult()) {
			throw new InternalErrorException("Failed to copy the volume from the source primary storage pool to secondary storage.");
		}
		
		secondaryStorageVolumePath = cvAnswer.getVolumePath();
		
		// Copy the volume from secondary storage to the destination storage pool
		cvCmd = new CopyVolumeCommand(volume.getId(), secondaryStorageVolumePath, destPool, secondaryStorageURL, false);
		cvAnswer = (CopyVolumeAnswer) _agentMgr.easySend(destHostId, cvCmd);
		
		if (cvAnswer == null || !cvAnswer.getResult()) {
			throw new InternalErrorException("Failed to copy the volume from secondary storage to the destination primary storage pool.");
		}
		
		String destPrimaryStorageVolumePath = cvAnswer.getVolumePath();
		String destPrimaryStorageVolumeFolder = cvAnswer.getVolumeFolder();
		
		// Delete the volume on the source storage pool
		ManageVolumeCommand mvCmd = new ManageVolumeCommand(false, volume.getSize(), volume.getFolder(), volume.getPath(), volume.getName(), volume.getNameLabel(), srcPool);
		ManageVolumeAnswer mvAnswer = (ManageVolumeAnswer) _agentMgr.easySend(sourceHostId, mvCmd);
		
		if (mvAnswer == null || !mvAnswer.getResult()) {
			throw new InternalErrorException("Failed to delete the volume from the source primary storage pool.");
		}
		
		volume.setPath(destPrimaryStorageVolumePath);
		volume.setFolder(destPrimaryStorageVolumeFolder);
		volume.setPodId(destPool.getPodId());
		volume.setPoolId(destPool.getId());
		_volsDao.update(volume.getId(), volume);
		
		return _volsDao.findById(volume.getId());
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
		VolumeVO volume = new VolumeVO(null, userSpecifiedName, -1, -1, -1, -1, new Long(-1), null, null, 0, Volume.VolumeType.DATADISK);
		volume.setPoolId(null);
    	volume.setDataCenterId(dc.getId());
    	volume.setPodId(null);
    	volume.setAccountId(accountId);
    	volume.setDomainId(account.getDomainId().longValue());
    	volume.setMirrorState(MirrorState.NOT_MIRRORED);
    	volume.setDiskOfferingId(diskOffering.getId());
    	volume.setStorageResourceType(StorageResourceType.STORAGE_POOL);
    	volume.setInstanceId(null);
    	volume.setNameLabel("detached");
    	volume.setUpdated(new Date());
    	volume.setStatus(AsyncInstanceCreateStatus.Creating);
    	volume = _volsDao.persist(volume);
    	volumeId = volume.getId();
		
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
                String eventParams = "id=" + volumeId +"\ndoId="+diskOffering.getId()+"\ntId="+templateId+"\ndcId="+dc.getId()+"\nsize="+sizeMB;
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

	

	@Override @DB
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
    		Transaction txn = Transaction.currentTxn();
    		txn.start();
    		Long volumeId = volume.getId();
    		
    		// Delete the recurring snapshot policies for this volume.
    		_snapshotMgr.deletePoliciesForVolume(volumeId);
    		
    		// Mark the volume as destroyed
    		_volsDao.destroyVolume(volumeId);
    		
    		// Mark the volume as removed
    		_volsDao.remove(volumeId);
    		
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
            txn.commit();
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

	@Override
    public Answer sendToStorageHostsOnPool(Long poolId, Command cmd, String basicErrMsg) {
	    return sendToStorageHostsOnPool(poolId, cmd, basicErrMsg, 1, 0);
	}
	
	@Override
	public Answer sendToStorageHostsOnPool(Long poolId, Command cmd, String basicErrMsg, int totalRetries, int pauseBeforeRetry) {
        Answer answer = null;
        Long hostId = null;
        StoragePoolVO storagePool = _storagePoolDao.findById(poolId);
        List<Long> hostsToAvoid = new ArrayList<Long>();
        StoragePoolHostVO storagePoolHost;
        int tryCount = 0;
        while ((storagePoolHost = _storageMgr.chooseHostForStoragePool(storagePool, hostsToAvoid)) != null && tryCount++ < totalRetries) {
            try {
                hostId = storagePoolHost.getHostId();
                s_logger.debug("Trying to execute Command: " + cmd + " on host: " + hostId + " try: " + tryCount);
                answer = _agentMgr.send(hostId, cmd);
                if (answer != null && answer.getResult()) {
                    return answer;
                } else {
                    s_logger.warn(basicErrMsg + " on host: " + hostId + " try: " + tryCount + ", reason: " + ((answer != null) ? answer.getDetails() : "null"));
                    Thread.sleep(pauseBeforeRetry * 1000);
                }
            } catch (AgentUnavailableException e1) {
                s_logger.warn(basicErrMsg + "on host " + hostId + " try: " + tryCount + ", reason: " + e1);
            } catch (OperationTimedoutException e1) {
                s_logger.warn(basicErrMsg + "on host " + hostId + " try: " + tryCount + ", reason: " + e1);
            } catch (InterruptedException e) {
                s_logger.warn(basicErrMsg + "on host " + hostId + " try: " + tryCount + ", reason: " + e);
            }
        }
        
        s_logger.error(basicErrMsg + ", no hosts available to execute command: " + cmd);
        return answer;
    }

	protected class StorageGarbageCollector implements Runnable {
    	StorageManagerImpl _storageMgr;
    	public StorageGarbageCollector(StorageManagerImpl storageMgr) {
    		_storageMgr = storageMgr;
    	}
    	
    	public void run() {
    		try {
    			s_logger.info("Storage Garbage Collection Thread is running.");

    			GlobalLock scanLock = GlobalLock.getInternLock(this.getClass().getName());
    			try {
    				if(scanLock.lock(3)) {
    					try {
    		    			_storageMgr.cleanupStorage(true);
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

	public void cleanupStorage(boolean recurring) {

		// Cleanup primary storage pools
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
				
				_tmpltMgr.evictTemplateFromStoragePool(templatePoolVO);
			}
		}
		
		// Cleanup secondary storage hosts
		List<HostVO> secondaryStorageHosts = _hostDao.listSecondaryStorageHosts();
		for (HostVO secondaryStorageHost : secondaryStorageHosts) {
			long hostId = secondaryStorageHost.getId();
			List<VMTemplateHostVO> destroyedTemplateHostVOs = _vmTemplateHostDao.listDestroyed(hostId);
			s_logger.debug("Secondary storage garbage collector found " + destroyedTemplateHostVOs.size() + " templates to cleanup on secondary storage host: " + secondaryStorageHost.getName());
			for (VMTemplateHostVO destroyedTemplateHostVO : destroyedTemplateHostVOs) {
				if (!_tmpltMgr.templateIsDeleteable(destroyedTemplateHostVO)) {
					s_logger.debug("Not deleting template at: " + destroyedTemplateHostVO.getInstallPath());
					continue;
				}
				
				String installPath = destroyedTemplateHostVO.getInstallPath();
				
				if (installPath != null) {
					Answer answer = _agentMgr.easySend(hostId, new DeleteTemplateCommand(destroyedTemplateHostVO.getInstallPath()));
					
					if (answer == null || !answer.getResult()) {
						s_logger.debug("Failed to delete template at: " + destroyedTemplateHostVO.getInstallPath());
					} else {
						_vmTemplateHostDao.remove(destroyedTemplateHostVO.getId());
						s_logger.debug("Deleted template at: " + destroyedTemplateHostVO.getInstallPath());
					}
				} else {
					_vmTemplateHostDao.remove(destroyedTemplateHostVO.getId());
				}
			}
		}
		
	}

	
	
}
