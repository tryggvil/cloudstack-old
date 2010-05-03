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

package com.vmops.async.executor;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.serializer.GsonHelper;
import com.vmops.storage.VolumeVO;

public class CreateVolumeFromSnapshotExecutor extends VolumeOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(CreateVolumeFromSnapshotExecutor.class.getName());
    
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
	    	VolumeVO volume = null;
	    	
	    	// By default assume that everything has failed.
            Long jobId = getJob().getId();
            int result = AsyncJobResult.STATUS_FAILED;
            int errorCode = BaseCmd.INTERNAL_ERROR;
            Object resultObject = "Failed to create volume from snapshot: " + param.getSnapshotId();
            
	    	try {
	    	    long accountId = param.getAccountId();
	    	    long userId = param.getUserId();
	    	    long snapshotId = param.getSnapshotId();
	    	    String volumeName = param.getName();
		    	volume = asyncMgr.getExecutorContext().getSnapshotMgr().createVolumeFromSnapshot(accountId, userId, snapshotId, volumeName);

		    	if (volume != null && volume.getStatus() == AsyncInstanceCreateStatus.Created) {
				    result = AsyncJobResult.STATUS_SUCCEEDED;
                    errorCode = 0; // Success
                    resultObject = composeResultObject(volume, param);
				}
	    	} catch(Exception e) {
	    	    resultObject = "Unable to create snapshot: " + e.getMessage();
	    		s_logger.warn(resultObject, e);
	    	}
	    	// In all cases, ensure that we call completeAsyncJob to the asyncMgr.
            asyncMgr.completeAsyncJob(jobId, result, errorCode, resultObject);
			return (volume != null);
		}
	}
}
