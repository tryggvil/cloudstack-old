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

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.StartupCommand;
import com.vmops.host.Status;

public class StorageSyncListener implements Listener {
    private static final Logger s_logger = Logger.getLogger(StorageSyncListener.class);
    
    public StorageSyncListener() {
    }
    
    @Override
    public boolean isRecurring() {
        return false;
    }
    
    @Override
    public boolean processAnswer(long agentId, long seq, Answer[] answers) {
        for (Answer answer : answers) {
            if (answer.getResult() == false) {
                s_logger.warn("Unable to execute sync command: " + answer.toString());
            } else {
                s_logger.debug("Sync command executed: " + answer.toString());
            }
        }
        return true;
    }
    
    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
        return false;
    }
    
    @Override
    public boolean processDisconnect(long agentId, Status state) {
        s_logger.debug("Disconnecting");
        return true;
    }
    
    @Override
    public boolean processCommand(long agentId, long seq, Command[] request) {
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
