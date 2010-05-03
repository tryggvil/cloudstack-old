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

import com.vmops.storage.VolumeVO;

public class DestroyCommand extends StorageCommand {
    String volume;
    List<VolumeVO> volumes;
    
    protected DestroyCommand() {
    }
    
    public DestroyCommand(String parent, List<VolumeVO> volumes) {
        this.volume = parent;
        this.volumes = volumes;
    }
    
    public List<VolumeVO> getVolumes() {
    	return volumes;
    }

    public String getVolumeName() {
        return volume;
    }
    
    @Override
    public boolean executeInSequence() {
        return true;
    }
}
