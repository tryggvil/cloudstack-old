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
package com.vmops.ha;

import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.vm.State;
import com.vmops.vm.VMInstanceVO;

@Local(value=Investigator.class)
public class CheckOnAgentInvestigator implements Investigator {
	private String _name;
	AgentManager _agentMgr;
	
	private final static Logger s_logger = Logger.getLogger(CheckOnAgentInvestigator.class);
	
	protected CheckOnAgentInvestigator() {
	}
	
	@Override
	public Status isAgentAlive(HostVO agent) {
		return null;
	}

	@Override
	public Boolean isVmAlive(VMInstanceVO vm) {
		CheckVirtualMachineCommand cmd = new CheckVirtualMachineCommand(vm.getInstanceName());
		try {
			CheckVirtualMachineAnswer answer = (CheckVirtualMachineAnswer)_agentMgr.send(vm.getHostId(), cmd, 10 * 1000);
			if (!answer.getResult()) {
				s_logger.debug("Unable to get vm state on " + vm.toString());
				return null;
			}

			s_logger.debug("Agent responded with state " + answer.getState().toString());
			return answer.getState() == State.Running;
		} catch (AgentUnavailableException e) {
			s_logger.debug("Unable to reach the agent for " + vm.toString() + ": " + e.getMessage());
			return null;
		} catch (OperationTimedoutException e) {
			s_logger.debug("Operation timed out for " + vm.toString() + ": " + e.getMessage());
			return null;
		}
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		_name = name;
		
		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		_agentMgr = locator.getManager(AgentManager.class);
		if (_agentMgr == null) {
			throw new ConfigurationException("Unable to find " + AgentManager.class.getName());
		}
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
