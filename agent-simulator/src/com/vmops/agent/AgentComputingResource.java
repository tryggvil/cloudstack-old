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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.AttachDiskCommand;
import com.vmops.agent.api.AttachIsoCommand;
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
import com.vmops.agent.api.GetVncPortCommand;
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
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.agent.api.storage.DownloadCommand;
import com.vmops.agent.api.storage.DownloadProgressCommand;
import com.vmops.agent.api.storage.PrimaryStorageDownloadCommand;
import com.vmops.host.Host.Type;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.utils.script.Script;
import com.vmops.vm.State;

public class AgentComputingResource extends AgentStorageResource {
    private static final Logger s_logger = Logger.getLogger(AgentComputingResource.class);
	
    protected MetricsCollector _collector;
    
    protected HashMap<String, State> _vms = new HashMap<String, State>(20);
    protected String _mountParent;
    
    public AgentComputingResource(AgentContainer agent) {
    	super(agent);
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
                return execute((CheckStateCommand)cmd);
            } else if (cmd instanceof MirrorCommand) {
                return execute((MirrorCommand)cmd);
            } else if (cmd instanceof CheckHealthCommand) {
                return execute((CheckHealthCommand)cmd);
            } else if (cmd instanceof PingTestCommand) {
                return execute((PingTestCommand)cmd);
            } else if (cmd instanceof PrepareForMigrationCommand) {
                return execute((PrepareForMigrationCommand)cmd);
            } else if (cmd instanceof MigrateCommand) {
                return execute((MigrateCommand)cmd);
            } else if (cmd instanceof CheckVirtualMachineCommand) {
                return execute((CheckVirtualMachineCommand)cmd);
            } else if (cmd instanceof ReadyCommand) {
            	return execute((ReadyCommand)cmd);
            } else if(cmd instanceof AttachDiskCommand) {
            	return execute((AttachDiskCommand)cmd);
            } else if(cmd instanceof AttachIsoCommand) {
            	return execute((AttachIsoCommand)cmd);
            } else if(cmd instanceof PrimaryStorageDownloadCommand) {
            	return execute((PrimaryStorageDownloadCommand)cmd);
            } else if (cmd instanceof DownloadProgressCommand) {
                return execute((DownloadProgressCommand)cmd);
            } else if (cmd instanceof DownloadCommand) {
                return execute((DownloadCommand)cmd);
            } else if(cmd instanceof GetVncPortCommand) {
                return execute((GetVncPortCommand)cmd);
        	} else {
                return super.executeRequest(cmd);
            }
        } catch (IllegalArgumentException e) {
            return new Answer(cmd, false, e.getMessage());
        }
	}

	@Override
	public PingCommand getCurrentStatus(long id) {
        HashMap<String, State> newStates = sync();
        return new PingComputingCommand(com.vmops.host.Host.Type.Computing, id, newStates);
	}

	@Override
	public Type getType() {
        return com.vmops.host.Host.Type.Computing;
	}

	@Override
	public StartupCommand[] initialize() {
        Map<String, State> changes = null;
        int[] ports = null;
        synchronized(_vms) {
            _vms.clear();
            changes = sync();
        }
        List<Object> info = getHostInfo();
        StartupComputingCommand cmd = new StartupComputingCommand((Integer)info.get(0), (Long)info.get(1), (Long)info.get(2), (Long)info.get(4), (String)info.get(3), changes);
        return new StartupCommand[] {cmd};
	}

    protected synchronized Answer execute(StartCommand cmd) throws IllegalArgumentException {
    	VmMgr vmMgr = getAgent().getVmMgr();
    	
        String vmName = cmd.getVmName();
        String vnet = cmd.getGuestNetworkId();
        
        String result = null;
        String local = _mountParent + vmName;
        
        if (cmd.isMirroredVols() ) {
        	result = mountMirroredImage(cmd.getStorageHosts(), local, vmName, cmd.getVolumes());
        } else {
            result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes());
        }
        
        if (result != null) {
            unmountImage(local, vmName);
            return new Answer(cmd, false, result);
        }
        
        State state = State.Stopped;
        synchronized(_vms) {
            _vms.put(vmName, State.Starting);
        }
        
        try {
            result = vmMgr.startVM(cmd.getVmName(), vnet, cmd.getGateway(), "10.0.0.1",
            	cmd.getGuestIpAddress(), cmd.getGuestMacAddress(), "255.0.0.0",
            	null, null, null, cmd.getCpu(), cmd.getUtilization(),
            	cmd.getRamSize(), local, cmd.getVncPassword());
            if (result != null) {
            	vmMgr.cleanupVM(vmName, local, vnet);
                return new StartAnswer(cmd, result);
            }
            
            _collector.addVM(cmd.getVmName());
            _collector.submitMetricsJobs();
            
            state = State.Running;
            return new StartAnswer(cmd);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
    }
    
    protected synchronized StopAnswer execute(StopCommand cmd) {
    	VmMgr vmMgr = getAgent().getVmMgr();
    	
        StopAnswer answer = null;
        String vmName = cmd.getVmName();
        
        Integer port = vmMgr.getVncPort(vmName);
        Long bytesReceived = null;
        Long bytesSent = null;
        
        Map<String, MockVmMetrics> map = _collector.getMetricsMap();
        MockVmMetrics metrics = map.get(cmd.getVmName());
        if (metrics != null) {
            Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
            bytesReceived = mapRx.get("eth2");
            Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
            bytesSent = mapTx.get("eth2");
        }
        
        State state = null;
        synchronized(_vms) {
            state = _vms.get(vmName);
            _vms.put(vmName, State.Stopping);
        }
        try {
            String result = vmMgr.stopVM(vmName, false);
            if (result != null) {
                s_logger.info("Trying destroy on " + vmName);
                if (result == Script.ERR_TIMEOUT) {
                    result = vmMgr.stopVM(vmName, true);
                }
                
                s_logger.warn("Couldn't stop " + vmName);
                
                if (result != null) {
                    return new StopAnswer(cmd, result);
                }
            }
            
            answer =  new StopAnswer(cmd, null, port, bytesSent, bytesReceived);
            String local = _mountParent + vmName;
            if (cmd.isMirroredVolumes()) {
            	result = unmountMirroredImage(local, vmName);
            } else {
            	result = unmountImage(local, vmName);
            }
            if (result != null) {
                answer = new StopAnswer(cmd, result, port, bytesSent, bytesReceived);
            }
            
            String result2 = vmMgr.cleanupVnet(cmd.getVnet());
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

    protected Answer execute(RebootCommand cmd) {
    	VmMgr vmMgr = getAgent().getVmMgr();
        vmMgr.rebootVM(cmd.getVmName());
        return new RebootAnswer(cmd, "success", 0L, 0L);
    }
    
    /*
    protected GetVmStatsAnswer execute(GetVmStatsCommand cmd) {
        Map<String, MockVmMetrics> map = _collector.getMetricsMap();
        MockVmMetrics metrics = map.get(cmd.getVmName());
        if (metrics == null) {
            return new GetVmStatsAnswer(cmd, new HashMap<String, long[]>(), new HashMap<String, long[]>());
        }
        
        Map<String, long[]> networkStats = new HashMap<String, long[]>();
        Map<String, Long> netRxStats = metrics.getNetRxTotalBytes();
        Set<Map.Entry<String, Long>> set = netRxStats.entrySet();
        for (Map.Entry<String, Long> entry : set) {
            long[] values = new long[2];
            values[0] = entry.getValue();
            values[1] = metrics.getNetTxTotalBytes(entry.getKey());
            networkStats.put(entry.getKey(), values);
        }
        
        Map<String, long[]> diskStats = new HashMap<String, long[]>();
        Map<String, Long> diskRxStats = metrics.getDiskReadTotalBytes();
        Set<Map.Entry<String, Long>> read = diskRxStats.entrySet();
        for (Map.Entry<String, Long> entry : read) {
            long[] values = new long[2];
            values[0] = entry.getValue();
            values[1] = metrics.getDiskWriteTotalBytes(entry.getKey());
            diskStats.put(entry.getKey(), values);
        }
        
        return new GetVmStatsAnswer(cmd, networkStats, diskStats);
    }
    */
    
    protected GetVmStatsAnswer execute(GetVmStatsCommand cmd) {
    	return new GetVmStatsAnswer(cmd, 0, 0, 0, 0, 0);
    }
    
    protected Answer execute(GetHostStatsCommand cmd) {
    	VmMgr vmMgr = getAgent().getVmMgr();
        return new GetHostStatsAnswer(cmd, vmMgr.getHostCpuUtilization(), vmMgr.getHostFreeMemory(), 0, 0, 0);
    }
    
	@Override
    protected CheckStateAnswer execute(CheckStateCommand cmd) {
        State state = getAgent().getVmMgr().checkVmState(cmd.getVmName());
        return new CheckStateAnswer(cmd, state);
    }
	
    private MirrorAnswer execute(MirrorCommand cmd) {
    	String vmName = cmd.getVmName();
    	String addHost = cmd.getAddHost();
    	String removeHost = cmd.getRemoveHost();
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
    
	private String  removeVolsFromMirror(String removeHost, String vmName, List<VolumeVO> removeVols) {
		return null;
	}
	
	private String  addVolsToMirror(String addHost, String vmName, List<VolumeVO> addVols) {
		return null;
	}

    @Override
    protected CheckHealthAnswer execute(CheckHealthCommand cmd) {
        return new CheckHealthAnswer(cmd, true);
    }
    
    protected Answer execute(PingTestCommand cmd) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Excuting PingTestCommand. agent instance id : " + getAgent().getInstanceId());
    	
        String result = null;
        String computingHostIp = cmd.getComputingHostIp();

        if (computingHostIp != null) {
        	if(s_logger.isInfoEnabled())
        		s_logger.info("Ping host : " + computingHostIp + ", agent instance id : " + getAgent().getInstanceId());
            result = doPingTest(computingHostIp);
        } else {
        	if(s_logger.isInfoEnabled())
        		s_logger.info("Ping user router. router IP: " + cmd.getRouterIp()
        			+ ", router private IP: " + cmd.getPrivateIp() + ", agent instance id : " + getAgent().getInstanceId());
        	
            result = doPingTest(cmd.getRouterIp(), cmd.getPrivateIp());
        }

        if (result != null) {
            return new Answer(cmd, false, result);
        }
        return new Answer(cmd);
    }
    
    private String doPingTest( String computingHostIp ) {
    	return null;
    }

    private String doPingTest( String domRIp, String vmIp ) {
    	return null;
    }
    
    protected PrepareForMigrationAnswer execute(final PrepareForMigrationCommand cmd) {
        if(s_logger.isDebugEnabled())
        	s_logger.debug("Handle PrepareForMigrationCommand, host: " + _agent.getHostPrivateIp() + ", vm: " + cmd.getVmName());
        
    	return new PrepareForMigrationAnswer(cmd, true, null);
    }
    
    protected MigrateAnswer execute(final MigrateCommand cmd) {
    	AgentSimulator simulator = AgentSimulator.getInstance();
    	VmMgr vmMgr = getAgent().getVmMgr();
    	
    	SimulatorMigrateVmCmd simulatorCmd = new SimulatorMigrateVmCmd(simulator.getTestCase());

        if(s_logger.isDebugEnabled())
        	s_logger.debug("Handle MigrationCommand, host: " + _agent.getHostPrivateIp()
        		+ ", vm: " + cmd.getVmName() + ", destIP: " + cmd.getDestinationIp());
    	
    	MockVm vm = vmMgr.getVm(cmd.getVmName());
    	if(vm != null) {
    		if(vmMgr.migrate(cmd.getVmName(), cmd.getDestinationIp())) {
    			simulatorCmd.setVmName(cmd.getVmName());
	    		simulatorCmd.setDestIp(cmd.getDestinationIp());
	    		simulatorCmd.setCpuCount(vm.getCpuCount());
	    		simulatorCmd.setRamSize(vm.getRamSize());
	    		simulatorCmd.setUtilization(vm.getUtilization());
		    	AgentSimulator.getInstance().castSimulatorCmd(simulatorCmd);
		        return new MigrateAnswer(cmd, true, null, null);
    		}
    	}
    	
	    return new MigrateAnswer(cmd, false, "VM " + cmd.getVmName() + " is no longer running", null);
    }
    
    protected CheckVirtualMachineAnswer execute(final CheckVirtualMachineCommand cmd) {
    	VmMgr vmMgr = getAgent().getVmMgr();
        final String vmName = cmd.getVmName();
        
        final State state = vmMgr.checkVmState(vmName);
        Integer vncPort = null;
        if (state == State.Running) {
            vncPort = vmMgr.getVncPort(vmName);
            synchronized(_vms) {
                _vms.put(vmName, State.Running);
            }
        }
        return new CheckVirtualMachineAnswer(cmd, state, vncPort);
    }
    
    protected synchronized ReadyAnswer execute(ReadyCommand cmd) {
    	return new ReadyAnswer(cmd);
    }
    
    protected Answer execute(final AttachDiskCommand cmd) {
        return new Answer(cmd);
    }
    
    protected Answer execute(final AttachIsoCommand cmd) {
    	return new Answer(cmd);
    }
    
	@Override
    protected CreateAnswer execute(final CreateCommand cmd) {
		final List<VolumeVO> vols = new ArrayList<VolumeVO>();
    	StorageMgr storageMgr = getAgent().getStorageMgr();
        String imagePath = cmd.getRootdiskFolder();
		
        VolumeVO vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), null, imagePath,
        		1024, Volume.VolumeType.ROOT); // 1G
        vol.setStorageResourceType(Volume.StorageResourceType.STORAGE_POOL);
        vols.add(vol);
	        
        String path = storageMgr.getVolumeName(imagePath, (long)1);
        if (path != null) {
            vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), null, path, 1024, Volume.VolumeType.DATADISK);
            vol.setStorageResourceType(Volume.StorageResourceType.STORAGE_POOL);
            vols.add(vol);
        }
        return new CreateAnswer(cmd, vols);
	}
    
	@Override
	protected Answer execute(DestroyCommand cmd) {
	    return new Answer(cmd, true, null);
	}
	
	public synchronized String mountImage(String storageHosts[], String dest, String vmName, List<VolumeVO> volumes, boolean mirroredVols) {
		if (!mirroredVols) {
			return mountImage(storageHosts[0], dest, vmName, volumes);
		} else {
			return mountMirroredImage(storageHosts, dest, vmName, volumes);
		}
	}
	
	public synchronized String mountImage(String host, String dest, String vmName, List<VolumeVO> volumes) {
		return null;
    }
    
    protected String mountMirroredImage(String hosts[], String dest, String vmName, List<VolumeVO> volumes) {
    	return null;
	}
    
    private synchronized String unmountMirroredImage(String local, String vmName) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("unmountMirroredImage for vm : " + vmName + ", path :" + local);
        return null;
	}

	protected synchronized String unmountImage(String path, String vmName) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("unmountMirroredImage for vm : " + vmName + ", path :" + path);
		return null;
    }
	
    protected List<Object> getHostInfo() {
        ArrayList<Object> info = new ArrayList<Object>();

        VmMgr vmMgr = getAgent().getVmMgr();
        
        long speed = vmMgr.getHostCpuSpeed();
        long cpus = vmMgr.getHostCpuCount();
        long ram = vmMgr.getHostFreeMemory();
        long dom0Ram = vmMgr.getHostDom0Memory();

        // make sure we add hvm support into caps, host allocator will check it
        StringBuilder caps = new StringBuilder();
        caps.append("hvm");
        
        info.add((int)cpus);
        info.add(speed);
        info.add(ram);
        info.add(caps.toString());
        info.add(dom0Ram);
        
        return info;
    }
	
    protected int[] gatherVncPorts(Collection<String> names) {
    	VmMgr vmMgr = getAgent().getVmMgr();
        ArrayList<Integer> ports = new ArrayList<Integer>(names.size());
        for (String name : names) {
            Integer port = vmMgr.getVncPort(name);
            
            if (port != null) {
                ports.add(port);
            }
        }
        
        int[] results = new int[ports.size()];
        int i = 0;
        for (Integer port : ports) {
            results[i++] = port;
        }
        
        return results;
    }
    
    protected HashMap<String, State> sync() {
        Map<String, State> newStates;
        Map<String, State> oldStates = null;
        
        HashMap<String, State> changes = new HashMap<String, State>();
        
        synchronized(_vms) {
            newStates = getAgent().getVmMgr().getVmStates();
            oldStates = new HashMap<String, State>(_vms.size());
            oldStates.putAll(_vms);
            
            for (Map.Entry<String, State> entry : newStates.entrySet()) {
                String vm = entry.getKey();
                
                State newState = entry.getValue();
                State oldState = oldStates.remove(vm);
                
                if (s_logger.isTraceEnabled()) {
                    s_logger.trace("VM " + vm + ": xen has state " + newState + " and we have state " + (oldState != null ? oldState.toString() : "null"));
                }

                if (oldState == null) {
                    _vms.put(vm, newState);
                    changes.put(vm, newState);
                } else if (oldState == State.Starting) {
                    if (newState == State.Running) {
                        _vms.put(vm, newState);
                    } else if (newState == State.Stopped) {
                        s_logger.debug("Ignoring vm " + vm + " because of a lag in starting the vm." );
                    }
                } else if (oldState == State.Stopping) {
                    if (newState == State.Stopped) {
                        _vms.put(vm, newState);
                    } else if (newState == State.Running) {
                        s_logger.debug("Ignoring vm " + vm + " because of a lag in stopping the vm. ");
                    }
                } else if (oldState != newState) {
                    _vms.put(vm, newState);
                    changes.put(vm, newState);
                }
            }
            
            for (Map.Entry<String, State> entry : oldStates.entrySet()) {
                String vm = entry.getKey();
                State oldState = entry.getValue();
                
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
                } else {
                    changes.put(entry.getKey(), State.Stopped);
                }
            }
        }
        
        return changes;
    }
    
    protected void setupMetricsCollector() {
        _collector = new MetricsCollector(getAgent());
        _collector.submitMetricsJobs();
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }

        _mountParent = (String)params.get("mount.parent");
        if (_mountParent == null) {
            _mountParent = "/images";
        }

        if (_mountParent.charAt(_mountParent.length() - 1) != File.separatorChar) {
            _mountParent += File.separatorChar;
        }
        
        setupMetricsCollector();
        
        s_logger.info("Parent directory for image mounts is: " + _mountParent);
        return true;
    }
}
