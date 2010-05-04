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

package com.vmops.consoleproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vmops.agent.AgentManager;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.ConsoleAccessAuthenticationAnswer;
import com.vmops.agent.api.ConsoleAccessAuthenticationCommand;
import com.vmops.agent.api.ConsoleProxyLoadReportCommand;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.RebootCommand;
import com.vmops.agent.api.StartConsoleProxyAnswer;
import com.vmops.agent.api.StartConsoleProxyCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.proxy.ConsoleProxyLoadAnswer;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.cluster.ClusterManager;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.domain.DomainVO;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.ConcurrentOperationException;
import com.vmops.exception.InsufficientCapacityException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.StorageUnavailableException;
import com.vmops.ha.HighAvailabilityManager;
import com.vmops.ha.dao.HighAvailabilityDao;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.info.ConsoleProxyConnectionInfo;
import com.vmops.info.ConsoleProxyLoadInfo;
import com.vmops.info.ConsoleProxyStatus;
import com.vmops.info.RunningHostCountInfo;
import com.vmops.info.RunningHostInfoAgregator;
import com.vmops.info.RunningHostInfoAgregator.ZoneHostInfo;
import com.vmops.network.IPAddressVO;
import com.vmops.network.NetworkManager;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.Account;
import com.vmops.user.AccountVO;
import com.vmops.user.User;
import com.vmops.user.dao.AccountDao;
import com.vmops.utils.DateUtil;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Pair;
import com.vmops.utils.component.Adapters;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.GlobalLock;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.events.SubscriptionMgr;
import com.vmops.utils.exception.ExecutionException;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.ConsoleProxyVO;
import com.vmops.vm.State;
import com.vmops.vm.UserVm;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.VirtualMachineManager;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.VirtualMachine.Event;
import com.vmops.vm.dao.ConsoleProxyDao;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;

//
// Possible console proxy state transition cases
//		Creating -> Destroyed
//		Creating -> Stopped --> Starting -> Running
//		HA -> Stopped -> Starting -> Running
//		Migrating -> Running	(if previous state is Running before it enters into Migrating state
//		Migrating -> Stopped	(if previous state is not Running before it enters into Migrating state)
//		Running -> HA			(if agent lost connection)
//		Stopped -> Destroyed
//
//		Creating state indicates of record creating and IP address allocation are ready, it is a transient
// 		state which will soon be switching towards Running if everything goes well.
//		Stopped state indicates the readiness of being able to start (has storage and IP resources allocated)
//		Starting state can only be entered from Stopped states
//
// Starting, HA, Migrating, Creating and Running state are all counted as "Open" for available capacity calculation
// because sooner or later, it will be driven into Running state
//
@Local(value = { ConsoleProxyManager.class })
public class ConsoleProxyManagerImpl implements ConsoleProxyManager, VirtualMachineManager<ConsoleProxyVO> {
	private static final Logger s_logger = Logger.getLogger(ConsoleProxyManagerImpl.class);

	private static final int DEFAULT_FIND_HOST_RETRY_COUNT = 2;
	private static final int DEFAULT_CAPACITY_SCAN_INTERVAL = 30000; 		// 30 seconds
	private static final int EXECUTOR_SHUTDOWN_TIMEOUT = 1000; 				// 1 second

	private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 3; 	// 3 seconds
	private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC = 180; 		// 3 minutes
	
	private static final int API_WAIT_TIMEOUT = 5000;							// 5 seconds (in milliseconds)
	private static final int STARTUP_DELAY = 60000; 							// 60 seconds

	private int _consoleProxyPort = ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT;
	private int _consoleProxyUrlPort = ConsoleProxyManager.DEFAULT_PROXY_URL_PORT;
	
	private String _mgmt_host;
	private int _mgmt_port = 8250;

	private String _name;
	private Adapters<ConsoleProxyAllocator> _consoleProxyAllocators;

	private ConsoleProxyDao _consoleProxyDao;
	private DataCenterDao _dcDao;
	private VlanDao _vlanDao;
	private VMTemplateDao _templateDao;
	private IPAddressDao _ipAddressDao;
	private VolumeDao _volsDao;
	private HostPodDao _podDao;
	private HostDao _hostDao;
	private StoragePoolDao _storagePoolDao;
	private UserVmDao _userVmDao;
	private VMInstanceDao _instanceDao;
    private AccountDao _accountDao;

	private VMTemplateHostDao _vmTemplateHostDao;
	private CapacityDao _capacityDao;
	private HighAvailabilityDao _haDao;

	private AgentManager _agentMgr;
	private NetworkManager _networkMgr;
	private StorageManager _storageMgr;
    private EventDao _eventDao;
	
	private ConsoleProxyListener _listener;
	
    private ServiceOfferingVO _serviceOffering;
    private DiskOfferingVO _diskOffering;
    private VMTemplateVO _template;
    
    private AsyncJobManager _asyncMgr;

	private final ScheduledExecutorService _capacityScanScheduler = Executors
			.newScheduledThreadPool(1, new NamedThreadFactory("CP-Scan"));
	private final ExecutorService _requestHandlerScheduler = Executors
			.newCachedThreadPool(new NamedThreadFactory("Request-handler"));
	
	private long _capacityScanInterval = DEFAULT_CAPACITY_SCAN_INTERVAL;
	private int _capacityPerProxy = ConsoleProxyManager.DEFAULT_PROXY_CAPACITY;
	private int _standbyCapacity = ConsoleProxyManager.DEFAULT_STANDBY_CAPACITY;

	private int _proxyRamSize;
	private int _find_host_retry = DEFAULT_FIND_HOST_RETRY_COUNT;
	private int _ssh_retry;
	private int _ssh_sleep;
	private boolean _use_lvm;
	private String _domain;
	private String _instance;
	
	// private String _privateNetmask;
	private int _proxyCmdPort = DEFAULT_PROXY_CMD_PORT;
	private int _proxySessionTimeoutValue = DEFAULT_PROXY_SESSION_TIMEOUT;
	private boolean _sslEnabled = false;

	private final GlobalLock _capacityScanLock = GlobalLock.getInternLock(getCapacityScanLockName());
	private final GlobalLock _allocProxyLock = GlobalLock.getInternLock(getAllocProxyLockName());
	
	public ConsoleProxyVO assignProxy(final long dataCenterId, final long userVmId) {
		
		final Pair<ConsoleProxyManagerImpl, ConsoleProxyVO> result = new Pair<ConsoleProxyManagerImpl, ConsoleProxyVO>(this, null);
		
		_requestHandlerScheduler.execute(new Runnable() {
			public void run() {
				Transaction txn = Transaction.open(Transaction.VMOPS_DB);
				try {
					ConsoleProxyVO proxy = doAssignProxy(dataCenterId, userVmId);
					synchronized(result) {
						result.second(proxy);
						result.notifyAll();
					}
				} catch(Throwable e) {
					s_logger.warn("Unexpected exception " + e.getMessage(), e);
				} finally {
					txn.close();
				}
			}
		});
		
		synchronized(result) {
			try {
				result.wait(API_WAIT_TIMEOUT);
			} catch (InterruptedException e) {
				s_logger.info("Waiting for console proxy assignment is interrupted");
			}
		}
		return result.second();
	}
	
	public ConsoleProxyVO doAssignProxy(long dataCenterId, long userVmId) {

		ConsoleProxyVO proxy = null;
		UserVmVO userVm = _userVmDao.findById(userVmId);
		if (userVm == null) {
			s_logger.warn("User VM " + userVmId + " no longer exists, return a null proxy for user vm:"
					+ userVmId);
			return null;
		}

		Boolean[] proxyFromStoppedPool = new Boolean[1];
		boolean repeat = false;
		do {
			if (_allocProxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
				try {
					proxy = getOrAllocProxyResource(dataCenterId, userVmId, proxyFromStoppedPool);
				} finally {
					_allocProxyLock.unlock();
				}
			} else {
				s_logger.error("Unable to acquire synchronization lock to get/allocate proxy resource for user vm :"
								+ userVmId
								+ ". Previous console proxy allocation is taking too long");
			}
			
			if(proxy == null) 
				break;

			long proxyVmId = proxy.getId();
			GlobalLock proxyLock = GlobalLock.getInternLock(getProxyLockName(proxyVmId));
			try {
				if (proxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
					try {
						proxy = startProxy(proxyVmId);
					} finally {
						proxyLock.unlock();
					}
				} else {
					s_logger.error("Unable to acquire synchronization lock to start console proxy "
								+ proxyVmId
								+ " for user vm: "
								+ userVmId
								+ ". It takes too long to start the proxy");
				}
			} finally {
				proxyLock.releaseRef();
			}

			if (proxy == null) {
				
				//
				// We had a situation with multi-pod configuration, where storage allocation of
				// the console proxy VM may succeed, but later-on starting of it may fail because
				// of running out of computing resource (CPU/memory). We currently don't
				// support moving storage to another pod on the fly, to deal with the situation
				// we will destroy this proxy VM and let it the whole proxy VM creation process
				// re-start again, by hoping that new storage and computing resource may be
				// allocated and assigned in another pod
				//
				if(s_logger.isInfoEnabled())
					s_logger.info("Unable to start console proxy, proxy vm Id : "
							+ proxyVmId + " will recycle it and restart a new one");
				destroyProxy(proxyVmId);
				
/*				
				if (proxyFromStoppedPool[0] != null && proxyFromStoppedPool[0] == true) {
					s_logger.info("Unable to start console proxy, proxy vm Id : "
						+ proxyVmId + " will recycle it and restart a new one");

					destroyProxy(proxyVmId);
					repeat = true;
				} else {
					s_logger.info("Unable to start console proxy, proxy vm Id : "
						+ proxyVmId
						+ ", caller needs to retry next time");
				}
*/				
			} else {
				if (s_logger.isTraceEnabled())
					s_logger.trace("Console proxy " + proxy.getName() + " is started");

				// if it is a new assignment or a changed assignment, update the record
				if (userVm.getProxyId() == null || userVm.getProxyId().longValue() != proxy.getId().longValue())
					_userVmDao.updateProxyId(userVmId, proxy.getId(), DateUtil.currentGMTTime());

				proxy.setPort(_consoleProxyUrlPort);
				proxy.setSslEnabled(_sslEnabled);
				return proxy;
			}
		} while (repeat);
		
		if(proxy != null) {
			proxy.setPort(_consoleProxyUrlPort);
			proxy.setSslEnabled(_sslEnabled);
		}
		return proxy;
	}
	
	private ConsoleProxyVO getOrAllocProxyResource(long dataCenterId,
			long userVmId, Boolean[] proxyFromStoppedPool) {
		ConsoleProxyVO proxy = null;
		UserVmVO userVm = _userVmDao.findById(userVmId);

		if (userVm != null && userVm.getState() != State.Running) {
			if (s_logger.isInfoEnabled())
				s_logger.info("Detected that user vm : " + userVmId + " is not currently at running state, we will fail the proxy assignment for it");
			return null;
		}

		if (userVm != null && userVm.getProxyId() != null) {
			proxy = _consoleProxyDao.findById(userVm.getProxyId());

			if (proxy != null) {
				if (!isInAssignableState(proxy)) {
					if (s_logger.isInfoEnabled())
						s_logger.info("A previous assigned proxy is not assignable now, reassign console proxy for user vm : " + userVmId);
					proxy = null;
				} else {
					// Use proxy actual load info to determine allocation instead of static load (assigned running VMs)
					// Proxy load info will be reported to management server at 5-second interval, the load info used here
					// may be temporarily out of sync with its actual load info
					if (_consoleProxyDao.getProxyActiveLoad(proxy.getId()) < _capacityPerProxy || hasPreviousSession(proxy, userVm)) {
						if (s_logger.isTraceEnabled())
							s_logger.trace("Assign previous allocated console proxy for user vm : " + userVmId);

						if (proxy.getActiveSession() >= _capacityPerProxy)
							s_logger.warn("Assign overloaded proxy to user VM as previous session exists, user vm : " + userVmId);
					} else {
						proxy = null;
					}
				}
			}
		}

		if (proxy == null)
			proxy = assignProxyFromRunningPool(dataCenterId);

		if (proxy == null) {
			if (s_logger.isInfoEnabled())
				s_logger.info("No running console proxy is available, check to see if we can bring up a stopped one for data center : "
					+ dataCenterId);

			proxy = assignProxyFromStoppedPool(dataCenterId);
			if (proxy == null) {
				if (s_logger.isInfoEnabled())
					s_logger.info("No stopped console proxy is available, need to allocate a new console proxy for data center : " + dataCenterId);

				proxy = startNew(dataCenterId);
			} else {
				if (s_logger.isInfoEnabled())
					s_logger.info("Found a stopped console proxy, bring it up to running pool. proxy vm id : " + proxy.getId()
						+ ", data center : " + dataCenterId);

				proxyFromStoppedPool[0] = new Boolean(true);
			}
		}

		return proxy;
	}

	private static boolean isInAssignableState(ConsoleProxyVO proxy) {
		// console proxies that are in states of being able to serve user VM
		State state = proxy.getState();
		if (state == State.Running || state == State.Starting
				|| state == State.Creating || state == State.Migrating)
			return true;

		return false;
	}

	private boolean hasPreviousSession(ConsoleProxyVO proxy, UserVmVO userVm) {

		ConsoleProxyStatus status = null;
		try {
			GsonBuilder gb = new GsonBuilder();
			gb.setVersion(1.3);
			Gson gson = gb.create();

			byte[] details = proxy.getSessionDetails();
			status = gson.fromJson(details != null ? new String(details,
					Charset.forName("US-ASCII")) : null,
					ConsoleProxyStatus.class);
		} catch (Throwable e) {
			s_logger.warn("Unable to parse proxy session details : "
					+ proxy.getSessionDetails());
		}

		if (status != null && status.getConnections() != null) {
			ConsoleProxyConnectionInfo[] connections = status.getConnections();
			for (int i = 0; i < connections.length; i++) {
				long taggedVmId = 0;
				if(connections[i].tag != null) {
					try {
						taggedVmId = Long.parseLong(connections[i].tag);
					} catch(NumberFormatException e) {
						s_logger.warn("Unable to parse console proxy connection info passed through tag: " + connections[i].tag, e);
					}
				}
				if(taggedVmId == userVm.getId().longValue())
					return true;
			}

			//
			// even if we are not in the list, it may because we haven't
			// received load-update yet
			// wait until session time
			//
			if (DateUtil.currentGMTTime().getTime() - userVm.getProxyAssignTime().getTime() < _proxySessionTimeoutValue)
				return true;

			return false;
		} else {
			s_logger.error("No proxy load info on an overloaded proxy ?");
			return false;
		}
	}

	@Override
	public ConsoleProxyVO startProxy(long proxyVmId) {
		try {
			return start(proxyVmId);
		} catch (StorageUnavailableException e) {
			s_logger.warn("Exception while trying to start console proxy", e);
			return null;
		} catch (InsufficientCapacityException e) {
			s_logger.warn("Exception while trying to start console proxy", e);
			return null;
		} catch (ConcurrentOperationException e) {
			s_logger.warn("Exception while trying to start console proxy", e);
			return null;
		}
	}

	@Override @DB
	public ConsoleProxyVO start(long proxyId) throws StorageUnavailableException, InsufficientCapacityException, ConcurrentOperationException {

        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Start console proxy " + proxyId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "console_proxy", proxyId);
        }
		
		ConsoleProxyVO proxy = _consoleProxyDao.findById(proxyId);
		if (proxy == null || proxy.getRemoved() != null) {
			s_logger.debug("proxy is not found: " + proxyId);
			return null;
		}

		if (s_logger.isTraceEnabled()) {
			s_logger.trace("Starting console proxy if it is not started, proxy vm id : " + proxyId);
		}

		for (int i = 0; i < 2; i++) {

			State state = proxy.getState();

			if (state == State.Starting /* || state == State.Migrating */) {
				if (s_logger.isDebugEnabled())
					s_logger.debug("Waiting console proxy to be ready, proxy vm id : "
						+ proxyId
						+ " proxy VM state : "
						+ state.toString());

				if (proxy.getPrivateIpAddress() == null || connect(proxy.getPrivateIpAddress(), _proxyCmdPort) != null) {
					if (proxy.getPrivateIpAddress() == null)
						s_logger.warn("Retruning a proxy that is being started but private IP has not been allocated yet, proxy vm id : "
							+ proxyId);
					else
						s_logger.warn("Waiting console proxy to be ready timed out, proxy vm id : "
							+ proxyId);

					// TODO, it is very tricky here, if the startup process
					// takes too long and it timed out here,
					// we may give back a proxy that is not fully ready for
					// functioning
				}
				return proxy;
			}

			if (state == State.Running) {
				if (s_logger.isTraceEnabled()) 
					s_logger.trace("Console proxy is already started: "
							+ proxy.getName());
				return proxy;
			}

			DataCenterVO dc = _dcDao.findById(proxy.getDataCenterId());
			HostPodVO pod = _podDao.findById(proxy.getPodId());
			StoragePoolVO sp = _storagePoolDao.findById(proxy.getPoolId());

			HashSet<Host> avoid = new HashSet<Host>();
			HostVO routingHost = (HostVO) _agentMgr.findHost(Host.Type.Routing, dc, pod, sp, _serviceOffering, _diskOffering, _template, proxy, null, avoid);

			if (routingHost == null) {
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Unable to find a routing host for " + proxy.toString());
					continue;
				}
			}
			// to ensure atomic state transition to Starting state
			if (!_consoleProxyDao.updateIf(proxy, Event.StartRequested, routingHost.getId())) {
				if (s_logger.isDebugEnabled()) {
					ConsoleProxyVO temp = _consoleProxyDao.findById(proxyId);
					s_logger.debug("Unable to start console proxy "
							+ proxy.getName()
							+ " because it is not in a startable state : "
							+ ((temp != null) ? temp.getState().toString() : "null"));
				}
				continue;
			}

			try {
				List<VolumeVO> vols = _volsDao.findByInstance(proxyId);
				VolumeVO vol = vols.get(0);
				HostVO storageHost = _hostDao.findById(vol.getHostId());
				
				// Get the VLAN ID for the ConsoleProxy
	            IPAddressVO ipVO = _ipAddressDao.findById(proxy.getPublicIpAddress());
	            VlanVO vlan = _vlanDao.findById(ipVO.getVlanDbId());
	            String vlanId = vlan.getVlanId();

				Answer answer = null;
				int retry = _find_host_retry;

				// Console proxy VM will be running at routing hosts as routing
				// hosts have public access to outside network
				do {
					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Trying to start console proxy on host "
								+ routingHost.getName());
					}
					
					List<String> vlanIds = _hostDao.getVlanIds(routingHost.getId().longValue());
	                if (!vlanIds.contains(vlanId)) {
	                	s_logger.debug("Could not start console proxy on host " + routingHost.getName() + " because it does not contain the VLAN " + vlanId + ". Avoiding host...");
	                	avoid.add(routingHost);
	                	continue;
	                }

					String storageIp = _storageMgr.chooseStorageIp(proxy,
							routingHost, storageHost);

					String privateIpAddress = _dcDao.allocatePrivateIpAddress(
							proxy.getDataCenterId(), routingHost.getPodId(),
							proxy.getId());
					if (privateIpAddress == null) {
						s_logger.debug("Not enough ip addresses in " + routingHost.getPodId());
						avoid.add(routingHost);
						continue;
					}

					proxy.setPrivateIpAddress(privateIpAddress);
					proxy.setStorageIp(storageIp);
					_consoleProxyDao.updateIf(proxy, Event.OperationRetry, routingHost.getId());
					proxy = _consoleProxyDao.findById(proxy.getId());

					Map<String, Integer> mappings = _storageMgr.share(proxy, vols, routingHost, true);
					if (mappings == null) {
						s_logger.warn("Can not share " + proxy.getId() + " on host " + vol.getHostId());

						SubscriptionMgr.getInstance().notifySubscribers(
							ConsoleProxyManager.ALERT_SUBJECT, this,
							new ConsoleProxyAlertEventArgs(
								ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
								proxy.getDataCenterId(), proxy.getId(), proxy, "Unable to share the mounting storage to target host")
						);
						throw new StorageUnavailableException(vol.getHostId());
					}

					// carry the console proxy port info over so that we don't
					// need to configure agent on this
					StartConsoleProxyCommand cmdStart = new StartConsoleProxyCommand(
							_proxyCmdPort, proxy, proxy.getName(), storageIp,
							vols, mappings, 
							Integer.toString(_consoleProxyPort),
							Integer.toString(_consoleProxyUrlPort),
							_mgmt_host, _mgmt_port);

					if (s_logger.isDebugEnabled())
						s_logger.debug("Sending start command for console proxy "
								+ proxy.getName()
								+ " to "
								+ routingHost.getName());

					answer = _agentMgr.easySend(routingHost.getId(), cmdStart);
					s_logger.debug("StartConsoleProxy Answer: " + (answer != null ? answer : "null"));

					if (s_logger.isDebugEnabled())
						s_logger.debug("Received answer on starting console proxy "
							+ proxy.getName()
							+ " on "
							+ routingHost.getName());

					if (answer != null) {
						if (s_logger.isDebugEnabled()) {
							s_logger.debug("Console proxy " + proxy.getName()
									+ " started on " + routingHost.getName());
						}
						
                		if (answer instanceof StartConsoleProxyAnswer){
                			StartConsoleProxyAnswer rAnswer = (StartConsoleProxyAnswer)answer;
                			if (rAnswer.getPrivateIpAddress() != null) {
                			    proxy.setPrivateIpAddress(rAnswer.getPrivateIpAddress());
                			}
                			if (rAnswer.getPrivateMacAddress() != null) {
                				proxy.setPrivateMacAddress(rAnswer.getPrivateMacAddress());
                			}
                		}
                		
                        final EventVO event = new EventVO();
                        event.setUserId(User.UID_SYSTEM);
                        event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
                        event.setType(EventTypes.EVENT_PROXY_START);
                        event.setLevel(EventVO.LEVEL_INFO);
                        event.setDescription("Console proxy started - " + proxy.getName());
                        _eventDao.persist(event);
						break;
					}

					avoid.add(routingHost);
					proxy.setPrivateIpAddress(null);
					_dcDao.releasePrivateIpAddress(privateIpAddress, proxy
							.getDataCenterId(), proxy.getId());

					_storageMgr.unshare(proxy, vols, routingHost);
				} while (--retry > 0 && (routingHost = (HostVO) _agentMgr.findHost(
								Host.Type.Routing, dc, pod, sp, _serviceOffering, _diskOffering, _template,
								proxy, null, avoid)) != null);
				if (routingHost == null || retry < 0) {
					
					SubscriptionMgr.getInstance().notifySubscribers(
						ConsoleProxyManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
							proxy.getDataCenterId(), proxy.getId(), proxy, "Unable to find a routing host to run")
					);

                    final EventVO event = new EventVO();
                    event.setUserId(User.UID_SYSTEM);
                    event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
                    event.setType(EventTypes.EVENT_PROXY_START);
                    event.setLevel(EventVO.LEVEL_ERROR);
                    event.setDescription("Starting console proxy failed due to unable to find a host - " + proxy.getName());
                    _eventDao.persist(event);
					throw new ExecutionException(
							"Couldn't find a routingHost to run console proxy");
				}

				_consoleProxyDao.updateIf(proxy, Event.OperationSucceeded, routingHost.getId());
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Console proxy is now started, vm id : " + proxy.getId());
				}

				// If starting the console proxy failed due to the external
				// firewall not being reachable, send an alert.
				if (answer != null && answer.getDetails() != null
						&& answer.getDetails().equals("firewall")) {
					
					SubscriptionMgr.getInstance().notifySubscribers(
						ConsoleProxyManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_FIREWALL_ALERT,
							proxy.getDataCenterId(), proxy.getId(), proxy, null)
					);
				}

				SubscriptionMgr.getInstance().notifySubscribers(
					ConsoleProxyManager.ALERT_SUBJECT, this,
					new ConsoleProxyAlertEventArgs(
						ConsoleProxyAlertEventArgs.PROXY_UP,
						proxy.getDataCenterId(), proxy.getId(), proxy, null)
				);
				
				return proxy;
			} catch (Throwable thr) {
				s_logger.warn("Unexpected exception: ", thr);
				
				SubscriptionMgr.getInstance().notifySubscribers(
					ConsoleProxyManager.ALERT_SUBJECT, this,
					new ConsoleProxyAlertEventArgs(
						ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
						proxy.getDataCenterId(), proxy.getId(), proxy, "Unexpected exception: " + thr.getMessage())
				);
				
                final EventVO event = new EventVO();
                event.setUserId(User.UID_SYSTEM);
                event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
                event.setType(EventTypes.EVENT_PROXY_START);
                event.setLevel(EventVO.LEVEL_ERROR);
                event.setDescription("Starting console proxy failed due to unhandled exception - " + proxy.getName());
                _eventDao.persist(event);

				Transaction txn = Transaction.currentTxn();
				try {
					txn.start();
					String privateIpAddress = proxy.getPrivateIpAddress();
					if (privateIpAddress != null) {
						proxy.setPrivateIpAddress(null);
						_dcDao.releasePrivateIpAddress(privateIpAddress, proxy.getDataCenterId(), proxy.getId());
					}
					proxy.setStorageIp(null);
					_consoleProxyDao.updateIf(proxy, Event.OperationFailed, null);
					txn.commit();
				} catch (Exception e) {
					s_logger.error("Caught exception during error recovery");
				}

				if (thr instanceof StorageUnavailableException) {
					throw (StorageUnavailableException) thr;
				} else if (thr instanceof ConcurrentOperationException) {
					throw (ConcurrentOperationException) thr;
				} else if (thr instanceof ExecutionException) {
					s_logger.error("Error while starting console proxy due to " + thr.getMessage());
				} else {
					s_logger.error("Error while starting console proxy ", thr);
				}
				return null;
			}
		}

		s_logger.warn("Starting console proxy encounters non-startable situation");
		return null;
	}

	public ConsoleProxyVO assignProxyFromRunningPool(long dataCenterId) {

		if (s_logger.isTraceEnabled())
			s_logger.trace("Assign console proxy from running pool for request from data center : " + dataCenterId);

		ConsoleProxyAllocator allocator = getCurrentAllocator();
		assert (allocator != null);
		List<ConsoleProxyVO> runningList = _consoleProxyDao.getProxyListInStates(dataCenterId, State.Running);
		if (runningList != null && runningList.size() > 0) {
			if (s_logger.isTraceEnabled()) {
				s_logger.trace("Running proxy pool size : " + runningList.size());
				for (ConsoleProxyVO proxy : runningList)
					s_logger.trace("Running proxy instance : " + proxy.getName());
			}

			List<Pair<Long, Integer>> l = _consoleProxyDao.getProxyLoadMatrix();
			Map<Long, Integer> loadInfo = new HashMap<Long, Integer>();
			if (l != null) {
				for (Pair<Long, Integer> p : l) {
					loadInfo.put(p.first(), p.second());

					if (s_logger.isTraceEnabled()) {
						s_logger.trace("Running proxy instance allocation load { proxy id : "
							+ p.first() + ", load : " + p.second() + "}");
					}
				}
			}
			return allocator.allocProxy(runningList, loadInfo, dataCenterId);
		} else {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Empty running proxy pool for now in data center : " + dataCenterId);
		}
		return null;
	}

	public ConsoleProxyVO assignProxyFromStoppedPool(long dataCenterId) {
		List<ConsoleProxyVO> l = _consoleProxyDao.getProxyListInStates(
				dataCenterId, State.Creating, State.Starting, State.Stopped,
				State.Migrating);
		if (l != null && l.size() > 0)
			return l.get(0);

		return null;
	}

	public ConsoleProxyVO startNew(long dataCenterId) {

		if (s_logger.isDebugEnabled())
			s_logger.debug("Assign console proxy from a newly started instance for request from data center : " + dataCenterId);

		Map<String, Object> context = createProxyInstance(dataCenterId);

		long proxyVmId = (Long) context.get("proxyVmId");
		if (proxyVmId == 0) {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Creating proxy instance failed, data center id : " + dataCenterId);

			// release critical system resource on failure
			if (context.get("publicIpAddress") != null)
				freePublicIpAddress((String) context.get("publicIpAddress"));

			return null;
		}

		ConsoleProxyVO proxy = allocProxyStorage(dataCenterId, proxyVmId);
		if (proxy != null) {
			SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
				new ConsoleProxyAlertEventArgs(
					ConsoleProxyAlertEventArgs.PROXY_CREATED,
					dataCenterId, proxy.getId(), proxy, null)
			);
			return proxy;
		} else {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Unable to allocate console proxy storage, remove the console proxy record from DB, proxy id: "
					+ proxyVmId);
			
			SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
				new ConsoleProxyAlertEventArgs(
					ConsoleProxyAlertEventArgs.PROXY_CREATE_FAILURE,
					dataCenterId, proxyVmId, null, "Unable to allocate storage")
			);
			
			destroyProxyDBOnly(proxyVmId);
		}
		return null;
	}

	@DB
	protected Map<String, Object> createProxyInstance(long dataCenterId) {

		Map<String, Object> context = new HashMap<String, Object>();
		String publicIpAddress = null;

		Transaction txn = Transaction.currentTxn();
		try {
			DataCenterVO dc = _dcDao.findById(dataCenterId);
			assert (dc != null);
			context.put("dc", dc);

			// this will basically allocate the pod based on data center id as
			// we use system user id here
			HostPodVO pod = _agentMgr.findPod(_template, _serviceOffering, dc, Account.ACCOUNT_ID_SYSTEM, new HashSet<Long>());
			if (pod == null) {
				s_logger.warn("Unable to allocate pod for console proxy in data center : " + dataCenterId);

				context.put("proxyVmId", (long) 0);
				return context;
			}
			context.put("pod", pod);
			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Pod allocated " + pod.getName());
			}

			// About MAC address allocation
			// MAC address used by User VM is inherited from DomR MAC address,
			// with the least 16 bits overrided. to avoid
			// potential conflicts, domP will mask bit 31
			//
			String[] macAddresses = _dcDao.getNextAvailableMacAddressPair(
					dataCenterId, (1L << 31));
			String privateMacAddress = macAddresses[0];
			String publicMacAddress = macAddresses[1];
			long id = _consoleProxyDao.getNextInSequence(Long.class, "id");

			publicIpAddress = allocPublicIpAddress(dataCenterId);
			if (publicIpAddress == null) {
				s_logger.warn("Unable to allocate public IP address for console proxy in data center : " + dataCenterId);

				context.put("proxyVmId", (long) 0);
				return context;
			}
			context.put("publicIpAddress", publicIpAddress);

			String cidrNetmask = NetUtils.getCidrNetmask(pod.getCidrSize());
			
			// Find the VLAN ID, VLAN gateway, and VLAN netmask for publicIpAddress
            IPAddressVO ipVO = _ipAddressDao.findById(publicIpAddress);
            VlanVO vlan = _vlanDao.findById(ipVO.getVlanDbId());
            String vlanGateway = vlan.getVlanGateway();
            String vlanNetmask = vlan.getVlanNetmask();

			txn.start();
			ConsoleProxyVO proxy;
			String name = VirtualMachineName.getConsoleProxyName(id, _instance).intern();
			proxy = new ConsoleProxyVO(id, name, State.Creating,
					privateMacAddress, null, cidrNetmask, _template.getId(), _template.getGuestOSId(),
					publicMacAddress, publicIpAddress, vlanNetmask, vlan.getId(), vlan.getVlanId(),
					pod.getId(), dataCenterId, vlanGateway, null,
					dc.getDns1(), dc.getDns2(), _domain, _proxyRamSize, 0);

			long proxyVmId = _consoleProxyDao.persist(proxy);
			
            final EventVO event = new EventVO();
            event.setUserId(User.UID_SYSTEM);
            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
            event.setType(EventTypes.EVENT_PROXY_CREATE);
            event.setLevel(EventVO.LEVEL_INFO);
            event.setDescription("New console proxy created - " + proxy.getName());
            _eventDao.persist(event);
			txn.commit();

			context.put("proxyVmId", proxyVmId);
			return context;
		} catch (Throwable e) {
			s_logger.error("Unexpected exception : ", e);

			context.put("proxyVmId", (long) 0);
			return context;
		}
	}

	@DB
	protected ConsoleProxyVO allocProxyStorage(long dataCenterId, long proxyVmId) {
		ConsoleProxyVO proxy = _consoleProxyDao.findById(proxyVmId);
		assert (proxy != null);

		DataCenterVO dc = _dcDao.findById(dataCenterId);
		HostPodVO pod = _podDao.findById(proxy.getPodId());
		long poolId = 0;
        final AccountVO account = _accountDao.findById(Account.ACCOUNT_ID_SYSTEM);
		
        try {
			poolId = _storageMgr.create(account, proxy, _template, dc, pod, _serviceOffering, _diskOffering);
			if( poolId == 0){
				s_logger.error("Unable to alloc storage for console proxy");
				return null;
			}
			
			Transaction txn = Transaction.currentTxn();
			txn.start();
			
			// update pool id
			ConsoleProxyVO vo = _consoleProxyDao.findById(proxy.getId());
			vo.setPoolId(poolId);
			_consoleProxyDao.update(proxy.getId(), vo);
			
			// kick the state machine
			_consoleProxyDao.updateIf(proxy, Event.OperationSucceeded, null);
			
			txn.commit();
			return proxy;
		} catch (StorageUnavailableException e) {
			s_logger.error("Unable to alloc storage for console proxy: ", e);
			return null;
		} catch (ExecutionException e) {
			s_logger.error("Unable to alloc storage for console proxy: ", e);
			return null;
		}
	}

	private String allocPublicIpAddress(long dcId) {
		long vlanDbIdToUse = _networkMgr.findNextVlan(dcId);
		
		String ipAddress = _ipAddressDao.assignIpAddress(
				Account.ACCOUNT_ID_SYSTEM, DomainVO.ROOT_DOMAIN.longValue(),
				vlanDbIdToUse, true);
		if (ipAddress == null) {
			s_logger.error("Unable to get public ip address from data center : " + dcId);
			return null;
		}
		return ipAddress;
	}

	private void freePublicIpAddress(String ipAddress) {
		_ipAddressDao.unassignIpAddress(ipAddress);
	}

	private ConsoleProxyAllocator getCurrentAllocator() {

		// for now, only one adapter is supported
		Enumeration<ConsoleProxyAllocator> it = _consoleProxyAllocators.enumeration();
		if (it.hasMoreElements())
			return it.nextElement();

		return null;
	}

	protected String connect(String ipAddress, int port) {
		for (int i = 0; i <= _ssh_retry; i++) {
			SocketChannel sch = null;
			try {
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Trying to connect to " + ipAddress);
				}
				sch = SocketChannel.open();
				sch.configureBlocking(true);
				sch.socket().setSoTimeout(5000);

				InetSocketAddress addr = new InetSocketAddress(ipAddress, port);
				sch.connect(addr);
				return null;
			} catch (IOException e) {
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Could not connect to " + ipAddress);
				}
			} finally {
				if (sch != null) {
					try {
						sch.close();
					} catch (IOException e) {
					}
				}
			}
			try {
				Thread.sleep(_ssh_sleep);
			} catch (InterruptedException ex) {
			}
		}

		s_logger.debug("Unable to logon to " + ipAddress);

		return "Unable to connect";
	}

	public void onLoadAnswer(ConsoleProxyLoadAnswer answer) {
		if(answer.getDetails() == null)
			return;

		ConsoleProxyStatus status = null;
		try {
			GsonBuilder gb = new GsonBuilder();
			gb.setVersion(1.3);
			Gson gson = gb.create();
			status = gson.fromJson(answer.getDetails(), ConsoleProxyStatus.class);
		} catch (Throwable e) {
			s_logger.warn("Unable to parse load info from proxy, proxy vm id : "
				+ answer.getProxyVmId() + ", info : " + answer.getDetails());
		}

		if (status != null) {
			int count = 0;
			if (status.getConnections() != null)
				count = status.getConnections().length;

			byte[] details = null;
			if (answer.getDetails() != null)
				details = answer.getDetails().getBytes(Charset.forName("US-ASCII"));
			_consoleProxyDao.update(answer.getProxyVmId(), count, DateUtil.currentGMTTime(), details);
		} else {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Unable to get console proxy load info, id : " + answer.getProxyVmId());

			_consoleProxyDao.update(answer.getProxyVmId(), 0, DateUtil.currentGMTTime(), null);
			// TODO : something is wrong with the VM, restart it?
		}
	}
	
	public void onLoadReport(ConsoleProxyLoadReportCommand cmd) {
		if(cmd.getLoadInfo() == null)
			return;
		
		ConsoleProxyStatus status = null;
		try {
			GsonBuilder gb = new GsonBuilder();
			gb.setVersion(1.3);
			Gson gson = gb.create();
			status = gson.fromJson(cmd.getLoadInfo(), ConsoleProxyStatus.class);
		} catch (Throwable e) {
			s_logger.warn("Unable to parse load info from proxy, proxy vm id : "
				+ cmd.getProxyVmId() + ", info : " + cmd.getLoadInfo());
		}
		
		if (status != null) {
			int count = 0;
			if (status.getConnections() != null)
				count = status.getConnections().length;

			byte[] details = null;
			if (cmd.getLoadInfo() != null)
				details = cmd.getLoadInfo().getBytes(Charset.forName("US-ASCII"));
			_consoleProxyDao.update(cmd.getProxyVmId(), count, DateUtil.currentGMTTime(), details);
		} else {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Unable to get console proxy load info, id : " + cmd.getProxyVmId());

			_consoleProxyDao.update(cmd.getProxyVmId(), 0, DateUtil.currentGMTTime(), null);
		}
	}
	
	public AgentControlAnswer onConsoleAccessAuthentication(ConsoleAccessAuthenticationCommand cmd) {
		long vmId = 0;
		
		if(cmd.getVmId() != null && cmd.getVmId().isEmpty()) {
			if(s_logger.isTraceEnabled())
				s_logger.trace("Invalid vm id sent from proxy(happens when proxy session has terminated)");
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		try {
			vmId = Long.parseLong(cmd.getVmId());
		} catch(NumberFormatException e) {
			s_logger.error("Invalid vm id " + cmd.getVmId() + " sent from console access authentication", e);
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		// TODO authentication channel between console proxy VM and management server needs to be secured, 
		// the data is now being sent through private network, but this is apparently not enough
		UserVm vm = _userVmDao.findById(vmId);
		if(vm == null) {
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		if(vm.getHostId() == null) {
			s_logger.warn("VM " + vmId + " lost host info, failed authentication request");
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		HostVO host = _hostDao.findById(vm.getHostId());
		if(host == null) {
			s_logger.warn("VM " + vmId + "'s host does not exist, fail authentication request");
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		String sid = cmd.getSid();
		if(sid == null || !sid.equals(vm.getVncPassword())) {
			s_logger.warn("sid " + sid + " in url does not match stored sid " + vm.getVncPassword());
			return new ConsoleAccessAuthenticationAnswer(cmd, false);
		}
		
		return new ConsoleAccessAuthenticationAnswer(cmd, true);
	}

	private void checkPendingProxyVMs() {
		// drive state to change away from transient states
		List<ConsoleProxyVO> l = _consoleProxyDao.getProxyListInStates(State.Creating);
		if (l != null && l.size() > 0) {
			for (ConsoleProxyVO proxy : l) {
				if (proxy.getLastUpdateTime() == null ||
					(proxy.getLastUpdateTime() != null && System.currentTimeMillis() - proxy.getLastUpdateTime().getTime() > 60000)) {
					try {
						ConsoleProxyVO readyProxy = null;
						if (_allocProxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
							try {
								readyProxy = allocProxyStorage(proxy.getDataCenterId(), proxy.getId());
							} finally {
								_allocProxyLock.unlock();
							}

							if (readyProxy != null) {
								GlobalLock proxyLock = GlobalLock.getInternLock(getProxyLockName(readyProxy.getId()));
								try {
									if (proxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
										try {
											readyProxy = start(readyProxy.getId());
										} finally {
											proxyLock.unlock();
										}
									} else {
										if (s_logger.isInfoEnabled())
											s_logger.info("Unable to acquire synchronization lock to start console proxy : " + readyProxy.getName());
									}
								} finally {
									proxyLock.releaseRef();
								}
							}
						} else {
							if (s_logger.isInfoEnabled())
								s_logger.info("Unable to acquire synchronization lock to allocate proxy storage, wait for next turn");
						}
					} catch (StorageUnavailableException e) {
						s_logger.warn("Storage unavailable", e);
					} catch (InsufficientCapacityException e) {
						s_logger.warn("insuffiient capacity", e);
					} catch (ConcurrentOperationException e) {
						s_logger.debug("Concurrent operation: " + e.getMessage());
					}
				}
			}
		}
	}

	private Runnable getCapacityScanTask() {
		return new Runnable() {
			
			@Override
			public void run() {
				Transaction txn = Transaction.open(Transaction.VMOPS_DB);
				try {
					reallyRun();
				} catch(Throwable e) {
					s_logger.warn("Unexpected exception " + e.getMessage(), e);
				} finally {
					txn.close();
				}
			}
			
			private void reallyRun() {
				if (s_logger.isTraceEnabled())
					s_logger.trace("Begin console proxy capacity scan");
				
				Map<Long, ZoneHostInfo> zoneHostInfoMap = getZoneHostInfo();
				if (isServiceReady(zoneHostInfoMap)) {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Service is ready, check to see if we need to allocate standby capacity");

					if (!_capacityScanLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION)) {
						if (s_logger.isTraceEnabled())
							s_logger.trace("Capacity scan lock is used by others, skip and wait for my turn");
						return;
					}

					if (s_logger.isTraceEnabled())
						s_logger.trace("*** Begining capacity scan... ***");

					try {
						checkPendingProxyVMs();

						// scan default data center first
						long defaultId = 0;
						
						// proxy count info by data-centers (zone-id, zone-name, count)
						List<ConsoleProxyLoadInfo> l = _consoleProxyDao.getDatacenterProxyLoadMatrix();

						// running VM session count by data-centers (zone-id, zone-name, count)
						List<ConsoleProxyLoadInfo> listVmCounts = _consoleProxyDao.getDatacenterSessionLoadMatrix();
						
						// indexing load info by data-center id
						Map<Long, ConsoleProxyLoadInfo> mapVmCounts = new HashMap<Long, ConsoleProxyLoadInfo>();
						if (listVmCounts != null)
							for (ConsoleProxyLoadInfo info : listVmCounts)
								mapVmCounts.put(info.getId(), info);

						for (ConsoleProxyLoadInfo info : l) {
							if (info.getName().equals(_instance)) {
								ConsoleProxyLoadInfo vmInfo = mapVmCounts.get(info.getId());

								if (!checkCapacity(info, vmInfo != null ? vmInfo : new ConsoleProxyLoadInfo())) {
									if(isZoneReady(zoneHostInfoMap, info.getId())) {
										allocCapacity(info.getId());
									} else {
										if(s_logger.isDebugEnabled())
											s_logger.debug("Zone " + info.getId() + " is not ready to alloc standy console proxy");
									}
								}

								defaultId = info.getId();
								break;
							}
						}

						// scan rest of data-centers
						for (ConsoleProxyLoadInfo info : l) {
							if (info.getId() != defaultId) {
								ConsoleProxyLoadInfo vmInfo = mapVmCounts.get(info.getId());

								if (!checkCapacity(info, vmInfo != null ? vmInfo : new ConsoleProxyLoadInfo())) {
									if(isZoneReady(zoneHostInfoMap, info.getId())) {
										allocCapacity(info.getId());
									} else {
										if(s_logger.isDebugEnabled())
											s_logger.debug("Zone " + info.getId() + " is not ready to alloc standy console proxy");
									}
								}
							}
						}

						if (s_logger.isTraceEnabled())
							s_logger.trace("*** Stop capacity scan ***");
					} finally {
						_capacityScanLock.unlock();
					}

				} else {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Service is not ready for capacity preallocation, wait for next time");
				}

				if (s_logger.isTraceEnabled())
					s_logger.trace("End of console proxy capacity scan");
			}
		};
	}

	private boolean checkCapacity(ConsoleProxyLoadInfo proxyCountInfo,
			ConsoleProxyLoadInfo vmCountInfo) {

		if (proxyCountInfo.getCount() * _capacityPerProxy
				- vmCountInfo.getCount() <= _standbyCapacity)
			return false;

		return true;
	}

	private void allocCapacity(long dataCenterId) {
		if (s_logger.isTraceEnabled())
			s_logger.trace("Allocate console proxy standby capacity for data center : " + dataCenterId);

		boolean proxyFromStoppedPool = false;
		ConsoleProxyVO proxy = assignProxyFromStoppedPool(dataCenterId);
		if (proxy == null) {
			if (s_logger.isInfoEnabled())
				s_logger.info("No stopped console proxy is available, need to allocate a new console proxy");

			if (_allocProxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
				try {
					proxy = startNew(dataCenterId);
				} finally {
					_allocProxyLock.unlock();
				}
			} else {
				if (s_logger.isInfoEnabled())
					s_logger.info("Unable to acquire synchronization lock to allocate proxy resource for standby capacity, wait for next scan");
				return;
			}
		} else {
			if (s_logger.isInfoEnabled())
				s_logger.info("Found a stopped console proxy, bring it up to running pool. proxy vm id : " + proxy.getId());
			proxyFromStoppedPool = true;
		}

		if (proxy != null) {
			long proxyVmId = proxy.getId();
			GlobalLock proxyLock = GlobalLock.getInternLock(getProxyLockName(proxyVmId));
			try {
				if (proxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
					try {
						proxy = startProxy(proxyVmId);
					} finally {
						proxyLock.unlock();
					}
				} else {
					if (s_logger.isInfoEnabled())
						s_logger.info("Unable to acquire synchronization lock to start proxy for standby capacity, proxy vm id : "
							+ proxy.getId());
					return;
				}
			} finally {
				proxyLock.releaseRef();
			}

			if (proxy == null) {
				if (s_logger.isInfoEnabled())
					s_logger.info("Unable to start console proxy for standby capacity, proxy vm Id : "
						+ proxyVmId + ", will recycle it and start a new one");

				if (proxyFromStoppedPool)
					destroyProxy(proxyVmId);
			} else {
				if (s_logger.isInfoEnabled())
					s_logger.info("Console proxy " + proxy.getName() + " is started");
			}
		}
	}

	public boolean isServiceReady(Map<Long, ZoneHostInfo> zoneHostInfoMap) {
		for (ZoneHostInfo zoneHostInfo : zoneHostInfoMap.values()) {
			if (zoneHostInfo.getFlags() == RunningHostInfoAgregator.ZoneHostInfo.ALL_HOST_MASK) {
				if (s_logger.isInfoEnabled())
					s_logger.info("Zone " + zoneHostInfo.getDcId() + " is ready to launch");
				return true;
			}
		}

		return false;
	}
	
	public boolean isZoneReady(Map<Long, ZoneHostInfo> zoneHostInfoMap, long dataCenterId) {
		ZoneHostInfo zoneHostInfo = zoneHostInfoMap.get(dataCenterId);
		if(zoneHostInfo != null && zoneHostInfo.getFlags() == RunningHostInfoAgregator.ZoneHostInfo.ALL_HOST_MASK) {
	        VMTemplateVO template = _templateDao.findConsoleProxyTemplate();
	        if(template != null && template.isReady()) {
	        	
	        	List<Pair<Long, Integer>> l = _consoleProxyDao.getDatacenterStoragePoolHostInfo(dataCenterId, _use_lvm);
	        	if(l != null && l.size() > 0 && l.get(0).second().intValue() > 0) {
	        		return true;
	        	} else {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Primary storage is not ready, wait until it is ready to launch console proxy");
	        	}
	        } else {
				if (s_logger.isTraceEnabled())
					s_logger.trace("Zone host is ready, but console proxy template is not ready");
	        }
		}
		return false;
	}
	
	private synchronized Map<Long, ZoneHostInfo> getZoneHostInfo() {
		Date cutTime = DateUtil.currentGMTTime();
		List<RunningHostCountInfo> l = _hostDao.getRunningHostCounts(new Date(cutTime.getTime() - 
				ClusterManager.DEFAULT_HEARTBEAT_THRESHOLD));

		RunningHostInfoAgregator aggregator = new RunningHostInfoAgregator();
		if (l.size() > 0)
			for (RunningHostCountInfo countInfo : l)
				aggregator.aggregate(countInfo);

		return aggregator.getZoneHostInfoMap();
	}

	@Override
	public String getName() {
		return _name;
	}

	@Override
	public boolean start() {
		if (s_logger.isInfoEnabled())
			s_logger.info("Start console proxy manager");

		return true;
	}

	@Override
	public boolean stop() {
		if (s_logger.isInfoEnabled())
			s_logger.info("Stop console proxy manager");
		_capacityScanScheduler.shutdownNow();

		try {
			_capacityScanScheduler.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
		}

		_capacityScanLock.releaseRef();
		_allocProxyLock.releaseRef();
		return true;
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		if (s_logger.isInfoEnabled())
			s_logger.info("Start configuring console proxy manager : " + name);

		_name = name;

		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
		if (configDao == null) {
			throw new ConfigurationException("Unable to get the configuration dao.");
		}

		Map<String, String> configs = configDao.getConfiguration("management-server", params);

		_proxyRamSize = NumbersUtil.parseInt(configs.get("consoleproxy.ram.size"), DEFAULT_PROXY_VM_RAMSIZE);

		String value = configs.get("start.retry");
		_find_host_retry = NumbersUtil.parseInt(value, DEFAULT_FIND_HOST_RETRY_COUNT);

		value = configs.get("consoleproxy.cmd.port");
		_proxyCmdPort = NumbersUtil.parseInt(value, DEFAULT_PROXY_CMD_PORT);
		
		value = configs.get("consoleproxy.sslEnabled");
		if(value != null && value.equalsIgnoreCase("true"))
			_sslEnabled = true;

		value = configs.get("consoleproxy.capacityscan.interval");
		_capacityScanInterval = NumbersUtil.parseLong(value, DEFAULT_CAPACITY_SCAN_INTERVAL);

		_capacityPerProxy = NumbersUtil.parseInt(configs.get("consoleproxy.session.max"), DEFAULT_PROXY_CAPACITY);
		_standbyCapacity = NumbersUtil.parseInt(configs.get("consoleproxy.capacity.standby"),
				DEFAULT_STANDBY_CAPACITY);
		_proxySessionTimeoutValue = NumbersUtil.parseInt(configs.get("consoleproxy.session.timeout"),
				DEFAULT_PROXY_SESSION_TIMEOUT);

		value = configs.get("consoleproxy.port");
		if (value != null)
			_consoleProxyPort = NumbersUtil.parseInt(value, ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT);

		value = configs.get("consoleproxy.url.port");
		if (value != null)
			_consoleProxyUrlPort = NumbersUtil.parseInt(value, ConsoleProxyManager.DEFAULT_PROXY_URL_PORT);
		
		value = configs.get("system.vm.use.local.storage");
		if(value != null && value.equalsIgnoreCase("true"))
			_use_lvm = true;

		if (s_logger.isInfoEnabled()) {
			s_logger.info("Console proxy max session soft limit : " + _capacityPerProxy);
			s_logger.info("Console proxy standby capacity : " + _standbyCapacity);
		}

		_domain = configs.get("domain");
		if (_domain == null) {
			_domain = "foo.com";
		}

		_instance = configs.get("instance.name");
		if (_instance == null) {
			_instance = "DEFAULT";
		}

		value = (String) params.get("ssh.sleep");
		_ssh_sleep = NumbersUtil.parseInt(value, 5) * 1000;

		value = (String) params.get("ssh.retry");
		_ssh_retry = NumbersUtil.parseInt(value, 3);
		
		Map<String, String> agentMgrConfigs = configDao.getConfiguration("AgentManager", params);
		_mgmt_host = agentMgrConfigs.get("host");
		if(_mgmt_host == null) {
			s_logger.warn("Critical warning! Please configure your management server host address right after you have started your management server and then restart it, otherwise you won't be able to do console access");
		}
		
		value = agentMgrConfigs.get("port");
		_mgmt_port = NumbersUtil.parseInt(value, 8250);

		_consoleProxyDao = locator.getDao(ConsoleProxyDao.class);
		if (_consoleProxyDao == null) {
			throw new ConfigurationException("Unable to get " + ConsoleProxyDao.class.getName());
		}

		_consoleProxyAllocators = locator.getAdapters(ConsoleProxyAllocator.class);
		if (_consoleProxyAllocators == null || !_consoleProxyAllocators.isSet()) {
			throw new ConfigurationException("Unable to get proxy allocators");
		}

		_dcDao = locator.getDao(DataCenterDao.class);
		if (_dcDao == null) {
			throw new ConfigurationException("Unable to get " + DataCenterDao.class.getName());
		}

		_templateDao = locator.getDao(VMTemplateDao.class);
		if (_templateDao == null) {
			throw new ConfigurationException("Unable to get " + VMTemplateDao.class.getName());
		}

		_ipAddressDao = locator.getDao(IPAddressDao.class);
		if (_ipAddressDao == null) {
			throw new ConfigurationException("Unable to get " + IPAddressDao.class.getName());
		}

		_volsDao = locator.getDao(VolumeDao.class);
		if (_volsDao == null) {
			throw new ConfigurationException("Unable to get " + VolumeDao.class.getName());
		}

		_podDao = locator.getDao(HostPodDao.class);
		if (_podDao == null) {
			throw new ConfigurationException("Unable to get " + HostPodDao.class.getName());
		}

		_hostDao = locator.getDao(HostDao.class);
		if (_hostDao == null) {
			throw new ConfigurationException("Unable to get " + HostDao.class.getName());
		}
		
		_eventDao = locator.getDao(EventDao.class);
		if(_eventDao == null) {
			throw new ConfigurationException("Unable to get " + EventDao.class.getName());
		}
		
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
            throw new ConfigurationException("Unable to find " + StoragePoolDao.class);
        }

		_vmTemplateHostDao = locator.getDao(VMTemplateHostDao.class);
		if (_vmTemplateHostDao == null) {
			throw new ConfigurationException("Unable to get " + VMTemplateHostDao.class.getName());
		}

		_userVmDao = locator.getDao(UserVmDao.class);
		if (_userVmDao == null)
			throw new ConfigurationException("Unable to get " + UserVmDao.class.getName());

		_instanceDao = locator.getDao(VMInstanceDao.class);
		if (_instanceDao == null)
			throw new ConfigurationException("Unable to get " + VMInstanceDao.class.getName());

		_capacityDao = locator.getDao(CapacityDao.class);
		if (_capacityDao == null) {
			throw new ConfigurationException("Unable to get " + CapacityDao.class.getName());
		}

		_haDao = locator.getDao(HighAvailabilityDao.class);
		if (_haDao == null) {
			throw new ConfigurationException("Unable to get " + HighAvailabilityDao.class.getName());
		}
		
        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to get " + AccountDao.class.getName());
        }
        
        _vlanDao = locator.getDao(VlanDao.class);
        if (_vlanDao == null) {
            throw new ConfigurationException("Unable to get " + VlanDao.class.getName());
        }

		_agentMgr = locator.getManager(AgentManager.class);
		if (_agentMgr == null) {
			throw new ConfigurationException("Unable to get " + AgentManager.class.getName());
		}
		
		_networkMgr = locator.getManager(NetworkManager.class);
		if (_networkMgr == null) {
			throw new ConfigurationException("Unable to get " + NetworkManager.class.getName());
		}

		_listener = new ConsoleProxyListener(this);
		_agentMgr.registerForHostEvents(_listener, true, true);

		_storageMgr = locator.getManager(StorageManager.class);
		if (_storageMgr == null) {
			throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
		}

        _asyncMgr = locator.getManager(AsyncJobManager.class);
		if (_asyncMgr == null) {
			throw new ConfigurationException("Unable to get " + AsyncJobManager.class.getName());
		}

		HighAvailabilityManager haMgr = locator.getManager(HighAvailabilityManager.class);
		if (haMgr != null) {
			haMgr.registerHandler(VirtualMachine.Type.ConsoleProxy, this);
		}

		_serviceOffering = new ServiceOfferingVO(null, "Fake Offering For DomP", 1, _proxyRamSize, 0, 0, 0, true, null, false);
		_diskOffering = new DiskOfferingVO(1, "fake disk offering", "fake disk offering", 0, false);
        _template = _templateDao.findConsoleProxyTemplate();
        if (_template == null) {
            throw new ConfigurationException("Unable to find the template for console proxy VMs");
        }
 
		_capacityScanScheduler.scheduleAtFixedRate(getCapacityScanTask(), STARTUP_DELAY,
				_capacityScanInterval, TimeUnit.MILLISECONDS);
		
		if (s_logger.isInfoEnabled())
			s_logger.info("Console Proxy Manager is configured.");
		return true;
	}

	protected ConsoleProxyManagerImpl() {
	}

	@Override
	public Command cleanup(ConsoleProxyVO vm, String vmName) {
		if (vmName != null) {
			return new StopCommand(vm, vmName, VirtualMachineName.getVnet(vmName));
		} else if (vm != null) {
			ConsoleProxyVO vo = vm;
			return new StopCommand(vo, null);
		} else {
			throw new VmopsRuntimeException("Shouldn't even be here!");
		}
	}

	@Override
	public void completeStartCommand(ConsoleProxyVO vm) {
		_consoleProxyDao.updateIf(vm, Event.AgentReportRunning, vm.getHostId());
	}

	@Override
	public void completeStopCommand(ConsoleProxyVO vm) {
		completeStopCommand(vm, Event.AgentReportStopped);
	}

	@DB
	protected void completeStopCommand(ConsoleProxyVO proxy, Event ev) {
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			String privateIpAddress = proxy.getPrivateIpAddress();
			if (privateIpAddress != null) {
				proxy.setPrivateIpAddress(null);
				_dcDao.releasePrivateIpAddress(privateIpAddress, proxy.getDataCenterId(), proxy.getId());
			}
			proxy.setStorageIp(null);
			if (!_consoleProxyDao.updateIf(proxy, ev, null)) {
				s_logger.debug("Unable to update the console proxy");
				return;
			}
			txn.commit();
		} catch (Exception e) {
			s_logger.error("Unable to complete stop command due to ", e);
		}

		if (_storageMgr.unshare(proxy, null) == null) {
			s_logger.warn("Unable to set share to false for " + proxy.getId());
		}
	}

	@Override
	public ConsoleProxyVO get(long id) {
		return _consoleProxyDao.findById(id);
	}

	@Override
	public Long convertToId(String vmName) {
		if (!VirtualMachineName.isValidConsoleProxyName(vmName, _instance)) {
			return null;
		}
		return VirtualMachineName.getConsoleProxyId(vmName);
	}

	@Override
	public boolean stopProxy(long proxyVmId) {
		
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Stop console proxy " + proxyVmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "console_proxy", proxyVmId);
        }
		
		ConsoleProxyVO proxy = _consoleProxyDao.findById(proxyVmId);
		if (proxy == null) {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Stopping console proxy failed: console proxy " + proxyVmId + " no longer exists");
			return false;
		}
		try {
			return stop(proxy);
		} catch (AgentUnavailableException e) {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Stopping console proxy " + proxy.getName() + " faled : exception " + e.toString());
			return false;
		}
	}

	@Override
	public boolean rebootProxy(long proxyVmId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Reboot console proxy " + proxyVmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "console_proxy", proxyVmId);
        }
        
		final ConsoleProxyVO proxy = _consoleProxyDao.findById(proxyVmId);

		if (proxy == null || proxy.getState() == State.Destroyed) {
			return false;
		}

		if (proxy.getState() == State.Running && proxy.getHostId() != null) {
			final RebootCommand cmd = new RebootCommand(proxy.getInstanceName());
			final Answer answer = _agentMgr.easySend(proxy.getHostId(), cmd);

			if (answer != null) {
				if (s_logger.isDebugEnabled())
					s_logger.debug("Successfully reboot console proxy " + proxy.getName());
				
				SubscriptionMgr.getInstance().notifySubscribers(
						ConsoleProxyManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_REBOOTED,
							proxy.getDataCenterId(), proxy.getId(), proxy, null)
					);
				
	            final EventVO event = new EventVO();
	            event.setUserId(User.UID_SYSTEM);
	            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
	            event.setType(EventTypes.EVENT_PROXY_REBOOT);
	            event.setLevel(EventVO.LEVEL_INFO);
	            event.setDescription("Console proxy rebooted - " + proxy.getName());
	            _eventDao.persist(event);
				return true;
			} else {
				if (s_logger.isDebugEnabled())
					s_logger.debug("failed to reboot console proxy : " + proxy.getName());
				
	            final EventVO event = new EventVO();
	            event.setUserId(User.UID_SYSTEM);
	            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
	            event.setType(EventTypes.EVENT_PROXY_REBOOT);
	            event.setLevel(EventVO.LEVEL_ERROR);
	            event.setDescription("Rebooting console proxy failed - " + proxy.getName());
	            _eventDao.persist(event);
				return false;
			}
		} else {
			return startProxy(proxyVmId) != null;
		}
	}

	@Override
	public boolean destroy(ConsoleProxyVO proxy)
			throws AgentUnavailableException {
		return destroyProxy(proxy.getId());
	}

	@Override
	@DB
	public boolean destroyProxy(long vmId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Destroy console proxy " + vmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "console_proxy", vmId);
        }
		
		ConsoleProxyVO vm = _consoleProxyDao.findById(vmId);
		if (vm == null || vm.getState() == State.Destroyed) {
			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Unable to find vm or vm is destroyed: " + vmId);
			}
			return true;
		}

		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Destroying console proxy vm " + vmId);
		}

		if (!_consoleProxyDao.updateIf(vm, Event.DestroyRequested, null)) {
			s_logger.debug("Unable to destroy the vm because it is not in the correct state: " + vmId);
			return false;
		}

		Transaction txn = Transaction.currentTxn();
		List<VolumeVO> vols = null;
		try {
			vols = _volsDao.findByInstance(vmId);
			if (!vm.isMirroredVols()) {
				if (vols.size() != 0) {
					VolumeVO vol = vols.get(0); // we only need one.
					DestroyCommand cmd = new DestroyCommand(vol.getFolder(), vols);
					long hostId = vol.getHostId();
					Answer answer = _agentMgr.easySend(hostId, cmd);
					if (answer == null || !answer.getResult()) {
						HostVO host = _hostDao.findById(hostId);
						DataCenterVO dcVO = _dcDao.findById(host
								.getDataCenterId());
						HostPodVO podVO = _podDao.findById(host.getPodId());
						String hostDesc = "name: " + host.getName() + " (id:"
								+ host.getId() + "), zone: "
								+ dcVO.getName() + ", pod: " + podVO.getName();

						String message = "Storage cleanup required due to deletion failure. host: " + hostDesc +", volume: " + vol.getFolder();
						SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
							new ConsoleProxyAlertEventArgs(
								ConsoleProxyAlertEventArgs.PROXY_STORAGE_ALERT,
								vol.getDataCenterId(), vmId, vm, message)
						);
						
						s_logger.warn("Cleanup required on storage server "
								+ host.getPrivateIpAddress()
								+ " for "
								+ vol.getFolder()
								+ " due to "
								+ (answer == null ? "answer is null" : answer.getDetails()));
					}
				}
			} else {
				if (vols.size() != 0) {
					Map<Long, List<VolumeVO>> hostVolMap = new HashMap<Long, List<VolumeVO>>();
					for (VolumeVO vol : vols) {
						List<VolumeVO> vollist = hostVolMap
								.get(vol.getHostId());
						if (vollist == null) {
							vollist = new ArrayList<VolumeVO>();
							hostVolMap.put(vol.getHostId(), vollist);
						}
						vollist.add(vol);
					}
					for (Long hostId : hostVolMap.keySet()) {
						List<VolumeVO> volumes = hostVolMap.get(hostId);
						String path = volumes.get(0) == null ? null : volumes.get(0).getFolder();
						DestroyCommand cmd = new DestroyCommand(path, volumes);
						Answer answer = _agentMgr.easySend(hostId, cmd);
						if (answer == null || !answer.getResult()) {
							HostVO host = _hostDao.findById(hostId);
							DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
							HostPodVO podVO = _podDao.findById(host.getPodId());
							String hostDesc = "name: " + host.getName()
									+ " (id:" + host.getId()
									+ "), zone: " + dcVO.getName()
									+ ", pod: " + podVO.getName();

							String message = "Storage cleanup required due to failure in contacting with storage host. host: " + hostDesc;
							SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
								new ConsoleProxyAlertEventArgs(
									ConsoleProxyAlertEventArgs.PROXY_STORAGE_ALERT,
									dcVO.getId(), vmId, vm, message)
							);
							
							s_logger.warn("Cleanup required on storage server "
									+ host.getPrivateIpAddress()
									+ " due to "
									+ (answer == null ? "answer is null" : answer.getDetails()));
						}
					}
				}
			}

			return true;
		} finally {
			try {
				txn.start();
				// release critical system resources used by the VM before we
				// delete them
				if (vols != null) {
					for (VolumeVO vol : vols) {
						_volsDao.remove(vol.getId());
					}
				}
				if (vm.getPublicIpAddress() != null)
					freePublicIpAddress(vm.getPublicIpAddress());
				vm.setPublicIpAddress(null);

				_consoleProxyDao.remove(vm.getId());
				
	            final EventVO event = new EventVO();
	            event.setUserId(User.UID_SYSTEM);
	            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
	            event.setType(EventTypes.EVENT_PROXY_DESTROY);
	            event.setLevel(EventVO.LEVEL_INFO);
	            event.setDescription("Console proxy destroyed - " + vm.getName());
	            _eventDao.persist(event);
				
				txn.commit();
			} catch (Exception e) {
				s_logger.error("Caught this error: ", e);
				txn.rollback();
				return false;
			} finally {
				s_logger.debug("console proxy vm is destroyed : "
						+ vm.getName());
			}
		}
	}

	@DB
	public boolean destroyProxyDBOnly(long vmId) {
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			_volsDao.deleteVolumesByInstance(vmId);

			ConsoleProxyVO proxy = _consoleProxyDao.findById(vmId);
			if (proxy != null) {
				if (proxy.getPublicIpAddress() != null)
					freePublicIpAddress(proxy.getPublicIpAddress());

				_consoleProxyDao.remove(vmId);
				
	            final EventVO event = new EventVO();
	            event.setUserId(User.UID_SYSTEM);
	            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
	            event.setType(EventTypes.EVENT_PROXY_DESTROY);
	            event.setLevel(EventVO.LEVEL_INFO);
	            event.setDescription("Console proxy destroyed - " + proxy.getName());
	            _eventDao.persist(event);
			}
			
			txn.commit();
			return true;
		} catch (Exception e) {
			s_logger.error("Caught this error: ", e);
			txn.rollback();
			return false;
		} finally {
			s_logger.debug("console proxy vm is destroyed from DB : " + vmId);
		}
	}

	@Override
	public boolean stop(ConsoleProxyVO proxy) throws AgentUnavailableException {
		if (!_consoleProxyDao.updateIf(proxy, Event.StopRequested, proxy.getHostId())) {
			s_logger.debug("Unable to stop console proxy: " + proxy.toString());
			return false;
		}

		// IPAddressVO ip = _ipAddressDao.findById(proxy.getPublicIpAddress());
		// VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
		
		GlobalLock proxyLock = GlobalLock.getInternLock(getProxyLockName(proxy.getId()));
		try {
			if (proxyLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
				try {
					StopCommand cmd = new StopCommand(proxy, true,
							Integer.toString(_consoleProxyPort),
							Integer.toString(_consoleProxyUrlPort),
							proxy.getPublicIpAddress());
					try {
						StopAnswer answer = (StopAnswer) _agentMgr.send(proxy.getHostId(), cmd);
						if (answer == null || !answer.getResult()) {
							s_logger.debug("Unable to stop due to " + (answer == null ? "answer is null" : answer.getDetails()));
							
				            final EventVO event = new EventVO();
				            event.setUserId(User.UID_SYSTEM);
				            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
				            event.setType(EventTypes.EVENT_PROXY_STOP);
				            event.setLevel(EventVO.LEVEL_ERROR);
				            event.setDescription("Stopping console proxy failed due to negative answer from agent - " + proxy.getName());
				            _eventDao.persist(event);
							return false;
						}
						completeStopCommand(proxy, Event.OperationSucceeded);
						
						SubscriptionMgr.getInstance().notifySubscribers(
								ConsoleProxyManager.ALERT_SUBJECT, this,
								new ConsoleProxyAlertEventArgs(
									ConsoleProxyAlertEventArgs.PROXY_DOWN,
									proxy.getDataCenterId(), proxy.getId(), proxy, null)
							);
						
			            final EventVO event = new EventVO();
			            event.setUserId(User.UID_SYSTEM);
			            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
			            event.setType(EventTypes.EVENT_PROXY_STOP);
			            event.setLevel(EventVO.LEVEL_INFO);
			            event.setDescription("Console proxy stopped - " + proxy.getName());
			            _eventDao.persist(event);
						return true;
					} catch (OperationTimedoutException e) {
			            final EventVO event = new EventVO();
			            event.setUserId(User.UID_SYSTEM);
			            event.setAccountId(Account.ACCOUNT_ID_SYSTEM);
			            event.setType(EventTypes.EVENT_PROXY_STOP);
			            event.setLevel(EventVO.LEVEL_ERROR);
			            event.setDescription("Stopping console proxy failed due to operation time out - " + proxy.getName());
			            _eventDao.persist(event);
						throw new AgentUnavailableException(proxy.getHostId());
					}
				} finally {
					proxyLock.unlock();
				}
			} else {
				s_logger.debug("Unable to acquire console proxy lock : " + proxy.toString());
				return false;
			}
		} finally {
			proxyLock.releaseRef();
		}
	}

	@Override
	public boolean migrate(ConsoleProxyVO proxy, HostVO host) {
		HostVO fromHost = _hostDao.findById(proxy.getId());

		if (!_consoleProxyDao.updateIf(proxy, Event.MigrationRequested, proxy.getHostId())) {
			s_logger.debug("State for " + proxy.toString() + " has changed so migration can not take place.");
			return false;
		}

		MigrateCommand cmd = new MigrateCommand(proxy.getInstanceName(), host.getPrivateIpAddress());
		Answer answer = _agentMgr.easySend(fromHost.getId(), cmd);
		if (answer == null) {
			return false;
		}

		_storageMgr.unshare(proxy, fromHost);

		return true;
	}

	@Override
	public boolean completeMigration(ConsoleProxyVO proxy, HostVO host)
			throws AgentUnavailableException, OperationTimedoutException {
		CheckVirtualMachineCommand cvm = new CheckVirtualMachineCommand(proxy.getInstanceName());
		CheckVirtualMachineAnswer answer = (CheckVirtualMachineAnswer) _agentMgr.send(host.getId(), cvm);
		if (!answer.getResult()) {
			s_logger.debug("Unable to complete migration for " + proxy.getId());
			_consoleProxyDao.updateIf(proxy, Event.AgentReportStopped, null);
			return false;
		}

		State state = answer.getState();
		if (state == State.Stopped) {
			s_logger.warn("Unable to complete migration as we can not detect it on " + host.getId());
			_consoleProxyDao.updateIf(proxy, Event.AgentReportStopped, null);
			return false;
		}

		_consoleProxyDao.updateIf(proxy, Event.OperationSucceeded, host.getId());
		return true;
	}

	@Override
	public HostVO prepareForMigration(ConsoleProxyVO proxy) throws StorageUnavailableException {
		
		VMTemplateVO template = _templateDao.findById(proxy.getTemplateId());
		long routerId = proxy.getId();
		boolean mirroredVols = proxy.isMirroredVols();
		DataCenterVO dc = _dcDao.findById(proxy.getDataCenterId());
		HostPodVO pod = _podDao.findById(proxy.getPodId());
		StoragePoolVO sp = _storagePoolDao.findById(proxy.getPoolId());

		List<VolumeVO> vols = _volsDao.findByInstance(routerId);

		String[] storageIps = new String[2];
		VolumeVO vol = vols.get(0);
		storageIps[0] = vol.getHostIp();
		if (mirroredVols && (vols.size() == 2)) {
			storageIps[1] = vols.get(1).getHostIp();
		}

		PrepareForMigrationCommand cmd = new PrepareForMigrationCommand(proxy.getName(), null, storageIps, vols, null, mirroredVols);

		HostVO routingHost = null;
		HashSet<Host> avoid = new HashSet<Host>();

		HostVO fromHost = _hostDao.findById(proxy.getHostId());
		avoid.add(fromHost);
		
		List<String> currentHostVlanIds = _hostDao.getVlanIds(proxy.getHostId());

		while ((routingHost = (HostVO) _agentMgr.findHost(Host.Type.Routing,
				dc, pod, sp, _serviceOffering, _diskOffering, template, proxy, fromHost, avoid)) != null) {
			if (routingHost.getPodId() != fromHost.getPodId()) {
				s_logger.debug("Unable to migrate to another pod");
				avoid.add(routingHost);
				continue;
			}
			
			List<String> migrationHostVlanIds = _hostDao.getVlanIds(routingHost.getId().longValue());
        	if (!migrationHostVlanIds.containsAll(currentHostVlanIds)) {
        		s_logger.debug("Cannot migrate console proxy to host " + routingHost.getName() + " because it is missing some VLAN IDs. Skipping host...");
        		avoid.add(routingHost);
        		continue;
        	}

			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Trying to migrate router to host " + routingHost.getName());
			}

			Map<String, Integer> mappings = _storageMgr.share(proxy, vols, routingHost, false);
			if (mappings == null) {
				s_logger.warn("Can not share " + proxy.getId() + " on host " + vol.getHostId());
				throw new StorageUnavailableException(vol.getHostId());
			}
			cmd.setMappings(mappings);

			Answer answer = _agentMgr.easySend(routingHost.getId(), cmd);
			if (answer != null && answer.getResult()) {
				return routingHost;
			}
			_storageMgr.unshare(proxy, vols, routingHost);
		}

		return null;
	}

	private String getCapacityScanLockName() {
		// to improve security, it may be better to return a unique mashed
		// name(for example MD5 hashed)
		return "consoleproxy.capacity.scan";
	}

	private String getAllocProxyLockName() {
		// to improve security, it may be better to return a unique mashed
		// name(for example MD5 hashed)
		return "consoleproxy.alloc";
	}

	private String getProxyLockName(long id) {
		return "consoleproxy." + id;
	}
}
