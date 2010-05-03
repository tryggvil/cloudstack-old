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
package com.vmops.agent.api.routing;

import com.vmops.network.LoadBalancerConfigurator;

/**
 * @author chiradeep
 *
 */
public class LoadBalancerCfgCommand extends RoutingCommand {
	private String [] config;
	private String [] addFwRules;
	private String [] removeFwRules;;
	private String routerIp;
	
	//no-args to satisfy gson
	protected LoadBalancerCfgCommand() {
		
	}
	
	public LoadBalancerCfgCommand(String[] config, String[][] addRemoveRules, String routerIp) {
		super();
		this.config = config;
		this.addFwRules = addRemoveRules[LoadBalancerConfigurator.ADD];
		this.removeFwRules = addRemoveRules[LoadBalancerConfigurator.REMOVE];
		this.routerIp = routerIp;
	}
	
	public String getRouterIp() {
		return routerIp;
	}

	public String[] getConfig() {
		return config;
	}

	public void setConfig(String[] config) {
		this.config = config;
	}

	public String[] getAddFwRules() {
		return addFwRules;
	}

	public String[] getRemoveFwRules() {
		return removeFwRules;
	}

    @Override
    public boolean executeInSequence() {
        return false;
    }
    
}
