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
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.VolumeVO;
import com.vmops.utils.Pair;

public class CreateSnapshotPolicyCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(CreateSnapshotPolicyCmd.class.getName());

    private static final String s_name = "createsnapshotpolicyresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VOLUME_ID, Boolean.TRUE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.SCHEDULE, Boolean.TRUE));
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
    	long volumeId = (Long)params.get(BaseCmd.Properties.VOLUME_ID.getName());
        String schedule = (String)params.get(BaseCmd.Properties.SCHEDULE.getName());
        String intervalType = (String)params.get(BaseCmd.Properties.INTERVAL_TYPE.getName());
        //ToDo: make maxSnaps optional. Use system wide max when not specified
        int maxSnaps = (Integer)params.get(BaseCmd.Properties.MAX_SNAPS.getName());
        
        // Verify that a volume exists with the specified volume ID
        VolumeVO volume = getManagementServer().findVolumeById(volumeId);
        if (volume == null) {
            throw new ServerApiException (BaseCmd.PARAM_ERROR, "Unable to find a volume with id " + volumeId);
        }
        
        
        SnapshotPolicyVO snapshotPolicy = null;
        try {
        	snapshotPolicy = getManagementServer().createSnapshotPolicy(volumeId, schedule, intervalType, maxSnaps);
        } catch (InvalidParameterValueException ex) {
        	throw new ServerApiException (BaseCmd.VM_INVALID_PARAM_ERROR, ex.getMessage());
        }
        
        if (snapshotPolicy == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to create Snapshot Policy");
        }

        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), snapshotPolicy.getId().toString()));
        //ToDo: get volumeId from SnapshotPolicyVO
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.VOLUME_ID.getName(), volumeId));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.SCHEDULE.getName(), snapshotPolicy.getSchedule()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.INTERVAL.getName(), snapshotPolicy.getInterval()));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.MAX.getName(), snapshotPolicy.getMaxSnaps()));

        return returnValues;
    }
}
