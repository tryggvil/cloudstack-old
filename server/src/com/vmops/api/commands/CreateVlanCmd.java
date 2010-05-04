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
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.VlanVO;
import com.vmops.utils.Pair;

public class CreateVlanCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(CreateVlanCmd.class.getName());

    private static final String s_name = "createvlanresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VLAN, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DESCRIPTION, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.GATEWAY, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NETMASK, Boolean.TRUE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Long zoneId = (Long) params.get(BaseCmd.Properties.ZONE_ID.getName());
    	String vlanId = (String) params.get(BaseCmd.Properties.VLAN.getName());
    	String description = (String) params.get(BaseCmd.Properties.DESCRIPTION.getName());
    	String vlanGateway = (String) params.get(BaseCmd.Properties.GATEWAY.getName());
    	String vlanNetmask = (String) params.get(BaseCmd.Properties.NETMASK.getName());
    	
    	//Verify input parameters
    	DataCenterVO zone = getManagementServer().findDataCenterById(zoneId);
        if (zone == null) {
        	throw new ServerApiException (BaseCmd.PARAM_ERROR, "Zone with id " + zoneId + " doesn't exist in the system");
        }
        
        // If a vlan ID was not specified, default to untagged
        if (vlanId == null){
        	vlanId = "untagged";
        }

    	VlanVO result = null;
        try {
             result = getManagementServer().createVlan(zoneId, vlanId, vlanGateway, vlanNetmask, description, null);
        } catch (Exception ex) {
            s_logger.error("Exception creating VLAN", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create vlan " + vlanId + ":  internal error.");
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        if (result == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create vlan " + vlanId + ":  internal error.");
        } else {
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), result.getId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VLAN.getName(), result.getVlanId()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DESCRIPTION.getName(), result.getDescription()));
        }
        return returnValues;
    }
}
