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
import com.vmops.configuration.ResourceLimitVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.domain.DomainVO;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class CreateResourceLimitCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(CreateResourceLimitCmd.class.getName());

    private static final String s_name = "createresourcelimitresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.RESOURCE_TYPE, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.MAX, Boolean.TRUE));
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
    	Long domainId = (Long) params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        String accountName = (String) params.get(BaseCmd.Properties.ACCOUNT.getName());
        Integer type = (Integer) params.get(BaseCmd.Properties.RESOURCE_TYPE.getName());
        Long max = (Long) params.get(BaseCmd.Properties.MAX.getName());
        Long accountId = null;
        
        if (accountName != null) {
        	if (domainId == null) {
            	domainId = Long.valueOf(1);
            } else {
            	DomainVO domain = getManagementServer().findDomainIdById(domainId);
            	if (domain == null) {
            		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find domain by id " + domainId);
            	}
            }
        	Account account = getManagementServer().findActiveAccount(accountName, domainId);
        	if (account == null) {
        		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find account by name " + accountName + " in domain with id " + domainId);
        	}
        	accountId = account.getId();
        	domainId = null;
        }
        else if (domainId == null) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "please specify either accountId or domainId for limit creation ");
        }
        
        // Map resource types
        ResourceType resourceType;
        try {
        	resourceType = ResourceType.values()[type];
        } catch (ArrayIndexOutOfBoundsException e) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "Please specify a valid resource type.");
        }
        
        ResourceLimitVO limit = null;
        try {
            limit = getManagementServer().createResourceLimit(domainId, accountId, resourceType, max);
        } catch (InvalidParameterValueException paramException) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, paramException.getMessage());
        } catch (Exception ex) {
            s_logger.error("Exception creating resource limit", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create limit due to exception: " + ex.getMessage());
        }
        List<Pair<String, Object>> embeddedObject = new ArrayList<Pair<String, Object>>();
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        
        if (limit == null)
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create resource limit. Please contact VMOps Support.");
        else {
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), limit.getId()));
        	
        	if (limit.getDomainId() != null) {
        		returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), limit.getDomainId()));
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(limit.getDomainId()).getName()));
        	}
        	
        	if (limit.getAccountId() != null) {
        		Account accountTemp = getManagementServer().findAccountById(limit.getAccountId());
                if (accountTemp != null) {
                	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), accountTemp.getAccountName()));
                	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), accountTemp.getDomainId()));
                	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(accountTemp.getDomainId()).getName()));
                }
        	}
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.TYPE.getName(), limit.getType()));
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.MAX.getName(), limit.getMax()));
        	embeddedObject.add(new Pair<String, Object>("resourcelimit", new Object[] { returnValues } ));
        }
        return embeddedObject;
    }
}
