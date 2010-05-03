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

package com.vmops.agent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

public class MockVmMetrics implements Runnable {

	private String vmName;
	
	//the maximum number of network interfaces to a VM (should be 1)
	public final int MAX_INTERFACES=1;
	
	//the maximum number of disks to a VM 
	public final int MAX_DISKS=8;
	
	//the last calculated traffic speed (transmit) per interface
	private Map<String, Double> netTxKBps = new HashMap<String, Double>();
	
	//the last calculated traffic speed (receive) per interface
	private Map<String, Double> netRxKBps = new HashMap<String, Double>();
	
	//the last calculated disk write speed per disk (Bytes Per Second)
	private Map<String, Double> diskWriteKBytesPerSec = new HashMap<String, Double>();
	
	//the last calculated disk read speed per disk (Bytes Per Second)
	private Map<String, Double> diskReadKBytesPerSec = new HashMap<String, Double>();
	
	//Total Bytes Transmitted on network interfaces
	private Map<String, Long> netTxTotalBytes = new HashMap<String, Long>();
	
	//Total Bytes Received on network interfaces
	private Map<String, Long> netRxTotalBytes = new HashMap<String, Long>();
	
	//Total Bytes read per disk
	private Map<String, Long> diskReadTotalBytes = new HashMap<String, Long>();

	//Total Bytes written per disk
	private Map<String, Long> diskWriteTotalBytes = new HashMap<String, Long>();
	
	//CPU time in seconds
	private Double cpuSeconds = new Double(0.0);
	
	//CPU percentage
	private Float cpuPercent = new Float(0.0);
	
	private Map<String, String> diskMap = new HashMap<String, String>();

	private Map<String, String> vifMap = new HashMap<String, String>();
	
	private Map<String, Long> diskStatTimestamp = new HashMap<String, Long>();
	private Map<String, Long> netStatTimestamp = new HashMap<String, Long>();
	
	private long cpuStatTimestamp = 0L;
	
	private ScheduledFuture<?> future;
	private boolean stopped = false;

	public MockVmMetrics(String vmName) {
		this.vmName = vmName;
	}
	
	@Override
	public void run() {
		// do nothing in mock VM
	}
	
	public String getVmName() {
		return vmName;
	}
	
	public Map<String, Double> getNetTxKBps() {
		return netTxKBps;
	}
	
	public Map<String, Double> getNetRxKBps() {
		return netRxKBps;
	}

	public Map<String, Double> getDiskWriteBytesPerSec() {
		return diskWriteKBytesPerSec;
	}
	
	public Map<String, Double> getDiskReadBytesPerSec() {
		return diskReadKBytesPerSec;
	}
	
	public  Map<String, Long> getNetTxTotalBytes() {
		return netTxTotalBytes;
	}

	public Map<String, Long> getNetRxTotalBytes() {
		return netRxTotalBytes;
	}
	
	public Map<String, Long> getDiskReadTotalBytes() {
		return diskReadTotalBytes;
	}

	public Map<String, Long> getDiskWriteTotalBytes() {
		return diskWriteTotalBytes;
	}
	
	public Double getNetTxKBps(String intf) {
		return netTxKBps.get(intf);
	}

	public Double getNetRxKBps(String intf) {
		return netRxKBps.get(intf);
	}
	
	public Double getDiskWriteKBytesPerSec(String disk) {
		return diskWriteKBytesPerSec.get(disk);
	}

	public Double getDiskReadKBytesPerSec(String disk) {
		return diskReadKBytesPerSec.get(disk);
	}
	
	public Long getNetTxTotalBytes(String intf) {
		return netTxTotalBytes.get(intf);
	}

	public Long getNetRxTotalBytes(String intf) {
		return netRxTotalBytes.get(intf);
	}
	
	public Long getDiskReadTotalBytes(String disk) {
		return diskReadTotalBytes.get(disk);
	}

	public Long getDiskWriteTotalBytes(String disk) {
		return diskWriteTotalBytes.get(disk);
	}
	
	public Double getCpuSeconds() {
		return cpuSeconds;
	}

	public Map<String, String> getDiskMap() {
		return diskMap;
	}

	public Float getCpuPercent() {
		return cpuPercent;
	}
	
	public void setFuture(ScheduledFuture<?> sf) {
		this.future = sf;
	}

	public ScheduledFuture<?> getFuture() {
		return future;
	}
	
	public void stop() {
		this.stopped = true;
	}
}
