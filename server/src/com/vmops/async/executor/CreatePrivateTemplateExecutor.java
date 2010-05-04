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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncJobExecutorContext;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.serializer.GsonHelper;
import com.vmops.server.ManagementServer;
import com.vmops.storage.Snapshot;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.user.AccountManager;
import com.vmops.user.AccountVO;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.vm.UserVmManager;

public class CreatePrivateTemplateExecutor extends VolumeOperationExecutor {
    public static final Logger s_logger = Logger.getLogger(CreatePrivateTemplateExecutor.class.getName());
	
	public boolean execute() {
    	Gson gson = GsonHelper.getBuilder().create();
    	AsyncJobManager asyncMgr = getAsyncJobMgr();
    	AsyncJobVO job = getJob();

		if(getSyncSource() == null) {
		    CreatePrivateTemplateParam param = gson.fromJson(job.getCmdInfo(), CreatePrivateTemplateParam.class);
	    	asyncMgr.syncAsyncJobExecution(job.getId(), "Volume", param.getVolumeId());
	    	
	    	// always true if it does not have sync-source
	    	return true;
		} else {
	    	CreatePrivateTemplateParam param = gson.fromJson(job.getCmdInfo(), CreatePrivateTemplateParam.class);
	    	
	    	AsyncJobExecutorContext asyncJobExecutorContext = asyncMgr.getExecutorContext();
	    	ManagementServer managerServer = asyncJobExecutorContext.getManagementServer();
	    	AccountManager accountManager = asyncJobExecutorContext.getAccountMgr();
	    	UserVmManager vmMgr = asyncJobExecutorContext.getVmMgr();
	    	SnapshotManager snapshotManager = asyncJobExecutorContext.getSnapshotMgr();
			try {
		        // Check that the resource limit for templates won't be exceeded
				VolumeVO volume = managerServer.findVolumeById(param.getVolumeId());
		    	AccountVO account = (AccountVO) managerServer.findAccountById(volume.getAccountId());
		        if (accountManager.resourceLimitExceeded(account, ResourceType.template)) {
		        	asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "The maximum number of templates for the specified account has been exceeded.");
		        	return true;
		        }
		        
		    	VMTemplateVO template = vmMgr.createPrivateTemplateRecord(param.getUserId(), 
		    		param.getVolumeId(), param.getName(), param.getDescription(), param.getGuestOsId(),
		    		param.getRequiresHvm(), param.getBits(), param.isPasswordEnabled(), param.isPublic(), param.isFeatured());

		    	if (template == null) {
					asyncMgr.completeAsyncJob(getJob().getId(), 
						AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "failed to create private template DB record");
					return true;
		    	}
		    	
            	if(s_logger.isInfoEnabled())
            		s_logger.info("CreatePrivateTemplate created a new instance " + template.getId() 
            			+ ", update async job-" + job.getId() + " progress status");

            	asyncMgr.updateAsyncJobAttachment(job.getId(), "vm_template", template.getId());
            	asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, template.getId());
            	Snapshot snapshot = null;
	    	    try {
	    	        List<Long> policyIds = new ArrayList<Long>();
	    	        // For template snapshot, we use the dummy policy Id 0, same as manual snapshots
	    	        policyIds.add(0L);
	                snapshot = snapshotManager.createSnapshot(param.getUserId(), param.getVolumeId(), policyIds);
	                if (snapshot != null) {
	                    param.setSnapshotId(snapshot.getId());
	                } else {
	                     asyncMgr.completeAsyncJob(getJob().getId(), 
	                             AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "failed to create snapshot for basis of private template");
	                     return true;
	                }
	    	    } catch (VmopsRuntimeException vmopsEx) {
	                s_logger.warn("Unable to create snapshot for private template: " + vmopsEx.getMessage(), vmopsEx);
	                asyncMgr.completeAsyncJob(getJob().getId(), 
	                        AsyncJobResult.STATUS_FAILED, BaseCmd.UNSUPPORTED_ACTION_ERROR, vmopsEx.getMessage());
                    return true;
	    	    }
		    	
				template = managerServer.createPrivateTemplate(template, param.getUserId(), 
						param.getSnapshotId(), param.getName(), param.getDescription());

				if(template != null) {
					asyncMgr.completeAsyncJob(getJob().getId(), 
						AsyncJobResult.STATUS_SUCCEEDED, 0, composeResultObject(template));
				} else {
				    // create template failed, clean up the snapshot that was created specifically for this template
				    managerServer.destroyTemplateSnapshot(param.getUserId(), param.getSnapshotId());

					asyncMgr.completeAsyncJob(getJob().getId(), 
						AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, "failed to create private template");
				}
				
				if( snapshot != null ) {
                    try {
                        managerServer.destroyTemplateSnapshot(param.getUserId(), snapshot.getId());
                    } catch (VmopsRuntimeException vmopsEx) {
                        s_logger.warn("Unable to destroy snapshot for private template: " + vmopsEx.getMessage(), vmopsEx);
                        asyncMgr.completeAsyncJob(getJob().getId(), 
                                AsyncJobResult.STATUS_FAILED, BaseCmd.UNSUPPORTED_ACTION_ERROR, vmopsEx.getMessage());
                        return true;
                    }
				}
			} catch (InvalidParameterValueException e) {
				if(s_logger.isDebugEnabled())
					s_logger.debug("Unable to create private template: " + e.getMessage());
	    		asyncMgr.completeAsyncJob(getJob().getId(), 
	        		AsyncJobResult.STATUS_FAILED, BaseCmd.PARAM_ERROR, e.getMessage());
			} catch (Exception e) {
				s_logger.warn("Unable to create private template: " + e.getMessage(), e);
	    		asyncMgr.completeAsyncJob(getJob().getId(), 
		        		AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
			}
	    	return true;
		}
	}
	
	private CreatePrivateTemplateResultObject composeResultObject(VMTemplateVO template) {
		CreatePrivateTemplateResultObject resultObject = new CreatePrivateTemplateResultObject();
		
		resultObject.setId(template.getId());
		resultObject.setName(template.getName());
		resultObject.setDisplayText(template.getDisplayText());
		resultObject.setPublic(template.isPublicTemplate());
		resultObject.setRequiresHvm(template.requiresHvm());
		resultObject.setBits(template.getBits());
		resultObject.setCreated(template.getCreated());
		resultObject.setReady(template.isReady());
		resultObject.setPasswordEnabled(template.getEnablePassword());
		return resultObject;
	}
}
