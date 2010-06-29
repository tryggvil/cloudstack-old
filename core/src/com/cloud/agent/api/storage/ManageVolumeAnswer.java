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
package com.cloud.agent.api.storage;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;

public class ManageVolumeAnswer extends Answer {


	private Long size;
	
	// Used for XenServer
	private String srName;
	private String uuid;
	
	protected ManageVolumeAnswer() {
    }
	
	// Used for KVM
	public ManageVolumeAnswer(Command command, boolean success, String details, Long size) {
		super(command, success, details);
		this.size = size;
		this.uuid = null;
	}
	
	// Used for XenServer
	public ManageVolumeAnswer(Command command, boolean success, String details, Long size, String srName, String uuid) {
		super(command, success, details);
		this.srName = srName;
		this.size = size;
		this.uuid = uuid;
	}
    
	public Long getSize() {
		return size;
	}
	
	public String getSrName() {
		return srName;
	}
	
	public String getUuid() {
		return uuid;
	}
	
}
