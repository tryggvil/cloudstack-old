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

package com.vmops.agent.api.storage;

import com.vmops.agent.api.Command;
import com.vmops.storage.StoragePoolVO;

public class ManageVolumeCommand extends Command {
	
	boolean add;
	long sizeInGB;
	
	String folder;
	String path;
	String name;
	
	// Used for XenServer
	StoragePoolVO pool;
	String nameLabel;

	public ManageVolumeCommand() {	
	}
	
	public ManageVolumeCommand(boolean add, long sizeInGB, String folder, String path, String name, String nameLabel, StoragePoolVO pool) {
    	this.add = add;
    	this.sizeInGB = sizeInGB;
    	this.folder = folder;
        this.path = path;
        this.name = name;
        this.nameLabel = nameLabel;
        this.pool = pool;
    }
	
	@Override
    public boolean executeInSequence() {
        return true;
    }
	
	public boolean getAdd() {
		return add;
	}
	
	public String getFolder() {
		return folder;
	}
	
	public String getPath() {
		return path;
	}
	
	public String getName() {
		return name;
	}
	
	public long getSize() {
		return sizeInGB;
	}
	
	public StoragePoolVO getPool() {
		return pool;
	}
	
	public void setPool(StoragePoolVO pool) {
		this.pool = pool;
	}
	
	public long getDiskSizeByte() {
        return sizeInGB * 1024L * 1024L * 1024L;
    }
	
	public String getNameLabel() {
		return nameLabel;
	}
	
	public void setNameLabel(String nameLabel) {
		this.nameLabel = nameLabel;
	}
	
}
