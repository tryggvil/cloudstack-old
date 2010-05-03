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
package com.vmops.storage.secondary;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
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

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.CheckVirtualMachineAnswer;
import com.vmops.agent.api.CheckVirtualMachineCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.MigrateCommand;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.RebootCommand;
import com.vmops.agent.api.StartSecStorageVmAnswer;
import com.vmops.agent.api.StartSecStorageVmCommand;
import com.vmops.agent.api.StopAnswer;
import com.vmops.agent.api.StopCommand;
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
import com.vmops.info.RunningHostCountInfo;
import com.vmops.info.RunningHostInfoAgregator;
import com.vmops.info.SecStorageVmLoadInfo;
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
import com.vmops.storage.dao.StoragePoolHostDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.Account;
import com.vmops.user.AccountVO;
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
import com.vmops.utils.exception.ExecutionException;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.VirtualMachineManager;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.VirtualMachine.Event;
import com.vmops.vm.dao.SecondaryStorageVmDao;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;

//
// Possible secondary storage vm state transition cases
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
@Local(value={SecondaryStorageVmManager.class})
public class SecondaryStorageManagerImpl implements SecondaryStorageVmManager, VirtualMachineManager<SecondaryStorageVmVO> {
	private static final Logger s_logger = Logger.getLogger(SecondaryStorageManagerImpl.class);

	private static final int DEFAULT_FIND_HOST_RETRY_COUNT = 2;
	private static final int DEFAULT_CAPACITY_SCAN_INTERVAL = 30000; 		// 30 seconds
	private static final int EXECUTOR_SHUTDOWN_TIMEOUT = 1000; 				// 1 second

	private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION = 3; 	// 3 seconds
	private static final int ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC = 180; 		// 3 minutes
	
	private static final int API_WAIT_TIMEOUT = 5000;							// 5 seconds (in milliseconds)
	private static final int STARTUP_DELAY = 60000; 							// 60 seconds

	
	
	private String _mgmt_host;
	private int _mgmt_port = 8250;
	private int _secStorageVmCmdPort = 8001;

	private String _name;
	private Adapters<SecondaryStorageVmAllocator> _ssVmAllocators;

	private SecondaryStorageVmDao _secStorageVmDao;
	private DataCenterDao _dcDao;
	private VlanDao _vlanDao;
	private VMTemplateDao _templateDao;
	private IPAddressDao _ipAddressDao;
	private VolumeDao _volsDao;
	private HostPodDao _podDao;
	private HostDao _hostDao;
	private StoragePoolDao _storagePoolDao;
	private StoragePoolHostDao _storagePoolHostDao;
	private UserVmDao _userVmDao;
	private VMInstanceDao _instanceDao;
    private AccountDao _accountDao;

	private VMTemplateHostDao _vmTemplateHostDao;
	private CapacityDao _capacityDao;
	private HighAvailabilityDao _haDao;

	private AgentManager _agentMgr;
	private NetworkManager _networkMgr;
	private StorageManager _storageMgr;
	
	private ClusterManager _clusterMgr;

	private SecondaryStorageListener _listener;
	
    private ServiceOfferingVO _serviceOffering;
    private DiskOfferingVO _diskOffering;
    private VMTemplateVO _template;
    
    private AsyncJobManager _asyncMgr;

	private final ScheduledExecutorService _capacityScanScheduler = Executors
			.newScheduledThreadPool(1, new NamedThreadFactory("SS-Scan"));
	private final ExecutorService _requestHandlerScheduler = Executors
			.newCachedThreadPool(new NamedThreadFactory("SS-Request-handler"));
	
	private long _capacityScanInterval = DEFAULT_CAPACITY_SCAN_INTERVAL;


	private int _secStorageVmRamSize;
	private int _find_host_retry = DEFAULT_FIND_HOST_RETRY_COUNT;
	private int _ssh_retry;
	private int _ssh_sleep;
	private String _domain;
	private String _instance;
	


	private final GlobalLock _capacityScanLock = GlobalLock.getInternLock(getCapacityScanLockName());
	private final GlobalLock _allocLock = GlobalLock.getInternLock(getAllocLockName());
	
	
	private static boolean isInAssignableState(SecondaryStorageVmVO secStorageVm) {
		// console proxies that are in states of being able to serve user VM
		State state = secStorageVm.getState();
		if (state == State.Running || state == State.Starting
				|| state == State.Creating || state == State.Migrating)
			return true;

		return false;
	}

	

	@Override
	public SecondaryStorageVmVO startSecStorageVm(long secStorageVmId) {
		try {
			return start(secStorageVmId);
		} catch (StorageUnavailableException e) {
			s_logger.warn("Exception while trying to start secondary storage vm", e);
			return null;
		} catch (InsufficientCapacityException e) {
			s_logger.warn("Exception while trying to start secondary storage vm", e);
			return null;
		} catch (ConcurrentOperationException e) {
			s_logger.warn("Exception while trying to start secondary storage vm", e);
			return null;
		}
	}

	@Override @DB
	public SecondaryStorageVmVO start(long secStorageVmId) throws StorageUnavailableException, InsufficientCapacityException, ConcurrentOperationException {

        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Start secondary storage vm " + secStorageVmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "sec_storage_vm", secStorageVmId);
        }
		
		SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findById(secStorageVmId);
		if (secStorageVm == null || secStorageVm.getRemoved() != null) {
			s_logger.debug("secondary storage vm is not found: " + secStorageVmId);
			return null;
		}

		if (s_logger.isTraceEnabled()) {
			s_logger.trace("Starting secondary storage vm if it is not started, secondary storage vm vm id : " + secStorageVmId);
		}

		for (int i = 0; i < 2; i++) {

			State state = secStorageVm.getState();

			if (state == State.Starting /* || state == State.Migrating */) {
				if (s_logger.isDebugEnabled())
					s_logger.debug("Waiting secondary storage vm to be ready, secondary storage vm id : "
						+ secStorageVmId
						+ " secStorageVm VM state : "
						+ state.toString());

				if (secStorageVm.getPrivateIpAddress() == null || connect(secStorageVm.getPrivateIpAddress(), _secStorageVmCmdPort) != null) {
					if (secStorageVm.getPrivateIpAddress() == null)
						s_logger.warn("Retruning a secondary storage vm that is being started but private IP has not been allocated yet, secondary storage vm id : "
							+ secStorageVmId);
					else
						s_logger.warn("Waiting secondary storage vm to be ready timed out, secondary storage vm id : "
							+ secStorageVmId);

					// TODO, it is very tricky here, if the startup process
					// takes too long and it timed out here,
					// we may give back a secondary storage vm that is not fully ready for
					// functioning
				}
				return secStorageVm;
			}

			if (state == State.Running) {
				if (s_logger.isTraceEnabled()) 
					s_logger.trace("Secondary storage vm is already started: "
							+ secStorageVm.getName());
				return secStorageVm;
			}

			DataCenterVO dc = _dcDao.findById(secStorageVm.getDataCenterId());
			HostPodVO pod = _podDao.findById(secStorageVm.getPodId());
			StoragePoolVO sp = _storagePoolDao.findById(secStorageVm.getPoolId());

			HashSet<Host> avoid = new HashSet<Host>();
			HostVO routingHost = (HostVO) _agentMgr.findHost(Host.Type.Routing, dc, pod, sp, _serviceOffering, _diskOffering, _template, secStorageVm, null, avoid);

			if (routingHost == null) {
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Unable to find a routing host for " + secStorageVm.toString());
					continue;
				}
			}
			// to ensure atomic state transition to Starting state
			if (!_secStorageVmDao.updateIf(secStorageVm, Event.StartRequested, routingHost.getId())) {
				if (s_logger.isDebugEnabled()) {
					SecondaryStorageVmVO temp = _secStorageVmDao.findById(secStorageVmId);
					s_logger.debug("Unable to start secondary storage vm "
							+ secStorageVm.getName()
							+ " because it is not in a startable state : "
							+ ((temp != null) ? temp.getState().toString() : "null"));
				}
				continue;
			}

			try {
				List<VolumeVO> vols = _volsDao.findByInstance(secStorageVmId);
				VolumeVO vol = vols.get(0);
				HostVO storageHost = _hostDao.findById(vol.getHostId());
				
				// Get the VLAN ID for the Secondary Storage VM
	            IPAddressVO ipVO = _ipAddressDao.findById(secStorageVm.getPublicIpAddress());
	            VlanVO vlan = _vlanDao.findById(ipVO.getVlanDbId());
	            String vlanId = vlan.getVlanId();

				Answer answer = null;
				int retry = _find_host_retry;

				// Secondary storage vm VM will be running at routing hosts as routing
				// hosts have public access to outside network
				do {
					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Trying to start secondary storage vm on host "
								+ routingHost.getName());
					}
					
					List<String> vlanIds = _hostDao.getVlanIds(routingHost.getId().longValue());
	                if (!vlanIds.contains(vlanId)) {
	                	s_logger.debug("Could not start secondary storage vm on host " + routingHost.getName() + " because it does not contain the VLAN " + vlanId + ". Avoiding host...");
	                	avoid.add(routingHost);
	                	continue;
	                }

					String storageIp = _storageMgr.chooseStorageIp(secStorageVm,
							routingHost, storageHost);

					String privateIpAddress = _dcDao.allocatePrivateIpAddress(
							secStorageVm.getDataCenterId(), routingHost.getPodId(),
							secStorageVm.getId());
					if (privateIpAddress == null) {
						s_logger.debug("Not enough ip addresses in " + routingHost.getPodId());
						avoid.add(routingHost);
						continue;
					}

					secStorageVm.setPrivateIpAddress(privateIpAddress);
					secStorageVm.setStorageIp(storageIp);
					_secStorageVmDao.updateIf(secStorageVm, Event.OperationRetry, routingHost.getId());
					secStorageVm = _secStorageVmDao.findById(secStorageVm.getId());

					Map<String, Integer> mappings = _storageMgr.share(secStorageVm, vols, routingHost, true);
					if (mappings == null) {
						s_logger.warn("Can not share " + secStorageVm.getId() + " on host " + vol.getHostId());

						/*SubscriptionMgr.getInstance().notifySubscribers(
							ConsoleProxyManager.ALERT_SUBJECT, this,
							new ConsoleProxyAlertEventArgs(
								ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
								secStorageVm.getDataCenterId(), proxy.getId(), proxy, "Unable to share the mounting storage to target host")
						);*/
						throw new StorageUnavailableException(vol.getHostId());
					}

					// carry the secondary storage vm port info over so that we don't
					// need to configure agent on this
					StartSecStorageVmCommand cmdStart = new StartSecStorageVmCommand(
							_secStorageVmCmdPort, secStorageVm, secStorageVm.getName(), storageIp,
							vols, mappings, _mgmt_host, _mgmt_port);

					if (s_logger.isDebugEnabled())
						s_logger.debug("Sending start command for secondary storage vm "
								+ secStorageVm.getName()
								+ " to "
								+ routingHost.getName());

					answer = _agentMgr.easySend(routingHost.getId(), cmdStart);
					s_logger.debug("StartSecStorageVmCommand Answer: " + (answer != null ? answer : "null"));

					if (s_logger.isDebugEnabled())
						s_logger.debug("Received answer on starting secondary storage vm "
							+ secStorageVm.getName()
							+ " on "
							+ routingHost.getName());

					if (answer != null) {
						if (s_logger.isDebugEnabled()) {
							s_logger.debug("Secondary storage vm " + secStorageVm.getName()
									+ " started on " + routingHost.getName());
						}
						
                		if (answer instanceof StartSecStorageVmAnswer){
                			StartSecStorageVmAnswer rAnswer = (StartSecStorageVmAnswer)answer;
                			if (rAnswer.getPrivateIpAddress() != null) {
                			    secStorageVm.setPrivateIpAddress(rAnswer.getPrivateIpAddress());
                			}
                			if (rAnswer.getPrivateMacAddress() != null) {
                				secStorageVm.setPrivateMacAddress(rAnswer.getPrivateMacAddress());
                			}
                		}
						break;
					}

					avoid.add(routingHost);
					secStorageVm.setPrivateIpAddress(null);
					_dcDao.releasePrivateIpAddress(privateIpAddress, secStorageVm
							.getDataCenterId(), secStorageVm.getId());

					_storageMgr.unshare(secStorageVm, vols, routingHost);
				} while (--retry > 0 && (routingHost = (HostVO) _agentMgr.findHost(
								Host.Type.Routing, dc, pod, sp, _serviceOffering, _diskOffering, _template,
								secStorageVm, null, avoid)) != null);
				if (routingHost == null || retry < 0) {
					
		/*			SubscriptionMgr.getInstance().notifySubscribers(
						ConsolesecStorageVmManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
							proxy.getDataCenterId(), proxy.getId(), proxy, "Unable to find a routing host to run")
					);*/
					
					throw new ExecutionException(
							"Couldn't find a routingHost to run secondary storage vm");
				}

				_secStorageVmDao.updateIf(secStorageVm, Event.OperationSucceeded, routingHost.getId());
				if (s_logger.isDebugEnabled()) {
					s_logger.debug("Secondary storage vm is now started, vm id : " + secStorageVm.getId());
				}

				// If starting the secondary storage vm failed due to the external
				// firewall not being reachable, send an alert.
				if (answer != null && answer.getDetails() != null
						&& answer.getDetails().equals("firewall")) {
					
				/*	SubscriptionMgr.getInstance().notifySubscribers(
						ConsoleProxyManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_FIREWALL_ALERT,
							proxy.getDataCenterId(), proxy.getId(), proxy, null)
					);*/
				}

			/*	SubscriptionMgr.getInstance().notifySubscribers(
					ConsoleProxyManager.ALERT_SUBJECT, this,
					new ConsoleProxyAlertEventArgs(
						ConsoleProxyAlertEventArgs.PROXY_UP,
						proxy.getDataCenterId(), proxy.getId(), proxy, null)
				);*/
				
				return secStorageVm;
			} catch (Throwable thr) {
				s_logger.warn("Unexpected exception: ", thr);
				
	/*			SubscriptionMgr.getInstance().notifySubscribers(
					ConsoleProxyManager.ALERT_SUBJECT, this,
					new ConsoleProxyAlertEventArgs(
						ConsoleProxyAlertEventArgs.PROXY_START_FAILURE,
						proxy.getDataCenterId(), proxy.getId(), proxy, "Unexpected exception: " + thr.getMessage())
				);*/

				Transaction txn = Transaction.currentTxn();
				try {
					txn.start();
					String privateIpAddress = secStorageVm.getPrivateIpAddress();
					if (privateIpAddress != null) {
						secStorageVm.setPrivateIpAddress(null);
						_dcDao.releasePrivateIpAddress(privateIpAddress, secStorageVm.getDataCenterId(), secStorageVm.getId());
					}
					secStorageVm.setStorageIp(null);
					_secStorageVmDao.updateIf(secStorageVm, Event.OperationFailed, null);
					txn.commit();
				} catch (Exception e) {
					s_logger.error("Caught exception during error recovery");
				}

				if (thr instanceof StorageUnavailableException) {
					throw (StorageUnavailableException) thr;
				} else if (thr instanceof ConcurrentOperationException) {
					throw (ConcurrentOperationException) thr;
				} else if (thr instanceof ExecutionException) {
					s_logger.error("Error while starting secondary storage vm due to " + thr.getMessage());
				} else {
					s_logger.error("Error while starting secondary storage vm ", thr);
				}
				return null;
			}
		}

		s_logger.warn("Starting secondary storage vm encounters non-startable situation");
		return null;
	}

	



	public SecondaryStorageVmVO startNew(long dataCenterId) {

		if (s_logger.isDebugEnabled())
			s_logger.debug("Assign secondary storage vm from a newly started instance for request from data center : " + dataCenterId);

		Map<String, Object> context = createSecStorageVmInstance(dataCenterId);

		long secStorageVmId = (Long) context.get("secStorageVmId");
		if (secStorageVmId == 0) {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Creating secondary storage vm instance failed, data center id : " + dataCenterId);

			// release critical system resource on failure
			if (context.get("publicIpAddress") != null)
				freePublicIpAddress((String) context.get("publicIpAddress"));

			return null;
		}

		SecondaryStorageVmVO secStorageVm = allocSecStorageVmStorage(dataCenterId, secStorageVmId);
		if (secStorageVm != null) {
		/*	SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
				new ConsoleProxyAlertEventArgs(
					ConsoleProxyAlertEventArgs.PROXY_CREATED,
					dataCenterId, proxy.getId(), proxy, null)
			);*/
			return secStorageVm;
		} else {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Unable to allocate secondary storage vm storage, remove the secondary storage vm record from DB, secondary storage vm id: "
					+ secStorageVmId);
			
/*			SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
				new ConsoleProxyAlertEventArgs(
					ConsoleProxyAlertEventArgs.PROXY_CREATE_FAILURE,
					dataCenterId, proxyVmId, null, "Unable to allocate storage")
			);*/
			destroySecStorageVmDBOnly(secStorageVmId);
		}
		return null;
	}

	@DB
	protected Map<String, Object> createSecStorageVmInstance(long dataCenterId) {

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
				s_logger.warn("Unable to allocate pod for secondary storage vm in data center : " + dataCenterId);

				context.put("secStorageVmId", (long) 0);
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
			long id = _secStorageVmDao.getNextInSequence(Long.class, "id");

			publicIpAddress = allocPublicIpAddress(dataCenterId);
			if (publicIpAddress == null) {
				s_logger.warn("Unable to allocate public IP address for secondary storage vm in data center : " + dataCenterId);

				context.put("secStorageVmId", (long) 0);
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
			SecondaryStorageVmVO secStorageVm;
			String name = VirtualMachineName.getSystemVmName(id, _instance, "s").intern();
			secStorageVm = new SecondaryStorageVmVO(id, name, State.Creating,
					privateMacAddress, null, cidrNetmask, _template.getId(), _template.getGuestOSId(),
					publicMacAddress, publicIpAddress, vlanNetmask, vlan.getId(), vlan.getVlanId(),
					pod.getId(), dataCenterId, vlanGateway, null,
					dc.getDns1(), dc.getDns2(), _domain, _secStorageVmRamSize);

			long secStorageVmId = _secStorageVmDao.persist(secStorageVm);
			txn.commit();

			context.put("secStorageVmId", secStorageVmId);
			return context;
		} catch (Throwable e) {
			s_logger.error("Unexpected exception : ", e);

			context.put("secStorageVmId", (long) 0);
			return context;
		}
	}

	@DB
	protected SecondaryStorageVmVO allocSecStorageVmStorage(long dataCenterId, long secStorageVmId) {
		SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findById(secStorageVmId);
		assert (secStorageVm != null);

		DataCenterVO dc = _dcDao.findById(dataCenterId);
		HostPodVO pod = _podDao.findById(secStorageVm.getPodId());
		long poolId = 0;
        final AccountVO account = _accountDao.findById(Account.ACCOUNT_ID_SYSTEM);
		
        try {
			poolId = _storageMgr.create(account, secStorageVm, _template, dc, pod, _serviceOffering, _diskOffering);
			if( poolId == 0){
				s_logger.error("Unable to alloc storage for secondary storage vm");
				return null;
			}
			
			Transaction txn = Transaction.currentTxn();
			txn.start();
			
			// update pool id
			SecondaryStorageVmVO vo = _secStorageVmDao.findById(secStorageVm.getId());
			vo.setPoolId(poolId);
			_secStorageVmDao.update(secStorageVm.getId(), vo);
			
			// kick the state machine
			_secStorageVmDao.updateIf(secStorageVm, Event.OperationSucceeded, null);
			
			txn.commit();
			return secStorageVm;
		} catch (StorageUnavailableException e) {
			s_logger.error("Unable to alloc storage for secondary storage vm: ", e);
			return null;
		} catch (ExecutionException e) {
			s_logger.error("Unable to alloc storage for secondary storage vm: ", e);
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

	private SecondaryStorageVmAllocator getCurrentAllocator() {

		// for now, only one adapter is supported
		Enumeration<SecondaryStorageVmAllocator> it = _ssVmAllocators.enumeration();
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

	
	

	private void checkPendingSecStorageVMs() {
		// drive state to change away from transient states
		List<SecondaryStorageVmVO> l = _secStorageVmDao.getSecStorageVmListInStates(State.Creating);
		if (l != null && l.size() > 0) {
			for (SecondaryStorageVmVO secStorageVm : l) {
				if (secStorageVm.getLastUpdateTime() == null ||
					(secStorageVm.getLastUpdateTime() != null && System.currentTimeMillis() - secStorageVm.getLastUpdateTime().getTime() > 60000)) {
					try {
						SecondaryStorageVmVO readysecStorageVm = null;
						if (_allocLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
							try {
								readysecStorageVm = allocSecStorageVmStorage(secStorageVm.getDataCenterId(), secStorageVm.getId());
							} finally {
								_allocLock.unlock();
							}

							if (readysecStorageVm != null) {
								GlobalLock secStorageVmLock = GlobalLock.getInternLock(getSecStorageVmLockName(readysecStorageVm.getId()));
								try {
									if (secStorageVmLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
										try {
											readysecStorageVm = start(readysecStorageVm.getId());
										} finally {
											secStorageVmLock.unlock();
										}
									} else {
										if (s_logger.isInfoEnabled())
											s_logger.info("Unable to acquire synchronization lock to start secondary storage vm : " + readysecStorageVm.getName());
									}
								} finally {
									secStorageVmLock.releaseRef();
								}
							}
						} else {
							if (s_logger.isInfoEnabled())
								s_logger.info("Unable to acquire synchronization lock to allocate secondary storage vm storage, wait for next turn");
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
					s_logger.trace("Begin secondary storage vm capacity scan");
				
				Map<Long, ZoneHostInfo> zoneHostInfoMap = getZoneHostInfo();
				if (isServiceReady(zoneHostInfoMap)) {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Sec Storage VM Service is ready, check to see if we need to allocate standby capacity");

					if (!_capacityScanLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_COOPERATION)) {
						if (s_logger.isTraceEnabled())
							s_logger.trace("Sec Storage VM Capacity scan lock is used by others, skip and wait for my turn");
						return;
					}

					if (s_logger.isTraceEnabled())
						s_logger.trace("*** Begining secondary storage vm capacity scan... ***");

					try {
						checkPendingSecStorageVMs();
						
						List<DataCenterVO> datacenters = _dcDao.listAll();


						for (DataCenterVO dc: datacenters){
							if(isZoneReady(zoneHostInfoMap, dc.getId())) {
								List<SecondaryStorageVmVO> alreadyRunning = _secStorageVmDao.getSecStorageVmListInStates(dc.getId(), State.Running, State.Migrating, State.Creating, State.Starting);
								if (alreadyRunning.size() == 0) {
									s_logger.info("No secondary storage vms found in datacenter id=" + dc.getId() + ", starting a new one" );
									allocCapacity(dc.getId());
								}
							} else {
								if(s_logger.isDebugEnabled())
									s_logger.debug("Zone " + dc.getId() + " is not ready to alloc secondary storage vm");
						}
						}

						if (s_logger.isTraceEnabled())
							s_logger.trace("*** Stop secondary storage vm capacity scan ***");
					} finally {
						_capacityScanLock.unlock();
					}

				} else {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Secondary storage vm service is not ready for capacity preallocation, wait for next time");
				}

				if (s_logger.isTraceEnabled())
					s_logger.trace("End of secondary storage vm capacity scan");
			}
		};
	}



	public SecondaryStorageVmVO assignSecStorageVmFromRunningPool(long dataCenterId) {

		if (s_logger.isTraceEnabled())
			s_logger.trace("Assign console secondary storage vm from running pool for request from data center : " + dataCenterId);

		SecondaryStorageVmAllocator allocator = getCurrentAllocator();
		assert (allocator != null);
		List<SecondaryStorageVmVO> runningList = _secStorageVmDao.getSecStorageVmListInStates(dataCenterId, State.Running);
		if (runningList != null && runningList.size() > 0) {
			if (s_logger.isTraceEnabled()) {
				s_logger.trace("Running secondary storage vm pool size : " + runningList.size());
				for (SecondaryStorageVmVO secStorageVm : runningList)
					s_logger.trace("Running secStorageVm instance : " + secStorageVm.getName());
			}

			Map<Long, Integer> loadInfo = new HashMap<Long, Integer>();
			
			return allocator.allocSecondaryStorageVm(runningList, loadInfo, dataCenterId);
		} else {
			if (s_logger.isTraceEnabled())
				s_logger.trace("Empty running secStorageVm pool for now in data center : " + dataCenterId);
		}
		return null;
	}

	public SecondaryStorageVmVO assignSecStorageVmFromStoppedPool(long dataCenterId) {
		List<SecondaryStorageVmVO> l = _secStorageVmDao.getSecStorageVmListInStates(
				dataCenterId, State.Creating, State.Starting, State.Stopped,
				State.Migrating);
		if (l != null && l.size() > 0)
			return l.get(0);

		return null;
	}

	private void allocCapacity(long dataCenterId) {
		if (s_logger.isTraceEnabled())
			s_logger.trace("Allocate secondary storage vm standby capacity for data center : " + dataCenterId);

		boolean secStorageVmFromStoppedPool = false;
		SecondaryStorageVmVO secStorageVm = assignSecStorageVmFromStoppedPool(dataCenterId);
		if (secStorageVm == null) {
			if (s_logger.isInfoEnabled())
				s_logger.info("No stopped secondary storage vm is available, need to allocate a new secondary storage vm");

			if (_allocLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
				try {
					secStorageVm = startNew(dataCenterId);
				} finally {
					_allocLock.unlock();
				}
			} else {
				if (s_logger.isInfoEnabled())
					s_logger.info("Unable to acquire synchronization lock to allocate secStorageVm resource for standby capacity, wait for next scan");
				return;
			}
		} else {
			if (s_logger.isInfoEnabled())
				s_logger.info("Found a stopped secondary storage vm, bring it up to running pool. secStorageVm vm id : " + secStorageVm.getId());
			secStorageVmFromStoppedPool = true;
		}

		if (secStorageVm != null) {
			long secStorageVmId = secStorageVm.getId();
			GlobalLock secStorageVmLock = GlobalLock.getInternLock(getSecStorageVmLockName(secStorageVmId));
			try {
				if (secStorageVmLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
					try {
						secStorageVm = startSecStorageVm(secStorageVmId);
					} finally {
						secStorageVmLock.unlock();
					}
				} else {
					if (s_logger.isInfoEnabled())
						s_logger.info("Unable to acquire synchronization lock to start secStorageVm for standby capacity, secStorageVm vm id : "
							+ secStorageVm.getId());
					return;
				}
			} finally {
				secStorageVmLock.releaseRef();
			}

			if (secStorageVm == null) {
				if (s_logger.isInfoEnabled())
					s_logger.info("Unable to start secondary storage vm for standby capacity, secStorageVm vm Id : "
						+ secStorageVmId + ", will recycle it and start a new one");

				if (secStorageVmFromStoppedPool)
					destroySecStorageVm(secStorageVmId);
			} else {
				if (s_logger.isInfoEnabled())
					s_logger.info("Secondary storage vm " + secStorageVm.getName() + " is started");
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
	        	
	        	List<Pair<Long, Integer>> l = _storagePoolHostDao.getDatacenterStoragePoolHostInfo(dataCenterId);
	        	if(l != null && l.size() > 0 && l.get(0).second().intValue() > 0) {
	        		return true;
	        	} else {
					if (s_logger.isTraceEnabled())
						s_logger.trace("Primary storage is not ready, wait until it is ready to launch secondary storage vm");
	        	}
	        } else {
				if (s_logger.isTraceEnabled())
					s_logger.trace("Zone host is ready, but secondary storage vm template is not ready");
	        }
		}
		return false;
	}
	
	private synchronized Map<Long, ZoneHostInfo> getZoneHostInfo() {
		Date cutTime = DateUtil.currentGMTTime();
		List<RunningHostCountInfo> l = _hostDao.getRunningHostCounts(new Date(cutTime.getTime() - _clusterMgr.getHeartbeatThreshold()));

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
			s_logger.info("Start secondary storage vm manager");

		return true;
	}

	@Override
	public boolean stop() {
		if (s_logger.isInfoEnabled())
			s_logger.info("Stop secondary storage vm manager");
		_capacityScanScheduler.shutdownNow();

		try {
			_capacityScanScheduler.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
		}

		_capacityScanLock.releaseRef();
		_allocLock.releaseRef();
		return true;
	}

	@Override
	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		if (s_logger.isInfoEnabled())
			s_logger.info("Start configuring secondary storage vm manager : " + name);

		_name = name;

		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
		if (configDao == null) {
			throw new ConfigurationException("Unable to get the configuration dao.");
		}

		Map<String, String> configs = configDao.getConfiguration("management-server", params);

		_secStorageVmRamSize = NumbersUtil.parseInt(configs.get("secstorage.vm.ram.size"), DEFAULT_SS_VM_RAMSIZE);

		String value = configs.get("start.retry");
		_find_host_retry = NumbersUtil.parseInt(value, DEFAULT_FIND_HOST_RETRY_COUNT);

		value = configs.get("secstorage.vm.cmd.port");
		_secStorageVmCmdPort = NumbersUtil.parseInt(value, 8001);
		
		
		value = configs.get("secstorage.capacityscan.interval");
		_capacityScanInterval = NumbersUtil.parseLong(value, DEFAULT_CAPACITY_SCAN_INTERVAL);


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

		_secStorageVmDao = locator.getDao(SecondaryStorageVmDao.class);
		if (_secStorageVmDao == null) {
			throw new ConfigurationException("Unable to get " + SecondaryStorageVmDao.class.getName());
		}

		_ssVmAllocators = locator.getAdapters(SecondaryStorageVmAllocator.class);
		if (_ssVmAllocators == null || !_ssVmAllocators.isSet()) {
			throw new ConfigurationException("Unable to get secStorageVm allocators");
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
		
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
            throw new ConfigurationException("Unable to find " + StoragePoolDao.class);
        }
        
        _storagePoolHostDao = locator.getDao(StoragePoolHostDao.class);
        if (_storagePoolHostDao == null) {
            throw new ConfigurationException("Unable to find " + StoragePoolHostDao.class);
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

		_listener = new SecondaryStorageListener(this);
		_agentMgr.registerForHostEvents(_listener, true, true);

		_storageMgr = locator.getManager(StorageManager.class);
		if (_storageMgr == null) {
			throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
		}

		_clusterMgr = locator.getManager(ClusterManager.class);
		if (_clusterMgr == null) {
			throw new ConfigurationException("Unable to get " + ClusterManager.class.getName());
		}
		
        _asyncMgr = locator.getManager(AsyncJobManager.class);
		if (_asyncMgr == null) {
			throw new ConfigurationException("Unable to get " + AsyncJobManager.class.getName());
		}

		HighAvailabilityManager haMgr = locator.getManager(HighAvailabilityManager.class);
		if (haMgr != null) {
			haMgr.registerHandler(VirtualMachine.Type.SecondaryStorageVm, this);
		}

		_serviceOffering = new ServiceOfferingVO(null, "Fake Offering For Secondary Storage VM", 1, _secStorageVmRamSize, 0, 0, 0, true, null, false);
		_diskOffering = new DiskOfferingVO(1, "fake disk offering", "fake disk offering", 0, false);
        _template = _templateDao.findConsoleProxyTemplate();
        if (_template == null) {
            throw new ConfigurationException("Unable to find the template for secondary storage vm VMs");
        }
 
//		_capacityScanScheduler.scheduleAtFixedRate(getCapacityScanTask(), STARTUP_DELAY,
//				_capacityScanInterval, TimeUnit.MILLISECONDS);
//		
		if (s_logger.isInfoEnabled())
			s_logger.info("Secondary storage vm Manager is configured.");
		return true;
	}

	protected SecondaryStorageManagerImpl() {
	}

	@Override
	public Command cleanup(SecondaryStorageVmVO vm, String vmName) {
		if (vmName != null) {
			return new StopCommand(vm, vmName, VirtualMachineName.getVnet(vmName));
		} else if (vm != null) {
			SecondaryStorageVmVO vo = vm;
			return new StopCommand(vo, null);
		} else {
			throw new VmopsRuntimeException("Shouldn't even be here!");
		}
	}

	@Override
	public void completeStartCommand(SecondaryStorageVmVO vm) {
		_secStorageVmDao.updateIf(vm, Event.AgentReportRunning, vm.getHostId());
	}

	@Override
	public void completeStopCommand(SecondaryStorageVmVO vm) {
		completeStopCommand(vm, Event.AgentReportStopped);
	}

	@DB
	protected void completeStopCommand(SecondaryStorageVmVO secStorageVm, Event ev) {
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			String privateIpAddress = secStorageVm.getPrivateIpAddress();
			if (privateIpAddress != null) {
				secStorageVm.setPrivateIpAddress(null);
				_dcDao.releasePrivateIpAddress(privateIpAddress, secStorageVm.getDataCenterId(), secStorageVm.getId());
			}
			secStorageVm.setStorageIp(null);
			if (!_secStorageVmDao.updateIf(secStorageVm, ev, null)) {
				s_logger.debug("Unable to update the secondary storage vm");
				return;
			}
			txn.commit();
		} catch (Exception e) {
			s_logger.error("Unable to complete stop command due to ", e);
		}

		if (_storageMgr.unshare(secStorageVm, null) == null) {
			s_logger.warn("Unable to set share to false for " + secStorageVm.getId());
		}
	}

	@Override
	public SecondaryStorageVmVO get(long id) {
		return _secStorageVmDao.findById(id);
	}

	@Override
	public Long convertToId(String vmName) {
		if (!VirtualMachineName.isValidSystemVmName(vmName, _instance, "s")) {
			return null;
		}
		return VirtualMachineName.getSystemVmId(vmName);
	}

	@Override
	public boolean stopSecStorageVm(long secStorageVmId) {
		
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Stop secondary storage vm " + secStorageVmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "console_secStorageVm", secStorageVmId);
        }
		
		SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findById(secStorageVmId);
		if (secStorageVm == null) {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Stopping secondary storage vm failed: secondary storage vm " + secStorageVmId + " no longer exists");
			return false;
		}
		try {
			return stop(secStorageVm);
		} catch (AgentUnavailableException e) {
			if (s_logger.isDebugEnabled())
				s_logger.debug("Stopping secondary storage vm " + secStorageVm.getName() + " faled : exception " + e.toString());
			return false;
		}
	}

	@Override
	public boolean rebootSecStorageVm(long secStorageVmId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Reboot secondary storage vm " + secStorageVmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "secstorage_vm", secStorageVmId);
        }
        
		final SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findById(secStorageVmId);

		if (secStorageVm == null || secStorageVm.getState() == State.Destroyed) {
			return false;
		}

		if (secStorageVm.getState() == State.Running && secStorageVm.getHostId() != null) {
			final RebootCommand cmd = new RebootCommand(secStorageVm.getInstanceName());
			final Answer answer = _agentMgr.easySend(secStorageVm.getHostId(), cmd);

			if (answer != null) {
				if (s_logger.isDebugEnabled())
					s_logger.debug("Successfully reboot secondary storage vm " + secStorageVm.getName());
				
/*				SubscriptionMgr.getInstance().notifySubscribers(
						ConsoleProxyManager.ALERT_SUBJECT, this,
						new ConsoleProxyAlertEventArgs(
							ConsoleProxyAlertEventArgs.PROXY_REBOOTED,
							secStorageVm.getDataCenterId(), secStorageVm.getId(), secStorageVm, null)
					);*/
				
				return true;
			} else {
				if (s_logger.isDebugEnabled())
					s_logger.debug("failed to reboot secondary storage vm : " + secStorageVm.getName());
				return false;
			}
		} else {
			return startSecStorageVm(secStorageVmId) != null;
		}
	}

	@Override
	public boolean destroy(SecondaryStorageVmVO secStorageVm)
			throws AgentUnavailableException {
		return destroySecStorageVm(secStorageVm.getId());
	}

	@Override
	@DB
	public boolean destroySecStorageVm(long vmId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Destroy secondary storage vm " + vmId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "secstorage_vm", vmId);
        }
		
		SecondaryStorageVmVO vm = _secStorageVmDao.findById(vmId);
		if (vm == null || vm.getState() == State.Destroyed) {
			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Unable to find vm or vm is destroyed: " + vmId);
			}
			return true;
		}

		if (s_logger.isDebugEnabled()) {
			s_logger.debug("Destroying secondary storage vm vm " + vmId);
		}

		if (!_secStorageVmDao.updateIf(vm, Event.DestroyRequested, null)) {
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
//						DataCenterVO dcVO = _dcDao.findById(host
//								.getDataCenterId());
//						HostPodVO podVO = _podDao.findById(host.getPodId());
//						String hostDesc = "name: " + host.getName() + " (id:"
//								+ host.getId() + "), zone: "
//								+ dcVO.getName() + ", pod: " + podVO.getName();

//						String message = "Storage cleanup required due to deletion failure. host: " + hostDesc +", volume: " + vol.getFolder();
//						SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
//							new ConsoleProxyAlertEventArgs(
//								ConsoleProxyAlertEventArgs.PROXY_STORAGE_ALERT,
//								vol.getDataCenterId(), vmId, vm, message)
//						);
						
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
//							DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
//							HostPodVO podVO = _podDao.findById(host.getPodId());
//							String hostDesc = "name: " + host.getName()
//									+ " (id:" + host.getId()
//									+ "), zone: " + dcVO.getName()
//									+ ", pod: " + podVO.getName();

//							String message = "Storage cleanup required due to failure in contacting with storage host. host: " + hostDesc;
							/*SubscriptionMgr.getInstance().notifySubscribers(ConsoleProxyManager.ALERT_SUBJECT, this,
								new ConsoleProxyAlertEventArgs(
									ConsoleProxyAlertEventArgs.PROXY_STORAGE_ALERT,
									dcVO.getId(), vmId, vm, message)
							);*/
							
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

				_secStorageVmDao.remove(vm.getId());
				txn.commit();
			} catch (Exception e) {
				s_logger.error("Caught this error: ", e);
				txn.rollback();
				return false;
			} finally {
				s_logger.debug("secondary storage vm vm is destroyed : "
						+ vm.getName());
			}
		}
	}

	@DB
	public boolean destroySecStorageVmDBOnly(long vmId) {
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			_volsDao.deleteVolumesByInstance(vmId);

			SecondaryStorageVmVO secStorageVm = _secStorageVmDao.findById(vmId);
			if (secStorageVm != null) {
				if (secStorageVm.getPublicIpAddress() != null)
					freePublicIpAddress(secStorageVm.getPublicIpAddress());

				_secStorageVmDao.remove(vmId);
			}
			txn.commit();
			return true;
		} catch (Exception e) {
			s_logger.error("Caught this error: ", e);
			txn.rollback();
			return false;
		} finally {
			s_logger.debug("secondary storage vm vm is destroyed from DB : " + vmId);
		}
	}

	@Override
	public boolean stop(SecondaryStorageVmVO secStorageVm) throws AgentUnavailableException {
		if (!_secStorageVmDao.updateIf(secStorageVm, Event.StopRequested, secStorageVm.getHostId())) {
			s_logger.debug("Unable to stop secondary storage vm: " + secStorageVm.toString());
			return false;
		}

		// IPAddressVO ip = _ipAddressDao.findById(secStorageVm.getPublicIpAddress());
		// VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
		
		GlobalLock secStorageVmLock = GlobalLock.getInternLock(getSecStorageVmLockName(secStorageVm.getId()));
		try {
			if (secStorageVmLock.lock(ACQUIRE_GLOBAL_LOCK_TIMEOUT_FOR_SYNC)) {
				try {
					StopCommand cmd = new StopCommand(secStorageVm, true,
							Integer.toString(0),
							Integer.toString(0),
							secStorageVm.getPublicIpAddress());
					try {
						StopAnswer answer = (StopAnswer) _agentMgr.send(secStorageVm.getHostId(), cmd);
						if (answer == null || !answer.getResult()) {
							s_logger.debug("Unable to stop due to " + (answer == null ? "answer is null" : answer.getDetails()));
							return false;
						}
						completeStopCommand(secStorageVm, Event.OperationSucceeded);
/*						
						SubscriptionMgr.getInstance().notifySubscribers(
								ConsoleProxyManager.ALERT_SUBJECT, this,
								new ConsoleProxyAlertEventArgs(
									ConsoleProxyAlertEventArgs.PROXY_DOWN,
									secStorageVm.getDataCenterId(), secStorageVm.getId(), secStorageVm, null)
							);*/
						return true;
					} catch (OperationTimedoutException e) {
						throw new AgentUnavailableException(secStorageVm.getHostId());
					}
				} finally {
					secStorageVmLock.unlock();
				}
			} else {
				s_logger.debug("Unable to acquire secondary storage vm lock : " + secStorageVm.toString());
				return false;
			}
		} finally {
			secStorageVmLock.releaseRef();
		}
	}

	@Override
	public boolean migrate(SecondaryStorageVmVO secStorageVm, HostVO host) {
		HostVO fromHost = _hostDao.findById(secStorageVm.getId());

		if (!_secStorageVmDao.updateIf(secStorageVm, Event.MigrationRequested, secStorageVm.getHostId())) {
			s_logger.debug("State for " + secStorageVm.toString() + " has changed so migration can not take place.");
			return false;
		}

		MigrateCommand cmd = new MigrateCommand(secStorageVm.getInstanceName(), host.getPrivateIpAddress());
		Answer answer = _agentMgr.easySend(fromHost.getId(), cmd);
		if (answer == null) {
			return false;
		}

		_storageMgr.unshare(secStorageVm, fromHost);

		return true;
	}

	@Override
	public boolean completeMigration(SecondaryStorageVmVO secStorageVm, HostVO host)
			throws AgentUnavailableException, OperationTimedoutException {
		CheckVirtualMachineCommand cvm = new CheckVirtualMachineCommand(secStorageVm.getInstanceName());
		CheckVirtualMachineAnswer answer = (CheckVirtualMachineAnswer) _agentMgr.send(host.getId(), cvm);
		if (!answer.getResult()) {
			s_logger.debug("Unable to complete migration for " + secStorageVm.getId());
			_secStorageVmDao.updateIf(secStorageVm, Event.AgentReportStopped, null);
			return false;
		}

		State state = answer.getState();
		if (state == State.Stopped) {
			s_logger.warn("Unable to complete migration as we can not detect it on " + host.getId());
			_secStorageVmDao.updateIf(secStorageVm, Event.AgentReportStopped, null);
			return false;
		}

		_secStorageVmDao.updateIf(secStorageVm, Event.OperationSucceeded, host.getId());
		return true;
	}

	@Override
	public HostVO prepareForMigration(SecondaryStorageVmVO secStorageVm) throws StorageUnavailableException {
		
		VMTemplateVO template = _templateDao.findById(secStorageVm.getTemplateId());
		long routerId = secStorageVm.getId();
		boolean mirroredVols = secStorageVm.isMirroredVols();
		DataCenterVO dc = _dcDao.findById(secStorageVm.getDataCenterId());
		HostPodVO pod = _podDao.findById(secStorageVm.getPodId());
		StoragePoolVO sp = _storagePoolDao.findById(secStorageVm.getPoolId());

		List<VolumeVO> vols = _volsDao.findByInstance(routerId);

		String[] storageIps = new String[2];
		VolumeVO vol = vols.get(0);
		storageIps[0] = vol.getHostIp();
		if (mirroredVols && (vols.size() == 2)) {
			storageIps[1] = vols.get(1).getHostIp();
		}

		PrepareForMigrationCommand cmd = new PrepareForMigrationCommand(secStorageVm.getName(), null, storageIps, vols, null, mirroredVols);

		HostVO routingHost = null;
		HashSet<Host> avoid = new HashSet<Host>();

		HostVO fromHost = _hostDao.findById(secStorageVm.getHostId());
		avoid.add(fromHost);
		
		List<String> currentHostVlanIds = _hostDao.getVlanIds(secStorageVm.getHostId());

		while ((routingHost = (HostVO) _agentMgr.findHost(Host.Type.Routing,
				dc, pod, sp, _serviceOffering, _diskOffering, template, secStorageVm, fromHost, avoid)) != null) {
			if (routingHost.getPodId() != fromHost.getPodId()) {
				s_logger.debug("Unable to migrate to another pod");
				avoid.add(routingHost);
				continue;
			}
			
			List<String> migrationHostVlanIds = _hostDao.getVlanIds(routingHost.getId().longValue());
        	if (!migrationHostVlanIds.containsAll(currentHostVlanIds)) {
        		s_logger.debug("Cannot migrate secondary storage vm to host " + routingHost.getName() + " because it is missing some VLAN IDs. Skipping host...");
        		avoid.add(routingHost);
        		continue;
        	}

			if (s_logger.isDebugEnabled()) {
				s_logger.debug("Trying to migrate router to host " + routingHost.getName());
			}

			Map<String, Integer> mappings = _storageMgr.share(secStorageVm, vols, routingHost, false);
			if (mappings == null) {
				s_logger.warn("Can not share " + secStorageVm.getId() + " on host " + vol.getHostId());
				throw new StorageUnavailableException(vol.getHostId());
			}
			cmd.setMappings(mappings);

			Answer answer = _agentMgr.easySend(routingHost.getId(), cmd);
			if (answer != null && answer.getResult()) {
				return routingHost;
			}
			_storageMgr.unshare(secStorageVm, vols, routingHost);
		}

		return null;
	}

	private String getCapacityScanLockName() {
		// to improve security, it may be better to return a unique mashed
		// name(for example MD5 hashed)
		return "secStorageVm.capacity.scan";
	}

	private String getAllocLockName() {
		// to improve security, it may be better to return a unique mashed
		// name(for example MD5 hashed)
		return "secStorageVm.alloc";
	}

	private String getSecStorageVmLockName(long id) {
		return "secStorageVm." + id;
	}
}
