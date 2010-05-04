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

package com.vmops.cluster;

import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.host.Status.Event;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.MacAddress;

public class DummyClusterManagerImpl implements ClusterManager {
    private static final Logger s_logger = Logger.getLogger(DummyClusterManagerImpl.class);
	
    protected long _id = MacAddress.getMacAddress().toLong();
    private String _name;
    private final String _clusterNodeIP = "127.0.0.1";
	
    public Answer[] execute(String strPeer, long agentId, Command [] cmds, boolean stopOnError) {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public long executeAsync(String strPeer, long agentId, Command[] cmds, boolean stopOnError, Listener listener) {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public boolean onAsyncResult(String executingPeer, long agentId, long seq, Answer[] answers) {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public boolean forwardAnswer(String targetPeer, long agentId, long seq, Answer[] answers) {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public Answer[] sendToAgent(Long hostId, Command []  cmds, boolean stopOnError)
    	throws AgentUnavailableException, OperationTimedoutException {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public long sendToAgent(Long hostId, Command[] cmds, boolean stopOnError, Listener listener) throws AgentUnavailableException {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public boolean executeAgentUserRequest(long agentId, Event event) throws AgentUnavailableException {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
    
    public Boolean propagateAgentEvent(long agentId, Event event) throws AgentUnavailableException {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
	
	public int getHeartbeatThreshold() {
    	return ClusterManager.DEFAULT_HEARTBEAT_INTERVAL;
	}
	
	public long getId() {
        return _id;
	}
	
	@Override
	public ManagementServerHostVO getPeer(String str) {
		return null;
	}
	
	public String getSelfPeerName() {
		return Long.toString(_id);
	}
	
	public String getSelfNodeIP() {
		return _clusterNodeIP;
	}
	
    public String getPeerName(long agentHostId) {
    	throw new VmopsRuntimeException("Unsupported feature");
    }
	
	public void regiterListener(ClusterManagerListener listener) {
	}
	
	public void unregisterListener(ClusterManagerListener listener) {
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		return true;
	}
	
	@Override
	public void broadcast(long hostId, Command[] cmds) {
	}

	@Override
	public String getName() {
        return _name;
	}

	@Override
	public boolean start() {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Starting cluster manager, msid : " + _id);
    	
        return true;
	}

	@Override
	public boolean stop() {
		return true;
	}
}
