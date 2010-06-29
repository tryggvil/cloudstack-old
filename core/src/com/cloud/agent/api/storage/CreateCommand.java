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

import com.cloud.storage.StoragePoolVO;



/**
 * CreateCommand is sent to create vm images for both vm instances and
 * domain routers.
 */
public class CreateCommand extends StorageCommand {
    long accountId;
    String vmName;
    String templateMount;
    String templatePath;
    String templateName;
    String dataDiskTemplateHost;
    String dataDiskTemplatePath;
    String rootdiskFolder;
    String datadiskFolder;
    String datadiskName;
    int dataDiskSizeGB;
    int rootDiskSizeGB;
    StoragePoolVO pool;
    String localPath;
    
    protected CreateCommand() {
    }
    
    /**
     * Used for vm instance creation.
     * @param accountId account id
     * @param name vm name
     * @param templatePath path to the template.
     * @param dataDiskTemplateHost host of the data disk template.
     * @param dataDiskTemplatePath path to the data disk template.
     * @param rootdiskFolder folder where rootdisks are stored
     * @param datadiskFolder folder where datadisks are stored
     * @param datadiskName name of the datadisk
     * @param diskSize data disk size in Gigabytes
     */

    public CreateCommand(long accountId, String name, String templatePath, String dataDiskTemplateHost, String dataDiskTemplatePath, String rootdiskFolder, String datadiskFolder, String datadiskName, int rootDiskSize, int dataDiskSize, StoragePoolVO pool, String templateMount, String templateName) {
        this.accountId = accountId;
        this.templatePath = templatePath;
        this.dataDiskTemplateHost = dataDiskTemplateHost;
        this.dataDiskTemplatePath = dataDiskTemplatePath;
        this.rootdiskFolder = rootdiskFolder;
        this.datadiskFolder = datadiskFolder;
        this.datadiskName = datadiskName;
        this.vmName = name;
        this.rootDiskSizeGB = rootDiskSize;
        this.dataDiskSizeGB = dataDiskSize;
        this.pool = pool;
        this.templateMount = templateMount;
        this.templateName = templateName;
    }
    
    
    /**
     * Used for vm instance creation.
     * @param accountId account id
     * @param name vm name
     * @param templatePath path to the template.
     * @param dataDiskTemplateHost host of the data disk template.
     * @param dataDiskTemplatePath path to the data disk template.
     * @param path vm path.
     */
    public CreateCommand(long accountId, String name, String templatePath, String dataDiskTemplateHost, String dataDiskTemplatePath, String rootDiskFolder, String dataDiskFolder, String dataDiskName, int dataDiskSize, StoragePoolVO pool, String templateMount, String templateName) {
        this(accountId, name, templatePath, dataDiskTemplateHost, dataDiskTemplatePath, rootDiskFolder, dataDiskFolder, dataDiskName, 0, dataDiskSize, pool, templateMount, templateName);
    }
    
    /**
     * Used for router creation which does not include data disks.
     * @param accountId account this belongs to.
     * @param name vm name
     * @param templatePath template path
     * @param path vm path
     */

    public CreateCommand(long accountId, String name, String templatePath, String rootdiskFolder, StoragePoolVO pool, String templateMount, String templateName) {
    	this(accountId, name, templatePath, null, null, rootdiskFolder, null, null, 0, 0, pool, templateMount, templateName);
    }
    
    /**
     * Used for VM creation with blank root disks; done when booting from ISO.
     * @param accountId
     * @param name
     * @param rootdiskSize in GB
     * @param rootdiskFolder
     */

    public CreateCommand(long accountId, String name, int rootdiskSize, String rootdiskFolder, StoragePoolVO pool, String templateMount, String templateName) {
    	this(accountId, name, null, null, null, rootdiskFolder, null, null, rootdiskSize, 0, pool, templateMount, templateName);
	}
     
    public StoragePoolVO getPool() {
        return pool;
    }
    
    public long getAccountId() {
        return accountId;
    }
    
    public String getDataDiskTemplateHost() {
        return dataDiskTemplateHost;
    }

    public String getRootdiskFolder() {
        return rootdiskFolder;
    }
    
    public String getDatadiskFolder() {
    	return datadiskFolder;
    }
    
    public String getDatadiskName() {
    	return datadiskName;
    }

    public String getTemplatePath() {
        return templatePath;
    }
    
    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getDataDiskTemplatePath() {
        return dataDiskTemplatePath;
    }
    
    public String getVmName() {
        return vmName;
    }

    public long getDataDiskSizeByte() {
        return dataDiskSizeGB * 1024L * 1024L * 1024L;
    }
 
    
    public int getDataDiskSizeGB() {
		return dataDiskSizeGB;
	}
    
    public long getRootDiskSizeByte() {
        return rootDiskSizeGB * 1024L * 1024L * 1024L;
    }
    
    public int getRootDiskSizeGB() {
    	return rootDiskSizeGB;
    }
    
    public void setLocalPath(String localPath) {
    	this.localPath = localPath;
    }

    public String getLocalPath() {
    	return this.localPath;
    }
	@Override
    public boolean executeInSequence() {
        return true;
    }
	
	public String getTemplateMount() {
	    return templateMount;
	}
}
