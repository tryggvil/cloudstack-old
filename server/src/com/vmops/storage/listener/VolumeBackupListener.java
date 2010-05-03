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

package com.vmops.storage.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.ScheduleVolumeSnapshotCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.storage.VolumeSnapshotAnswer;
import com.vmops.agent.api.storage.VolumeSnapshotCommand;
import com.vmops.host.Status;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.dao.SnapshotDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.ScheduledVolumeBackup;
import com.vmops.user.dao.ScheduledVolumeBackupDao;
import com.vmops.utils.concurrency.TestClock;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.vm.UserVmManager;

public class VolumeBackupListener implements Listener {
    private static final Logger s_logger = Logger.getLogger(VolumeBackupListener.class);

    private final ScheduledVolumeBackupDao _volumeBackupDao;
    private final VolumeDao _volumeDao;
    private final SnapshotDao _snapshotDao;
    private final AgentManager _agentMgr;
    private final UserVmManager _userVmMgr;

    private TestClock _testClock;
    private Timer _testClockTimer;

    public VolumeBackupListener(AgentManager mgr, UserVmManager userVmMgr, ScheduledVolumeBackupDao vmBackupDao, VolumeDao volDao, SnapshotDao snapshotDao) {
        _agentMgr = mgr;
        _userVmMgr = userVmMgr;
        _volumeDao = volDao;
        _volumeBackupDao = vmBackupDao;
        _snapshotDao = snapshotDao;
    }

    public void init(int minutesPerHour, int hoursPerDay, int daysPerWeek, int daysPerMonth, int weeksPerMonth, int monthsPerYear) {
        _testClock = new TestClock(null, minutesPerHour, hoursPerDay, daysPerWeek, daysPerMonth, weeksPerMonth, monthsPerYear);
        _testClockTimer = new Timer("TestClock");
        _testClockTimer.schedule(_testClock, 60L*1000L, 60L*1000L);
    }

    @Override
    public boolean isRecurring() {
        return true;
    }

    @Override
    @DB
    public boolean processAnswer(long agentId, long seq, Answer[] answers) {
        if (answers != null) {
            for (Answer answer : answers) {
                if (answer instanceof VolumeSnapshotAnswer) {
                    VolumeSnapshotAnswer volumeSnapshotAnswer = (VolumeSnapshotAnswer) answer;
                    List<Long> volumeIds = volumeSnapshotAnswer.getVolumeIds();
                    Map<Long, Object[]> retentionMap = new HashMap<Long, Object[]>();

                    // save the snapshot in the database
                    Transaction txn = Transaction.open("processRecurringSnapshot");
                    try {
                        txn.start();
                        if ((volumeIds != null) && !volumeIds.isEmpty()) {
                            for (Long volumeId : volumeIds) {
                                VolumeVO vol = _volumeDao.findById(volumeId);
                                if (vol != null) {
                                    short snapshotType = volumeSnapshotAnswer.getSnapshotType();
                                    String snapshotTypeDesc = Snapshot.TYPE_DESCRIPTIONS[snapshotType];
                                    Long newId = _snapshotDao.persist(new SnapshotVO(vol.getAccountId(), vol.getHostId(), vol.getPoolId(), vol.getInstanceId(),
                                                                                     vol.getFolder(), volumeSnapshotAnswer.getSnapshotName(), snapshotType, snapshotTypeDesc));
                                    ScheduledVolumeBackup backupVO = _volumeBackupDao.findByVolumeId(vol.getId());
                                    if (backupVO != null) {
                                        Object[] snapshotAndBackup = new Object[] { newId, backupVO };
                                        retentionMap.put(volumeId, snapshotAndBackup);
                                    }
                                }

                            }
                        }
                        txn.commit();
                    } catch (Exception ex) {
                        s_logger.warn("Exception while processing backups", ex);
                    } finally {
                        txn.close();
                    }

                    // query for retention values, if it's time to rollup
                    if ((volumeIds != null) && !volumeIds.isEmpty()) {
                        for (Long volId : volumeIds) {
                            Object[] retentionObj = retentionMap.get(volId);
                            if (retentionObj != null) {
                                Long snapshotId = (Long)retentionObj[0];
                                ScheduledVolumeBackup scheduledBackupObj = (ScheduledVolumeBackup)retentionObj[1];
                                if (isRollupNeeded(volumeSnapshotAnswer.getSnapshotType())) {
                                    // if the next "highest" snapshot type is being retained, then we can rollup, otherwise skip
                                    short rollupType = -1;
                                    if ((volumeSnapshotAnswer.getSnapshotType() == Snapshot.TYPE_HOURLY) && (scheduledBackupObj.getMaxDaily() > 0)) {
                                        rollupType = Snapshot.TYPE_DAILY;
                                    } else if ((volumeSnapshotAnswer.getSnapshotType() == Snapshot.TYPE_DAILY) && (scheduledBackupObj.getMaxWeekly() > 0)) {
                                        rollupType = Snapshot.TYPE_WEEKLY;
                                    } else if ((volumeSnapshotAnswer.getSnapshotType() == Snapshot.TYPE_DAILY) && (scheduledBackupObj.getMaxMonthly() > 0)) {
                                        rollupType = Snapshot.TYPE_MONTHLY;
                                    }

                                    if (rollupType > -1) {
                                        SnapshotVO updatedSnapshot = _snapshotDao.createForUpdate();
                                        updatedSnapshot.setSnapshotType(rollupType);
                                        updatedSnapshot.setTypeDescription(Snapshot.TYPE_DESCRIPTIONS[rollupType]);
                                        _snapshotDao.update(snapshotId, updatedSnapshot);
                                    }
                                }

                                // now delete excess snapshots for this Volume
                                Long oldSnapshotId = _snapshotDao.findExcessSnapshotsByVolumeAndType(volId, Snapshot.TYPE_HOURLY, scheduledBackupObj.getMaxHourly());
                                if (oldSnapshotId != null) {
                                    _userVmMgr.destroyRecurringSnapshot(Long.valueOf(1), oldSnapshotId);
                                }

                                oldSnapshotId = _snapshotDao.findExcessSnapshotsByVolumeAndType(volId, Snapshot.TYPE_DAILY, scheduledBackupObj.getMaxDaily());
                                if (oldSnapshotId != null) {
                                    _userVmMgr.destroyRecurringSnapshot(Long.valueOf(1), oldSnapshotId);
                                }

                                oldSnapshotId = _snapshotDao.findExcessSnapshotsByVolumeAndType(volId, Snapshot.TYPE_WEEKLY, scheduledBackupObj.getMaxWeekly());
                                if (oldSnapshotId != null) {
                                    _userVmMgr.destroyRecurringSnapshot(Long.valueOf(1), oldSnapshotId);
                                }

                                oldSnapshotId = _snapshotDao.findExcessSnapshotsByVolumeAndType(volId.longValue(), Snapshot.TYPE_MONTHLY, scheduledBackupObj.getMaxMonthly());
                                if (oldSnapshotId != null) {
                                    _userVmMgr.destroyRecurringSnapshot(Long.valueOf(1), oldSnapshotId);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean processCommand(long agentId, long seq, Command[] commands) {
        return false;
    }
    
    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
    	return null;
    }
    
    @Override
    public boolean processTimeout(long agentId, long seq) {
    	return true;
    }
    
    @Override
    public int getTimeout() {
    	return -1;
    }

    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
        if (cmd instanceof StartupStorageCommand) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending VolumeSnapshotCommand to " + agentId);
            }
            int minutesPerHour = 60;
            int hoursPerDay = 24;
            if (_testClock != null) {
                minutesPerHour = _testClock.getMinutesPerHour();
                hoursPerDay = _testClock.getHoursPerDay();
            }
            VolumeSnapshotCommand hourlySnapshotCommand = new VolumeSnapshotCommand(Snapshot.TYPE_HOURLY, minutesPerHour * 60);
            _agentMgr.gatherStats(agentId, hourlySnapshotCommand, this);

            VolumeSnapshotCommand dailySnapshotCommand = new VolumeSnapshotCommand(Snapshot.TYPE_DAILY, hoursPerDay * minutesPerHour * 60);
            _agentMgr.gatherStats(agentId, dailySnapshotCommand, this);
        }

        Map<Long, ScheduleVolumeSnapshotCommand> commandMap = new HashMap<Long, ScheduleVolumeSnapshotCommand>();
        List<VolumeVO> volumes = _volumeDao.findByHost(agentId);
        if ((volumes != null) && !volumes.isEmpty()) {
            for (VolumeVO volume : volumes) {
        		ScheduledVolumeBackup volumeBackup = _volumeBackupDao.findByVolumeId(volume.getId());
            	if (volumeBackup != null) {
            		commandMap.put(Long.valueOf(volume.getInstanceId()), new ScheduleVolumeSnapshotCommand(volume.getId(), volume.getFolder(), volumeBackup.getInterval()));
                }
            }
        }

        if (!commandMap.isEmpty()) {
            Command[] commandArray = commandMap.values().toArray(new Command[commandMap.values().size()]);
            try {
                _agentMgr.send(agentId, commandArray, false /* stopOnError = false */);
            } catch (Exception ex) {
                s_logger.error("Unable to restart regular backups for volumes on agent " + agentId, ex);
            }
        }

        return true;
    }

    @Override
    public boolean processDisconnect(long agentId, Status state) {
        return false;
    }

    private boolean isRollupNeeded(short snapshotType) {
        boolean isRollupNeeded = false;
        if (_testClock != null) {
            synchronized(_testClock) {
                int hour = _testClock.getHour();
                int day = _testClock.getDay();
                if (isRollupNeeded(snapshotType, hour, day)) {
                    isRollupNeeded = true;
                }
            }
        } else {
            // do some calendar stuff for figuring out the end of the day???  should this be mgmt server end of day
            // or storage server end of day?
        }
        return isRollupNeeded;
    }

    private boolean isRollupNeeded(short snapshotType, int hour, int day) {
        boolean result = false;
        switch(snapshotType) {
        case Snapshot.TYPE_HOURLY:
            result = (hour == 0);
            break;
        case Snapshot.TYPE_DAILY:
            result = (day == 0);
            break;
        }
        return result;
    }
}
