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

import java.util.List;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.agent.api.Answer;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.serializer.GsonHelper;
import com.vmops.storage.VolumeVO;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.VirtualMachine.Event;

public class DestroyVMExecutor extends VMOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(DestroyVMExecutor.class.getName());
    
	@Override
    public boolean execute() {
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();
		
		if(getSyncSource() == null) {
	    	VMOperationParam param = gson.fromJson(job.getCmdInfo(), VMOperationParam.class);
	    	asyncMgr.syncAsyncJobExecution(job.getId(), "UserVM", param.getVmId());
	    	
	    	// always true if it does not have sync-source
	    	return true;
		} else {
	    	VMOperationParam param = gson.fromJson(job.getCmdInfo(), VMOperationParam.class);
			asyncMgr.updateAsyncJobAttachment(job.getId(), "vm_instance", param.getVmId());
	    	return asyncMgr.getExecutorContext().getVmMgr().executeDestroyVM(this, param);
		}
	}
	
	@Override
	@DB
    public void processAnswer(VMOperationListener listener, long agentId, long seq, Answer answer) {
		UserVmVO vm = listener.getVm();
		VMOperationParam param = listener.getParam();
		AsyncJobManager asyncMgr = getAsyncJobMgr();
		
    	if(s_logger.isDebugEnabled())
    		s_logger.debug("Execute asynchronize destroy VM command: received stop-VM answer, " + vm.getHostId() + "-" + seq);
    	
        EventVO event = new EventVO();
        event.setUserId(param.getUserId());
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_STOP);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
    	
        boolean stopped = false;
    	if(answer != null && answer.getResult())
			stopped = true;
    	
    	if(stopped) {
        	asyncMgr.getExecutorContext().getVmMgr().completeStopCommand(param.getUserId(), vm, Event.OperationSucceeded);
            event.setDescription("successfully stopped VM instance : " + vm.getName());
            asyncMgr.getExecutorContext().getEventDao().persist(event);
        	
            Transaction txn = Transaction.currentTxn();
            txn.start();
            
	        event = new EventVO();
	        event.setUserId(param.getUserId());
	        event.setAccountId(vm.getAccountId());
	        event.setType(EventTypes.EVENT_VM_DESTROY);
	        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
            event.setDescription("successfully destroyed VM instance : " + vm.getName());
            asyncMgr.getExecutorContext().getEventDao().persist(event);
            
            asyncMgr.getExecutorContext().getAccountMgr().decrementResourceCount(vm.getAccountId(), ResourceType.user_vm);
	        if (!asyncMgr.getExecutorContext().getVmDao().updateIf(vm, VirtualMachine.Event.DestroyRequested, vm.getHostId())) {
	            s_logger.debug("Unable to destroy the vm because it is not in the correct state: " + vm.toString());
	            
	            txn.rollback();
	        	asyncMgr.completeAsyncJob(getJob().getId(),
            		AsyncJobResult.STATUS_FAILED, 0, "Unable to destroy the vm because it is not in the correct state");
	        	
	            asyncMgr.releaseSyncSource(this);
	        	return;
	        }

	        asyncMgr.getExecutorContext().getVmMgr().cleanNetworkRules(param.getUserId(), vm.getId());
	        
	        // Mark the VM's disks as destroyed
	        List<VolumeVO> volumes = asyncMgr.getExecutorContext().getVolumeDao().findByInstance(param.getVmId());
	        for (VolumeVO volume : volumes) {
	        	asyncMgr.getExecutorContext().getVolumeDao().destroyVolume(volume.getId());
	        }
	        
	        asyncMgr.getExecutorContext().getAccountMgr().decrementResourceCount(vm.getAccountId(), ResourceType.volume, new Long(volumes.size()));

	        txn.commit();
        	
    		asyncMgr.completeAsyncJob(getJob().getId(),	AsyncJobResult.STATUS_SUCCEEDED, 0, "success");
    	} else {
            asyncMgr.getExecutorContext().getVmDao().updateIf(vm, Event.OperationFailed, vm.getHostId());
            asyncMgr.completeAsyncJob(getJob().getId(),
        		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "Agent failed to stop VM: " + vm.getName());
    		
            event.setDescription("failed to stop VM instance : " + vm.getName());
            event.setLevel(EventVO.LEVEL_ERROR);
            asyncMgr.getExecutorContext().getEventDao().persist(event);
    	}
        
        asyncMgr.releaseSyncSource(this);
	}
	
	@Override
    public void processDisconnect(VMOperationListener listener, long agentId) {
    	if(s_logger.isDebugEnabled())
    		s_logger.debug("Execute asynchronize destroy VM command: agent " + agentId + " disconnected");
    	
    	processDisconnectAndTimeout(listener, "agent is disconnected");
	}
	
	@Override
    public void processTimeout(VMOperationListener listener, long agentId, long seq) {
    	if(s_logger.isDebugEnabled())
    		s_logger.debug("Execute asynchronize destroy VM command: timed out, " + agentId + "-" + seq);
    	
    	processDisconnectAndTimeout(listener, "operation timed out");
	}

	private void processDisconnectAndTimeout(VMOperationListener listener, String resultMessage) {
		UserVmVO vm = listener.getVm();
		VMOperationParam param = listener.getParam();
		AsyncJobManager asyncMgr = getAsyncJobMgr();
		
        EventVO event = new EventVO();
        event.setUserId(param.getUserId());
        event.setAccountId(vm.getAccountId());
        event.setType(EventTypes.EVENT_VM_DESTROY);
        event.setParameters("id="+vm.getId() + "\nvmName=" + vm.getName() + "\nsoId=" + vm.getServiceOfferingId() + "\ntId=" + vm.getTemplateId() + "\ndcId=" + vm.getDataCenterId());
        event.setDescription("failed to stop VM instance : " + vm.getName() + " due to " + resultMessage);
        event.setLevel(EventVO.LEVEL_ERROR);
        
        asyncMgr.completeAsyncJob(getJob().getId(),
    		AsyncJobResult.STATUS_FAILED, 0, resultMessage);
    	asyncMgr.getExecutorContext().getEventDao().persist(event);
    	
    	asyncMgr.releaseSyncSource(this);
	}
}
