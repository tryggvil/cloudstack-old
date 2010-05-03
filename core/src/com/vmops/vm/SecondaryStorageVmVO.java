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
package com.vmops.vm;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * SecondaryStorageVmVO domain object
 */

@Entity
@Table(name="secondary_storage_vm")
@PrimaryKeyJoinColumn(name="id")
@DiscriminatorValue(value="SecondaryStorageVm")
public class SecondaryStorageVmVO extends VMInstanceVO implements SecondaryStorageVm {

    @Column(name="gateway", nullable=false)
    private String gateway;
    
    @Column(name="dns1")
    private String dns1;
    
    @Column(name="dns2")
    private String dns2;

    @Column(name="public_ip_address", nullable=false)
    private String publicIpAddress;
    
    @Column(name="public_mac_address", nullable=false)
    private String publicMacAddress;
    
    @Column(name="public_netmask", nullable=false)
    private String publicNetmask;
    
    @Column(name="vlan_db_id")
    private Long vlanDbId;
    
    @Column(name="vlan_id")
    private String vlanId;
    
    @Column(name="domain", nullable=false)
    private String domain;
    
    @Column(name="ram_size", updatable=false, nullable=false)
    private int ramSize;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name="last_update", updatable=true, nullable=true)
    private Date lastUpdateTime;
    
    
    public SecondaryStorageVmVO(
    		long id,
            String name,
            State state,
            String privateMacAddress,
            String privateIpAddress,
            String privateNetmask,
            long templateId,
            long guestOSId,
            String publicMacAddress,
            String publicIpAddress,
            String publicNetmask,
            Long vlanDbId,
            String vlanId,
            long podId,
            long dataCenterId,
            String gateway,
            Long hostId,
            String dns1,
            String dns2,
            String domain,
            int ramSize) {
    	super(id, name, name, state, Type.SecondaryStorageVm, templateId, guestOSId,
    			privateMacAddress, privateIpAddress, privateNetmask, dataCenterId, podId, true, hostId, null, null);
    	this.gateway = gateway;
    	this.publicIpAddress = publicIpAddress;
    	this.publicNetmask = publicNetmask;
    	this.publicMacAddress = publicMacAddress;
    	this.vlanDbId = vlanDbId;
    	this.vlanId = vlanId;
    	this.dns1 = dns1;
    	this.dns2 = dns2;
    	this.domain = domain;
    	this.ramSize = ramSize;
    }
    
    protected SecondaryStorageVmVO() {
        super();
    }

    public void setGateway(String gateway) {
    	this.gateway = gateway;
    }
    
    public void setDns1(String dns1) {
    	this.dns1 = dns1;
    }
    
    public void setDns2(String dns2) {
    	this.dns2 = dns2;
    }
    
    public void setDomain(String domain) {
    	this.domain = domain;
    }
    
    public void setPublicIpAddress(String publicIpAddress) {
    	this.publicIpAddress = publicIpAddress;
    }
    
    public void setPublicNetmask(String publicNetmask) {
    	this.publicNetmask = publicNetmask;
    }
    
    public void setPublicMacAddress(String publicMacAddress) {
    	this.publicMacAddress = publicMacAddress;
    }
    
    public void setRamSize(int ramSize) {
    	this.ramSize = ramSize;
    }
    
    public void setLastUpdateTime(Date time) {
    	this.lastUpdateTime = time;
    }
  
    @Override
	public String getGateway() {
		return this.gateway;
	}
	
    @Override
	public String getDns1() {
    	return this.dns1;
	}
	
    @Override
	public String getDns2() {
    	return this.dns2;
	}
	
    @Override
	public String getPublicIpAddress() {
    	return this.publicIpAddress;
	}
	
    @Override
	public String getPublicNetmask() {
    	return this.publicNetmask;
	}
	
    @Override
	public String getPublicMacAddress() {
		return this.publicMacAddress;
	}
    
    @Override
	public Long getVlanDbId() {
    	return vlanDbId;
    }
    
    @Override
	public String getVlanId() {
    	return vlanId;
    }
    
    @Override
    public String getDomain() {
    	return this.domain;
    }
	
    @Override
	public int getRamSize() {
    	return this.ramSize;
    }
 
    
    @Override
    public Date getLastUpdateTime() {
    	return this.lastUpdateTime;
    }
    
  

}
