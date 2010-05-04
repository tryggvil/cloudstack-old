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

package com.vmops.async.executor;

import java.util.List;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.serializer.GsonHelper;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.user.Account;

public class CreateSnapshotExecutor extends VolumeOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(CreateSnapshotExecutor.class.getName());
    
	public boolean execute() {
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();
    	Gson gson = GsonHelper.getBuilder().create();
    	
		if (getSyncSource() == null) {
	    	SnapshotOperationParam param = gson.fromJson(job.getCmdInfo(), SnapshotOperationParam.class);
	    	asyncMgr.syncAsyncJobExecution(job.getId(), "Volume", param.getVolumeId());
	    	
	    	// always true if it does not have sync-source
	    	return true;
		} else {
	    	SnapshotOperationParam param = gson.fromJson(job.getCmdInfo(), SnapshotOperationParam.class);
	    	SnapshotManager snapshotManager = asyncMgr.getExecutorContext().getSnapshotMgr();
	    	long volumeId = param.getVolumeId();
	    	List<Long> policyIds = param.getPolicyIds();
	    	long snapshotId = 0;
	    	long userId = param.getUserId();
	    	
	    	// By default assume that everything has failed.
            boolean backedUp = false;
	    	Long jobId = getJob().getId();
            int result = AsyncJobResult.STATUS_FAILED;
            int errorCode = BaseCmd.INTERNAL_ERROR;
            Object resultObject = "Failed to create snapshot.";
            
	    	try {
	    	    SnapshotVO snapshot = snapshotManager.createSnapshot(userId, param.getVolumeId(), param.getPolicyIds());

		    	if (snapshot != null && snapshot.getStatus() != AsyncInstanceCreateStatus.Corrupted) {
				    snapshotId = snapshot.getId();
				    backedUp = snapshotManager.backupSnapshotToSecondaryStorage(userId, snapshot);
				    if (backedUp) {
				        result = AsyncJobResult.STATUS_SUCCEEDED;
				        errorCode = 0; // Success
				        resultObject = composeResultObject(snapshot);
				    }
				    else {
				        // More specific error
				        resultObject = "Created snapshot: " + snapshotId + " on primary but failed to backup on secondary";
				    }
				}
			} catch(Exception e) {
	    	    resultObject = "Unable to create snapshot: " + e.getMessage();
	    		s_logger.warn(resultObject, e);
	    	}

			// In all cases, ensure that we call completeAsyncJob to the asyncMgr.
	    	asyncMgr.completeAsyncJob(jobId, result, errorCode, resultObject);
	    	
	    	// Cleanup jobs to do after the snapshot has been created.
	    	snapshotManager.postCreateSnapshot(userId, volumeId, snapshotId, policyIds, backedUp);
	    	return true;
		}
	}
	
	private CreateSnapshotResultObject composeResultObject(Snapshot snapshot) {
		CreateSnapshotResultObject resultObject = new CreateSnapshotResultObject();
		resultObject.setId(snapshot.getId());
		
		Account account = getAsyncJobMgr().getExecutorContext().getAccountDao().findById(snapshot.getAccountId());
		if(account != null)
			resultObject.setAccountName(account.getAccountName());
		resultObject.setVolumeId(snapshot.getVolumeId());
		resultObject.setCreated(snapshot.getCreated());
		resultObject.setName(snapshot.getName());
		resultObject.setPath(snapshot.getPath());
		return resultObject;
	}
}
