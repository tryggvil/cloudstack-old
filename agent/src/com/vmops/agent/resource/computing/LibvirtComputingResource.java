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


package com.vmops.agent.resource.computing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.SortedMap;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.DomainInfo;
import org.libvirt.LibvirtException;
import org.libvirt.Network;
import org.libvirt.NodeInfo;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.AttachDiskCommand;
import com.vmops.agent.api.AttachIsoCommand;
import com.vmops.agent.api.CheckHealthAnswer;
import com.vmops.agent.api.CheckHealthCommand;
import com.vmops.agent.api.CheckStateCommand;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetHostStatsAnswer;
import com.vmops.agent.api.GetHostStatsCommand;
import com.vmops.agent.api.GetVmStatsCommand;
import com.vmops.agent.api.GetVncPortAnswer;
import com.vmops.agent.api.GetVncPortCommand;
import com.vmops.agent.api.MigrateAnswer;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.MirrorCommand;
import com.vmops.agent.api.ModifyVlanCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingComputingCommand;
import com.vmops.agent.api.PingTestCommand;
import com.vmops.agent.api.PrepareForMigrationAnswer;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.ReadyAnswer;
import com.vmops.agent.api.ReadyCommand;
import com.vmops.agent.api.RebootAnswer;
import com.vmops.agent.api.RebootCommand;
import com.vmops.agent.api.StartAnswer;
import com.vmops.agent.api.StartCommand;
import com.vmops.agent.api.StartConsoleProxyAnswer;
import com.vmops.agent.api.StartConsoleProxyCommand;
import com.vmops.agent.api.StartRouterAnswer;
import com.vmops.agent.api.StartRouterCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupComputingCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.WatchNetworkCommand;
import com.vmops.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.vmops.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.vmops.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.vmops.agent.api.routing.DhcpEntryCommand;
import com.vmops.agent.api.routing.IPAssocCommand;
import com.vmops.agent.api.routing.LoadBalancerCfgCommand;
import com.vmops.agent.api.routing.SavePasswordCommand;
import com.vmops.agent.api.routing.SetFirewallRuleCommand;
import com.vmops.agent.resource.storage.Qcow2StorageResource;
import com.vmops.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.vmops.host.Host.HypervisorType;
import com.vmops.host.Host.Type;
import com.vmops.network.NetworkEnums.RouterPrivateIpStrategy;
import com.vmops.resource.ServerResource;
import com.vmops.resource.ServerResourceBase;
import com.vmops.storage.StorageResource;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.PropertiesUtil;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.ConsoleProxyVO;

/**
 * LibvirtComputingResource execute requests on the computing/routing host using the libvirt API
 * 
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || hypervisor.type | type of local hypervisor | string | kvm ||
 *    || hypervisor.uri | local hypervisor to connect to | URI | qemu:///system ||
 *    || domr.arch | instruction set for domr template | string | i686 ||
 *    || private.bridge.name | private bridge where the domrs have their private interface | string | vmops0 ||
 *    || public.bridge.name | public bridge where the domrs have their public interface | string | br0 ||
 *    || private.network.name | name of the network where the domrs have their private interface | string | vmops-private ||
 *    || private.ipaddr.start | start of the range of private ip addresses for domrs | ip address | 192.168.166.128 ||
 *    || private.ipaddr.end | end of the range of private ip addresses for domrs | ip address | start + 126  ||
 *    || private.macaddr.start | start of the range of private mac addresses for domrs | mac address | 00:16:3e:77:e2:a0 ||
 *    || private.macaddr.end | end of the range of private mac addresses for domrs | mac address | start + 126 ||
 *    || pool | the parent of the storage pool hierarchy
 *  }
 **/
@Local(value={ServerResource.class})
public class LibvirtComputingResource extends ServerResourceBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(LibvirtComputingResource.class);
    
    private String _createvnetPath;
    private String _vnetcleanupPath;
    private String _modifyVlanPath;
    private String _versionstringpath;
    private String _patchdomrPath;
    private String _host;
    private String _dcId;
    private String _pod;
    
	private static final class KeyValueInterpreter extends OutputInterpreter {
		private final Map<String, String> map = new HashMap<String, String>();
		
		@Override
		public String interpret(BufferedReader reader) throws IOException {
			String line = null;
		    int numLines=0;
		    while ((line = reader.readLine()) != null) {
		       String [] toks = line.trim().split("=");
		       if (toks.length < 2) s_logger.warn("Failed to parse Script output: " + line);
		       else map.put(toks[0].trim(), toks[1].trim());
		       numLines++;
 		    }
		    if (numLines == 0) {
		    	s_logger.warn("KeyValueInterpreter: no output lines?");
		    }
		    return null;
		}

		public Map<String, String> getKeyValues() {
			return map;
		}
	}
	
	@Override
    protected String getDefaultScriptsDir() {
	    return null;
	}

	public class DomrPrivateNetwork {
		private final String macAddress;
		private final String name;
		private final String ipAddress;
		
		public DomrPrivateNetwork (String mac, String name, String ip) {
			this.macAddress = mac;
			this.name = name;
			this.ipAddress = ip;
		}

		public String getMacAddress() {
			return macAddress;
		}

		public String getName() {
			return name;
		}

		public String getIpAddress() {
			return ipAddress;
		}
	}
	
	protected static MessageFormat domrXMLformat= new MessageFormat( "<domain type=''{0}''>" +
		"  <name>{1}</name>" +
		"  <uuid>{24}</uuid>" +
		"  <memory>{2}</memory>" +
		"  <vcpu>1</vcpu>" +
		"  <os>" +
		"    <type arch=''{3}''>hvm</type>" +
		"    <kernel>{13}</kernel>"+
		"    <ramdisk>{14}</ramdisk>"+
		"    <cmdline>ro root=/dev/sda1 acpi=force console=ttyS0 selinux=0 eth0ip={15} eth0mask={16} eth2ip={17} eth2mask={18} gateway={19} dns1={20} dns2={21} domain={22}</cmdline>"+
		"  </os>" +
		"  <features>" +
		"    <acpi/>" +
		"    <pae/>" +
		"  </features>" +
		"  <on_poweroff>destroy</on_poweroff>" +
		"  <on_reboot>restart</on_reboot>" +
		"  <on_crash>destroy</on_crash>" +
		"  <devices>" +
		"    <emulator>{23}</emulator>" +
		"    <disk type=''file'' device=''disk''>" +
		"      <source file=''{4}''/>" +
		"      <target dev=''hda'' bus=''ide''/>" +
		"    </disk>" +
		"    <interface type=''bridge''>" +
		"      <mac address=''{5}'' />" +
		"      <source bridge=''{6}''/>" +
		"      <target dev=''{7}''/>" +
		"      <model type=''virtio''/>" +
		"    </interface>" +
		"    <interface type=''network''>" +
		"      <source network=''{8}''/>" +
		"      <mac address=''{9}'' />" +
		"      <model type=''virtio''/>" +
		"    </interface>" +
		"    <interface type=''bridge''>" +
		"      <mac address=''{10}'' />" +
		"      <source bridge=''{11}''/>" +
		"      <target dev=''{12}''/>" +
		"      <model type=''virtio''/>" +
		"    </interface>" +
		"    <console type=''pty''/>" +
		"    <input type=''mouse'' bus=''ps2''/>" +
		"  </devices>" +
		"</domain>");
	
	protected static MessageFormat consoleProxyXMLformat= new MessageFormat( "<domain type=''{0}''>" +
			"  <name>{1}</name>" +
			"  <uuid>{2}</uuid>" +
			"  <memory>{3}</memory>" +
			"  <vcpu>1</vcpu>" +
			"  <os>" +
			"    <type arch=''{4}''>hvm</type>" +
			"    <kernel>{5}</kernel>"+
			"    <ramdisk>{6}</ramdisk>"+
			"    <cmdline>ro root=/dev/sda1 acpi=force console=ttyS0 selinux=0  eth0ip=0.0.0.0 eth0mask=255.255.255.0  eth2ip={7} eth2mask={8} gateway={9} dns1={10} dns2={11} domain={12} template=domP host={19} port={20} zone={21} pod={22} guid=Proxy.{23} proxy_vm={23}</cmdline>"+
			"  </os>" +
			"  <features>" +
			"    <acpi/>" +
			"    <pae/>" +
			"  </features>" +
			"  <on_poweroff>destroy</on_poweroff>" +
			"  <on_reboot>restart</on_reboot>" +
			"  <on_crash>destroy</on_crash>" +
			"  <devices>" +
			"    <emulator>{13}</emulator>" +
			"    <disk type=''file'' device=''disk''>" +
			"      <source file=''{14}''/>" +
			"      <target dev=''hda'' bus=''ide''/>" +
			"    </disk>" +
			"    <interface type=''network''>" +
			"      <source network=''default''/>" +
			"      <model type=''virtio''/>" +
			"    </interface>" +
			"    <interface type=''network''>" +
			"      <source network=''{15}''/>" +
			"      <mac address=''{16}'' />" +
			"      <model type=''virtio''/>" +
			"    </interface>" +
			"    <interface type=''bridge''>" +
			"      <mac address=''{17}'' />" +
			"      <source bridge=''{18}''/>" +
			"      <model type=''virtio''/>" +
			"    </interface>" +
			"    <console type=''pty''/>" +
			"    <input type=''mouse'' bus=''ps2''/>" +
			"  </devices>" +
			"</domain>");
	
	protected static MessageFormat vmXMLformat= new MessageFormat( "<domain type=''{0}''>" +
			"  <name>{1}</name>" +
			"  <uuid>{2}</uuid>" +
			"  <memory>{3}</memory>" +
			"  <vcpu>{4}</vcpu>" +
			"  <os>" +
			"    <type arch=''{5}''>hvm</type>" +
			"    <boot dev=''cdrom''/>" +
			"    <boot dev=''hd''/>" +
			"  </os>" +
			"  <features>" +
			"    <acpi/>" +
			"    <pae/>" +
			"  </features>" +
			"  <on_poweroff>destroy</on_poweroff>" +
			"  <on_reboot>restart</on_reboot>" +
			"  <on_crash>destroy</on_crash>" +
			"  <devices>" +
			"    <emulator>{6}</emulator>" +
			"    <disk type=''file'' device=''disk''>" +
			"      <source file=''{7}''/>" +
			"      <target dev=''hda'' bus=''ide''/>" +
			"    </disk>" +
			"	 <disk type=''file'' device=''cdrom''>" +
			"      <source file=''{8}''/>" +
			"	   <target dev=''hdc'' bus=''ide''/>" +
			"	   <readonly/>" +
			"	 </disk>" +
			"    <interface type=''bridge''>" +
			"      <mac address=''{9}'' />" +
			"      <source bridge=''{10}''/>" +
			"      <model type=''e1000''/>" +
			"    </interface>" +
			"    <console type=''pty''/>" +
			"    <graphics type=''vnc'' autoport=''yes'' listen=''''/>" +
			"    <input type=''tablet'' bus=''usb''/>" +
			"  </devices>" +
			"</domain>");
	
	protected static MessageFormat IsoXMLformat = new MessageFormat(
			"	<disk type=''file'' device=''cdrom''>" +
			"		<source file=''{0}''/>" +
			"		<target dev=''hdc'' bus=''ide''/>" +
			"		<readonly/>" +
			"	</disk>");
	
	protected static MessageFormat DiskXMLformat = new MessageFormat(
			"	<disk type=''file'' device=''disk''>" +
			"		<source file=''{0}''/>" +
			"		<target dev=''{1}'' bus=''scsi''/>" +
			"	</disk>");
	
	protected Connect _conn;
	protected ArrayList<DomrPrivateNetwork> _domrPrivConfig = new ArrayList<DomrPrivateNetwork>();
	protected String _hypervisorType;
	protected String _hypervisorURI;
	protected String _hypervisorPath;
	protected String _privNwName;
	protected String _privBridgeName;
	protected String _publicBridgeName;
	protected String _privateBridgeIp;
	protected String _domrArch;
	protected String _domrKernel;
	protected String _domrRamdisk;
	protected String _pool;

	
	protected boolean _disconnected = true;
	protected int _timeout;
	protected int _stopTimeout;
    protected static HashMap<DomainInfo.DomainState, State> s_statesTable;
    static {
            s_statesTable = new HashMap<DomainInfo.DomainState, State>();
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF, State.Stopped);
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_PAUSED, State.Running);
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_RUNNING, State.Running);
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_BLOCKED, State.Running);
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_NOSTATE, State.Unknown);
            s_statesTable.put(DomainInfo.DomainState.VIR_DOMAIN_SHUTDOWN, State.Stopping);
    }

    protected HashMap<String, State> _vms = new HashMap<String, State>(20);
    protected List<String> _vmsKilled = new ArrayList<String>();

    protected BitSet _domrIndex;
    protected Map<String,String> _domrNames = new ConcurrentHashMap<String, String>();
    
	private VirtualRoutingResource _virtRouterResource;
	private StorageResource _storageResource;

	private boolean _debug;

	private String _pingTestPath;

	private int _dom0MinMem;

	private enum defineOps {
		UNDEFINE_VM,
		DEFINE_VM
	}
	
	private String getEndIpFromStartIp(String startIp, int numIps) {
		String[] tokens = startIp.split("[.]");
		assert(tokens.length == 4);
		int lastbyte = Integer.parseInt(tokens[3]);
		lastbyte = lastbyte + numIps;
		tokens[3] = Integer.toString(lastbyte);
		StringBuilder end = new StringBuilder(15);
		end.append(tokens[0]).append(".").append(tokens[1]).append(".").append(tokens[2]).append(".").append(tokens[3]);
		return end.toString();
	}
	
	private Map<String, Object> getDeveloperProperties() throws ConfigurationException {
		final File file = PropertiesUtil.findConfigFile("developer.properties");
        if (file == null) {
            throw new ConfigurationException("Unable to find developer.properties.");
        }

        s_logger.info("developer.properties found at " + file.getAbsolutePath());
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream(file));

            String startMac = (String)properties.get("private.macaddr.start");
            if (startMac == null) {
            	throw new ConfigurationException("Developers must specify start mac for private ip range");
            }

            String startIp  = (String)properties.get("private.ipaddr.start");
            if (startIp == null) {
            	throw new ConfigurationException("Developers must specify start ip for private ip range");
            }
            final Map<String, Object> params = PropertiesUtil.toMap(properties);

            String endIp  = (String)properties.get("private.ipaddr.end");
            if (endIp == null) {
            	endIp = getEndIpFromStartIp(startIp, 16);
            	params.put("private.ipaddr.end", endIp);
            }
            return params;
        } catch (final FileNotFoundException ex) {
            throw new VmopsRuntimeException("Cannot find the file: " + file.getAbsolutePath(), ex);
        } catch (final IOException ex) {
            throw new VmopsRuntimeException("IOException in reading " + file.getAbsolutePath(), ex);
        }
	}
	
	protected String getDefaultNetworkScriptsDir() {
	    return "scripts/vm/network/vnet";
	}
	
    public synchronized String modifyVlan(final boolean add, final String vlanId, final String vlanGateway) {
        final Script command = new Script(_modifyVlanPath, _timeout, s_logger);
        
        if (add)
            command.add("-o", "add");
        else
            command.add("-o", "delete");
        
        command.add("-v", vlanId);
        command.add("-g", vlanGateway);
        
        return command.execute();
    }

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		boolean success = super.configure(name, params);
		if (! success)
			return false;
		_virtRouterResource = new VirtualRoutingResource();
		
		// Set the domr scripts directory
		params.put("domr.scripts.dir", "scripts/network/domr/kvm");
		
		success = _virtRouterResource.configure(name, params);
		
		String kvmScriptsDir = (String)params.get("kvm.scripts.dir");
		if (kvmScriptsDir == null) {
		    kvmScriptsDir = "scripts/vm/hypervisor/kvm";
		}
		
		String networkScriptsDir = (String)params.get("network.scripts.dir");
		if (networkScriptsDir == null) {
		    networkScriptsDir = getDefaultNetworkScriptsDir();
		}
		
		if ( ! success) {
			return false;
		}
		
		_host = (String)params.get("host");
		if (_host == null) {
			_host = "localhost";
        }


		_dcId = (String) params.get("zone");
		if (_dcId == null) {
			_dcId = "default";
		}

        _pod = (String) params.get("pod");
        if (_pod == null) {
        	_pod = "default";
        }
		
        _createvnetPath = Script.findScript(networkScriptsDir, "createvnet.sh");
        if(_createvnetPath == null) {
            throw new ConfigurationException("Unable to find createvnet.sh");
        }

        _vnetcleanupPath = Script.findScript(networkScriptsDir, "vnetcleanup.sh");
        if(_vnetcleanupPath == null) {
            throw new ConfigurationException("Unable to find createvnet.sh");
        }
        
        _modifyVlanPath = Script.findScript(networkScriptsDir, "modifyvlan.sh");
        if (_modifyVlanPath == null) {
            throw new ConfigurationException("Unable to find modifyvlan.sh");
        }
        
        _versionstringpath = Script.findScript(kvmScriptsDir, "versions.sh");
        if (_versionstringpath == null) {
            throw new ConfigurationException("Unable to find versions.sh");
        }
        
        _patchdomrPath = Script.findScript(kvmScriptsDir + "/patch/", "rundomrpre.sh");
        if (_patchdomrPath == null) {
        	throw new ConfigurationException("Unable to find rundomrpre.sh");
        }
        
		String value = (String)params.get("developer");
        boolean isDeveloper = Boolean.parseBoolean(value);
        
        if (isDeveloper) {
        	params.putAll(getDeveloperProperties());
        }
        
        _pool = (String) params.get("pool");
        if (_pool == null) {
        	_pool = "/root";
        }
        

        _storageResource = new Qcow2StorageResource();
        success = _storageResource.configure(name, params);
        if (!success) {
        	return false;
        }

        
        String instance = (String)params.get("instance");
        
		_hypervisorType = (String)params.get("hypervisor.type");
		if (_hypervisorType == null) {
			_hypervisorType = "kvm";
		}
		
		_hypervisorURI = (String)params.get("hypervisor.uri");
		if (_hypervisorURI == null) {
			_hypervisorURI = "qemu:///system";
		}
        String startMac = (String)params.get("private.macaddr.start");
        if (startMac == null) {
        	startMac = "00:16:3e:77:e2:a0";
        }

        String startIp  = (String)params.get("private.ipaddr.start");
        if (startIp == null) {
        	startIp = "192.168.166.128";
        }
        
        _pingTestPath = Script.findScript(kvmScriptsDir, "pingtest.sh");
        if (_pingTestPath == null) {
            throw new ConfigurationException("Unable to find the pingtest.sh");
        }
        
        String endIp  = (String)params.get("private.ipaddr.end");
        if (endIp == null) {
          endIp = getEndIpFromStartIp(startIp, 126);
        }
        
        _privateBridgeIp = (String)params.get("private.bridge.ipaddr");
        if (_privateBridgeIp == null) {
        	if (isDeveloper) {
        		String [] tokens = startIp.split("[.]");
                assert(tokens.length == 4);
                StringBuilder bridgeIp = new StringBuilder(15);
                bridgeIp.append(tokens[0]).append(".").append(tokens[1]).append(".").append(tokens[2]).append(".").append("1");
                _privateBridgeIp = bridgeIp.toString();
        	} else {
        		_privateBridgeIp = "192.168.166.1";
        	}
        }
        
        _privBridgeName = (String)params.get("private.bridge.name");
        if (_privBridgeName == null) {
        	if (isDeveloper) {
        		_privBridgeName = "vmops-" + instance + "-0";
        	} else {
        		_privBridgeName = "vmops0";
        	}
        }
        
        _publicBridgeName = (String)params.get("public.network.device");
        if (_publicBridgeName == null) {
        	_publicBridgeName = "vmops-br0";
        }
        
        _privNwName = (String)params.get("private.network.name");
        if (_privNwName == null) {
        	if (isDeveloper) {
        		_privNwName = "vmops-" + instance + "-private";
        	} else {
        		_privNwName = "vmops-private";
        	}
        }
        
         value = (String)params.get("scripts.timeout");
        _timeout = NumbersUtil.parseInt(value, 120) * 1000;
        
        value = (String)params.get("stop.script.timeout");
        _stopTimeout = NumbersUtil.parseInt(value, 120) * 1000;
        

        _domrArch = (String)params.get("domr.arch");
        if (_domrArch == null ) {
        	_domrArch = "i686";
        } else if (!"i686".equalsIgnoreCase(_domrArch) && !"x86_64".equalsIgnoreCase(_domrArch)) {
        	throw new ConfigurationException("Invalid architecture (domr.arch) -- needs to be i686 or x86_64");
        }
        
        _domrKernel = (String)params.get("domr.kernel");
        if (_domrKernel == null ) {
        	_domrKernel = new File("/var/lib/libvirt/images/vmops-domr-kernel").getAbsolutePath();
        }
        _domrRamdisk = (String)params.get("domr.ramdisk");
        if (_domrRamdisk == null ) {
        	_domrRamdisk = new File("/var/lib/libvirt/images/vmops-domr-initramfs").getAbsolutePath();
        }
        
        
        
        value = (String)params.get("host.reserved.mem.mb");
        _dom0MinMem = NumbersUtil.parseInt(value, 0)*1024*1024;
         
        
        value = (String)params.get("debug.mode");
        _debug = Boolean.parseBoolean(value);
        
        long start = NetUtils.ip2Long(startIp);
        long end = NetUtils.ip2Long(endIp);
        if (end - start > 254 || end -start <  0 ) {
        	throw new ConfigurationException("Private Ip range is too large or negative");
        }
        
        long macStart = NetUtils.mac2Long(startMac);
        int i = 0;
        while (start <= end) {
        	DomrPrivateNetwork nw = new DomrPrivateNetwork(NetUtils.long2Mac(macStart), "domr"+i, NetUtils.long2Ip(start));
        	_domrPrivConfig.add(i, nw);
        	macStart++;
        	start++;
        	i++;
        }
        _domrIndex = new BitSet(_domrPrivConfig.size());
        
		try{
			_conn = new Connect(_hypervisorURI, false);
		} catch (LibvirtException e){
			throw new ConfigurationException("Unable to connect to hypervisor: " +  e.getMessage());
		}
		
		/* Does node support HVM guest? If not, exit*/
		if (!IsHVMEnabled()) {
			throw new ConfigurationException("NO HVM support on this machine, pls make sure: " +
												"1. VT/SVM is supported by your CPU, or is enabled in BIOS. " +
												"2. kvm modules is installed");
		}
		
		_hypervisorPath = getHypervisorPath();
		
		Network vmopsNw = null;
		try {
			 vmopsNw = _conn.networkLookupByName(_privNwName);
		} catch (LibvirtException lve){
			
		}
		if (vmopsNw == null) {
			try {
				/*vmopsNw = conn.networkCreateXML("<network>" +
						"  <name>vmops-private</name>"+
						"  <bridge name='vmops0'/>"+
						"  <ip address='192.168.166.1' netmask='255.255.255.0'>"+
						"    <dhcp>"+
						"      <range start='192.168.166.128' end='192.168.166.254'/>+
						"      <host mac='00:16:3e:77:e2:a0' name='domr1' ip='192.168.166.128' />" +
						"      <host mac='00:16:3e:77:e2:a1' name='domr2' ip='192.168.166.129' />" +
						"      <host mac='00:16:3e:77:e2:a2' name='domr3' ip='192.168.166.130' />" +
						"    </dhcp>"+
						"  </ip>"+
				"</network>");*/
				
				_virtRouterResource.cleanupPrivateNetwork(_privNwName, _privBridgeName);
				
				StringBuilder nwXML = new StringBuilder("<network>").append("\n")
						.append("  <name>" + _privNwName + "</name>").append("\n")
						.append("  <bridge name='").append(_privBridgeName).append("'/>").append("\n")
						.append("  <ip address='").append(_privateBridgeIp).append("' netmask='255.255.255.0'>").append("\n")
						.append("    <dhcp>").append("\n")
						.append("      <range start='").append(startIp).append("' end='").append(endIp).append("'/>").append("\n");
				for (int j=0; j< _domrPrivConfig.size(); j++) {
					DomrPrivateNetwork nw = _domrPrivConfig.get(j);
					nwXML.append("      <host mac='").append(nw.getMacAddress()).append("' name='")
					     .append(nw.getName()).append("' ip='").append(nw.getIpAddress()).append("' />").append("\n");
				}
				nwXML.append("    </dhcp>").append("\n")
				     .append("  </ip>").append("\n")
				     .append("</network>");
				s_logger.debug(nwXML.toString());
				vmopsNw = _conn.networkCreateXML(nwXML.toString());
			} catch (LibvirtException lve) {
				throw new ConfigurationException("Unable to define private network " +  lve.getMessage());
			}
		} else {
			s_logger.info("Found private network " + _privNwName + " already defined");
		}

		return true;
	}
	
	private String getVnetId(String vnetId) {
		String id = "0000" + vnetId;
		return id.substring(id.length() - 4);
	}
	
	protected synchronized String startDomainRouter(String domrName, String fileName, int ramMB, String vnetId, String guestMac, String privateMac, String publicMac, 
													String eth0ip, String eth0mask, String eth2ip, String eth2mask, String gw, String dns1, String dns2, String domain,
													String domrKernel, String domrRamdisk) {
		String memSize = Integer.toString(ramMB*1024);
		String vnetBridge= "vnbr" + vnetId;
		String vnetDev = "vtap" + vnetId;
		String pubDev = "tap" + vnetId;
		String uuid = UUID.nameUUIDFromBytes(domrName.getBytes()).toString();
		String domXML = domrXMLformat.format(new Object[]{_hypervisorType, domrName, memSize,
				_domrArch, fileName, guestMac,
				vnetBridge, vnetDev, _privNwName,
				privateMac, publicMac, _publicBridgeName,
				pubDev, domrKernel, domrRamdisk,
				eth0ip, eth0mask, eth2ip, eth2mask, gw, dns1, dns2, domain, _hypervisorPath, uuid});
		s_logger.debug(domXML);
		
		
		String result = startDomain(domrName, domXML);
		if (result == null) {
			//TODO: workaround for KVM on ubuntu, disable bridge forward table
			Script disableFD = new Script("/bin/bash", _timeout);
			disableFD.add("-c");
			disableFD.add("brctl setfd " + vnetBridge + " 0; brctl setageing " + vnetBridge + " 0");
			disableFD.execute();
		}
		return result;
	}
	
	protected synchronized String startConsoleProxy(String domrName, String fileName, int ramMB, String privateMac, 
														String publicMac, String eth2ip, String eth2mask, String gw,
														String dns1, String dns2, String domain, 
														String domrKernel, String domrRamdisk, String proxyVmId, String host, int port) {
		String memSize = Integer.toString(ramMB*1024);
		String uuid = UUID.nameUUIDFromBytes(domrName.getBytes()).toString();
		String domXML = consoleProxyXMLformat.format(new Object[]{_hypervisorType, domrName, uuid, memSize, _domrArch,
				domrKernel, domrRamdisk, 
				eth2ip, eth2mask, gw, dns1, dns2, domain,
				_hypervisorPath, fileName, _privNwName, privateMac,
				 publicMac, _publicBridgeName, host, port, _dcId, _pod, proxyVmId});
		s_logger.debug(domXML);
		
		
		String result = startDomain(domrName, domXML);
	
		return result;
	}
	
	protected String startDomain(String vmName, String domainXML) {
		/*No duplicated vm, we will success, or failed*/
		boolean failed =false;
		Domain dm = null;
		try {
			dm = _conn.domainDefineXML(domainXML);
		} catch (final LibvirtException e) {
			/*Duplicated defined vm*/
			s_logger.warn("Failed to define domain " + vmName + ": " + e.getMessage());
			failed = true;
		} finally {
			try {
				if (dm != null)
					dm.free();
			} catch (final LibvirtException e) {
				
			}
		}
		
		/*If failed, undefine the vm*/
		Domain dmOld = null;
		Domain dmNew = null;
		try {
			if (failed) {
				dmOld = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
				dmOld.undefine();
				dmNew = _conn.domainDefineXML(domainXML);
			}
		} catch (final LibvirtException e) {
			s_logger.warn("Failed to define domain (second time) " + vmName + ": " + e.getMessage());
			return e.getMessage();
		} finally {
			try {
				if (dmOld != null)
					dmOld.free();
				if (dmNew != null)
					dmNew.free();
			} catch (final LibvirtException e) {
				
			}
		}

		/*Start the VM*/
		try {
			dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
			dm.create();
		} catch (LibvirtException e) {
			s_logger.warn("Failed to start domain: " + vmName + ": " + e.getMessage());
			return e.getMessage();
		} finally {
			try {
				if (dm != null)
					dm.free();
			} catch (final LibvirtException e) {
				
			}
		}
		return null;
	}
	
	protected  synchronized int getNextDomrIndex() {
		int result = _domrIndex.nextClearBit(0);
		_domrIndex.set(result);
		return result;
	}
	
	protected  synchronized void clearDomrIndex(int index) {
		_domrIndex.set(index, false);
	}
	
	
	@Override
	public boolean stop() {
		if (_conn != null) {
			try {
				_conn.close();
			} catch (LibvirtException e) {
			}
			_conn = null;
		}
		return true;
	}

	public static void main(String[] args) {
		s_logger.addAppender(new org.apache.log4j.ConsoleAppender(new org.apache.log4j.PatternLayout(), "System.out"));
		LibvirtComputingResource test = new LibvirtComputingResource();
		Map<String, Object> params = new HashMap<String, Object>();
		try {
			test.configure("test", params);
		} catch (ConfigurationException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		String result = null;
		//String result = test.startDomainRouter("domr1", "/var/lib/images/centos.5-4.x86-64/centos-small.img", 128, "0064", "02:00:30:00:01:01", "00:16:3e:77:e2:a1", "02:00:30:00:64:01");
		boolean created = (result==null);
		s_logger.info("Domain " + (created?" ":" not ") + " created");
		
		s_logger.info("Rule " + (created?" ":" not ") + " created");
		test.stop();

	}

	@Override
	public Answer executeRequest(Command cmd) {

        try {
            if (cmd instanceof StartCommand) {
                return execute((StartCommand)cmd);
            } else if (cmd instanceof StopCommand) {
                return execute((StopCommand)cmd);
            } else if (cmd instanceof GetVmStatsCommand) {
                return execute((GetVmStatsCommand)cmd);
            } else if (cmd instanceof RebootCommand) {
                return execute((RebootCommand)cmd);
            } else if (cmd instanceof GetHostStatsCommand) {
                return execute((GetHostStatsCommand)cmd);
            } else if (cmd instanceof CheckStateCommand) {
                return executeRequest(cmd);
            } else if (cmd instanceof MirrorCommand) {
                return executeRequest(cmd);
            } else if (cmd instanceof CheckHealthCommand) {
                return execute((CheckHealthCommand)cmd);
            } else if (cmd instanceof PrepareForMigrationCommand) {
                return execute((PrepareForMigrationCommand)cmd);
            } else if (cmd instanceof MigrateCommand) {
                return execute((MigrateCommand)cmd);
            } else if (cmd instanceof PingTestCommand) {
                return execute((PingTestCommand)cmd);
            } else if (cmd instanceof CheckVirtualMachineCommand) {
                return execute((CheckVirtualMachineCommand)cmd);
            } else if (cmd instanceof ReadyCommand) {
            	return execute((ReadyCommand)cmd);
            } else if (cmd instanceof StartRouterCommand) {
            	return execute((StartRouterCommand)cmd);
            } else if(cmd instanceof StartConsoleProxyCommand) {
            	return execute((StartConsoleProxyCommand)cmd);
            } else if (cmd instanceof AttachIsoCommand) {
            	return execute((AttachIsoCommand) cmd);
            } else if (cmd instanceof AttachDiskCommand) {
            	return execute((AttachDiskCommand) cmd);
            } else if (cmd instanceof SetFirewallRuleCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            }else if (cmd instanceof LoadBalancerCfgCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof IPAssocCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof CheckConsoleProxyLoadCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if(cmd instanceof WatchConsoleProxyLoadCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof WatchNetworkCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof SavePasswordCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof DhcpEntryCommand) {
            	return _virtRouterResource.executeRequest(cmd);
            } else if (cmd instanceof StopCommand) {
            	return execute((StopCommand)cmd);
            } else if (cmd instanceof ModifyVlanCommand) {
            	return execute((ModifyVlanCommand)cmd);
            } else if (cmd instanceof CheckConsoleProxyLoadCommand) {
            	return execute((CheckConsoleProxyLoadCommand)cmd);
            } else if (cmd instanceof WatchConsoleProxyLoadCommand) {
            	return execute((WatchConsoleProxyLoadCommand)cmd);
            } else if (cmd instanceof GetVncPortCommand) {
            	return execute((GetVncPortCommand)cmd);
            } else if (_storageResource != null){
            	return _storageResource.executeRequest(cmd);
            } else {
        		s_logger.warn("Unsupported command ");
                return Answer.createUnsupportedCommandAnswer(cmd);
            }
        } catch (final IllegalArgumentException e) {
            return new Answer(cmd, false, e.getMessage());
        }
	}

	protected GetVncPortAnswer execute(GetVncPortCommand cmd) {
		String realVmName = getRealVmName(cmd.getName());	
		return new GetVncPortAnswer(cmd, 5900 + getVncPort(realVmName));
	}
	
	protected Answer execute(final CheckConsoleProxyLoadCommand cmd) {
		return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
	}

	protected Answer execute(final WatchConsoleProxyLoadCommand cmd) {
		return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
	}

	private Answer executeProxyLoadScan(final Command cmd, final long proxyVmId, final String proxyVmName, final String proxyManagementIp, final int cmdPort) {
		String result = null;

		final StringBuffer sb = new StringBuffer();
		sb.append("http://").append(proxyManagementIp).append(":" + cmdPort).append("/cmd/getstatus");

		boolean success = true;
		try {
			final URL url = new URL(sb.toString());
			final URLConnection conn = url.openConnection();

			final InputStream is = conn.getInputStream();
			final BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			final StringBuilder sb2 = new StringBuilder();
			String line = null;
			try {
				while ((line = reader.readLine()) != null)
					sb2.append(line + "\n");
				result = sb2.toString();
			} catch (final IOException e) {
				success = false;
			} finally {
				try {
					is.close();
				} catch (final IOException e) {
					s_logger.warn("Exception when closing , console proxy address : " + proxyManagementIp);
					success = false;
				}
			}
		} catch(final IOException e) {
			s_logger.warn("Unable to open console proxy command port url, console proxy address : " + proxyManagementIp);
			success = false;
		}

		return new ConsoleProxyLoadAnswer(cmd, proxyVmId, proxyVmName, success, result);
	}

	private Answer execute(StartConsoleProxyCommand cmd) {
		final ConsoleProxyVO router = cmd.getProxy();
		String result = null;
		int domrId =  getNextDomrIndex();
		if (domrId == -1) {
			return new Answer(cmd, false, "Ran out of private Ip addresses");
		}

		final String vmName = getIndexedDomrName(cmd.getVmName(), domrId);

		State state = State.Stopped;
		synchronized(_vms) {
			_vms.put(cmd.getVmName(), State.Starting);
		}
		try {
			if ( result != null) {
				throw new ExecutionException(result, null);
			}
			String privateIp = _domrPrivConfig.get(domrId).getIpAddress();
			String privateMac = _domrPrivConfig.get(domrId).getMacAddress();
			List<VolumeVO> rootVolumes = findVolumes(cmd.getVolumes(), VolumeType.ROOT, true);

			if (rootVolumes.size() != 1) {
				throw new ExecutionException("Could not find DomR root disk.", null);
			}

			VolumeVO rootVolume = rootVolumes.get(0);

			/*final Script command = new Script(_patchdomrPath, _timeout, s_logger);
			command.add("-l", vmName);
			command.add("-t", "domp");
			command.add("-d", rootVolume.getPath());
			result = command.execute();
			if (result != null) {
				throw new ExecutionException(result, null);
			}*/
			
			result = startConsoleProxy(vmName, rootVolume.getPath(), router.getRamSize(), privateMac, router.getPublicMacAddress(), router.getPublicIpAddress(), 
										router.getPublicNetmask(), router.getGateway(), router.getDns1(), router.getDns2(), router.getDomain(), 
										_domrKernel, _domrRamdisk,
										String.valueOf(cmd.getProxy().getId()), cmd.getManagementHost(), cmd.getManagementPort());
			if (result != null) {
				throw new ExecutionException(result, null);
			}

			result = _virtRouterResource.connect(privateIp, cmd.getProxyCmdPort());
			if (result != null) {
				throw new ExecutionException(result, null);
			}

			if (result != null) {
				throw new ExecutionException(result, null);
			}

			state = State.Running;
			_domrNames.put(cmd.getVmName(), vmName);
			//	            _collector.addVM(vmName);
			//	            _collector.submitMetricsJobs();
			return new StartConsoleProxyAnswer(cmd, privateIp, privateMac);
		} catch (final ExecutionException e) {
			if (!_debug) {
				cleanupVM(vmName, null);
				clearDomrIndex(domrId);
			}
			return new Answer(cmd, false, e.getMessage());
		} catch (final Throwable th) {
			s_logger.warn("Exception while starting router.", th);
			if (!_debug) {
				cleanupVM(vmName, null);
				clearDomrIndex(domrId);
			}
			return createErrorAnswer(cmd, "Unable to start router", th);
		} finally {
			synchronized(_vms) {
				_vms.put(cmd.getVmName(), state);
			}
		}
	}

	private Answer execute(AttachIsoCommand cmd) {
		String result = attachOrDetachISO(cmd.getVmName(), cmd.getIsoPath(), cmd.isAttach());
		
		if (result == null)
			return new Answer(cmd);
		else
			return new Answer(cmd, false, result);
		
	}
	
	private Answer execute(AttachDiskCommand cmd) {
		String result = attachOrDetachDisk(cmd.getAttach(), cmd.getVmName(), cmd.getVolumePath());
		
		if (result == null)
			return new Answer(cmd);
		else
			return new Answer(cmd, false, result);
		
	}
	
	protected static List<VolumeVO> findVolumes(final List<VolumeVO> volumes, final Volume.VolumeType vType, boolean singleVolume) {
		List<VolumeVO> filteredVolumes = new ArrayList<VolumeVO>();
		
		if (volumes == null)
			return filteredVolumes;

		for (final VolumeVO v: volumes) {
			if (v.getVolumeType() == vType) {
				filteredVolumes.add(v);
				
				if(singleVolume)
					return filteredVolumes;
			}
		}

		return filteredVolumes;
	}
	
	private Answer execute(StartRouterCommand cmd) {

	        final DomainRouter router = cmd.getRouter();
	        final String vnet = getVnetId(router.getVnet());
	        String result = null;
            int domrId =  getNextDomrIndex();
            if (domrId == -1) {
            	return new Answer(cmd, false, "Ran out of private Ip addresses");
            }

	        final String vmName = getIndexedDomrName(cmd.getVmName(), domrId);

	        State state = State.Stopped;
	        synchronized(_vms) {
	            _vms.put(cmd.getVmName(), State.Starting);
	        }
	        try {
	        	result = createVnet(vnet);
	        	if ( result != null) {
	                throw new ExecutionException(result, null);
	        	}
	            String privateIp = _domrPrivConfig.get(domrId).getIpAddress();
	            String privateMac = _domrPrivConfig.get(domrId).getMacAddress();
	            List<VolumeVO> rootVolumes = findVolumes(cmd.getVolumes(), VolumeType.ROOT, true);
	            
	            if (rootVolumes.size() != 1) {
	            	throw new ExecutionException("Could not find DomR root disk.", null);
	            }
	            
	            VolumeVO rootVolume = rootVolumes.get(0);
	            
	            /*final Script command = new Script(_patchdomrPath, _timeout, s_logger);
	            command.add("-l", vmName);
	            command.add("-t", "domr");
	            command.add("-d", rootVolume.getPath());
	            result = command.execute();
	            if (result != null) {
	            	throw new ExecutionException(result, null);
	            }*/
	            
	            result = startDomainRouter(vmName, rootVolume.getPath(), router.getRamSize(), vnet, router.getGuestMacAddress(), privateMac, 
	            							router.getPublicMacAddress(), router.getGuestIpAddress(), router.getGuestNetmask(), router.getPublicIpAddress(), router.getPublicNetmask(), 
	            							router.getGateway(), router.getDns1(), router.getDns2(), router.getDomain(),
	            							_domrKernel, _domrRamdisk);
	            if (result != null) {
	                throw new ExecutionException(result, null);
	            }

	            result = _virtRouterResource.connect(privateIp);
	            if (result != null) {
	                throw new ExecutionException(result, null);
	            }

	            if (result != null) {
	                throw new ExecutionException(result, null);
	            }

	            state = State.Running;
	            _domrNames.put(cmd.getVmName(), vmName);
//	            _collector.addVM(vmName);
//	            _collector.submitMetricsJobs();
	            return new StartRouterAnswer(cmd, privateIp, privateMac);
	        } catch (final ExecutionException e) {
	            if (!_debug) {
	                cleanupVM(vmName, vnet);
		            clearDomrIndex(domrId);
	            }
	            return new Answer(cmd, false, e.getMessage());
	        } catch (final Throwable th) {
	            s_logger.warn("Exception while starting router.", th);
	            if (!_debug) {
	                cleanupVM(vmName, vnet);
		            clearDomrIndex(domrId);
	            }
	            return createErrorAnswer(cmd, "Unable to start router", th);
	        } finally {
	            synchronized(_vms) {
	                _vms.put(cmd.getVmName(), state);
	            }
	        }
	}

	private Answer execute(ReadyCommand cmd) {
		return new ReadyAnswer(cmd);
	}
	
	protected State convertToState(DomainInfo.DomainState ps) {
		final State state = s_statesTable.get(ps);
		return state == null ? State.Unknown : state;
	}

	protected State getVmState(final String vmName) {
		int retry = 3;
		Domain vms = null;
		while (retry-- > 0) {
			try {
				vms = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
				State s = convertToState(vms.getInfo().state);
				return s;
			} catch (final LibvirtException e) {
				s_logger.warn("Can't get vm state " + vmName + e.getMessage() + "retry:" + retry);
			} finally {
				try {
					if (vms != null) {
						vms.free();
					}
				} catch (final LibvirtException e) {

				}
			}
		}
		return State.Stopped;
	}

	private Answer execute(CheckVirtualMachineCommand cmd) {
		if (VirtualMachineName.isValidRouterName(cmd.getVmName()) || VirtualMachineName.isValidConsoleProxyName(cmd.getVmName()) ) {
			/*For domr, the trick is that the actual vmname is vmName-domrId.
			 *Here, we need to build the relationship between vmName and its actual name at first*/
			getAllVms();
		}
		
		String realVmName = getRealVmName(cmd.getVmName());

        final State state = getVmState(realVmName);
        Integer vncPort = null;
        if (state == State.Running) {
            vncPort = getVncPort(realVmName);
            synchronized(_vms) {
                _vms.put(cmd.getVmName(), State.Running);
            }
        }
        
        return new CheckVirtualMachineAnswer(cmd, state, vncPort);
	}

	private Answer execute(PingTestCommand cmd) {
        String result = null;
        final String computingHostIp = cmd.getComputingHostIp(); //TODO, split the command into 2 types

        if (computingHostIp != null) {
            result = doPingTest(computingHostIp);
        } else {
            result = doPingTest(cmd.getRouterIp(), cmd.getPrivateIp());
        }

        if (result != null) {
            return new Answer(cmd, false, result);
        }
        return new Answer(cmd);
	}

    private String doPingTest( final String computingHostIp ) {
        final Script command = new Script(_pingTestPath, 10000, s_logger);
        command.add("-h", computingHostIp);
        return command.execute();
    }

    private String doPingTest( final String domRIp, final String vmIp ) {
        final Script command = new Script(_pingTestPath, 10000, s_logger);
        command.add("-i", domRIp);
        command.add("-p", vmIp);
        return command.execute();
    }
    
	private Answer execute(MigrateCommand cmd) {
		String vmName = cmd.getVmName();
    	String realVmName = getRealVmName(vmName);
    	
		State state = null;
		String result = null;
		synchronized(_vms) {
			state = _vms.get(vmName);
			_vms.put(vmName, State.Stopping);
		}
		
		Domain dm = null;
		Connect dconn = null;
		Domain destDomain = null;
		try {
			dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(realVmName.getBytes()));
			dconn = new Connect("qemu+tcp://" + cmd.getDestinationIp() + "/system");
			/*Hard code lm flags: VIR_MIGRATE_LIVE(1<<0) and VIR_MIGRATE_PERSIST_DEST(1<<3)*/
			destDomain = dm.migrate(dconn, (1<<0)|(1<<3), realVmName, null, 0);
		} catch (LibvirtException e) {
			s_logger.debug("Can't migrate domain: %s" + e.getMessage());
			result = e.getMessage();
		} finally {
			try {
				if (dm != null)
					dm.free();
				if (dconn != null)
					dconn.close();
				if (destDomain != null)
					destDomain.free();
			} catch (final LibvirtException e) {

			}
		}
		
		if (result != null) {
			synchronized(_vms) {
				_vms.put(vmName, state);
			}
		} else {
			cleanupVM(vmName, getVnetId(VirtualMachineName.getVnet(vmName)));
		}

		return new MigrateAnswer(cmd, result == null, result, null);
	}

	private Answer execute(PrepareForMigrationCommand cmd) {
		final String vmName = cmd.getVmName();
		String result = null;
		final String vnet = getVnetId(cmd.getVnet());
		if (vnet != null) {
			result = createVnet(vnet);
			if ( result != null) {
				return new PrepareForMigrationAnswer(cmd, false, result);
			}
		}

		synchronized(_vms) {
			_vms.put(vmName, State.Migrating);
		}

		return new PrepareForMigrationAnswer(cmd, result == null, result);
	}
	
    public String createVnet(String vnetId){
        final Script command  = new Script(_createvnetPath, _timeout, s_logger);
        command.add("-v", vnetId);

        final String result = command.execute();
        return result;
    }
    
	private Answer execute(CheckHealthCommand cmd) {
		return new CheckHealthAnswer(cmd, true);
	}
	
	private Answer execute(GetHostStatsCommand cmd) {
		final Script cpuScript = new Script("/bin/bash", s_logger);
		cpuScript.add("-c");
		cpuScript.add("idle=$(top -b -n 1|grep Cpu\\(s\\):|cut -d% -f4|cut -d, -f2);echo $idle");
		
		final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
		String result = cpuScript.execute(parser);
		if (result != null) {
			s_logger.info("Unable to get the host CPU state: " + result);
			return new Answer(cmd, false, result);
		}
		double cpuUtil = (100.0D - Double.parseDouble(parser.getLine()))/100.0D;
		
		long freeMem = 0;
		final Script memScript = new Script("/bin/bash", s_logger);
		memScript.add("-c");
		memScript.add("freeMem=$(free|grep cache:|awk '{print $4}');echo $freeMem");
		final OutputInterpreter.OneLineParser Memparser = new OutputInterpreter.OneLineParser();
		result = memScript.execute(Memparser);
		if (result != null) {
			s_logger.info("Unable to get the host Mem state: " + result);
			return new Answer(cmd, false, result);
		}
		freeMem = Long.parseLong(Memparser.getLine()) * 1000L;
		
		Script totalMem = new Script("/bin/bash", s_logger);
		totalMem.add("-c");
		totalMem.add("free|grep Mem:|awk '{print $2}'");
		final OutputInterpreter.OneLineParser totMemparser = new OutputInterpreter.OneLineParser();
		result = totalMem.execute(totMemparser);
		if (result != null) {
			s_logger.info("Unable to get the host Mem state: " + result);
			return new Answer(cmd, false, result);
		}
		long totMem = Long.parseLong(totMemparser.getLine()) * 1000L;
		return new GetHostStatsAnswer(cmd, cpuUtil, freeMem, totMem, 0, 0);
	}

	private Answer execute(RebootCommand cmd) {
		Long bytesReceived = null;
    	Long bytesSent = null;
    	/*final Map<String, VMopsVMMetrics> map = _collector.getMetricsMap();
    	final VMopsVMMetrics metrics = map.get(cmd.getVmName());
    	if (metrics != null) {
        	metrics.getNetworkData("eth2");
    		final Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
    		bytesReceived = mapRx.get("eth2");
    		final Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
    		bytesSent = mapTx.get("eth2");
    	}*/
    	synchronized(_vms) {
    		_vms.put(cmd.getVmName(), State.Starting);
    	}
    
    	try {
	    	final String result = rebootVM(cmd.getVmName());
	    	if (result == null) {
	    		/*_collector.removeVM(cmd.getVmName());
	    		_collector.addVM(cmd.getVmName());
	    		_collector.submitMetricsJobs();
				*/
	    		
	    		/*TODO: need to reset iptables rules*/
	    		String realVmName = getRealVmName(cmd.getVmName());
	    		return new RebootAnswer(cmd, null, bytesSent, bytesReceived, getVncPort(realVmName));
	    	} else {
	    		return new RebootAnswer(cmd, result);
	    	}
    	} finally {
    		synchronized(_vms) {
    			_vms.put(cmd.getVmName(), State.Running);
    		}
    	}
	}
	
	private Answer execute(GetVmStatsCommand cmd) {
		// TODO Auto-generated method stub
		return null;
	}

	protected Answer execute(StopCommand cmd) {
	    StopAnswer answer = null;
        final String vmName = cmd.getVmName();
        
        final Integer port = getVncPort(vmName);
        Long bytesReceived = new Long(0);
        Long bytesSent = new Long(0);
        
        //final Map<String, VMopsVMMetrics> map = _collector.getMetricsMap();
       // final VMopsVMMetrics metrics = map.get(cmd.getVmName());
        //if (metrics != null) {
        //	metrics.getNetworkData("eth2");
         //   final Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
         //   bytesReceived = mapRx.get("eth2");
         //   final Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
        //    bytesSent = mapTx.get("eth2");
        //}
        
        State state = null;
        synchronized(_vms) {
            state = _vms.get(vmName);
            _vms.put(vmName, State.Stopping);
        }
        try {
            String result = stopVM(vmName, defineOps.UNDEFINE_VM);
            
            answer =  new StopAnswer(cmd, null, port, bytesSent, bytesReceived);
            
            if (result != null) {
                answer = new StopAnswer(cmd, result, port, bytesSent, bytesReceived);
            }
            
            final String result2 = cleanupVnet(cmd.getVnet());
            if (result2 != null) {
                result = result2 + (result != null ? ("\n" + result) : "") ;
                answer = new StopAnswer(cmd, result, port, bytesSent, bytesReceived);
            }
            
            //_collector.removeVM(vmName);
            return answer;
        } finally {
            if (answer == null || !answer.getResult()) {
                synchronized(_vms) {
                    _vms.put(vmName, state);
                }
            }
        }
	}

	protected StartAnswer execute(StartCommand cmd) {
        final String vmName = cmd.getVmName();
        
        final String vnet = getVnetId(cmd.getGuestNetworkId());
        
        String result = null;
        
        State state = State.Stopped;
        synchronized(_vms) {
            _vms.put(vmName, State.Starting);
        }
        
        try {
        	result = createVnet(vnet);
        	if ( result != null) {
                return new StartAnswer(cmd, result );
        	}
            result = startVM(cmd, vnet,  cmd.getRamSize(), cmd.getNetworkRateMbps(), cmd.getNetworkRateMulticastMbps());
            if (result != null) {
                if (!_debug) {
                    cleanupVM(vmName,  vnet);
                }
                return new StartAnswer(cmd, result);
            }
            
           // _collector.addVM(cmd.getVmName());
           // _collector.submitMetricsJobs();
            
            state = State.Running;
            return new StartAnswer(cmd);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
	}

	protected synchronized String startVM(StartCommand cmd, String vnetId,
			int ramMB, int networkRateMbps,
			int networkRateMulticastMbps) {
		
		try {
			String memSize = Integer.toString(ramMB*1024);
			String vnetBridge= "vnbr" + vnetId;
			String uuid = UUID.nameUUIDFromBytes(cmd.getVmName().getBytes()).toString();
			
			// Get the root volume
			List<VolumeVO> rootVolumes = findVolumes(cmd.getVolumes(), VolumeType.ROOT, true);
	        
			if (rootVolumes.size() != 1) {
				throw new ExecutionException("Could not find UserVM root disk.", null);
			}
	            
			VolumeVO rootVolume = rootVolumes.get(0);
			
			// Get the ISO path
			String isoPath = "";
			if (cmd.getISOPath() != null) {
				isoPath = cmd.getISOPath();
				int index = isoPath.lastIndexOf("/template/");
				isoPath = _storageResource.getSecondaryStorageMountPoint(isoPath.substring(0, index)) + isoPath.substring(index);
			}
			// Build the VM domain XML
			String vmDomainXML = vmXMLformat.format(new Object[]{_hypervisorType,
																 cmd.getVmName(),
																 uuid,
																 memSize,
																 cmd.getCpu(),
																 cmd.getArch(),
																 _hypervisorPath,
																 rootVolume.getPath(),
																 isoPath,
																 cmd.getGuestMacAddress(),
																 vnetBridge,
																 cmd.getVncPassword()});
			
			s_logger.debug(vmDomainXML);
			
			// Start the domain
			String result = startDomain(cmd.getVmName(), vmDomainXML);
			
			if (result != null)
				return result;
			
			// Get the data volumes
			List<VolumeVO> dataVolumes = findVolumes(cmd.getVolumes(), VolumeType.DATADISK, false);

			// Attach each data volume to the VM
			for (VolumeVO dataVolume : dataVolumes) {
				result = attachOrDetachDisk(true, cmd.getVmName(), dataVolume.getPath());
				
				if (result != null)
					return result;
			}
			
			return null;
		} catch(Exception e) {
			s_logger.error("Unable to start VM: ", e);
			return "Unable to start VM due to: " + e.getMessage();
		}
	}
	
	protected synchronized String attachOrDetachISO(String vmName, String isoPath, boolean isAttach) {
		String xml = IsoXMLformat.format(new Object[]{""});;
		
		if (isoPath != null && isAttach) {
			int index = isoPath.lastIndexOf("/iso/");
			isoPath = _storageResource.getSecondaryStorageMountPoint(isoPath.substring(0, index)) + isoPath.substring(index);
			xml = IsoXMLformat.format(new Object[]{isoPath});
		}
		
		return attachOrDetachDevice(true, vmName, xml);
	}
	
	protected synchronized String attachOrDetachDisk(boolean attach, String vmName, String sourceFile) {
		String diskDev = null;
		SortedMap<String, String> diskMaps = null;
		try {
			Domain dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
			LibvirtDomainXMLParser parser = new LibvirtDomainXMLParser();
			String xml = dm.getXMLDesc(0);
			parser.parseDomainXML(xml);
			diskMaps = parser.getDiskMaps();
		}	catch (LibvirtException e) {

		}

		if (attach) {				
			diskDev = diskMaps.lastKey();
			/*Find the latest disk dev, and add 1 on it: e.g. if we already attach sdc to a vm, the next disk dev is sdd*/
			diskDev = diskDev.substring(0, diskDev.length() - 1) + (char)(diskDev.charAt(diskDev.length() -1) + 1);
		} else {
			Set<Map.Entry<String, String>> entrySet = diskMaps.entrySet();
			Iterator<Map.Entry<String, String>> itr = entrySet.iterator();
			while (itr.hasNext()) {
				Map.Entry<String, String> entry = itr.next();
				if (entry.getValue().equalsIgnoreCase(sourceFile)) {
					diskDev = entry.getKey();
					break;
				}
			}
		}

		if (diskDev == null) {
			s_logger.warn("Can't get disk dev");
			return "Can't get disk dev";
		}
		String xml = DiskXMLformat.format(new Object[]{sourceFile, diskDev});
		return attachOrDetachDevice(attach, vmName, xml);
	}
	
	private synchronized String attachOrDetachDevice(boolean attach, String vmName, String xml) {
		Domain dm = null;
		try {
			dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes((vmName.getBytes())));
			
			if (dm == null)
				throw new Exception("Could not find domain with name: " + vmName);
			
			if (attach) {
				s_logger.debug("Attaching device: " + xml);
				dm.attachDevice(xml);
			} else {
				s_logger.debug("Detaching device: " + xml);
				dm.detachDevice(xml);
			}
		} catch (Exception e) {
			if (attach)
				s_logger.warn("Failed to attach device to " + vmName + ": " + e.getMessage());
			else
				s_logger.warn("Failed to detach device from " + vmName + ": " + e.getMessage());
			return e.getMessage();
		} finally {
			if (dm != null) {
				try {
					dm.free();
				} catch (LibvirtException l) {
					
				}
			}
		}
		
		return null;
	}

	@Override
	public PingCommand getCurrentStatus(long id) {
        final HashMap<String, State> newStates = sync();
        return new PingComputingCommand(com.vmops.host.Host.Type.Routing, id, newStates);
	}

	@Override
	public Type getType() {
		return Type.Routing;
	}

	private Map<String, String> getVersionStrings() {
        final Script command = new Script(_versionstringpath, _timeout, s_logger);
        KeyValueInterpreter kvi = new KeyValueInterpreter();
        String result = command.execute(kvi);
        if (result == null) {
        	return kvi.getKeyValues();
        }else {
        	return new HashMap<String, String>(1);
        }
	}
	
	@Override
	public StartupCommand [] initialize() {
        Map<String, State> changes = null;

        synchronized(_vms) {
        	_vms.clear();
        	changes = sync();
        }
 
        final List<Object> info = getHostInfo();
      
        final StartupComputingCommand cmd = new StartupRoutingCommand((Integer)info.get(0), (Long)info.get(1), (Long)info.get(2), (Long)info.get(4), (String)info.get(3), HypervisorType.KVM, RouterPrivateIpStrategy.HostLocal, changes);
        fillNetworkInformation(cmd);
        cmd.getHostDetails().putAll(getVersionStrings());
        cmd.setPool(_pool);
        if (_storageResource != null) {
        	StartupCommand[] storageStartup = _storageResource.initialize();
        	StartupCommand [] result = new StartupCommand[storageStartup.length + 1];
        	result[0] = cmd;
        	for (int i=0; i < storageStartup.length; i++) {
        		result[i+1] = storageStartup[i];
        	}
        	return result;
        } else {
        	return new StartupCommand[]{cmd};
        }
	}
	
	protected HashMap<String, State> sync() {
		HashMap<String, State> newStates;
		HashMap<String, State> oldStates = null;

		final HashMap<String, State> changes = new HashMap<String, State>();

		synchronized(_vms) {
			newStates = getAllVms();
			if (newStates == null) {
				s_logger.debug("Unable to get the vm states so no state sync at this point.");
				return changes;
			}

			oldStates = new HashMap<String, State>(_vms.size());
			oldStates.putAll(_vms);

			for (final Map.Entry<String, State> entry : newStates.entrySet()) {
				final String vm = entry.getKey();

				State newState = entry.getValue();
				final State oldState = oldStates.remove(vm);

				if (newState == State.Stopped && oldState != State.Stopping && oldState != null && oldState != State.Stopped) {
					newState = getRealPowerState(vm);
				}

				if (s_logger.isTraceEnabled()) {
					s_logger.trace("VM " + vm + ": libvirt has state " + newState + " and we have state " + (oldState != null ? oldState.toString() : "null"));
				}

				if (vm.startsWith("migrating")) {
					s_logger.debug("Migration detected.  Skipping");
					continue;
				}
				if (oldState == null) {
					_vms.put(vm, newState);
					s_logger.debug("Detecting a new state but couldn't find a old state so adding it to the changes: " + vm);
					changes.put(vm, newState);
				} else if (oldState == State.Starting) {
					if (newState == State.Running) {
						_vms.put(vm, newState);
					} else if (newState == State.Stopped) {
						s_logger.debug("Ignoring vm " + vm + " because of a lag in starting the vm." );
					}
				} else if (oldState == State.Migrating) {
					if (newState == State.Running) {
						s_logger.debug("Detected that an migrating VM is now running: " + vm);
						_vms.put(vm, newState);
					}
				} else if (oldState == State.Stopping) {
					if (newState == State.Stopped) {
						_vms.put(vm, newState);
					} else if (newState == State.Running) {
						s_logger.debug("Ignoring vm " + vm + " because of a lag in stopping the vm. ");
					}
				} else if (oldState != newState) {
					_vms.put(vm, newState);
					if (newState == State.Stopped) {
						if (_vmsKilled.remove(vm)) {
							s_logger.debug("VM " + vm + " has been killed for storage. ");
							newState = State.Error;
						}
					}
					changes.put(vm, newState);
				}
			}

			for (final Map.Entry<String, State> entry : oldStates.entrySet()) {
				final String vm = entry.getKey();
				final State oldState = entry.getValue();

				if (s_logger.isTraceEnabled()) {
					s_logger.trace("VM " + vm + " is now missing from libvirt so reporting stopped");
				}

				if (oldState == State.Stopping) {
					s_logger.debug("Ignoring VM " + vm + " in transition state stopping.");
					_vms.remove(vm);
				} else if (oldState == State.Starting) {
					s_logger.debug("Ignoring VM " + vm + " in transition state starting.");
				} else if (oldState == State.Stopped) {
					_vms.remove(vm);
				} else if (oldState == State.Migrating) {
					s_logger.debug("Ignoring VM " + vm + " in migrating state.");
				} else {
					State state = State.Stopped;
					if (_vmsKilled.remove(entry.getKey())) {
						s_logger.debug("VM " + vm + " has been killed by storage monitor");
						state = State.Error;
					}
					changes.put(entry.getKey(),	state);
				}
			}
		}

		return changes;
	}
	
    protected synchronized Answer execute(ModifyVlanCommand cmd) {
    	/*
        boolean add = cmd.getAdd();
        String vlanId = cmd.getVlanId();
        String vlanGateway = cmd.getVlanGateway();
        
        try {
            String result = modifyVlan(add, vlanId, vlanGateway);
             
            if (result != null) {
                return new Answer(cmd, false, "Unable to execute Modify VLAN command.");
            } else {
                return new Answer(cmd);
            }
        } catch (Exception e) {
            s_logger.warn("Exception while modifying vlan.", e);
            return new Answer(cmd, false, e.getMessage());
        }*/
    	return new Answer(cmd);
    }
	
	protected State getRealPowerState(String vm) {
        int i = 0;
        s_logger.trace("Checking on the HALTED State");
        Domain dm = null;
        for (; i < 5; i++) {
            try {
                dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vm.getBytes()));
                DomainInfo.DomainState vps = dm.getInfo().state;
                if (vps != null && vps != DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF &&
                    vps != DomainInfo.DomainState.VIR_DOMAIN_NOSTATE) {
                    return convertToState(vps);
                }
            } catch (final LibvirtException e) {
                s_logger.trace(e.getMessage());
            } finally {
            	try {
            		if (dm != null)
            			dm.free();
            	} catch (final LibvirtException e) {
            		
            	}
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        return State.Stopped;
	}

	private String stripDomrIndex(String vmName) {
		int index = vmName.lastIndexOf(VirtualMachineName.SEPARATOR);
		if (index == -1) {
			return vmName;
		}
        String substr = vmName.substring(0, index);
        if (VirtualMachineName.isValidRouterName(substr) || VirtualMachineName.isValidConsoleProxyName(substr)) {
        	return substr;
        }
        return vmName;
	}
	
	private String getIndexedDomrName(String vmName, int index) {
		return vmName + VirtualMachineName.SEPARATOR + index;
	}
	
	private int getDomrIndex(String vmName) {
		int index = vmName.lastIndexOf(VirtualMachineName.SEPARATOR);
		if (index == -1) {
			return -1;
		}
        String substr = vmName.substring(0, index);
        if (VirtualMachineName.isValidRouterName(substr) || VirtualMachineName.isValidConsoleProxyName(substr)) {
        	return Integer.parseInt(vmName.substring(index+1));
        }
        return -1;
	}
	
	protected List<String> getAllVmNames() {
		ArrayList<String> la = new ArrayList<String>();
		try {
			final String names[] = _conn.listDefinedDomains();
			for (int i = 0; i < names.length; i++) {
				la.add(stripDomrIndex(names[i]));
			}
		} catch (final LibvirtException e) {
			s_logger.warn("Failed to list Defined domains", e);
		}

		int[] ids = null;
		try {
			ids = _conn.listDomains();
		} catch (final LibvirtException e) {
			s_logger.warn("Failed to list domains", e);
			return la;
		}
		
		Domain dm = null;
		for (int i = 0 ; i < ids.length; i++) {
			try {
				dm = _conn.domainLookupByID(ids[i]);
				la.add(stripDomrIndex(dm.getName()));
			} catch (final LibvirtException e) {
				s_logger.warn("Unable to get vms", e);
			} finally {
				try {
					if (dm != null)
						dm.free();
				} catch (final LibvirtException e) {

				}
			}
		}
		
		return la;
	}

	
	private HashMap<String, State> getAllVms() {
        final HashMap<String, State> vmStates = new HashMap<String, State>();

        String[] vms = null;
        int[] ids = null;
        try {
                ids = _conn.listDomains();
        } catch (final LibvirtException e) {
                s_logger.warn("Unable to listDomains", e);
                return null;
        }
        try {
                vms = _conn.listDefinedDomains();
        } catch (final LibvirtException e){
                s_logger.warn("Unable to listDomains", e);
                return null;
        }
        BitSet newDomrIndex = new BitSet(_domrPrivConfig.size());
        
        Domain dm = null;
        for (int i =0; i < ids.length; i++) {
            try {
                s_logger.debug("domid" + ids[i]);
                dm = _conn.domainLookupByID(ids[i]);

                DomainInfo.DomainState ps = dm.getInfo().state;

                final State state = convertToState(ps);

                s_logger.trace("VM " + dm.getName() + ": powerstate = " + ps + "; vm state=" + state.toString());
                String vmName = stripDomrIndex(dm.getName());
                vmStates.put(vmName, state);

                int index = getDomrIndex(dm.getName());
                if (index != -1) {
                	newDomrIndex.set(index);
                	_domrNames.put(vmName, dm.getName());
                }
            } catch (final LibvirtException e) {
                s_logger.warn("Unable to get vms", e);
            } finally {
            	try {
            		if (dm != null)
            			dm.free();
            	} catch (LibvirtException e) {
            		
            	}
            }
        }

        for (int i =0 ; i < vms.length; i++) {
            try {
            	
            	dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vms[i].getBytes()));
  
                 DomainInfo.DomainState ps = dm.getInfo().state;
                 final State state = convertToState(ps);
                 int index = getDomrIndex(dm.getName());
                 String vmName = stripDomrIndex(dm.getName());
                 if (index != -1) {
                 	newDomrIndex.set(index);
                	_domrNames.put(vmName, dm.getName());
                 }
                 s_logger.trace("VM " + vmName + ": powerstate = " + ps + "; vm state=" + state.toString());

                 vmStates.put(vmName, state);
            } catch (final LibvirtException e) {
                 s_logger.warn("Unable to get vms", e);
            } finally {
            	try {
            		if (dm != null)
            			dm.free();
            	} catch (LibvirtException e) {

            	}
            }
        }

        _domrIndex = newDomrIndex;
        return vmStates;
	}

	protected List<Object> getHostInfo() {
        final ArrayList<Object> info = new ArrayList<Object>();
        long speed = 0;
        long cpus = 0;
        long ram = 0;
        String capXML = null;
        String osType = null;
        try {
        	final NodeInfo hosts = _conn.nodeInfo();

        	cpus = hosts.cpus;
        	speed = hosts.mhz;
        	ram = hosts.memory * 1024L;
            LibvirtCapXMLParser parser = new LibvirtCapXMLParser();
            capXML = parser.parseCapabilitiesXML(_conn.getCapabilities());
            ArrayList<String> oss = parser.getGuestOsType();
            for(String s : oss)
            	/*Even host supports guest os type more than hvm, we only report hvm to management server*/
            	if (s.equalsIgnoreCase("hvm"))
            		osType = "hvm";
        } catch (LibvirtException e) {

        }

        info.add((int)cpus);
        info.add(speed);
        info.add(ram);
        info.add(osType);
        long dom0ram = Math.min(ram/10, 768*1024*1024L);//save a maximum of 10% of system ram or 768M
        dom0ram = Math.max(dom0ram, _dom0MinMem);
        info.add(dom0ram);
    	s_logger.info("cpus=" + cpus + ", speed=" + speed + ", ram=" + ram + ", dom0ram=" + dom0ram);

        return info;
    }
	
    protected void cleanupVM(final String vmName, final String vnet) {
        s_logger.debug("Trying to stop " + vmName);
        final String result = stopVM(vmName, defineOps.UNDEFINE_VM);
        s_logger.debug("Trying to cleanup the vnet: " + vnet);
        if (vnet != null)
        	cleanupVnet(vnet);
    }

	protected String rebootVM(String vmName) {
		String msg = stopVM(vmName, defineOps.DEFINE_VM);
	
		int domrIndex = -1;
		if (_domrNames.get(vmName) != null) {
			vmName = _domrNames.get(vmName);
			 domrIndex = getDomrIndex(vmName);
            
		}
		if (msg == null) {
			Domain dm = null;
			try {
				dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
				dm.create();
				if (domrIndex >= 0) {
					String result = _virtRouterResource.connect(_domrPrivConfig.get(domrIndex).ipAddress);
					if (result != null) {
						return result;
					}
				}

				return null;
			} catch (LibvirtException e) {
				s_logger.warn("Failed to create vm", e);
				msg = e.getMessage();
		    } finally {
		    	try {
		    		if (dm != null)
		    			dm.free();
		    	} catch (LibvirtException e) {

		    	}
		    }
		}

		return msg;
	}
	protected String stopVM(String vmName, defineOps df) {
		DomainInfo.DomainState state = null;
		Domain dm = null;
		
		int domrId = -1;
		String domrName = _domrNames.get(vmName);
		String actualVmName = vmName;
		if (domrName != null) {
			domrId = getDomrIndex(vmName);
			actualVmName = domrName;
		}
		
		s_logger.debug("Try to stop the vm at first");
		String ret = stopVM(actualVmName, false);
		if (ret == Script.ERR_TIMEOUT) {
			ret = stopVM(actualVmName, true);
		} else if (ret != null) {
			/*There is a race condition between libvirt and qemu:
			 * libvirt listens on qemu's monitor fd. If qemu is shutdown, while libvirt is reading on
			 * the fd, then libvirt will report an error. */
			/*Retry 3 times, to make sure we can get the vm's status*/
			for (int i = 0; i < 3; i++) {
				try {
					dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(actualVmName.getBytes()));
					state = dm.getInfo().state;
					break;
				} catch (LibvirtException e) {
					s_logger.debug("Failed to get vm status:" + e.getMessage());
				} finally {
					try {
						if (dm != null)
							dm.free();
					} catch (LibvirtException l) {

					}
				}
			}
			
			if (state == null) {
				s_logger.debug("Can't get vm's status, assume it's dead already");
				return null;
			}
			
			if (state != DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF) {
				s_logger.debug("Try to destroy the vm");
				ret = stopVM(actualVmName, true);
				if (ret != null) {
					return ret;
				}
			}
		}
		
		if (df == defineOps.UNDEFINE_VM) {
			try {
				dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(actualVmName.getBytes()));
				dm.undefine();
				if (domrId > 0)
					clearDomrIndex(domrId);
				_domrNames.remove(vmName);
			} catch (LibvirtException e) {

			} finally {
				try {
					if (dm != null)
						dm.free();
				} catch (LibvirtException l) {
					
				}
			}
		}
		return null;
	}
    protected String stopVM(String vmName, boolean force) {
    	Domain dm = null;
        try {
            dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
            if (force) {
				if (dm.getInfo().state != DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF) {
					dm.destroy();
				}
            } else {
            	if (dm.getInfo().state == DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF) {
            		return null;
            	}
    			dm.shutdown();
    			int retry = _stopTimeout/2000;
    			/*Wait for the domain gets into shutoff state*/
    			while ((dm.getInfo().state != DomainInfo.DomainState.VIR_DOMAIN_SHUTOFF) && (retry >= 0)) {
    				Thread.sleep(2000);
    				retry--;
    			}
    			if (retry < 0) {
    				s_logger.warn("Timed out waiting for domain " + vmName + " to shutdown gracefully");
    				return Script.ERR_TIMEOUT;
    			}
            }
        } catch (LibvirtException e) {
            s_logger.debug("Failed to stop VM :" + vmName + " :", e);
            return e.getMessage();
        } catch (InterruptedException ie) {
        	s_logger.debug("Interrupted sleep");
        } finally {
        	try {
        		if (dm != null)
        			dm.free();
        	} catch (LibvirtException e) {
        	}
        }
        
        return null;
    }

    public synchronized String cleanupVnet(final String vnetId) {
		// VNC proxy VMs do not have vnet
		if(vnetId == null || vnetId.isEmpty())
			return null;

		final List<String> names = getAllVmNames();
		
		if (!names.isEmpty()) {
			for (final String name : names) {
				if (VirtualMachineName.getVnet(name).equals(vnetId)) {
					return null;    // Can't remove the vnet yet.
				}
			}
		}
		
        final Script command = new Script(_vnetcleanupPath, _timeout, s_logger);
        command.add("-v", vnetId);
        return command.execute();
    }

    protected Integer getVncPort( String vmName) {
    	if (VirtualMachineName.isValidRouterName(vmName) || VirtualMachineName.isValidConsoleProxyName(vmName)) {
    		return null; // no vnc ports for domr
    	}
    	LibvirtDomainXMLParser parser = new LibvirtDomainXMLParser();
    	Domain dm = null;
    	try {
    		dm = _conn.domainLookupByUUID(UUID.nameUUIDFromBytes(vmName.getBytes()));
    		String xmlDesc = dm.getXMLDesc(0);
    		parser.parseDomainXML(xmlDesc);
    		return parser.getVncPort();
    	} catch (LibvirtException e) {
    		
    	} finally {
    		try {
    			if (dm != null)
    				dm.free();
    		} catch (LibvirtException l) {

    		}
    	}
    	return null;
    }
    
    protected int[] gatherVncPorts(final Collection<String> names) {
        final ArrayList<Integer> ports = new ArrayList<Integer>(names.size());
        for (final String name : names) {
            final Integer port = getVncPort(name);
            
            if (port != null) {
                ports.add(port);
            }
        }
        
        final int[] results = new int[ports.size()];
        int i = 0;
        for (final Integer port : ports) {
            results[i++] = port;
        }
        
        return results;
    }
    
    private boolean IsHVMEnabled() {
		LibvirtCapXMLParser parser = new LibvirtCapXMLParser();
		try {
			parser.parseCapabilitiesXML(_conn.getCapabilities());
			ArrayList<String> osTypes = parser.getGuestOsType();
			for (String o : osTypes) {
				if (o.equalsIgnoreCase("hvm"))
					return true;
			}
		} catch (LibvirtException e) {
			
		}
    	return false;
    }
    
    private String getHypervisorPath() {
    	if (_conn == null)
    		return null;
    	
    	LibvirtCapXMLParser parser = new LibvirtCapXMLParser();
    	try {
    		parser.parseCapabilitiesXML(_conn.getCapabilities());
    	} catch (LibvirtException e) {

    	}
    	return parser.getEmulator();
    }
    
    private String getRealVmName(String vmName) {
		String realVmName = vmName;
		String domrName = _domrNames.get(vmName);
		if (domrName != null) {
			realVmName = domrName;
		}
		return realVmName;
    }
}
