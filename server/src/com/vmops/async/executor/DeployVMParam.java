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

package com.vmops.async.executor;

public class DeployVMParam extends VMOperationParam {
	private long accountId;
	private long dataCenterId;
	private long serviceOfferingId;
	private long dataDiskOfferingId;
	private long rootDiskOfferingId;
	private long templateId;
	private String domain;
	private String password;
	private String displayName;
	private String group;
	private String userData;
	private long domainId;
	
	public DeployVMParam() {
	}
	
	public DeployVMParam(long userId, long accountId, long dataCenterId, 
		long serviceOfferingId, long dataDiskOfferingId, long templateId,
		long rootDiskOfferingId, String domain, String password,
		String displayName, String group, String userData) {
		
		setUserId(userId);
		this.accountId = accountId;
		this.dataCenterId = dataCenterId;
		this.serviceOfferingId = serviceOfferingId;
		this.dataDiskOfferingId = dataDiskOfferingId;
		this.templateId = templateId;
		this.rootDiskOfferingId = rootDiskOfferingId;
		this.domain = domain;
		this.password = password;
		this.displayName = displayName;
		this.group = group;
		this.userData = userData;
	}

	public long getAccountId() {
		return accountId;
	}
	
	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}
	
	public long getDataCenterId() {
		return dataCenterId;
	}
	
	public void setDataCenterId(long dataCenterId) {
		this.dataCenterId = dataCenterId;
	}
	
	public long getServiceOfferingId() {
		return serviceOfferingId;
	}
	
	public void setServiceOfferingId(long serviceOfferingId) {
		this.serviceOfferingId = serviceOfferingId;
	}
	
	public long getDataDiskOfferingId() {
		return dataDiskOfferingId;
	}
	
	public void setDataDiskOfferingId(long dataDiskOfferingId) {
		this.dataDiskOfferingId = dataDiskOfferingId;
	}
	
	public long getrootDiskOfferingId() {
		return rootDiskOfferingId;
	}
	
	public void setrootDiskOfferingId(long rootDiskOfferingId) {
		this.rootDiskOfferingId = rootDiskOfferingId;
	}
	
	public long getTemplateId() {
		return templateId;
	}
	
	public void setTemplateId(long templateId) {
		this.templateId = templateId;
	}
	
	public String getDomain() {
		return domain;
	}
	
	public void setDomain(String domain) {
		this.domain = domain;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String getGroup() {
		return group;
	}
	
	public void setGroup(String group) {
		this.group = group;
	}
	public long getDomainId() {
		return domainId;
	}
	public void setDomainId(long domainId) {
		this.domainId = domainId;
	}

	public void setUserData(String userData) {
		this.userData = userData;
	}

	public String getUserData() {
		return userData;
	}
}
