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
package com.vmops.agent.manager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.CheckHealthCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetHostStatsAnswer;
import com.vmops.agent.api.GetHostStatsCommand;
import com.vmops.agent.api.PingAnswer;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingComputingCommand;
import com.vmops.agent.api.PingRoutingCommand;
import com.vmops.agent.api.PingStorageCommand;
import com.vmops.agent.api.ReadyAnswer;
import com.vmops.agent.api.ShutdownCommand;
import com.vmops.agent.api.StartupAnswer;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupComputingCommand;
import com.vmops.agent.api.StartupProxyCommand;
import com.vmops.agent.api.StartupRoutingCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.StoragePoolInfo;
import com.vmops.agent.api.UpgradeCommand;
import com.vmops.agent.manager.allocator.HostAllocator;
import com.vmops.agent.manager.allocator.PodAllocator;
import com.vmops.agent.transport.Request;
import com.vmops.agent.transport.Response;
import com.vmops.agent.transport.UpgradeResponse;
import com.vmops.alert.AlertManager;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.capacity.CapacityVO;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.configuration.Config;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterIpAddressVO;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.DataCenterIpAddressDaoImpl;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.HostVlanMapDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.UnsupportedVersionException;
import com.vmops.ha.HighAvailabilityManager;
import com.vmops.host.DetailVO;
import com.vmops.host.Host;
import com.vmops.host.HostStats;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.Host.HypervisorType;
import com.vmops.host.Status.Event;
import com.vmops.host.dao.DetailsDao;
import com.vmops.host.dao.HostDao;
import com.vmops.maint.UpgradeManager;
import com.vmops.network.IPAddressVO;
import com.vmops.network.NetworkManager;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.resource.Discoverer;
import com.vmops.resource.ServerResource;
import com.vmops.service.ServiceOffering;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.GuestOSCategoryVO;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStorageResourceAssoc;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VirtualMachineTemplate;
import com.vmops.storage.Storage.FileSystem;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.dao.GuestOSCategoryDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.storage.resource.DummySecondaryStorageResource;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.user.Account;
import com.vmops.user.dao.UserStatisticsDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.Adapters;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.component.Inject;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.MacAddress;
import com.vmops.utils.net.NetUtils;
import com.vmops.utils.nio.HandlerFactory;
import com.vmops.utils.nio.Link;
import com.vmops.utils.nio.NioServer;
import com.vmops.utils.nio.Task;
import com.vmops.vm.ConsoleProxyVO;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.UserVm;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.DomainRouter.Role;
import com.vmops.vm.dao.VMInstanceDao;

/**
 * Implementation of the Agent Manager. This class controls the connection to
 * the agents.
 * 
 * @config {@table || Param Name | Description | Values | Default || || port |
 *         port to listen on for agent connection. | Integer | 8250 || ||
 *         workers | # of worker threads | Integer | 5 || || router.template.id
 *         | default id for template | Integer | 1 || || router.ram.size |
 *         default ram for router vm in mb | Integer | 128 || ||
 *         router.ip.address | ip address for the router | ip | 10.1.1.1 || ||
 *         wait | Time to wait for control commands to return | seconds | 1800
 *         || || domain | domain for domain routers| String | foo.com || ||
 *         alert.wait | time to wait before alerting on a disconnected agent |
 *         seconds | 1800 || || update.wait | time to wait before alerting on a
 *         updating agent | seconds | 600 || || ping.interval | ping interval in
 *         seconds | seconds | 60 || || instance.name | Name of the deployment
 *         instance | String | DEFAULT || || default.zone | Default zone |
 *         String | required || || start.retry | Number of times to retry start
 *         | Number | 2 || || ping.timeout | multiplier to ping.interval before
 *         announcing an agent has timed out | float | 2.0x || ||
 *         router.stats.interval | interval to report router statistics |
 *         seconds | 300s || * }
 **/
@Local(value = { AgentManager.class })
public class AgentManagerImpl implements AgentManager, HandlerFactory {
    private static final Logger s_logger = Logger.getLogger(AgentManagerImpl.class);

    protected ConcurrentHashMap<Long, AgentAttache> _agents = new ConcurrentHashMap<Long, AgentAttache>(2047);
    protected ConcurrentHashMap<Integer, Listener> _hostMonitors = new ConcurrentHashMap<Integer, Listener>(7);
    protected ConcurrentHashMap<Integer, Listener> _cmdMonitors = new ConcurrentHashMap<Integer, Listener>(7);
    protected int _monitorId = 0;

    protected NioServer _connection;
    @Inject
    protected HostDao _hostDao = null;
    @Inject
    protected UserStatisticsDao _userStatsDao = null;
    @Inject
    protected DataCenterDao _dcDao = null;
    @Inject
    protected HostVlanMapDao _hostVlanMapDao = null;
    @Inject
    protected VlanDao _vlanDao = null;
    @Inject
    protected DataCenterIpAddressDaoImpl _privateIPAddressDao = null;
    @Inject
    protected IPAddressDao _publicIPAddressDao = null;
    @Inject
    protected HostPodDao _podDao = null;
    protected Adapters<HostAllocator> _hostAllocators = null;
    protected Adapters<PodAllocator> _podAllocators = null;
    @Inject
    protected EventDao _eventDao = null;
    @Inject
    protected VMInstanceDao _vmDao = null;
    @Inject
    protected VolumeDao _volDao = null;
    @Inject
    protected CapacityDao _capacityDao = null;
    @Inject
    protected StoragePoolDao _storagePoolDao = null;
    @Inject
    protected StoragePoolHostDao _storagePoolHostDao = null;
    @Inject
    protected GuestOSCategoryDao _guestOSCategoryDao = null;
    @Inject
    protected DetailsDao _hostDetailsDao = null;
    protected Adapters<Discoverer> _discoverers = null;
    protected int _port;

    @Inject
    protected HighAvailabilityManager _haMgr = null;
    @Inject
    protected AlertManager _alertMgr = null;

    @Inject
    protected NetworkManager _networkMgr = null;

    @Inject
    protected UpgradeManager _upgradeMgr = null;

    protected int _retry = 2;

    protected String _name;
    protected String _instance;

    protected int _wait;
    protected int _updateWait;
    protected int _alertWait;
    protected long _nodeId = -1;
    protected int _overProvisioningFactor = 1;
    protected float _cpuOverProvisioningFactor = 1;

    protected Random _rand = new Random(System.currentTimeMillis());

    protected int _pingInterval;
    protected long _pingTimeout;
    protected AgentMonitor _monitor = null;

    protected ExecutorService _executor;

    @Inject
    protected VMTemplateDao _tmpltDao;
    @Inject
    protected VMTemplateHostDao _vmTemplateHostDao;
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _name = name;

        Request.initBuilder();

        final ComponentLocator locator = ComponentLocator.getCurrentLocator();
        ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
        if (configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }

        final Map<String, String> configs = configDao.getConfiguration("AgentManager", params);

        _port = NumbersUtil.parseInt(configs.get("port"), 8250);
        final int workers = NumbersUtil.parseInt(configs.get("workers"), 5);

        String value = configs.get("ping.interval");
        _pingInterval = NumbersUtil.parseInt(value, 60);

        value = configs.get("wait");
        _wait = NumbersUtil.parseInt(value, 1800) * 1000;

        value = configs.get("alert.wait");
        _alertWait = NumbersUtil.parseInt(value, 1800);

        value = configs.get("update.wait");
        _updateWait = NumbersUtil.parseInt(value, 600);

        value = configs.get("ping.timeout");
        final float multiplier = value != null ? Float.parseFloat(value) : 2.5f;
        _pingTimeout = (long) (multiplier * _pingInterval);

        s_logger.info("Ping Timeout is " + _pingTimeout);

        _instance = configs.get("instance.name");
        if (_instance == null) {
            _instance = "DEFAULT";
        }

        _hostAllocators = locator.getAdapters(HostAllocator.class);
        if (_hostAllocators == null || !_hostAllocators.isSet()) {
            throw new ConfigurationException("Unable to find an host allocator.");
        }

        _podAllocators = locator.getAdapters(PodAllocator.class);
        if (_podAllocators == null || !_podAllocators.isSet()) {
            throw new ConfigurationException("Unable to find an pod allocator.");
        }
        
        _discoverers = locator.getAdapters(Discoverer.class);

        if (_nodeId == -1) {
            // FIXME: We really should not do this like this. It should be done
            // at config time and is stored as a config variable.
            _nodeId = MacAddress.getMacAddress().toLong();
        }

        _hostDao.markHostsAsDisconnected(_nodeId, Status.Up, Status.Disconnected);
        
        _monitor = new AgentMonitor(_nodeId, _hostDao, _volDao, _vmDao, _dcDao, _podDao, this, _alertMgr, _pingTimeout);
        registerForHostEvents(_monitor, true, true);

        _executor = new ThreadPoolExecutor(10, 100, 60l, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory("AgentTaskPool"));

        String overProvisioningFactorStr = configs.get("storage.overprovisioning.factor");
        _overProvisioningFactor = NumbersUtil.parseInt(overProvisioningFactorStr, 1);
        
        String cpuOverProvisioningFactorStr = configs.get("cpu.overprovisioning.factor");
        _cpuOverProvisioningFactor = NumbersUtil.parseFloat(cpuOverProvisioningFactorStr, 1);
        if(_cpuOverProvisioningFactor < 1){
        	_cpuOverProvisioningFactor = 1;
        }

        _connection = new NioServer("AgentManager", _port, workers + 10, this);
       
        s_logger.info("Listening on " + _port + " with " + workers + " workers");

        return true;
    }

    @Override
    public Task create(Task.Type type, Link link, byte[] data) {
        return new AgentHandler(type, link, data);
    }

    @Override
    public int registerForHostEvents(final Listener listener, boolean connections, boolean commands) {
        synchronized (_hostMonitors) {
            _monitorId++;
        }
        if (connections) {
            _hostMonitors.put(_monitorId, listener);
        }
        if (commands) {
            _cmdMonitors.put(_monitorId, listener);
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Registering listener " + listener.getClass().getSimpleName() + " with id " + _monitorId);
        }
        return _monitorId;
    }

    @Override
    public void unregisterForHostEvents(final int id) {
        s_logger.debug("Deregistering " + id);
        _hostMonitors.remove(id);
    }

    private AgentControlAnswer handleControlCommand(AgentAttache attache, final AgentControlCommand cmd) {
    	AgentControlAnswer answer = null;
    	
        for (Listener listener : _cmdMonitors.values()) {
            answer = listener.processControlCommand(attache.getId(), cmd);
            
            if(answer != null)
            	return answer;
        }
        
        s_logger.warn("No handling of agent control command: " + cmd.toString() + " sent from " + attache.getId());
        return new AgentControlAnswer(cmd);
    }
    
    public void handleCommands(AgentAttache attache, final long sequence, final Command[] cmds) {
        for (Listener listener : _cmdMonitors.values()) {
            boolean processed = listener.processCommand(attache.getId(), sequence, cmds);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace("SeqA " + attache.getId() + "-" + sequence + ": " + (processed ? "processed" : "not processed") + " by " + listener.getClass());
            }
        }
    }

    public AgentAttache findAttache(long hostId) {
        return _agents.get(hostId);
    }

    @Override
    public Set<Long> getConnectedHosts() {
        // make the returning set be safe for concurrent iteration
        final HashSet<Long> result = new HashSet<Long>();

        synchronized (_agents) {
            final Set<Long> s = _agents.keySet();
            for (final Long id : s)
                result.add(id);
        }
        return result;
    }

    @Override
    public Host findHost(final Host.Type type, final DataCenterVO dc, final HostPodVO pod, final StoragePoolVO sp,
    		final ServiceOffering offering, final DiskOfferingVO diskOffering, final VMTemplateVO template, VMInstanceVO vm,
    		Host currentHost, final Set<Host> avoid) {
        Enumeration<HostAllocator> en = _hostAllocators.enumeration();
        while (en.hasMoreElements()) {
            final HostAllocator allocator = en.nextElement();
            final Host host = allocator.allocateTo(offering, diskOffering, type, dc, pod, sp, template, avoid);
            if (host == null)
                continue;

            if (type == Host.Type.Routing) {
                if (vm != null && currentHost == null) {
                    // The system is trying to start a DomainRouter on a Routing
                    // Host
                    // Make sure that any Routing Host that is returned has the
                    // VLAN ID of the DomainRouter's public IP address

                	String ipAddress;
                	
                	if(vm instanceof DomainRouterVO) {
	                    DomainRouterVO router = (DomainRouterVO) vm;
	                    ipAddress = (router.getRole() == Role.DHCP_FIREWALL_LB_PASSWD_USERDATA) ? router.getPublicIpAddress() : router.getGuestIpAddress();
                	} else if (vm instanceof ConsoleProxyVO){
	                    ConsoleProxyVO proxy = (ConsoleProxyVO) vm;
                		ipAddress = proxy.getPublicIpAddress();
                	} else  {
                		SecondaryStorageVmVO secStorageVm = (SecondaryStorageVmVO) vm;
                		ipAddress = secStorageVm.getPublicIpAddress();
                	}
                	
                    IPAddressVO ipVO = _publicIPAddressDao.findById(ipAddress);

                    // Get the VLAN ID for the DomainRouter
                    VlanVO vlan = _vlanDao.findById(ipVO.getVlanDbId());
                    String vlanId = vlan.getVlanId();

                    // Get the list of VLAN IDs on the host
                    List<String> vlanIds = _hostDao.getVlanIds(host.getId().longValue());
                    if (!vlanIds.contains(vlanId)) {
                        s_logger.warn("Cannot start router on host " + host.getName() + " because it does not contain the VLAN " + vlanId
                                + ". Avoiding host...");
                        avoid.add(host);
                        continue;
                    }
                } else if (vm != null && currentHost != null) {
                    // The system is trying to migrate a DomainRouter from one
                    // Routing Host to another
                    // Make sure that any Routing Host that is returned has all
                    // the VLAN IDs of the current Routing Host

                    List<String> currentHostVlanIds = _hostDao.getVlanIds(vm.getHostId());

                    List<String> migrationHostVlanIds = _hostDao.getVlanIds(host.getId().longValue());
                    if (!migrationHostVlanIds.containsAll(currentHostVlanIds)) {
                        s_logger.warn("Cannot migrate router to host " + host.getName() + " because it is missing some VLAN IDs. Skipping host...");
                        avoid.add(host);
                        continue;
                    }
                }
            }

            return host;
        }

        s_logger.warn("findHost() could not find a non-null host.");
        return null;
    }

    protected AgentAttache handleDirectConnect(ServerResource resource, StartupCommand[] startup, Map<String, String> details, boolean old) {
        if (startup == null) {
            return null;
        }
        HostVO server = createHost(startup, resource, details, old);
        if (server == null) {
            return null;
        }

        long id = server.getId();

        AgentAttache attache = createAttache(id, server, resource);
        notifyMonitorsOfConnection(id, startup);
        return attache;
    }

    @Override
    public List<HostVO> discoverHosts(long dcId, Long podId, String url, String username, String password) {
        List<HostVO> hosts = new ArrayList<HostVO>();
        s_logger.info("Trying to add a new host at " + url + " in data center " + dcId);
        Enumeration<Discoverer> en = _discoverers.enumeration();
        while (en.hasMoreElements()) {
            Discoverer discoverer = en.nextElement();
            Map<? extends ServerResource, Map<String, String>> resources = discoverer.find(dcId, podId, url, username, password);
            if (resources != null) {
                for (Map.Entry<? extends ServerResource, Map<String, String>> entry : resources.entrySet()) {
                    ServerResource resource = entry.getKey();

                    AgentAttache attache = simulateStart(resource, entry.getValue(), true);
                    if (attache != null) {
                        hosts.add(_hostDao.findById(attache.getId()));
                    }
                    discoverer.postDiscovery(hosts, _nodeId);
                    
                }
                s_logger.info("server resources successfully discovered by " + discoverer.getName());
                return hosts;
            }
        }

        s_logger.warn("Unable to find the server resources at " + url);
        return hosts;
    }

    @DB
    public boolean deleteHost(long hostId) {
        Transaction txn = Transaction.currentTxn();
        try {
            HostVO host = _hostDao.findById(hostId);
            if (host == null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Host: " + hostId + " does not even exist.  Delete call is ignored.");
                }
                return true;
            }

            txn.start();
            host.setPublicIpAddress(null);
            host.setGuid(null);
            if (!_hostDao.updateStatus(host, Event.Remove, _nodeId)) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Host: " + hostId + " is not in a 'Removed' or 'Maintenance' state.  Delete call is ignored");
                }
                return false;
            }
            _dcDao.releasePrivateIpAddress(host.getPrivateIpAddress(), host.getDataCenterId(), null);
            _hostDao.remove(hostId);
            txn.commit();
            return true;
        } catch (Throwable t) {
            s_logger.error("Unable to delete host: " + hostId, t);
            return false;
        }
    }

    @Override
    public boolean isVirtualMachineUpgradable(final UserVm vm, final ServiceOffering offering) {
        Enumeration<HostAllocator> en = _hostAllocators.enumeration();
        boolean isMachineUpgradable = true;
        while (isMachineUpgradable && en.hasMoreElements()) {
            final HostAllocator allocator = en.nextElement();
            isMachineUpgradable = allocator.isVirtualMachineUpgradable(vm, offering);
        }

        return isMachineUpgradable;
    }

    protected int getPingInterval() {
        return _pingInterval;
    }

    @Override
    public Answer send(final Long hostId, final Command cmd, final int timeout) throws AgentUnavailableException, OperationTimedoutException {
        Answer[] answers = send(hostId, new Command[] { cmd }, true, timeout);
        if (answers != null)
            return answers[0];

        return null;
    }

    @Override
    public Answer[] send(final Long hostId, final Command[] cmds, final boolean stopOnError, final int timeout) throws AgentUnavailableException,
            OperationTimedoutException {
        assert hostId != null : "Who's not checking the agent id before sending?  ... (finger wagging)";
        if (hostId == null) {
            throw new AgentUnavailableException(-1);
        }

        assert cmds.length > 0 : "Ask yourself this about a hundred times.  Why am I  sending zero length commands?";

        if (cmds.length == 0) {
            return new Answer[0];
        }

        final AgentAttache agent = getAttache(hostId);
        if (agent.isClosed()) {
            throw new AgentUnavailableException("agent not logged into this management server", hostId);
        }

        long seq = _hostDao.getNextSequence(hostId);
        Request req = new Request(seq, hostId, _nodeId, cmds, stopOnError, true);
        return agent.send(req, timeout);
    }

    protected Status investigate(AgentAttache agent) {
        Long hostId = agent.getId();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("checking if agent (" + hostId + ") is alive");
        }

        try {
            long seq = _hostDao.getNextSequence(hostId);
            Request req = new Request(seq, hostId, _nodeId, new Command[] { new CheckHealthCommand() }, true, true);
            Answer[] answers = agent.send(req, 10 * 1000);
            if (answers[0].getResult()) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("agent (" + hostId + ") responded to checkHeathCommand, reporting that agent is up");
                }
                return Status.Up; // the agent might be behind on ping, but it's
                // there and responding to commands...
            }
        } catch (AgentUnavailableException e) {
            s_logger.debug("Agent is unavailable so we move on.");
        } catch (OperationTimedoutException e) {
            s_logger.debug("Timed Out " + e.getMessage());
        }

        return _haMgr.investigate(hostId);
    }

    protected AgentAttache getAttache(final Long hostId) {
        assert (hostId != null) : "Who didn't check their id value?";
        if (hostId == null) {
            return null;
        }

        AgentAttache agent = findAttache(hostId);
        if (agent == null) {
            s_logger.debug("Unable to find agent for " + hostId);
        }

        return agent;
    }

    @Override
    public long send(final Long hostId, final Command[] cmds, final boolean stopOnError, final Listener listener) throws AgentUnavailableException {
        final AgentAttache agent = getAttache(hostId);
        if (agent.isClosed()) {
            return -1;
        }

        assert cmds.length > 0 : "Why are you sending zero length commands?";
        if (cmds.length == 0) {
            return -1;
        }
        long seq = _hostDao.getNextSequence(hostId);
        Request req = new Request(seq, hostId, _nodeId, cmds, stopOnError, true);
        agent.send(req, listener);
        return seq;
    }

    @Override
    public long gatherStats(final Long hostId, final Command cmd, final Listener listener) {
        final Command[] cmds = new Command[] { cmd };
        try {
            return send(hostId, cmds, true, listener);
        } catch (final AgentUnavailableException e) {
            return -1;
        }
    }

    @Override
    public void disconnect(final long hostId, final Status.Event event, final boolean investigate) {
        AgentAttache attache = _agents.get(hostId);

        if (attache != null) {
            disconnect(attache, event, investigate);
        }
    }

    public void disconnect(AgentAttache attache, final Status.Event event, final boolean investigate) {
        _executor.submit(new DisconnectTask(attache, event, investigate));
    }

    protected boolean handleDisconnect(AgentAttache attache, Status.Event event, boolean investigate) {
        long hostId = attache.getId();

        s_logger.info("Host " + hostId + " is disconnecting with event " + event.toString());

        HostVO host = _hostDao.findById(hostId);
        if (host == null) {
            s_logger.warn("Can't find host with " + hostId);
            return false;
        }

        final Status currentState = host.getStatus();
        if (currentState == Status.Down || currentState == Status.Alert) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Host " + hostId + " is already " + currentState.toString());
            }
            return false;
        }

        Status nextState = currentState.getNextStatus(event);
        if (nextState == null) {
            s_logger.debug("There is no transition from state " + currentState.toString() + " and event " + event.toString());
            assert false : "How did we get here.  Look at the FSM";
            return false;
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("The next state is " + nextState.toString() + " current state is " + currentState);
        }

        // Now we go and correctly diagnose what the actual situation is
        if (nextState == Status.Alert && investigate) {
            s_logger.info("Investigating why host " + hostId + " has disconnected with event " + event.toString());

            final Status determinedState = investigate(attache);
            s_logger.info("The state determined is " + (determinedState != null ? determinedState.toString() : "undeterminable"));

            if (determinedState == null || determinedState == Status.Down) {
                s_logger.error("Host is down: " + host.getId() + "-" + host.getName() + ".  Starting HA on the VMs");

                event = Event.HostDown;
            } else if (determinedState == Status.Up) {
                // we effectively pinged from the server here.
                s_logger.info("Agent is determined to be up and running");
                _hostDao.updateStatus(host, Event.Ping, _nodeId);
                return false;
            } else if (determinedState == Status.Disconnected) {
                s_logger.warn("Agent is disconnected but the host is still up: " + host.getId() + "-" + host.getName());
                if (currentState == Status.Disconnected) {
                    if (((System.currentTimeMillis() >> 10) - host.getLastPinged()) > _alertWait) {
                        s_logger.warn("Host " + host.getId() + " has been disconnected pass the time it should be disconnected.");
                        event = Event.WaitedTooLong;
                    } else {
                        s_logger.debug("Host has been determined to be disconnected but it hasn't passed the wait time yet.");
                        return false;
                    }
                } else if (currentState == Status.Updating) {
                    if (((System.currentTimeMillis() >> 10) - host.getLastPinged()) > _updateWait) {
                        s_logger.warn("Host " + host.getId() + " has been updating for too long");

                        event = Event.WaitedTooLong;
                    } else {
                        s_logger.debug("Host has been determined to be disconnected but it hasn't passed the wait time yet.");
                        return false;
                    }
                } else if (currentState == Status.Up) {
                    DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                    HostPodVO podVO = _podDao.findById(host.getPodId());
                    String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: "
                            + podVO.getName();
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Host disconnected, " + hostDesc,
                            "If the agent for host [" + hostDesc + "] is not restarted within " + _alertWait + " seconds, HA will begin on the VMs");
                    event = Event.AgentDisconnected;
                }
            } else {
                // if we end up here we are in alert state, send an alert
                DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                HostPodVO podVO = _podDao.findById(host.getPodId());
                String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, host.getDataCenterId(), host.getPodId(), "Host in ALERT state, " + hostDesc,
                        "In availability zone " + host.getDataCenterId() + ", host is in alert state: " + host.getId() + "-" + host.getName());
            }
        }

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Deregistering link for " + hostId + " with state " + nextState);
        }
        synchronized (_agents) {
            AgentAttache removed = _agents.remove(hostId);
            if (removed != null && removed != attache) { // NOTE: == is
                // intentionally used
                // here.
                _agents.put(removed.getId(), removed);
            } else {
                _hostDao.disconnect(host, event, _nodeId);
                host = _hostDao.findById(host.getId());
                if (host.getStatus() == Status.Alert || host.getStatus() == Status.Down) {
                    _haMgr.scheduleRestartForVmsOnHost(host);
                }
            }
        }

        attache.disconnect(nextState);

        for (final Listener monitor : _hostMonitors.values()) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending Disconnect to listener: " + monitor.getClass().getName());
            }
            monitor.processDisconnect(hostId, nextState);
        }

        return true;
    }

    protected void notifyMonitorsOfConnection(final long hostId, final StartupCommand[] cmd) {
        HostVO host = _hostDao.findById(hostId);
        for (final Listener monitor : _hostMonitors.values()) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Sending Connect to listener: " + monitor.getClass().getSimpleName());
            }
            for (int i = 0; i < cmd.length; i++) {
                if (!monitor.processConnect(hostId, cmd[i])) {
                    s_logger.info("Monitor " + monitor.getClass().getSimpleName() + " says not to continue the connect process for " + hostId);
                    _hostDao.updateStatus(host, Event.AgentDisconnected, _nodeId);
                }
            }
        }
        
        _hostDao.updateStatus(host, Event.Ready, _nodeId);
    }

    @Override
    public boolean start() {
        startDirectlyConnectedHosts();
        if (_monitor != null) {
            _monitor.start();
        }
        _connection.start();

        return true;
    }

    public void startDirectlyConnectedHosts() {
        List<HostVO> hosts = _hostDao.findDirectlyConnectedHosts();
        for (HostVO host : hosts) {
        	loadDirectlyConnectedHost(host);
        }
    }
    
    protected void loadDirectlyConnectedHost(HostVO host) {
        String resourceName = host.getResource();
        ServerResource resource = null;
        try {
            Class<?> clazz = Class.forName(resourceName);
            Constructor constructor = clazz.getConstructor();
            resource = (ServerResource) constructor.newInstance();
        } catch (ClassNotFoundException e) {
            s_logger.warn("Unable to find class " + host.getResource(), e);
            return;
        } catch (InstantiationException e) {
            s_logger.warn("Unablet to instantiate class " + host.getResource(), e);
            return;
        } catch (IllegalAccessException e) {
            s_logger.warn("Illegal access " + host.getResource(), e);
            return;
        } catch (SecurityException e) {
            s_logger.warn("Security error on " + host.getResource(), e);
            return;
        } catch (NoSuchMethodException e) {
            s_logger.warn("NoSuchMethodException error on " + host.getResource(), e);
            return;
        } catch (IllegalArgumentException e) {
            s_logger.warn("IllegalArgumentException error on " + host.getResource(), e);
            return;
        } catch (InvocationTargetException e) {
            s_logger.warn("InvocationTargetException error on " + host.getResource(), e);
            return;
        }

        _hostDao.loadDetails(host);

        HashMap<String, Object> params = new HashMap<String, Object>(host.getDetails().size() + 5);
        params.putAll(host.getDetails());
        params.put("guid", host.getGuid());
        params.put("zone", Long.toString(host.getDataCenterId()));
        if (host.getPodId() != null) {
            params.put("pod", Long.toString(host.getPodId()));
        }
        params.put(Config.Wait.toString().toLowerCase(), Integer.toString(_wait));
        params.put("secondary.storage.vm", "false");
        
        try {
            resource.configure(host.getName(), params);
        } catch (ConfigurationException e) {
            s_logger.warn("Unable to configure resource due to ", e);
            return;
        }
        
        if (!resource.start()) {
            s_logger.warn("Unable to start the resource");
            return;
        }

        _executor.execute(new SimulateStartTask(host.getId(), resource, host.getDetails()));
    }

    protected AgentAttache simulateStart(ServerResource resource, Map<String, String> details, boolean old) {
        StartupCommand[] cmds = resource.initialize();
        AgentAttache attache = null;
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Startup request from directly connected host: " + new Request(0, -1, -1, cmds[0], false).toString());
        }
        try {
            attache = handleDirectConnect(resource, cmds, details, old);
        } catch (Exception e) {
            s_logger.warn("Unable to connect due to ", e);
        }

        if (attache == null) {
            resource.disconnected();
            return null;
        }

        StartupAnswer answer = new StartupAnswer(cmds[0], attache.getId(), _pingInterval);
        attache.process(new Answer[] { answer });
        return attache;
    }

    @Override
    public boolean stop() {
        if (_monitor != null) {
            _monitor.signalStop();
        }
        if (_connection != null) {
            _connection.stop();
        }

        s_logger.info("Disconnecting agents: " + _agents.size());
        synchronized (_agents) {
            for (final AgentAttache agent : _agents.values()) {
                final HostVO host = _hostDao.findById(agent.getId());
                _hostDao.updateStatus(host, Event.ManagementServerDown, _nodeId);
            }
        }
        return true;
    }

    @Override
    public HostPodVO findPod(final VirtualMachineTemplate template, ServiceOfferingVO offering, final DataCenterVO dc, final long accountId, Set<Long> avoids) {
        final Enumeration en = _podAllocators.enumeration();
        while (en.hasMoreElements()) {
            final PodAllocator allocator = (PodAllocator) en.nextElement();
            final HostPodVO pod = allocator.allocateTo(template, offering, dc, accountId, avoids);
            if (pod != null) {
                return pod;
            }
        }
        return null;
    }

    @Override
    public HostStats getHostStatistics(long hostId) throws InternalErrorException {
    	GetHostStatsCommand cmd = new GetHostStatsCommand();
		Answer answer = easySend(hostId, cmd);
		if (answer == null || !answer.getResult()) {
			throw new InternalErrorException("Unable to obtain host statistics.");
		} else {
			return (GetHostStatsAnswer) answer;
		}
    }
    
    public Long getGuestOSCategoryId(long hostId) {
    	HostVO host = _hostDao.findById(hostId);
    	if (host == null) {
    		return null;
    	} else {
    		_hostDao.loadDetails(host);
    		DetailVO detail = _hostDetailsDao.findDetail(hostId, "guest.os.category.id");
    		if (detail == null) {
    			return null;
    		} else {
    			return Long.parseLong(detail.getValue());
    		}
    	}
    }

    @Override
    public String getName() {
        return _name;
    }

    protected class DisconnectTask implements Runnable {
        AgentAttache _attache;
        Status.Event _event;
        boolean _investigate;

        DisconnectTask(final AgentAttache attache, final Status.Event event, final boolean investigate) {
            _attache = attache;
            _event = event;
            _investigate = investigate;
        }

        public void run() {
            try {
                handleDisconnect(_attache, _event, _investigate);
            } catch (final Exception e) {
                s_logger.error("Exception caught while handling disconnect: ", e);
            }
        }
    }

    @Override
    public Answer easySend(final Long hostId, final Command cmd) {
        try {
            final Answer answer = send(hostId, cmd, _wait);
            if (answer == null) {
                s_logger.warn("send returns null answer");
                return null;
            }

            if (!answer.getResult()) {
                s_logger.warn("Unable to execute command: " + cmd.toString() + " due to " + answer.getDetails());
                return null;
            }

            if (s_logger.isDebugEnabled() && answer.getDetails() != null) {
                s_logger.debug("Details from executing " + cmd.getClass().toString() + ": " + answer.getDetails());
            }

            return answer;

        } catch (final AgentUnavailableException e) {
            s_logger.warn(e.getMessage());
            return null;
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Operation timed out: " + e.getMessage());
            return null;
        } catch (final Exception e) {
            s_logger.warn("Exception while sending", e);
            return null;
        }
    }

    @Override
    public Answer send(final Long hostId, final Command cmd) throws AgentUnavailableException, OperationTimedoutException {
        return send(hostId, cmd, _wait);
    }

    @Override
    public Answer[] send(final Long hostId, final Command[] cmds, final boolean stopOnError) throws AgentUnavailableException, OperationTimedoutException {
        return send(hostId, cmds, stopOnError, _wait);
    }

    @Override
    public boolean reconnect(final long hostId) throws AgentUnavailableException {
        HostVO host;

        host = _hostDao.findById(hostId);
        if (host == null || host.getRemoved() != null) {
            s_logger.warn("Unable to find host " + hostId);
            return false;
        }

        if (host.getStatus() != Status.Up && host.getStatus() != Status.Alert) {
            s_logger.info("Unable to disconnect host because it is not in the correct state: host=" + hostId + "; Status=" + host.getStatus());
            return false;
        }

        AgentAttache attache = findAttache(hostId);
        if (attache == null) {
            s_logger.info("Unable to disconnect host because it is not connected to this server: " + hostId);
            return false;
        }

        disconnect(attache, Event.ShutdownRequested, false);
        return true;
    }

    @Override
    public boolean cancelMaintenance(final long hostId) {

        HostVO host;
        host = _hostDao.findById(hostId);
        if (host == null || host.getRemoved() != null) {
            s_logger.warn("Unable to find host " + hostId);
            return true;
        }

        if (host.getStatus() != Status.PrepareForMaintenance && host.getStatus() != Status.Maintenance && host.getStatus() != Status.ErrorInMaintenance) {
            return true;
        }

        _haMgr.cancelScheduledMigrations(host);
        List<VMInstanceVO> vms = _haMgr.findTakenMigrationWork();
        for (VMInstanceVO vm : vms) {
            if (vm.getHostId() != null && vm.getHostId() == hostId) {
                s_logger.info("Unable to cancel migration because the vm is being migrated: " + vm.toString());
                return false;
            }
        }
        disconnect(hostId, Event.ResetRequested, false);
        return true;
    }

    @Override
    public boolean executeUserRequest(long hostId, Event event) throws AgentUnavailableException {
        if (event == Event.MaintenanceRequested) {
            return maintain(hostId);
        } else if (event == Event.ResetRequested) {
            return cancelMaintenance(hostId);
        } else if (event == Event.Remove) {
            return deleteHost(hostId);
        } else if (event == Event.AgentDisconnected) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Received agent disconnect event for host " + hostId);
            }
            AgentAttache attache = null;
            synchronized (_agents) {
                attache = _agents.get(hostId);
            }
            if (attache != null) {
                handleDisconnect(attache, Event.AgentDisconnected, false);
            }

            return true;
        } else if (event == Event.ShutdownRequested) {
            return reconnect(hostId);
        }
        return false;
    }

    @Override
    public boolean maintain(final long hostId) throws AgentUnavailableException {
        HostVO host;
        Status state;

        if (_hostDao.listByStatus(Status.PrepareForMaintenance).size() > 0) {
            s_logger.debug("Unable to put the server into maintenance because there are other servers in that mode.");
            return false;
        }

        // Let's put this guy in maintenance state
        do {
            host = _hostDao.findById(hostId);
            if (host == null) {
                s_logger.debug("Unable to find host " + hostId);
                return false;
            }
            state = host.getStatus();
            if (state == Status.Disconnected || state == Status.Updating) {
                s_logger.debug("Unable to put host " + hostId + " in matinenance mode because it is currently in " + state.toString());
                throw new AgentUnavailableException("Agent is in " + state.toString() + " state.  Please wait for it to become Alert state try again.", hostId);
            }
        } while (!_hostDao.updateStatus(host, Event.MaintenanceRequested, _nodeId));

        AgentAttache attache;
        synchronized (_agents) {
            attache = _agents.get(hostId);
            if (attache != null) {
                attache.setMaintenanceMode(true);
            }
        }

        if (attache != null) {
            // Now cancel all of the commands except for the active one.
            attache.cancelAllCommands(Status.PrepareForMaintenance, false);
        }

        final Host.Type type = host.getType();

        if (type == Host.Type.Routing || type == Host.Type.Computing) {
            final List<VMInstanceVO> vms = _vmDao.listByHostId(hostId);
            if (vms.size() == 0) {
                return true;
            }

            for (final VMInstanceVO vm : vms) {
                _haMgr.scheduleMigration(vm);
            }
        } else {
            final List<Long> ids = _volDao.findVmsStoredOnHost(hostId);
            for (final Long id : ids) {
                final VMInstanceVO instance = _vmDao.findById(id);
                if (instance != null && (instance.getState() == State.Running || instance.getState() == State.Starting)) {
                    _haMgr.scheduleStop(instance, host.getId(), false);
                }
            }
        }

        return true;
    }

    public boolean checkCIDR(Host.Type type, HostPodVO pod,  String serverPrivateIP, String serverPrivateNetmask) {
        // Get the CIDR address and CIDR size
        String cidrAddress = pod.getCidrAddress();
        long cidrSize = pod.getCidrSize();

        // If the server's private IP address is not in the same subnet as the
        // pod's CIDR, return false
        String cidrSubnet = NetUtils.getCidrSubNet(cidrAddress, cidrSize);
        String serverSubnet = NetUtils.getSubNet(serverPrivateIP, serverPrivateNetmask);
        if (!cidrSubnet.equals(serverSubnet)) {
            return false;
        }

        // If the server's private netmask is less inclusive than the pod's CIDR
        // netmask, return false
        String cidrNetmask = NetUtils.getCidrSubNet("255.255.255.255", cidrSize);
        long cidrNetmaskNumeric = NetUtils.ip2Long(cidrNetmask);
        long serverNetmaskNumeric = NetUtils.ip2Long(serverPrivateNetmask);
        if (serverNetmaskNumeric > cidrNetmaskNumeric) {
            return false;
        }
        return true;
    }
    public void checkCIDR(Host.Type type, HostPodVO pod, DataCenterVO dc, String serverPrivateIP, String serverPrivateNetmask) {
        // Skip this check for Storage Agents and Console Proxies
        if (type == Host.Type.Storage || type == Host.Type.ConsoleProxy)
            return;

        // Get the CIDR address and CIDR size
        String cidrAddress = pod.getCidrAddress();
        long cidrSize = pod.getCidrSize();

        // If the server's private IP address is not in the same subnet as the
        // pod's CIDR, return false
        String cidrSubnet = NetUtils.getCidrSubNet(cidrAddress, cidrSize);
        String serverSubnet = NetUtils.getSubNet(serverPrivateIP, serverPrivateNetmask);
        if (!cidrSubnet.equals(serverSubnet)) {
            throw new IllegalArgumentException("The private ip address of the server (" + serverPrivateIP + ") is not compatible with the CIDR of pod: "
                    + pod.getName() + " and zone: " + dc.getName());
        }

        // If the server's private netmask is less inclusive than the pod's CIDR
        // netmask, return false
        String cidrNetmask = NetUtils.getCidrSubNet("255.255.255.255", cidrSize);
        long cidrNetmaskNumeric = NetUtils.ip2Long(cidrNetmask);
        long serverNetmaskNumeric = NetUtils.ip2Long(serverPrivateNetmask);
        if (serverNetmaskNumeric > cidrNetmaskNumeric) {
            throw new IllegalArgumentException("The private ip address of the server (" + serverPrivateIP + ") is not compatible with the CIDR of pod: "
                    + pod.getName() + " and zone: " + dc.getName());
        }

    }

    public void checkIPConflicts(Host.Type type, HostPodVO pod, DataCenterVO dc, String serverPrivateIP, String serverPrivateNetmask, String serverPublicIP,
            String serverPublicNetmask) {
        // If the server's private IP is the same as is public IP, this host has
        // a host-only private network. Don't check for conflicts with the
        // private IP address table.
        if (serverPrivateIP != serverPublicIP) {
            if (!_privateIPAddressDao.mark(dc.getId(), pod.getId(), serverPrivateIP)) {
                // If the server's private IP address is already in the
                // database, return false
                List<DataCenterIpAddressVO> existingPrivateIPs = _privateIPAddressDao.listByPodIdDcIdIpAddress(pod.getId(), dc.getId(), serverPrivateIP);

                assert existingPrivateIPs.size() <= 1 : " How can we get more than one ip address with " + serverPrivateIP;
                if (existingPrivateIPs.size() > 1) {
                    throw new IllegalArgumentException("The private ip address of the server (" + serverPrivateIP + ") is already in use in pod: "
                            + pod.getName() + " and zone: " + dc.getName());
                }
                if (existingPrivateIPs.size() == 1) {
                    DataCenterIpAddressVO vo = existingPrivateIPs.get(0);
                    if (vo.getInstanceId() != null) {
                        throw new IllegalArgumentException("The private ip address of the server (" + serverPrivateIP + ") is already in use in pod: "
                                + pod.getName() + " and zone: " + dc.getName());
                    }
                }
            }
        }

        if (serverPublicIP != null && !_publicIPAddressDao.mark(dc.getId(), serverPublicIP)) {
            // If the server's public IP address is already in the database,
            // return false
            List<IPAddressVO> existingPublicIPs = _publicIPAddressDao.listByDcIdIpAddress(dc.getId(), serverPublicIP);
            if (existingPublicIPs.size() > 0) {
                throw new IllegalArgumentException("The public ip address of the server (" + serverPublicIP + ") is already in use in zone: " + dc.getName());
            }
        }
    }

    public HostVO createHost(final StartupCommand startup, ServerResource resource, Map<String, String> details, boolean directFirst) {
        Host.Type type = null;

        if (startup instanceof StartupStorageCommand) {

            StartupStorageCommand ssCmd = ((StartupStorageCommand) startup);
            if (ssCmd.getResourceType() == StorageResourceType.SECONDARY_STORAGE) {
                type = Host.Type.SecondaryStorage;
                if (resource != null && resource instanceof DummySecondaryStorageResource){
                	resource = null;
                }
            } else {
                type = Host.Type.Storage;
            }
            final Map<String, String> hostDetails = ssCmd.getHostDetails();
            if (hostDetails != null) {
                if (details != null) {
                    details.putAll(hostDetails);
                } else {
                    details = hostDetails;
                }
            }
        } else if (startup instanceof StartupRoutingCommand) {
            StartupRoutingCommand ssCmd = ((StartupRoutingCommand) startup);
            type = Host.Type.Routing;
            final Map<String, String> hostDetails = ssCmd.getHostDetails();
            if (hostDetails != null) {
                if (details != null) {
                    details.putAll(hostDetails);
                } else {
                    details = hostDetails;
                }
            }
        } else if (startup instanceof StartupProxyCommand) {
            type = Host.Type.ConsoleProxy;
        } else if (startup instanceof StartupComputingCommand) {
            type = Host.Type.Computing;
        } else {
            assert false : "Did someone add a new Startup command?";
        }

        Long id = null;
        HostVO server = _hostDao.findByGuid(startup.getGuid());
        if (server == null) {
        	server = _hostDao.findByGuid(startup.getGuidWithoutResource());
        }
        if (server != null && server.getRemoved() == null) {
            id = server.getId();
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Found the host " + id + " by guid: " + startup.getGuid());
            }
            if (directFirst) {
                s_logger.debug("Old host reconnected as new");
                return null;
            }
        } else {
            server = new HostVO(startup.getGuid());
        }

        server.setDetails(details);

        updateHost(server, startup, type, _nodeId);
        if (resource != null) {
            server.setResource(resource.getClass().getName());
        }
        if (id == null) {
            /*
             * // ignore integrity check for agent-simulator
             * if(!"0.0.0.0".equals(startup.getPrivateIpAddress()) &&
             * !"0.0.0.0".equals(startup.getStorageIpAddress())) { if
             * (_hostDao.findByPrivateIpAddressInDataCenter
             * (server.getDataCenterId(), startup.getPrivateIpAddress()) !=
             * null) { throw newIllegalArgumentException(
             * "The private ip address is already in used: " +
             * startup.getPrivateIpAddress()); }
             * 
             * if
             * (_hostDao.findByPrivateIpAddressInDataCenter(server.getDataCenterId
             * (), startup.getStorageIpAddress()) != null) { throw new
             * IllegalArgumentException
             * ("The private ip address is already in used: " +
             * startup.getStorageIpAddress()); } }
             */

            if (startup instanceof StartupStorageCommand) {
                id = _hostDao.persist(server);
            } else if (startup instanceof StartupProxyCommand) {
                server.setProxyPort(((StartupProxyCommand) startup).getProxyPort());
                id = _hostDao.persist(server);
            } else if (startup instanceof StartupRoutingCommand) {
                server.setProxyPort(((StartupRoutingCommand) startup).getProxyPort());
                id = _hostDao.persist(server);
            } else if (startup instanceof StartupComputingCommand) {
                server.setProxyPort(((StartupComputingCommand) startup).getProxyPort());
                id = _hostDao.persist(server);
            }

            s_logger.info("New " + server.getType().toString() + " host connected w/ guid " + startup.getGuid() + " and id is " + id);
        } else {
            if (!_hostDao.connect(server, _nodeId)) {
                throw new VmopsRuntimeException("Agent cannot connect because the current state is " + server.getStatus().toString());
            }
            s_logger.info("Old " + server.getType().toString() + " host reconnected w/ id =" + id);
        }
        createCapacityEntry(startup, server);

        return server;
    }


    
    protected void createPVTemplate(long Hostid, StartupStorageCommand cmd) {
    	Map<String, TemplateInfo> tmplts = cmd.getTemplateInfo();
        if ((tmplts != null) && !tmplts.isEmpty()) {
        	TemplateInfo xenPVISO = tmplts.get("xs-tools");
        	if (xenPVISO != null) {
        		VMTemplateVO tmplt = _tmpltDao.findByName(xenPVISO.getTemplateName());
    			Long id;
    			if (tmplt == null) {
    				 id = _tmpltDao.getNextInSequence(Long.class, "id");
    				VMTemplateVO template = new VMTemplateVO(id, xenPVISO.getTemplateName(), xenPVISO.getTemplateName(), ImageFormat.ISO , true, true, FileSystem.cdfs.toString(), "/opt/xensource/packages/iso/xs-tools-5.5.0.iso", null, true, 64, Account.ACCOUNT_ID_SYSTEM, null, "xen-pv-drv-iso", false, 1, false);
    				template.setCreateStatus(AsyncInstanceCreateStatus.Created);
    				template.setReady(true);
    				_tmpltDao.persist(template);
    			} else {
    				id = _tmpltDao.findByName(xenPVISO.getTemplateName()).getId();
    			}
    			
    			VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(Hostid, id);
    			
    			if (tmpltHost == null) {
    				VMTemplateHostVO vmTemplateHost = new VMTemplateHostVO(Hostid, id, new Date(), 100, VMTemplateStorageResourceAssoc.Status.DOWNLOADED, null, null, null, "iso/users/2/xs-tools");
    				_vmTemplateHostDao.persist(vmTemplateHost);
    			}
        	}
        }
    }
    
    public HostVO createHost(final StartupCommand[] startup, ServerResource resource, Map<String, String> details, boolean directFirst) {
        StartupCommand firstCmd = startup[0];
        HostVO result = createHost(firstCmd, resource, details, directFirst);
        if( result == null ) {
            return null;
        }
        if (startup.length > 1) {
            for (int i = 1; i < startup.length; i++) {
                if (startup[i] instanceof StartupStorageCommand) {
                    StartupStorageCommand ssCmd = (StartupStorageCommand) startup[i];
                    createStoragePools(ssCmd, result);
                    createPVTemplate(result.getId(), ssCmd);
                }
            }
        }
        return result;

    }

    private void createStoragePools(StartupStorageCommand ssCmd, HostVO host) {
        if (ssCmd.getPoolInfo() != null) {
            StoragePoolInfo pInfo = ssCmd.getPoolInfo();
            if (pInfo == null) {
                return;
            }
            StoragePoolVO pool = _storagePoolDao.findPoolByHostPath(host.getDataCenterId(), host.getPodId(), pInfo.getHost(), pInfo.getHostPath());
            if (pool == null) {
                long poolId = _storagePoolDao.getNextInSequence(Long.class, "id");
                pool = new StoragePoolVO(poolId, host.getName() + " Local Storage", pInfo.getUuid(), pInfo.getPoolType(), host.getDataCenterId(), host
                        .getPodId(), pInfo.getAvailableBytes(), pInfo.getCapacityBytes(), pInfo.getHost(), 0, pInfo.getHostPath());
                _storagePoolDao.persist(pool);
                StoragePoolHostVO poolHost = new StoragePoolHostVO(pool.getId(), host.getId(), pInfo.getLocalPath());
                _storagePoolHostDao.persist(poolHost);
            } else {

                StoragePoolHostVO poolHost = _storagePoolHostDao.findByPoolHost(pool.getId(), host.getId());
                if (poolHost == null) {
                    poolHost = new StoragePoolHostVO(pool.getId(), host.getId(), pInfo.getLocalPath());
                    _storagePoolHostDao.persist(poolHost);
                }
                pool.setAvailableBytes(pInfo.getAvailableBytes());
                pool.setCapacityBytes(pInfo.getCapacityBytes());
                _storagePoolDao.update(pool.getId(), pool);
            }
        }
    }

    public AgentAttache handleConnect(final Link link, final StartupCommand[] startup) throws IllegalArgumentException {
        HostVO server = createHost(startup, null, null, false);
        if ( server == null ) {
            return null;
        }
        long id = server.getId();

        AgentAttache attache = createAttache(id, server, link);

        notifyMonitorsOfConnection(id, startup);

        /*
         * // If the host that connected is a Routing or Computing Server, and
         * there is an external firewall configured for the VLAN of the server's
         * public IP, open port 443 for Console Proxy if ((startup instanceof
         * StartupRoutingCommand) || (startup instanceof
         * StartupComputingCommand)) {
         * 
         * String ipAddress = startup.getPublicIpAddress();
         * 
         * 
         * 
         * 
         * s_logger.warn("Routing or Computing Server connected with ipAddress = "
         * + ipAddress + ". Will open TCP port 443 if necessary.");
         * 
         * IPAddressVO ip = _publicIPAddressDao.findById(ipAddress); VlanVO vlan
         * = _vlanDao.findById(new Long(ip.getVlanDbId()));
         * 
         * String firewallIpAddress = vlan.getFirewallIpAddress(); String
         * firewallUser = vlan.getFirewallUser(); String firewallPassword =
         * vlan.getFirewallPassword(); String firewallEnablePassword =
         * vlan.getFirewallEnablePassword(); firewallIpAddress =
         * vlan.getFirewallIpAddress();
         * 
         * if (firewallIpAddress != null) {s_logger.debug(
         * "Routing or Computing Server connected, opening TCP port 443 on external firewall."
         * );
         * 
         * FirewallRuleVO fwRule = new FirewallRuleVO();
         * fwRule.setPublicIpAddress(startup.getPublicIpAddress());
         * fwRule.setPublicPort("443"); fwRule.setProtocol("tcp");
         * fwRule.setEnabled(true); fwRule.setForwarding(true);
         * SetFirewallRuleCommand cmd = new SetFirewallRuleCommand(null, null,
         * fwRule, firewallIpAddress, firewallUser, firewallPassword,
         * firewallEnablePassword); easySend(id, cmd); } else {s_logger.debug(
         * "Routing or Computing Server connected, but not opening TCP port 443 on external firewall, since it was not configured for the VLAN of the server's public IP."
         * ); } }
         */

        return attache;
    }

    public AgentAttache findAgent(long hostId) {
        synchronized (_agents) {
            return _agents.get(hostId);
        }
    }

    protected AgentAttache createAttache(long id, HostVO server, Link link) {
        s_logger.debug("Adding link for " + id);
        final AgentAttache attache = new ConnectedAgentAttache(id, link, server.getStatus() == Status.Maintenance
                || server.getStatus() == Status.ErrorInMaintenance || server.getStatus() == Status.PrepareForMaintenance);
        link.attach(attache);
        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.get(id);
            _agents.put(id, attache);
        }

        if (old != null) {
            assert (!attache.equals(old)) : "How can same links be put into two different attaches: " + id;
            s_logger.debug("Discovered an old link.  We are disconnecting it since we have a new one.");
            _executor.submit(new DisconnectTask(old, Event.AgentDisconnected, false));
        }

        return attache;
    }

    protected AgentAttache createAttache(long id, HostVO server, ServerResource resource) {
        s_logger.debug("Adding directly connect host for " + id);
        if (resource instanceof DummySecondaryStorageResource) {
        	return new DummyAttache(id, false);
        }
        final DirectAgentAttache attache = new DirectAgentAttache(id, resource, server.getStatus() == Status.Maintenance
                || server.getStatus() == Status.ErrorInMaintenance || server.getStatus() == Status.PrepareForMaintenance, this);
        AgentAttache old = null;
        synchronized (_agents) {
            old = _agents.get(id);
            _agents.put(id, attache);
        }

        if (old != null) {
            assert (!attache.equals(old)) : "How can same links be put into two different attaches: " + id;
            s_logger.debug("Discovered an old link.  We are disconnecting it since we have a new one.");
            _executor.submit(new DisconnectTask(old, Event.AgentDisconnected, false));
        }

        return attache;
    }

    @Override
    public boolean maintenanceFailed(long hostId) {
        HostVO host = _hostDao.findById(hostId);
        return _hostDao.updateStatus(host, Event.UnableToMigrate, _nodeId);
    }
    
    public void updateHost(long hostId, long guestOSCategoryId) {
    	GuestOSCategoryVO guestOSCategory = _guestOSCategoryDao.findById(guestOSCategoryId);
    	Map<String, String> hostDetails = _hostDetailsDao.findDetails(hostId);
    	
    	if (guestOSCategory != null) {
    		// Save a new entry for guest.os.category.id
    		hostDetails.put("guest.os.category.id", String.valueOf(guestOSCategory.getId()));
    	} else {
    		// Delete any existing entry for guest.os.category.id
    		hostDetails.remove("guest.os.category.id");
    	}
    	
    	_hostDetailsDao.persist(hostId, hostDetails);
    }

    protected void updateHost(final HostVO host, final StartupCommand startup, final Host.Type type, final long msId) throws IllegalArgumentException {
        s_logger.debug("updateHost() called");

        String dataCenter = startup.getDataCenter();
        String pod = startup.getPod();
        
        if (pod != null && dataCenter != null && pod.equalsIgnoreCase("default") && dataCenter.equalsIgnoreCase("default")) {
        	List<HostPodVO> pods = _podDao.listAll();
        	for (HostPodVO hpv : pods) {
        		if (checkCIDR(type, hpv, startup.getPrivateIpAddress(), startup.getPrivateNetmask())) {
        			pod = hpv.getName();
        			dataCenter = _dcDao.findById(hpv.getDataCenterId()).getName();
        			break;
        		}
        	}
        }
        long dcId = -1;
        DataCenterVO dc = _dcDao.findByName(dataCenter);
        if (dc == null) {
            try {
                dcId = Long.parseLong(dataCenter);
                dc = _dcDao.findById(dcId);
            } catch (final NumberFormatException e) {
            }
        }
        if (dc == null) {
            throw new IllegalArgumentException("Host " + startup.getPrivateIpAddress() + " sent incorrect data center: " + dataCenter);
        }
        dcId = dc.getId();

        HostPodVO p = _podDao.findByName(pod, dcId);
        if (p == null) {
            try {
                final long podId = Long.parseLong(pod);
                p = _podDao.findById(podId);
            } catch (final NumberFormatException e) {
            }
        }
        Long podId = null;
        if (p == null) {
            if (type != Host.Type.SecondaryStorage) {

                /*
                 * s_logger.info("Unable to find the pod so we are creating one."
                 * ); p = createPod(pod, dcId, startup.getPrivateIpAddress(),
                 * NetUtils.getCidrSize(startup.getPrivateNetmask())); podId =
                 * p.getId();
                 */
                s_logger.error("Host " + startup.getPrivateIpAddress() + " sent incorrect pod: " + pod + " in " + dataCenter);
                throw new IllegalArgumentException("Host " + startup.getPrivateIpAddress() + " sent incorrect pod: " + pod + " in " + dataCenter);
            }
        } else {
            podId = p.getId();
        }

        if (type == Host.Type.Computing || type == Host.Type.Routing) {
            StartupComputingCommand scc = (StartupComputingCommand) startup;
            Host.HypervisorType hypervisorType = scc.getHypervisorType();
            boolean doCidrCheck = true;

            // If this command is from the agent simulator, don't do the CIDR
            // check
            if (scc.getAgentTag() != null && startup.getAgentTag().equalsIgnoreCase("vmops-simulator"))
                doCidrCheck = false;

            // If this command is from a KVM agent, or from an agent that has a
            // null hypervisor type, don't do the CIDR check
            if (hypervisorType == null || hypervisorType == Host.HypervisorType.KVM)
                doCidrCheck = false;

            if (doCidrCheck)
                s_logger.info("Host: " + host.getName() + " connected with hypervisor type: " + hypervisorType + ". Checking CIDR...");
            else
                s_logger.info("Host: " + host.getName() + " connected with hypervisor type: " + hypervisorType + ". Skipping CIDR check...");

            if (doCidrCheck)
                checkCIDR(type, p, dc, scc.getPrivateIpAddress(), scc.getPrivateNetmask());

            // Check if the private/public IPs of the server are already in the
            // private/public IP address tables
            checkIPConflicts(type, p, dc, scc.getPrivateIpAddress(), scc.getPublicIpAddress(), scc.getPublicIpAddress(), scc.getPublicNetmask());
        }

        host.setDataCenterId(dc.getId());
        host.setPodId(podId);

        host.setPrivateIpAddress(startup.getPrivateIpAddress());
        host.setPrivateNetmask(startup.getPrivateNetmask());
        host.setPrivateMacAddress(startup.getPrivateMacAddress());
        host.setPublicIpAddress(startup.getPublicIpAddress());
        host.setPublicMacAddress(startup.getPublicMacAddress());
        host.setPublicNetmask(startup.getPublicNetmask());
        host.setStorageIpAddress(startup.getStorageIpAddress());
        host.setStorageMacAddress(startup.getStorageMacAddress());
        host.setStorageNetmask(startup.getStorageNetmask());
        host.setVersion(startup.getVersion());
        host.setName(startup.getName());
        host.setType(type);
        host.setManagementServerId(msId);
        host.setStorageUrl(startup.getIqn());
        host.setLastPinged(System.currentTimeMillis() >> 10);
        if (startup instanceof StartupComputingCommand) {
            final StartupComputingCommand scc = (StartupComputingCommand) startup;
            host.setCaps(scc.getCapabilities());
            host.setCpus(scc.getCpus());
            host.setTotalMemory(scc.getMemory());
            host.setDom0MinMemory(scc.getDom0MinMemory());
            host.setSpeed(scc.getSpeed());
            HypervisorType hyType = scc.getHypervisorType();
            if (hyType == null) {
                host.setHypervisorType(HypervisorType.Xen);
            } else {
                host.setHypervisorType(hyType);
            }
        } else {
            final StartupStorageCommand ssc = (StartupStorageCommand) startup;
            host.setParent(ssc.getParent());
            host.setTotalSize(ssc.getTotalSize());
            host.setHypervisorType(HypervisorType.None);
            if (ssc.getNfsShare() != null) {
                host.setStorageUrl(ssc.getNfsShare());
            }
        }
        if (startup.getStorageIpAddressDeux() != null) {
            host.setStorageIpAddressDeux(startup.getStorageIpAddressDeux());
            host.setStorageMacAddressDeux(startup.getStorageMacAddressDeux());
            host.setStorageNetmaskDeux(startup.getStorageNetmaskDeux());
        }

    }

    // create capacity entries if none exist for this server
    private void createCapacityEntry(final StartupCommand startup, HostVO server) {
        SearchCriteria capacitySC = _capacityDao.createSearchCriteria();
        capacitySC.addAnd("hostOrPoolId", SearchCriteria.Op.EQ, server.getId());
        capacitySC.addAnd("dataCenterId", SearchCriteria.Op.EQ, server.getDataCenterId());
        capacitySC.addAnd("podId", SearchCriteria.Op.EQ, server.getPodId());
        List<CapacityVO> capacities = _capacityDao.search(capacitySC, null);

        // remove old entries, we'll recalculate them anyway
        if ((capacities != null) && !capacities.isEmpty()) {
            for (CapacityVO capacity : capacities) {
                _capacityDao.remove(capacity.getId());
            }
        }

        if (startup instanceof StartupStorageCommand) {
            StartupStorageCommand ssCmd = (StartupStorageCommand) startup;
            if (ssCmd.getResourceType() == StorageResourceType.STORAGE_HOST) {
                CapacityVO capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L, server.getTotalSize(),
                        CapacityVO.CAPACITY_TYPE_STORAGE);
                _capacityDao.persist(capacity);

                capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L, server.getTotalSize() * _overProvisioningFactor,
                        CapacityVO.CAPACITY_TYPE_STORAGE_ALLOCATED);
                _capacityDao.persist(capacity);
            }
        } else if (startup instanceof StartupRoutingCommand) {
            CapacityVO capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L,
                    (server.getTotalMemory().longValue() * 7L) / 8L, CapacityVO.CAPACITY_TYPE_MEMORY);
            _capacityDao.persist(capacity);

            capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L, (long)(server.getCpus().longValue()
                    * server.getSpeed().longValue()*_cpuOverProvisioningFactor), CapacityVO.CAPACITY_TYPE_CPU);
            _capacityDao.persist(capacity);
        } else if (startup instanceof StartupComputingCommand) {
            CapacityVO capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L,
                    (server.getTotalMemory().longValue() * 7L) / 8L, CapacityVO.CAPACITY_TYPE_MEMORY);
            _capacityDao.persist(capacity);

            capacity = new CapacityVO(server.getId(), server.getDataCenterId(), server.getPodId(), 0L, (long)(server.getCpus().longValue()
                    * server.getSpeed().longValue()*_cpuOverProvisioningFactor), CapacityVO.CAPACITY_TYPE_CPU);
            _capacityDao.persist(capacity);
        }
    }

    protected void upgradeAgent(final Link link, final byte[] request, final String reason) {

        if (reason == UnsupportedVersionException.IncompatibleVersion) {
            final UpgradeResponse response = new UpgradeResponse(request, _upgradeMgr.getAgentUrl());
            try {
                s_logger.info("Asking for the agent to update due to incompatible version: " + response.toString());
                link.send(response.toBytes());
            } catch (final ClosedChannelException e) {
                s_logger.warn("Unable to send response due to connection closed: " + response.toString());
            }
            return;
        }

        assert (reason == UnsupportedVersionException.UnknownVersion) : "Unknown reason: " + reason;
        final UpgradeResponse response = new UpgradeResponse(request, _upgradeMgr.getAgentUrl());
        try {
            s_logger.info("Asking for the agent to update due to unknown version: " + response.toString());
            link.send(response.toBytes());
        } catch (final ClosedChannelException e) {
            s_logger.warn("Unable to send response due to connection closed: " + response.toString());
        }
    }

    protected class SimulateStartTask implements Runnable {
        ServerResource resource;
        Map<String, String> details;
        long id;

        public SimulateStartTask(long id, ServerResource resource, Map<String, String> details) {
            this.id = id;
            this.resource = resource;
            this.details = details;
        }

        public void run() {
            try {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Simulating start for resource " + resource.getName() + " id " + id);
                }
                simulateStart(resource, details, false);
            } catch (Exception e) {
                s_logger.warn("Unable to simulate start on resource " + id + " name " + resource.getName(), e);
            }
        }
    }

    public class AgentHandler extends Task {
        public AgentHandler(Task.Type type, Link link, byte[] data) {
            super(type, link, data);
        }

        protected void processRequest(final Link link, final Request request) {
            AgentAttache attache = (AgentAttache) link.attachment();
            final Command[] cmds = request.getCommands();
            Command cmd = cmds[0];
            boolean logD = true;

            Response response = null;
            if (attache == null) {
                s_logger.debug("Processing sequence " + request.getSequence() + ": Processing " + request.toString());
                if (!(cmd instanceof StartupCommand)) {
                    s_logger.warn("Throwing away a request because it came through as the first command on a connect: " + request.toString());
                    return;
                }
                StartupCommand startup = (StartupCommand) cmd;
                if ((_upgradeMgr.registerForUpgrade(-1, startup.getVersion()) == UpgradeManager.State.RequiresUpdate) && (_upgradeMgr.getAgentUrl() != null)) {
                    final UpgradeCommand upgrade = new UpgradeCommand(_upgradeMgr.getAgentUrl());
                    final Request req = new Request(1, -1, -1, new Command[] { upgrade }, true, true);
                    s_logger.info("Agent requires upgrade: " + req.toString());
                    try {
                        link.send(req.toBytes());
                    } catch (ClosedChannelException e) {
                        s_logger.warn("Unable to tell agent it should update.");
                    }
                    return;
                }
                try {
                    StartupCommand[] startups = new StartupCommand[cmds.length];
                    for (int i = 0; i < cmds.length; i++)
                        startups[i] = (StartupCommand) cmds[i];
                    attache = handleConnect(link, startups);
                } catch (final IllegalArgumentException e) {
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, 0, new Long(0), "Agent from " + startup.getPrivateIpAddress()
                            + " is unable to connect due to " + e.getMessage(), "Agent from " + startup.getPrivateIpAddress() + " is unable to connect with "
                            + request.toString() + " because of " + e.getMessage());
                    s_logger.warn("Unable to create attache for agent: " + request.toString(), e);
                    response = new Response(request, new StartupAnswer((StartupCommand) cmd, e.getMessage()), _nodeId, -1);
                } catch (final VmopsRuntimeException e) {
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, 0, new Long(0), "Agent from " + startup.getPrivateIpAddress()
                            + " is unable to connect due to " + e.getMessage(), "Agent from " + startup.getPrivateIpAddress() + " is unable to connect with "
                            + request.toString() + " because of " + e.getMessage());
                    s_logger.warn("Unable to create attache for agent: " + request.toString(), e);
                }
                if (attache == null) {
                    if (response == null) {
                        s_logger.warn("Unable to create attache for agent: " + request.toString());
                        response = new Response(request, new StartupAnswer((StartupCommand) request.getCommand(), "Unable to register this agent"), _nodeId, -1);
                    }
                    try {
                        link.send(response.toBytes(), true);
                    } catch (final ClosedChannelException e) {
                        s_logger.warn("Response was not sent: " + response.toString());
                    }
                    return;
                }
            }

            final long hostId = attache.getId();

            if (s_logger.isDebugEnabled()) {
                if (cmd instanceof PingComputingCommand) {
                    final PingComputingCommand ping = (PingComputingCommand) cmd;
                    if (ping.getNewStates().size() > 0) {
                        s_logger.debug("SeqA " + hostId + "-" + request.getSequence() + ": Processing " + request.toString());
                    } else {
                        logD = false;
                        s_logger.debug("Ping from " + hostId);
                        s_logger.trace("SeqA " + hostId + "-" + request.getSequence() + ": Processing " + request.toString());
                    }
                } else if (cmd instanceof PingStorageCommand) {
                    logD = false;
                    s_logger.debug("Ping from " + hostId);
                    s_logger.trace("SeqA " + attache.getId() + "-" + request.getSequence() + ": Processing " + request.toString());
                } else {
                    s_logger.debug("SeqA " + attache.getId() + "-" + request.getSequence() + ": Processing " + request.toString());
                }
            }

            final Answer[] answers = new Answer[cmds.length];
            for (int i = 0; i < cmds.length; i++) {
                cmd = cmds[i];
                Answer answer = null;
                try {
                    if (cmd instanceof StartupRoutingCommand) {
                        final StartupRoutingCommand startup = (StartupRoutingCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof StartupComputingCommand) {
                        final StartupComputingCommand startup = (StartupComputingCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof StartupStorageCommand) {
                        final StartupStorageCommand startup = (StartupStorageCommand) cmd;
                        answer = new StartupAnswer(startup, attache.getId(), getPingInterval());
                    } else if (cmd instanceof ShutdownCommand) {
                        final ShutdownCommand shutdown = (ShutdownCommand) cmd;
                        final String reason = shutdown.getReason();
                        s_logger.info("Host " + attache.getId() + " has informed us that it is shutting down with reason " + reason + " and detail "
                                + shutdown.getDetail());
                        if (reason.equals(ShutdownCommand.Update)) {
                            disconnect(attache, Event.UpdateNeeded, false);
                        } else if (reason.equals(ShutdownCommand.Requested)) {
                            disconnect(attache, Event.ShutdownRequested, false);
                        }
                        return;
                    } else if(cmd instanceof AgentControlCommand) {
                    	answer = handleControlCommand(attache, (AgentControlCommand)cmd);
                    } else {
                        handleCommands(attache, request.getSequence(), new Command[] { cmd });
                        if (cmd instanceof PingCommand) {
                            long cmdHostId = ((PingCommand) cmd).getHostId();

                            // if the router is sending a ping, verify the
                            // gateway was pingable
                            if (cmd instanceof PingRoutingCommand) {
                                boolean gatewayAccessible = ((PingRoutingCommand) cmd).isGatewayAccessible();
                                HostVO host = _hostDao.findById(Long.valueOf(cmdHostId));
                                if (!gatewayAccessible) {
                                    // alert that host lost connection to
                                    // gateway (cannot ping the default route)
                                    DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                                    HostPodVO podVO = _podDao.findById(host.getPodId());
                                    String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName()
                                            + ", pod: " + podVO.getName();

                                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_ROUTING, host.getDataCenterId(), host.getPodId(),
                                            "Host lost connection to gateway, " + hostDesc, "Host [" + hostDesc
                                                    + "] lost connection to gateway (default route) and is possibly having network connection issues.");
                                } else {
                                    _alertMgr.clearAlert(AlertManager.ALERT_TYPE_ROUTING, host.getDataCenterId(), host.getPodId());
                                }
                            }
                            answer = new PingAnswer((PingCommand) cmd);
                        } else if (cmd instanceof ReadyAnswer) {
                            HostVO host = _hostDao.findById(attache.getId());
                            s_logger.info("Host " + attache.getId() + " is now ready to processing commands.");
                            _hostDao.updateStatus(host, Event.Ready, _nodeId);
                        } else {
                            answer = new Answer(cmd);
                        }
                    }
                } catch (final Throwable th) {
                    s_logger.warn("Caught: ", th);
                    answer = new Answer(cmd, false, th.getMessage());
                }
                answers[i] = answer;
            }

            response = new Response(request, answers, _nodeId, attache.getId());
            if (s_logger.isDebugEnabled()) {
                if (logD) {
                    s_logger.debug("SeqA " + attache.getId() + "-" + response.getSequence() + ": Sending " + response.toString());
                } else {
                    s_logger.trace("SeqA " + attache.getId() + "-" + response.getSequence() + ": Sending " + response.toString());
                }
            }
            try {
                link.send(response.toBytes());
            } catch (final ClosedChannelException e) {
                s_logger.warn("Unable to send response because connection is closed: " + response.toString());
            }
        }

        protected void processResponse(final Link link, final Response response) {
            final AgentAttache attache = (AgentAttache) link.attachment();
            if (attache == null) {
                s_logger.warn("Unable to process: " + response.toString());
            }

            if (!attache.processAnswers(response.getSequence(), response)) {
                s_logger.info("Host " + attache.getId() + " - Seq " + response.getSequence() + ": Response is not processed: " + response.toString());
            }
        }

        @Override
        protected void doTask(final Task task) throws Exception {
        	Transaction txn = Transaction.open(Transaction.VMOPS_DB);
        	try {
	            final Type type = task.getType();
	            if (type == Task.Type.DATA) {
	                final byte[] data = task.getData();
	                try {
	                    final Request event = Request.parse(data);
	                    if (event instanceof Response) {
	                        processResponse(task.getLink(), (Response) event);
	                    } else {
	                        processRequest(task.getLink(), event);
	                    }
	                } catch (final UnsupportedVersionException e) {
	                    s_logger.warn(e.getMessage());
	                    upgradeAgent(task.getLink(), data, e.getReason());
	                }
	            } else if (type == Task.Type.CONNECT) {
	            } else if (type == Task.Type.DISCONNECT) {
	                final Link link = task.getLink();
	                final AgentAttache attache = (AgentAttache) link.attachment();
	                if (attache != null) {
	                    disconnect(attache, Event.AgentDisconnected, true);
	                } else {
	                    s_logger.info("Connection from " + link.getIpAddress() + " closed but no cleanup was done.");
	                    link.close();
	                    link.terminated();
	                }
	            }
        	} finally {
        		txn.close();
        	}
        }
    }
    
    protected AgentManagerImpl() {
    }
}
