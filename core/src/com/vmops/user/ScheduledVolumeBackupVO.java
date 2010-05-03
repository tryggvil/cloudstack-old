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

package com.vmops.user;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="scheduled_volume_backups")
public class ScheduledVolumeBackupVO implements ScheduledVolumeBackup {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    private Long id = null;

    @Column(name="volume_id")
    private Long volumeId;

    @Column(name="interval")
    private short interval;

    @Column(name="max_hourly")
    private int maxHourly;

    @Column(name="max_daily")
    private int maxDaily;

    @Column(name="max_weekly")
    private int maxWeekly;

    @Column(name="max_monthly")
    private int maxMonthly;

    public ScheduledVolumeBackupVO() { }

    public ScheduledVolumeBackupVO(Long volumeId, short interval, int maxHourly, int maxDaily, int maxWeekly, int maxMonthly) {
        this.volumeId = volumeId;
        this.interval = interval;
        this.maxHourly = maxHourly;
        this.maxDaily = maxDaily;
        this.maxWeekly = maxWeekly;
        this.maxMonthly = maxMonthly;
    }

    public Long getId() {
        return id;
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public short getInterval() {
        return interval;
    }
    public void setInterval(short interval) {
        this.interval = interval;
    }

    public int getMaxHourly() {
        return maxHourly;
    }
    public void setMaxHourly(int maxHourly) {
        this.maxHourly = maxHourly;
    }

    public int getMaxDaily() {
        return maxDaily;
    }
    public void setMaxDaily(int maxDaily) {
        this.maxDaily = maxDaily;
    }

    public int getMaxWeekly() {
        return maxWeekly;
    }
    public void setMaxWeekly(int maxWeekly) {
        this.maxWeekly = maxWeekly;
    }

    public int getMaxMonthly() {
        return maxMonthly;
    }
    public void setMaxMonthly(int maxMonthly) {
        this.maxMonthly = maxMonthly;
    }
}
