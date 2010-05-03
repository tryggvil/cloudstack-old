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
import com.vmops.storage.Volume.MirrorState;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.Volume.VolumeType;

public class VolumeOperationResultObject {
	@Param(name="id")
	private long id;
    
	@Param(name="name")
	private String name;

    @Param(name="hostname")
	private String hostName;

	// @Param(name="host_id")
	// private Long hostId;
	
	// @Param(name="pool_id")
	// private Long poolId;

    @Param(name="accountid")
    long accountId;

    @Param(name="account")
    private String accountName;

    @Param(name="domainid")
    long domainId;
    
    @Param(name="domain")
    String domain;
    
    @Param(name="iscsiname")
    String iscsiName;
    
    // @Param(name="pod_id")
    // Long podId;
    
    @Param(name="destroyed")
    boolean destroyed;
    
    // @Param(name="created")
    // Date created;
    
    @Param(name="datacenterid")
    long dataCenterId;
    
    // @Param(name="host_ip")
    // String hostip;

    @Param(name="diskofferingid")
    Long diskOfferingId;

    // @Param(name="mirror_vol")
    // Long mirrorVolume;
    
    // @Param(name="template_name")
    // String templateName;
    
    // @Param(name="device_name")
    // String deviceName;

    @Param(name="type")
  	VolumeType volumeType;

    @Param(name="size")
    private long volumeSize;

    // @Param(name="removed")
    // Date removed;
    
    @Param(name="vmstate")
    private String instanceState;

    @Param(name="created")
    private Date createdDate;

    @Param(name="storagetype")
	String storageType;
    
    @Param(name="storage")
    private String storage;

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public long getId() {
		return id;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public void setHostName(String hostName) {
	    this.hostName = hostName;
	}

	public String getHostName() {
	    return hostName;
	}
	
	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}
	
	public long getAccountId() {
		return accountId;
	}

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public String getAccountName() {
        return accountName;
    }

	public void setDomainId(long domainId) {
		this.domainId = domainId;
	}
	
	public long getDomainId() {
		return domainId;
	}
	
	public void setDomain(String domain) {
		this.domain = domain;
	}
	
	public String getDomain() {
		return domain;
	}
	
	/*
	public void setSize(long size) {
		this.size = size;
	}
	*/
	
	/*
    public void setFolder(String folder) {
    	this.folder = folder;
    }
    */
    
	/*
    public void setPath(String path) {
    	this.path = path;
    }
    */
    
    public void setIscsiName(String iscsiName) {
    	this.iscsiName = iscsiName;
    }
    
    public String getIscsiName() {
    	return iscsiName;
    }
    
    /*
    public void setPodId(Long podId) {
    	this.podId = podId;
    }
    */
    
    public void setDestroyed(boolean destroyed) {
    	this.destroyed = destroyed;
    }
    
    public boolean getDestroyed() {
    	return destroyed;
    }
    
    /*
    public void setCreated(Date created) {
    	this.created = created;
    }
    */
    
    public void setDataCenterId(long dataCenterId) {
    	this.dataCenterId = dataCenterId;
    }
    
    public long getDataCenterId() {
    	return dataCenterId;
    }
    
    /*
    public void setHostIp(String hostIp) {
    	this.hostip = hostIp;
    }
    */
    
    public void setDiskOfferingId(long diskOfferingId) {
    	this.diskOfferingId = diskOfferingId;
    }
    
    public Long getDiskOfferingId() {
    	return diskOfferingId;
    }
    
    /*
    public void setMirrorVolume(long mirrorVolume) {
    	this.mirrorVolume = mirrorVolume;
    }
    */
    
    /*
    public long getMirrorVolume() {
    	return mirrorVolume;
    }
    */
    
    /*
    public void setTemplateName(String templateName) {
    	this.templateName = templateName;
    }
    */
    
    /*
    public void setDeviceName(String deviceName) {
    	this.deviceName = deviceName;
    }
    */
    
    public void setVolumeType(VolumeType volumeType) {
    	this.volumeType = volumeType;
    }
    
    public VolumeType getVolumeType() {
    	return volumeType;
    }

    public void setVolumeSize(long volumeSize) {
        this.volumeSize = volumeSize;
    }

    public long getVolumeSize() {
        return volumeSize;
    }

    /*
    public void setRemoved(Date removed) {
    	this.removed = removed;
    }
    */

    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    public Date getCreatedDate() {
        return createdDate;
    }

    public void setStorageType (String storageType) {
    	this.storageType = storageType;
    }
    
    public String getStorageType() {
    	return storageType;
    }
    
    public void setInstanceState(String state) {
    	this.instanceState = state;
    }
    
    public String getInstanceState() {
    	return instanceState;
    }
}
