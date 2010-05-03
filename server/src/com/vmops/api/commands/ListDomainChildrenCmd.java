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
import com.vmops.domain.DomainVO;
import com.vmops.server.Criteria;
import com.vmops.utils.Pair;

public class ListDomainChildrenCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(ListDomainChildrenCmd.class.getName());
	
    private static final String s_name = "listdomainchildrenresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_RECURSIVE, Boolean.FALSE));
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
    	Long domainId = (Long)params.get(BaseCmd.Properties.ID.getName());
        String domainName = (String)params.get(BaseCmd.Properties.NAME.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
        Boolean isRecursive = (Boolean)params.get(BaseCmd.Properties.IS_RECURSIVE.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());

        Long startIndex = Long.valueOf(0);
        int pageSizeNum = 50;
    	if (pageSize != null) {
    		pageSizeNum = pageSize.intValue();
    	}
    	
        if (page != null) {
            int pageNum = page.intValue();
            if (pageNum > 0) {
                startIndex = Long.valueOf(pageSizeNum * (pageNum-1));
            }
        }
        
        Criteria c = new Criteria("id", Boolean.TRUE, startIndex, Long.valueOf(pageSizeNum));
        
        if (keyword != null) {
        	c.addCriteria(Criteria.KEYWORD, keyword);
        }
        else {
        	c.addCriteria(Criteria.ID, domainId);
            c.addCriteria(Criteria.NAME, domainName);
            c.addCriteria(Criteria.ISRECURSIVE, isRecursive);
        }
        
        // TODO : Recursive listing is not supported yet 
        List<DomainVO> domains = getManagementServer().searchForDomainChildren(c);
        
        List<Pair<String, Object>> domainTags = new ArrayList<Pair<String, Object>>();
        Object[] dTag = new Object[domains.size()];
        int i = 0;
        for (DomainVO domain : domains) {
            List<Pair<String, Object>> domainData = new ArrayList<Pair<String, Object>>();
            domainData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), Long.valueOf(domain.getId()).toString()));
            domainData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), domain.getName()));
            domainData.add(new Pair<String, Object>(BaseCmd.Properties.LEVEL.getName(), domain.getLevel().toString()));

            domainData.add(new Pair<String, Object>(BaseCmd.Properties.HAS_CHILD.getName(), 
        		(domain.getChildCount()) > 0 ? "true" : "false"));
            
            if (domain.getParent() != null){
            	domainData.add(new Pair<String, Object>(BaseCmd.Properties.PARENT_DOMAIN_ID.getName(), domain.getParent().toString()));
            }
            dTag[i++] = domainData;
        }
        Pair<String, Object> domainTag = new Pair<String, Object>("domain", dTag);
        domainTags.add(domainTag);
        return domainTags;
    }
}
