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

package com.vmops.dc;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="vlan")
public class VlanVO implements Vlan {
	    
	@Id
	@GeneratedValue(strategy=GenerationType.IDENTITY)
	@Column(name="id") 
	Long id;
	
	@Column(name="vlan_id") 
	String vlanId;
	
	@Column(name="vlan_gateway") 
	String vlanGateway;
	
	@Column(name="vlan_netmask") 
	String vlanNetmask;
	
	@Column(name="data_center_id") 
	long dataCenterId;
	
	@Column(name="vlan_name") 
	String vlanName;
	
	@Column(name="description") 
	String description;
	
	@Column(name="vlan_type")
	@Enumerated(EnumType.STRING) 
	VlanType vlanType = VlanType.DcExternal; 
	
	public VlanVO(String vlanId, String vlanGateway, String vlanNetmask, long dataCenterId, String description, String name) {
		this.vlanId = vlanId;
		this.vlanGateway = vlanGateway;
		this.vlanNetmask = vlanNetmask;
		this.dataCenterId = dataCenterId;
		this.description = description;
		this.vlanName = name;
	}
	
	public VlanVO() {
		
	}
	
	public long getId() {
		return id.longValue();
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
	
	public long getDataCenterId() {
		return dataCenterId;
	}

	public void setVlanName(String vlanName) {
		this.vlanName = vlanName;
	}

	public String getVlanName() {
		return vlanName;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getDescription() {
		return description;
	}

	public void setVlanType(VlanType vlanType) {
		this.vlanType = vlanType;
	}

	public VlanType getVlanType() {
		return vlanType;
	}
}
