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
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.VolumeVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ListSnapshotPoliciesCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListSnapshotPoliciesCmd.class.getName());

    private static final String s_name = "listsnapshotpoliciesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VOLUME_ID, Boolean.TRUE));
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
        Long volumeId = (Long)params.get(BaseCmd.Properties.VOLUME_ID.getName());
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        Long accountId = null;

        // if an admin account was passed in, or no account was passed in, make sure we honor the accountName/domainId parameters
        if ((account == null) || isAdmin(account.getType())) {
            // validate domainId before proceeding
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list snapshot policies.");
                }
                if (accountName != null) {
                    Account userAccount = getManagementServer().findAccountByName(accountName, domainId);
                    if (userAccount != null) {
                        accountId = userAccount.getId();
                    } else {
                        throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                }
            }
        } else {
            accountId = account.getId();
        }

        // Verify that a volume exists with the specified volume ID
        VolumeVO volume = getManagementServer().findVolumeById(volumeId);
        if (volume == null) {
            throw new ServerApiException (BaseCmd.PARAM_ERROR, "Unable to find a volume with id " + volumeId);
        }

        if ((accountId != null) && (volume.getAccountId() != accountId.longValue())) {
            throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "unable to list snapshot policies for volume: " + volumeId);
        }

        List<SnapshotPolicyVO> polices = getManagementServer().listSnapshotPolicies(volumeId);

        List<Pair<String, Object>> policesTags = new ArrayList<Pair<String, Object>>();
        Object[] policyTag = new Object[polices.size()];
        int i = 0;
        for (SnapshotPolicyVO policy : polices) {
            List<Pair<String, Object>> policyData = new ArrayList<Pair<String, Object>>();
            policyData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), policy.getId().toString()));
            //ToDo: get volumeId from SnapshotPolicyVO
            policyData.add(new Pair<String, Object>(BaseCmd.Properties.VOLUME_ID.getName(), volumeId));
            policyData.add(new Pair<String, Object>(BaseCmd.Properties.SCHEDULE.getName(), policy.getSchedule()));
            policyData.add(new Pair<String, Object>(BaseCmd.Properties.INTERVAL.getName(), policy.getInterval()));
            policyData.add(new Pair<String, Object>(BaseCmd.Properties.MAX.getName(), policy.getMaxSnaps()));
            policyTag[i++] = policyData;
        }
        Pair<String, Object> eventTag = new Pair<String, Object>("snapshotpolicy", policyTag);
        policesTags.add(eventTag);
        return policesTags;
        
    }
}
