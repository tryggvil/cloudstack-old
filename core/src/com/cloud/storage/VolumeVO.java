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
package com.cloud.storage;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import com.cloud.async.AsyncInstanceCreateStatus;
import com.cloud.utils.db.GenericDao;
import com.google.gson.annotations.Expose;

@Entity
@Table(name="volumes")
public class VolumeVO implements Volume {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id")
    Long id;
    
    @Expose
    @Column(name="name")
    String name;
    
    @Expose
    @Column(name="name_label")
    String nameLabel;
    
    @Column(name="pool_id")
    Long poolId;

    @Column(name="account_id")
    long accountId;

    @Column(name="domain_id")
    long domainId;
    
    @Column(name="instance_id")
    Long instanceId = null;
    
    @Expose
    @Column(name="size")
    long size;

    @Expose
    @Column(name="folder")
    String folder;
    
    @Expose
    @Column(name="path")
    String path;
    
    @Expose
    @Column(name="iscsi_name")
    String iscsiName;
    
    @Column(name="pod_id")
    Long podId;
    
    @Column(name="destroyed")
    boolean destroyed = false;
    
    @Column(name="created")
    Date created;
    
    @Column(name="data_center_id")
    long dataCenterId;
    
    @Expose
    @Column(name="host_ip")
    String hostip;

    @Column(name="disk_offering_id")
    Long diskOfferingId;

    @Expose
    @Column(name="mirror_vol")
    Long mirrorVolume;
    
    @Column(name="template_name")
    String templateName;
    
    @Column(name="template_id")
    Long templateId;
    
    @Column(name="first_snapshot_backup_uuid")
    String firstSnapshotBackupUuid;

    @Expose
    @Column(name="volume_type")
    @Enumerated(EnumType.STRING)
	VolumeType volumeType = Volume.VolumeType.UNKNOWN;

    @Expose
    @Column(name="mirror_state")
    @Enumerated(EnumType.STRING)
	MirrorState mirrorState = Volume.MirrorState.NOT_MIRRORED;
    
    @Column(name=GenericDao.REMOVED_COLUMN)
    Date removed;
    
    @Expose
    @Column(name="resource_type")
    @Enumerated(EnumType.STRING)
	StorageResourceType storageResourceType;
    
    @Expose
    @Column(name="status", updatable = true, nullable=false)
    @Enumerated(value=EnumType.STRING)
    private AsyncInstanceCreateStatus status;
    
    @Column(name="updated")
    @Temporal(value=TemporalType.TIMESTAMP)
    Date updated;
    
    public VolumeVO(String name, long size, String folder, String path, String iscsiName, String hostIp, Long mirrorVolume, Volume.MirrorState state, Volume.VolumeType type) {
    	this(null, name, -1, -1, -1, -1, null, type, folder, path, iscsiName, size);
    }
    
    public VolumeVO(Long id, String name, long dcId, long podId, long accountId, long domainId, Long instanceId, String folder, String path, long size, Volume.VolumeType vType) {
        this.id = id;
        this.name = name;
        this.accountId = accountId;
        this.domainId = domainId;
        this.instanceId = instanceId;
        this.folder = folder;
        this.path = path;
        this.size = size;
        this.podId = podId;
        this.dataCenterId = dcId;
        this.volumeType = vType;
        this.status = AsyncInstanceCreateStatus.Created;
    }

    public VolumeVO(Long id, String name, long dcId, long podId, long accountId, long domainId, Long instanceId, Volume.VolumeType vType, String folder, String path, String iscsiName, long size) {
        this.id = id;
        this.name = name;
        this.accountId = accountId;
        this.domainId = domainId;
        this.instanceId = instanceId;
        this.folder = folder;
        this.path = path;
        this.size = size;
        this.podId = podId;
        this.dataCenterId = dcId;
        this.volumeType = vType;
        this.iscsiName = iscsiName;
        this.status = AsyncInstanceCreateStatus.Created;
    }
    
    public String getIscsiName() {
		return iscsiName;
	}

	public Long getId() {
        return id;
	}
	
    @Override
    public Long getPodId() {
        return podId;
    }

    @Override
    public long getDataCenterId() {
        return dataCenterId;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getNameLabel() {
    	return nameLabel;
    }

    @Override
    public long getAccountId() {
        return accountId;
    }

    @Override
    public long getDomainId() {
        return domainId;
    }
    
    @Override
    public String getFolder() {
    	return folder;
    }

    @Override
    public String getPath() {
        return path;
    }
    
    protected VolumeVO() {
    }
    
    @Override
    public long getSize() {
        return size;
    }
    
	public void setSize(long size) {
		this.size = size;
	}
    
    @Override
    public Long getInstanceId() {
    	return instanceId;
    }
    
	@Override
	public VolumeType getVolumeType() {
		return volumeType;
	}
	
	public void setId(Long id) {
		this.id = id;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public void setNameLabel(String nameLabel) {
		this.nameLabel = nameLabel;
	}
	
	public void setFolder(String folder) {
		this.folder = folder;
	}

	public void setAccountId(long accountId) {
		this.accountId = accountId;
	}

    public void setDomainId(long domainId) {
        this.domainId = domainId;
    }

	public void setInstanceId(Long instanceId) {
		this.instanceId = instanceId;
	}
	
	public void setPath(String path) {
		this.path = path;
	}

	public String getHostIp() {
		return hostip;
	}

	public void setHostIp(String hostip) {
		this.hostip = hostip;
	}

	public void setIscsiName(String iscsiName) {
		this.iscsiName = iscsiName;
	}

	public void setPodId(Long podId) {
		this.podId = podId;
	}

	public void setDataCenterId(long dataCenterId) {
		this.dataCenterId = dataCenterId;
	}

	public void setVType(VolumeType type) {
		volumeType = type;
	}
	
	public boolean getDestroyed() {
		return destroyed;
	}

	public Date getCreated() {
		return created;
	}
	
//	public void setCreated(Date created) {
//		this.created = created;
//	}
	
	public void setDestroyed(boolean destroyed) {
		this.destroyed = destroyed;
	}
	
	public Date getRemoved() {
	    return removed;
	}
	
	public void setRemoved(Date removed) {
		this.removed = removed;
	}

	public MirrorState getMirrorState() {
		return mirrorState;
	}

	public void setMirrorState(MirrorState mirrorState) {
		this.mirrorState = mirrorState;
	}

	public Long getDiskOfferingId() {
		return diskOfferingId;
	}

	public void setDiskOfferingId(Long diskOfferingId) {
		this.diskOfferingId = diskOfferingId;
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public Long getTemplateId() {
		return templateId;
	}

	public void setTemplateId(Long templateId) {
		this.templateId = templateId;
	}
	
	public String getFirstSnapshotBackupUuid() {
	    return firstSnapshotBackupUuid;
	}
	
	public void setFirstSnapshotBackupUuid(String firstSnapshotBackupUuid) {
	    this.firstSnapshotBackupUuid = firstSnapshotBackupUuid;
	}
	
	public Long getMirrorVolume() {
		return mirrorVolume;
	}

	public void setMirrorVolume(Long mirrorVolume) {
		this.mirrorVolume = mirrorVolume;
	}

	@Override
	public long getStorageResourceId() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public StorageResourceType getStorageResourceType() {
		return storageResourceType;
	}

	public void setStorageResourceType(StorageResourceType storageResourceType2) {
		this.storageResourceType = storageResourceType2;
	}

	public Long getPoolId() {
		return poolId;
	}
	
	public void setPoolId(Long poolId) {
		this.poolId = poolId;
	}
	
	@Override
    public AsyncInstanceCreateStatus getStatus() {
		return status;
	}
	
	@Override
	public void setStatus(AsyncInstanceCreateStatus status) {
		this.status = status;
	}
	
	public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }

    public Lun getLun() {
	    return new Lun(hostip, iscsiName);
	}
	
	public class Lun {
	    private final String ip;
	    private String iqn;
	    private String lun;
	    
	    protected Lun(String ip, String iscsiName) {
	        this.ip = ip;
	        String[] str = iscsiName.split(":lu:");
	        if (str != null && str.length == 2) {
	            iqn = str[0];
	            lun = str[1];
	        } else {
	            iqn = null;
	            lun = null;
	        }
	    }
	    
	    public Lun(String ip, String iqn, String lun) {
	        this.ip = ip;
	        this.iqn = iqn;
	        this.lun = lun;
	    }
	    
	    public String getIp() {
	        return ip;
	    }
	    
	    public String getIqn() {
	        return iqn;
	    }
	    
	    public String getLun() {
	        return lun;
	    }
	    
	    public boolean isIscsi() {
	        return lun != null;
	    }
	    
	    protected String getIscsiName() {
	        return iqn + ":lu:" + lun;
	    }
	}
}
