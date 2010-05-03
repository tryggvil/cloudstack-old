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
package com.vmops.agent.manager;

import java.util.List;

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.alert.AlertManager;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.Status.Event;
import com.vmops.host.dao.HostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.utils.db.GlobalLock;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.dao.VMInstanceDao;

public class AgentMonitor extends Thread implements Listener {
    private static Logger s_logger = Logger.getLogger(AgentMonitor.class);
    private final long _pingTimeout;
    private final HostDao _hostDao;
    private boolean _stop;
    private final AgentManagerImpl _agentMgr;
    private final VMInstanceDao _vmDao;
    private final VolumeDao _volDao;
    private DataCenterDao _dcDao = null;
    private HostPodDao _podDao = null;
    private final AlertManager _alertMgr;
    private final long _msId;
    
    public AgentMonitor(long msId, HostDao hostDao, VolumeDao volDao, VMInstanceDao vmDao, DataCenterDao dcDao, HostPodDao podDao, AgentManagerImpl agentMgr, AlertManager alertMgr, long pingTimeout) {
    	super("AgentMonitor");
    	_msId = msId;
        _pingTimeout = pingTimeout;
        _hostDao = hostDao;
        _agentMgr = agentMgr;
        _stop = false;
        _vmDao = vmDao;
        _volDao = volDao;
        _dcDao = dcDao;
        _podDao = podDao;
        _alertMgr = alertMgr;
    }
    
    // TODO : use host machine time is not safe in clustering environment
    @Override
	public void run() {
        s_logger.info("Agent Monitor is started.");
        
//        _agentMgr.startDirectlyConnectedHosts();
        try {
            Thread.sleep(_pingTimeout * 2000);
        } catch (InterruptedException e) {
            s_logger.info("Woke me up so early!");
        }
        while (!_stop) {
            try {
                Thread.sleep(_pingTimeout * 1000);
            } catch (InterruptedException e) {
                s_logger.info("Who woke me from my slumber?");
            }
            
        	GlobalLock lock = GlobalLock.getInternLock("AgentMonitorLock");
        	if (lock == null) {
        		s_logger.error("Unable to acquire lock.  Better luck next time?");
        		continue;
        	}
        	
        	if (!lock.lock(10)) {
        		s_logger.info("Someone else is already working on the agents.  Skipping my turn");
        		continue;
        	}
        	
            try {
                long time = (System.currentTimeMillis() >> 10) - _pingTimeout;
                List<HostVO> hosts = _hostDao.findLostHosts(time);
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Found " + hosts.size() + " hosts behind on ping. pingTimeout : " + _pingTimeout + ", mark time : " + time);
                }
                
                for (HostVO host : hosts) {
                	if (host.getManagementServerId() == null || host.getManagementServerId() == _msId) {
	                    if (s_logger.isInfoEnabled()) {
	                        s_logger.info("Asking agent mgr to investgate why host " + host.getId() + " is behind on ping. last ping time: " + host.getLastPinged());
	                    }
	                    _agentMgr.disconnect(host.getId(), Event.PingTimeout, true);
                	}
                }
                
                hosts = _hostDao.listByStatus(Status.PrepareForMaintenance, Status.ErrorInMaintenance);
                for (HostVO host : hosts) {
                    long hostId = host.getId();
                    DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                    HostPodVO podVO = _podDao.findById(host.getPodId());
                    String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();

                    if (host.getType() != Host.Type.Storage) {
                        List<VMInstanceVO> vos = _vmDao.listByHostId(host.getId());
                        if (vos.size() == 0) {
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Migration Complete for host " + hostDesc, "Host [" + hostDesc + "] is ready for maintenance");
                            _hostDao.updateStatus(host, Event.PreparationComplete, _msId);
                        }
                    } else {
                        List<Long> ids = _volDao.findVmsStoredOnHost(hostId);
                        boolean stillWorking = false;
                        for (Long id : ids) {
                            VMInstanceVO instance = _vmDao.findById(id);
                            if (instance != null && (instance.getState() == com.vmops.vm.State.Starting || instance.getState() == com.vmops.vm.State.Stopping || instance.getState() == com.vmops.vm.State.Running || instance.getState() == com.vmops.vm.State.Migrating)) {
                                stillWorking = true;
                                break;
                            }
                        }
                        if (!stillWorking) {
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "All VMs Stopped for host " + hostDesc, "Host [" + hostDesc + "] is ready for maintenance");
                            _hostDao.updateStatus(host, Event.PreparationComplete, _msId);
                        }
                    }
                }
            } catch (Throwable th) {
                s_logger.error("Caught the following exception: ", th);
            } finally {
            	lock.unlock();
            }
        }
        
        s_logger.info("Agent Monitor is leaving the building!");
    }
    
    public void signalStop() {
        _stop = true;
        interrupt();
    }

    @Override
    public boolean isRecurring() {
        return true;
    }

    @Override
    public boolean processAnswer(long agentId, long seq, Answer[] answers) {
        return false;
    }

    @Override
    public boolean processCommand(long agentId, long seq, Command[] commands) {
        boolean processed = false;
        for (Command cmd : commands) {
            if (cmd instanceof PingCommand) {
                HostVO host = _hostDao.findById(agentId);
                _hostDao.updateStatus(host, Event.Ping, _msId);
                processed = true;
            }
        }
        return processed;
    }
    
    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
    	return null;
    }

    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
        s_logger.debug("Registering agent monitor for " + agentId);
        return true;
    }

    @Override
    public boolean processDisconnect(long agentId, Status state) {
        return true;
    }
    
    @Override
    public boolean processTimeout(long agentId, long seq) {
    	return true;
    }
    
    @Override
    public int getTimeout() {
    	return -1;
    }
}
