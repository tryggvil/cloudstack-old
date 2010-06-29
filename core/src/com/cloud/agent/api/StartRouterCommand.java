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
package com.cloud.agent.api;

import java.util.List;
import java.util.Map;

import com.cloud.storage.VolumeVO;
import com.cloud.vm.DomainRouter;
import com.cloud.vm.DomainRouterVO;


public class StartRouterCommand extends AbstractStartCommand {

    DomainRouterVO router;

    protected StartRouterCommand() {
    	super();
    }
    
    @Override
    public boolean executeInSequence() {
        return true;
    }
    
    public StartRouterCommand(DomainRouterVO router, String routerName, String storageHost, List<VolumeVO> vols) {
        super(routerName, storageHost, vols);
        this.router = router;
    }
    
    public StartRouterCommand(DomainRouterVO router, String routerName, String storageHost0, String storageHost1, List<VolumeVO> vols) {
        super(routerName, storageHost0, storageHost1, vols);
        this.router = router;
    }
    
    
    public StartRouterCommand(DomainRouterVO router, String routerName, String[] storageIps, List<VolumeVO> vols, boolean mirroredVols) {
        super(routerName, storageIps, vols, mirroredVols);
        this.router = router;
	}

	public DomainRouter getRouter() {
        return router;
    }
	
	public String getBootArgs() {
		String basic = " eth0ip=" + router.getGuestIpAddress() + " eth0mask=" + router.getGuestNetmask() + " eth1ip="
        + router.getPrivateIpAddress() + " eth1mask=" + router.getPrivateNetmask() + " eth2ip="
        + router.getPublicIpAddress() + " eth2mask=" + router.getPublicNetmask() + " gateway=" + router.getGateway()
		+ " dns1=" + router.getDns1() + " type=router" + " name=" + router.getName();
		if (router.getDns2() != null) {
			basic = basic + " dns2=" + router.getDns2();
		}
		if (getDhcpRange() != null) {
			basic = basic + " dhcprange=" + getDhcpRange();
		}
		return basic;
	}

	public String getDhcpRange() {
		String [] range = router.getDhcpRange();
		String result = null;
		if (range[0] != null) {
			result = range[0];
			if (range[1] != null) {
				result = result + "," + range[1];
			}
		}
		return result;
	}


}
