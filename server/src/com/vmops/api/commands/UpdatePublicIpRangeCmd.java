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
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.utils.Pair;

public class UpdatePublicIpRangeCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdatePublicIpRangeCmd.class.getName());

    private static final String s_name = "updatepubliciprangeresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ADD, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VLAN_DB_ID, Boolean.TRUE));
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
        boolean add = (Boolean) params.get(BaseCmd.Properties.ADD.getName());
        Long vlanDbId = (Long) params.get(BaseCmd.Properties.VLAN_DB_ID.getName());
        String startIp = (String) params.get(BaseCmd.Properties.START_IP.getName());
        String endIp = (String) params.get(BaseCmd.Properties.END_IP.getName());

        String msg = null;
        try {
        	msg = getManagementServer().changePublicIPRange(add, vlanDbId, startIp, endIp);
        } catch (InvalidParameterValueException ex) {
        	throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        
        if (msg == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Unable to change public IP range");
        } else {
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SUCCESS.getName(), "true"));
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), msg));
        }
        
        return returnValues;
    }
}
