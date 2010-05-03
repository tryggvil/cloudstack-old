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

import java.util.Date;

import com.vmops.serializer.Param;

public class CreatePrivateTemplateResultObject {
	@Param(name="id")
	private long id;
	
	@Param(name="name")
	private String name;
	
	@Param(name="displaytext")
	private String displayText;
	
	@Param(name="ispublic", propName="public")
	private boolean isPublic;
	
	@Param(name="requireshvm")
	private boolean requiresHvm;
	
	@Param(name="bits")
	private int bits;
	
	@Param(name="created")
	private Date created;
	
	@Param(name="isready", propName="ready")
	private boolean isReady;
	
	@Param(name="passwordenabled")
	private boolean passwordEnabled;
	
	public long getId() {
		return id;
	}
	
	public void setId(long id) {
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getDisplayText() {
		return displayText;
	}
	
	public void setDisplayText(String displayText) {
		this.displayText = displayText;
	}
	
	public boolean isPublic() {
		return isPublic;
	}
	
	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}
	
	public boolean isRequiresHvm() {
		return requiresHvm;
	}
	
	public void setRequiresHvm(boolean requiresHvm) {
		this.requiresHvm = requiresHvm;
	}
	
	public int getBits() {
		return bits;
	}
	
	public void setBits(int bits) {
		this.bits = bits;
	}
	
	public Date getCreated() {
		return created;
	}
	
	public void setCreated(Date created) {
		this.created = created;
	}
	
	public boolean isReady() {
		return isReady;
	}
	
	public void setReady(boolean isReady) {
		this.isReady = isReady;
	}
	
	public boolean isPasswordEnabled() {
		return passwordEnabled;
	}
	
	public void setPasswordEnabled(boolean passwordEnabled) {
		this.passwordEnabled = passwordEnabled;
	}
}
