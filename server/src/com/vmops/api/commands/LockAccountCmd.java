/**
 *  Copyright (C) 2010 Cloud.com.  All rights reserved.
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

public class LockAccountCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(LockAccountCmd.class.getName());

    private static final String s_name = "lockaccountresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
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
        Account adminAccount = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long id = (Long)params.get(BaseCmd.Properties.ID.getName());

        // don't allow modify system account
        if (id == Long.valueOf(Account.ACCOUNT_ID_SYSTEM)) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "can not enable system account");
        }

        // check if account specified by id exists in the system
        Account account = getManagementServer().findAccountById(id);
        if (account == null) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account by id");
        } else if (getManagementServer().findActiveAccount(account.getAccountName(), account.getDomainId()) == null) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account by id");
        }

        if ((adminAccount != null) && !getManagementServer().isChildDomain(adminAccount.getDomainId(), account.getDomainId())) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Failed to enable account " + id + ", permission denied.");
        }

        boolean success = getManagementServer().lockAccount(id.longValue());
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), Boolean.valueOf(success).toString()));
        return returnValues;
    }
}
