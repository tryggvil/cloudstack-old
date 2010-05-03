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

public class UpdateUserCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateUserCmd.class.getName());

    private static final String s_name = "updateuserresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USERNAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PASSWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.FIRSTNAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.LASTNAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.EMAIL, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long userId = (Long)params.get(BaseCmd.Properties.ID.getName());
        String username = (String)params.get(BaseCmd.Properties.USERNAME.getName());
        String password = (String)params.get(BaseCmd.Properties.PASSWORD.getName());
        String firstname = (String)params.get(BaseCmd.Properties.FIRSTNAME.getName());
        String lastname = (String)params.get(BaseCmd.Properties.LASTNAME.getName());
        String email = (String)params.get(BaseCmd.Properties.EMAIL.getName());

        //check if the user exists in the system
        User user = getManagementServer().getUser(userId.longValue());
        if (user == null) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find user by id");
        }

        // If the account is an admin type, return an error.  We do not allow this
        Account account = getManagementServer().findAccountById(user.getAccountId());
        if (account != null && (account.getId() == Account.ACCOUNT_ID_SYSTEM)) {
        	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "user id : " + userId + " is system account, update is not allowed");
        }

        if (firstname == null) { 
        	firstname = user.getFirstname();
        }
        if (lastname == null) { 
        	lastname = user.getLastname(); 
        }
        if (username == null) { 
        	username = user.getUsername();  
        }
        if (password == null) { 
        	password = user.getPassword();
        }
        if (email == null) {
        	email = user.getEmail();
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        boolean success = getManagementServer().updateUser(user.getId(), username, password, firstname, lastname, email);
        if (success) {
           returnValues.add(new Pair<String,Object> (BaseCmd.Properties.SUCCESS.getName(), Boolean.valueOf(success).toString()));
        } else {
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "failed to update user");
        }
        return returnValues;
    }
}
