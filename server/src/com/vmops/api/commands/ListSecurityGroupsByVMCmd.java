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
import com.vmops.network.SecurityGroupVO;
import com.vmops.server.Criteria;
import com.vmops.user.Account;
import com.vmops.utils.Pair;
import com.vmops.vm.UserVm;

public class ListSecurityGroupsByVMCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListSecurityGroupsByVMCmd.class.getName());

    private static final String s_name = "searchforsecuritygroupsbyvmresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_ID, Boolean.TRUE));
         s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
         s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
         s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
         s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IP_ADDRESS, Boolean.TRUE));
         s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
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
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        String ipAddress = (String)params.get(BaseCmd.Properties.IP_ADDRESS.getName());
        Long vmId = (Long)params.get(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());

        Long accountId = null;
        if ((account == null) || isAdmin(account.getType())) {
            // validate domainId before proceeding
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list security groups.");
                }
                if (accountName != null) {
                    Account userAccount = getManagementServer().findAccountByName(accountName, domainId);
                    if (userAccount != null) {
                        accountId = userAccount.getId();
                    } else {
                        throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                }
            }
        } else {
            accountId = account.getId();
        }

        UserVm userVm = getManagementServer().findUserVMInstanceById(vmId);
        if (userVm == null) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "Internal error, unable to find virtual machine " + vmId + " for listing security groups.");
        }

        if ((accountId != null) && (userVm.getAccountId() != accountId.longValue())) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to list security groups, account " + accountId + " does not own virtual machine " + vmId);
        }

        Criteria c = new Criteria("id", Boolean.TRUE, null, null);
        
        c.addCriteria(Criteria.INSTANCEID, vmId);
        if (keyword != null) {
        	c.addCriteria(Criteria.KEYWORD, keyword);
        } else {
        	c.addCriteria(Criteria.ADDRESS, ipAddress);
        }

        List<SecurityGroupVO> groups = getManagementServer().searchForSecurityGroupsByVM(c);

        if (groups == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Internal error searching for security groups");
        }

        List<Pair<String, Object>> groupsTags = new ArrayList<Pair<String, Object>>();
        Object[] groupTag = new Object[groups.size()];
        int i = 0;
        for (SecurityGroupVO group : groups) {
            List<Pair<String, Object>> groupData = new ArrayList<Pair<String, Object>>();
            groupData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), Long.valueOf(group.getId()).toString()));
            groupData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), group.getName()));
            groupData.add(new Pair<String, Object>(BaseCmd.Properties.DESCRIPTION.getName(), group.getDescription()));
            
            Account accountTemp = getManagementServer().findAccountById(group.getAccountId());
            if (accountTemp != null) {
            	groupData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), accountTemp.getAccountName()));
            	groupData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), accountTemp.getDomainId()));
            	groupData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(accountTemp.getDomainId()).getName()));
            } 
            groupTag[i++] = groupData;
        }
        Pair<String, Object> eventTag = new Pair<String, Object>("securitygroup", groupTag);
        groupsTags.add(eventTag);
        return groupsTags;
    }
}
