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
import com.vmops.storage.Storage;
import com.vmops.storage.VMTemplateVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class UpdateIsoCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(UpdateIsoCmd.class.getName());

    private static final String s_name = "updateisoresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DISPLAY_TEXT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
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
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String description = (String)params.get(BaseCmd.Properties.DISPLAY_TEXT.getName());
        String name = (String)params.get(BaseCmd.Properties.NAME.getName());
        Long isoId = (Long)params.get(BaseCmd.Properties.ID.getName());

        VMTemplateVO iso = getManagementServer().findTemplateById(isoId.longValue());
        if ((iso == null) || iso.getFormat() != Storage.ImageFormat.ISO) {
            throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find ISO with id " + isoId);
        }

        // do a permission check
        if (account != null) {
            Long isoOwner = iso.getAccountId();
            if (!isAdmin(account.getType())) {
                if ((isoOwner == null) || (account.getId().longValue() != isoOwner.longValue())) {
                    throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to modify ISO with id " + isoId);
                }
            } else if (account.getType() != Account.ACCOUNT_TYPE_ADMIN) {
                Long isoOwnerDomainId = getManagementServer().findDomainIdByAccountId(isoOwner);
                if (!getManagementServer().isChildDomain(account.getDomainId(), isoOwnerDomainId)) {
                    throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to modify ISO with id " + isoId);
                }
            }
        }

        if (name == null) {
            name = iso.getName();
        }
        if (description == null) {
            description = iso.getDisplayText();
        }

        // do the update
        getManagementServer().updateTemplate(isoId, name, description);

        VMTemplateVO updatedIso = getManagementServer().findTemplateById(isoId);
        if (updatedIso != null) {
            List<Pair<String, Object>> templateData = new ArrayList<Pair<String, Object>>();
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), updatedIso.getId().toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), updatedIso.getName()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), updatedIso.getDisplayText()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_PUBLIC.getName(), Boolean.valueOf(updatedIso.isPublicTemplate()).toString()));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(updatedIso.getCreated())));
            templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_READY.getName(), Boolean.valueOf(updatedIso.isReady()).toString()));
            return templateData;
        }
        else {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "internal error registering ISO");
        }
    }
}
