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
import com.vmops.service.ServiceOfferingVO;
import com.vmops.utils.Pair;

public class UpdateServiceOfferingCmd extends BaseCmd{
    public static final Logger s_logger = Logger.getLogger(UpdateServiceOfferingCmd.class.getName());
    private static final String s_name = "updateserviceofferingresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DISPLAY_TEXT, Boolean.FALSE));
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
        Long offeringId = (Long)params.get(BaseCmd.Properties.ID.getName());
        String name = (String)params.get(BaseCmd.Properties.NAME.getName());
        String displayText = (String)params.get(BaseCmd.Properties.DISPLAY_TEXT.getName());
        Boolean editSOResult = false;
        
        //Verify input parameters
        ServiceOfferingVO offering = getManagementServer().findServiceOfferingById(offeringId);
    	if (offering == null) {
    		throw new ServerApiException(BaseCmd.PARAM_ERROR, "unable to find service offering " + offeringId);
    	}

    	if (name == null) {
    		name = offering.getName();
    	}

    	if (displayText == null) {
    		displayText = offering.getDisplayText();
    	}

        try {     
        	getManagementServer().updateServiceOffering(offeringId, name, offering.getCpu(), offering.getRamSize(), offering.getSpeed(), displayText);
        	editSOResult = true;
        } catch (Exception ex) {
            s_logger.error("Exception updating service offering", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update service offering " + offeringId + ":  internal error.");
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        if (editSOResult == true) {
        	returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), Boolean.TRUE));
        } else {
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to update service offering " + offeringId);
        }
        return returnValues;
    }  
}
