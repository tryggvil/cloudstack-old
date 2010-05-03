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
import com.vmops.user.User;
import com.vmops.user.UserAccount;
import com.vmops.user.UserVO;
import com.vmops.utils.Pair;

public class RegisterCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(RegisterCmd.class.getName());

    private static final String s_name = "registerresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USERNAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
        String username = (String)params.get(BaseCmd.Properties.USERNAME.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());

        User user = null;
        if (userId == null) {
            if (username == null) {
                throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "No username or userId given, unable to register user");
            } else {
                if (domainId == null) {
                    throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Username given without domainId, unable to find user to register.");
                }
                UserAccount useracct = getManagementServer().getUserAccount(username, domainId);
                if (useracct != null) {
                    user = new UserVO(useracct.getId());
                    user.setAccountId(useracct.getAccountId());
                }
            }
        } else {
            user = getManagementServer().findUserById(userId);
        }

        if (user == null) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "unable to find user: " + ((username == null) ? userId : username) + ", verify username or userId is correct");
        }
        
        // generate both an api key and a secret key, update the user table with the keys, return the keys to the user
        String apiKey = getManagementServer().createApiKey(user.getId());
        String secretKey = getManagementServer().createSecretKey(user.getId());

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();

        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.API_KEY.getName(), apiKey));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SECRET_KEY.getName(), secretKey));
        return returnValues;
    }
}
