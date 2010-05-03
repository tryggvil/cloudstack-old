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

package com.vmops.agent.api;

import java.util.List;

public class ManageSnapshotCommand extends Command {
    public static String CREATE_SNAPSHOT = "-c";
    public static String DESTROY_SNAPSHOT = "-d";
    public static String ROLLBACK_SNAPSHOT = "-r";

    private String _commandSwitch;
    
    // Information about the volume that the snapshot is based on
    private String _volumeFolder;
    private String _volumePath;
    private String _volumeName;
    private String _volumeNameLabel;
    
    // Information about the snapshot
    private String _snapshotPath;
    private String _snapshotName;
    private long _snapshotId;

    // Information about the recurring policies for which the snapshot was taken
    private List<Long> _policyIds;
    
    public ManageSnapshotCommand() {}

    public ManageSnapshotCommand(String commandSwitch, long snapshotId, String volumePath, String snapshotName) {
        _commandSwitch = commandSwitch;
        _volumePath = volumePath;
        _snapshotName = snapshotName;
        _snapshotId = snapshotId;
    }

    // For XenServer, CreateSnapshot
    public ManageSnapshotCommand(String commandSwitch, long snapshotId, String volumeFolder, String volumePath, String volumeNameLabel, String snapshotName, List<Long> policyIds) {
        _commandSwitch = commandSwitch;
        _volumeFolder = volumeFolder;
        _volumePath = volumePath;
        _volumeNameLabel = volumeNameLabel;
        _snapshotName = snapshotName;
        _snapshotId = snapshotId;
        _policyIds  = policyIds;
    }
    
    // For XenServer, DestroySnapshot
    public ManageSnapshotCommand(String commandSwitch, long snapshotId, String volumeFolder, String snapshotPath, String snapshotName) {
        _commandSwitch = commandSwitch;
        _volumeFolder = volumeFolder;
        _snapshotPath = snapshotPath;
        _snapshotName = snapshotName;
        _snapshotId = snapshotId;
    }

    @Override
    public boolean executeInSequence() {
        return false;
    }

    public String getCommandSwitch() {
        return _commandSwitch;
    }
    
    public String getVolumeFolder() {
    	return _volumeFolder;
    }

    public String getVolumePath() {
        return _volumePath;
    }
    
    public String getVolumeName() {
    	return  _volumeName;
    }
    
    public String getVolumeNameLabel() {
    	return _volumeNameLabel;
    }
    
    public String getSnapshotPath() {
    	return _snapshotPath;
    }

    public String getSnapshotName() {
        return _snapshotName;
    }

    public long getSnapshotId() {
        return _snapshotId;
    }
    
    public List<Long> getPolicyIds() {
        return _policyIds;
    }
}