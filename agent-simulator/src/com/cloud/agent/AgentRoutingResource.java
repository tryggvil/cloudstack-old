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

package com.cloud.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingComputingCommand;
import com.cloud.agent.api.RebootRouterCommand;
import com.cloud.agent.api.StartConsoleProxyCommand;
import com.cloud.agent.api.StartRouterCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupRoutingCommand;
import com.cloud.agent.api.WatchNetworkAnswer;
import com.cloud.agent.api.WatchNetworkCommand;
import com.cloud.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.cloud.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.cloud.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.cloud.agent.api.routing.DhcpEntryCommand;
import com.cloud.agent.api.routing.IPAssocCommand;
import com.cloud.agent.api.routing.LoadBalancerCfgCommand;
import com.cloud.agent.api.routing.SavePasswordCommand;
import com.cloud.agent.api.routing.SetFirewallRuleCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.vm.ConsoleProxy;
import com.cloud.vm.ConsoleProxyVO;
import com.cloud.vm.DomainRouter;
import com.cloud.vm.State;

public class AgentRoutingResource extends AgentComputingResource {
    private static final Logger s_logger = Logger.getLogger(AgentRoutingResource.class);

	public AgentRoutingResource(AgentContainer agent) {
		super(agent);
	}
	
	@Override
	public Type getType() {
        return com.cloud.host.Host.Type.Routing;
	}
	
	@Override
	public PingCommand getCurrentStatus(long id) {
        HashMap<String, State> newStates = sync();
        return new PingComputingCommand(Host.Type.Routing, id, newStates);
	}

	@Override
	public StartupCommand [] initialize() {
        int[] ports = null;
        synchronized(_vms) {
            _vms.clear();
            ports = gatherVncPorts(_vms.keySet());
        }
        Map<String, State> changes = sync();
        List<Object> info = getHostInfo();
        StartupRoutingCommand cmd = new StartupRoutingCommand((Integer)info.get(0), (Long)info.get(1), (Long)info.get(2), (Long)info.get(4), (String)info.get(3), ports, changes);
        return new StartupCommand [] {cmd};
	}
	
    @Override
    public Answer executeRequest(Command cmd) {
        try {
            if (cmd instanceof StartRouterCommand) {
                return execute((StartRouterCommand)cmd);
            } else if(cmd instanceof StartConsoleProxyCommand) {
            	return execute((StartConsoleProxyCommand)cmd);
            } else if (cmd instanceof SetFirewallRuleCommand) {
                return execute((SetFirewallRuleCommand)cmd);
            }else if (cmd instanceof LoadBalancerCfgCommand) {
                return execute((LoadBalancerCfgCommand)cmd);
            } else if (cmd instanceof IPAssocCommand) {
                return execute((IPAssocCommand)cmd);
            } else if (cmd instanceof CheckConsoleProxyLoadCommand) {
            	return execute((CheckConsoleProxyLoadCommand)cmd);
            } else if(cmd instanceof WatchConsoleProxyLoadCommand) {
            	return execute((WatchConsoleProxyLoadCommand)cmd);
            } else if (cmd instanceof WatchNetworkCommand) {
                return execute((WatchNetworkCommand)cmd);
            } else if (cmd instanceof SavePasswordCommand) {
            	return execute((SavePasswordCommand)cmd);
            } else if (cmd instanceof RebootRouterCommand) {
            	return execute((RebootRouterCommand)cmd);
            } else if (cmd instanceof DhcpEntryCommand) {
            	return execute((DhcpEntryCommand)cmd);
            } else {
                return super.executeRequest(cmd);
            }
        } catch (IllegalArgumentException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }
    
    protected synchronized Answer execute(StartRouterCommand cmd) {
        Answer answer = null;
        
        DomainRouter router = cmd.getRouter();
        String result = null;
        
        String vmName = cmd.getVmName();
        
        String local = _mountParent + vmName;
        
        if (cmd.isMirroredVols() ) {
        	result = mountMirroredImage(cmd.getStorageHosts(), local, vmName, cmd.getVolumes());
        } else {
            result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes());
        }
        
        if (result != null) {
            answer = new Answer(cmd, false, result);
            return answer;
        }

        State state = State.Stopped;
        synchronized(_vms) {
            _vms.put(vmName, State.Starting);
        }
        try {
            result = startRouter(router, vmName, router.getVnet(), local);
            if (result != null) {
                throw new ExecutionException(result, null);
            }
            
            state = State.Running;
            _collector.addVM(vmName);
            _collector.submitMetricsJobs();
            
            return new Answer(cmd);
        } catch (ExecutionException e) {
            return new Answer(cmd, false, e.getMessage());
        } catch (Throwable th) {
            s_logger.warn("Exception while starting router.", th);
            return createErrorAnswer(cmd, "Unable to start router", th);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
    }
    
    protected synchronized Answer execute(StartConsoleProxyCommand cmd) {
        
        Answer answer = null;
        
        ConsoleProxyVO proxy = cmd.getProxy();
        String result = null;
        
        String vmName = cmd.getVmName();
        
        String local = _mountParent + vmName;
        result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes());
        
        if (result != null) {
            answer = new Answer(cmd, false, result);
            return answer;
        }
        
        State state = State.Stopped;
        synchronized(_vms) {
            _vms.put(vmName, State.Starting);
        }
        try {
            result = startConsoleProxy(proxy, vmName, local);
            if (result != null) {
                throw new ExecutionException(result, null);
            }
            
            state = State.Running;
            
            _collector.addVM(vmName);
            _collector.submitMetricsJobs();
            
            return new Answer(cmd);
        } catch (ExecutionException e) {
            return new Answer(cmd, false, e.getMessage());
        } catch (Throwable th) {
            s_logger.warn("Exception while starting console proxy.", th);
            return createErrorAnswer(cmd, "Unable to start console proxy", th);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
    }
    
    protected Answer execute(SetFirewallRuleCommand cmd) {
        return new Answer(cmd, true, null);
    }
    
    protected Answer execute(LoadBalancerCfgCommand cmd) {
        return new Answer(cmd, true, null);
	}
    
    protected Answer execute(IPAssocCommand cmd) {
        return new Answer(cmd);
	}
    
    protected Answer execute(CheckConsoleProxyLoadCommand cmd) {
    	return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
    }
    
    protected Answer execute(WatchConsoleProxyLoadCommand cmd) {
    	return executeProxyLoadScan(cmd, cmd.getProxyVmId(), cmd.getProxyVmName(), cmd.getProxyManagementIp(), cmd.getProxyCmdPort());
    }
    
    private Answer executeProxyLoadScan(Command cmd, long proxyVmId, String proxyVmName, String proxyManagementIp, int cmdPort) {
    	return new ConsoleProxyLoadAnswer(cmd, proxyVmId, proxyVmName, true, null);
    }

    protected WatchNetworkAnswer execute(WatchNetworkCommand cmd) {
        return new WatchNetworkAnswer(cmd);
    }
    
    protected Answer execute(SavePasswordCommand cmd) {
        return new Answer(cmd);
    }
    
	protected Answer execute(RebootRouterCommand cmd) {
		return new Answer(cmd);
	}
    
    protected Answer execute (DhcpEntryCommand cmd) {
    	return new Answer(cmd, true, null);
    }
	
    public synchronized String startRouter(DomainRouter router, String name, String vnet, String localPath) {

    	return getAgent().getVmMgr().startVM(name, vnet, router.getGateway(), router.getDns1(),
        		router.getPrivateIpAddress(), router.getPrivateMacAddress(), router.getPrivateNetmask(),
        		router.getPublicIpAddress(), router.getPublicMacAddress(), router.getPublicNetmask(),
        		1, 1, router.getRamSize(), "", "");
    }
    
    public synchronized String startConsoleProxy(ConsoleProxy proxy, String name, String localPath) {
    	return getAgent().getVmMgr().startVM(proxy.getName(), "", proxy.getGateway(), proxy.getDns1(),
    		proxy.getPrivateIpAddress(), proxy.getPrivateMacAddress(), proxy.getPrivateNetmask(),
    		proxy.getPublicIpAddress(), proxy.getPublicMacAddress(), proxy.getPublicNetmask(),
    		1, 1, proxy.getRamSize(), "", "");
    }
}

