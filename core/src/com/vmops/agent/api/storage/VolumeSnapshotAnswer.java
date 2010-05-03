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

package com.vmops.agent.api.storage;

import java.util.List;

import com.vmops.agent.api.Answer;

public class VolumeSnapshotAnswer extends Answer {
    private String _snapshotName = null;
    private List<Long> _volumeIds = null;
    private short _snapshotType;

    protected VolumeSnapshotAnswer() {
    }

    public VolumeSnapshotAnswer(VolumeSnapshotCommand cmd, short snapshotType, String snapshotName, List<Long> volumeIds, boolean success, String result) {
        super(cmd, success, result);
        _snapshotType = snapshotType;
        _volumeIds = volumeIds;
        _snapshotName = snapshotName;
    }

    public String getSnapshotName() {
        return _snapshotName;
    }

    public List<Long> getVolumeIds() {
        return _volumeIds;
    }

    public short getSnapshotType() {
        return _snapshotType;
    }
}
