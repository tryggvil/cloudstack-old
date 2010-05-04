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

import com.vmops.storage.StoragePool;
import com.vmops.storage.StoragePool.StoragePoolType;

public class StoragePoolInfo {
	String name;
	String uuid;
	String host;
	String localPath;
	String hostPath;
	StoragePool.StoragePoolType poolType;
	long capacityBytes;
	long availableBytes;
	
	protected StoragePoolInfo() {
		super();
	}

	public StoragePoolInfo(String name, String uuid, String host, String hostPath,
			String localPath, StoragePoolType poolType, long capacityBytes,
			long availableBytes) {
		super();
		this.name = name;
		this.uuid = uuid;
		this.host = host;
		this.localPath = localPath;
		this.hostPath = hostPath;
		this.poolType = poolType;
		this.capacityBytes = capacityBytes;
		this.availableBytes = availableBytes;
	}
	
	public long getCapacityBytes() {
		return capacityBytes;
	}
	public long getAvailableBytes() {
		return availableBytes;
	}

	public String getUuid() {
		return uuid;
	}
	public String getHost() {
		return host;
	}
	public String getLocalPath() {
		return localPath;
	}
	public String getHostPath() {
		return hostPath;
	}
	public StoragePool.StoragePoolType getPoolType() {
		return poolType;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	
}