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
