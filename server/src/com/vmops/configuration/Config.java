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
package com.vmops.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.vmops.agent.AgentManager;
import com.vmops.agent.manager.allocator.StoragePoolAllocator;
import com.vmops.ha.HighAvailabilityManager;
import com.vmops.network.NetworkManager;
import com.vmops.resource.xen.XenServerDiscoverer;
import com.vmops.server.ManagementServer;
import com.vmops.storage.StorageManager;
import com.vmops.vm.UserVmManager;

public enum Config {
	
	// Alert
	
	AlertEmailAddresses("Alert", ManagementServer.class, String.class, "alert.email.addresses", null, "Comma separated list of email addresses used for sending alerts.", null),
	AlertEmailSender("Alert", ManagementServer.class, String.class, "alert.email.sender", null, "Sender of alert email (will be in the From header of the email).", null),
	AlertSMTPHost("Alert", ManagementServer.class, String.class, "alert.smtp.host", null, "SMTP hostname used for sending out email alerts.", null),
	AlertSMTPPassword("Alert", ManagementServer.class, String.class, "alert.smtp.password", null, "Password for SMTP authentication (applies only if alert.smtp.useAuth is true).", null),
	AlertSMTPPort("Alert", ManagementServer.class, Integer.class, "alert.smtp.port", "465", "Port the SMTP server is listening on.", null),
	AlertSMTPUseAuth("Alert", ManagementServer.class, String.class, "alert.smtp.useAuth", null, "If true, use SMTP authentication when sending emails.", null),
	AlertSMTPUsername("Alert", ManagementServer.class, String.class, "alert.smtp.username", null, "Username for SMTP authentication (applies only if alert.smtp.useAuth is true).", null),
	AlertWait("Alert", AgentManager.class, Integer.class, "alert.wait", null, "Time to wait before alerting on a disconnected agent", "seconds"),
	
	// Storage
	
	StorageOverprovisioningFactor("Storage", StoragePoolAllocator.class, String.class, "storage.overprovisioning.factor", "2", "Used for storage overprovisioning calculation; available storage will be (actualStorageSize * storage.overprovisioning.factor)", null),
	StorageOverwriteProvisioning("Storage", UserVmManager.class, String.class, "storage.overwrite.provisioning", "false", null, null),
	StorageStatsInterval("Storage", ManagementServer.class, String.class, "storage.stats.interval", "60000", "The interval in milliseconds when storage stats (per host) are retrieved from agents.", null),
	MaxVolumeSize("Storage", ManagementServer.class, Integer.class, "max.volume.size.gb", "2000", "The maximum size for a volume in Gb.", null),
	
	// Network
	
	GuestIpNetwork("Network", AgentManager.class, String.class, "guest.ip.network", "10.1.1.1", "The network address of the guest virtual network. Virtual machines will be assigned an IP in this subnet.", "privateip"),
	GuestNetmask("Network", AgentManager.class, String.class, "guest.netmask", "255.255.255.0", "The netmask of the guest virtual network.", "netmask"),
	MulticastThrottlingRate("Network", ManagementServer.class, Integer.class, "multicast.throttling.rate", "10", "Default multicast rate in megabits per second allowed.", null),
	NetworkThrottlingRate("Network", ManagementServer.class, Integer.class, "network.throttling.rate", "200", "Default data transfer rate in megabits per second allowed.", null),
	ManagementServerNetwork("Network", AgentManager.class, String.class, "management.network.cidr", "192.168.1.0/24", "The private network CIDR of the management server cluster. Virtual routers need to know this if the management server programs the virtual router directly", null),
	XenPublicNetwork("Network", AgentManager.class, String.class, "xen.public.network.device", null, "[ONLY IF THE PUBLIC NETWORK IS ON A DEDICATED NIC]:The network name label of the physical device dedicated to the public network on a XenServer host", null),

	
	// Usage
	
	CapacityCheckPeriod("Usage", ManagementServer.class, Integer.class, "capacity.check.period", "300000", "The interval in milliseconds between capacity checks", null),
	StorageAllocatedCapacityThreshold("Usage", ManagementServer.class, Float.class, "storage.allocated.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of allocated storage utilization above which alerts will be sent about low storage available.", null),
	StorageCapacityThreshold("Usage", ManagementServer.class, Float.class, "storage.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of storage utilization above which alerts will be sent about low storage available.", null),
	CPUCapacityThreshold("Usage", ManagementServer.class, Float.class, "cpu.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of cpu utilization above which alerts will be sent about low cpu available.", null),
	MemoryCapacityThreshold("Usage", ManagementServer.class, Float.class, "memory.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of memory utilization above which alerts will be sent about low memory available.", null),
	PublicIpCapacityThreshold("Usage", ManagementServer.class, Float.class, "public.ip.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of public IP address space utilization above which alerts will be sent.", null),
	PrivateIpCapacityThreshold("Usage", ManagementServer.class, Float.class, "private.ip.capacity.threshold", "0.85", "Percentage (as a value between 0 and 1) of private IP address space utilization above which alerts will be sent.", null),
	
	// Console Proxy
	ConsoleProxyCapacityStandby("Console Proxy", AgentManager.class, String.class, "consoleproxy.capacity.standby", null, "The minimal number of console proxy viewer sessions that system is able to serve immediately(standby capacity)", null),
	ConsoleProxyCapacityScanInterval("Console Proxy", AgentManager.class, String.class, "consoleproxy.capacityscan.interval", null, "The time interval(in millisecond) to scan whether or not system needs more console proxy to ensure minimal standby capacity", null),
	ConsoleProxyCmdPort("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.cmd.port", null, "Console proxy command port that is used to communicate with management server", null),
	
	// obselete
	//ConsoleProxyDomPEnable("Console Proxy", ManagementServer.class, Boolean.class, "consoleproxy.domP.enable", null, null, null),
	
	ConsoleProxyLoadscanInterval("Console Proxy", AgentManager.class, String.class, "consoleproxy.loadscan.interval", null, "The time interval(in milliseconds) to scan console proxy working-load info", null),
	
	// obselete
	// ConsoleProxyPort("Console Proxy", ManagementServer.class, Integer.class, "consoleproxy.port", null, null, null),
	
	ConsoleProxyRamSize("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.ram.size", null, "RAM size (in MB) used to create new console proxy VMs", null),
	ConsoleProxySessionMax("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.session.max", null, "The max number of viewer sessions console proxy is configured to serve for", null),
	ConsoleProxySessionTimeout("Console Proxy", AgentManager.class, Integer.class, "consoleproxy.session.timeout", null, "Timeout(in milliseconds) that console proxy tries to maintain a viewer session before it times out the session for no activity", null),
	
	ConsoleProxyURLPort("Console Proxy", ManagementServer.class, Integer.class, "consoleproxy.url.port", null, "Console proxy port for AJAX viewer", null),
			
	// Advanced
	
	AccountCleanupInterval("Advanced", ManagementServer.class, Integer.class, "account.cleanup.interval", "86400", "The interval in seconds between cleanup for removed accounts", null),
	DefaultZone("Advanced", ManagementServer.class, String.class, "default.zone", "ZONE1", "The default.zone parameter controls in which zone machines are created by default, if you do not specify a zone.", null),
	InstanceName("Advanced", AgentManager.class, String.class, "instance.name", "VM", "Name of the deployment instance.", null),
	ExpungeDelay("Advanced", UserVmManager.class, Integer.class, "expunge.delay", "86400", "Determines how long to wait before actually expunging destroyed vm. The default value = the default value of expunge.interval", null),
	ExpungeInterval("Advanced", UserVmManager.class, Integer.class, "expunge.interval", "86400", "The interval to wait before running the expunge thread.", null),
	ExpungeWorkers("Advanced", UserVmManager.class, Integer.class, "expunge.workers",  "1", "Number of workers performing expunge ", null),
	HostStatsInterval("Advanced", ManagementServer.class, Integer.class, "host.stats.interval", "60000", "The interval in milliseconds when host stats are retrieved from agents.", null),
	IntegrationAPIPort("Advanced", ManagementServer.class, Integer.class, "integration.api.port", "8096", "Defaul API port", null),
	InvestigateRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "investigate.retry.interval", "60", "Time in seconds between VM pings when agent is disconnected", null),
	MigrateRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "migrate.retry.interval", "120", "Time in seconds between migration retries", null),
	PingInterval("Advanced", AgentManager.class, Integer.class, "ping.interval", "60", "Ping interval in seconds", null),
	PingTimeout("Advanced", AgentManager.class, Float.class, "ping.timeout", "2.5", "Multiplier to ping.interval before announcing an agent has timed out", "2.5x"),
	Port("Advanced", AgentManager.class, Integer.class, "port", "8250", "Port to listen on for agent connection.", null),
	RouterRamSize("Advanced", NetworkManager.class, Integer.class, "router.ram.size", "128", "Default RAM for router VM in MB.", null),
	RestartRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "restart.retry.interval", "600", "Time in seconds between retries to restart a vm", null),
	RouterCleanupInterval("Advanced", ManagementServer.class, Integer.class, "router.cleanup.interval", "3600", "Time in seconds identifies when to stop router when there are no user vms associated with it", null),
	RouterStatsInterval("Advanced", NetworkManager.class, Integer.class, "router.stats.interval", "300", "Interval to report router statistics.", null),
	RouterTemplateId("Advanced", NetworkManager.class, Long.class, "router.template.id", "1", "Default ID for template.", null),
	StartRetry("Advanced", AgentManager.class, Integer.class, "start.retry", "2", "Number of times to retry create and start commands", null),
	StopRetryInterval("Advanced", HighAvailabilityManager.class, Integer.class, "stop.retry.interval", "600", null, null),
	StoragePoolCleanupInterval("Advanced", StorageManager.class, Integer.class, "storage.pool.cleanup.interval", "86400", "The interval to wait before running the storage pool cleanup thread.", null),
	StoragePoolCleanupEnabled("Advanced", StorageManager.class, Boolean.class, "storage.pool.cleanup.enabled", "true", "Enables/disables the storage pool cleanup thread.", null),
	UpdateWait("Advanced", AgentManager.class, Integer.class, "update.wait", "600", "Time to wait before alerting on a updating agent", null),
	Wait("Advanced", AgentManager.class, Integer.class, "wait", "1800", "Time to wait for control commands to return", null),
	Workers("Advanced", AgentManager.class, Integer.class, "workers", "5", "Number of worker threads.", null),
	MountParent("Advanced", ManagementServer.class, String.class, "mount.parent", "/var/lib/vmops/mnt", "The mount point on the Management Server for Secondary Storage.", null),
	UpgradeURL("Advanced", ManagementServer.class, String.class, "upgrade.url", "http://example.com:8080/client/agent/update.zip", "The upgrade URL is the URL of the management server that agents will connect to in order to automatically upgrade.", null),
	SystemVMUseLocalStorage("Advanced", ManagementServer.class, Boolean.class, "system.vm.use.local.storage", "false", "Indicates whether to use local storage pools or shared storage pools for system VMs.", null),
	CPUOverprovisioningFactor("Advanced", ManagementServer.class, String.class, "cpu.overprovisioning.factor", "1", "Used for CPU overprovisioning calculation; available CPU will be (actualCpuCapacity * cpu.overprovisioning.factor)", null),
	CreatePoolsInPod("Advanced", XenServerDiscoverer.class, Boolean.class, "create.pools.in.pod", "true", "Should we automatically add XenServers into pools that are inside a Pod", null),
	PoolCreationStrategy("Advanced", XenServerDiscoverer.class, String.class, "pool.creation.strategy", "Greedy", "Greedy=Fill up one pool first; Even=Distribute between pools", "Greedy, Even"),
	MaxPoolsInPod("Advanced", XenServerDiscoverer.class, Number.class, "max.pools.in.pod", "1", "Number of XenServer pools in a Pod", "1-5"),
	ForceJoinPool("Advanced", XenServerDiscoverer.class, Boolean.class, "force.join.pool", "false", "Force joinging into the pool?", "true/false"),
	NetworkType("Advanced", ManagementServer.class, String.class, "network.type", "vnet", "The type of network that this deployment will use.", "vnet/vlan/direct.attached"),
	HypervisorType("Advanced", ManagementServer.class, String.class, "hypervisor.type", "kvm", "The type of hypervisor that this deployment will use.", "kvm/xenserver"),
	ManagementHostIPAdr("Advanced", ManagementServer.class, String.class, "host", "localhost", "The ip address of management server", null),
	
	// Premium
	
	UsageAggregationTimezone("Premium", ManagementServer.class, String.class, "usage.aggregation.timezone", "GMT", "The timezone to use when aggregating user statistics", null),
	UsageStatsJobAggregationRange("Premium", ManagementServer.class, Integer.class, "usage.stats.job.aggregation.range", "1440", "The range of time for aggregating the user statistics specified in minutes (e.g. 1440 for daily, 60 for hourly.", null),
	UsageStatsJobExecTime("Premium", ManagementServer.class, String.class, "usage.stats.job.exec.time", "00:15", "The time at which the usage statistics aggregation job will run as an HH24:MM time, e.g. 00:30 to run at 12:30am.", null);
    
	// Developer
	
	private final String _category;
	private final Class<?> _componentClass;
	private final Class<?> _type;
    private final String _name;
    private final String _defaultValue;
    private final String _description;
    private final String _range;
    
    private static final HashMap<String, List<Config>> _configs = new HashMap<String, List<Config>>();
    static {
    	// Add categories
    	_configs.put("Alert", new ArrayList<Config>());
    	_configs.put("Storage", new ArrayList<Config>());
    	_configs.put("Network", new ArrayList<Config>());
    	_configs.put("Usage", new ArrayList<Config>());
    	_configs.put("Console Proxy", new ArrayList<Config>());
    	_configs.put("Advanced", new ArrayList<Config>());
    	_configs.put("Premium", new ArrayList<Config>());
    	_configs.put("Developer", new ArrayList<Config>());
    	
    	// Add values into HashMap
        for (Config c : Config.values()) {
        	String category = c.getCategory();
        	List<Config> currentConfigs = _configs.get(category);
        	currentConfigs.add(c);
        	_configs.put(category, currentConfigs);
        }
    }
    
    private Config(String category, Class<?> componentClass, Class<?> type, String name, String defaultValue, String description, String range) {
    	_category = category;
    	_componentClass = componentClass;
    	_type = type;
    	_name = name;
    	_defaultValue = defaultValue;
    	_description = description;
    	_range = range;
    }
    
    public String getCategory() {
    	return _category;
    }
    
    public String getName() {
        return _name;
    }
    
    public String getDescription() {
        return _description;
    }

    public String getDefaultValue() {
        return _defaultValue;
    }

    public Class<?> getType() {
        return _type;
    }

    public Class<?> getComponentClass() {
        return _componentClass;
    }
    
    public String getComponent() {
    	if (_componentClass == ManagementServer.class)
    		return "management-server";
    	else if (_componentClass == AgentManager.class)
    		return "AgentManager";
    	else if (_componentClass == UserVmManager.class)
    		return "UserVmManager";
    	else if (_componentClass == HighAvailabilityManager.class)
    		return "HighAvailabilityManager";
    	else if (_componentClass == StoragePoolAllocator.class)
    		return "StorageAllocator";
    	else
    		return "none";
    }

    public String getRange() {
        return _range;
    }
    
    @Override
	public String toString() {
        return _name;
    }
    
    public static List<Config> getConfigs(String category) {
    	return _configs.get(category);
    }
    
    public static Config getConfig(String name) {
    	List<String> categories = getCategories();
    	for (String category : categories) {
    		List<Config> currentList = getConfigs(category);
    		for (Config c : currentList) {
    			if (c.getName().equals(name))
    				return c;
    		}
    	}
    	
    	return null;
    }
    
    public static List<String> getCategories() {
    	Object[] keys = _configs.keySet().toArray();
    	List<String> categories = new ArrayList<String>();
    	for (Object key : keys) {
    		categories.add((String) key);
    	}
    	return categories;
    }
   
    /*
    public static int main(String[] args) {
    	System.out.println("VMOps Management Server Configuration Variables");
    	
    	for (Config config : Config.values()) {
    		
    	}
    }
    */
}
