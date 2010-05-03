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
import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.Pair;

public class RegisterTemplateCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(RegisterTemplateCmd.class.getName());

    private static final String s_name = "registertemplateresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DISPLAY_TEXT, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.URL, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.BITS, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PASSWORD_ENABLED, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.REQUIRES_HVM, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_PUBLIC, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.FORMAT, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.OS_TYPE_ID, Boolean.TRUE));
        
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
        //Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
        String description = (String)params.get(BaseCmd.Properties.DISPLAY_TEXT.getName());
        String name = (String)params.get(BaseCmd.Properties.NAME.getName());
        Integer bits = (Integer)params.get(BaseCmd.Properties.BITS.getName());
        Boolean passwordEnabled = (Boolean)params.get(BaseCmd.Properties.PASSWORD_ENABLED.getName());
        Boolean requiresHVM = (Boolean)params.get(BaseCmd.Properties.REQUIRES_HVM.getName());
        String url = (String)params.get(BaseCmd.Properties.URL.getName());
        Boolean isPublic = (Boolean)params.get(BaseCmd.Properties.IS_PUBLIC.getName());
        String format = (String)params.get(BaseCmd.Properties.FORMAT.getName());
        Long guestOSId = (Long) params.get(BaseCmd.Properties.OS_TYPE_ID.getName());

        //parameters verification
        if (bits == null) {
            bits = Integer.valueOf(64);
        }
        if (passwordEnabled == null) {
            passwordEnabled = false;
        }
        if (requiresHVM == null) {
            requiresHVM = true;
        }
        if (isPublic == null) {
            isPublic = true;
        }

        //If command is executed via 8096 port, set userId to the id of System account (1)
        if (userId == null) {
            userId = Long.valueOf(1);
        }

        Long templateId;
        try {
        	templateId = getManagementServer().createTemplate(userId, name, isPublic, format, "ext3", url, null, requiresHVM, bits, passwordEnabled, guestOSId, true);
        } catch (Exception ex) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, ex.getMessage());
        }
        	
    	VMTemplateVO template = getManagementServer().findTemplateById(templateId);
    	if (template != null) {
    		List<Pair<String, Object>> templateData = new ArrayList<Pair<String, Object>>();
    		
    		// TODO : when UI has complated migration, remove following block
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), template.getId().toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), template.getName()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), template.getDisplayText()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_PUBLIC.getName(), Boolean.valueOf(template.isPublicTemplate()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.REQUIRES_HVM.getName(), new Boolean(template.requiresHvm()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.BITS.getName(), Integer.valueOf(template.getBits()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(template.getCreated())));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_READY.getName(), Boolean.valueOf(template.isReady()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.PASSWORD_ENABLED.getName(), Boolean.valueOf(template.getEnablePassword()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.FORMAT.getName(), template.getFormat().toString()));

            // Use embeded object for response
            List<Pair<String, Object>> listForEmbeddedObject = new ArrayList<Pair<String, Object>>();
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), template.getId().toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), template.getName()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), template.getDisplayText()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.IS_PUBLIC.getName(), Boolean.valueOf(template.isPublicTemplate()).toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.REQUIRES_HVM.getName(), new Boolean(template.requiresHvm()).toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.BITS.getName(), Integer.valueOf(template.getBits()).toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(template.getCreated())));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.IS_READY.getName(), Boolean.valueOf(template.isReady()).toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.PASSWORD_ENABLED.getName(), Boolean.valueOf(template.getEnablePassword()).toString()));
            listForEmbeddedObject.add(new Pair<String, Object>(BaseCmd.Properties.FORMAT.getName(), template.getFormat().toString()));
            
            templateData.add(new Pair<String, Object>("template", new Object[] { listForEmbeddedObject } ));
            return templateData;
    	}
    	
		throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "internal error registering template");
    }
    
}
