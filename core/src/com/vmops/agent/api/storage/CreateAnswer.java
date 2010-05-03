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
package com.vmops.agent.api.storage;

import java.util.List;

import com.vmops.agent.api.Answer;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;

public class CreateAnswer extends Answer {


	private List<VolumeVO> volumes;
    
    public List<VolumeVO> getVolumes() {
		return volumes;
	}
  
	public void setVolumes(List<VolumeVO> volumes) {
		this.volumes = volumes;
	}

	protected CreateAnswer() {
    }
    
	protected VolumeVO findVolume (Volume.VolumeType vType) {
		if (volumes == null) return null;

		for (VolumeVO v: volumes) {
			if (v.getVolumeType() == vType)
				return v;
		}

		return null;
	}
	
    public CreateAnswer(CreateCommand cmd, List<VolumeVO> vols) {
        super(cmd);
        
       this.volumes = vols;
    }
    
    public CreateAnswer(CreateCommand cmd, String details) {
        super(cmd, false, details);
    }
    
    public String getRootDiskPath() {
    	VolumeVO vol = findVolume(Volume.VolumeType.ROOT);
        return vol!=null?vol.getPath():null;
    }
    
    public String getDataDiskPath() {
    	VolumeVO vol = findVolume(Volume.VolumeType.DATADISK);
        return vol!=null?vol.getPath():null;
    }
    
    public long getRootDiskSize() {
    	VolumeVO vol = findVolume(Volume.VolumeType.ROOT);
        return vol!=null?vol.getSize():0;
    }
    
    public long getDataDiskSize() {
    	VolumeVO vol = findVolume(Volume.VolumeType.DATADISK);
        return vol!=null?vol.getSize():0;
    }
}
