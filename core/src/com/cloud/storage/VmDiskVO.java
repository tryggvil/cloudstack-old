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

package com.cloud.storage;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import com.cloud.utils.db.GenericDao;

@Entity
@Table(name="vm_disk")
public class VmDiskVO {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    Long id;

    @Column(name="instance_id")
    long instanceId;

    @Column(name="disk_offering_id")
    long diskOfferingId;

    @Column(name=GenericDao.REMOVED_COLUMN)
    private Date removed;

    public VmDiskVO() {}

    public VmDiskVO(long instanceId, long diskOfferingId) {
        this.instanceId = instanceId;
        this.diskOfferingId = diskOfferingId;
    }

    public Long getId() {
        return id;
    }

    public long getInstanceId() {
        return instanceId;
    }

    public long getDiskOfferingId() {
        return diskOfferingId;
    }

    public Date getRemoved() {
        return removed;
    }
}
