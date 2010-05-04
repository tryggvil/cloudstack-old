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

package com.vmops.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class UpdateAccountCmd extends BaseCmd{
    public static final Logger s_logger = Logger.getLogger(UpdateAccountCmd.class.getName());
    private static final String s_name = "updateaccountresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
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
        Long accountId = (Long)params.get(BaseCmd.Properties.ID.getName());
        String accountName = (String)params.get(BaseCmd.Properties.NAME.getName());
        Account adminAccount = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());;
        Boolean updateAccountResult = false;
        Account account = null;

        // check if account exists in the system
        account = getManagementServer().findAccountById(accountId);
    	if (account == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find account by id (" + accountId + ")");
    	}

    	if ((adminAccount != null) && !getManagementServer().isChildDomain(adminAccount.getDomainId(), account.getDomainId())) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid account id (" + accountId + ") given, unable to update account.");
    	}

    	// don't allow modify system account
    	if (accountId == Long.valueOf(Account.ACCOUNT_ID_SYSTEM)) {
    		throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "can not modify system account");
    	}

    	// if name is not specified, update the account with existing name
    	if (accountName == null) {
    		accountName = account.getAccountName();
    	}

        try {
        	getManagementServer().updateAccount(accountId, accountName);
        	account = getManagementServer().findAccountById(accountId);
        	if (account.getAccountName().equals(accountName)) {
        		updateAccountResult = true;
        	}
        } catch (Exception ex) {
            s_logger.error("Exception updating account", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update account " + accountId + ":  internal error.");
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        if (updateAccountResult == true) {
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), new Boolean(true)));
        } else {
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update account " + accountId);
        }
        return returnValues;
    }
}
