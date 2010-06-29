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
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.storage.SnapshotPolicyVO;
import com.cloud.storage.VolumeVO;
import com.cloud.user.Account;
import com.cloud.utils.Pair;

public class CreateSnapshotPolicyCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(CreateSnapshotPolicyCmd.class.getName());

    private static final String s_name = "createsnapshotpolicyresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VOLUME_ID, Boolean.TRUE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.SCHEDULE, Boolean.TRUE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.TIMEZONE, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.INTERVAL_TYPE, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.MAX_SNAPS, Boolean.TRUE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
    	long volumeId = (Long)params.get(BaseCmd.Properties.VOLUME_ID.getName());
        String schedule = (String)params.get(BaseCmd.Properties.SCHEDULE.getName());
        String timezone = (String)params.get(BaseCmd.Properties.TIMEZONE.getName());
        String intervalType = (String)params.get(BaseCmd.Properties.INTERVAL_TYPE.getName());
        //ToDo: make maxSnaps optional. Use system wide max when not specified
        int maxSnaps = (Integer)params.get(BaseCmd.Properties.MAX_SNAPS.getName());
        
        
        // Verify that a volume exists with the specified volume ID
        VolumeVO volume = getManagementServer().findVolumeById(volumeId);
        if (volume == null) {
            throw new ServerApiException (BaseCmd.PARAM_ERROR, "Unable to find a volume with id " + volumeId);
        }

        if (account != null) {
            if (isAdmin(account.getType())) {
                if (!getManagementServer().isChildDomain(account.getDomainId(), volume.getDomainId())) {
                    throw new ServerApiException (BaseCmd.ACCOUNT_ERROR, "Unable to create a snapshot policy for volume with id " + volumeId + ", permission denied.");
                }
            } else if (account.getId().longValue() != volume.getAccountId()) {
                throw new ServerApiException (BaseCmd.ACCOUNT_ERROR, "Account " + account.getAccountName() + " does not own volume " + volumeId + ", unable to create a snapshot policy.");
            }
        }
        
        long accountId = volume.getAccountId();

        // If command is executed via 8096 port, set userId to the id of System account (1)
        if (userId == null) {
            userId = Long.valueOf(1);
        }

        SnapshotPolicyVO snapshotPolicy = null;
        try {
        	snapshotPolicy = getManagementServer().createSnapshotPolicy(accountId, userId, volumeId, schedule, intervalType, maxSnaps, timezone);
        } catch (InvalidParameterValueException ex) {
        	throw new ServerApiException (BaseCmd.VM_INVALID_PARAM_ERROR, ex.getMessage());
        }
        
        if (snapshotPolicy == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create Snapshot Policy");
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), snapshotPolicy.getId().toString()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VOLUME_ID.getName(), snapshotPolicy.getVolumeId()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SCHEDULE.getName(), snapshotPolicy.getSchedule()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.INTERVAL_TYPE.getName(), snapshotPolicy.getInterval()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.MAX_SNAPS.getName(), snapshotPolicy.getMaxSnaps()));

        return returnValues;
    }
}
