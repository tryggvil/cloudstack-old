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

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.agent.api.Answer;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.serializer.GsonHelper;
import com.vmops.server.ManagementServer;
import com.vmops.storage.Snapshot;
import com.vmops.utils.exception.VmopsRuntimeException;

public class ScheduleRecurringSnapshotExecutor extends VMOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(ScheduleRecurringSnapshotExecutor.class.getName());
	
	public boolean execute() {
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();
    	ManagementServer managementServer = asyncMgr.getExecutorContext().getManagementServer();

		if(getSyncSource() == null) {
	    	SnapshotOperationParam param = gson.fromJson(job.getCmdInfo(), SnapshotOperationParam.class);
	    	Snapshot snapshot = managementServer.findSnapshotById(param.getSnapshotId());
	    	if(snapshot != null)
	    		asyncMgr.syncAsyncJobExecution(job.getId(), "Volume", snapshot.getVolumeId());
    		else
    			asyncMgr.completeAsyncJob(getJob().getId(), 
    		    		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "snaphsot no long exists");
	    	
	    	// always true if it does not have sync-source
	    	return true;
		} else {
	    	RecurringSnapshotParam param = gson.fromJson(job.getCmdInfo(), RecurringSnapshotParam.class);
	    	
	    	try {
				managementServer.scheduleRecurringSnapshots(
					param.getUserId(), param.getVmId(), param.getHourlyMax(), 
					param.getDailyMax(), param.getWeeklyMax(), param.getMonthlyMax());
				
				asyncMgr.completeAsyncJob(getJob().getId(), 
		    		AsyncJobResult.STATUS_SUCCEEDED, 0, "success");
	    	} catch(VmopsRuntimeException e) {
	    	    if (s_logger.isInfoEnabled()) {
	                s_logger.warn("Unable to schedule recurring snapshot: " + e.getMessage());
	    	    }
                asyncMgr.completeAsyncJob(getJob().getId(), 
                    AsyncJobResult.STATUS_FAILED, BaseCmd.UNSUPPORTED_ACTION_ERROR, e.getMessage());
            } catch(Exception e) {
	    		s_logger.warn("Unable to schedule recurring snapshot: " + e.getMessage(), e);
				asyncMgr.completeAsyncJob(getJob().getId(), 
		    		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
	    	}
	    	
	    	return true;
		}
	}
    
	public void processAnswer(VMOperationListener listener, long agentId, long seq, Answer answer) {
	}
	
	public void processDisconnect(VMOperationListener listener, long agentId) {
	}

	public void processTimeout(VMOperationListener listener, long agentId, long seq) {
	}
}
