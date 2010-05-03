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
import com.vmops.service.ServiceOfferingVO;
import com.vmops.utils.Pair;

public class CreateServiceOfferingCmd extends BaseCmd{
	public static final Logger s_logger = Logger.getLogger(CreateServiceOfferingCmd.class.getName());
	private static final String _name = "createserviceofferingresponse";
	private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();
	
	static {
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.TRUE));
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DISPLAY_TEXT, Boolean.TRUE));
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.CPU_NUMBER, Boolean.TRUE));
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.CPU_SPEED, Boolean.TRUE));
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.MEMORY, Boolean.TRUE));
		s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.STORAGE_TYPE, Boolean.FALSE));
	}
	
	public String getName() {
		return _name;
	}
	
	public List<Pair<Enum, Boolean>> getProperties (){
		return s_properties;
	}
	
	
	@Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
	    // FIXME: add domain-private service offerings
//        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
//        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
//        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
		String name = (String)params.get(BaseCmd.Properties.NAME.getName());
		String displayText = (String)params.get(BaseCmd.Properties.DISPLAY_TEXT.getName());
		Long cpuNumber = (Long)params.get(BaseCmd.Properties.CPU_NUMBER.getName());
		Long cpuSpeed = (Long)params.get(BaseCmd.Properties.CPU_SPEED.getName());
		Long memory = (Long)params.get(BaseCmd.Properties.MEMORY.getName());
		String storageType = (String) params.get(BaseCmd.Properties.STORAGE_TYPE.getName());

		Long serviceOfferingResponse = null;

		if (name.length() == 0) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Failed to create service offering: specify the name that has non-zero length");
		}

		if (displayText.length() == 0) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Failed to create service offering: specify the display text that has non-zero length");
		}

		if ((cpuNumber.intValue() <= 0) || (cpuNumber.intValue() > 2147483647)) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Failed to create service offering: specify the cpu number value between 1 and 2147483647");
		}

		if ((cpuSpeed.intValue() <= 0) || (cpuSpeed.intValue() > 2147483647)) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Failed to create service offering: specify the cpu speed value between 1 and 2147483647");
		}

		if ((memory.intValue() <= 0) || (memory.intValue() > 2147483647)) {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Failed to create service offering: specify the memory value between 1 and 2147483647");
		}
		
		boolean localStorageRequired;
		if (storageType == null) {
			localStorageRequired = false;
		} else if (storageType.equals("local")) {
			localStorageRequired = true;
		} else if (storageType.equals("shared")) {
			localStorageRequired = false;
		} else {
			throw new ServerApiException(BaseCmd.PARAM_ERROR, "Valid pool types are: 'local' and 'shared'");
		}

		ServiceOfferingVO offering = null;
		try {
			serviceOfferingResponse = getManagementServer().createServiceOffering(null, name, cpuNumber.intValue(), memory.intValue(), cpuSpeed.intValue(), displayText, localStorageRequired);
			
			if (serviceOfferingResponse != null) {
				offering = getManagementServer().findServiceOfferingById(serviceOfferingResponse);
			}
		} catch (Exception ex) {
			s_logger.error("Exception creating service offering", ex);
	        throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create service offering " + name + ":  internal error.");
		} 

		List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        if (serviceOfferingResponse == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create service offering " + name);
        } else {
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), serviceOfferingResponse.toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), offering.getName()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), offering.getDisplayText()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.CPU_NUMBER.getName(), Integer.valueOf(offering.getCpu()).toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.CPU_SPEED.getName(), Integer.valueOf(offering.getSpeed()).toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.MEMORY.getName(), Integer.valueOf(offering.getRamSize()).toString()));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(offering.getCreated())));
            storageType = offering.getUseLocalStorage() ? "local" : "shared";
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.STORAGE_TYPE.getName(), storageType));
        }
        return returnValues;
	}
}
