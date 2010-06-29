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

package com.cloud.agent.api.routing;


public class VmDataCommand extends RoutingCommand {

	String routerPrivateIpAddress;
	String routerPublicIpAddress;
	String vmIpAddress;
    String userData;
    String serviceOffering;
    String zoneName;
    String guestIP;
    String vmName;
    String vmInstanceName;
    
    protected VmDataCommand() {    	
    }
    
    @Override
    public boolean executeInSequence() {
        return true;
    }
    
    public VmDataCommand(String routerPrivateIpAddress, String routerPublicIpAddress, String vmIpAddress, String userData, String serviceOffering, String zoneName, String guestIP, String vmName, String vmInstanceName) {
    	this.routerPrivateIpAddress = routerPrivateIpAddress;
    	this.routerPublicIpAddress = routerPublicIpAddress;
    	this.vmIpAddress = vmIpAddress;
    	this.userData = userData;
    	this.serviceOffering = serviceOffering;
    	this.zoneName = zoneName;
    	this.guestIP = guestIP;
    	this.vmName = vmName;
    	this.vmInstanceName = vmInstanceName;
    }
	
	public String getRouterPrivateIpAddress() {
		return routerPrivateIpAddress;
	}
	
	public String getRouterPublicIpAddress() {
		return routerPublicIpAddress;
	}
	
	public String getVmIpAddress() {
		return vmIpAddress;
	}
	
	public String getUserData() {
		return userData;
	}
	
	public String getServiceOffering() {
		return serviceOffering;
	}
	
	public String getZoneName() {
		return zoneName;
	}
	
	public String getGuestIP() {
		return guestIP;
	}
	
	public String getVmName() {
		return vmName;
	}
	
	public String getVmInstanceName() {
		return vmInstanceName;
	}

	public void setUserData(String userData) {
		this.userData = userData;
	}
	
}
