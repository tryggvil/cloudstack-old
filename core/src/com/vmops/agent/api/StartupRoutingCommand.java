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
package com.vmops.agent.api;

import java.util.Map;

import com.vmops.host.Host;
import com.vmops.network.NetworkEnums.RouterPrivateIpStrategy;
import com.vmops.vm.State;

public class StartupRoutingCommand extends StartupComputingCommand {
    public StartupRoutingCommand() {
        super();
        getHostDetails().put(RouterPrivateIpStrategy.class.getCanonicalName(), RouterPrivateIpStrategy.DcGlobal.toString());
    }
    
    public StartupRoutingCommand(int cpus,
                                 long speed,
                                 long memory,
                                 long dom0MinMemory,
                                 String caps,
                                 Host.HypervisorType hypervisorType,
                                 RouterPrivateIpStrategy privIpStrategy,
                                 Map<String, State> vms) {
        super(cpus, speed, memory, dom0MinMemory, caps, hypervisorType, vms);
        getHostDetails().put(RouterPrivateIpStrategy.class.getCanonicalName(), privIpStrategy.toString());
     }
    
    public StartupRoutingCommand(int cpus,
    		long speed,
    		long memory,
    		long dom0MinMemory,
    		String caps,
    		int[] ports,
    		Map<String, State> vms) {
    	this(cpus, speed, memory, dom0MinMemory, caps, Host.HypervisorType.Xen, RouterPrivateIpStrategy.DcGlobal, vms);
    }

}

