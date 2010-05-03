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
package com.vmops.network.listener;

import java.util.Collection;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.agent.api.WatchNetworkAnswer;
import com.vmops.agent.api.WatchNetworkCommand;
import com.vmops.host.Status;
import com.vmops.user.UserStatisticsVO;
import com.vmops.user.dao.UserStatisticsDao;
import com.vmops.utils.component.Inject;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.dao.DomainRouterDao;

public class RouterStatsListener implements Listener {
    private static final Logger s_logger = Logger.getLogger(RouterStatsListener.class);

    @Inject
    private DomainRouterDao _routerDao;
    @Inject
    private UserStatisticsDao _statsDao;
    @Inject
    private AgentManager _agentMgr;
    
    private int _interval;
    
    public RouterStatsListener(int interval) {
        _interval = interval;
    }

    @Override
    public boolean isRecurring() {
        return true;
    }

    @Override @DB
    public boolean processAnswer(long agentId, long seq, Answer[] answers) {
        for (Answer answer : answers) {
            if (!(answer instanceof WatchNetworkAnswer)) {
                continue;
            }
            WatchNetworkAnswer watch = (WatchNetworkAnswer)answer;
            Collection<String> map = watch.getAllVms();
            for (String vmName : map) {
            	// Console proxy domU and routing domU share the same host
            	if(!VirtualMachineName.isValidRouterName(vmName)) {
            		if(s_logger.isTraceEnabled())
            			s_logger.trace("ignore user statistic update for console proxy : " + vmName);
            		continue;
            	}
            		
                long id = VirtualMachineName.getRouterId(vmName);
                DomainRouterVO router = _routerDao.findById(id);
                if (router == null || router.isRemoved()) {
                    s_logger.warn("Router is removed or non existent: " + vmName);
                    continue;
                }

                long[] bytes = watch.getStats(vmName);
                if (router.getState() != State.Running) {
                    s_logger.info("Not logging anything for a router that's not running: Rx " + bytes[1] + " and Tx " + bytes[0]);
                    continue;
                }
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("VM " + vmName + " Tx: " + bytes[0] + " Rx: " + bytes[1]);
                }
                Transaction txn = Transaction.currentTxn();
                try {
                    txn.start();

                    UserStatisticsVO stats = _statsDao.lock(router.getAccountId(), router.getDataCenterId());
                    if (stats == null) {
                        s_logger.warn("unable to find stats for account: " + router.getAccountId());
                        txn.rollback();
                        continue;
                    }
                    if (stats.getCurrentBytesReceived() > bytes[1]) {
                    	if (s_logger.isDebugEnabled()) {
                    		s_logger.debug("Received # of bytes that's less than the last one.  Assuming something went wrong and persisting it.  Reported: " + bytes[1] + " Stored: " + stats.getCurrentBytesReceived());
                    	}
                    	stats.setNetBytesReceived(stats.getNetBytesReceived() + stats.getCurrentBytesReceived());
                    }
                    stats.setCurrentBytesReceived(bytes[1]);
                    if (stats.getCurrentBytesSent() > bytes[0]) {
                    	if (s_logger.isDebugEnabled()) {
                    		s_logger.debug("Received # of bytes that's less than the last one.  Assuming something went wrong and persisting it.  Reported: " + bytes[0] + " Stored: " + stats.getCurrentBytesSent());
                    	}
                    	stats.setNetBytesSent(stats.getNetBytesSent() + stats.getCurrentBytesSent());
                    }
                    stats.setCurrentBytesSent(bytes[0]);
                    _statsDao.update(stats.getId(), stats);
                    txn.commit();
                } catch(Exception e) {
                    s_logger.warn("Unable to update user statistics for account: " + router.getAccountId() + " Rx: " + bytes[1] + "; Tx: " + bytes[0]);
                }
            }
        }
        return true;
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
    public boolean processDisconnect(long agentId, Status state) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Disconnected caleld on " + agentId + " with status " + state.toString());
        }
        return true;
    }
    
    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
        s_logger.debug("Sending WatchNetworkCommand to " + agentId);
        if (cmd instanceof StartupRoutingCommand) {
            WatchNetworkCommand watch = new WatchNetworkCommand(_interval);
            _agentMgr.gatherStats(agentId, watch, this);
        }
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
    
    protected RouterStatsListener() {
    }
}
