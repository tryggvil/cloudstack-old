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
package com.vmops.agent.metrics;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;

/**
 * Schedules collection of metrics for each VM on a host
 * @author chiradeep
 *
 */
public class MetricsCollector {
    final static private Logger s_logger = Logger.getLogger(MetricsCollector.class);
	/**
	 * Snarfs VM names from output of shell command
	 * @author chiradeep
	 *
	 */
	private static final class VMNameInterpreter extends OutputInterpreter {
		public Set<String> getCurrentVMs() {
			return currentVMs;
		}

		private final Set<String> currentVMs = new HashSet<String>();

		@Override
		public String interpret(BufferedReader reader) throws IOException {
		    String line = null;
	        while ((line = reader.readLine()) != null) {
	           String name = line.trim();
	           currentVMs.add(name);
	        }
			return null;
		}
	}
	
	/**
	 * Debug Task that prints out all collected metrics to standard out
	 * @author chiradeep
	 *
	 */
	private static final class MetricsDebug implements Runnable {
		private final MetricsCollector mc;
		
		public MetricsDebug(MetricsCollector collector) {
			this.mc = collector;
		}
		
		@Override
		public void run() {
			Map<String, VMopsVMMetrics> MetricsMap = mc.getMetricsMap();
			for (String vm: MetricsMap.keySet()) {
				VMopsVMMetrics metrics = MetricsMap.get(vm);
				if (metrics == null) {
					System.out.println("Null metrics object!! vm=" + vm);
					return;
				}
				try {
					System.out.println(vm + " eth0: Rx: Speed=" + String.format("%.2f", metrics.getNetRxKBps("eth0").doubleValue()/1024.0d) + " MBps" + " Total=" + String.format("%.2f", metrics.getNetRxTotalBytes("eth0")*1.0d) + " Mbytes");
					System.out.println(vm + " eth0: Tx: Speed=" + String.format("%.2f", metrics.getNetTxKBps("eth0").doubleValue()/1024.0d) + " MBps" + " Total=" + String.format("%.2f", metrics.getNetTxTotalBytes("eth0")*1.0d) + " Mbytes");
/*					for (String disk: metrics.getDiskMap().keySet()){
						System.out.println(String.format("%s /dev/%s Read Speed=%.2f KBytes/s Total=%.2f KBytes", vm, disk, metrics.getDiskReadKBytesPerSec(disk), metrics.getDiskReadTotalBytes(disk)/1024.0d));
						System.out.println(String.format("%s /dev/%s Write Speed=%.2f KBytes/s Total=%.2f KBytes", vm, disk, metrics.getDiskWriteKBytesPerSec(disk), metrics.getDiskWriteTotalBytes(disk)/1024.0d));

					}*/
					System.out.println(String.format("%s cpuTime=%.2f seconds cpuPercent=%.2f",vm, metrics.getCpuSeconds().doubleValue(), metrics.getCpuPercent()));
				} catch (Exception ex){
					System.out.println("Caught Exception for vm: " + vm);
					ex.printStackTrace();
				}

			}
			
		}
		
	}

	private final Set<String> vmNames = new HashSet<String>();
	
	private final ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(5, new NamedThreadFactory("Metrics"));
	private final Map<String, VMopsVMMetrics> metricsMap = new HashMap<String, VMopsVMMetrics>();

	private final MetricsDebug debugObj;

	private final Set<String> newVMnames = new HashSet<String>();
	
	/**
	 * Determine the list of running VMs on a host
	 */
	public synchronized void getAllVMNames() {
		Script command = new Script("/bin/bash", s_logger);
        command.add("-c");
        command.add("for a in $(xm list | grep -v VCPUs | grep -v Domain-0 | awk '{print $1}');" +
                     "do " +
                       "d=$(xm domid $a); " +
                       "if [ ${d:0:4} != None ];" +
                       "then    " +
                         "echo $a ; " +
                        "fi;" +
                      "done");
        VMNameInterpreter vmni = new VMNameInterpreter();
        command.execute(vmni);
        newVMnames.clear();
        newVMnames.addAll(vmni.getCurrentVMs());
        newVMnames.removeAll(vmNames); //leave only new vms
        vmNames.removeAll(vmni.getCurrentVMs()); //leave non-running vms;
        for (String vm: vmNames) {
        	removeVM(vm);
        }
        vmNames.clear();
        vmNames.addAll(vmni.getCurrentVMs());

	}
	
	/**
	 * Create and schedule collection of metrics for running VMs
	 */
	public synchronized void submitMetricsJobs() {
		s_logger.debug("Submit Metric Jobs called");
		for (String vm : newVMnames) {
			VMopsVMMetrics task = new VMopsVMMetrics(vm);
			if (!metricsMap.containsKey(vm)) {
			    metricsMap.put(vm, task);
			    ScheduledFuture<?> sf = stpe.scheduleWithFixedDelay(task, 2, 10, TimeUnit.SECONDS);
			    task.setFuture(sf);
			}
		}
		newVMnames.clear();
	}
	
	public synchronized void addVM(String vmName) {
		newVMnames.add(vmName);
		s_logger.debug("Added vm name= " + vmName);
	}
	
	public synchronized void removeVM(String vmName) {
		newVMnames.remove(vmName);
		vmNames.remove(vmName);
    	VMopsVMMetrics task = metricsMap.get(vmName);
    	if (task != null) {
    		task.stop();
    		boolean r1= task.getFuture().cancel(false);
    		metricsMap.remove(vmName);
    		s_logger.debug("removeVM: cancel returned " + r1 + " for VM " + vmName);
    	} else {
    		s_logger.warn("removeVM called for nonexistent VM " + vmName);
    	}

	}

	/**
	 * @return set of vm name strings
	 */
	public synchronized Set<String> getVMNames() {
	  return vmNames;
	}
	
	/**
	 * @return map of vm name to its metric object
	 */
	public synchronized Map<String, VMopsVMMetrics> getMetricsMap() {
		return metricsMap;
	}
	
	/**
	 * Constructor
	 */
	public MetricsCollector() {
		getAllVMNames();
		debugObj = new MetricsDebug(this);
	}
	

	/**
	 * Print out collected stats to standard out periodically
	 * @param on : true to turn debug on, false to turn it off
	 */
	public void debug(boolean on) {
		if (on)
			stpe.scheduleWithFixedDelay(debugObj, 4, 10, TimeUnit.SECONDS);
		else
			stpe.remove(debugObj);
	}

	public static void main(String args[]) {
		MetricsCollector  mc = new MetricsCollector();
		//mc.submitMetricsJobs();
		mc.debug(true);
		boolean added=false;
		for (int i=0; i < 1000; i++) {
			try {
				if (!added && i%5==0) {
					System.out.println("Adding domR-vnet0008");
					mc.addVM("domR-vnet0008");
					mc.submitMetricsJobs();

					System.out.println("Adding centos5-2");
					mc.addVM("centos5-2");
					mc.submitMetricsJobs();
					added=true;
				} else if ( i%5==0 && i%10==0) {
					System.out.println("Removing domR-vnet0008 & centos5-2");
					mc.removeVM("domR-vnet0008");
					mc.removeVM("centos5-2");
					added=false;
				}
				Thread.sleep(5000);
				//mc.getAllVMNames();
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
	}
}
