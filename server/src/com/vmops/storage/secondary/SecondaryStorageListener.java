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
package com.vmops.storage.secondary;

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.StartupCommand;
import com.vmops.host.Status;

public class SecondaryStorageListener implements Listener {
    private final static Logger s_logger = Logger.getLogger(SecondaryStorageListener.class);
    
    SecondaryStorageVmManager _ssVmMgr = null;

    public SecondaryStorageListener(SecondaryStorageVmManager ssVmMgr) {
        _ssVmMgr = ssVmMgr;
    }
    
    @Override
    public boolean isRecurring() {
        return true;
    }

    @Override
    public boolean processAnswer(long agentId, long seq, Answer[] answers) {
    	boolean processed = false;
    	if(answers != null) {
    		for(int i = 0; i < answers.length; i++) {
    			/*if(answers[i] instanceof ConsoleProxyLoadAnswer) {
    				_ssVmMgr.onLoadAnswer((ConsoleProxyLoadAnswer)answers[i]);
    				processed = true;
    			}*/
    		}
    	}
    	
        return processed;
    }

    @Override
    public boolean processCommand(long agentId, long seq, Command[] commands) {
        return false;
    }
    
    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
    /*	if(cmd instanceof ConsoleProxyLoadReportCommand) {
    		_ssVmMgr.onLoadReport((ConsoleProxyLoadReportCommand)cmd);
    	} else if(cmd instanceof ConsoleAccessAuthenticationCommand) {
    		return _ssVmMgr.onConsoleAccessAuthentication((ConsoleAccessAuthenticationCommand)cmd);
    	}*/
    	return null;
    }

    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
        if(s_logger.isInfoEnabled())
            s_logger.info("Received a host startup notification");

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
