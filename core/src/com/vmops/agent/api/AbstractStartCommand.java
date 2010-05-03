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

package com.vmops.agent.api;

import java.util.List;
import java.util.Map;

import com.vmops.storage.VolumeVO;
import com.vmops.storage.VirtualMachineTemplate.BootloaderType;

public abstract class AbstractStartCommand extends Command {
	
	protected String vmName;
	protected String storageHosts[] = new String[2];
	protected List<VolumeVO> volumes;
	protected boolean mirroredVols = false;
	protected BootloaderType bootloader = BootloaderType.PyGrub;
	protected Map<String, Integer> mappings = null;

	public AbstractStartCommand(String vmName, String storageHost, List<VolumeVO> vols, Map<String, Integer> mappings) {
		super();
		this.vmName = vmName;
		this.storageHosts[0] = storageHost;
		this.volumes = vols;
		this.mappings = mappings;
	}
	
	public AbstractStartCommand(String vmName, String storageHost, List<VolumeVO> vols, Map<String, Integer> mappings, boolean mirrored) {
		super();
		this.vmName = vmName;
		this.storageHosts[0] = storageHost;
		this.volumes = vols;
		this.mirroredVols = mirrored;
		this.mappings = mappings;
	}

	public AbstractStartCommand(String vmName, String[] storageHosts, List<VolumeVO> volumes, Map<String, Integer> mappings, boolean mirroredVols) {
		super();
		this.vmName = vmName;
		this.storageHosts = storageHosts;
		this.volumes = volumes;
		this.mirroredVols = mirroredVols;
		this.mappings = mappings;
	}

	public BootloaderType getBootloader() {
		return bootloader;
	}

	public void setBootloader(BootloaderType bootloader) {
		this.bootloader = bootloader;
	}

	protected AbstractStartCommand() {
		super();
	}

	public AbstractStartCommand(String vmName, String storageHost0, String storageHost1, List<VolumeVO> vols, Map<String, Integer> mappings) {
		super();
		this.vmName = vmName;
		this.storageHosts[0] = storageHost0;
		this.storageHosts[1] = storageHost1;
		this.volumes = vols;
		this.mirroredVols = true;
		this.mappings = mappings;
	}
	
	public Map<String, Integer> getMappings() {
	    return mappings;
	}
	
	public void setMappings(Map<String, Integer> mappings) {
	    this.mappings = mappings;
	}

	public List<VolumeVO> getVolumes() {
		return volumes;
	}

	public String getVmName() {
	    return vmName;
	}

	public String getStorageHost() {
		return storageHosts[0];
	}

	public boolean isMirroredVols() {
		return mirroredVols;
	}

	public void setMirroredVols(boolean mirroredVols) {
		this.mirroredVols = mirroredVols;
	}
	
	public String [] getStorageHosts() {
		return storageHosts;
	}

}