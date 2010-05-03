/**
 *  Copyright (c) 2008, VMOps Inc.
 *
 *  This code is Copyrighted and must not be reused, modified, or redistributed without the explicit consent of VMOps.
 */
package com.vmops.agent.resource.routing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.ModifyVlanCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingRoutingCommand;
import com.vmops.agent.api.RebootRouterCommand;
import com.vmops.agent.api.StartConsoleProxyCommand;
import com.vmops.agent.api.StartRouterCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.WatchNetworkAnswer;
import com.vmops.agent.api.WatchNetworkCommand;
import com.vmops.agent.api.proxy.CheckConsoleProxyLoadCommand;
import com.vmops.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.vmops.agent.api.proxy.WatchConsoleProxyLoadCommand;
import com.vmops.agent.api.routing.DhcpEntryCommand;
import com.vmops.agent.api.routing.IPAssocCommand;
import com.vmops.agent.api.routing.LoadBalancerCfgCommand;
import com.vmops.agent.api.routing.SavePasswordCommand;
import com.vmops.agent.api.routing.SetFirewallRuleCommand;
import com.vmops.agent.metrics.VMopsVMMetrics;
import com.vmops.agent.resource.computing.ComputingResource;
import com.vmops.agent.resource.virtualnetwork.VirtualRoutingResource;
import com.vmops.host.Host;
import com.vmops.resource.ServerResource;
import com.vmops.utils.encryption.SymmetricKeyEncrypter;
import com.vmops.utils.script.Script;
import com.vmops.vm.ConsoleProxy;
import com.vmops.vm.ConsoleProxyVO;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.State;

/**
 * ComputingResource is the independence layer to execute requests for the
 * computing host.
 *
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || mount.parent | parent directory to mount the files | Path | \images ||
 *    || public.network.device | physical device name to sent to start the domr | String | eth1 ||
 *    || public.ip.address | ip address to assign to this router. mainly used for lab purposes. | ip address | none ||
 *  }
 **/
@Local(value={ServerResource.class})
public class RoutingResource extends ComputingResource implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(RoutingResource.class);
    private String _rundomrPath;
    private String _modifyVlanPath;
    private String _savepasswordPath; 	// This script saves a random password to the DomR file system
    private String _ipassocPath;
    private String _firewallPath;
    private String _loadbPath;
    private String _firewallextPath;
    private String _dhcpEntryPath;
    private String _rundompPath;		// domP(roxy) - console proxy VM
    
    // Untagged network bridges
    private String _publicEthIf;
    private String _privateEthIf;
    
    private VirtualRoutingResource _vrr;

    private Map<String, long[]> _netStats;
    
    // Encryption
    SymmetricKeyEncrypter encrypter = new SymmetricKeyEncrypter();

    @Override
    public Answer executeRequest(final Command cmd) {
        try {
            if (cmd instanceof StartRouterCommand) {
                return execute((StartRouterCommand)cmd);
            } else if (cmd instanceof ModifyVlanCommand) {
            	return execute((ModifyVlanCommand) cmd);
            } else if(cmd instanceof StartConsoleProxyCommand) {
            	return execute((StartConsoleProxyCommand)cmd);
            } else if (cmd instanceof SetFirewallRuleCommand) {
                return _vrr.executeRequest(cmd);
//                return execute((SetFirewallRuleCommand)cmd);
            } else if (cmd instanceof LoadBalancerCfgCommand) {
                return _vrr.executeRequest(cmd);
//                return execute((LoadBalancerCfgCommand)cmd);
            } else if (cmd instanceof IPAssocCommand) {
                return _vrr.executeRequest(cmd);
//                return execute((IPAssocCommand)cmd);
            } else if (cmd instanceof CheckConsoleProxyLoadCommand) {
            	return execute((CheckConsoleProxyLoadCommand)cmd);
            } else if(cmd instanceof WatchConsoleProxyLoadCommand) {
            	return execute((WatchConsoleProxyLoadCommand)cmd);
            } else if (cmd instanceof WatchNetworkCommand) {
                return execute((WatchNetworkCommand)cmd);
            } else if (cmd instanceof SavePasswordCommand) {
                return _vrr.executeRequest(cmd);
//            	return execute((SavePasswordCommand)cmd);
            }  else if (cmd instanceof RebootRouterCommand) {
            	return execute((RebootRouterCommand)cmd);
            } else if (cmd instanceof DhcpEntryCommand) {
                return _vrr.executeRequest(cmd);
//            	return execute((DhcpEntryCommand)cmd);
            } else if (cmd instanceof StopCommand) {
            	return execute((StopCommand)cmd);
            } else {
                return super.executeRequest(cmd);
            }
        } catch (final IllegalArgumentException e) {
            return new Answer(cmd, false, e.getMessage());
        }
    }

    protected WatchNetworkAnswer execute(final WatchNetworkCommand cmd) {
        final WatchNetworkAnswer answer = new WatchNetworkAnswer(cmd);

        final Map<String, VMopsVMMetrics> currentStats = _collector.getMetricsMap();
        final Map<String, long[]> newStats = new HashMap<String, long[]>();
        final Set<String> vmNames = new HashSet<String>(currentStats.keySet());
        synchronized(_netStats) {
            for (final String vmName : vmNames) {
                final VMopsVMMetrics metrics = currentStats.get(vmName);
                if (metrics != null) {
                    metrics.getNetworkData("eth2");
                    final Map<String, Long> mapRx = metrics.getNetRxTotalBytes();
                    final Long rx = mapRx.get("eth2");
                    final Map<String, Long> mapTx = metrics.getNetTxTotalBytes();
                    final Long tx = mapTx.get("eth2");

                    if (rx != null && tx != null) {
                        newStats.put(vmName, new long[] {rx, tx});
                        final long[] oldStats = _netStats.get(vmName);
                        if (oldStats == null || (oldStats[0] != rx || oldStats[1] != tx)) {
                            answer.addStats(vmName, tx, rx);
                        }
                    }
                }
            }

            _netStats.clear();
            _netStats.putAll(newStats);
        }
        return answer;
    }

    protected Answer execute(final LoadBalancerCfgCommand cmd) {
    	
		File tmpCfgFile = null;
    	try {
    		String cfgFilePath = "";
    		String routerIP = null;
    		
    		if (cmd.getRouterIp() != null) {
    			tmpCfgFile = File.createTempFile(cmd.getRouterIp().replace('.', '_'), "cfg");
				final PrintWriter out
			   	= new PrintWriter(new BufferedWriter(new FileWriter(tmpCfgFile)));
				for (int i=0; i < cmd.getConfig().length; i++) {
					out.println(cmd.getConfig()[i]);
				}
				out.close();
				cfgFilePath = tmpCfgFile.getAbsolutePath();
				routerIP = cmd.getRouterIp();
    		}
			
			final String result = setLoadBalancerConfig(cfgFilePath,
											      cmd.getAddFwRules(), cmd.getRemoveFwRules(),
											      routerIP);
			
			return new Answer(cmd, result == null, result);
		} catch (final IOException e) {
			return new Answer(cmd, false, e.getMessage());
		} finally {
			if (tmpCfgFile != null) {
				tmpCfgFile.delete();
			}
		}
	}

    protected Answer execute(final IPAssocCommand cmd) {
        final String result = assignPublicIpAddress(cmd.getRouterName(), cmd.getRouterIp(), cmd.getPublicIp(), cmd.isAdd(), cmd.isSourceNat(), cmd.getVlanId(), cmd.getVlanGateway(), cmd.getVlanNetmask());
        if (result != null) {
            return new Answer(cmd, false, result);
        }
        return new Answer(cmd);
	}

	private String setLoadBalancerConfig(final String cfgFile,
			final String[] addRules, final String[] removeRules, String routerIp) {
		
		// If the DomR is not running in the Management Server database, and an external firewall was not enabled, return null (success)
		// if (routerIp == null && firewallIpAddress == null) return null;
		
		if (routerIp == null) routerIp = "none";
		
        final Script command = new Script(_loadbPath, _timeout, s_logger);
        
        command.add("-i", routerIp);
        command.add("-f", cfgFile);
        
        StringBuilder sb = new StringBuilder();
        if (addRules.length > 0) {
        	for (int i=0; i< addRules.length; i++) {
        		// Decrypt the external firewall passwords in each addRule
        		String addRule = addRules[i];
        		String[] addRuleList = addRule.split(":");
        		if (addRuleList[5] != "none") {
        			addRuleList[5] = encrypter.decrypt(addRuleList[5]);
        			addRuleList[6] = encrypter.decrypt(addRuleList[6]);
        		}
        		addRule = "";
        		for (int j = 0; j < addRuleList.length; j++) {
        			addRule += addRuleList[j];
        			if (j != (addRuleList.length - 1))
        				addRule += ":";
        		}
        		
        		sb.append(addRule).append(',');
        	}
        	command.add("-a", sb.toString());
        }
        
        sb = new StringBuilder();
        if (removeRules.length > 0) {
        	for (int i=0; i< removeRules.length; i++) {
        		// Decrypt the external firewall passwords in each removeRule
        		String removeRule = removeRules[i];
        		String[] removeRuleList = removeRule.split(":");
        		if (removeRuleList[5] != "none") {
        			removeRuleList[5] = encrypter.decrypt(removeRuleList[5]);
        			removeRuleList[6] = encrypter.decrypt(removeRuleList[6]);
        		}
        		removeRule = "";
        		for (int j = 0; j < removeRuleList.length; j++) {
        			removeRule += removeRuleList[j];
        			if (j != (removeRuleList.length - 1))
        				removeRule += ":";
        		}
        		
        		sb.append(removeRules[i]).append(',');
        	}
        	command.add("-d", sb.toString());
        }
        
        return command.execute();
        
        /*
        if (firewallIpAddress != null) {
        	String synchronizeString = "firewall-" + firewallIpAddress;
        	synchronized(synchronizeString.intern()) {
        		commandResult = command.execute();
        	}
        } else {
        	commandResult = command.execute();
        }
        
        return commandResult;
        */

	}

	@Override
    public Host.Type getType() {
        return Host.Type.Routing;
    }

    @Override
    public synchronized StartupCommand []initialize() {
        checkMounts();
        _netStats = new HashMap<String, long[]>();
        int[] ports = null;
        synchronized(_vms) {
            _vms.clear();
            ports = gatherVncPorts(_vms.keySet());
        }
        final Map<String, State> changes = sync();
        final List<Object> info = getHostInfo();
        final StartupRoutingCommand cmd = new StartupRoutingCommand((Integer)info.get(0), (Long)info.get(1), (Long)info.get(2), (Long)info.get(4), (String)info.get(3), ports, changes);
        cmd.setIqn(getIQN());
        cmd.setHypervisorType(Host.HypervisorType.Xen);
        fillNetworkInformation(cmd);
        return new StartupCommand[] {cmd};
    }

    protected synchronized Answer execute(final SavePasswordCommand cmd) {
    	final String password = cmd.getPassword();
    	final String routerPrivateIPAddress = cmd.getRouterPrivateIpAddress();
    	final String vmName = cmd.getVmName();
    	final String vmIpAddress = cmd.getVmIpAddress();
    	final String local = _mountParent + vmName;

    	// Run save_password_to_domr.sh
        final String result = savePassword(routerPrivateIPAddress, vmIpAddress, password, local);
        if (result != null) {
        	return new Answer(cmd, false, "Unable to save password to DomR.");
        } else {
        	return new Answer(cmd);
        }
    }
    
    protected synchronized Answer execute (final DhcpEntryCommand cmd) {
    	final Script command  = new Script(_dhcpEntryPath, _timeout, s_logger);
    	command.add("-r", cmd.getRouterPrivateIpAddress());
    	command.add("-v", cmd.getVmIpAddress());
    	command.add("-m", cmd.getVmMac());
    	command.add("-n", cmd.getVmName());

    	final String result = command.execute();
    	return new Answer(cmd, result==null, result);
    }
    
    @Override
	protected synchronized StopAnswer execute (final StopCommand cmd) {
    	StopAnswer a = super.execute(cmd);
    	return a;
    }

    protected synchronized Answer execute(final StartRouterCommand cmd) {
        Answer answer = null;

        final DomainRouter router = cmd.getRouter();
        final String vnet = router.getVnet();
        String result = null;

        final String vmName = cmd.getVmName();

        final String local = _mountParent + vmName;

        if (cmd.isMirroredVols() ) {
        	result = mountMirroredImage(cmd.getStorageHosts(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
        } else {
            result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes(), cmd.getBootloader());
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

            result = connect(router.getPrivateIpAddress());
            if (result != null) {
                throw new ExecutionException(result, null);
            }

            //result = assignPublicIpAddress(vmName, router.getId(), router.getVnet(), router.getPrivateIpAddress(), router.getPublicMacAddress(), _publicIpAddress != null ? _publicIpAddress : router.getPublicIpAddress());
            if (result != null) {
                throw new ExecutionException(result, null);
            }

            state = State.Running;
            _collector.addVM(vmName);
            _collector.submitMetricsJobs();

            return new Answer(cmd);
        } catch (final ExecutionException e) {
            if (!_debug) {
                cleanupVM(vmName, local, vnet);
            }
            return new Answer(cmd, false, e.getMessage());
        } catch (final Throwable th) {
            s_logger.warn("Exception while starting router.", th);
            if (!_debug) {
                cleanupVM(vmName, local, vnet);
            }
            return createErrorAnswer(cmd, "Unable to start router", th);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
    }
    
    protected synchronized Answer execute(ModifyVlanCommand cmd) {
    	boolean add = cmd.getAdd();
    	String vlanId = cmd.getVlanId();
    	String vlanGateway = cmd.getVlanGateway();
    	
    	try {
    		String result = modifyVlan(add, vlanId, vlanGateway);
    		
    		if (result != null) {
    			throw new ExecutionException(result, null);
    		}
    		
    		return new Answer(cmd);
    	} catch (Exception e) {
    		s_logger.warn("Exception while modifying vlan.", e);
    		return new Answer(cmd, false, e.getMessage());
    	}
    }

	protected Answer execute(RebootRouterCommand cmd) {
		Answer answer = super.execute(cmd);
		if (answer.getResult()) {
			String cnct = connect(cmd.getPrivateIpAddress());
			if (cnct == null) {
				return answer;
			} else {
				return new Answer(cmd, false, cnct);
			}
		}
		return answer;
	}

	protected synchronized Answer execute(final StartConsoleProxyCommand cmd) {

        Answer answer = null;

        final ConsoleProxyVO proxy = cmd.getProxy();
        String result = null;

        final String vmName = cmd.getVmName();

        final String local = _mountParent + vmName;

        result = mountImage(cmd.getStorageHost(), local, vmName, cmd.getVolumes(), cmd.getBootloader());

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

            s_logger.info("Ping console proxy VM at " + proxy.getPrivateIpAddress() + ":" + cmd.getProxyCmdPort());
            result = connect(proxy.getPrivateIpAddress(), cmd.getProxyCmdPort());
            if (result != null) {
                s_logger.error("Console proxy VM at " + proxy.getPrivateIpAddress() + ":" + cmd.getProxyCmdPort() + " is not connectable");
                throw new ExecutionException(result, null);
            }
            s_logger.info("Console proxy VM at " + proxy.getPrivateIpAddress() + ":" + cmd.getProxyCmdPort() + " is fully up");

            state = State.Running;
            
            _collector.addVM(vmName);
            _collector.submitMetricsJobs();

            return new Answer(cmd);
        } catch (final ExecutionException e) {
            if (!_debug) {
                cleanupVM(vmName, local, "");
            }
            return new Answer(cmd, false, e.getMessage());
        } catch (final Throwable th) {
            s_logger.warn("Exception while starting console proxy.", th);
            if (!_debug) {
                cleanupVM(vmName, local, "");
            }
            return createErrorAnswer(cmd, "Unable to start console proxy", th);
        } finally {
            synchronized(_vms) {
                _vms.put(vmName, state);
            }
        }
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

    @Override
    public PingCommand getCurrentStatus(final long id) {
        PingRoutingCommand cmd = new PingRoutingCommand(Host.Type.Routing, id, sync());

        // ping the gateway
        //cmd.setGatewayAccessible(doPingTest() == null);
        return cmd;
    }

    public synchronized String startRouter(final DomainRouter router, final String name, final String vnet, final String localPath) {
        final Script command = new Script(_rundomrPath, _startTimeout, s_logger);
        command.add("-v", vnet);
        command.add("-i", router.getPrivateIpAddress());
        command.add("-m", Long.toString(router.getRamSize()));
        command.add("-g", router.getGateway());
        command.add("-a", router.getGuestMacAddress());
        command.add("-l", name);
        command.add("-A", router.getPrivateMacAddress());
        command.add("-n", router.getPrivateNetmask());
        command.add("-e", router.getGuestIpAddress());
        command.add("-E", router.getGuestNetmask());
        command.add("-b", router.getVlanId());
        command.add("-B", _privateEthIf);
        final StringBuilder builder = new StringBuilder();
        builder.append("dns1=").append(router.getDns1()).append(" dns2=").append(router.getDns2()).append(" domain=").append(router.getDomain());
        command.add("-d", builder.toString());
        command.add("-p", router.getPublicMacAddress());
        command.add("-I", router.getPublicIpAddress());
        command.add("-N", router.getPublicNetmask());
        if (router.isMirroredVols()) {
        	command.add("-M");
        }
        command.add(localPath);

        return command.execute();
    }
    
    public synchronized String modifyVlan(final boolean add, final String vlanId, final String vlanGateway) {
    	final Script command = new Script(_modifyVlanPath, _startTimeout, s_logger);
    	
    	if (add)
    		command.add("-o", "add");
    	else
    		command.add("-o", "delete");
    	
    	command.add("-v", vlanId);
    	command.add("-g", vlanGateway);
    	
    	return command.execute();
    }

    public synchronized String savePassword(final String privateIpAddress, final String vmIpAddress, final String password, final String localPath) {
    	final Script command  = new Script(_savepasswordPath, _startTimeout, s_logger);
    	command.add("-r", privateIpAddress);
    	command.add("-v", vmIpAddress);
    	command.add("-p", password);
    	command.add(localPath);

    	return command.execute();
    }

    public synchronized String startConsoleProxy(final ConsoleProxy proxy, final String name, final String localPath) {
/*
        final Script command = new Script(_rundompPath, _startTimeout, s_logger);

        command.add("-i", proxy.getPrivateIpAddress());
        command.add("-m", Long.toString(proxy.getRamSize()));
        command.add("-g", proxy.getGateway());
        command.add("-l", name);
        command.add("-A", proxy.getPrivateMacAddress());
        command.add("-n", proxy.getPrivateNetmask());
        command.add("-b", _publicEthIf);
        command.add("-B", _privateEthIf);
        final StringBuilder builder = new StringBuilder();
        builder.append("dns1=").append(proxy.getDns1()).append(" dns2=").append(proxy.getDns2()).append(" domain=").append(proxy.getDomain());
        command.add("-d", builder.toString());
        command.add("-p", proxy.getPublicMacAddress());
        command.add("-I", proxy.getPublicIpAddress());
        command.add("-N", proxy.getPublicNetmask());
        command.add(localPath);
        return command.execute();
*/
        final Script command = new Script(_rundomrPath, _startTimeout, s_logger);
        // command.add("-v", "0");
        command.add("-t", "domP");
        command.add("-i", proxy.getPrivateIpAddress());
        command.add("-m", Long.toString(proxy.getRamSize()));
        command.add("-g", proxy.getGateway());
        // command.add("-a", proxy.getRouterMacAddress());
        command.add("-l", name);
        command.add("-A", proxy.getPrivateMacAddress());
        command.add("-n", proxy.getPrivateNetmask());
        // command.add("-e", proxy.getRouterIpAddress());
        // command.add("-E", proxy.getRouterNetmask());
        command.add("-B", _privateEthIf);
        final StringBuilder builder = new StringBuilder();
        builder.append("dns1=").append(proxy.getDns1()).append(" dns2=").append(proxy.getDns2()).append(" domain=").append(proxy.getDomain());
        command.add("-d", builder.toString());
        command.add("-p", proxy.getPublicMacAddress());
        command.add("-I", proxy.getPublicIpAddress());
        command.add("-N", proxy.getPublicNetmask());
        command.add(localPath);

        return command.execute();
    }

    public String assignPublicIpAddress(final String vmName, final long id, final String vnet, final String privateIpAddress, final String macAddress, final String publicIpAddress) {

        final Script command = new Script(_ipassocPath, _timeout, s_logger);
        command.add("-A");
        command.add("-f"); //first ip is source nat ip
        command.add("-r", vmName);
        command.add("-i", privateIpAddress);
        command.add("-a", macAddress);
        command.add("-l", publicIpAddress);

        return command.execute();
    }

    public String assignPublicIpAddress(final String vmName, final String privateIpAddress, final String publicIpAddress, final boolean add, final boolean sourceNat, final String vlanId, final String vlanGateway, final String vlanNetmask) {

        final Script command = new Script(_ipassocPath, _timeout, s_logger);
        if (add) {
        	command.add("-A");
        } else {
        	command.add("-D");
        }
        if (sourceNat) {
        	command.add("-f");
        }
        command.add("-i", privateIpAddress);
        command.add("-l", publicIpAddress);
        command.add("-r", vmName);
        
        command.add("-n", vlanNetmask);
        
        if (vlanId != null) {
        	command.add("-v", vlanId);
        	command.add("-g", vlanGateway);
        }

        return command.execute();
    }

    public String setFirewallRules(final boolean enable, final String routerName, final String routerIpAddress, final String protocol,
    							   final String publicIpAddress, final String publicPort, final String privateIpAddress, final String privatePort,
    							   final String firewallIpAddress, final String firewallUser, final String encryptedFirewallPassword, final String encryptedFirewallEnablePassword,
    							   String oldPrivateIP,  String oldPrivatePort, String vlanNetmask) {
        
    	if (routerIpAddress == null && firewallIpAddress == null) {
        	s_logger.warn("SetFirewallRuleCommand did nothing because Router IP address was null and Firewall IP address was null when creating rule for public IP: " + publicIpAddress);
            return null;    // Setting for external firewall only but no firewall ip address given.
        }
    	
    	if (oldPrivateIP == null) oldPrivateIP = "";
    	if (oldPrivatePort == null) oldPrivatePort = "";
    	
    	boolean callFirewallScript = (routerIpAddress != null);
        final Script command = new Script(callFirewallScript ? _firewallPath : _firewallextPath, _timeout, s_logger);
        
        command.add(enable ? "-A" : "-D");
        command.add("-P", protocol);
        command.add("-l", publicIpAddress);
        command.add("-p", publicPort);
        
        if (callFirewallScript) {
        	command.add("-n", routerName);
            command.add("-i", routerIpAddress);
            command.add("-r", privateIpAddress);
            command.add("-d", privatePort);
            command.add("-N", vlanNetmask);
            command.add("-w", oldPrivateIP);
            command.add("-x", oldPrivatePort);
        }
        
        if (firewallIpAddress != null) {
        	String firewallPassword = encrypter.decrypt(encryptedFirewallPassword);
        	String firewallEnablePassword = encrypter.decrypt(encryptedFirewallEnablePassword);
        	
            command.add("-f", firewallIpAddress);
            command.add("-u", firewallUser);
            command.add("-y", firewallPassword);
            command.add("-z", firewallEnablePassword);
        }
        
        String commandResult;
        
        if (firewallIpAddress != null) {
        	String synchronizeString = "firewall-" + firewallIpAddress;
        	synchronized(synchronizeString.intern()) {
        		commandResult = command.execute();
        	}
        } else {
        	commandResult = command.execute();
        }
        
        return commandResult;
    }

    protected Answer execute(final SetFirewallRuleCommand cmd) {
        final String result = setFirewallRules(cmd.isEnable(),
        								 cmd.getRouterName(),
                                         cmd.getRouterIpAddress(),
                                         cmd.getProtocol().toLowerCase(),
                                         cmd.getPublicIpAddress(),
                                         cmd.getPublicPort(),
                                         cmd.getPrivateIpAddress(),
                                         cmd.getPrivatePort(),
                                         cmd.getFirewallIpAddress(),
                                         cmd.getFirewallUser(),
                                         cmd.getFirewallPassword(),
                                         cmd.getFirewallEnablePassword(),
                                         cmd.getOldPrivateIP(),
                                         cmd.getOldPrivatePort(),
                                         cmd.getVlanNetmask());
                                         
        return new Answer(cmd, result == null, result);
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.error("Base class was unable to configure.");
            return false;
        }
        
        _vrr = new VirtualRoutingResource();
        _vrr.configure("DomR Resource", params);
        
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
        
        _modifyVlanPath = Script.findScript(networkScriptsDir, "modifyvlan.sh");
        if (_modifyVlanPath == null) {
        	throw new ConfigurationException("Unable to find modifyvlan.sh");
        }

/*
        _ipassocPath = findScript("ipassoc.sh");
        if (_ipassocPath == null) {
            throw new ConfigurationException("Unable to find the ipassoc.sh");
        }
        s_logger.info("ipassoc.sh found in " + _ipassocPath);

        _firewallPath = findScript("firewall.sh");
        if (_firewallPath == null) {
            throw new ConfigurationException("Unable to find the firewall.sh");
        }

        _firewallextPath = findScript("firewallext.sh");
        if (_firewallextPath == null) {
            throw new ConfigurationException("Unable to find the firewallext.sh");
        }

        _loadbPath = findScript("loadbalancer.sh");
        if (_loadbPath == null) {
            throw new ConfigurationException("Unable to find the loadbalancer.sh");
        }

        _savepasswordPath = findScript("save_password_to_domr.sh");
        if(_savepasswordPath == null) {
        	throw new ConfigurationException("Unable to find save_password_to_domr.sh");
        }
        
        _dhcpEntryPath = findScript("dhcp_entry.sh");
        if(_dhcpEntryPath == null) {
        	throw new ConfigurationException("Unable to find dhcp_entry.sh");
        }
         */
        
        _rundomrPath = Script.findScript(xenScriptsDir, "rundomr.sh");
        if (_rundomrPath == null) {
            throw new ConfigurationException("Unable to find the rundomr.sh");
            }
        s_logger.info("rundomr.sh found in " + _rundomrPath);

        _rundompPath = Script.findScript(xenScriptsDir, "rundomp.sh");
        if(_rundompPath == null) {
            throw new ConfigurationException("Unable to find rundomp.sh");
        }
        
        _publicEthIf = (String)params.get("public.network.device");
        if (_publicEthIf == null) {
            _publicEthIf = "xenbr1";
        }
        _publicEthIf = _publicEthIf.toLowerCase();
        
        _privateEthIf = (String)params.get("private.network.device");
        if (_privateEthIf == null) {
        	_privateEthIf = "xenbr0";
        }
        _privateEthIf = _privateEthIf.toLowerCase();

        if (_publicEthIf.equals(_privateEthIf)) {
        	throw new ConfigurationException("Private and Public network interfaces cannot be the same (" + _privateEthIf+ "). Specify them as public.network.device and private.network.device in agent.properties ");
        }

        // Initialize the encrypter
        try {
        	encrypter.prepareEncrypter();
        } catch (Exception e) {
        	throw new ConfigurationException("Unable to initialize the encrypter.");
        }

        return true;
    }

    private String doPingTest() {
        final Script command = new Script(_pingTestPath, 10000, s_logger);
        command.add("-g", "");
        return command.execute();
    }

}
