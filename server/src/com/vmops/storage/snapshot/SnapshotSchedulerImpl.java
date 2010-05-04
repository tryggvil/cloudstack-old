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
package com.vmops.storage.snapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.Timer;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.SnapshotScheduleVO;
import com.vmops.storage.dao.SnapshotPolicyDao;
import com.vmops.storage.dao.SnapshotPolicyRefDao;
import com.vmops.storage.dao.SnapshotScheduleDao;
import com.vmops.utils.DateUtil;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.DateUtil.IntervalType;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.TestClock;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 *
 */
@Local(value={SnapshotScheduler.class})
public class SnapshotSchedulerImpl implements SnapshotScheduler {
    private static final Logger s_logger = Logger.getLogger(SnapshotSchedulerImpl.class);
    
    
    private String _name = null;
    private SnapshotScheduleDao     _snapshotScheduleDao;
    private SnapshotPolicyDao       _snapshotPolicyDao;
    private SnapshotPolicyRefDao    _snapshotPolicyRefDao;
    private SnapshotManager         _snapshotManager;
    private int                     _pingInterval;
    private Timer                   _testClockTimer;
    private Date                    _currentTimestamp;
    
    @Override @DB 
    public void init(SnapshotManager snapshotManager, int minutesPerHour, int hoursPerDay, int daysPerWeek, int daysPerMonth, int weeksPerMonth, int monthsPerYear) {
        
        if (_name == null) {
            // Snapshot Scheduler is not configured. Can't initialize yet.
            throw new VmopsRuntimeException("Snapshot Scheduler is not configured. Can't initialize yet.");
        }
        
        // Delete the old schedules. They will get rescheduled now as we scan the policyInstance table again.
        List<SnapshotScheduleVO> oldSchedules = _snapshotScheduleDao.listAll();
        for (SnapshotScheduleVO oldSchedule : oldSchedules) {
            _snapshotScheduleDao.remove(oldSchedule.getId());
        }
        
        _snapshotManager = snapshotManager;
        List<SnapshotPolicyVO> policies = _snapshotPolicyDao.listAll();
        _currentTimestamp = new Date();
        for (SnapshotPolicyVO policy : policies) {
            Long policyId = policy.getId();
            if(policyId == SnapshotManager.MANUAL_POLICY_ID){
                continue;
            }
            Date nextSnapshotTimestamp =  getNextScheduledTime(policyId, _currentTimestamp);
            SnapshotScheduleVO snapshotScheduleVO = new SnapshotScheduleVO(policy.getVolumeId(), policyId, nextSnapshotTimestamp);
            Transaction txn = Transaction.currentTxn();
            txn.start();
            _snapshotScheduleDao.persist(snapshotScheduleVO);
            txn.commit();
        }
        TestClock testTimerTask = new TestClock(this, minutesPerHour, hoursPerDay, daysPerWeek, daysPerMonth, weeksPerMonth, monthsPerYear);
        _testClockTimer = new Timer("TestClock");
        _testClockTimer.schedule(testTimerTask, _pingInterval*1000L, _pingInterval*1000L);
    }

    private Date getNextScheduledTime(long policyId, Date currentTimestamp) {
        SnapshotPolicyVO policy = _snapshotPolicyDao.findById(policyId);
        Date nextTimestamp = null;
        if (policy != null) {
            short intervalType = policy.getInterval();
            IntervalType type = DateUtil.getIntervalType(intervalType);
            String schedule = policy.getSchedule();
            nextTimestamp = DateUtil.getNextRunTime(type, schedule, currentTimestamp);
            String currentTime = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, currentTimestamp);
            String nextScheduledTime = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, nextTimestamp);
            s_logger.debug("Current time is " + currentTime + ". NextScheduledTime of policyId " + policyId + " is " + nextScheduledTime);
        }
        return nextTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @DB
    public void poll(Date currentTimestamp) {
        // We don't maintain the time. The test clock does. Use it's time.
        _currentTimestamp = currentTimestamp;
        String displayTime = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, currentTimestamp);
        s_logger.debug("Snapshot scheduler.poll is being called at " + displayTime);
        
        List<SnapshotScheduleVO> snapshotsToBeExecuted = _snapshotScheduleDao.getSchedulesToExecute(currentTimestamp);
        s_logger.debug("Got " + snapshotsToBeExecuted.size() + " snapshots to be executed at " + displayTime);
        
        // This is done for recurring snapshots, which are executed by the system automatically
        // Hence set user id to that of system
        long userId = 1;
        
        // The volumes which are going to be snapshotted now. 
        // The value contains the list of policies associated with this new snapshot.
        // There can be more than one policy for a list if different policies coincide for the same volume.
        Map<Long, List<Long>> listOfVolumesSnapshotted = new HashMap<Long, List<Long>>();
        for (SnapshotScheduleVO snapshotToBeExecuted : snapshotsToBeExecuted) {
            long policyId = snapshotToBeExecuted.getPolicyId();
            long volumeId = snapshotToBeExecuted.getVolumeId();
            List<Long> coincidingPolicies = listOfVolumesSnapshotted.get(volumeId);
            if (coincidingPolicies != null) {
                s_logger.debug("The snapshot for this volume " + volumeId + " and policy " + policyId + " has already been sent for execution along with " + coincidingPolicies.size() + " policies in total");
                // This can happen if this coincided with another schedule with a different policy
                // It would have added all the coinciding policies for the volume to the Map
                
                if (coincidingPolicies.contains(snapshotToBeExecuted.getPolicyId())) {
                    // Don't need to do anything now. The snapshot is already scheduled for execution.
                    s_logger.debug("coincidingPolicies contains snapshotToBeExecuted id: " + snapshotToBeExecuted.getId() + ". Don't need to do anything now. The snapshot is already scheduled for execution.");
                }
                else {
                    // This is a bug. 
                    // The getSchedulesToExecute method of snapshotScheduleDao 
                    // thinks that both these schedules can be executed right away,
                    // but the getCoincidingSnapshotSchedules method thinks that these two
                    // are not coincident. The window of the former should be smaller than the later.
                    s_logger.warn("Snapshot Schedule " + snapshotToBeExecuted.getId() + 
                                  " is ready for execution now at timestamp " + currentTimestamp + 
                                  " but is not coincident with one being executed for volume " + volumeId);
                    // Add this to the list of policies for the snapshot schedule
                    coincidingPolicies.add(snapshotToBeExecuted.getPolicyId());
                    listOfVolumesSnapshotted.put(volumeId, coincidingPolicies);
                    // But this has already been sent for execution. We don't want to execute it again
                    // Just update the snapshot_policy_ref table.
                    // SnapshotManager should have an interface for doing that.
                    
                }
            }
            else {
                coincidingPolicies = new ArrayList<Long>();
                List<SnapshotScheduleVO> coincidingSchedules = _snapshotScheduleDao.getCoincidingSnapshotSchedules(volumeId, currentTimestamp);

                if (s_logger.isDebugEnabled()) {
                    Date scheduledTimestamp = snapshotToBeExecuted.getScheduledTimestamp();
                    displayTime = DateUtil.getDateDisplayString(DateUtil.GMT_TIMEZONE, scheduledTimestamp);
                    
                }
                Transaction txn = Transaction.currentTxn();
                txn.start();
                // There are more snapshots scheduled for this volume at the same time.
                // Club all the policies together and append them to the coincidingPolicies List
                StringBuilder coincidentSchedules = new StringBuilder();
                for (SnapshotScheduleVO coincidingSchedule : coincidingSchedules) {
                    coincidingPolicies.add(coincidingSchedule.getPolicyId());
                    // In any case, whether we sent the job for creation, the snapshot was created or not, 
                    // remove it from the job schedule queue so that it doesn't get scheduled again and block others.
                    // Remove the schedule from the job queue
                    // Remove each of the coinciding schedule from the job queue.
                    _snapshotScheduleDao.remove(coincidingSchedule.getId());
                    coincidentSchedules.append(coincidingSchedule.getId() + ", ");
                }
                txn.commit();
                s_logger.debug("Scheduling 1 snapshot for volume " + volumeId + " for schedule ids: " + coincidentSchedules + " at " + displayTime);
                long jobId = -1;
                try {
                    jobId = _snapshotManager.createSnapshotAsync(userId, volumeId, coincidingPolicies);
                } catch (InvalidParameterValueException e) {
                    s_logger.error("One of volumeId: " + volumeId + " or policyIds: " + coincidingPolicies + " is invalid. " + e.getMessage(), e);
                } catch (ResourceAllocationException rae) {
                	s_logger.error("The maximum number of snapshots has been exceeded.");
                    return;
                }
				if (jobId > 0) {
					// Add this snapshot to the listOfVolumesSnapshotted
					// So that the coinciding schedules don't get scheduled again.
					listOfVolumesSnapshotted.put(volumeId, coincidingPolicies);
                }
            }
        }
    }

    @Override @DB
    public Date scheduleNextSnapshotJob(SnapshotPolicyVO policyInstance) {
        long policyId = policyInstance.getId();

        Date nextSnapshotTimestamp = getNextScheduledTime(policyId, _currentTimestamp);
        SnapshotScheduleVO snapshotScheduleVO = new SnapshotScheduleVO(policyInstance.getVolumeId(), policyId, nextSnapshotTimestamp);
        Transaction txn = Transaction.currentTxn();
        txn.start();
        _snapshotScheduleDao.persist(snapshotScheduleVO);
        txn.commit();
        return nextSnapshotTimestamp;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params)
    throws ConfigurationException {
        _name = name;

        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        
        _snapshotScheduleDao = locator.getDao(SnapshotScheduleDao.class);
        if (_snapshotScheduleDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotScheduleDao.class.getName());
        }
        
        _snapshotPolicyDao = locator.getDao(SnapshotPolicyDao.class);
        if (_snapshotPolicyDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotPolicyDao.class.getName());
        }
        
        _snapshotPolicyRefDao = locator.getDao(SnapshotPolicyRefDao.class);
        if (_snapshotPolicyRefDao == null) {
            throw new ConfigurationException("Unable to get " + SnapshotPolicyRefDao.class.getName());
        }

        ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
        if (configDao == null) {
            s_logger.error("Unable to get the configuration dao. " + ConfigurationDao.class.getName());
            return false;
        }
        Map<String, String> configs = configDao.getConfiguration("management-server", params);
        _pingInterval = NumbersUtil.parseInt(configs.get("ping.interval"), 60);
        
        /**
        _snapshotManager = locator.getDao(SnapshotManager.class);
        if (_snapshotManager == null) {
            throw new ConfigurationException("Unable to get " + SnapshotManager.class.getName());
        }
        */
        s_logger.info("Snapshot Scheduler is configured.");
       
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
}
