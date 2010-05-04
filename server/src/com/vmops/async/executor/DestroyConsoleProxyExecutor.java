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

public class DestroyConsoleProxyExecutor extends VMOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(DestroyConsoleProxyExecutor.class.getName());
    
	public boolean execute() {
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();
		ManagementServer managementServer = asyncMgr.getExecutorContext().getManagementServer();
    	VMOperationParam param = gson.fromJson(job.getCmdInfo(), VMOperationParam.class);
    	
		if(getSyncSource() == null) {
	    	asyncMgr.syncAsyncJobExecution(job.getId(), "ConsoleProxy", param.getVmId());
			return true;
		} else {
	    	try {
	    		boolean result = managementServer.destroyConsoleProxy(param.getVmId());
	    		if(result)
	    			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, 
						"success");
	    		else
	    			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
						"operation failed");
			} catch(Exception e) {
				s_logger.warn("Unable to destroy console proxy " + param.getVmId() + ":" + e.getMessage(), e);
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
					e.getMessage());
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
