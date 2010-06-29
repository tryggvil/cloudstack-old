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

package com.cloud.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cloud.api.BaseCmd;
import com.cloud.api.ServerApiException;
import com.cloud.dc.DataCenterIpAddressVO;
import com.cloud.utils.Pair;

public class ListPrivateIpAddressesCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListPrivateIpAddressesCmd.class.getName());

    private static final String s_name = "listprivateipaddressesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.POD_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.TRUE));
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
        Long podId = (Long) params.get(BaseCmd.Properties.POD_ID.getName());
        Long zoneId = (Long) params.get(BaseCmd.Properties.ZONE_ID.getName());
        List<DataCenterIpAddressVO> result = null;

        try {
            result = getManagementServer().listPrivateIpAddressesBy(podId, zoneId);
        } catch (Exception ex) {
        	throw new ServerApiException(BaseCmd.NET_LIST_ERROR, "unable to find private IP addresses in pod: " + podId + " and zone: " + zoneId + ".");
        }
        
        if (result == null) {
        	throw new ServerApiException(BaseCmd.NET_LIST_ERROR, "unable to find private IP addresses in pod: " + podId + " and zone: " + zoneId + ".");
        }
        
        List<Pair<String, Object>> ipAddrTags = new ArrayList<Pair<String, Object>>();
        Object[] ipTag = new Object[result.size()];
        int i = 0;
        for (DataCenterIpAddressVO ipAddress : result) {
            List<Pair<String, Object>> ipAddrData = new ArrayList<Pair<String, Object>>();
            ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.IP_ADDRESS.getName(), ipAddress.getIpAddress()));
            if (ipAddress.getTakenAt() != null) {
                ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.ALLOCATED.getName(), getDateString(ipAddress.getTakenAt())));
            }
            ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.POD_ID.getName(), Long.valueOf(ipAddress.getPodId()).toString()));
            ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.POD_NAME.getName(), getManagementServer().findHostPodById(ipAddress.getPodId()).getName()));
            ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), Long.valueOf(ipAddress.getDataCenterId()).toString()));
            ipAddrData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_NAME.getName(), getManagementServer().findDataCenterById(ipAddress.getDataCenterId()).getName()));

            ipTag[i++] = ipAddrData;
        }
        Pair<String, Object> ipAddrTag = new Pair<String, Object>("allocatedipaddress", ipTag);
        ipAddrTags.add(ipAddrTag);
        return ipAddrTags;
    }
}
