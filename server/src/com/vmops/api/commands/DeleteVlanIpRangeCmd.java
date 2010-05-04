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
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.dc.VlanVO;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.utils.Pair;

public class DeleteVlanIpRangeCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(DeleteVlanIpRangeCmd.class.getName());

    private static final String s_name = "deletevlaniprangeresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Long vlanDbId = (Long) params.get(BaseCmd.Properties.ID.getName());
    	String startIp = null;
    	String endIp = null;
		
		//Verify input parameters
    	VlanVO vlan = getManagementServer().findVlanById(vlanDbId);
    	if (vlan == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find vlan with id " + vlanDbId);
    	}
    	
    	String vlanDescription = vlan.getDescription();
    	if (vlanDescription != null) {
    		StringTokenizer st = new StringTokenizer(vlan.getDescription(), "-");
    		int count = st.countTokens();
    		if (count < 1) {
    			throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Can't get start ip address from vlan description");
    		}
    		startIp = st.nextToken();
    		if (count == 2) {
    			endIp = st.nextToken();
    		}

    		try {
    			getManagementServer().changePublicIPRange(false, vlan.getId(), startIp, endIp);
    		} catch (InvalidParameterValueException e) {
    			throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Unable to delete public IP range");
    		}
    	}
    	
    	//delete vlan
        try {
             getManagementServer().deleteVlan(vlanDbId);
        } catch (InvalidParameterValueException ex) {
        	//rollback ip address deletion if vlan delete fails
        	if (vlanDescription != null) {
        		try {
        			getManagementServer().changePublicIPRange(true, vlan.getId(), startIp, endIp);
        		} catch (InvalidParameterValueException e) {
        			throw new ServerApiException (BaseCmd.INTERNAL_ERROR, "Unable to delete a vlan, and delete public ip addresses rollback failed");
        		}
        	}
            s_logger.error("Exception deleting VLAN", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to delete VLAN with ID " + vlanDbId + ":  internal error.");
        }
    	 
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), "true"));
        return returnValues;
    }
}
