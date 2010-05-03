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
import com.vmops.async.AsyncJobResult;
import com.vmops.async.executor.VolumeOperationResultObject;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ListVmStatsCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(ListVmStatsCmd.class.getName());
    private static final String s_name = "listvmstatsresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_IDS, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    
    public static String getResultObjectName() {
    	return "vmstats";
    }
    
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Account account = (Account) params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long) params.get(BaseCmd.Properties.USER_ID.getName());
    	String accountName = (String) params.get(BaseCmd.Properties.ACCOUNT.getName());
    	Long domainId = (Long) params.get(BaseCmd.Properties.DOMAIN_ID.getName());
    	Long virtualMachineId = (Long) params.get(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName());
    	String virtualMachineIdsString = (String) params.get(BaseCmd.Properties.VIRTUAL_MACHINE_IDS.getName());
    	
    	if ((virtualMachineId == null) && (virtualMachineIdsString == null)) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "No virtual machine id (or list of ids) specified.");
        }
    	
    	List<Long> virtualMachineIds = new ArrayList<Long>();
    	if (virtualMachineIdsString == null) { 
    		virtualMachineIds.add(virtualMachineId);
    	} else {
    		String[] virtualMachineIdsStringArray = virtualMachineIdsString.split("//,");
    		for (String virtualMachineIdString : virtualMachineIdsStringArray) {
    			try {
    				virtualMachineIds.add(Long.valueOf(virtualMachineIdString));
    			} catch (Exception e) {
    				throw new ServerApiException(BaseCmd.PARAM_ERROR, "Please specify valid virtual machine IDs.");
    			}
    		}
    	}
    	
    	if (account == null) {
    		// Admin API call
    		
    		// Check if accountName was passed in
    		if (accountName == null) {
    			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Account must be passed in.");
    		}
    		
    		if (domainId == null) {
    			domainId = Long.valueOf(1);
    		}
    		
    		// Look up the account by name and domain ID
    		account = getManagementServer().findActiveAccount(accountName, domainId);    		
    		
    		// If the account is null, this means that the accountName and domainId passed in were invalid
    		if (account == null) {
    			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find account with name: " + accountName + " and domain ID: " + domainId);
    		}
    	} else {
    		// User API call
    		
    		// If the account is an admin, and accountName/domainId were passed in, use the account specified by these parameters
    		if (isAdmin(account.getType()) && accountName != null && domainId != null) {
    			account = getManagementServer().findActiveAccount(accountName, domainId);
    			
    			if (account == null) {
    				throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find account with name: " + accountName + " and domain ID: " + domainId);
    			}
    		}
    	}
    	
    	// If command is executed via the Admin API, set userId to the id of System account (1)
        if (userId == null) {
            userId = Long.valueOf(Account.ACCOUNT_ID_SYSTEM);
        }
    	
    	try {
    		long jobId = getManagementServer().listVirtualMachineStatisticsAsync(virtualMachineIds);
    		
    		if (jobId == 0) {
            	s_logger.warn("Unable to schedule async-job for ListVmStats command");
            } else {
    	        if(s_logger.isDebugEnabled())
    	        	s_logger.debug("ListVmStats command has been accepted, job id: " + jobId);
            }
    		
    		waitInstanceCreation(jobId);
    		List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
            
            return returnValues;
    	} catch (Exception ex) {
    		throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to list VM stats: " + ex.getMessage());
    	}
    	
    }
  
}
