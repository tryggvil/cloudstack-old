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
import com.vmops.user.Account;
import com.vmops.user.User;
import com.vmops.utils.Pair;

public class DisableUserCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(DisableUserCmd.class.getName());
    private static final String s_name = "disableuserresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
    }

    @Override
    public String getName() {
        return s_name;
    }
    
    @Override
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }
    
    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long id = (Long)params.get(BaseCmd.Properties.ID.getName());
        
       //Check if user with id exists in the system
        User user = getManagementServer().findUserById(id);
        if (user == null) {
        	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find user by id");
        }
        else if (user.getRemoved() != null) {
        	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find user by id");
        }
        
        // If the user is a System user, return an error.  We do not allow this
        Account account = getManagementServer().findAccountById(user.getAccountId());
        if ((account != null) && (account.getId() == Account.ACCOUNT_ID_SYSTEM)) {
        	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "user id : " + id + " is a system user, disabling is not allowed");
        }
        
        
        long jobId = getManagementServer().disableUserAsync(id.longValue());
        if(jobId == 0) {
        	s_logger.warn("Unable to schedule async-job for DisableUser comamnd");
        } else {
	        if(s_logger.isDebugEnabled())
	        	s_logger.debug("DisableUser command has been accepted, job id: " + jobId);
        }
                
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
        return returnValues;
    }
}
