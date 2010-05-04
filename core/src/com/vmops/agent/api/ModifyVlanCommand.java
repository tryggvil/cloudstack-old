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

import com.vmops.host.Host;

public class ModifyVlanCommand extends Command {
	
	boolean add;
	String vlanId;
	String vlanGateway;
	
	public ModifyVlanCommand() {
		
	}
    
    public ModifyVlanCommand(boolean add, String vlanId, String vlanGateway) {
    	this.add = add;
        this.vlanId = vlanId;
        this.vlanGateway = vlanGateway;
    }
    
    public boolean getAdd() {
    	return add;
    }
    
    public String getVlanId() {
    	return vlanId;
    }
    
    public String getVlanGateway() {
    	return vlanGateway;
    }

	@Override
    public boolean executeInSequence() {
        return false;
    }
}
