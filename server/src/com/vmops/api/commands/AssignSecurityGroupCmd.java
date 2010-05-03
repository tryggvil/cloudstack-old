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
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.user.Account;
import com.vmops.utils.Pair;
import com.vmops.utils.StringUtils;

public class AssignSecurityGroupCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(AssignSecurityGroupCmd.class.getName());
	
    private static final String s_name = "assignsecuritygroupresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.GROUP_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.GROUP_IDS, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PUBLIC_IP, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_ID, Boolean.TRUE));
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
        String securityGroupIds = (String)params.get(BaseCmd.Properties.GROUP_IDS.getName());
        String publicIp = (String)params.get(BaseCmd.Properties.PUBLIC_IP.getName());
        Long vmId = (Long)params.get(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName());

        if ((securityGroupId == null) && (securityGroupIds == null)) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "No group id (or list if ids) specified.");
        }

        List<Long> sgIdList = null;
        if (securityGroupIds != null) {
            sgIdList = new ArrayList<Long>();
            StringTokenizer st = new StringTokenizer(securityGroupIds, ",");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                try {
                    Long nextSGId = Long.parseLong(token);
                    sgIdList.add(nextSGId);
                } catch (NumberFormatException nfe) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "The group id " + token + " is not a valid parameter.");
                }
            }
        }

        if (userId == null) {
            userId = Long.valueOf(1);
        }

        List<Long> validateSGList = null;
        if (securityGroupId == null) {
            validateSGList = sgIdList;
        } else {
            validateSGList = new ArrayList<Long>();
            validateSGList.add(securityGroupId);
        }
        Long validatedAccountId = getManagementServer().validateSecurityGroupsAndInstance(validateSGList, vmId);
        if (validatedAccountId == null) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to apply security groups " + StringUtils.join(sgIdList, ",") + " to instance " + vmId + ".  Invalid list of groups for the given instance.");
        }
        if ((account != null) && !isAdmin(account.getType())) {
            if (account.getId().longValue() != validatedAccountId.longValue()) {
                throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Permission denied applying security groups " + StringUtils.join(sgIdList, ",") + " to instance " + vmId + ".");
            }
        }

        long jobId = getManagementServer().assignSecurityGroupAsync(userId, securityGroupId, sgIdList, publicIp, vmId);
        
        if(jobId == 0) {
        	s_logger.warn("Unable to schedule async-job for AssignSecurityGroup comamnd");
        } else {
	        if(s_logger.isDebugEnabled())
	        	s_logger.debug("AssignSecurityGroup command has been accepted, job id: " + jobId);
        }
        
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
        return returnValues;
    }
}
