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
import com.vmops.utils.Pair;

public class UpdateZoneCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateZoneCmd.class.getName());

    private static final String s_name = "updatezoneresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DNS1, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DNS2, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DNS3, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DNS4, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VNET, Boolean.FALSE));
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
    	Long zoneId = (Long) params.get(BaseCmd.Properties.ID.getName());
    	String zoneName = (String) params.get(BaseCmd.Properties.NAME.getName());
    	String dns1 = (String) params.get(BaseCmd.Properties.DNS1.getName());
    	String dns2 = (String) params.get(BaseCmd.Properties.DNS2.getName());
    	String dns3 = (String) params.get(BaseCmd.Properties.DNS3.getName());
    	String dns4 = (String) params.get(BaseCmd.Properties.DNS4.getName());
    	String vnet = (String) params.get(BaseCmd.Properties.VNET.getName());
    	
    	//verify input parameters
    	DataCenterVO zone = getManagementServer().findDataCenterById(zoneId);
    	if (zone == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find zone by id " + zoneId);
    	}
    	
    	if (zoneName == null) {
    		zoneName = zone.getName();
    	}

    	DataCenterVO updatedZone = null;
    	
        try {
             updatedZone = getManagementServer().editZone(zoneId, zoneName, dns1, dns2, dns3, dns4, vnet);
        } catch (Exception ex) {
            s_logger.error("Exception updating zone", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        
        if (updatedZone == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update zone; internal error.");
        } else {
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), "true"));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), "Successfully updated zone."));
        }
        
        return returnValues;
    }
}
