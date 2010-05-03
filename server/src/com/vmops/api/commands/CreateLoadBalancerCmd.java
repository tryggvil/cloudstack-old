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
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.PermissionDeniedException;
import com.vmops.network.LoadBalancerVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class CreateLoadBalancerCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(CreateLoadBalancerCmd.class.getName());

    private static final String s_name = "createloadbalancerresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DESCRIPTION, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IP_ADDRESS, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PUBLIC_PORT, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PRIVATE_PORT, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ALGORITHM, Boolean.TRUE));
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
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        String name = (String)params.get(BaseCmd.Properties.NAME.getName());
        String description = (String)params.get(BaseCmd.Properties.DESCRIPTION.getName());
        String ipAddress = (String)params.get(BaseCmd.Properties.IP_ADDRESS.getName());
        String publicPort = (String)params.get(BaseCmd.Properties.PUBLIC_PORT.getName());
        String privatePort = (String)params.get(BaseCmd.Properties.PRIVATE_PORT.getName());
        String algorithm = (String)params.get(BaseCmd.Properties.ALGORITHM.getName());

        if (userId == null) {
            userId = Long.valueOf(1);
        }

        Long accountId = null;
        if ((account == null) || isAdmin(account.getType())) {
            if ((accountName != null) && (domainId != null)) {
                Account userAccount = getManagementServer().findActiveAccount(accountName, domainId);
                if (userAccount != null) {
                    accountId = userAccount.getId();
                } else {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to create load balancer for account " + accountName + " in domain " + domainId + "; account not found...");
                }
            } else {
                Account userAccount = getManagementServer().findAccountByIpAddress(ipAddress);
                if (userAccount != null) {
                    accountId = userAccount.getId();
                }
            }
        }

        if (accountId == null) {
            accountId = ((account == null) ? null : account.getId());
        }
        if (accountId == null) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account for creating load balancer");
        }

        LoadBalancerVO existingLB = getManagementServer().findLoadBalancer(accountId, name);

        if (existingLB != null) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to create load balancer, an existing load balancer with name " + name + " already exisits.");
        }

        try {
            LoadBalancerVO loadBalancer = getManagementServer().createLoadBalancer(userId, accountId, name, description, ipAddress, publicPort, privatePort, algorithm);
            List<Pair<String, Object>> embeddedObject = new ArrayList<Pair<String, Object>>();
            List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), loadBalancer.getId().toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), loadBalancer.getName()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DESCRIPTION.getName(), loadBalancer.getDescription()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.PUBLIC_IP.getName(), loadBalancer.getIpAddress()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.PUBLIC_PORT.getName(), loadBalancer.getPublicPort()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.PRIVATE_PORT.getName(), loadBalancer.getPrivatePort()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ALGORITHM.getName(), loadBalancer.getAlgorithm()));
            
            Account accountTemp = getManagementServer().findAccountById(loadBalancer.getAccountId());
            if (accountTemp != null) {
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), accountTemp.getAccountName()));
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), accountTemp.getDomainId()));
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(accountTemp.getDomainId()).getName()));
            }
            embeddedObject.add(new Pair<String, Object>("loadbalancer", new Object[] { returnValues } ));
            return embeddedObject;
        } catch (InvalidParameterValueException paramError) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, paramError.getMessage());
        } catch (PermissionDeniedException permissionError) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, permissionError.getMessage());
        } catch (Exception ex) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }
    }
}
