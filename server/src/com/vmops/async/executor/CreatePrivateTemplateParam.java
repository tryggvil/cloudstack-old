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

package com.vmops.async.executor;

public class CreatePrivateTemplateParam {
	private long userId;
	private long volumeId;
	private long snapshotId;
	private long guestOsId;
	private String name;
	private String description;
	private Boolean requiresHvm;
	private Integer bits;
	private Boolean passwordEnabled;
	private Boolean isPublic;

	public CreatePrivateTemplateParam() {
	}

	public CreatePrivateTemplateParam(long userId, long volumeId, long guestOsId, String name, String description, Boolean requiresHvm, Integer bits, Boolean passwordEnabled, Boolean isPublic) {
		this.userId = userId;
		this.name = name;
		this.description = description;
		this.volumeId = volumeId;
		this.guestOsId = guestOsId;
		this.requiresHvm = requiresHvm;
		this.bits = bits;
		this.passwordEnabled = passwordEnabled;
		this.isPublic = isPublic;
	}
	
	public long getUserId() {
		return userId;
	}
	
	public void setUserId(long userId) {
		this.userId = userId;
	}
	
	public long getVolumeId() {
		return volumeId;
	}
	
	public void setVmId(long volumeId) {
		this.volumeId = volumeId;
	}
	
	public long getSnapshotId() {
		return snapshotId;
	}
	
	public void setSnapshotId(long snapshotId) {
		this.snapshotId = snapshotId;
	}
	
	public long getGuestOsId() {
		return guestOsId;
	}
	
	public void setGuestOsId(long guestOsId) {
		this.guestOsId = guestOsId;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getRequiresHvm() {
	    return requiresHvm;
	}

	public void setRequiresHvm(Boolean requiresHvm) {
	    this.requiresHvm = requiresHvm;
	}

	public Integer getBits() {
	    return bits;
	}

	public void setBits(Integer bits) {
	    this.bits = bits;
	}

	public Boolean isPasswordEnabled() {
	    return passwordEnabled;
	}

	public void setPasswordEnabled(Boolean passwordEnabled) {
	    this.passwordEnabled = passwordEnabled;
	}
	
	public Boolean isPublic() {
		return isPublic;
	}
	
	public void setIsPublic(Boolean isPublic) {
		this.isPublic = isPublic;
	}
}
