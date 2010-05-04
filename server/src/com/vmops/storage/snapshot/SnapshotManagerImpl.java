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

package com.vmops.storage.snapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.agent.AgentManager;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.BackupSnapshotAnswer;
import com.vmops.agent.api.BackupSnapshotCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.CreateVolumeFromSnapshotAnswer;
import com.vmops.agent.api.CreateVolumeFromSnapshotCommand;
import com.vmops.agent.api.DeleteSnapshotBackupCommand;
import com.vmops.agent.api.ManageSnapshotAnswer;
import com.vmops.agent.api.ManageSnapshotCommand;
import com.vmops.api.BaseCmd;
import com.vmops.api.commands.CreateSnapshotCmd;
import com.vmops.api.commands.CreateVolumeFromSnapshotCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.async.executor.SnapshotOperationParam;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.host.dao.DetailsDao;
import com.vmops.host.dao.HostDao;
import com.vmops.serializer.GsonHelper;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotPolicyRefVO;
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.SnapshotScheduleVO;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateStoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.MirrorState;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.storage.dao.SnapshotDao;
import com.vmops.storage.dao.SnapshotPolicyDao;
import com.vmops.storage.dao.SnapshotPolicyRefDao;
import com.vmops.storage.dao.SnapshotScheduleDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplatePoolDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.Account;
import com.vmops.user.AccountManager;
import com.vmops.user.AccountVO;
import com.vmops.user.UserContext;
import com.vmops.user.UserVO;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.utils.DateUtil;
import com.vmops.utils.DateUtil.IntervalType;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.dao.UserVmDao;

@Local(value={SnapshotManager.class})
public class SnapshotManagerImpl implements SnapshotManager {
    private static final Logger s_logger = Logger.getLogger(SnapshotManagerImpl.class);

    HostDao _hostDao = null;
    UserVmDao _vmDao = null;
    VolumeDao _volsDao = null;
    AccountDao _accountDao = null;
    UserDao _userDao = null;
    SnapshotDao _snapshotDao = null;
    StoragePoolDao _storagePoolDao;
    EventDao _eventDao = null;
    SnapshotPolicyDao _snapshotPolicyDao =  null;
    SnapshotPolicyRefDao _snapPolicyRefDao =  null;
    SnapshotScheduleDao _snapshotScheduleDao = null;
    DetailsDao _detailsDao = null;
    VMTemplateDao _templatesDao = null;
    VMTemplatePoolDao _vmTemplatePoolDao = null;
    
    StorageManager _storageMgr = null;
    AgentManager _agentMgr = null;
    SnapshotScheduler _snapSchedMgr = null;
    AsyncJobManager _asyncMgr = null;
    AccountManager _accountMgr = null;
    String _name;

    protected SearchBuilder<SnapshotVO> PolicySnapshotSearch;
    protected SearchBuilder<SnapshotPolicyVO> PoliciesForSnapSearch;

    @Override @DB
    public long createSnapshotAsync(long userId, long volumeId, List<Long> policies) throws InvalidParameterValueException, ResourceAllocationException {
        
        boolean runSnap = true;
        VolumeVO volume = _volsDao.findById(volumeId);
        if (_storageMgr.volumeInactive(volume) || volume.getNameLabel().equals("detached")) {
            // For manual policies, we should still take a snapshot, but only for that policy.
            if (!policies.contains(MANUAL_POLICY_ID)) {
                s_logger.info("Snapshot creation skipped for the moment. Volume is attached to a non-running VM or detached.");
                runSnap = false;
            }
            else {
                // Take a snapshot, but only for the manual policy
                policies = new ArrayList<Long>();
                policies.add(MANUAL_POLICY_ID);
            }

        }
        else {
            for (Long policyId : policies) {
                // If it's a manual policy then it won't be there in the volume_snap_policy_ref table.
                // We need to run the snapshot
                if(policyId == MANUAL_POLICY_ID) {
                    // Check if the resource limit for snapshots has been exceeded
                    UserVO user = _userDao.findById(userId);
                    AccountVO account = _accountDao.findById(user.getId());
                    if (_accountMgr.resourceLimitExceeded(account, ResourceType.snapshot)) {
                        throw new ResourceAllocationException("The maximum number of snapshots for account " + account.getAccountName() + " has been exceeded.");
                    }
                }
                else {
                    // Does volume have this policy assigned still
                    SnapshotPolicyVO volPolicy =  _snapshotPolicyDao.findById(policyId);
                    if(volPolicy == null) {
                        // The policy has been removed for the volume. Don't run the snapshot for this policy
                        s_logger.debug("Policy " + policyId + " has been removed for the volume " + volumeId + ". Not running snapshot for this policy");
                        policies.remove(policyId);
                    }
                }
            }
            if (policies.size() == 0) {
                // There are no valid policies left for the snapshot. Don't execute it.
                runSnap = false;
            }
        }
        
        if(!runSnap) {
            s_logger.warn("Snapshot for volume " + volumeId + " not created. No policy assigned currently.");
            // XXX: What the hell do we return.
            return -1;
        }
        
        SnapshotOperationParam param = new SnapshotOperationParam(userId, volumeId, policies);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(userId);
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CreateSnapshot");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateSnapshotCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);

    }
    
    @Override @DB
    public SnapshotVO createSnapshot(long userId, long volumeId, List<Long> policyIds) {
        SnapshotVO createdSnapshot = null;
        VolumeVO volume = _volsDao.findById(volumeId);
        Long instanceId = volume.getInstanceId();
        VMInstanceVO vm = null;
        
        if (instanceId != null) {
            vm = _vmDao.findById(instanceId);
        }
        
        String volumeNameLabel  = _storageMgr.getVolumeNameLabel(volume, vm);
        
        Long id = null;
        
        // Determine the name for this snapshot
        String timeString = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, new Date(), DateUtil.YYYYMMDD_FORMAT);
        String snapshotName = volume.getName() + "_" + timeString;
        
        // Create the Snapshot object and save it so we can return it to the user
        id = _snapshotDao.persist(new SnapshotVO(volume.getAccountId(), volume.getHostId(), volume.getPoolId(), volume.getId(), null, snapshotName, Snapshot.TYPE_USER, Snapshot.TYPE_DESCRIPTIONS[Snapshot.TYPE_USER]));

        // Send a ManageSnapshotCommand to the agent
        ManageSnapshotCommand cmd = new ManageSnapshotCommand(ManageSnapshotCommand.CREATE_SNAPSHOT, id, volume.getFolder(), volume.getPath(), volumeNameLabel, snapshotName, policyIds);
        ManageSnapshotAnswer answer = null;
        try {
            Long hostId = _storageMgr.chooseHostForVolume(volume);
            if (hostId != null) {
                answer = (ManageSnapshotAnswer) _agentMgr.send(hostId, cmd);
            } else {
                s_logger.warn("Failed to create snapshot for volume: " + volume.getId() + ", no hosts available to make snapshot");
            }
        } catch (AgentUnavailableException e1) {
            s_logger.warn("Failed to create snapshot for volume: " + volume.getId() + ", reason: " + e1);
        } catch (OperationTimedoutException e1) {
            s_logger.warn("Failed to create snapshot for volume: " + volume.getId(), e1);
        }
        
        // Create an event
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(volume.getAccountId());
        event.setType(EventTypes.EVENT_SNAPSHOT_CREATE);
        
        // Update the snapshot in the database
        if ((answer != null) && answer.getResult()) {
            // The snapshot was successfully created
            
            long snapshotIdCreated = answer.getSnapshotId();
            if(id.longValue() != snapshotIdCreated) {
                s_logger.warn("Incorrect return value in ManageSnapshotAnswer command. Expected " + id + " returned " + snapshotIdCreated);
            }
            
            Transaction txn = Transaction.currentTxn();
            txn.start();
            createdSnapshot = _snapshotDao.findById(id);
            createdSnapshot.setPath(answer.getSnapshotPath());
            createdSnapshot.setPrevSnapshotId(_snapshotDao.getLastSnapshot(volumeId, id));            
            // We cannot say that it's created until it's been backed up
            _snapshotDao.update(id, createdSnapshot);
            txn.commit();
            String eventParams = "id=" + volumeId + "\nssName=" + snapshotName+"\nsize=" + volume.getSize()+"\ndcId=" + volume.getDataCenterId();
            event.setDescription("Created snapshot " + snapshotName + " for volume " + volumeId);
            event.setParameters(eventParams);
            
            
        } else {
            // The snapshot was not successfully created
            Transaction txn = Transaction.currentTxn();
            txn.start();
            createdSnapshot = _snapshotDao.findById(id);
            createdSnapshot.setStatus(AsyncInstanceCreateStatus.Corrupted);
            _snapshotDao.update(id, createdSnapshot);
            // mark it as removed in the snapshots table
            _snapshotDao.remove(id);
            txn.commit();
            
            event.setLevel(EventVO.LEVEL_ERROR);
            event.setDescription("Failed to create snapshot " + snapshotName + " for volume " + volumeId);
        }

        // Save the event
        _eventDao.persist(event);

        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if(asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if(s_logger.isDebugEnabled())
                s_logger.debug("CreateSnapshot created a new instance " + id + ", update async job-" + job.getId() + " progress status");

            _asyncMgr.updateAsyncJobAttachment(job.getId(), "snapshot", id);
            _asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, id);
        }

        return createdSnapshot;
    }

    @Override
    @DB
    public boolean backupSnapshotToSecondaryStorage(long userId, SnapshotVO snapshot) {
        long volumeId = snapshot.getVolumeId();
        VolumeVO volume = _volsDao.findById(volumeId);
        long primaryStoragePoolId = volume.getPoolId();
        StoragePoolVO primaryStoragePool = _storagePoolDao.findById(primaryStoragePoolId);
        if (primaryStoragePool == null) {
            String msg = "Serious error. Storage pool of volume " + volumeId + "is null?";
            s_logger.error(msg);
            throw new VmopsRuntimeException(msg);
        }
        String primaryStoragePoolUuid = primaryStoragePool.getUuid();
        
        String snapshotUuid = snapshot.getPath();
        Long dcId = volume.getDataCenterId();
        Long accountId = volume.getAccountId();
        
        String secondaryStoragePoolUrl = _storageMgr.getSecondaryStorageURL(volume.getDataCenterId());
        
        String prevSnapshotUuid = null;
        String lastBackedUpSnapshotUuid = null;
        SnapshotVO prevSnapshot = null;
        
        boolean isFirstSnapshotOfRootVolume = false;
        long prevSnapshotId = snapshot.getPrevSnapshotId();
        if (prevSnapshotId > 0) {
            prevSnapshot = _snapshotDao.findById(prevSnapshotId);
            prevSnapshotUuid = prevSnapshot.getPath();
            lastBackedUpSnapshotUuid = prevSnapshot.getBackupSnapshotId();
        }
        else {
            // This is the first snapshot of the volume.
            if (volume.getVolumeType() == VolumeType.ROOT) {
                isFirstSnapshotOfRootVolume = true;
            }
        }
        
        BackupSnapshotCommand backupSnapshotCommand = 
            new BackupSnapshotCommand(primaryStoragePoolUuid, 
                                      snapshotUuid, 
                                      volume.getName(),
                                      secondaryStoragePoolUrl, 
                                      lastBackedUpSnapshotUuid, 
                                      prevSnapshotUuid,
                                      isFirstSnapshotOfRootVolume);
        BackupSnapshotAnswer answer = null;
        String backedUpSnapshotUuid = null;
        // By default, assume failed.
        AsyncInstanceCreateStatus status = AsyncInstanceCreateStatus.Corrupted;
        boolean backedUp = false;
        try {
            Long hostId = _storageMgr.chooseHostForVolume(volume);
            if (hostId != null) {
                answer = (BackupSnapshotAnswer) _agentMgr.send(hostId, backupSnapshotCommand);
                backedUpSnapshotUuid = answer.getBackupSnapshotName();
                if (answer.getResult() && backedUpSnapshotUuid != null) {
                    status = AsyncInstanceCreateStatus.Created;
                    backedUp = true;
                    
                    // Destroying the previous snapshot succeeded too. 
                    if (prevSnapshot != null) {
                        // Mark the previous snapshot as removed from the database.
                        // removed column is used when the snapshot is removed from the secondary storage.
                        // Either we need another column, or don't do anything.
                        /**
                        Transaction txn = Transaction.currentTxn();
                        txn.start();
                        // Don't know if this will just mark it as removed
                        _snapshotDao.remove(prevSnapshot.getId());
                        txn.commit();
                        */
                    }
                    
                }
                else {
                    String failureDetails = answer.getDetails();
                    s_logger.warn("Failed to backup snapshot to secondary storage for " + snapshot.getName() + " for volume: " + volume.getId() + ", " + failureDetails); 
                }
            } else {
                s_logger.warn("Failed to backup snapshot to secondary storage for " + snapshot.getName() + " for volume: " + volume.getId() + ", no hosts available to make snapshot");
            }
        } catch (AgentUnavailableException e1) {
            s_logger.warn("Failed to backup snapshot to secondary storage for " + snapshot.getName() + " for volume: " + volumeId + ", reason: " + e1);
        } catch (OperationTimedoutException e1) {
            s_logger.warn("Failed to backup snapshot to secondary storage for " + snapshot.getName() + " for volume: " + volumeId, e1);
        } catch(Exception e) {
            s_logger.error("Unhandled exception while backup snapshot " + snapshot.getName() + " to secondary storage for volume: " + volumeId, e);
        }

        // Update the status in all cases.
        Transaction txn = Transaction.currentTxn();
        txn.start();
        long id = snapshot.getId();
        SnapshotVO snapshotVO = _snapshotDao.findById(id);
        snapshotVO.setBackupSnapshotId(backedUpSnapshotUuid);
        snapshotVO.setStatus(status);
        _snapshotDao.update(id, snapshotVO);
        if (!backedUp) {
            // This snapshot has been destroyed on primary.
            // It can't be recovered.
            // mark this as removed in the database
            _snapshotDao.remove(id);
        }
        txn.commit();
        
        return backedUp;
    }

    @Override
    @DB
    public void postCreateSnapshot(long userId, long volumeId, long snapshotId, List<Long> policyIds, boolean backedUp) {
        // Update the snapshot_policy_ref table with the created snapshot
        // Get the list of policies for this snapshot
        for (long policyId : policyIds) {
            if (backedUp) {
                // create an entry in snap_policy_ref table
                SnapshotPolicyRefVO snapPolicyRef = new SnapshotPolicyRefVO(snapshotId, volumeId, policyId);
                
                Transaction txn = Transaction.currentTxn();
                txn.start();
                _snapPolicyRefDao.persist(snapPolicyRef);
                txn.commit();
            
            
            	// This is a manual create, so increment the count of snapshots for this account
                if (policyId == MANUAL_POLICY_ID) {
                	Snapshot snapshot = _snapshotDao.findById(snapshotId);
                	_accountMgr.incrementResourceCount(snapshot.getAccountId(), ResourceType.snapshot);
                }
            }
            
            // Even if the current snapshot failed, we should schedule the next recurring snapshot for this policy.
            if (policyId != MANUAL_POLICY_ID) {
                postCreateRecurringSnapshotForPolicy(userId, volumeId, snapshotId, policyId);
            }
        }
    }
    
    private void postCreateRecurringSnapshotForPolicy(long userId, long volumeId, long snapshotId, long policyId) {
        //Use count query
    	Filter searchFilter = new Filter(SnapshotVO.class, GenericDaoBase.CREATED_COLUMN, true, null, null);
        List<SnapshotVO> snaps = listSnapsforPolicy(policyId, searchFilter);
        SnapshotPolicyVO policy = _snapshotPolicyDao.findById(policyId);
        
        if(snaps.size() > policy.getMaxSnaps() && snaps.size() > 1) {
            //Delete the oldest snap ref in snap_policy_ref
            SnapshotVO oldestSnapshot = snaps.get(0);
            long oldSnapId = oldestSnapshot.getId();
            
            // Excess snapshot. delete it asynchronously
           long jobId = deleteSnapshot(userId, oldSnapId, policyId);
           // What do we do with jobId
           
        }
        // Schedule the next job if it's recurring
        
        Date nextScheduledTime = _snapSchedMgr.scheduleNextSnapshotJob(policy);
        // What do we do with the nextScheduledTime
    }

    @Override @DB
    public long deleteSnapshot(long userId, long snapshotId, long policyId) {
        long jobId = -1;
        long prevSnapshotId = 0;
        SnapshotVO nextSnapshot = null;
        List<SnapshotPolicyRefVO> snapPolicyRefs = _snapPolicyRefDao.listBySnapshotId(snapshotId);
        // Destroy snapshot if its not part of any policy other than the given one.
        if(snapPolicyRefs.size() == 1 && (snapPolicyRefs.get(0).getPolicyId() == policyId)) {
            SnapshotVO currentSnapshot = _snapshotDao.findById(snapshotId);
            String backupOfSnapshot = currentSnapshot.getBackupSnapshotId();
            nextSnapshot = _snapshotDao.findNextSnapshot(snapshotId);
            String backupOfNextSnapshot = null;
            if (nextSnapshot != null) {
                backupOfNextSnapshot = nextSnapshot.getBackupSnapshotId();
            }
             
            prevSnapshotId = currentSnapshot.getPrevSnapshotId();
            String backupOfPreviousSnapshot = null;
            if (prevSnapshotId > 0) {
                SnapshotVO prevSnapshot = _snapshotDao.findById(prevSnapshotId);
                backupOfPreviousSnapshot = prevSnapshot.getBackupSnapshotId();
            }
            
            if (backupOfSnapshot != null) {
                if (backupOfNextSnapshot != null && backupOfSnapshot.equals(backupOfNextSnapshot)) {
                    // Both the snapshots point to the same backup VHD file.
                    // There is no difference in the data between them.
                    // We don't want to delete the backup of the older snapshot
                    // as it means that we delete the next snapshot too

                    // XXX: So what the hell do we return?
                    jobId = -1;
                }
                else if (backupOfPreviousSnapshot != null && backupOfSnapshot.equals(backupOfPreviousSnapshot)) {
                    // If we delete the current snapshot, the user will not 
                    // be able to recover from the previous snapshot
                    // So don't delete anything
                    
                    // XXX: So what the hell do we return?
                    jobId = -1;
                }
                else {
                    jobId = destroySnapshotAsync(userId, currentSnapshot.getVolumeId(), snapshotId, policyId);
                }
                
                if (jobId == -1) {
                    // Don't actually delete the snapshot backup but delete the entry
                    // from both snapshots and snapshot_policy_ref table
                    postDeleteSnapshot(snapshotId, policyId);
                }
            }
        }
        else {
            // Just delete the entry from the snapshot_policy_ref table
            Transaction txn = Transaction.currentTxn();
            txn.start();
            _snapPolicyRefDao.removeSnapPolicy(snapshotId, policyId);
            txn.commit();
        }
        
        return jobId;

    }

    @Override @DB
    public long destroySnapshotAsync(long userId, long volumeId, long snapshotId, long policyId) {
        
        SnapshotOperationParam param = new SnapshotOperationParam(userId, volumeId, snapshotId, policyId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteSnapshot");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateSnapshotCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);

    }

    @Override @DB
    public boolean destroySnapshot(long userId, long snapshotId, long policyId) {
        boolean success = false;
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
        if (snapshot != null) {
            VolumeVO volume = _volsDao.findById(snapshot.getVolumeId());
            String volumeName = volume.getName();
            
            String secondaryStoragePoolUrl = _storageMgr.getSecondaryStorageURL(volume.getDataCenterId());
            
            String backupOfSnapshot = snapshot.getBackupSnapshotId();
            SnapshotVO nextSnapshot = _snapshotDao.findNextSnapshot(snapshotId);
            String backupOfNextSnapshot = null;
            if (nextSnapshot != null) {
                backupOfNextSnapshot = nextSnapshot.getBackupSnapshotId();
            }
            
            DeleteSnapshotBackupCommand cmd = new DeleteSnapshotBackupCommand(volumeName, secondaryStoragePoolUrl, backupOfSnapshot, backupOfNextSnapshot);
            Answer answer = sendToStorageHostOrPool(snapshot, cmd);

            if ((answer != null) && answer.getResult()) {
                postDeleteSnapshot(snapshotId, policyId);
                success = true;
            }
        }

        // create the event
        String eventParams = "id=" + snapshotId;
        EventVO event = new EventVO();
        
        event.setUserId(userId);
        event.setAccountId((snapshot != null) ? snapshot.getAccountId() : 0);
        event.setType(EventTypes.EVENT_SNAPSHOT_DELETE);
        event.setDescription("Deleted snapshot " + snapshotId);
        event.setParameters(eventParams);
        event.setLevel(success ? EventVO.LEVEL_INFO : EventVO.LEVEL_ERROR);
        _eventDao.persist(event);

        return success;

    }

    @DB
    protected void postDeleteSnapshot(long snapshotId, long policyId) {
        // Remove the snapshot from the snapshots table and the snap_policy_ref table.
        // In the snapshots table, 
        // the last_snapshot_id field of the next snapshot becomes the last_snapshot_id of the deleted snapshot
        Transaction txn = null;
        try {
            txn = Transaction.currentTxn();
            txn.start();
            _snapshotDao.remove(snapshotId);
            SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
            long prevSnapshotId = snapshot.getPrevSnapshotId();
            SnapshotVO nextSnapshot = _snapshotDao.findNextSnapshot(snapshotId);
            if (nextSnapshot != null) {
                nextSnapshot.setPrevSnapshotId(prevSnapshotId);
                _snapshotDao.update(nextSnapshot.getId(), nextSnapshot);
            }
            _snapPolicyRefDao.removeSnapPolicy(snapshotId, policyId);
            
            // If this is a manual delete, decrement the count of snapshots for this account
            if (policyId == 0) {
            	_accountMgr.decrementResourceCount(snapshot.getAccountId(), ResourceType.snapshot);
            }
            
            txn.commit();
        }
        catch (Exception e) {
            txn.rollback();
            String errMsg = "Database error while trying to Remove the snapshot from the snapshots table and the snap_policy_ref table. " + e.getMessage();
            s_logger.error(errMsg, e);
            throw new VmopsRuntimeException(errMsg);
        }
        finally {
            txn.close();
        }
    }
    
    protected String createVDIFromSnapshot(long userId, SnapshotVO snapshot, String templateUuid) {
        String vdiUUID = null;
        
        long volumeId = snapshot.getVolumeId();
        VolumeVO volume = _volsDao.findById(volumeId);
        long primaryStoragePoolId = volume.getPoolId();
        StoragePoolVO primaryStoragePool = _storagePoolDao.findById(primaryStoragePoolId);
        if (primaryStoragePool == null) {
            String msg = "Serious error. Storage pool of volume " + volumeId + "is null?";
            s_logger.error(msg);
            throw new VmopsRuntimeException(msg);
        }
        String primaryStoragePoolUuid = primaryStoragePool.getUuid();
        
        String volumeName = volume.getName();

        String secondaryStoragePoolUrl = _storageMgr.getSecondaryStorageURL(volume.getDataCenterId());
        
        String backedUpSnapshotUuid = snapshot.getBackupSnapshotId();
        
        CreateVolumeFromSnapshotCommand createVolumeFromSnapshotCommand =
            new CreateVolumeFromSnapshotCommand(primaryStoragePoolUuid, volumeName, secondaryStoragePoolUrl, backedUpSnapshotUuid, templateUuid);
        CreateVolumeFromSnapshotAnswer answer = null;
        try {
            Long hostId = _storageMgr.chooseHostForVolume(volume);
            if (hostId != null) {
                answer = (CreateVolumeFromSnapshotAnswer) _agentMgr.send(hostId, createVolumeFromSnapshotCommand);
                vdiUUID = answer.getVdi();
                if (answer.getResult() && vdiUUID != null) {
                    s_logger.info("Successfully created volume from " + snapshot.getName() + " for volume: " + volume.getId());
                }
                else {
                    String failureDetails = answer.getDetails();
                    s_logger.warn("Failed to create volume from " + snapshot.getName() + " for volume: " + volume.getId() + ", " + failureDetails); 
                }
            } else {
                s_logger.warn("Failed to create volume from " + snapshot.getName() + " for volume: " + volume.getId() + ", no hosts available to make snapshot");
            }
        } catch (AgentUnavailableException e1) {
            s_logger.warn("Failed to create volume from " + snapshot.getName() + " for volume: " + volumeId + ", reason: " + e1);
        } catch (OperationTimedoutException e1) {
            s_logger.warn("Failed to create volume from " + snapshot.getName() + " for volume: " + volumeId, e1);
        } catch(Exception e) {
            s_logger.error("Unhandled exception while saving snapshot " + snapshot.getName() + " for volume: " + volumeId, e);
        }

        return vdiUUID;
    }

    @Override
    @DB
    public VolumeVO createVolumeFromSnapshot(long accountId, long userId, long snapshotId, String volumeName) {
        VolumeVO createdVolume = null;
        Long volumeId = null;
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
        VolumeVO originalVolume = _volsDao.findById(snapshot.getVolumeId());
        String templateUuid = null;
        if(originalVolume.getVolumeType().equals(Volume.VolumeType.ROOT)){
            if(originalVolume.getTemplateId() == null){
                s_logger.error("Invalid Template Id. Cannot create volume from snapshot of root disk.");
                return null;
            }
            VMTemplateVO template = _templatesDao.findById(originalVolume.getTemplateId());
            if(template == null){
                s_logger.error("Unable find template with name: "+originalVolume.getTemplateName()+" to create volume from root disk");
                return null;
            }
            VMTemplateStoragePoolVO templatePool = _vmTemplatePoolDao.findByPoolTemplate(originalVolume.getPoolId(), template.getId());
            templateUuid = templatePool.getInstallPath();
        }

        
        // The size and folder of the new volume is the same as that of the old volume from which the snapshot was created. 
        long volumeSize = originalVolume.getSize();
        String volumeFolder = originalVolume.getFolder();
        // Get the newly created VDI from the snapshot. 
        // This will return a null volumePath if it could not be created  
        String volumePath = createVDIFromSnapshot(userId, snapshot, templateUuid);
        
        // Create the Volume object and save it so that we can return it to the user
        Account account = _accountDao.findById(accountId);
        VolumeVO volume = new VolumeVO(null, volumeName, -1, -1, -1, -1, -1, new Long(-1), null, null, 0, Volume.VolumeType.DATADISK);
        volume.setPoolId(originalVolume.getPoolId());
        volume.setHostId(originalVolume.getHostId());
        volume.setDataCenterId(originalVolume.getDataCenterId());
        volume.setPodId(originalVolume.getPodId());
        volume.setAccountId(accountId);
        volume.setDomainId(account.getDomainId().longValue());
        volume.setMirrorState(MirrorState.NOT_MIRRORED);
        volume.setDiskOfferingId(originalVolume.getDiskOfferingId());
        volume.setTemplateId(originalVolume.getTemplateId());
        volume.setStorageResourceType(StorageResourceType.STORAGE_POOL);
        volume.setInstanceId(null);
        volume.setNameLabel("detached");
        volume.setStatus(AsyncInstanceCreateStatus.Creating);
        volumeId = _volsDao.persist(volume);

        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if(asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if(s_logger.isInfoEnabled())
                s_logger.info("CreateVolumeFromSnapshot created a new instance " + volumeId + ", update async job-" + job.getId() + " progress status");
            
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "volume", volumeId);
            _asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, volumeId);
        }
        
        // Create an event
        long templateId = -1;
        long diskOfferingId = -1;
        if(volume.getTemplateId() !=null){
            templateId = volume.getTemplateId();
        }
        if(volume.getDiskOfferingId() !=null){
            diskOfferingId = volume.getDiskOfferingId();
        }
        String eventParams = "id=" + volumeId +"\ndoId="+diskOfferingId+"\ntId="+templateId+"\ndcId="+volume.getDataCenterId();
        EventVO event = new EventVO();
        event.setAccountId(accountId);
        event.setUserId(userId);
        event.setType(EventTypes.EVENT_VOLUME_CREATE);
        event.setParameters(eventParams);
            
        // Update the volume in the database
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            createdVolume = _volsDao.findById(volumeId);
            
            if (volumePath != null) {
                StoragePoolVO pool = _storagePoolDao.findById(createdVolume.getPoolId());
                createdVolume.setStatus(AsyncInstanceCreateStatus.Created);
                createdVolume.setHostId(originalVolume.getHostId());
                createdVolume.setPoolId(pool.getId());
                createdVolume.setFolder(volumeFolder);
                createdVolume.setPath(volumePath);
                createdVolume.setSize(originalVolume.getSize());
                createdVolume.setPath(volumePath);
                long sizeMB = createdVolume.getSize()/(1024*1024);
                event.setDescription("Created volume: "+ createdVolume.getName() +" with size: " + sizeMB + " MB in pool: " + pool.getName());
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
    public long createVolumeFromSnapshotAsync(long accountId, long userId, long snapshotId) throws InternalErrorException {
        // Precondition the snapshot is valid
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
        VolumeVO volume = _volsDao.findById(snapshot.getVolumeId());
        

        String eventParams = "id=" + snapshotId;
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(volume.getAccountId());
        event.setType(EventTypes.EVENT_SNAPSHOT_ROLLBACK);
        event.setDescription("Creating a new volume from snapshot " + snapshotId + " of volume " + volume.getId());
        event.setParameters(eventParams);

        _eventDao.persist(event);
    
        String snapshotName = snapshot.getName();
        String volumeName = volume.getNameLabel();
        String name = volumeName + "_" + snapshotName;
        
        SnapshotOperationParam param = new SnapshotOperationParam(accountId, userId, volume.getId(), snapshotId, name);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CreateVolumeFromSnapshot");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateVolumeFromSnapshotCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job);
        
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
    public SnapshotPolicyVO createPolicy(long volumeId, String schedule, short interval, int maxSnaps) {
        SnapshotPolicyVO policy = new SnapshotPolicyVO(volumeId, schedule, interval, maxSnaps);
        Long policyId = _snapshotPolicyDao.persist(policy);
        if(policyId ==  null){
            return null;
        }        
        _snapSchedMgr.scheduleNextSnapshotJob(policy);
        return _snapshotPolicyDao.findById(policyId);
    }

    @Override
    public int getIntervalMaxSnaps(IntervalType intevral){
    	switch(intevral){
    	case HOURLY:
    		return HOURLYMAX;
    	case DAILY:
    		return DAILYMAX;
    	case WEEKLY:
    		return WEEKLYMAX;
    	case MONTHLY:
    		return MONTHLYMAX;
    	default:
    		return -1;
    	}
    }

    @Override
    public boolean deletePolicy(long policyId) {
        return _snapshotPolicyDao.delete(policyId);
    }


    @Override
    public List<SnapshotPolicyVO> listPoliciesforVolume(long volumeId) {
        return _snapshotPolicyDao.listByVolumeId(volumeId);
    }
    
    @Override
    public List<SnapshotPolicyVO> listPoliciesforSnapshot(long snapshotId) {
        SearchCriteria sc = PoliciesForSnapSearch.create();
        sc.setJoinParameters("policyRef", "snapshotId", snapshotId);
        return _snapshotPolicyDao.search(sc, null);
    }

    @Override
    public List<SnapshotVO> listSnapsforPolicy(long policyId, Filter filter) {
        SearchCriteria sc = PolicySnapshotSearch.create();
        sc.setJoinParameters("policy", "policyId", policyId);
        return _snapshotDao.search(sc, filter);
    }


    @Override
    public List<SnapshotVO> listSnapsforVolume(long volumeId) {
        return _snapshotDao.listByVolumeId(volumeId);        
    }

    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
    	return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotScheduleVO> findRecurringSnapshotSchedule(Long volumeId, Long policyId) {
        return _snapshotScheduleDao.listSchedules(volumeId, policyId);
    }
    
	@Override
	public SnapshotPolicyVO getPolicyForVolumeByInterval(long volumeId,
			short interval) {
		return _snapshotPolicyDao.findOneByVolumeInterval(volumeId, interval);
	}
        
    @Override
    public boolean configure(String name, Map<String, Object> params)
    throws ConfigurationException {
        _name = name;

        ComponentLocator locator = ComponentLocator.getCurrentLocator();

        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + HostDao.class.getName());
        }

        _volsDao = locator.getDao(VolumeDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + VolumeDao.class.getName());
        }

        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
            throw new ConfigurationException("unable to get " + EventDao.class.getName());
        }

        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("Unable to get " + UserVmDao.class.getName());
        }

        _snapshotDao = locator.getDao(SnapshotDao.class);
        if (_snapshotDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotDao.class.getName());
        }

        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
            throw new ConfigurationException("Unable to get " + StoragePoolDao.class.getName());
        }

        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
            throw new ConfigurationException("Unable to get " + AgentManager.class.getName());
        }

        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
            throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
        }
        
        _snapSchedMgr = locator.getManager(SnapshotScheduler.class);
        if (_snapSchedMgr == null) {
            throw new ConfigurationException("Unable to get " + SnapshotScheduler.class.getName());
        }

        _asyncMgr = locator.getManager(AsyncJobManager.class);
        if (_asyncMgr == null) {
            throw new ConfigurationException("Unable to get " + AsyncJobManager.class.getName());
        }
        
        _accountMgr = locator.getManager(AccountManager.class);
        if (_accountMgr == null) {
        	throw new ConfigurationException("Unable to get " + AccountManager.class.getName());
        }

        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to get " + AccountDao.class.getName());
        }
        
        _userDao = locator.getDao(UserDao.class);
        if (_userDao == null) {
            throw new ConfigurationException("Unable to get " + UserDao.class.getName());
        }

        _snapshotPolicyDao = locator.getDao(SnapshotPolicyDao.class);
        if (_snapshotPolicyDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotPolicyDao.class.getName());
        }
        
        _snapPolicyRefDao = locator.getDao(SnapshotPolicyRefDao.class);
        if (_snapPolicyRefDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotPolicyRefDao.class.getName());
        }
        
        _snapshotScheduleDao = locator.getDao(SnapshotScheduleDao.class);
        if (_snapshotScheduleDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotScheduleDao.class.getName());
        }
        
        _detailsDao = locator.getDao(DetailsDao.class);
        if (_detailsDao == null) {
            throw new ConfigurationException("Unable to get " + DetailsDao.class.getName());
        }
        
        _templatesDao = locator.getDao(VMTemplateDao.class);
        if (_templatesDao == null) {
            throw new ConfigurationException("unable to get templates dao");
        }
        
        _vmTemplatePoolDao = locator.getDao(VMTemplatePoolDao.class);
        if (_vmTemplatePoolDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplatePoolDao.class.getName());
        }
        
        PolicySnapshotSearch = _snapshotDao.createSearchBuilder();
        
        SearchBuilder<SnapshotPolicyRefVO> policySearch = _snapPolicyRefDao.createSearchBuilder();
        policySearch.and("policyId", policySearch.entity().getPolicyId(), SearchCriteria.Op.EQ);
        
        PolicySnapshotSearch.join("policy", policySearch, policySearch.entity().getSnapshotId(), PolicySnapshotSearch.entity().getId());
        policySearch.done();
        PolicySnapshotSearch.done();
        
        PoliciesForSnapSearch = _snapshotPolicyDao.createSearchBuilder();
        
        SearchBuilder<SnapshotPolicyRefVO> policyRefSearch = _snapPolicyRefDao.createSearchBuilder();
        policyRefSearch.and("snapshotId", policyRefSearch.entity().getSnapshotId(), SearchCriteria.Op.EQ);
        
        PoliciesForSnapSearch.join("policyRef", policyRefSearch, policyRefSearch.entity().getPolicyId(), PoliciesForSnapSearch.entity().getId());
        policyRefSearch.done();
        PoliciesForSnapSearch.done();
        s_logger.info("Snapshot Manager is configured.");

        return true;
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
}
