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
import com.vmops.utils.Pair;

public class DeleteResourceLimitCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(DeleteResourceLimitCmd.class.getName());
    private static final String s_name = "deleteresourcelimitresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
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
    	Long limitId = (Long) params.get(BaseCmd.Properties.ID.getName());
    	
    	//Verify input parameters
    	ResourceLimitVO limit = getManagementServer().findLimitById(limitId);
    	if (limit == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find resource limit by id  " + limitId);
    	}

		boolean success = false;
		try {
			success = getManagementServer().deleteLimit(limitId);
		} catch (Exception ex) {
			s_logger.error("Exception deleting limit", ex);
			throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to delete resource limit due to exception: " + ex.getMessage());
		}

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();

        if (success)
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), new Boolean(true)));
        else
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to delete resource limit. Please verify that a resource limit with the specified ID exists.");

        return returnValues;
    }
}
