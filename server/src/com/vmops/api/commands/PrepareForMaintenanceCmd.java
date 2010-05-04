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

public class PrepareForMaintenanceCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(PrepareForMaintenanceCmd.class.getName());
	
    private static final String s_name = "preparehostformaintenanceresponse";
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
        Long hostId = (Long)params.get(BaseCmd.Properties.ID.getName());
        
        //verify input parameters
    	HostVO host = getManagementServer().getHostBy(hostId);
    	if (host == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "Host with id " + hostId.toString() + " doesn't exist");
    	}
        
        long jobId = getManagementServer().prepareForMaintenanceAsync(hostId);
        
        if(jobId == 0) {
        	s_logger.warn("Unable to schedule async-job for PrepareForMaintenance comamnd");
        } else {
	        if(s_logger.isDebugEnabled())
	        	s_logger.debug("PrepareForMaintenance command has been accepted, job id: " + jobId);
        }
        
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
        return returnValues;
    }
}
