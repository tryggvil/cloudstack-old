<<<<<<< .mine
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

=======
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

>>>>>>> .r7854
package com.vmops.async.executor;

import com.vmops.serializer.Param;

public class ListVmStatsResultObject {
	@Param(name="vcpuutilisation")
	double vCPUUtilisation;
	
	@Param(name="diskreadkbs")
	double diskReadKBs;
	
	@Param(name="diskwritekbs")
	double diskWriteKBs;
	
	@Param(name="networkreadkbs")
	double networkReadKBs;
	
	@Param(name="networkwritekbs")
	double networkWriteKBs;
	
	public double getVCPUUtilisation() {
    	return vCPUUtilisation;
    }
	
	public void setVCPUUtilisation(double vCPUUtilisation) {
		this.vCPUUtilisation = vCPUUtilisation;
	}
    
    public double getDiskReadKBs() {
    	return diskReadKBs;
    }
    
    public void setDiskReadKBs(double diskReadKBs) {
    	this.diskReadKBs = diskReadKBs;
    }
    
    public double getDiskWriteKBs() {
    	return diskWriteKBs;
    }
    
    public void setDiskWriteKBs(double diskWriteKBs) {
    	this.diskWriteKBs = diskWriteKBs;
    }
    
    public double getNetworkReadKBs() {
    	return networkReadKBs;
    }
    
    public void setNetworkReadKBs(double networkReadKBs) {
    	this.networkReadKBs = networkReadKBs;
    }
    
    public double getNetworkWriteKBs() {
    	return networkWriteKBs;
    }
    
    public void setNetworkWriteKBs(double networkWriteKBs) {
    	this.networkWriteKBs = networkWriteKBs;
    }
}
