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

package com.vmops.configuration;

public interface ResourceCount {

	public enum ResourceType {
		user_vm,
	    public_ip,
	    volume,
	    snapshot,
	    template
	}
	
	public Long getId();
	
	public void setId(Long id);
	
	public ResourceType getType();
	
	public void setType(ResourceType type);
	
	public long getAccountId();
	
	public void setAccountId(long accountId);
	
	public long getCount();
	
	public void setCount(long count);

}
