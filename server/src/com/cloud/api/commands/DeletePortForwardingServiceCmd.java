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

package com.cloud.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cloud.api.BaseCmd;
import com.cloud.api.ServerApiException;
import com.cloud.exception.InternalErrorException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.network.SecurityGroupVO;
import com.cloud.user.Account;
import com.cloud.utils.Pair;

public class DeletePortForwardingServiceCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(DeletePortForwardingServiceCmd.class.getName());

    private static final String s_name = "deleteportforwardingserviceresponse";
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
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find port forwarding service with id " + id);
        }
        
        if (account != null) {
            if (!isAdmin(account.getType())) {
                if (account.getId().longValue() != sg.getAccountId()) {
                    throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "unable to find a port forwarding service with id " + id + " for this account");
                }
            } else if (!getManagementServer().isChildDomain(account.getDomainId(), sg.getDomainId())) {
                throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to delete port forwarding service " + id + ", permission denied.");
            }
        }

        boolean success = true;
        if (sg != null) {
            try {
                success = getManagementServer().deleteSecurityGroup(accountId, id.longValue());
            } catch (PermissionDeniedException ex) {
                throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to delete port forwarding service " + sg.getName() + "; not the owner");
            } catch (InternalErrorException ex) {
                throw new ServerApiException(BaseCmd.UNSUPPORTED_ACTION_ERROR, "Port forwarding service " + sg.getName() +
                        " is currently assigned to one or more instances, please remove it from all instances before trying to delete.");
            }
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), Boolean.valueOf(success).toString()));
        return returnValues;
    }
}
