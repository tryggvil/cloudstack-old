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
import com.vmops.dc.VlanVO;
import com.vmops.server.Criteria;
import com.vmops.utils.Pair;

public class ListVlanRangesCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(ListVlanRangesCmd.class.getName());

    private static final String s_name = "listvlanrangesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VNET, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.FALSE));
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
    	Long vlan = (Long) params.get(BaseCmd.Properties.VNET.getName());
    	Long zoneId = (Long) params.get(BaseCmd.Properties.ZONE_ID.getName());
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
    	
    	//we need to be able to search by 
    	c.addCriteria(Criteria.ID, id);
    	c.addCriteria(Criteria.VLAN, vlan);
    	c.addCriteria(Criteria.DATACENTERID, zoneId);
    	
    	
    	 List<? extends VlanVO> vlans = null; // alex, your code comes here - we have to be able to search by id, vnet value, and zoneId...and probably accountId as well

         if (vlans == null) {
             throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "unable to find vlans");
         }
         
         Object[] vlanTag = new Object[vlans.size()];
         int i = 0;
         
         for (VlanVO vlanIn : vlans) {
             List<Pair<String, Object>> vlanData = new ArrayList<Pair<String, Object>>();
            
             //add vlan data in the response: id, vnet value, zoneId, allocated, accountName and domainId
             
             vlanTag[i++] = vlanData;
         }
         List<Pair<String, Object>> returnTags = new ArrayList<Pair<String, Object>>();
         Pair<String, Object> vlanTags = new Pair<String, Object>("vlanrange", vlanTag);
         returnTags.add(vlanTags);
         return returnTags;

    } 	
}
