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

package com.vmops.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.network.SecurityGroupVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;
import com.vmops.vm.UserVmVO;

public class RemoveSecurityGroupCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(RemoveSecurityGroupCmd.class.getName());

    private static final String s_name = "removesecuritygroupresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.GROUP_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PUBLIC_IP, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
        Long securityGroupId = (Long)params.get(BaseCmd.Properties.GROUP_ID.getName());
        String publicIp = (String)params.get(BaseCmd.Properties.PUBLIC_IP.getName());
        Long vmId = (Long)params.get(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName());
        
        //verify input parameters
        SecurityGroupVO securityG = getManagementServer().findSecurityGroupById(securityGroupId);
        if (securityG == null) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find a security group with id " + securityGroupId);
        } else if (account != null) {
            if (!isAdmin(account.getType()) && (account.getId().longValue() != securityG.getAccountId())) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find a security group with id " + securityGroupId + " for this account");
            } else if (!getManagementServer().isChildDomain(account.getDomainId(), securityG.getDomainId())) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid security group id (" + securityGroupId + ") given, unable to remove security group.");
            }
        }
        
        UserVmVO vmInstance = getManagementServer().findUserVMInstanceById(vmId.longValue());
        if (vmInstance == null) {
        	throw new ServerApiException(BaseCmd.VM_INVALID_PARAM_ERROR, "unable to find a virtual machine with id " + vmId);
        }
        if (account != null) {
            if (!isAdmin(account.getType()) && (account.getId().longValue() != vmInstance.getAccountId())) {
                throw new ServerApiException(BaseCmd.VM_INVALID_PARAM_ERROR, "unable to find a virtual machine with id " + vmId + " for this account");
            } else if (!getManagementServer().isChildDomain(account.getDomainId(), vmInstance.getDomainId())) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid virtual machine id (" + vmId + ") given, unable to remove security group.");
            }
        }

        Account ipAddrAccount = getManagementServer().findAccountByIpAddress(publicIp);
        if (ipAddrAccount == null) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "account " + account.getAccountName() + " doesn't own ip address " + publicIp);
        }

        Long accountId = ipAddrAccount.getId();
        if ((account != null) && !isAdmin(account.getType())) {
            if (account.getId().longValue() != accountId) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "account " + account.getAccountName() + " doesn't own ip address " + publicIp);
            }
        }

        if (userId == null) {
            userId = Long.valueOf(1);
        }
        
        long jobId = getManagementServer().removeSecurityGroupAsync(userId, securityGroupId, publicIp, vmId);
        if(jobId == 0) {
        	s_logger.warn("Unable to schedule async-job for RemoveSecurityGroup comamnd");
        } else {
	        if(s_logger.isDebugEnabled())
	        	s_logger.debug("RemoveSecurityGroup command has been accepted, job id: " + jobId);
        }
        
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
        return returnValues;
    }
}
