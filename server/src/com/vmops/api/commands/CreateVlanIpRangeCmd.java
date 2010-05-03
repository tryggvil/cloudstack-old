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
import com.vmops.dc.VlanVO;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.utils.Pair;

public class CreateVlanIpRangeCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(CreateVlanIpRangeCmd.class.getName());

    private static final String s_name = "createvlaniprangeresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.TRUE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VLAN, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.GATEWAY, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NETMASK, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.POD_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.START_IP, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.END_IP, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	String name = (String) params.get(BaseCmd.Properties.NAME.getName());
    	String vlanId = (String) params.get(BaseCmd.Properties.VLAN.getName());
    	String vlanGateway = (String) params.get(BaseCmd.Properties.GATEWAY.getName());
    	String vlanNetmask = (String) params.get(BaseCmd.Properties.NETMASK.getName());
    	Long zoneId = (Long) params.get(BaseCmd.Properties.ZONE_ID.getName());
    	Long podId = (Long) params.get(BaseCmd.Properties.POD_ID.getName());
    	String startIp = (String) params.get(BaseCmd.Properties.START_IP.getName());
    	String endIp = (String) params.get(BaseCmd.Properties.END_IP.getName());
    	String description = null;
    	
    	
    	//If vlanId is null, set it to untagged
    	if (vlanId == null) {
    		vlanId = "untagged";
    	}
    	
    	if ((endIp != null) && !endIp.isEmpty() && !(startIp.equalsIgnoreCase(endIp))) {
    		description = startIp + "-" + endIp;
    	}
    	else {
    		endIp = startIp;
    		description = startIp + "-" + endIp;
    	}
    	
    
    	//Verify input parameters
    	if (name.equals("Guest")) {
    		if (podId == null) {
    			throw new ServerApiException(BaseCmd.PARAM_ERROR, "PodId is required for Guest vlans");
    		}
    		else {
    	    	if (getManagementServer().findHostPodById(podId) == null) {
    	    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find pod by id " + podId);
    	    	}
    		}
    	}
    	else if (name.equals("Public")){
    		if (zoneId == null) {
    			throw new ServerApiException(BaseCmd.PARAM_ERROR, "ZoneId is required for Public vlan");
    		}
            if (getManagementServer().findDataCenterById(zoneId) == null) {
            	throw new ServerApiException (BaseCmd.PARAM_ERROR, "Zone with id " + zoneId + " doesn't exist in the system");
            }
    	}
    	else {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "Wrong type is specified; should have used one of the followning: Guest or Public");
    	}
     	
    	
    	VlanVO vlan = null;
		//add vlan without mapping to pod for Public type
		if (name.equals("Public")) {
			try {
    			vlan = getManagementServer().createVlan(zoneId, vlanId, vlanGateway, vlanNetmask, description, name);
    		} catch (InvalidParameterValueException e1) {
    			s_logger.error("Error adding vlan: ", e1);
    			throw new ServerApiException (BaseCmd.INTERNAL_ERROR, e1.getMessage());
    		}
		}
		else if (name.equals("Guest")) {
			//TBD - need to add vlan and map it to the pod
		}
		
		
		//add public ip addresses
		try {
        	getManagementServer().changePublicIPRange(true, vlan.getId(), startIp, endIp);
        } catch (InvalidParameterValueException e) {
        	//remove the vlan
        	try {
        		getManagementServer().deleteVlan(vlan.getId());
        	} catch (InvalidParameterValueException e2) {
        		throw new ServerApiException (BaseCmd.INTERNAL_ERROR, "Unable to add public IP range, and vlan add rollback failed");
        	}
        	s_logger.error("Error adding public ips: ", e);
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Unable to add public IP range");
        }
    	
    	
    	List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
    	if (vlan != null) {
    		returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), vlan.getId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VLAN.getName(), vlan.getVlanId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), vlan.getVlanName()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), vlan.getDataCenterId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.GATEWAY.getName(), vlan.getVlanGateway()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.NETMASK.getName(), vlan.getVlanNetmask()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DESCRIPTION.getName(), vlan.getDescription()));
    	}
    	returnValues.add ((new Pair<String, Object>(BaseCmd.Properties.START_IP.getName(), startIp)));
    	returnValues.add ((new Pair<String, Object>(BaseCmd.Properties.END_IP.getName(), endIp)));
        
        return returnValues;
    }
}
