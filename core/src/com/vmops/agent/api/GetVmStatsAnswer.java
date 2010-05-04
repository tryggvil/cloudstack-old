package com.vmops.agent.api;

import java.util.HashMap;

public class GetVmStatsAnswer extends Answer {
	
	HashMap<String, VmStatsEntry> vmStatsMap;
	
	public GetVmStatsAnswer(GetVmStatsCommand cmd, HashMap<String, VmStatsEntry> vmStatsMap) {
		super(cmd);
		this.vmStatsMap = vmStatsMap;
	}
	
	public HashMap<String, VmStatsEntry> getVmStatsMap() {
		return vmStatsMap;
	}
}
