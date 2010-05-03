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
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.NetworkRuleConflictException;
import com.vmops.exception.PermissionDeniedException;
import com.vmops.serializer.GsonHelper;
import com.vmops.server.ManagementServer;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;

public class AssignSecurityGroupExecutor extends BaseAsyncJobExecutor {
    public static final Logger s_logger = Logger.getLogger(AssignSecurityGroupExecutor.class.getName());
	
	public boolean execute() {
		Gson gson = GsonHelper.getBuilder().create();
		AsyncJobManager asyncMgr = getAsyncJobMgr();
		AsyncJobVO job = getJob();
		ManagementServer managementServer = asyncMgr.getExecutorContext().getManagementServer();
		SecurityGroupParam param = gson.fromJson(job.getCmdInfo(), SecurityGroupParam.class);
		
		if(getSyncSource() == null) {
			DomainRouterVO router = getRouterSyncSource(param);
	        if(router == null) {
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, 
					BaseCmd.NET_INVALID_PARAM_ERROR, "Unable to find router for user vm " + param.getInstanceId() + " when assigning security group"); 
	        } else {
		    	asyncMgr.syncAsyncJobExecution(job.getId(), "Router", router.getId());
	        }
			return true;
		} else {
			try {
				managementServer.assignSecurityGroup(param.getUserId(), param.getSecurityGroupId(), param.getSecurityGroupIdList(), param.getPublicIp(), param.getInstanceId());
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, 
					"success");
			} catch (PermissionDeniedException e) {
				if(s_logger.isDebugEnabled())
					s_logger.debug("Unable to assign security group : " + e.getMessage());
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.PARAM_ERROR, 
					e.getMessage());
			} catch (NetworkRuleConflictException e) {
				if(s_logger.isDebugEnabled())
					s_logger.debug("Unable to assign security group : " + e.getMessage());
				
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.NET_CONFLICT_IPFW_RULE_ERROR, 
						e.getMessage());
			} catch (InvalidParameterValueException e) {
				if(s_logger.isDebugEnabled())
					s_logger.debug("Unable to assign security group : " + e.getMessage());
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.PARAM_ERROR, 
						e.getMessage());
			} catch (InternalErrorException e) {
				if(s_logger.isDebugEnabled())
					s_logger.debug("Unable to assign security group : " + e.getMessage());
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
						e.getMessage());
			} catch(Exception e) {
				s_logger.warn("Unable to assign security group : " + e.getMessage(), e);
				asyncMgr.completeAsyncJob(getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, 
					e.getMessage());
			}
		}
		return true;
	}
	
	private DomainRouterVO getRouterSyncSource(SecurityGroupParam param) {
	    UserVmDao userVmDao = getAsyncJobMgr().getExecutorContext().getVmDao();
	    DomainRouterDao routerDao = getAsyncJobMgr().getExecutorContext().getRouterDao();
		
        UserVmVO userVm = userVmDao.findById(param.getInstanceId());
        if(userVm == null)
        	return null;
        
        return routerDao.findById(userVm.getDomainRouterId());
	}
}
