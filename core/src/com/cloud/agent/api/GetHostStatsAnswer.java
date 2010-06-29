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
package com.cloud.agent.api;

import com.cloud.host.HostStats;

/**
 * @author ahuang
 *
 */
public class GetHostStatsAnswer extends Answer implements HostStats {
    double cpuUtilization;
    long freeMemory;
    long totalMemory;
    double publicNetworkReadKBs;
    double publicNetworkWriteKBs;
    
    protected GetHostStatsAnswer() {
    }
    
    public GetHostStatsAnswer(GetHostStatsCommand cmd, String detail) {
        super(cmd, false, detail);
    }
    
    public GetHostStatsAnswer(GetHostStatsCommand cmd, double cpuUtilization, long freeMemory, long totalMemory, double publicNetworkReadKBs, double publicNetworkWriteKBs) {
        super(cmd);
    
        this.cpuUtilization = cpuUtilization;
        this.freeMemory = freeMemory;
        this.totalMemory = totalMemory;
        this.publicNetworkReadKBs = publicNetworkReadKBs;
        this.publicNetworkWriteKBs = publicNetworkWriteKBs;
    }
    
    @Override
    public long getUsedMemory() {
    	return (totalMemory - freeMemory);
    }
    
    @Override
    public long getFreeMemory() {
        return freeMemory;
    }
    
    @Override
    public long getTotalMemory() {
    	return totalMemory;
    }
    
    @Override
    public double getCpuUtilization() {
        return cpuUtilization;
    }
    
    @Override
    public double getPublicNetworkReadKBs() {
    	return publicNetworkReadKBs;
    }
    
    @Override
    public double getPublicNetworkWriteKBs() {
    	return publicNetworkWriteKBs;
    }

}
