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
package com.vmops.network;

import java.util.List;

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.VlanDao;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.dao.HostDao;

public class VlanMonitor implements Listener {
    private static final Logger s_logger = Logger.getLogger(VlanMonitor.class);
	private HostDao _hostDao;
	private VlanDao _vlanDao;
	private NetworkManager _networkMgr;
	
    public VlanMonitor(NetworkManager mgr, HostDao hostDao, VlanDao vlanDao) {
    	this._networkMgr = mgr;
    	this._hostDao = hostDao;
    	this._vlanDao = vlanDao;
    }
    
    
    @Override
    public boolean isRecurring() {
        return false;
    }
    
    @Override
    public synchronized boolean processAnswer(long agentId, long seq, Answer[] resp) {
        return true;
    }
    
    @Override
    public synchronized boolean processDisconnect(long agentId, Status state) {
    	if(s_logger.isTraceEnabled())
    		s_logger.trace("Agent disconnected, agent id: " + agentId + ", state: " + state + ". Will notify waiters");
    	
    
        return true;
    }
    
    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
    	// If the host that connected is a Routing Server, add VLANs to it
        if (cmd instanceof StartupRoutingCommand) {

            s_logger.debug("Routing Server connected, sending down ModifyVlanCommands...");
            
            HostVO host = _hostDao.findById(agentId);
            List<VlanVO> vlansForZone = _vlanDao.findByZone(host.getDataCenterId());
            for (VlanVO vlan : vlansForZone) {
                Long hostId = host.getId();
                String vlanId = vlan.getVlanId();
                String vlanGateway = vlan.getVlanGateway();
                if (_networkMgr.addVlanToHost(hostId, vlanId, vlanGateway)) {
                    // Save entry to HostVlanMapDao
                    _hostDao.addVlan(hostId, vlanId);
                }
            }
        }
        return true;
    }

    @Override
    public boolean processCommand(long agentId, long seq, Command[] req) {
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
}
