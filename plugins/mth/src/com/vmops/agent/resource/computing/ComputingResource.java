/**
 *  Copyright (c) 2008, VMOps Inc.
 *
 *  This code is Copyrighted and must not be reused, modified, or redistributed without the explicit consent of VMOps.
 */
package com.vmops.agent.resource.computing;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.CheckHealthAnswer;
import com.vmops.agent.api.CheckHealthCommand;
import com.vmops.agent.api.CheckStateAnswer;
import com.vmops.agent.api.CheckStateCommand;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetHostStatsAnswer;
import com.vmops.agent.api.GetHostStatsCommand;
import com.vmops.agent.api.GetVmStatsAnswer;
import com.vmops.agent.api.GetVmStatsCommand;
import com.vmops.agent.api.MigrateAnswer;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.MirrorAnswer;
import com.vmops.agent.api.MirrorCommand;
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
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupComputingCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.metrics.MetricsCollector;
import com.vmops.agent.metrics.VMopsVMMetrics;
import com.vmops.host.Host.HypervisorType;
import com.vmops.resource.ServerResource;
import com.vmops.resource.ServerResourceBase;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.VirtualMachineTemplate.BootloaderType;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.exception.ExecutionException;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachineName;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Crashdump;
import com.xensource.xenapi.Event;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.HostCpu;
import com.xensource.xenapi.HostMetrics;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.VM;
import com.xensource.xenapi.Types.BadServerResponse;
import com.xensource.xenapi.Types.EventsLost;
import com.xensource.xenapi.Types.SessionNotRegistered;
import com.xensource.xenapi.Types.VmPowerState;

/**
 * ComputingResource is the independence layer to execute requests for the
 * computing host.
 * 
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || mount.parent | parent directory to mount the files | Path | \images ||
 *    || xen.port | port where xen is listening on | integer | 9363 ||
 *    || xen.username | username | string | any ||
 *    || xen.password | password | string | any ||
 *    || ssh.sleep | Seconds to sleep when trying to connect to ssh | integer | 5 ||
 *    || ssh.retry | # of retries before declaring the vm down and report failure | integer | 24 ||
 *    || scripts.timeout | time to wait before terminating scripts in general | seconds | 60s ||
 *    || start.script.timeout | time to wait before terminating the start script | seconds | 180s ||
 *    || migrate.script.timeout | time to wait before terminating the migrate script | seconds | 5 * start.script.timeout ||
 *    || stop.script.timeout | time to wait before terminating the stop script | seconds | 60s ||
 *    || mount.script.timeout | time to wait before terminating the mount script | seconds | 120s ||
 *    || vnc.keyword | String to search for when looking for the vncport of a vm | String | (vncdisplay ||
 *    || debug.mode | put in debug mode which then doesn't clean up | Boolean | false ||
 *    || dom0.min.memory | memory used by dom0.  If null, get it from dom0 | long | null ||
 *    || storage.node.name | name of the storage node | String | vmops_has_node ||
 *    || mgmmt.node.name | name of the management node | String vmops_mgmt_server ||
 *  }
 **/
@Local(value={ServerResource.class})
public class ComputingResource extends ServerResourceBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(ComputingResource.class);
	private static final Logger s_monitorLogger = Logger.getLogger(StorageMonitorTask.class);
    private Connection _conn;
    private String _xmPath;
    private String _runvmPath;
    private String _mountvmPath;
    private String _mountRootdiskPath;
    private String _mountDatadiskPath;
    protected String _mountParent;
    private String _stopvmPath;
    private String _rebootvmPath;
    private String _vnetcleanupPath;
    private String _createVnetPath;
    private int _sleep;
    protected int _retry;
    protected MetricsCollector _collector;
    protected int _timeout;
    protected int _startTimeout;
    protected int _stopTimeout;
    protected int _migrateTimeout;
    protected int _mountTimeout;
    protected String _vncDisplay;
    protected boolean _debug;
    protected String _mapiscsiPath;
    protected String _mirrorPath;
    protected String _pingTestPath;
    protected String _iscsimonPath;
    protected String _iscsikillPath;
    protected String _iqn;
    protected String _iqnPath;

    protected InetAddress _multicastAddress;
	protected NetworkInterface _nic;
    
    protected MulticastSocket _sendSocket;
    protected MulticastSocket _recvSocket;
    protected int _disconnectSleepTime;
    protected boolean _disconnected = true;
    protected String _gateway;
    protected InetAddress _privateAddress;
    protected int _heartbeatInterval;

    protected com.xensource.xenapi.Host _host;
    protected HostMetrics _hostMetrics;
    protected Set<HostCpu> _hostCpus;
    protected Long _dom0MinMemory = null;
    protected String _bridgePath;
    protected HashMap<String, State> _vms = new HashMap<String, State>(20);
    
    protected String _storageNodeName;
    protected String _mgmtNodeName;
    protected ScheduledExecutorService _executor;
    protected List<String> _vmsKilled = new ArrayList<String>();
    
    protected static HashMap<Types.VmPowerState, State> s_statesTable;
    static {
        s_statesTable = new HashMap<Types.VmPowerState, State>();
        s_statesTable.put(Types.VmPowerState.HALTED, State.Stopped);
        s_statesTable.put(Types.VmPowerState.PAUSED, State.Running);
        s_statesTable.put(Types.VmPowerState.RUNNING, State.Running);
        s_statesTable.put(Types.VmPowerState.SUSPENDED, State.Running);
        s_statesTable.put(Types.VmPowerState.UNKNOWN, State.Unknown);
        s_statesTable.put(Types.VmPowerState.UNRECOGNIZED, State.Unknown);
    }

    @Override
    public Answer executeRequest(final Command cmd) {
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
                return execute((CheckStateCommand)cmd);
            } else if (cmd instanceof MirrorCommand) {
                return execute((MirrorCommand)cmd);
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
            } else {
                return Answer.createUnsupportedCommandAnswer(cmd);
            }
        } catch (final IllegalArgumentException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }
    
    protected synchronized ReadyAnswer execute(ReadyCommand cmd) {
    	s_logger.info("Bringing up the bridges");
    	Script script = new Script(_bridgePath, _timeout, s_logger);
    	script.add("up");
    	String result = script.execute();
    	if (result != null) {
    		s_logger.debug("Unable to bring up connections " + result);
    	}
    	
    	_disconnected = false;
    	
    	return new ReadyAnswer(cmd);
    }
    
    protected CheckVirtualMachineAnswer execute(final CheckVirtualMachineCommand cmd) {
        final String vmName = cmd.getVmName();
        final State state = getVmState(vmName);
        Integer vncPort = null;
        if (state == State.Running) {
            vncPort = getVncPort(vmName);
            synchronized(_vms) {
                _vms.put(vmName, State.Running);
            }
        }
        
        return new CheckVirtualMachineAnswer(cmd, state, vncPort);
    }
    
    protected PrepareForMigrationAnswer execute(final PrepareForMigrationCommand cmd) {
        final String vmName = cmd.getVmName();

        String result = null;
        final String local = _mountParent + vmName;
        if (cmd.isMirroredVols()) {
            result = mountMirroredImage(cmd.getStorageHosts(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
        } else {
            result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
        }
        
        final String vnet = cmd.getVnet();
        if (vnet != null) {
            final Script script = new Script(_createVnetPath, _timeout, s_logger);
            script.add("-v", vnet);
            result = script.execute();
            if (result != null) {
                if (cmd.isMirroredVols()) {
                    unmountMirroredImage(local, vmName);
                } else {
                    unmountImage(local, vmName);
                }
                return new PrepareForMigrationAnswer(cmd, false, result);
            }
        }
        
        synchronized(_vms) {
            _vms.put(vmName, State.Migrating);
        }
        
        return new PrepareForMigrationAnswer(cmd, result == null, result);
    }
    
    protected MigrateAnswer execute(final MigrateCommand cmd) {
        final String vmName = cmd.getVmName();
        State state = null;
        synchronized(_vms) {
            state = _vms.get(vmName);
            _vms.put(vmName, State.Stopping);
        }
        final Script script = new Script("xm", _migrateTimeout, s_logger);
        script.add("migrate", "-l", cmd.getVmName(), cmd.getDestinationIp());
        final String result = script.execute();
        if (result != null) {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        } else {
        	cleanupVM(vmName, _mountParent + vmName, VirtualMachineName.getVnet(vmName));
        }
        
        return new MigrateAnswer(cmd, result == null, result, null);
    }
    
    private MirrorAnswer execute(final MirrorCommand cmd) {
    	final String vmName = cmd.getVmName();
    	final String addHost = cmd.getAddHost();
    	final String removeHost = cmd.getRemoveHost();
    	String result;
    	if (removeHost != null) {
    		result = removeVolsFromMirror(removeHost, vmName, cmd.getRemoveVols());
    		if (result != null) {
    			return new MirrorAnswer(cmd, vmName, result);
    		}
    	}
    	if (addHost != null) {
   		 result = addVolsToMirror(addHost, vmName, cmd.getAddVols());
   		 if (result != null) {
   			 return new MirrorAnswer(cmd, vmName, result);
   		 }
    	}
    	return new MirrorAnswer(cmd, vmName);
	}
    
	private String  addVolsToMirror(final String addHost, final String vmName, final List<VolumeVO> addVols) {
		//Usage: mirror.sh: -m  -h <iscsi-target-host> -t <root disk target-name> -w <swap disk target name> -n <vm-name>
		//Usage: mirror.sh: -m  -h <iscsi-target-host> -d <data disk target-name> -c <disk num> -n <vm-name>

        final List<VolumeVO> rootVols = findVolumes(addVols, Volume.VolumeType.ROOT);
        String result = null;
		
        for (final VolumeVO rVol: rootVols) {
        	final Script command = new Script(_mirrorPath, _timeout, s_logger);
        	command.add("-m");
        	command.add("-h", rVol.getHostIp());
        	command.add("-n", vmName);
        	command.add("-t", rVol.getIscsiName());
        	final VolumeVO swapVol = findVolume(addVols, Volume.VolumeType.SWAP, rVol.getHostIp());
        	if (swapVol != null) {
        		command.add("-w", swapVol.getIscsiName());
        	}
        	result = command.execute();
        	if (result != null) {
        		break;
        	}
        }


		if (result != null) {
			return result;
		}
        final List<VolumeVO> diskVols = findVolumes(addVols, Volume.VolumeType.DATADISK);
        int i=1;
		for (final VolumeVO vol: diskVols) {
			final Script command = new Script(_mirrorPath, _timeout, s_logger);
			command.add("-m");
			command.add("-h", vol.getHostIp());
			command.add("-n", vmName);
			command.add("-d", vol.getIscsiName());
			command.add("-c", "1");
			i++;
			result = command.execute();
			if (result != null) {
				break;
			}
		}

        return result;
		
	}
	
	@Override
    protected String getDefaultScriptsDir() {
	    return null;
	}

	private String  removeVolsFromMirror(final String removeHost, final String vmName, final List<VolumeVO> removeVols) {
		//Usage: mirror.sh: -u  -h <iscsi-target-host> -T  -W  -n <vm-name>
		//Usage: mirror.sh: -u  -h <iscsi-target-host> -D -c <disknum> -n <vm-name>
        final VolumeVO rootVol = findVolume(removeVols, Volume.VolumeType.ROOT);
        final VolumeVO swapVol = findVolume(removeVols, Volume.VolumeType.SWAP);
        String result = null;
		if (rootVol != null) {
			final Script command = new Script(_mirrorPath, _timeout, s_logger);
			command.add("-u");
			command.add("-h", removeHost);
			command.add("-n", vmName);
			command.add("-T");
			if (swapVol != null)
				command.add("-W");
			result = command.execute();
		}
		 
        final List<VolumeVO> diskVols = findVolumes(removeVols, Volume.VolumeType.DATADISK);
        int i=1;
		for (final VolumeVO vol: diskVols) {
			final Script command = new Script(_mirrorPath, _timeout, s_logger);
			command.add("-u");
			command.add("-h", removeHost);
			command.add("-n", vmName);
			command.add("-D");
			command.add("-c", Integer.toString(i));
			i++;
			final String r = command.execute();
			if (result != null) {
				result=result.concat("***").concat(r);
			}
		}

        return result;
		
	}

	protected CheckStateAnswer execute(final CheckStateCommand cmd) {
        try {
            final State state = checkVmState(cmd.getVmName());
            return new CheckStateAnswer(cmd, state);
        } catch (final ExecutionException e) {
            return new CheckStateAnswer(cmd, createErrorDetail(e.getMessage(), e));
        }
    }

    protected CheckHealthAnswer execute(final CheckHealthCommand cmd) {
        return new CheckHealthAnswer(cmd, true);
    }

    protected State checkVmState(final String vmName) throws ExecutionException {
        try {
            final Set<VM> vms = VM.getByNameLabel(_conn, vmName);
            if (vms.size() == 1) {
                final VM vm = vms.iterator().next();
                final VmPowerState ps = vm.getPowerState(_conn);
                return convertToState(ps);
                // TODO: Check the mounts!
            } else if (vms.size() == 0) {
                return State.Stopped;
            } else {
                assert false : "Multiple vms returned for " + vmName;
                return State.Error;
            }
            
        } catch (final BadServerResponse e) {
            s_logger.warn("Error while getting info on VM: " + vmName, e);
            return State.Stopped;
        } catch (final XmlRpcException e) {
            s_logger.warn("Error while getting info on VM: " + vmName, e);
            throw new ExecutionException("Error while getting info on VM: " + vmName, e);
        }
    }
    
    protected Answer execute(final RebootCommand cmd) {
    	Long bytesReceived = null;
    	Long bytesSent = null;
    	final Map<String, VMopsVMMetrics> map = _collector.getMetricsMap();
    	final VMopsVMMetrics metrics = map.get(cmd.getVmName());
    	if (metrics != null) {
        	metrics.getNetworkData("eth2");
    		final Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
    		bytesReceived = mapRx.get("eth2");
    		final Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
    		bytesSent = mapTx.get("eth2");
    	}
    	synchronized(_vms) {
    		_vms.put(cmd.getVmName(), State.Starting);
    	}
    
    	try {
	    	final Script command = new Script(_rebootvmPath, _stopTimeout, s_logger);
	    	command.add("-l", cmd.getVmName());
	
	    	final String result = command.execute();
	    	if (result == null) {
	    		_collector.removeVM(cmd.getVmName());
	    		_collector.addVM(cmd.getVmName());
	    		_collector.submitMetricsJobs();
	
	    		return new RebootAnswer(cmd, null, bytesSent, bytesReceived);
	    	} else {
	    		return new RebootAnswer(cmd, result);
	    	}
    	} finally {
    		synchronized(_vms) {
    			_vms.put(cmd.getVmName(), State.Running);
    		}
    	}
    }
    
    protected synchronized StopAnswer execute(final StopCommand cmd) {
        StopAnswer answer = null;
        final String vmName = cmd.getVmName();
        
        final Integer port = getVncPort(vmName);
        Long bytesReceived = null;
        Long bytesSent = null;
        
        final Map<String, VMopsVMMetrics> map = _collector.getMetricsMap();
        final VMopsVMMetrics metrics = map.get(cmd.getVmName());
        if (metrics != null) {
        	metrics.getNetworkData("eth2");
            final Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
            bytesReceived = mapRx.get("eth2");
            final Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
            bytesSent = mapTx.get("eth2");
        }
        
        State state = null;
        synchronized(_vms) {
            state = _vms.get(vmName);
            _vms.put(vmName, State.Stopping);
        }
        try {
            String result = stopVM(vmName, false);
            if (result != null) {
                s_logger.info("Trying destroy on " + vmName);
                if (result == Script.ERR_TIMEOUT) {
                    result = stopVM(vmName, true);
                }
                
                s_logger.warn("Couldn't stop " + vmName);
                
                if (result != null) {
                    return new StopAnswer(cmd, result);
                }
            }
            
            answer =  new StopAnswer(cmd, null, port, bytesSent, bytesReceived);
            final String local = _mountParent + vmName;
            if (cmd.isMirroredVolumes()) {
            	result = unmountMirroredImage(local, vmName);
            } else {
            	result = unmountImage(local, vmName);
            }
            if (result != null) {
                answer = new StopAnswer(cmd, result, port, bytesSent, bytesReceived);
            }
            
            final String result2 = cleanupVnet(cmd.getVnet());
            if (result2 != null) {
                result = result2 + (result != null ? ("\n" + result) : "") ;
                answer = new StopAnswer(cmd, result, port, bytesSent, bytesReceived);
            }
            
            _collector.removeVM(vmName);
            return answer;
        } finally {
            if (answer == null || !answer.getResult()) {
                synchronized(_vms) {
                    _vms.put(vmName, state);
                }
            }
        }
    }
    
    private synchronized String unmountMirroredImage(final String local, final String vmName) {
    	Script command = new Script(_mountDatadiskPath, _mountTimeout, s_logger);
        command.add("-u");
        command.add("-M");
        command.add("-n", vmName);
        command.add("-c", "1");
        
    	final String result1 = command.execute();
    	
    	command = new Script(_mountRootdiskPath, _mountTimeout, s_logger);
        command.add("-u");
        command.add("-M");
        command.add("-n", vmName);
        command.add("-l", local);
        
        final String result2 = command.execute();
        
        if (result1 == null && result2 == null) {
            return null;
        }
        
        return (result1 != null ? result1 : "") + (result2 != null ? result2 : "");
		
	}

	protected synchronized String unmountImage(final String path, final String vmName) {
        
        final Script command = new Script(_mountvmPath, _mountTimeout, s_logger);
        command.add("-u");
        command.add("-l", path);
        command.add("-n", vmName);
        
        return command.execute();
    }
    
	protected synchronized String cleanupVnet(final String vnetId) {
		// VNC proxy VMs do not have vnet
		if(vnetId == null || vnetId.isEmpty())
			return null;
		
        final List<String> names = getAllVmNames();
        for (final String name : names) {
            if (VirtualMachineName.getVnet(name).equals(vnetId)) {
                return null;    // Can't remove the vnet yet.
            }
        }
        
        final Script command = new Script(_vnetcleanupPath, _timeout, s_logger);
        command.add("-v", vnetId);
        return command.execute();
    }
	
    protected synchronized StartAnswer execute(final StartCommand cmd) throws IllegalArgumentException {
        final String vmName = cmd.getVmName();
        
        final String vnet = cmd.getVnetId();
        
        String result = null;
        
        final String local = _mountParent + vmName;
        
        if (cmd.isMirroredVols() ) {
        	result = mountMirroredImage(cmd.getStorageHosts(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
        } else {
            result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
        }
        
        if (result != null) {
            unmountImage(local, vmName);
            return new StartAnswer(cmd, result);
        }
        
        State state = State.Stopped;
        synchronized(_vms) {
            _vms.put(vmName, State.Starting);
        }
        
        try {
            result = startVM(cmd, vnet, cmd.getGateway(), cmd.getRamSize(), local, cmd.getVncPort(), cmd.getNetworkRateMbps(), cmd.getNetworkRateMulticastMbps());
            if (result != null) {
                if (!_debug) {
                    cleanupVM(vmName, local, vnet);
                }
                return new StartAnswer(cmd, result);
            }
            
            _collector.addVM(cmd.getVmName());
            _collector.submitMetricsJobs();
            
            state = State.Running;
            return new StartAnswer(cmd, cmd.getVncPort());
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
    }

    protected Answer execute(final PingTestCommand cmd) {
        String result = null;
        final String computingHostIp = cmd.getComputingHostIp();

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
    
    protected State getVmState(final String vmName) {
        int retry = 3;
        while (retry-- > 0) {
            try {
                final Set<VM> vms = VM.getByNameLabel(_conn, vmName);
                for (final VM vm : vms) {
                    return convertToState(vm.getPowerState(_conn));
                }
            } catch (final BadServerResponse e) {
            } catch (final XmlRpcException e) {
            }
        }
        
        return State.Stopped;
    }

    protected List<String> getAllVmNamesSlowly() {
        try {
            final Set<VM> vms = VM.getAll(_conn);
            final ArrayList<String> names = new ArrayList<String>(vms.size());
            for (final VM vm : vms) {
                VM.Record record;
                try {
                    record = vm.getRecord(_conn);
                } catch (final BadServerResponse e) {
                    s_logger.warn("Unable to get a vm record.", e);
                    recordWarning(vm, "Unable to get a vm record.", e);
                    continue;
                } catch (final XmlRpcException e) {
                    s_logger.warn("Unable to get vm record.", e);
                    recordWarning(vm, "Unable to get vm record.", e);
                    continue;
                } catch (final Throwable th) {
                    s_logger.warn("Unable to get vm record", th);
                    recordWarning(vm, "Unable to get vm record.", th);
                    continue;
                }
                if (record.isControlDomain) {
                    continue;  // Skip DOM0
                }
                
                final Long domId = record.domid;
                if (domId != null && domId != -1) {
                    names.add(record.nameLabel);
                }
            }
            return names;
        } catch (final BadServerResponse e) {
            s_logger.warn("Unable to get vms", e);
        } catch (final XmlRpcException e) {
            s_logger.warn("Unable to get vms", e);
        }
        return new ArrayList<String>();
    }
    
    protected List<String> getAllVmNames() {
        try {
            final Map<VM, VM.Record> vms = VM.getAllRecords(_conn);
            final ArrayList<String> names = new ArrayList<String>(vms.size());
            for (final VM.Record record : vms.values()) {
                if (record.isControlDomain) {
                    continue;  // Skip DOM0
                }
                
                final Long domId = record.domid;
                if (domId != null && domId != -1) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("Found VM: " + record.nameLabel);
                    }
                    names.add(record.nameLabel);
                }
            }
            return names;
        } catch (final Throwable e) {
            s_logger.warn("Unable to get vms", e);
            return getAllVmNamesSlowly();
        }
    }
    
    protected Integer getVncPort(final String name) {
        final Script script = new Script("xm", _timeout, s_logger);
        script.add("list", "-l", name);
        
        final VncPortParser parser = new VncPortParser();
        final String result = script.execute(parser);
        if (result != null) {
            s_logger.info("Unable to get the vnc port: " + result);
            return null;
        }
        
        return parser.getPort();
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
    
    protected String getVmInfoForLogging(final VM vm) {
        final StringBuilder info = new StringBuilder();
        info.append("[id: ");
        try {
            final Long id = vm.getDomid(_conn);
            if (id != null) {
                info.append(id);
            }
        } catch (final BadServerResponse e) {
        } catch (final XmlRpcException e) {
        }
        
        info.append("; label: ");
        try {
            final String label = vm.getNameLabel(_conn);
            if (label != null) {
               info.append(label);
            }
        } catch (final BadServerResponse e) {
        } catch (final XmlRpcException e) {
        }
        info.append("]");
        return info.toString();
    }
    
    protected void cleanupVM(final String vmName, final String local, final String vnet) {
        s_logger.debug("Trying to stop " + vmName);
        final String result = stopVM(vmName, false);
        if (result != null && result == Script.ERR_TIMEOUT) {
            s_logger.debug("Trying to stop forcefully " + vmName);
            stopVM(vmName, true);
        }
        s_logger.debug("Trying to unmount the image: " + local);
        unmountImage(local, vmName);
        s_logger.debug("Trying to cleanup the vnet: " + vnet);
        cleanupVnet(vnet);
    }
    
    protected HashMap<String, State> getAllVmsSlowly() {
        final HashMap<String, State> vmStates = new HashMap<String, State>();
        
        Set<VM> vms;
        try {
            vms = VM.getAll(_conn);
        } catch (final BadServerResponse e) {
            s_logger.warn("Unable to get vms", e);
            return null;
        } catch (final XmlRpcException e) {
            s_logger.warn("Unable to get vms", e);
            return null;
        }
        
        
        for (final VM vm : vms) {
            VM.Record record = null;
            int i = 0;
            while (i++ < 3) {
                try {
                    record = vm.getRecord(_conn);
                    break;
                } catch (final BadServerResponse e) {
                	// There is a race condition within xen such that if a vm is deleted and we
                	// happen to ask for it, it throws this stupid response.  So if this happens,
                	// we take a nap and try again which then avoids the race condition because
                	// the vm's information is now cleaned up by xen.  The error is as follows
                	// com.xensource.xenapi.Types$BadServerResponse [HANDLE_INVALID, VM, 3dde93f9-c1df-55a7-2cde-55e1dce431ab]
                    s_logger.info("Unable to get a vm record due to " + e.getMessage() + ". We are retrying.  Count: " + i);
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException ex) {
                        
                    }
                } catch (final XmlRpcException e) {
                    s_logger.warn("Unable to get vm record.", e);
                    recordWarning(vm, "Unable to get vm record.", e);
                }
            }
            
            if (record == null) {
            	return null;
            }
            
            if (record.isControlDomain) {
                continue;  // Skip DOM0
            }
            
            final State state = convertToState(record.powerState);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("VM " + record.nameLabel + ": powerstate = " + record.powerState + "; vm state=" + state.toString());
            }
            vmStates.put(record.nameLabel, state);
        }
        
        return vmStates;
    }
    
    protected HashMap<String, State> getAllVms() {
        final HashMap<String, State> vmStates = new HashMap<String, State>();

        Map<VM, VM.Record> vms = null;
        try {
            vms = VM.getAllRecords(_conn);
        } catch (final Throwable e) {
            s_logger.warn("Unable to get vms", e);
            return getAllVmsSlowly();
        }
        
        
        for (Map.Entry<VM, VM.Record> entry : vms.entrySet()) {
        	VM.Record record = entry.getValue();
        	
            if (record.isControlDomain) {
                continue;  // Skip DOM0
            }

        	VmPowerState ps = record.powerState;
            final State state = convertToState(ps);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("VM " + record.nameLabel + ": powerstate = " + ps + "; vm state=" + state.toString());
            }
            vmStates.put(record.nameLabel, state);
        }
        
        return vmStates;
    }
    
    protected State getRealPowerState(String label) {
    	int i = 0;
    	s_logger.trace("Checking on the HALTED State");
    	for (; i < 20; i++) {
        	try {
        		Set<VM> vms = VM.getByNameLabel(_conn, label);
        		if (vms == null || vms.size() == 0) {
        			continue;
        		}
        		
        		VM vm = vms.iterator().next();
        		
        		VmPowerState vps = vm.getPowerState(_conn);
        		if (vps != null && vps != VmPowerState.HALTED && vps != VmPowerState.UNKNOWN && vps != VmPowerState.UNRECOGNIZED) {
        			return convertToState(vps);
        		}
			} catch (BadServerResponse e) {
				s_logger.trace(e.getMessage());
			} catch (XmlRpcException e) {
				s_logger.trace(e.getMessage());
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
    	}
    	return State.Stopped;
    }
    
    protected VM.Record getControlDomain() {
        Map<VM, VM.Record> vms = null;
        try {
            vms = VM.getAllRecords(_conn);
        } catch (final Throwable e) {
            s_logger.warn("Unable to get vms", e);
            throw new VmopsRuntimeException("Unable to get vms", e);
        }
        
        
        for (final VM.Record record : vms.values()) {
            if (record.isControlDomain) {
                return record;
            }
        }
        
        return null;
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
                    s_logger.trace("VM " + vm + ": xen has state " + newState + " and we have state " + (oldState != null ? oldState.toString() : "null"));
                }

                if (vm.startsWith("migrating")) {
                	s_logger.debug("Migrating from xen detected.  Skipping");
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
                    s_logger.trace("VM " + vm + " is now missing from xen so reporting stopped");
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
    
    protected void recordWarning(final VM vm, final String message, final Throwable e) {
        final StringBuilder msg = new StringBuilder();
        try {
            final Long domId = vm.getDomid(_conn);
            msg.append("[").append(domId != null ? domId : -1l).append("] ");
        } catch (final BadServerResponse e1) {
        } catch (final XmlRpcException e1) {
        }
        msg.append(message);
        recordWarning(msg.toString(), e);
    }
    
    protected State convertToState(Types.VmPowerState ps) {
        final State state = s_statesTable.get(ps);
        return state == null ? State.Unknown : state;
    }
    
    protected GetVmStatsAnswer execute(final GetVmStatsCommand cmd) {
        final Map<String, VMopsVMMetrics> map = _collector.getMetricsMap();
        final VMopsVMMetrics metrics = map.get(cmd.getVmName());
        if (metrics == null) {
            return new GetVmStatsAnswer(cmd, new HashMap<String, long[]>(), new HashMap<String, long[]>());
        }
        
        final Map<String, long[]> networkStats = new HashMap<String, long[]>();
        final Map<String, Long> netRxStats = metrics.getNetRxTotalBytes();
        final Set<Map.Entry<String, Long>> set = netRxStats.entrySet();
        for (final Map.Entry<String, Long> entry : set) {
            final long[] values = new long[2];
            values[0] = entry.getValue();
            values[1] = metrics.getNetTxTotalBytes(entry.getKey());
            networkStats.put(entry.getKey(), values);
        }
        
        final Map<String, long[]> diskStats = new HashMap<String, long[]>();
        final Map<String, Long> diskRxStats = metrics.getDiskReadTotalBytes();
        final Set<Map.Entry<String, Long>> read = diskRxStats.entrySet();
        for (final Map.Entry<String, Long> entry : read) {
            final long[] values = new long[2];
            values[0] = entry.getValue();
            values[1] = metrics.getDiskWriteTotalBytes(entry.getKey());
            diskStats.put(entry.getKey(), values);
        }
        
        return new GetVmStatsAnswer(cmd, networkStats, diskStats);
    }
    
    protected String connect(final String ipAddress) {
    	return connect(ipAddress, 3922);
    }
    
    protected String connect(final String ipAddress, final int port) {
        for (int i = 0; i <= _retry; i++) {
            SocketChannel sch = null;
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Trying to connect to " + ipAddress);
                }
                sch = SocketChannel.open();
                sch.configureBlocking(true);
                
                final InetSocketAddress addr = new InetSocketAddress(ipAddress, port);
                sch.connect(addr);
                return null;
            } catch (final IOException e) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Could not connect to " + ipAddress);
                }
            } finally {
                if (sch != null) {
                    try {
                        sch.close();
                    } catch (final IOException e) {}
                }
            }
            try {
                Thread.sleep(_sleep);
            } catch (final InterruptedException e) {
            }
        }
        
        s_logger.debug("Unable to logon to " + ipAddress);
        
        return "Unable to connect";
    }
    
    @Override
    public PingCommand getCurrentStatus(final long id) {
        final HashMap<String, State> newStates = sync();
        return new PingComputingCommand(com.vmops.host.Host.Type.Computing, id, newStates);
    }

    @Override
    public synchronized StartupCommand [] initialize() {
        checkMounts();
        Map<String, State> changes = null;
        int[] ports = null;
        synchronized(_vms) {
            _vms.clear();
            changes = sync();
            ports = gatherVncPorts(_vms.keySet());
        }
        final List<Object> info = getHostInfo();
        final StartupComputingCommand cmd = new StartupComputingCommand((Integer)info.get(0), (Long)info.get(1), (Long)info.get(2), (Long)info.get(4), (String)info.get(3), ports, changes);
        fillNetworkInformation(cmd);
        cmd.setIqn(getIQN());
        cmd.setHypervisorType(HypervisorType.Xen);
        return new StartupCommand[] {cmd};
    }
    
    protected void checkMounts() {
        Script cmd = new Script(_mapiscsiPath, _timeout, s_logger);
        final List<String> vms = getAllVmNames();
        
        final OrphanedIscsiMountParser parser = new OrphanedIscsiMountParser(vms);
        String result = cmd.execute(parser);
        if (result != null) {
            s_logger.warn("Unable to check iscsi mounts due to " + result);
        }
        
        List<String> mounts = parser.getOrphanedMounts();
        for (final String mount : mounts) {
            unmountIscsi(mount);
        }
        
        mounts = parser.getOrphanedVms();
        for (final String mount : mounts) {
            unmountImage("a", mount);
        }
        
        cmd = new Script("mount");
        final OrphanedNfsMountParser p = new OrphanedNfsMountParser(vms, _mountParent);
        result = cmd.execute(p);
        if (result != null) {
            s_logger.warn("Unable to check nfs mounts due to " + result);
        }
        
        mounts = p.getOrphanedMounts();
        for (final String mount : mounts) {
            unmountNfs(mount);
        }
    }
    
    protected void unmountNfs(final String mount) {
        final Script cmd = new Script("umount");
        cmd.add(mount);
        final String result = cmd.execute();
        if (result != null) {
            s_logger.warn("Unable to unmount: " + mount);
        } else {
            s_logger.warn("Unmounted: " + mount);
            
        }
    }
    
    protected void unmountIscsi(final String mount) {
        Script cmd = new Script("iscsiadm", _timeout, s_logger);
        cmd.add("-m", "node");
        cmd.add("-T", mount, "-u");
        
        String result = cmd.execute();
        if (result != null) {
            s_logger.warn("Unable to unmount iscsi: " + mount);
        } else {
            s_logger.warn("Unmounted: " + mount);
        }
        
        cmd = new Script("iscsiadm", _timeout, s_logger);
        cmd.add("-m", "node");
        cmd.add("-T", mount, "-o", "delete");
        
        result = cmd.execute();
        if (result != null) {
            s_logger.warn("Unable to delete iscsi: " + mount);
        } else {
            s_logger.warn("Deleted: " + mount);
        }
    }
    
    protected List<Object> getHostInfo() {
        final ArrayList<Object> info = new ArrayList<Object>();
        long speed = 0;
        long cpus = 0;
        long ram = 0;
        
        long dom0Ram = 0;
        final StringBuilder caps = new StringBuilder();
        try {
            final Map<Host, Host.Record> hosts = Host.getAllRecords(_conn);
            for (final Host.Record host : hosts.values()) {
                for (final String cap : host.capabilities) {
                    if (cap.length() > 0) {
                        caps.append(cap).append(" , ");
                    }
                }
                if (caps.length() > 0) {
                    caps.delete(caps.length() - 3, caps.length());
                }
            }
            final Map<HostCpu, HostCpu.Record> map = HostCpu.getAllRecords(_conn);
            cpus = map.size();
            final Collection<HostCpu.Record> records = map.values();
            for (final HostCpu.Record record : records) {
                speed = record.speed;
            }
            
            long free = 0;
            final Map<HostMetrics, HostMetrics.Record> hms = HostMetrics.getAllRecords(_conn);
            for (final Map.Entry<HostMetrics, HostMetrics.Record> metric : hms.entrySet()) {
                final HostMetrics.Record record = metric.getValue();
                ram = record.memoryTotal;
                free = record.memoryFree;
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Total Ram: " + ram + " Free Ram: " + free);
            }
            
            if (_dom0MinMemory == null) {
                final Map<VM, VM.Record> vms = VM.getAllRecords(_conn);
                for (final Map.Entry<VM, VM.Record> vm : vms.entrySet()) {
                    final VM.Record record = vm.getValue();
                    if (record.powerState == Types.VmPowerState.RUNNING &&
                        !record.isControlDomain &&
                        !record.nameLabel.equals(_storageNodeName) &&
                        !record.nameLabel.equals(_mgmtNodeName)) {
                        free += record.memoryDynamicMin;
                        if (s_logger.isDebugEnabled()) {
                            s_logger.debug("Adding memory " + record.memoryDynamicMin + " used by vm: " + record.nameLabel);
                        }
                    }
                }
                dom0Ram = ram - free;
            } else {
                dom0Ram = _dom0MinMemory;
            }
        } catch (final Types.BadServerResponse e) {
            throw new VmopsRuntimeException(e.getMessage(), e);
        } catch (final XmlRpcException e) {
            throw new VmopsRuntimeException("XML RPC Exception", e);
        }
        
        info.add((int)cpus);
        info.add(speed);
        info.add(ram);
        info.add(caps.toString());
        info.add(dom0Ram);
        
        return info;
    }

    @Override
    public synchronized void disconnected() {
    	if (_disconnected) {
    		s_logger.info("Still disconnected so leaving");
    		return;
    	}
    	
    	s_logger.info("Checking connection to gateway");
    	
    	if (_gateway != null && doPingTest(_gateway) == null) {
    		s_logger.info("gateway is pingable so I'm not bringing down the bridges");
    		return;
    	}
    	
    	
    	TrafficListener listener = new TrafficListener();
    	_executor.schedule(listener, 0, TimeUnit.MICROSECONDS);
    	
    	try {
    		Thread.sleep(_disconnectSleepTime);
    	} catch (InterruptedException e) {
    	} finally {
    		listener.stop();
    	}
    	
	    if (listener.received()) {
	    	s_logger.info("Still getting traffic on so I'm not bringing down the bridges");
    		return;
    	}
	    
	    s_logger.info("Shutting down the bridges because there's no traffic");
    	Script script = new Script(_bridgePath, _timeout, s_logger);
    	script.add("down");
    	String result = script.execute();
    	if (result != null) {
    		s_logger.warn("Unable to shutdown the bridges " + result);
    	}
    	
    	_disconnected = true;
    }
    
    @Override
    public boolean start() {
    	//_eventThread.start();
    	return true;
    }
    
    @Override
    public boolean stop() {
        try {
            _conn.dispose();
        } catch (final BadServerResponse e) {
        } catch (final XmlRpcException e) {
        }
        return true;
    }
    
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }

        String value = (String)params.get("ssh.sleep");
        _sleep = NumbersUtil.parseInt(value, 5) * 1000;
        
        final String instance = (String)params.get("instance");
        
        value = (String)params.get("ssh.retry");
        _retry = NumbersUtil.parseInt(value, 24);
        
        value = (String)params.get("debug.mode");
        _debug = Boolean.parseBoolean(value);
        
        value = (String)params.get("dom0.min.memory");
        if (value != null) {
            _dom0MinMemory = NumbersUtil.parseLong(value, 512000000);
        } else {
            _dom0MinMemory = null;
        }
        
        _vncDisplay = (String)params.get("vnc.keyword");
        if (_vncDisplay == null) {
            _vncDisplay = "(vncdisplay";
        }
        
        String port = (String)params.get("xen.port");
        if (port == null) {
            port = "9363";
        }
        
        String username = (String)params.get("xen.username");
        if (username == null) {
            username = "any";
        }
        
        String password = (String)params.get("xen.password");
        if (password == null) {
            password = "any";
        }
        
        value = (String)params.get("scripts.timeout");
        _timeout = NumbersUtil.parseInt(value, 120) * 1000;
        
        value = (String)params.get("start.script.timeout");
        _startTimeout = NumbersUtil.parseInt(value, 360) * 1000;
        
        value = (String)params.get("migrate.script.timeout");
        _migrateTimeout = NumbersUtil.parseInt(value, 0) * 1000;
        if (_migrateTimeout == 0) {
        	_migrateTimeout = _startTimeout * 5;
        }
        
        value = (String)params.get("stop.script.timeout");
        _stopTimeout = NumbersUtil.parseInt(value, 120) * 1000;
        
        value = (String)params.get("mount.script.timeout");
        _mountTimeout = NumbersUtil.parseInt(value, 240) * 1000;
        
        try {
            _conn = new Connection("http://localhost:" + port, username, password);
            _host = com.xensource.xenapi.Host.getAll(_conn).iterator().next();
            _hostMetrics = _host.getMetrics(_conn);
            _hostCpus = _host.getHostCPUs(_conn);
//			Event.register(_conn, new TreeSet<String>());
        } catch(final MalformedURLException e) {
            throw new ConfigurationException("Problem with the Xml to xen: " +  e.getMessage());
        } catch(final XmlRpcException e) {
            throw new ConfigurationException("Problem with connecting to RPC host: " + e.getMessage());
        } catch(final Types.BadServerResponse e) {
            throw new ConfigurationException("Server response is bad: " + e.getMessage());
        } catch(final Types.SessionAuthenticationFailed e) {
            throw new ConfigurationException("authentication failed: " + e.getMessage());
        }
        
        if (new File("/usr/sbin/xm").exists()) {
            _xmPath = "/usr/sbin/xm";
        } else if (new File("/usr/bin/xm").exists()) {
            _xmPath = "/usr/bin/xm";
        } else {
            s_logger.warn("Unable to find xm");
            _xmPath = null;
        }
        
        String storageScriptsDir = (String)params.get("storage.scripts.dir");
        if (storageScriptsDir == null) {
            storageScriptsDir = "scripts/vm/storage/iscsi/comstar/filebacked";
        }
        String networkScriptsDir = (String)params.get("network.scripts.dir");
        if (networkScriptsDir == null) {
            networkScriptsDir = "scripts/vm/network/vnet";
        }
        String xenScriptsDir = (String)params.get("xen.scripts.dir");
        if (xenScriptsDir == null) {
            xenScriptsDir = "scripts/vm/hypervisor/xen";
        }
        
        s_logger.info("Looking for storage scripts in " + storageScriptsDir);
        s_logger.info("Looking for network scripts in " + networkScriptsDir);
        s_logger.info("looking for xen scripts in " + xenScriptsDir);
        
        _mountvmPath = Script.findScript(storageScriptsDir, "mountvm.sh");
        if (_mountvmPath == null) {
            throw new ConfigurationException("Unable to find mountvm.sh");
        }
        s_logger.info("mountvm.sh found in " + _mountvmPath);
        
        _mountRootdiskPath = Script.findScript(storageScriptsDir, "mountrootdisk.sh");
        if (_mountRootdiskPath == null) {
            throw new ConfigurationException("Unable to find mountrootdisk.sh");
        }
        s_logger.info("mountrootdisk.sh found in " + _mountRootdiskPath);
        
        _createVnetPath = Script.findScript(networkScriptsDir, "createvnet.sh");
        if (_createVnetPath == null) {
            throw new ConfigurationException("Unable to find createvnet.sh");
        }

        _mountDatadiskPath = Script.findScript(storageScriptsDir, "mountdatadisk.sh");
        if (_mountDatadiskPath == null) {
            throw new ConfigurationException("Unable to find mountdatadisk.sh");
        }
        s_logger.info("mountdatadisk.sh found in " + _mountDatadiskPath);
        
        _vnetcleanupPath = Script.findScript(networkScriptsDir, "vnetcleanup.sh");
        if (_vnetcleanupPath == null) {
            throw new ConfigurationException("Unable to find vnetcleanup.sh");
        }
        s_logger.info("vnetcleanup.sh found in " + _vnetcleanupPath);
        
        _stopvmPath = Script.findScript(xenScriptsDir, "stopvm.sh");
        if (_stopvmPath == null) {
            throw new ConfigurationException("Unable to find stopvm.sh");
        }
        
        _rebootvmPath = Script.findScript(xenScriptsDir, "rebootvm.sh");
        if (_rebootvmPath == null) {
            throw new ConfigurationException("Unable to find rebootvm.sh");
        }
        
        _iscsimonPath = Script.findScript(storageScriptsDir, "iscsimon.sh");
        if (_iscsimonPath == null) {
            throw new ConfigurationException("Unable to find iscsimon.sh");
        }
        
        _iscsikillPath = Script.findScript(storageScriptsDir, "iscsikill.sh");
        if (_iscsikillPath == null) {
        	throw new ConfigurationException("Unable to find iscsikill.sh");
        }
        
        _mapiscsiPath = Script.findScript(storageScriptsDir, "mapiscsi.sh");
        if (_mapiscsiPath == null) {
            throw new ConfigurationException("Unable to find the mapiscsi.sh");
        }
        s_logger.info("mapiscsi.sh found in " + _mapiscsiPath);
        
        _bridgePath = Script.findScript(networkScriptsDir, "bridge.sh");
        if (_bridgePath == null) {
        	throw new ConfigurationException("Unable to find bridge.sh");
        }
        
        _mirrorPath = Script.findScript(storageScriptsDir, "mirror.sh");
        if (_mirrorPath == null) {
            throw new ConfigurationException("Unable to find the mirror.sh");
        }
        s_logger.info("mirror.sh found in " + _mirrorPath);

        _pingTestPath = Script.findScript(xenScriptsDir, "pingtest.sh");
        if (_pingTestPath == null) {
            throw new ConfigurationException("Unable to find the pingtest.sh");
        }

        _iqnPath = Script.findScript(storageScriptsDir, "get_iqn.sh");
        if (_iqnPath == null) {
            throw new ConfigurationException("Unable to find get_iqn.sh");
        }
        s_logger.info("get_iqn.sh found in " + _iqnPath);

        _runvmPath = Script.findScript(xenScriptsDir, "runvm.sh");
        if (_runvmPath == null) {
            throw new ConfigurationException("Unable to find the runvm.sh");
        }
        s_logger.info("runvm.sh found in " + _runvmPath);
        
        _mountParent = (String)params.get("mount.parent");
        if (_mountParent == null) {
            _mountParent = "/images";
        }

        if (_mountParent.charAt(_mountParent.length() - 1) != File.separatorChar) {
            _mountParent += File.separatorChar;
        }
        if (instance != null) {
            _mountParent += "instance" + File.separator;
        }

        _storageNodeName = (String)params.get("storage.node.name");
        if (_storageNodeName == null) {
            _storageNodeName = "vmops-has-node";
        }
        
        _mgmtNodeName = (String)params.get("mgmt.node.name");
        if (_mgmtNodeName == null) {
            _mgmtNodeName = "vmops_mgmt_server";
        }
        
        final File file = new File(_mountParent);
        if (!file.exists()) {
            s_logger.info("Creating mount directory: " + _mountParent);
            if (!file.mkdir()) {
                s_logger.error("Unable to create mount directory: " + file.getAbsolutePath());
                throw new ConfigurationException("Unable to create mount directory: " + file.getAbsolutePath());
            }
        }
        
        value = (String)params.get("storage.monitor.interval");
        int time = NumbersUtil.parseInt(value, 60);
        
        s_logger.info("Parent directory for image mounts is: " + _mountParent);

        setupMetricsCollector();
        
        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("resource-monitor"));
        _executor.scheduleAtFixedRate(new StorageMonitorTask(), time, time, TimeUnit.SECONDS);
        //_eventThread = new Thread(this, "EventListener");
        
        String multicast = (String)params.get("heart.beat.multicast");
        if (multicast == null) {
        	multicast = "224.10.17.1";
        }
        
        value = (String)params.get("heart.beat.port");
        int vmopsPort = NumbersUtil.parseInt(value, 1898);
        
        value = (String)params.get("heart.beat.interval");
        _heartbeatInterval = NumbersUtil.parseInt(value, 1);
        
        value = (String)params.get("disconnect.sleep.time");
        _disconnectSleepTime = NumbersUtil.parseInt(value, _heartbeatInterval * 5) * 1000;
        
        value = (String)params.get("heart.beat.ttl");
        int hops = NumbersUtil.parseInt(value, 1);
        
        s_logger.info("ttl is " + hops);
        
        _gateway = (String)params.get("private.gateway");
        
		try {
			_multicastAddress = InetAddress.getByName(multicast);
			String[] info = NetUtils.getNicParams(_privateNic.getName());
			if (info == null) {
				throw new ConfigurationException("Unable to figure out network");
			}
			_privateAddress = InetAddress.getByName(info[0]);
			_nic = NetworkInterface.getByInetAddress(_privateAddress);
		} catch (SocketException e) {
			throw new ConfigurationException("Unable to get the inet address");
		} catch (UnknownHostException e) {
			throw new ConfigurationException("Unable to get inet address from " + multicast);
		}
		try {
			
			_sendSocket = new MulticastSocket(vmopsPort);
			_sendSocket.setTimeToLive(hops);
			_sendSocket.setNetworkInterface(_nic);
			_sendSocket.setLoopbackMode(true);
			_sendSocket.joinGroup(_multicastAddress);
			
			_recvSocket = new MulticastSocket(vmopsPort);
		} catch (IOException e) {
			throw new ConfigurationException("Unable to get a socket for " + multicast + " due to " + e.getMessage());
		}
		
        _executor.scheduleAtFixedRate(new VnetSender(_multicastAddress, vmopsPort), _heartbeatInterval, _heartbeatInterval, TimeUnit.SECONDS);
        
        return true;
    }
    
    protected void setupMetricsCollector() {
        _collector = new MetricsCollector();
        try {
            final Set<VM> vms = VM.getAll(_conn);
            for (final VM vm : vms) {
                VM.Record record;
                try {
                    record = vm.getRecord(_conn);
                } catch (final BadServerResponse e) {
                    s_logger.warn("Unable to get a vm record.", e);
                    recordWarning(vm, "Unable to get a vm record.", e);
                    continue;
                } catch (final XmlRpcException e) {
                    s_logger.warn("Unable to get vm record.", e);
                    recordWarning(vm, "Unable to get vm record.", e);
                    continue;
                }
                if (record.isControlDomain) {
                    continue;  // Skip DOM0
                }
               
                final Long domId = record.domid;
                if (domId != null && domId != -1) {
                    _collector.addVM(record.nameLabel);
                }
            }
        } catch (final BadServerResponse e) {
            s_logger.warn("Unable to get vms", e);
        } catch (final XmlRpcException e) {
            s_logger.warn("Unable to get vms", e);
        }
        
        _collector.submitMetricsJobs();
    }
    
    protected Answer execute(final GetHostStatsCommand cmd) {
        try {
            final Map<HostCpu, HostCpu.Record> map = HostCpu.getAllRecords(_conn);
            double util = 0.0d;
            for (final HostCpu.Record cpu : map.values()) {
                util += cpu.utilisation;
            }
            util = util / map.size();
            
            final long memory = _hostMetrics.getMemoryFree(_conn);
            
            return new GetHostStatsAnswer(cmd, util, memory);
        } catch (final BadServerResponse e) {
            return new Answer(cmd, false, e.getMessage());
        } catch (final XmlRpcException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }
    
    @Override
    public com.vmops.host.Host.Type getType() {
        return com.vmops.host.Host.Type.Computing;
    }
    
    protected String getIQN() {
        final Script command = new Script(_iqnPath, 500, s_logger);
        final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        final String result = command.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException("Unable to get iqn: " + result);
        }

        return parser.getLine();
    }
    
	protected static VolumeVO findVolume(final List<VolumeVO> volumes, final Volume.VolumeType vType) {
		if (volumes == null) return null;

		for (final VolumeVO v: volumes) {
			if (v.getVolumeType() == vType)
				return v;
		}

		return null;
	}
	
	protected static List<VolumeVO>  findVolumes(final List<VolumeVO> volumes, final Volume.VolumeType vType) {
		if (volumes == null) return null;
		final List<VolumeVO> result = new ArrayList<VolumeVO>();
		for (final VolumeVO v: volumes) {
			if (v.getVolumeType() == vType)
				result.add(v);
		}

		return result;
	}
	
	protected static VolumeVO  findVolume(final List<VolumeVO> volumes, final Volume.VolumeType vType, final String storageHost) {
		if (volumes == null) return null;
		for (final VolumeVO v: volumes) {
			if ((v.getVolumeType() == vType) && (v.getHostIp().equalsIgnoreCase(storageHost)))
				return v;
		}
		return null;
	}
	
	protected static boolean mirroredVolumes(final List<VolumeVO> vols, final Volume.VolumeType vType) {
		final List<VolumeVO> volumes = findVolumes(vols, vType);
		return volumes.size() > 1;
	}
	 
	public synchronized String mountImage(final String host, final String dest, final String vmName, final List<VolumeVO> volumes, final BootloaderType bootloader) {
        final Script command = new Script(_mountvmPath, _mountTimeout, s_logger);
        command.add("-h", host);
        command.add("-l", dest);
        command.add("-n", vmName);
        command.add("-b", bootloader.toString());

        command.add("-t");

        
        final VolumeVO root = findVolume(volumes, Volume.VolumeType.ROOT);
        if (root == null) {
        	return null;
        }
    	command.add(root.getIscsiName());
        command.add("-r", root.getFolder());
        
        final VolumeVO swap = findVolume(volumes, Volume.VolumeType.SWAP);
        if (swap !=null && swap.getIscsiName() != null) {
        	command.add("-w", swap.getIscsiName());
        }
        
        final VolumeVO datadsk = findVolume(volumes, Volume.VolumeType.DATADISK);
        if (datadsk !=null && datadsk.getIscsiName() != null) {
        	command.add("-1", datadsk.getIscsiName());
        }
        
        return command.execute();
    }
	
	public synchronized String mountImage(final String storageHosts[], final String dest, final String vmName, final List<VolumeVO> volumes, final boolean mirroredVols, final BootloaderType booter) {
		if (!mirroredVols) {
			return mountImage(storageHosts[0], dest, vmName, volumes, booter);
		} else {
			return mountMirroredImage(storageHosts, dest, vmName, volumes, booter);
		}
	}
	
	public synchronized void cleanupStorage(List<String> iscsis) {
		synchronized (_vms) {
			for (String iscsi : iscsis) {
				String[] tokens = iscsi.split(" ");
				if (tokens.length != 3) {
					s_logger.warn("Skipping " + iscsi);
					continue;
				}
				
				Script script = new Script(_iscsikillPath, _stopTimeout, s_logger);
				script.add("-r", tokens[0]);
				script.add("-t", tokens[1]);
				script.add("-p", tokens[2].split(":")[0]);
				
				VmKillParser parser = new VmKillParser(_vmsKilled);
				script.execute(parser);
			}
		}
	}
    
    protected String mountMirroredImage(final String hosts[], final String dest, final String vmName, final List<VolumeVO> volumes, final BootloaderType booter) {
		final List<VolumeVO> rootDisks = findVolumes(volumes, VolumeType.ROOT);
		final String storIp0 = hosts[0];
		final String storIp1 = hosts[1];
		//mountrootdisk.sh -m -h $STORAGE0 -t $iqn0 -l $src -n $vmname -r $dest -M -H $STORAGE1 -T $iqn1
        final Script command = new Script(_mountRootdiskPath, _mountTimeout, s_logger);
        command.add("-m");
        command.add("-M");
        command.add("-h", storIp0);
        command.add("-H", storIp1);
        command.add("-l", dest);
        command.add("-r", rootDisks.get(0).getFolder());
        command.add("-n", vmName);
        command.add("-t", rootDisks.get(0).getIscsiName());
        command.add("-T", rootDisks.get(1).getIscsiName());
        command.add("-b", booter.toString());

		final List<VolumeVO> swapDisks = findVolumes(volumes, VolumeType.SWAP);
		if (swapDisks.size() == 2) {
	        command.add("-w", swapDisks.get(0).getIscsiName());
	        command.add("-W", swapDisks.get(1).getIscsiName());
		}

		final String result = command.execute();
		if (result == null){
			final List<VolumeVO> dataDisks = findVolumes(volumes, VolumeType.DATADISK);
			if (dataDisks.size() == 2) {
				final Script mountdata = new Script(_mountDatadiskPath, _mountTimeout, s_logger);
				mountdata.add("-m");
				mountdata.add("-M");
				mountdata.add("-h", storIp0);
				mountdata.add("-H", storIp1);
				mountdata.add("-n", vmName);
				mountdata.add("-c", "1");
				mountdata.add("-d", dataDisks.get(0).getIscsiName());
				mountdata.add("-D", dataDisks.get(1).getIscsiName());
				return mountdata.execute();

			} else if (dataDisks.size() == 0){
				return result;
			}
		}
		
		return result;
	}

	protected String stopVM(final String vmName, final boolean force) {
        final Script command = new Script(_stopvmPath, _stopTimeout, s_logger);
        if (force) {
            command.add("-f");
        }
        command.add("-l", vmName);
        
        return command.execute();
    }
    
    protected synchronized String startVM(final StartCommand cmd, final String vnetId, final String gateway, final int ramSize, final String localPath, final int port, int rate, int mcrate) {
        final Script command = new Script(_runvmPath, _startTimeout, s_logger);
        command.add("-v", vnetId);
        command.add("-i", cmd.getGuestIpAddress());
        command.add("-m", Integer.toString(ramSize));
        command.add("-g", gateway);
        command.add("-a", cmd.getGuestMacAddress());
        command.add("-l", cmd.getVmName());
        command.add("-c", Integer.toString(port));
        command.add("-n", Integer.toString(cmd.getCpu()));
        command.add("-u", Integer.toString(cmd.getUtilization()));
        command.add("-w", cmd.getVncPassword());
        command.add("-b", cmd.getBootloader().toString());
        command.add("-r", Integer.toString(rate));
        command.add("-R", Integer.toString(mcrate));
        if (cmd.isMirroredVols()) {
        	command.add("-M");
        }
        command.add(localPath);
        
        return command.execute();
    }
    
    protected class StorageMonitorTask implements Runnable {
    	StorageMonitorParser _parser = new StorageMonitorParser();
    	
        public void run() {
        	try {
	            s_logger.debug("Scanning for storage problems");
	            Script script = new Script(_iscsimonPath, _timeout * 3, s_monitorLogger);
	            String result = script.execute(_parser);
	            if (_parser.getIscsi().size() > 0) {
	            	cleanupStorage(_parser.getIscsi());
	            }
        	} catch (Exception e) {
        		s_logger.warn("Storage Monitor caught this ", e);
        	}
        }
    }
    
    protected static class VmKillParser extends OutputInterpreter {
    	List<String> _vmsKilled;
    	final static private String token = "Shutting down vm: ";
    	final static private int tokenLength = token.length();
    	
    	public VmKillParser(List<String> vmsKilled) {
    		_vmsKilled = vmsKilled;
    	}
    	
    	@Override
    	public String interpret(final BufferedReader reader) throws IOException {
    		String line;
    		while ((line = reader.readLine()) != null) {
    			if (line.startsWith(token)) {
    				String vm = line.substring(tokenLength);
    				_vmsKilled.add(vm);
    				s_logger.debug("Adding to kill list: " + vm);
    			}
    		}
    		return null;
    	}
    }
    
    protected class StorageMonitorParser extends OutputInterpreter {
    	ArrayList<String> _iscsis;
    	
    	public List<String> getIscsi() {
    		return _iscsis;
    	}
    	
    	@Override
    	public String interpret(final BufferedReader reader) throws IOException {
    		_iscsis = new ArrayList<String>();
    		String line;
    		while ((line = reader.readLine()) != null) {
    			if (line.equals("OK")) {
    				return null;
    			} else if (line.startsWith("DOWN")) {
    				_iscsis.add(line.substring(5));
    			}
    		}
    		return null;
    	}
    }
    
    protected class VncPortParser extends OutputInterpreter {
        Integer port;
        
        public Integer getPort() {
            return port;
        }
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            port = null;
            String line = null;
            boolean lookForPort = false;
            while ((line = reader.readLine()) != null) {
                if (!lookForPort) {
                    final int index = line.indexOf(_vncDisplay);
                    if (index != -1) {
                        lookForPort = true;
                    }
                    continue;
                }
                
                final int index = line.indexOf("location");
                if (index == -1) {
                    if (line.contains("type vnc")) {
                        return "Unable to find the port";
                    } else {
                        continue;
                    }
                }
                
                final int end = line.lastIndexOf(")");
                final int begin = line.lastIndexOf(":");
                if (end == -1 || begin == -1) {
                    return "Unable to find the port in: " + line + " begin = " + begin + " end = " + end + " index = " + index;
                }

                try {
                    port = Integer.parseInt(line.substring(begin + 1, end)) - 5900;
                } catch (final NumberFormatException e) {
                    return "Unable to parse port from " + line;
                }
                
                return null;
            }
            
            return "Unable to find vnc port";
        }
    }
    
    protected class VnetSender implements Runnable {
    	final Logger _vnetLogger = Logger.getLogger(VnetSender.class);
    	byte[] _bytes;
    	InetAddress _group;
    	int _port;
    	
    	public VnetSender(InetAddress group, int port) {
    		_bytes = new byte[] {0x56, 0x4D, 0x4F, 0x50, 0x53};
    		_group = group;
    		_port = port;
    	}
    	
    	@Override
    	public void run() {
			try {
				DatagramPacket packet = new DatagramPacket(_bytes, _bytes.length, _group, _port);
				_sendSocket.send(packet);
				if (_vnetLogger.isTraceEnabled()) {
					_vnetLogger.trace("Sent multicast traffic to " + packet.getSocketAddress().toString() + " Length = " + packet.getLength());
				}
				return;
			} catch (IOException e) {
				_vnetLogger.warn("Unable to send", e);
			}
    	}
    }
    
    protected class TrafficListener implements Runnable {
    	boolean _stop = false;
    	boolean _received = false;
    	
    	public void stop() {
    		_stop = true;
    	}
    	
    	public boolean received() {
    		return _received;
    	}
    	
    	@Override
		public void run() {
    		s_logger.info("Checking for multicast traffic ");
    		try {
				_recvSocket.joinGroup(_multicastAddress);
				_recvSocket.setNetworkInterface(_nic);
    		} catch (IOException e) {
    			s_logger.warn("Unable to join the multicast address");
    			return;
    		}
    		try {
	    		DatagramPacket packet = new DatagramPacket(new byte[256], 250);
				while (!_stop) {
					try {
						_recvSocket.receive(packet);
						if (s_logger.isTraceEnabled()) {
							s_logger.trace("packet address: " + packet.getAddress() + " sent address: " + _privateAddress);
						}
						if (!packet.getAddress().equals(_privateAddress)) {
							if (s_logger.isTraceEnabled()) {
								s_logger.trace("Received multicast traffic from " + packet.getSocketAddress().toString() + " Length = " + packet.getLength());
							}
							_received = true;
							return;
						}
					} catch (IOException e) {
					}
				}
    		} finally {
    			try {
    				_recvSocket.leaveGroup(_multicastAddress);
    			} catch(IOException e) {
    				s_logger.warn("Unable to leave multicast group");
    			}
    		}
    	}
    }
    
    protected class OrphanedIscsiMountParser extends OutputInterpreter {
        List<String> mounts;
        List<String> vms;
        List<String> orphanedVms;
        
        public OrphanedIscsiMountParser(final List<String> vms) {
            this.vms = vms;
        }
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            mounts = new ArrayList<String>();
            orphanedVms = new ArrayList<String>();
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                final String[] tokens = line.split(" ");
                if (tokens.length == 1) {
                    mounts.add(line);
                } else {
                    final String vm = tokens[1].substring(0, tokens[1].lastIndexOf("-"));
                    if (!vms.contains(vm)) {
                        orphanedVms.add(vm);
                    }
                }
            }
            
            return null;
        }
        
        public List<String> getOrphanedVms() {
            return orphanedVms;
        }
        
        public List<String> getOrphanedMounts() {
            return mounts;
        }
    }
    
    protected class OrphanedNfsMountParser extends OutputInterpreter {
        List<String> mounts;
        List<String> vms;
        String mountParent;
        
        public OrphanedNfsMountParser(final List<String> vms, final String mountParent) {
            this.vms = vms;
            this.mountParent = mountParent;
        }
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            mounts = new ArrayList<String>();
            
            String line = null;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(" on ");
                if (index == -1) {
                    continue;
                }
                
                final int end = line.indexOf(" ", index + 4);
                if (end == -1) {
                    continue;
                }
                
                final String mount = line.substring(index + 4, end);
                
                if (!mount.startsWith(mountParent)) {
                    continue;
                }
                
                index = mount.lastIndexOf(File.separator);
                if (index == -1) {
                    continue;
                }
                
                final String vmName = mount.substring(index + 1);
                
                if (!vms.contains(vmName)) {
                    mounts.add(mount);
                }
            }
            return null;
        }
        
        public List<String> getOrphanedMounts() {
            return mounts;
        }
    }
    
    protected class EventListener extends Thread {
    	boolean events;
    	
    	public EventListener() {
    		super("EventListener");
        	try {
				Event.register(_conn, new TreeSet<String>());
				events = true;
			} catch (BadServerResponse e) {
				s_logger.warn("No Events due to ", e);
				events = false;
			} catch (XmlRpcException e) {
				s_logger.warn("No Events due to ", e);
				events = false;
			}
    	}
    	
        @Override
        public void run() {
        	while (true) {
        		try {
    				Set<Event.Record> events = Event.next(_conn);
    				for (Event.Record event : events) {
    					if (event.clazz.equals("VM") && event.operation.equals("MOD")) {
    						final VM vm = VM.getByUuid(_conn, event.objUuid);
    						final VM.Record vmr = vm.getRecord(_conn);
    					}
    				}
    			} catch (BadServerResponse e) {
    				s_logger.warn("Problem getting events", e);
    				events = false;
    			} catch (SessionNotRegistered e) {
    				s_logger.warn("Problem getting events", e);
    				events = false;
    			} catch (EventsLost e) {
    				s_logger.warn("Problem getting events", e);
    				events = false;
    			} catch (XmlRpcException e) {
    				s_logger.warn("Problem getting events", e);
    				events = false;
    			} catch (Exception e) {
    				s_logger.warn("Problem getting events", e);
    				events = false;
    			}
        	}
        }
    }
    
    public static void main(final String[] args) {
        try {
            final Connection conn = new Connection("http://localhost:9363", "any", "any");
            final Map<VM, VM.Record> vms = VM.getAllRecords(conn);
            
            long ramUsed = 0;
            for (final Map.Entry<VM, VM.Record> vm : vms.entrySet()) {
                System.out.println(vm.getValue().toString());
                ramUsed += vm.getValue().memoryDynamicMin;
            }
            System.out.println("Memory Used is " + ramUsed);
            
            Event.register(conn, new TreeSet<String>());
            
            while (true) {
                try {
                    final Set<com.xensource.xenapi.Event.Record> records = Event.next(conn);
                    for (final com.xensource.xenapi.Event.Record record : records) {
                        System.out.println("Event Record: " + record.toString());
                        if (record.operation != null) {
                        	System.out.println("Event Operation: " + record.operation.toString());
                        }
                        if (record.ref != null) {
                        	System.out.println("Reference: " + record.ref);
                        }
                        if (record.snapshot != null) {
                        	System.out.println("Snapshot: " + record.snapshot);
                        }
                        if (record.clazz.equals("VM")) {
                            final VM vm = VM.getByUuid(conn, record.objUuid);
                            System.out.println("VM: " + vm.toString());
                            for (int i = 0; i < 5; i++) {
                                System.out.println("VM name label: " + vm.getNameLabel(conn));
                                System.out.println("Vpower state: " + vm.getPowerState(conn).toString());
                                VM.Record rec = vm.getRecord(conn);
                                Map<String, String> data = rec.xenstoreData;
                                if (rec.xenstoreData != null) {
                                    System.out.println("XenStore");
	                                for (Map.Entry<String, String> d : data.entrySet()) {
	                                	System.out.println(d.getKey() + ":" + d.getValue());
	                                }
                                }
                                if (rec.allowedOperations !=  null) {
                                    System.out.println("Operations");
                                	for (Types.VmOperations op : rec.allowedOperations) {
                                		System.out.println(op.toString());
                                	}
                                }
                                
                                if (rec.crashDumps != null) {
                                	System.out.println("CrashDumps");
                                	for (Crashdump cd : rec.crashDumps) {
                                		System.out.println(cd.toString());
                                	}
                                }
                                System.out.println("Sleep: " + i);
                                try {
                                    Thread.sleep(1000);
                                } catch (final InterruptedException e) {
                                }
                            }
                            final VM.Record rec = vm.getRecord(conn);
                            System.out.println("VM Record: " + rec.toString());
                        }
                    }
                } catch (final SessionNotRegistered e) {
                    e.printStackTrace();
                } catch (final EventsLost e) {
                    e.printStackTrace();
                } catch(final Types.BadServerResponse e) {
                    e.printStackTrace();
                }
            }
        } catch(final MalformedURLException e) {
            e.printStackTrace();
        } catch(final XmlRpcException e) {
            e.printStackTrace();
        } catch(final Types.BadServerResponse e) {
            e.printStackTrace();
        } catch(final Types.SessionAuthenticationFailed e) {
            e.printStackTrace();
        }
    }
}
