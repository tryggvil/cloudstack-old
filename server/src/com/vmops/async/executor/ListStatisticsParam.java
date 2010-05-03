package com.vmops.async.executor;

import java.util.List;

public class ListStatisticsParam {
	
	public enum StatisticsType { UserVm, Host;}

	private StatisticsType type;
	
	// Used for UserVm
	private List<Long> vmIds;
	
	// Used for Host
	private List<Long> hostIds;

	public ListStatisticsParam() {
	}
	
	public StatisticsType getType() {
		return type;
	}
	
	public void setType(StatisticsType type) {
		this.type = type;
	}
	
	public List<Long> getVmIds() {
		return vmIds;
	}
	
	public void setVmIds(List<Long> vmIds) {
		this.vmIds = vmIds;
	}
	
	public List<Long> getHostIds() {
		return hostIds;
	}
	
	public void setHostIds(List<Long> hostIds) {
		this.hostIds = hostIds;
	}

}
