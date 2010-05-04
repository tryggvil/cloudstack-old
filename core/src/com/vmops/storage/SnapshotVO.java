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

package com.vmops.storage;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.google.gson.annotations.Expose;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.utils.db.GenericDao;

@Entity
@Table(name="snapshots")
public class SnapshotVO implements Snapshot {
	
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    Long id;

    @Column(name="account_id")
    long accountId;

    @Column(name="host_id")
    Long hostId;
    
    @Column(name="pool_id")
    Long poolId;

    @Column(name="volume_id")
    long volumeId;

    @Expose
    @Column(name="path")
    String path;

    @Expose
    @Column(name="name")
    String name;

    @Expose
    @Column(name="status", updatable = true, nullable=false)
    @Enumerated(value=EnumType.STRING)
    private AsyncInstanceCreateStatus status;

    @Column(name="snapshot_type")
    short snapshotType;

    @Column(name="type_description")
    String typeDescription;

    @Column(name=GenericDao.CREATED_COLUMN)
    Date created;

    @Column(name=GenericDao.REMOVED_COLUMN)
    Date removed;

    @Column(name="backup_snap_id")
    String backupSnapshotId;
    
    @Column(name="prev_snap_id")
    long prevSnapshotId;
    
    public SnapshotVO() { }

    public SnapshotVO(long accountId, Long hostId, Long poolId, long volumeId, String path, String name, short snapshotType, String typeDescription) {
        this.accountId = accountId;
        this.hostId = hostId;
        this.poolId = poolId;
        this.volumeId = volumeId;
        this.path = path;
        this.name = name;
        this.snapshotType = snapshotType;
        this.typeDescription = typeDescription;
        this.status = AsyncInstanceCreateStatus.Creating;
        this.prevSnapshotId = 0;
    }

    public Long getId() {
        return id;
    }

    public long getAccountId() {
        return accountId;
    }

    public Long getHostId() {
        return hostId;
    }

    public long getVolumeId() {
        return volumeId;
    }

    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
    	this.path = path;
    }

    public String getName() {
        return name;
    }

    public short getSnapshotType() {
        return snapshotType;
    }
    public void setSnapshotType(short snapshotType) {
        this.snapshotType = snapshotType;
    }

    public String getTypeDescription() {
        return typeDescription;
    }
    public void setTypeDescription(String typeDescription) {
        this.typeDescription = typeDescription;
    }

    public Date getCreated() {
        return created;
    }

    public Date getRemoved() {
        return removed;
    }
    
	@Override
	public Long getPoolId() {
		return poolId;
	}
	
	@Override
    public AsyncInstanceCreateStatus getStatus() {
		return status;
	}
	
	public void setStatus(AsyncInstanceCreateStatus status) {
		this.status = status;
	}
	
	public String getBackupSnapshotId(){
		return backupSnapshotId;
	}
	
	public long getPrevSnapshotId(){
		return prevSnapshotId;
	}
	
	public void setBackupSnapshotId(String backUpSnapshotId){
		this.backupSnapshotId = backUpSnapshotId;
	}
	
	public void setPrevSnapshotId(long prevSnapshotId){
		this.prevSnapshotId = prevSnapshotId;
	}
}
