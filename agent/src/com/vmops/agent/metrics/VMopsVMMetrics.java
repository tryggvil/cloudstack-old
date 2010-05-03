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
package com.vmops.agent.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import org.apache.log4j.Logger;

import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;

/**
 * @author chiradeep
 * The metrics for a particular VM
 */
public class VMopsVMMetrics implements Runnable{
    private static final Logger s_logger = Logger.getLogger(VMopsVMMetrics.class);
    public enum RETURN_STRINGS  {
        OK("OK"), EMPTY("EMPTY"), ERROR("ERROR"), IOERROR("IOError"); 
        String name;
        RETURN_STRINGS(String str)
        {
            this.name=str;
        }
        @Override
        public String toString() {
            return this.name;
        }
        
        };
    
    
	private static final class DoubleOutputInterpreter extends
	    OutputInterpreter {
		public final static Double zero = new Double(0.0);
		private Double doubleValue = zero;
		
		@Override
        public String interpret(BufferedReader reader) throws IOException {
		    String line = null;
	        while ((line = reader.readLine()) != null) {
	        	try {
	        		doubleValue = Double.valueOf(line.trim());
	        	} catch (NumberFormatException nfe) {
	    		    return RETURN_STRINGS.ERROR.toString();
	        	}
	           //System.out.println("Double: " + doubleValue);
	        }
		    return RETURN_STRINGS.OK.toString();
		}
 
		public Double getDoubleValue() {
			return doubleValue;
		}
	}

	public static final class IOStatsInterpreter extends OutputInterpreter {
		public final int RX = 0;
		public final int TX = 1;
		private Long [] iostats = new Long [2];
		
		public Long[] getIostats() {
			return iostats;
		}
		public Long getRxstats() {
			return (iostats[RX]==null)?0L:iostats[RX];
		}
		
		public Long getTxstats() {
			return (iostats[TX]==null)?0L:iostats[TX];
		}

		public IOStatsInterpreter() {
		}

		@Override
        public String interpret(BufferedReader reader) throws IOException {
		    String line = null;
	        while ((line = reader.readLine()) != null) {
	           String [] toks = line.trim().split("\\s+");
	           if (toks.length < 2) {
	        	   return RETURN_STRINGS.EMPTY.toString();
	           }
	           try {
	        	   iostats[RX] = Long.parseLong(toks[0]);
	        	   iostats[TX] = Long.parseLong(toks[1]);
	           }catch (NumberFormatException nfe){
	        	   return RETURN_STRINGS.ERROR.toString();
	           }

	        }
		    return RETURN_STRINGS.OK.toString();
		}
	}

	public static class MapInterpreter extends OutputInterpreter {
		private Map<String, String> map;
		
		public MapInterpreter(Map<String, String> map){
			this.map = map;
		}
		
		@Override
        public String interpret(BufferedReader reader) throws IOException {
		    String line = null;
		    int numLines=0;
	        while ((line = reader.readLine()) != null) {
	           String [] toks = line.trim().split("\\s+");
	           if (toks.length < 2) s_logger.warn("Failed to parse Script output: " + line);
	           else map.put(toks[0], toks[1]);
	           numLines++;
	           //System.out.println("MapInterpreter: " + toks[0] + " " + toks[1]);
	        }
	        if (numLines == 0) {
	        	s_logger.warn("VMOpsVMMetrics: Map Interpreter: no script output??");
	        }
		    return RETURN_STRINGS.OK.toString();
		}
	}

	//the name of the VM
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

	private boolean stopped = false;

	private ScheduledFuture<?> future;


	public String getVmName() {
		return vmName;
	}

	/**
	 * @return a map of device names (eth0, etc) to their last measured transmit speed
	 */
	public Map<String, Double> getNetTxKBps() {
		return netTxKBps;
	}

	/**
	 * @return a map of device names (eth0, etc) to their last measured receive speed
	 */
	public Map<String, Double> getNetRxKBps() {
		return netRxKBps;
	}

	/**
	 * @return  a map of disk names (sda, c:, etc) to their last measured write speed
	 */
	public Map<String, Double> getDiskWriteBytesPerSec() {
		return diskWriteKBytesPerSec;
	}

	/**
	 * @return  a map of disk names (sda, c:, etc) to their last measured read speed
	 */
	public Map<String, Double> getDiskReadBytesPerSec() {
		return diskReadKBytesPerSec;
	}

	/**
	 * @return a map of device names (eth0, etc) to their total transmit traffic
	 */
	public  Map<String, Long> getNetTxTotalBytes() {
		return netTxTotalBytes;
	}

	/**
	 * @return a map of device names (eth0, etc) to their total receive traffic
	 */
	public Map<String, Long> getNetRxTotalBytes() {
		return netRxTotalBytes;
	}

	/**
	 * @return  a map of disk names (sda, c:, etc) to the total read bytes
	 */
	public Map<String, Long> getDiskReadTotalBytes() {
		return diskReadTotalBytes;
	}

	/**
	 * @return  a map of disk names (sda, c:, etc) to the total written bytes
	 */
	public Map<String, Long> getDiskWriteTotalBytes() {
		return diskWriteTotalBytes;
	}
	
	/**
	 * @param intf - the interface name ("eth0" typically)
	 * @return last measured transmit speed
	 */
	public Double getNetTxKBps(String intf) {
		return netTxKBps.get(intf);
	}

	/**
	 * @param intf - the interface name ("eth0" typically)
	 * @return last measured receive speed
	 */
	public Double getNetRxKBps(String intf) {
		return netRxKBps.get(intf);
	}

	/**
	 * @param disk - the disk name ("sda","sdb", etc) 
	 * @return last measured write performance
	 */
	public Double getDiskWriteKBytesPerSec(String disk) {
		return diskWriteKBytesPerSec.get(disk);
	}

	/**
	 * @param disk - the disk name ("sda","sdb", etc) 
	 * @return last measured read performance
	 */
	public Double getDiskReadKBytesPerSec(String disk) {
		return diskReadKBytesPerSec.get(disk);
	}

	
	/**
	 * @param intf - the interface name (eth0)
	 * @return total transmit bytes
	 */
	public Long getNetTxTotalBytes(String intf) {
		return netTxTotalBytes.get(intf);
	}

	/**
	 * @param intf - the interface name (eth0)
	 * @return total receive bytes
	 */
	public Long getNetRxTotalBytes(String intf) {
		return netRxTotalBytes.get(intf);
	}

	/**
	 * @param disk - the disk name ("sda","sdb", etc) 
	 * @return total read bytes for this disk
	 */
	public Long getDiskReadTotalBytes(String disk) {
		return diskReadTotalBytes.get(disk);
	}

	/**
	 * @param disk - the disk name ("sda","sdb", etc) 
	 * @return total written bytes for this disk
	 */
	public Long getDiskWriteTotalBytes(String disk) {
		return diskWriteTotalBytes.get(disk);
	}
	
	
	/**
	 * Read disk stats using shell utilities
	 * @param diskName name of the disk ("sda" etc)
	 * @param diskStatsPath 
	 */
	private void getDiskData(final String diskName, final String diskStatsPath) {
		Date now = new Date();
		Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("rx=$(cat " + diskStatsPath + "/rd_sect); " +
        			 "tx=$(cat " + diskStatsPath + "/wr_sect);" +
        			 "echo $rx $tx");
        IOStatsInterpreter iostatsIntprtr = new IOStatsInterpreter();
        Long prevRead = diskReadTotalBytes.get(diskName);
        Long prevWrite = diskWriteTotalBytes.get(diskName);
        Long prevTs = diskStatTimestamp.get(diskName);
		String ret = command.execute(iostatsIntprtr);
		if (!RETURN_STRINGS.OK.toString().equalsIgnoreCase(ret)) {
			return;
		}
		
		//TODO: read sector size using 
		//xenstore-read /local/domain/0/backend/tap/2/2064/sector-size
		//For now assume 512 bytes
		Long currRead = iostatsIntprtr.getRxstats()*512; //TODO:
		Long currWrite = iostatsIntprtr.getTxstats()*512;
		
		diskReadTotalBytes.put(diskName, currRead);
		diskWriteTotalBytes.put(diskName, currWrite);
		diskStatTimestamp.put(diskName, now.getTime());
		
		if (prevRead==null || prevWrite==null || prevTs == null) {
			diskReadKBytesPerSec.put(diskName, 0.0d);
			diskWriteKBytesPerSec.put(diskName, 0.0d);
			return;
		}
		double timediff = (now.getTime()-prevTs.longValue())/1000.0;
		diskReadKBytesPerSec.put(diskName, (currRead.longValue()-prevRead.longValue())*1.0d/(timediff*1024.0d));
		diskWriteKBytesPerSec.put(diskName, (currWrite.longValue()-prevWrite.longValue())*1.0d/(timediff*1024.0d));

			
	}
	
	/**
	 * Read Network statistics using shell utilities
	 * @param devname
	 */
	public void getNetworkData(final String devname) {
		Date now = new Date();
		String vifName = vifMap.get(devname);
		Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
       // command.add("stats=($(cat /proc/net/dev | grep " + vifName + " | cut -d\":\" -f2));" +
                   //  "echo ${stats[0]} ${stats[8]}");
        command.add("tx=$(iptables -nvx -L FORWARD  | grep " + vifName + " | grep physdev-in | awk '{print $2}');" +
        		    "rx=$(iptables -nvx -L FORWARD  | grep " + vifName + " | grep physdev-out |  awk '{sum+=$2} END {print sum;}');" +
        		    "echo $tx $rx"
        		    );
        IOStatsInterpreter iostatsIntprtr = new IOStatsInterpreter();
       
        Long prevRx = netRxTotalBytes.get(devname);
        Long prevTx = netTxTotalBytes.get(devname);
        Long prevTs = netStatTimestamp.get(devname);
        
		String ret = command.execute(iostatsIntprtr);
		if (!RETURN_STRINGS.OK.toString().equalsIgnoreCase(ret)) {
			return;
		}
		Long currWrite = iostatsIntprtr.getRxstats(); //flip them around for network stats
		Long currRead = iostatsIntprtr.getTxstats();
		
		netRxTotalBytes.put(devname, currRead);
		netTxTotalBytes.put(devname, currWrite);
		netStatTimestamp.put(devname, now.getTime());
		
		if (prevRx==null || prevTx==null || prevTs == null) {
			netRxKBps.put(devname, 0.0d);
			netTxKBps.put(devname, 0.0d);
			return;
		}
		double timediff = (now.getTime()-prevTs.longValue())/1000.0;
		
		netStatTimestamp.put(devname, now.getTime());
		netRxKBps.put(devname, (currRead.longValue()-prevRx.longValue())*1.0d/(timediff*1024));
		netTxKBps.put(devname, (currWrite.longValue()-prevTx.longValue())*1.0d/(timediff*1024));	
	}
	

	public Double getCpuSeconds() {
		return cpuSeconds;
	}

	private void readCpuSecondsData() {
		Double prevCpuSecs = cpuSeconds;
		long prevTs = cpuStatTimestamp;
		cpuStatTimestamp = (new Date()).getTime()/1000;
		Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("xm list " + vmName + " | grep -v VCPUs | awk '{if ($6==\"\")  print $4; else print $6}'");
        DoubleOutputInterpreter foi = new DoubleOutputInterpreter();
		command.execute(foi);
		
		cpuSeconds = foi.getDoubleValue();
		if (prevTs == 0L) {
			return;
		}
		if (cpuSeconds.equals(DoubleOutputInterpreter.zero)){
			cpuSeconds = prevCpuSecs;
		}
		long timediff = (cpuStatTimestamp - prevTs);
		cpuPercent = new Float ((cpuSeconds.doubleValue() - prevCpuSecs.doubleValue())*100.0/timediff);
		if (cpuPercent > 100.0f) 
			cpuPercent = 100.0f;
	}
	//
	/**
	 * Determine the disks in use by this VM and the path to their stats device<p>
	 * TODO: also read sector size using xenstore-read
	 */
	public void initializeDiskMap() {
        Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("for i in $(xm block-list " + vmName +  " | awk \'{print $7}\'|grep -v BE-path);" +
        		      "do " +
        		        "dev=$(echo $i | cut -d\"/\" -f6- | tr / -); " +  
        		        "fed=$(xenstore-read $i/dev); " +
        		    	"rd=\"/sys/devices/xen-backend/$dev/statistics\";  " +
        		    	"echo $fed    $rd ;" +
        		      "done");
		command.execute(new MapInterpreter(diskMap));
		for (String disk: diskMap.keySet()) {
			diskReadKBytesPerSec.put(disk, 0.0d);
			diskWriteKBytesPerSec.put(disk, 0.0d);
			diskReadTotalBytes.put(disk, 0L);
			diskWriteTotalBytes.put(disk, 0L);
		}
	}
	
	/**
	 * Determine the disks in use by this VM and the path to their stats device<p>
	 * TODO: also read sector size using xenstore-read
	 */
	public static Map<String, String> getDiskMap(String vmName) {
		Map<String, String> diskMap = new HashMap<String, String>();
        Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("for i in $(xm block-list " + vmName +  " | awk \'{print $7}\'|grep -v BE-path);" +
        		      "do " +
        		        "dev=$(echo $i | cut -d\"/\" -f6- | tr / -); " +  
        		        "fed=$(xenstore-read $i/dev); " +
        		    	"mounted=$(xenstore-read $i/params|cut -d\":\" -f2);" +
        		    	"printf \"$fed    $mounted\n\" ;" +
        		      "done");
        command.execute(new MapInterpreter(diskMap));
		return diskMap;
	}
	
	/**
	 * Determine the network interfaces in use by this VM
	 */
	public void initializeNetMap() {
        Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("for i in $(xm network-list " + vmName +  " | awk \'{print $9}\'|grep vif);" +
        			 "do " +
        			 	"dev=$(echo $i | cut -d\"/\" -f7- | tr / .); " +  
        			 	"devnum=$(echo $i | cut -d\"/\" -f8- ); " +
        			 	"echo eth$devnum    vif$dev ;" +
        			 "done");
		command.execute(new MapInterpreter(vifMap));
		/*
		String vifName = vmName.replace('-', '_').concat(".");
		for (int i=0; i <=2 ; i++) {
		  String vif = vifName.concat(Integer.toString(i));
		  String dev = "eth".concat(Integer.toString(i));
		  vifMap.put(dev, vif);
		}
		*/
		for (String dev: vifMap.keySet()) {
			netRxKBps.put(dev, 0.0d);
			netRxTotalBytes.put(dev, 0L);
			netTxKBps.put(dev, 0.0d);
			netTxTotalBytes.put(dev, 0L);
		}
		if (vifMap.keySet().contains("eth2")) {
			s_logger.info("VMopsVMMetrics: mapped eth2 of " + vmName + " to " + vifMap.get("eth2"));
		}
		
	}
	
    /**
     * Read disk IO statistics  using shell utilities
     */
    private void getDiskData() {
		for (String disk: diskMap.keySet()) {
			getDiskData(disk, diskMap.get(disk));
		}
    }
    
    /**
     * Read network IO statistics using shell utilities
     */
    private void getNetworkData() {
		for (String net: vifMap.keySet()) {
			getNetworkData(vifMap.get(net));
		}
    }
 
	/**
	 * Constructor
	 * @param vmName - the name label for the VM
	 */
	public VMopsVMMetrics(String vmName) {
		super();
		this.vmName = vmName;
		initializeDiskMap();
		initializeNetMap();
	}
	


	public static void main(String [] args) {
		VMopsVMMetrics vmm = new VMopsVMMetrics("winxp-sp3 ");
		vmm.initializeNetMap();
		vmm.initializeDiskMap();
		for (String disk: vmm.diskMap.keySet()) {
		  vmm.getDiskData(disk, vmm.diskMap.get(disk));
		}
		for (String net: vmm.vifMap.keySet()) {
			vmm.getNetworkData(vmm.vifMap.get(net), net);
		}
		Map<String, String> diskMap = VMopsVMMetrics.getDiskMap("winxp-sp3 ");
        for (String d: diskMap.keySet()) {
        	System.out.println("Disk: " + d + ", mounted= " + diskMap.get(d));
        }
	}

	@Override
	public void run() {
		if (!stopped) {
			//getDiskData();
			//getNetworkData();
			//readCpuSecondsData();
		}
	}

	/**
	 * @return the map of disk names ("sda", "sdb", etc) to their statistics device
	 */
	public Map<String, String> getDiskMap() {
		return diskMap;
	}

	public Float getCpuPercent() {
		return cpuPercent;
	}

	public void stop() {
		this.stopped = true;
		
	}

	public void setFuture(ScheduledFuture<?> sf) {
		this.future = sf;
	}

	public ScheduledFuture<?> getFuture() {
		return future;
	}

	/**
	 * Read Network statistics using shell utilities
	 * @param vifName
	 * @param devname
	 */
	private void getNetworkData(final String vifName, String devname) {
		Date now = new Date();
		Script command = new Script("/bin/bash", s_logger);
	    command.add("-c");
	    command.add("stats=($(cat /proc/net/dev | grep " + vifName + " | cut -d\":\" -f2));" +
	                 "echo ${stats[0]} ${stats[8]}");
	    IOStatsInterpreter iostatsIntprtr = new IOStatsInterpreter();
	
	    Long prevRx = netRxTotalBytes.get(devname);
	    Long prevTx = netTxTotalBytes.get(devname);
	    Long prevTs = netStatTimestamp.get(devname);
	    
		String ret = command.execute(iostatsIntprtr);
		if (!RETURN_STRINGS.OK.toString().equalsIgnoreCase(ret)) {
			return;
		}
		Long currWrite = iostatsIntprtr.getRxstats(); //flip them around for network stats
		Long currRead = iostatsIntprtr.getTxstats();
		
		netRxTotalBytes.put(devname, currRead);
		netTxTotalBytes.put(devname, currWrite);
		netStatTimestamp.put(devname, now.getTime());
		
		if (prevRx==null || prevTx==null || prevTs == null) {
			netRxKBps.put(devname, 0.0d);
			netTxKBps.put(devname, 0.0d);
			return;
		}
		double timediff = (now.getTime()-prevTs.longValue())/1000.0;
		
		netStatTimestamp.put(devname, now.getTime());
		netRxKBps.put(devname, (currRead.longValue()-prevRx.longValue())*1.0d/(timediff*1024));
		netTxKBps.put(devname, (currWrite.longValue()-prevTx.longValue())*1.0d/(timediff*1024));	
	}


	
}
