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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.exception.InsufficientAddressCapacityException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.network.IPAddressVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class AssociateIPAddrCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(AssociateIPAddrCmd.class.getName());

    private static final String s_name = "associateipaddressresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.TRUE));
    }

    public String getName() {
        return s_name;
    }
    
    public static String getResultObjectName() {
    	return "addressinfo";
    }
    
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Long zoneId = (Long)params.get(BaseCmd.Properties.ZONE_ID.getName());
    	Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        String newIpAddr = null;
        String errorDesc = null;
        Long accountId = null;
        boolean isAdmin = false;

    	if (account != null) {
            accountId = account.getId();
            domainId = account.getDomainId();
            if (isAdmin(account.getType()))
            	isAdmin = true;  
        } else {
        	isAdmin = true;
        	if (accountName != null) {
        		if (domainId == null) {
        			domainId = Long.valueOf(1);
        		}
                account = getManagementServer().findActiveAccount(accountName, domainId);
                if (account != null) {
                    accountId = account.getId();
                }
                else {
                	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "could not find account " + accountName + " in domain " + domainId);
                }
	        }else {
	        	throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "no account is specified");
	        }
        }

        if (userId == null) {
            userId = Long.valueOf(1);
        }
        
        try {
            newIpAddr = getManagementServer().associateIpAddress(userId.longValue(), accountId.longValue(), domainId.longValue(), zoneId.longValue());
        } catch (ResourceAllocationException rae) {
        	if (rae.getResourceType().equals("vm")) throw new ServerApiException (BaseCmd.VM_ALLOCATION_ERROR, rae.getMessage());
        	else if (rae.getResourceType().equals("ip")) throw new ServerApiException (BaseCmd.IP_ALLOCATION_ERROR, rae.getMessage());
        } catch (InvalidParameterValueException ex1) {
            s_logger.error("error associated IP Address with userId: " + userId, ex1);
            throw new ServerApiException (BaseCmd.NET_INVALID_PARAM_ERROR, ex1.getMessage());
        } catch (InsufficientAddressCapacityException ex2) {
        	throw new ServerApiException (BaseCmd.NET_IP_ASSOC_ERROR, ex2.getMessage());
        } catch (InternalErrorException ex3){
        	throw new ServerApiException (BaseCmd.NET_IP_ASSOC_ERROR, ex3.getMessage());
        } catch (Exception ex4) {
        	throw new ServerApiException (BaseCmd.NET_IP_ASSOC_ERROR, "Unable to associate IP address");
        }
        
        
        if (newIpAddr == null) {
            s_logger.warn("unable to associate IP address for user " + ((errorDesc != null) ? (", reason: " + errorDesc) : null));
            throw new ServerApiException(BaseCmd.NET_IP_ASSOC_ERROR, "unable to associate IP address for user " + ((errorDesc != null) ? (", reason: " + errorDesc) : null));
        }
        List<Pair<String, Object>> embeddedObject = new ArrayList<Pair<String, Object>>();
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        
        try {
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.IP_ADDRESS.getName(), newIpAddr));
        	List<IPAddressVO> ipAddresses = getManagementServer().listPublicIpAddressesBy(accountId.longValue(), true, null, null);
        	IPAddressVO ipAddress = null;
        	for (Iterator<IPAddressVO> iter = ipAddresses.iterator(); iter.hasNext();) {
        		IPAddressVO current = iter.next();
        		if (current.getAddress().equals(newIpAddr)) {
        			ipAddress = current;
        			break;
        		}
        	}
        	if (ipAddress == null) {
        		return returnValues;
        	}
            if (ipAddress.getAllocated() != null) {
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ALLOCATED.getName(), getDateString(ipAddress.getAllocated())));
            }
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), Long.valueOf(ipAddress.getDataCenterId()).toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_NAME.getName(), getManagementServer().findDataCenterById(ipAddress.getDataCenterId()).getName()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.IS_SOURCE_NAT.getName(), Boolean.valueOf(ipAddress.isSourceNat()).toString()));
            //get account information
            Account accountTemp = getManagementServer().findAccountById(ipAddress.getAccountId());
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), accountTemp.getAccountName()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), accountTemp.getDomainId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(accountTemp.getDomainId()).getName()));
            //show this info to admin only
            if (isAdmin == true) {
            	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VLAN_DB_ID.getName(), Long.valueOf(ipAddress.getVlanDbId()).toString()));
                returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VLAN_ID.getName(), getManagementServer().findVlanById(ipAddress.getVlanDbId()).getVlanId()));
            }
            embeddedObject.add(new Pair<String, Object>("publicipaddress", new Object[] { returnValues } ));
        } catch (Exception ex) {
            s_logger.error("error!", ex);
        }
        return embeddedObject;
    }
}
