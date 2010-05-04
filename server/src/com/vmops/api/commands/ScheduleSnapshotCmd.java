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
import com.vmops.storage.VolumeVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ScheduleSnapshotCmd extends BaseCmd {
    private static final Logger s_logger = Logger.getLogger(ScheduleSnapshotCmd.class.getName());

    private static final String s_name = "schedulerecurringsnapshotsresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VOLUME_ID, Boolean.TRUE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.USER_ID, Boolean.FALSE));        
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.HOURLY_MAX, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DAILY_MAX, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.WEEKLY_MAX, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.MONTHLY_MAX, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long volumeId = (Long)params.get(BaseCmd.Properties.VOLUME_ID.getName());
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long userId = (Long)params.get(BaseCmd.Properties.USER_ID.getName());
        Integer hourlyMaxObj = (Integer)params.get(BaseCmd.Properties.HOURLY_MAX.getName());
        Integer dailyMaxObj = (Integer)params.get(BaseCmd.Properties.DAILY_MAX.getName());
        Integer weeklyMaxObj = (Integer)params.get(BaseCmd.Properties.WEEKLY_MAX.getName());
        Integer monthlyMaxObj = (Integer)params.get(BaseCmd.Properties.MONTHLY_MAX.getName());

        int hourlyMax = ((hourlyMaxObj == null) ? 0 : hourlyMaxObj.intValue());
        int dailyMax = ((dailyMaxObj == null) ? 0 : dailyMaxObj.intValue());
        int weeklyMax = ((weeklyMaxObj == null) ? 0 : weeklyMaxObj.intValue());
        int monthlyMax = ((monthlyMaxObj == null) ? 0 : monthlyMaxObj.intValue());

        //Verify input parameters
        // Verify that a volume exists with a specified volume ID
        VolumeVO volume = getManagementServer().findVolumeById(volumeId);
        if (volume == null) {
            throw new ServerApiException (BaseCmd.PARAM_ERROR, "Unable to find a volume with id " + volumeId);
        }

        if (account != null) {
            if (!isAdmin(account.getType()) && (account.getId().longValue() != volume.getAccountId())) {
                throw new ServerApiException(BaseCmd.VM_INVALID_PARAM_ERROR, "unable to find a volume with id " + volumeId + " for this account");
            } else if (!getManagementServer().isChildDomain(account.getDomainId(), volume.getDomainId())) {
                throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid volume id (" + volumeId + ") given, unable to schedule snapshots.");
            }
        }

        // If command is executed via 8096 port, set userId to the id of System account (1)
        if (userId == null) {
            userId = Long.valueOf(1);
        }

        try {
            long jobId = getManagementServer().scheduleRecurringSnapshotsAsync(userId, volumeId, hourlyMax, dailyMax, weeklyMax, monthlyMax);
            if (jobId == 0) {
            	s_logger.warn("Unable to schedule async-job for ScheduleRecurringSnapshots comamnd");
            } else {
    	        if(s_logger.isDebugEnabled())
    	        	s_logger.debug("ScheduleRecurringSnapshots command has been accepted, job id: " + jobId);
            }

            List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
            returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), Long.valueOf(jobId))); 
            return returnValues;
        } catch (Exception ex) {
            if (ex instanceof ServerApiException) {
                throw ((ServerApiException)ex);
            } else {
                s_logger.warn("Unexpected exception scheduling recurring snapshots for volume with id " + volumeId, ex);
                throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "unable to create a snapshot for volume with id " + volumeId);
            }
        }
    }
}
