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

import com.vmops.vm.VmStats;

/**
 * @author ahuang
 *
 */
public class GetVmStatsAnswer extends Answer implements VmStats {
	/*
    Map<String, long[]> netStats;
    Map<String, long[]> diskStats;
    */
	
	double vCPUUtilisation;
	double diskReadKBs;
	double diskWriteKBs;
	double networkReadKBs;
	double networkWriteKBs;
    
    protected GetVmStatsAnswer() {
    }
    
    public GetVmStatsAnswer(GetVmStatsCommand cmd, double vCPUUtilisation, double diskReadKBs, double diskWriteKBs, double networkReadKBs, double networkWriteKBs) {
        super(cmd);
        this.vCPUUtilisation = vCPUUtilisation;
        this.diskReadKBs = diskReadKBs;
        this.diskWriteKBs = diskWriteKBs;
        this.networkReadKBs = networkReadKBs;
        this.networkWriteKBs = networkWriteKBs;
    }

    /*
    protected String convert(Map<String, long[]> map) {
        if (map == null || map.size() == 0) {
            return null;
        }
        
        StringBuilder builder = new StringBuilder();
        Set<Map.Entry<String, long[]>> entries = map.entrySet();
        for (Map.Entry<String, long[]> entry : entries) {
            builder.append(entry.getKey()).append("%");
            builder.append(Long.toString(entry.getValue()[0])).append("%");
            builder.append(Long.toString(entry.getValue()[1])).append("&");
        }
        builder.deleteCharAt(builder.length() - 1); // get rid of last ?
        return builder.toString();
    }
    
    protected Map<String, long[]> convert(String str) {
        if (str == null || str.length() == 0) {
            return new HashMap<String, long[]>();
        }
        
        String[] tokens = str.split("&");
        Map<String, long[]> results = new HashMap<String, long[]>(tokens.length);
        for (String row : tokens) {
            String[] cols = row.split("%");
            long[] values = new long[2];
            values[0] = Long.parseLong(cols[cols.length - 1]);
            values[1] = Long.parseLong(cols[cols.length - 2]);
            String key = cols[cols.length -3];
            results.put(key, values);
        }
        
        return results;
    }
    
    public Map<String, long[]> getNetworkStats() {
        return netStats;
    }
    
    public Map<String, long[]> getDiskStats() {
        return diskStats;
    }
    
    */
    
    public double getVCPUUtilisation() {
    	return vCPUUtilisation;
    }
    
    public double getDiskReadKBs() {
    	return diskReadKBs;
    }
    
    public double getDiskWriteKBs() {
    	return diskWriteKBs;
    }
    
    public double getNetworkReadKBs() {
    	return networkReadKBs;
    }
    
    public double getNetworkWriteKBs() {
    	return networkWriteKBs;
    }
}
