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
import com.vmops.host.HostVO;
import com.vmops.utils.Pair;

public class UpdateHostCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateHostCmd.class.getName());
    private static final String s_name = "updatehostresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.OS_CATEGORY_ID, Boolean.FALSE));
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
        Long id = (Long)params.get(BaseCmd.Properties.ID.getName());
        Long guestOSCategoryId = (Long)params.get(BaseCmd.Properties.OS_CATEGORY_ID.getName());
        
        if (guestOSCategoryId == null) {
        	guestOSCategoryId = new Long(-1);
        } 
        
        // Verify that the host exists
    	HostVO host = getManagementServer().getHostBy(id);
    	if (host == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "Host with id " + id.toString() + " doesn't exist");
    	}
       
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        try {
        	getManagementServer().updateHost(id, guestOSCategoryId);
        } catch (Exception ex) {
        	s_logger.error("Failed to update host: ", ex);
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update host: " + ex.getMessage());
        }
    	
        returnValues.add(new Pair<String,Object> (BaseCmd.Properties.SUCCESS.getName(), "true"));
        return returnValues;
    }
}
