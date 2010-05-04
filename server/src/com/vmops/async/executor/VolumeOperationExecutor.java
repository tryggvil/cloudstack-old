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
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.async.executor.VolumeOperationParam.VolumeOp;
import com.vmops.serializer.GsonHelper;
import com.vmops.storage.VolumeVO;
import com.vmops.vm.UserVm;
import com.vmops.exception.InternalErrorException;


public class VolumeOperationExecutor extends BaseAsyncJobExecutor {
    public static final Logger s_logger = Logger.getLogger(VolumeOperationExecutor.class.getName());

	public boolean execute() {
		// Currently, volume operations will not be sync-ed with any queue, execute them directly
		
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();
    	
    	VolumeOperationParam param = gson.fromJson(job.getCmdInfo(), VolumeOperationParam.class);
    	
    	try {
    		VolumeOp op = param.getOp();
    		VolumeVO volume = null;
    		if (op == VolumeOp.Create) {
    			volume = asyncMgr.getExecutorContext().getManagementServer().createVolume(param.getAccountId(), param.getUserId(), param.getName(), param.getZoneId(), param.getDiskOfferingId());
                if (volume.getStatus() == AsyncInstanceCreateStatus.Corrupted) {
                	asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "Failed to create volume.");
                } else {
                	asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, composeResultObject(volume, param));
                }
            }
    		else if (op == VolumeOp.Delete) {
    			asyncMgr.getExecutorContext().getManagementServer().deleteVolume(param.getVolumeId());
    			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, null);
    		} 
    		else if (op == VolumeOp.Attach) {
    			asyncMgr.getExecutorContext().getManagementServer().attachVolumeToVM(param.getVmId(), param.getVolumeId());

    			// get the VM instance and Volume for the result object
    			UserVm vmInstance = asyncMgr.getExecutorContext().getManagementServer().findUserVMInstanceById(param.getVmId());
    			VolumeVO vol = asyncMgr.getExecutorContext().getManagementServer().findVolumeById(param.getVolumeId());
    			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, composeAttachResultObject(vmInstance, vol));
    		} 
    		else if (op == VolumeOp.Detach) {
    			asyncMgr.getExecutorContext().getManagementServer().detachVolumeFromVM(param.getVolumeId());
    			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, null);
    		} else {
    			throw new Exception("Invalid Volume Operation. Valid Operations are: CreateVolume, DeleteVolume, AttachVolume, and DetachVolume.");
    		}
    					
		} catch (InternalErrorException e) {
			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
		} catch (Exception e) {
			s_logger.warn("Unhandled Exception executing volume operation " + param.getOp(), e);
			asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
		}
		
    	return true;
	}
    
	public void processAnswer(VolumeOperationListener listener, long agentId, long seq, Answer answer) {
	}
	
	public void processDisconnect(VolumeOperationListener listener, long agentId) {
	}

	public void processTimeout(VolumeOperationListener listener, long agentId, long seq) {
	}
	
	protected VolumeOperationResultObject composeResultObject(VolumeVO volume, VolumeOperationParam param) {
		VolumeOperationResultObject resultObject = new VolumeOperationResultObject();

        resultObject.setId(volume.getId());
		resultObject.setName(param.getName());
		resultObject.setVolumeType(volume.getVolumeType());
		resultObject.setVolumeSize(volume.getSize());
		resultObject.setCreatedDate(volume.getCreated());
		resultObject.setAccountName(getAsyncJobMgr().getExecutorContext().getManagementServer().findAccountById(param.getAccountId()).getAccountName());
		resultObject.setDomainId(volume.getDomainId());
		//resultObject.setDomain(getAsyncJobMgr().getExecutorContext().getManagementServer().findDomainIdById(volume.getDomainId()).getName());
		resultObject.setStorageType("shared"); // NOTE: You can never create a local disk volume but if that changes, we need to change this
		if (volume.getPoolId() != null)
			resultObject.setStorage(getAsyncJobMgr().getExecutorContext().getManagementServer().findPoolById(volume.getPoolId()).getName());
		return resultObject;
	}

	private AttachVolumeOperationResultObject composeAttachResultObject(UserVm instance, VolumeVO vol) {
        AttachVolumeOperationResultObject resultObject = new AttachVolumeOperationResultObject();

        resultObject.setInstanceName(instance.getName());
        resultObject.setInstanceId(instance.getId());
        resultObject.setInstanceState(instance.getState().toString());
        resultObject.setStorageType("shared"); // NOTE: You can never attach a local disk volume but if that changes, we need to change this
        resultObject.setVolumeId(vol.getId());
        resultObject.setVolumeName(vol.getName());

        return resultObject;
    }
}
