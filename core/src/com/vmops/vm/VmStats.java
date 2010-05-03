package com.vmops.vm;

public interface VmStats {
	public double getVCPUUtilisation();
    public double getDiskReadKBs();
    public double getDiskWriteKBs();
    public double getNetworkReadKBs();
    public double getNetworkWriteKBs();
}
