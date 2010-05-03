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
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.PermissionDeniedException;
import com.vmops.network.SecurityGroupVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class DeleteSecurityGroupCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(DeleteSecurityGroupCmd.class.getName());

    private static final String s_name = "deletesecuritygroupresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
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
        Long id = (Long)params.get(BaseCmd.Properties.ID.getName());
        Long accountId = (account == null) ? null : account.getId();

        //verify parameters
        SecurityGroupVO sg = getManagementServer().findSecurityGroupById(id.longValue());
        if (sg == null) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find secuirty group with id " + id);
        }
        
        if ((account != null) && !isAdmin(account.getType())) {
            if (account.getId().longValue() != sg.getAccountId()) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find a security group with id " + id + " for this account");
            }
        }
        
        boolean success = true;
        if (sg != null) {
            try {
                success = getManagementServer().deleteSecurityGroup(accountId, id.longValue());
            } catch (PermissionDeniedException ex) {
                throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to delete security group " + sg.getName() + "; not the owner");
            } catch (InternalErrorException ex) {
                throw new ServerApiException(BaseCmd.UNSUPPORTED_ACTION_ERROR, "Security group " + sg.getName() +
                        " is currently assigned to one or more instances, please remove it from all instances before trying to delete.");
            }
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), Boolean.valueOf(success).toString()));
        return returnValues;
    }
}
