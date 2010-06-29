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
import com.cloud.dc.VlanVO;
import com.cloud.server.Criteria;
import com.cloud.utils.Pair;

public class ListVlanIpRangesCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(ListVlanIpRangesCmd.class.getName());

    private static final String s_name = "listvlaniprangesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VLAN, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGESIZE, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Long id = (Long) params.get(BaseCmd.Properties.ID.getName());
    	String vlan = (String) params.get(BaseCmd.Properties.VLAN.getName());
    	Long zoneId = (Long) params.get(BaseCmd.Properties.ZONE_ID.getName());
    	String name = (String) params.get(BaseCmd.Properties.NAME.getName());
    	String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
    	Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());
        
    	
    	 Long startIndex = Long.valueOf(0);
         int pageSizeNum = 50;
	     if (pageSize != null) 
	     		pageSizeNum = pageSize.intValue();
	     
         if (page != null) {
             int pageNum = page.intValue();
             if (pageNum > 0) {
                 startIndex = Long.valueOf(pageSizeNum * (pageNum-1));
             }
         }
    	Criteria c = new Criteria("id", Boolean.TRUE, startIndex, Long.valueOf(pageSizeNum));
    	
    	if (keyword != null) {
    		c.addCriteria(Criteria.KEYWORD, keyword);
    	}else {
    		c.addCriteria(Criteria.ID, id);
        	c.addCriteria(Criteria.VLAN, vlan);
        	c.addCriteria(Criteria.NAME, name);
        	c.addCriteria(Criteria.DATACENTERID, zoneId);
    	}
    	
    	 List<? extends VlanVO> vlans = getManagementServer().searchForVlans(c);

         if (vlans == null) {
             throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "unable to find vlans");
         }
         
         Object[] vlanTag = new Object[vlans.size()];
         int i = 0;
         
         for (VlanVO vlanIn : vlans) {
             List<Pair<String, Object>> vlanData = new ArrayList<Pair<String, Object>>();
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), vlanIn.getId()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.VLAN.getName(), vlanIn.getVlanId()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), vlanIn.getVlanName()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.DESCRIPTION.getName(), vlanIn.getDescription()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), vlanIn.getDataCenterId()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.GATEWAY.getName(), vlanIn.getVlanGateway()));
             vlanData.add(new Pair<String, Object>(BaseCmd.Properties.NETMASK.getName(), vlanIn.getVlanNetmask()));
             vlanTag[i++] = vlanData;
         }
         List<Pair<String, Object>> returnTags = new ArrayList<Pair<String, Object>>();
         Pair<String, Object> vlanTags = new Pair<String, Object>("vlaniprange", vlanTag);
         returnTags.add(vlanTags);
         return returnTags;

    } 	
}
