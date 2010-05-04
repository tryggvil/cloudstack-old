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
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.serializer.GsonHelper;
import com.vmops.server.ManagementServer;

public class ResetVMPasswordExecutor extends BaseAsyncJobExecutor {
    public static final Logger s_logger = Logger.getLogger(ResetVMPasswordExecutor.class.getName());
	
	public boolean execute() {
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobVO job = getJob();

		if(getSyncSource() == null) {
	    	VMOperationParam param = gson.fromJson(job.getCmdInfo(), VMOperationParam.class);
	    	asyncMgr.syncAsyncJobExecution(job.getId(), "UserVM", param.getVmId());
	    	
	    	// always true if it does not have sync-source
	    	return true;
		} else {
			ManagementServer managementServer = asyncMgr.getExecutorContext().getManagementServer();
			ResetVMPasswordParam param = gson.fromJson(job.getCmdInfo(), ResetVMPasswordParam.class);
			
			asyncMgr.updateAsyncJobAttachment(job.getId(), "vm_instance", param.getVmId());
			try {
				boolean success = managementServer.resetVMPassword(param.getUserId(), param.getVmId(), param.getPassword());
				if(success)
					asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, 
						new ResetVMPasswordResultObject(param.getVmId(), param.getPassword()));
				else
					asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
						new ResetVMPasswordResultObject(param.getVmId(), ""));
			} catch(Exception e) {
				s_logger.warn("Unable to reset password for VM " + param.getVmId() + ": " + e.getMessage(), e);
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
						new ResetVMPasswordResultObject(param.getVmId(), e.getMessage()));
			}
		}
		return true;
	}
}
