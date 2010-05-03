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

public class ManageSnapshotAnswer extends Answer {
    private long _snapshotId = -1;
    
    // For create Snapshot
    private String _snapshotPath;
    private List<Long> _policyIds = null;
    
    public ManageSnapshotAnswer() {}

    public ManageSnapshotAnswer(Command cmd, long snapshotId, boolean success, String result) {
        super(cmd, success, result);
        _snapshotId = snapshotId;
    }
    
    // For XenServer
    public ManageSnapshotAnswer(ManageSnapshotCommand cmd, long snapshotId, String snapshotPath, boolean success, String result) {
    	super(cmd, success, result);
    	_snapshotId = snapshotId;
    	_snapshotPath = snapshotPath;
    	_policyIds = cmd.getPolicyIds();
    }

    public long getSnapshotId() {
        return _snapshotId;
    }
    
    public String getSnapshotPath() {
    	return _snapshotPath;
    }
    
    public List<Long> getPolicyIds() {
        return _policyIds;
    }
}
