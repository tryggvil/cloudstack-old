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
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.utils.Pair;

public class AddHostCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(AddHostCmd.class.getName());
    private static final String s_name = "addhostresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.POD_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.URL, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USERNAME, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PASSWORD, Boolean.TRUE));
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
        Long zoneId = (Long)params.get(BaseCmd.Properties.ZONE_ID.getName());
        Long podId = (Long)params.get(BaseCmd.Properties.POD_ID.getName());
        String url = (String)params.get(BaseCmd.Properties.URL.getName());
        String username = (String)params.get(BaseCmd.Properties.USERNAME.getName());
        String password = (String)params.get(BaseCmd.Properties.PASSWORD.getName());
        
        //Check if the zone exists in the system
        if (getManagementServer().findDataCenterById(zoneId) == null ){
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "Can't find zone by id " + zoneId);
        }

        
        //Check if the pod exists in the system
        if (podId != null) {
            if (getManagementServer().findHostPodById(podId) == null ){
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "Can't find pod by id " + podId);
            }
            //check if pod belongs to the zone
            HostPodVO pod = getManagementServer().findHostPodById(podId);
            if (!Long.valueOf(pod.getDataCenterId()).equals(zoneId)) {
            	throw new ServerApiException(BaseCmd.PARAM_ERROR, "Pod " + podId + " doesn't belong to the zone " + zoneId);
            }
        }
        
        boolean success = false;
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        try {
        	List<? extends Host> h = getManagementServer().discoverHosts(zoneId, podId, url, username, password);
        	success = !h.isEmpty();
        } catch (Exception ex) {
        	s_logger.error("Failed to add host: ", ex);
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }
    	
        
        if (success) {
           returnValues.add(new Pair<String,Object> (BaseCmd.Properties.SUCCESS.getName(), Boolean.valueOf(success).toString()));
        } else {
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Can't connect host with url " + url);
        }
        return returnValues;
    }
}
