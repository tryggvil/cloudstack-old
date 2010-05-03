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
package com.vmops.agent.api;

import java.util.HashMap;

import com.vmops.host.Host;
import com.vmops.vm.State;

public class StartupProxyCommand extends StartupComputingCommand {
	
	private long proxyVmId;
    
	public StartupProxyCommand() {
        super(0, 0l, 0l, 0l, "", Host.HypervisorType.None, new HashMap<String, State>());
        setIqn("NoIqn");
    }
	
    @Override
    public boolean executeInSequence() {
        return true;
    }

    public long getProxyVmId() {
		return proxyVmId;
	}
	public void setProxyVmId(long proxyVmId) {
		this.proxyVmId = proxyVmId;
	}
}
