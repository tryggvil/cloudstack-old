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

package com.vmops.storage;

import java.util.Date;

import com.vmops.async.AsyncInstanceCreateStatus;

public interface Snapshot {
    public static final short TYPE_USER = 0;
    public static final short TYPE_HOURLY = 1;
    public static final short TYPE_DAILY = 2;
    public static final short TYPE_WEEKLY = 3;
    public static final short TYPE_MONTHLY = 4;
    public static final short TYPE_NONE = 5;

    public static final String[] TYPE_DESCRIPTIONS = { "user", "hourly", "daily", "weekly", "monthly", "none" };
    
    Long getId();
    long getAccountId();
    Long getHostId();
    Long getPoolId();
    long getVolumeId();
    String getPath();
    String getName();
    Date getCreated();
    short getSnapshotType();
    AsyncInstanceCreateStatus getStatus();
}
