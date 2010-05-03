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


/**
 * @author chiradeep
 *
 */
public class IPAssocCommand extends RoutingCommand {

	private String routerName;
	private String routerIp;
	private String publicIp;
	private boolean sourceNat;
	private boolean add;
	private String vlanId;
	private String vlanGateway;
	private String vlanNetmask;

	protected IPAssocCommand() {
	}
	
	public IPAssocCommand(String routerName, String privateIpAddress, String ipAddress, boolean add) {
		this.setRouterName(routerName);
		this.routerIp = privateIpAddress;
		this.publicIp = ipAddress;
		this.add = add;
		this.sourceNat = false;
	}
	
	public IPAssocCommand(String routerName, String privateIpAddress, String ipAddress, boolean add, boolean sourceNat, String vlanId, String vlanGateway, String vlanNetmask) {
		this.setRouterName(routerName);
		this.routerIp = privateIpAddress;
		this.publicIp = ipAddress;
		this.add = add;
		this.sourceNat = sourceNat;
		this.vlanId = vlanId;
		this.vlanGateway = vlanGateway;
		this.vlanNetmask = vlanNetmask;
	}

	public String getRouterIp() {
		return routerIp;
	}

	public String getPublicIp() {
		return publicIp;
	}

	public boolean isAdd() {
		return add;
	}

    @Override
    public boolean executeInSequence() {
        return false;
    }

	public void setRouterName(String routerName) {
		this.routerName = routerName;
	}

	public String getRouterName() {
		return routerName;
	}

	public void setSourceNat(boolean sourceNat) {
		this.sourceNat = sourceNat;
	}

	public boolean isSourceNat() {
		return sourceNat;
	}
	
	public String getVlanId() {
		return vlanId;
	}
	
	public String getVlanGateway() {
		return vlanGateway;
	}
	
	public String getVlanNetmask() {
		return vlanNetmask;
	}
}
