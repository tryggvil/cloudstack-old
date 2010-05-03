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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name="snapshot_schedule")
public class SnapshotScheduleVO {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id")
	long id;
	
    @Column(name="volume_id")
    long volumeId;

    @Column(name="policy_id")
    long policyId;

    @Column(name="scheduled_timestamp")
    @Temporal(value=TemporalType.TIMESTAMP)
    Date scheduledTimestamp;

    public SnapshotScheduleVO() { }

    public SnapshotScheduleVO(long volumeId, long policyId, Date scheduledTimestamp) {
        this.volumeId = volumeId;
        this.policyId = policyId;
        this.scheduledTimestamp = scheduledTimestamp;
    }
    
    public Long getId() {
        return id;
    }
    
    public Long getVolumeId() {
        return volumeId;
    }
    
    public Long getPolicyId() {
        return policyId;
    }

	/**
	 * @return the scheduledTimestamp
	 */
	public Date getScheduledTimestamp() {
		return scheduledTimestamp;
	}
}