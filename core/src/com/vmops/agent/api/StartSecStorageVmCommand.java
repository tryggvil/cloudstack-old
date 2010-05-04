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

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import com.vmops.storage.VolumeVO;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NfsUtils;
import com.vmops.vm.SecondaryStorageVmVO;

public class StartSecStorageVmCommand extends AbstractStartCommand {

    private SecondaryStorageVmVO secStorageVm;
    private int proxyCmdPort;
    private String mgmt_host;
    private int mgmt_port;
    
	protected StartSecStorageVmCommand() {
	}
	
    public StartSecStorageVmCommand(int proxyCmdPort, SecondaryStorageVmVO secStorageVm, String vmName, String storageHost, 
    		List<VolumeVO> vols, Map<String, Integer> mappings,
    		String mgmtHost, int mgmtPort) {
    	super(vmName, storageHost, vols, mappings);
    	this.proxyCmdPort = proxyCmdPort;
    	this.secStorageVm = secStorageVm;

    	this.mgmt_host = mgmtHost;
    	this.mgmt_port = mgmtPort;
    }
	
	@Override
	public boolean executeInSequence() {
        return true;
	}
	
	public SecondaryStorageVmVO getSecondaryStorageVmVO() {
		return secStorageVm;
	}
	
	public int getProxyCmdPort() {
		return proxyCmdPort;
	}
	
	
	public String getManagementHost() {
		return mgmt_host;
	}
	
	public int getManagementPort() {
		return mgmt_port;
	}
	
	public String getBootArgs() {
		String basic = " eth0ip=" + "0.0.0.0" + " eth0mask=" + "255.255.255.0" + " eth1ip="
        + secStorageVm.getPrivateIpAddress() + " eth1mask=" + secStorageVm.getPrivateNetmask() + " eth2ip="
        + secStorageVm.getPublicIpAddress() + " eth2mask=" + secStorageVm.getPublicNetmask() + " gateway=" + secStorageVm.getGateway()
		+ " dns1=" + secStorageVm.getDns1() + " template=domP";
		if (secStorageVm.getDns2() != null) {
			basic = basic + " dns2=" + secStorageVm.getDns2();
		}
		basic = basic + " host=" + mgmt_host + " port=" + mgmt_port;
		String mountStr = null;
		try {
			mountStr = NfsUtils.url2Mount(secStorageVm.getNfsShare());
		} catch (URISyntaxException e1) {
			throw new VmopsRuntimeException("NFS url malformed in database? url=" + secStorageVm.getNfsShare());
		}
		basic = basic + " mount.path=" + mountStr + " guid=" + secStorageVm.getGuid();
		basic = basic + " resource=com.vmops.storage.resource.NfsSecondaryStorageResource";
		basic = basic + " instance=SecStorage";
		return basic;
	}
}
