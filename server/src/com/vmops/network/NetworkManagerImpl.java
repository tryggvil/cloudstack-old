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
package com.vmops.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.vmops.agent.api.ModifyVlanCommand;
import com.vmops.agent.api.PrepareForMigrationCommand;
import com.vmops.agent.api.RebootAnswer;
import com.vmops.agent.api.RebootRouterCommand;
import com.vmops.agent.api.StartRouterAnswer;
import com.vmops.agent.api.StartRouterCommand;
import com.vmops.agent.api.StopCommand;
import com.vmops.agent.api.routing.DhcpEntryCommand;
import com.vmops.agent.api.routing.IPAssocCommand;
import com.vmops.agent.api.routing.LoadBalancerCfgCommand;
import com.vmops.agent.api.routing.SavePasswordCommand;
import com.vmops.agent.api.routing.SetFirewallRuleCommand;
import com.vmops.agent.api.routing.UserDataCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.alert.AlertManager;
import com.vmops.api.BaseCmd;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.async.executor.AssignToLoadBalancerExecutor;
import com.vmops.async.executor.LoadBalancerParam;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.configuration.ConfigurationManager;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.configuration.dao.ResourceLimitDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.Vlan;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.domain.DomainVO;
import com.vmops.domain.dao.DomainDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.ConcurrentOperationException;
import com.vmops.exception.InsufficientCapacityException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.NetworkRuleConflictException;
import com.vmops.exception.OperationTimedoutException;
import com.vmops.exception.PermissionDeniedException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.exception.StorageUnavailableException;
import com.vmops.ha.HighAvailabilityManager;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.network.NetworkEnums.RouterPrivateIpStrategy;
import com.vmops.network.dao.FirewallRulesDao;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.network.dao.LoadBalancerDao;
import com.vmops.network.dao.SecurityGroupVMMapDao;
import com.vmops.network.listener.RouterStatsListener;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.service.ServiceOffering.GuestIpType;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.storage.dao.DiskTemplateDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.user.Account;
import com.vmops.user.AccountManager;
import com.vmops.user.AccountVO;
import com.vmops.user.UserStatisticsVO;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.user.dao.UserStatisticsDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.StringUtils;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.ExecutionException;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.State;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.VirtualMachineManager;
import com.vmops.vm.VirtualMachineName;
import com.vmops.vm.DomainRouter.Role;
import com.vmops.vm.VirtualMachine.Event;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;

/**
 * NetworkManagerImpl implements NetworkManager.
 */
@Local(value={NetworkManager.class})
public class NetworkManagerImpl implements NetworkManager, VirtualMachineManager<DomainRouterVO> {
    private static final Logger s_logger = Logger.getLogger(NetworkManagerImpl.class);

    String _name;
    DataCenterDao _dcDao = null;
    VlanDao _vlanDao = null;
    FirewallRulesDao _rulesDao = null;
    SecurityGroupVMMapDao _securityGroupVMMapDao = null;
    LoadBalancerDao _loadBalancerDao = null;
    IPAddressDao _ipAddressDao = null;
    VMTemplateDao _templateDao =  null;
    DiskTemplateDao _diskDao = null;
    DomainRouterDao _routerDao = null;
    UserDao _userDao = null;
    AccountDao _accountDao = null;
    DomainDao _domainDao = null;
    UserStatisticsDao _userStatsDao = null;
    VolumeDao _volsDao = null;
    HostDao _hostDao = null;
    EventDao _eventDao = null;
    ConfigurationDao _configDao;
    HostPodDao _podDao = null;
    VMTemplateHostDao _vmTemplateHostDao = null;
    UserVmDao _vmDao = null;
    ResourceLimitDao _limitDao = null;
    CapacityDao _capacityDao = null;
    AgentManager _agentMgr;
    StorageManager _storageMgr;
    HighAvailabilityManager _haMgr;
    AlertManager _alertMgr;
    AccountManager _accountMgr;
    ConfigurationManager _configMgr;
    AsyncJobManager _asyncMgr;
    StoragePoolDao _storagePoolDao = null;

    long _routerTemplateId = -1;
    int _routerRamSize;
    // String _privateNetmask;
    String _guestNetmask;
    String _guestIpAddress;
    int _retry = 2;
    String _domain;
    String _instance;
    int _routerCleanupInterval = 3600;
    private ServiceOfferingVO _offering;
    private DiskOfferingVO _diskOffering;
    private VMTemplateVO _template;
    
    ScheduledExecutorService _executor;
	
	@Override
	public boolean destroy(DomainRouterVO router) {
		return destroyRouter(router.getId());
	}
	
	@Override
	public long findNextVlan(long zoneId) {
		long vlanDbIdToUse = -1;
		List<VlanVO> vlans = _vlanDao.findByZone(zoneId);
		for (VlanVO vlan : vlans) {
			long vlanDbId = vlan.getId();
			
			int countOfAllocatedIps = _ipAddressDao.countIPs(zoneId, vlanDbId, true);
			int countOfAllIps = _ipAddressDao.countIPs(zoneId, vlanDbId, false);
			
			if ((countOfAllocatedIps > 0) && (countOfAllocatedIps < countOfAllIps)) {
				vlanDbIdToUse = vlanDbId;
				break;
			}
		}
		
		if (vlanDbIdToUse == -1) {
			for (VlanVO vlan : vlans) {
				long vlanDbId = vlan.getId();
				
				int countOfAllocatedIps = _ipAddressDao.countIPs(zoneId, vlanDbId, true);
				int countOfAllIps = _ipAddressDao.countIPs(zoneId, vlanDbId, false);

				if (countOfAllocatedIps == 0 && countOfAllIps > 0) {
					vlanDbIdToUse = vlanDbId;
					break;
				}
			}
		}
		
		return vlanDbIdToUse;
	}
	

    @Override @DB
    public String assignSourceNatIpAddress(AccountVO account, final DataCenterVO dc, final String domain, final ServiceOfferingVO serviceOffering) throws ResourceAllocationException {
    	if (serviceOffering.getGuestIpType() == GuestIpType.DirectDual || serviceOffering.getGuestIpType() == GuestIpType.DirectSingle) {
    		return null;
    	}
        final long dcId = dc.getId();
        String sourceNat = null;

        final long accountId = account.getId();
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Locking up account " + account.getAccountName());
        }

        Transaction txn = Transaction.currentTxn();
        try {
            
            txn.start();

        	account = _accountDao.acquire(accountId);
            if (account == null) {
                s_logger.warn("Unable to lock account " + accountId);
                return null;
            }
            if(s_logger.isDebugEnabled())
            	s_logger.debug("lock account " + account + " is acquired");

            final List<IPAddressVO> addrs = _ipAddressDao.listByAccountDcId(account.getId(), dcId, true);
            if (addrs.size() == 0) {
            	
            	// Check that the maximum number of public IPs for the given accountId will not be exceeded
        		if (_accountMgr.resourceLimitExceeded(account, ResourceType.public_ip)) {
        			ResourceAllocationException rae = new ResourceAllocationException("Maximum number of public IP addresses for account: " + account.getAccountName() + " has been exceeded.");
        			rae.setResourceType("ip");
        			throw rae;
        		}
            	
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("assigning a new ip address");
                }
                
                // Figure out which VLAN to grab an IP address from
    			long vlanDbIdToUse = findNextVlan(dcId);
                
                sourceNat = _ipAddressDao.assignIpAddress(account.getId(), account.getDomainId().longValue(), vlanDbIdToUse, true);
                
                // Increment the number of public IPs for this accountId in the database
                if (sourceNat != null) {
                	_accountMgr.incrementResourceCount(accountId, ResourceType.public_ip);
                    final EventVO event = new EventVO();
                    event.setUserId(1L); // system user performed the action...
                    event.setAccountId(account.getId());
                    event.setType(EventTypes.EVENT_NET_IP_ASSIGN);
                    event.setParameters("address=" + sourceNat + "\nsourceNat=true\ndcId="+dcId);
                    event.setDescription("acquired a public ip: " + sourceNat);
                    _eventDao.persist(event);
                }
                
            } else {
                sourceNat = addrs.get(0).getAddress();
            }

            if (sourceNat == null) {
                txn.rollback();
                s_logger.error("Unable to get source nat ip address for account " + account.getId());
                return null;
            }

            UserStatisticsVO stats = _userStatsDao.findBy(account.getId(), dcId);
            if (stats == null) {
                stats = new UserStatisticsVO(account.getId(), dcId);
                _userStatsDao.persist(stats);
            }

            txn.commit();

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Source Nat is " + sourceNat);
            }

            DomainRouterVO router = null;
            try {
                router = createRouter(account.getId(), sourceNat, dcId, domain, serviceOffering);
            } catch (final Exception e) {
                s_logger.error("Unable to create router for " + account.getAccountName(), e);
            }

            if (router != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Router is " + router.getName());
                }
                return sourceNat;
            }

            s_logger.warn("releasing the source nat because router was not created: " + sourceNat);
            txn.start();
            _ipAddressDao.unassignIpAddress(sourceNat);
            _accountMgr.decrementResourceCount(accountId, ResourceType.public_ip);
            EventVO event2 = new EventVO();
            event2.setUserId(1L);
            event2.setAccountId(account.getId());
            event2.setType(EventTypes.EVENT_NET_IP_RELEASE);
            event2.setParameters("address=" + sourceNat + "\nsourceNat=true");
            event2.setDescription("released source nat ip " + sourceNat + " since router could not be started");
            _eventDao.persist(event2);
            txn.commit();
            return null;
        } finally {
        	if (account != null) {
        		if(s_logger.isDebugEnabled())
        			s_logger.debug("Releasing lock account " + accountId);
        		
        		_accountDao.release(accountId);
        	}
        }
    }

    @DB
    public DomainRouterVO createDhcpServerForDirectlyAttachedGuests(DataCenterVO dc, HostPodVO pod, VlanVO guestVlan) throws ConcurrentOperationException{
 
		final String domain = "root";
        final Long adminAccountId = new Long(Account.ACCOUNT_ID_SYSTEM);
        final Long domainId =  DomainVO.ROOT_DOMAIN;
        final AccountVO adminAccount = _accountDao.findById(adminAccountId);

        final VMTemplateVO rtrTemplate = _templateDao.findRoutingTemplate();
        
        final Transaction txn = Transaction.currentTxn();
        DomainRouterVO router = null;
        Long podId = pod.getId();
        pod = _podDao.acquire(podId);
        if (pod == null) {
            	throw new ConcurrentOperationException("Unable to acquire lock on pod " + podId );
        }
        if(s_logger.isDebugEnabled())
        	s_logger.debug("Lock on pod " + podId + " is acquired");
        
        final long id = _routerDao.getNextInSequence(Long.class, "id");
        final String[] macAddresses = _dcDao.getNextAvailableMacAddressPair(dc.getId());
        final String mgmtMacAddress = macAddresses[0];
        final String guestMacAddress = macAddresses[1];
        final String name = VirtualMachineName.getRouterName(id, _instance).intern();

        try {
            long poolId = 0;

            List<DomainRouterVO> rtrs = _routerDao.listByVlanDbId(guestVlan.getId());
            assert rtrs.size() < 2 : "How did we get more than one router per vlan?";
            if (rtrs.size() == 1) {
            	return rtrs.get(0);
            }
            String mgmtNetmask = NetUtils.getCidrNetmask(pod.getCidrSize());
            final String guestIp = _ipAddressDao.assignIpAddress(adminAccountId, domainId.longValue(), guestVlan.getId(), false);
                
                router =
                    new DomainRouterVO(id,
                            name,
                            mgmtMacAddress,
                            null,
                            mgmtNetmask,
                            _routerTemplateId,
                            rtrTemplate.getGuestOSId(),
                            guestMacAddress,
                            guestIp,
                            guestVlan.getVlanNetmask(),
                            adminAccountId,
                            domainId,
                            null,
                            null,
                            null,
                            guestVlan.getId(),
                            guestVlan.getVlanId(),
                            pod.getId(),
                            dc.getId(),
                            _routerRamSize,
                            guestVlan.getVlanGateway(),
                            domain,
                            dc.getDns1(),
                            dc.getDns2());
                router.setRole(Role.DHCP_USERDATA);
                router.setVnet(guestVlan.getVlanId());

                long routerId = _routerDao.persist(router);
                
                router = _routerDao.findById(routerId);

                poolId = _storageMgr.create(adminAccount, router, rtrTemplate, dc, pod, _offering, _diskOffering);
                if (poolId == 0){
                	_ipAddressDao.unassignIpAddress(guestIp);
                	_routerDao.delete(router.getId());
                	if (s_logger.isDebugEnabled()) {
                		s_logger.debug("Unable to create router in storage host or pool in pod " + pod.getName() + " (id:" + pod.getId() + ")");
                	}
                }

            final EventVO event = new EventVO();
            event.setUserId(1L); // system user performed the action
            event.setAccountId(adminAccountId);
            event.setType(EventTypes.EVENT_ROUTER_CREATE);

            if (poolId == 0) {
                event.setDescription("failed to create Domain Router : " + router.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                throw new ExecutionException("Unable to create DomainRouter");
            }
            router.setPoolId(poolId);
            _routerDao.updateIf(router, Event.OperationSucceeded, null);

            s_logger.debug("Router created: id=" + router.getId() + "; name=" + router.getName());

            event.setDescription("successfully created Domain Router : " + router.getName() + " with ip : " + router.getGuestIpAddress());
            _eventDao.persist(event);

            return router;
        } catch (final Throwable th) {
            if (th instanceof ExecutionException) {
                s_logger.error("Error while starting router due to " + th.getMessage());
            } else {
                s_logger.error("Unable to create router", th);
            }
            txn.rollback();

            if (router.getState() == State.Creating) {
                _routerDao.delete(router.getId());
            }
            return null;
        } finally {
            if (pod != null) {
                if(s_logger.isDebugEnabled())
                	s_logger.debug("Releasing lock on pod " + podId);
            	_podDao.release(pod.getId());
            }
        }
	}

	@Override
    public DomainRouterVO assignRouter(final long userId, final long accountId, final long dataCenterId, final long podId, final String domain, final String instance) throws InsufficientCapacityException {
        return null;
    }

    @Override
    public boolean releaseRouter(final long routerId) {
        return destroyRouter(routerId);
    }
    
    @Override
	public VlanVO addOrDeleteVlan(long zoneId, boolean add, String vlanId, String vlanGateway, String vlanNetmask, String description, String name) throws InvalidParameterValueException{
		
    	VlanVO vlan = null;
		// Ask ConfigurationManager to add the VLAN to the database
    	try {
    		vlan = _configMgr.addOrDeleteVlan(zoneId, add, vlanId, vlanGateway, vlanNetmask, description, name);
    	} catch (InvalidParameterValueException ex) {
    		throw new InvalidParameterValueException (ex.getMessage());
    	}
    	

		// Persist the VLAN to all Routing Servers in the specified zone

		// Get list of Routing Servers in zone
		List<HostVO> successHosts = new ArrayList<HostVO>();
    	List<HostVO> routingHosts = _hostDao.listByTypeDataCenter(Host.Type.Routing, zoneId);
    	
    	// Iterate through all the Routing Servers and send each one a ModifyVlanCommand
    	// If the command did not work for a particular host, send an alert
    	
    	String errorMsg;
    	if (add)
    		errorMsg = "Failed to add VLAN to host: ";
    	else
    		errorMsg = "Failed to delete VLAN from host: ";
    	
    	for (HostVO host : routingHosts) {
    		ModifyVlanCommand cmd = new ModifyVlanCommand(add, vlanId, vlanGateway);
    		if (_agentMgr.easySend(host.getId(), cmd) != null) {
    			successHosts.add(host);
    		} else {
    			_alertMgr.sendAlert(AlertManager.ALERT_TYPE_VLAN, zoneId, null, "VLAN Error", errorMsg + host.getName());
    		}
    	}
		
		// Iterate through the list of hosts for which the operation was successful, update HostVlanMapDao for each host
		for (HostVO host : successHosts) {
			long hostId = host.getId().longValue();
			if (add)
				_hostDao.addVlan(hostId, vlanId);
			else
				_hostDao.removeVlan(hostId, vlanId);
		}
		return vlan;
	}
    
    public boolean addVlanToHost(Long hostId, String vlanId, String vlanGateway) {
    	ModifyVlanCommand cmd = new ModifyVlanCommand(true, vlanId, vlanGateway);
    	final Answer answer = _agentMgr.easySend(hostId, cmd);
    	
    	if (answer != null)
    		return true;
    	else
    		return false;
    }

    @Override @DB
    public DomainRouterVO createRouter(final long accountId, final String publicIpAddress, final long dataCenterId, String domain, final ServiceOfferingVO offering) throws ConcurrentOperationException {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating a router for account=" + accountId + "; publicIpAddress=" + publicIpAddress + "; dc=" + dataCenterId + "domain=" + domain);
        }
        
        final AccountVO account = _accountDao.acquire(accountId);
        if (account == null) {
        	throw new ConcurrentOperationException("Unable to acquire account " + accountId);
        }
        
        if(s_logger.isDebugEnabled())
        	s_logger.debug("lock on account " + accountId + " for createRouter is acquired");

        final Transaction txn = Transaction.currentTxn();
        DomainRouterVO router = null;
        try {
            router = _routerDao.findBy(accountId, dataCenterId);
            if (router != null && router.getState() != State.Creating) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Router " + router.toString() + " found for account " + accountId + " in data center " + dataCenterId);
                }
                return router;
            }
            final DataCenterVO dc = _dcDao.findById(dataCenterId);
            final VMTemplateVO template = _templateDao.findRoutingTemplate();

            final String[] macAddresses = _dcDao.getNextAvailableMacAddressPair(dataCenterId);
            final String privateMacAddress = macAddresses[0];
            String publicMacAddress = macAddresses[1];
            long inst = NumbersUtil.parseInt(_instance, _instance.hashCode());
            long mac = NetUtils.mac2Long(publicMacAddress);
            mac = mac | ((inst << 16) & 0x00000000ffff0000l);
            publicMacAddress = NetUtils.long2Mac(mac);

            final long id = _routerDao.getNextInSequence(Long.class, "id");

            if (domain == null) {
                domain = "v" + Long.toHexString(accountId) + "." + _domain;
            }

            final String name = VirtualMachineName.getRouterName(id, _instance).intern();
            long routerMacAddress = NetUtils.mac2Long(dc.getRouterMacAddress()) | ((dc.getId() & 0xff) << 32);

//            String path = null;
//            final int numVolumes = offering.isMirroredVolumes()?2:1;
            long routerId = 0;
            
            // Find the VLAN ID, VLAN gateway, and VLAN netmask for publicIpAddress
            IPAddressVO ipVO = _ipAddressDao.findById(publicIpAddress);
            VlanVO vlan = _vlanDao.findById(ipVO.getVlanDbId());
            String vlanId = vlan.getVlanId();
            String vlanGateway = vlan.getVlanGateway();
            String vlanNetmask = vlan.getVlanNetmask();

            long poolId = 0;
            HostPodVO pod = null;
            Set<Long> avoids = new HashSet<Long>();
            while ((pod = _agentMgr.findPod(template, offering, dc, accountId, avoids)) != null) {
                
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Attempting to create in pod " + pod.getName());
                }
                
                String cidrNetmask = NetUtils.getCidrNetmask(pod.getCidrSize());
                
                router =
                    new DomainRouterVO(id,
                            name,
                            privateMacAddress,
                            null,
                            cidrNetmask,
                            _routerTemplateId,
                            template.getGuestOSId(),
                            NetUtils.long2Mac(routerMacAddress),
                            _guestIpAddress,
                            _guestNetmask,
                            accountId,
                            account.getDomainId().longValue(),
                            publicMacAddress,
                            publicIpAddress,
                            vlanNetmask,
                            vlan.getId(),
                            vlanId,
                            pod.getId(),
                            dataCenterId,
                            _routerRamSize,
                            vlanGateway,
                            domain,
                            dc.getDns1(),
                            dc.getDns2());
                router.setMirroredVols(offering.isMirroredVolumes());

                routerId = _routerDao.persist(router);
                
                router = _routerDao.findById(routerId);

                poolId = _storageMgr.create(account, router, template, dc, pod, _offering, _diskOffering);
                if(poolId != 0)
                	break;

                _routerDao.delete(router.getId());
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Unable to find storage host or pool in pod " + pod.getName() + " (id:" + pod.getId() + "), checking other pods");
                }
                avoids.add(pod.getId());
            }
            
            if (pod == null) {
                s_logger.warn("Unable to find a pod with enough capacity");
                return null;
            }

            final EventVO event = new EventVO();
            event.setUserId(1L); // system user performed the action
            event.setAccountId(accountId);
            event.setType(EventTypes.EVENT_ROUTER_CREATE);

            if (poolId == 0) {
                event.setDescription("failed to create Domain Router : " + router.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                throw new ExecutionException("Unable to create DomainRouter");
            }
            router.setPoolId(poolId);
            _routerDao.updateIf(router, Event.OperationSucceeded, null);

            s_logger.debug("Router created: id=" + router.getId() + "; name=" + router.getName());

            event.setDescription("successfully created Domain Router : " + router.getName() + " with ip : " + publicIpAddress);
            _eventDao.persist(event);

            return router;
        } catch (final Throwable th) {
            if (th instanceof ExecutionException) {
                s_logger.error("Error while starting router due to " + th.getMessage());
            } else {
                s_logger.error("Unable to create router", th);
            }
            txn.rollback();

            if (router != null && router.getState() == State.Creating) {
                _routerDao.delete(router.getId());
            }
            return null;
        } finally {
            if (account != null) {
            	if(s_logger.isDebugEnabled())
            		s_logger.debug("Releasing lock on account " + account.getId() + " for createRouter");
            	_accountDao.release(account.getId());
            }
        }
    }

    @Override
    @DB
    public boolean destroyRouter(final long routerId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Attempting to destroy router " + routerId);
        }

        DomainRouterVO router = _routerDao.acquire(routerId);

        if (router == null) {
            s_logger.debug("Unable to acquire lock on router " + routerId);
            return false;
        }

        try {
            if (router.getState() == State.Destroyed || router.getState() == State.Expunging || router.getRemoved() != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Unable to find router or router is destroyed: " + routerId);
                }
                return true;
            }

            if (!stop(router)) {
                s_logger.debug("Unable to stop the router: " + routerId);
                return false;
            }
            router = _routerDao.findById(routerId);
            if (!_routerDao.updateIf(router, Event.DestroyRequested, router.getHostId())) {
                s_logger.debug("VM " + router.toString() + " is not in a state to be destroyed.");
                return false;
            }
        } finally {
            if (s_logger.isDebugEnabled())
                s_logger.debug("Release lock on router " + routerId + " for stop");
            _routerDao.release(routerId);
        }
            
            
        final Transaction txn = Transaction.currentTxn();
        try {
            final EventVO event = new EventVO();
            event.setUserId(1L);
            event.setAccountId(router.getAccountId());
            event.setType(EventTypes.EVENT_ROUTER_DESTROY);
            event.setParameters("id="+router.getId());

            List<VolumeVO> vols = null;
            try {
                vols = _volsDao.findByInstance(routerId);
                if ( !router.isMirroredVols()){
                    if (vols.size() != 0) {
                        final VolumeVO vol = vols.get(0);  // we only need one.
                        final DestroyCommand cmd = new DestroyCommand(vol.getFolder(), vols);
                        final long hostId = vol.getHostId();
                        final Answer answer = _agentMgr.easySend(hostId, cmd);
                        if (answer == null || !answer.getResult()) {
                            HostVO host = _hostDao.findById(hostId);
                            DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                            HostPodVO podVO = _podDao.findById(host.getPodId());
                            String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_STORAGE_MISC, vol.getDataCenterId(), vol.getPodId(), "Storage cleanup required for " + vol.getFolder() + " on storage host [" + hostDesc + "]", "Delete failed for volume " + vol.getFolder() + ", action required on the storage server with " + hostDesc);
                            s_logger.warn("Cleanup required on storage server " + host.getPrivateIpAddress() + " for " + vol.getFolder() + " due to " +
                            	(answer == null ? "answer is null" : answer.getDetails()));
                        }
                    }
                } else {
                    final Map<Long, List<VolumeVO>> hostVolMap = new HashMap<Long, List<VolumeVO>>();
                    for (final VolumeVO vol: vols){
                        List<VolumeVO> vollist = hostVolMap.get(vol.getHostId());
                        if (vollist == null) {
                            vollist = new ArrayList<VolumeVO>();
                            hostVolMap.put(vol.getHostId(), vollist);
                        }
                        vollist.add(vol);
                    }
                    for (final Long hostId: hostVolMap.keySet()){
                        final List<VolumeVO> volumes = hostVolMap.get(hostId);
                        final String path = volumes.get(0)==null?null:volumes.get(0).getFolder();
                        final DestroyCommand cmd = new DestroyCommand(path, volumes);
                        final Answer answer = _agentMgr.easySend(hostId, cmd);
                        if (answer == null || !answer.getResult()) {
                            HostVO host = _hostDao.findById(hostId);
                            DataCenterVO dcVO = _dcDao.findById(host.getDataCenterId());
                            HostPodVO podVO = _podDao.findById(host.getPodId());
                            String hostDesc = "name: " + host.getName() + " (id:" + host.getId() + "), availability zone: " + dcVO.getName() + ", pod: " + podVO.getName();
                            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_STORAGE_MISC, host.getDataCenterId(), host.getPodId(), "Storage cleanup required for host [" + hostDesc + "]", "Failed to destroy volumes, action required on the storage server with " + hostDesc);
                            s_logger.warn("Cleanup required on storage server " + host.getPrivateIpAddress() + " due to " +
                            	(answer == null ? "answer is null" : answer.getDetails()));
                        }
                    }
                }

            } finally {
                txn.start();
                if (vols != null) {
                    for (final VolumeVO vol : vols) {
                    	_volsDao.destroyVolume(vol.getId());
                        _volsDao.remove(vol.getId());
                    }
                }

                if (router != null) {
                	// _ipAddressDao.unassignIpAddress(router.getPublicIpAddress());
                    router.setPublicIpAddress(null);
                    _routerDao.update(router.getId(), router);
                    _routerDao.remove(router.getId());
                    
                    // Decrement the number of public IPs for this accountId in the database
                    // long accountId = router.getAccountId();
        	        // _accountDao.decrementNumberIPs(accountId);
               
                }
                txn.commit();
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Successfully destroyed router: " + routerId);
            }
            event.setDescription("successfully destroyed router : " + router.getName());
            _eventDao.persist(event);

            return true;
        } catch (final Throwable th) {
            s_logger.error("Unable to destroy machine.", th);
            txn.rollback();
            return false;
        }
    }

    private String rot13(final String password) {
        final StringBuffer newPassword = new StringBuffer("");

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);

            if ((c >= 'a' && c <= 'm') || ((c >= 'A' && c <= 'M'))) {
                c += 13;
            } else if  ((c >= 'n' && c <= 'z') || (c >= 'N' && c <= 'Z')) {
                c -= 13;
            }

            newPassword.append(c);
        }

        return newPassword.toString();
    }

    @Override
    public boolean savePasswordToRouter(final long routerId, final String vmIpAddress, final String password) {

        final DomainRouterVO router = _routerDao.findById(routerId);
        final String routerPrivateIpAddress = router.getPrivateIpAddress();
        final String vmName = router.getName();
        final String encodedPassword = rot13(password);
        final SavePasswordCommand cmdSavePassword = new SavePasswordCommand(encodedPassword, vmIpAddress, routerPrivateIpAddress, vmName);

        if (router != null && router.getHostId() != null) {
            final Answer answer = _agentMgr.easySend(router.getHostId(), cmdSavePassword);

            // TODO: Keshav, you need to handle errors if any.  Otherwise this assumes that
            // any answers means the command was executed successfully.
            if (answer != null) {
                return true;
            }
        }
        // either the router doesn't exist or router isn't running at all
        return false;
    }

    @Override
    public DomainRouterVO startRouter(final long routerId) {
        try {
            return start(routerId);
        } catch (final StorageUnavailableException e) {
        	s_logger.debug(e.getMessage());
            return null;
        } catch (final ConcurrentOperationException e) {
        	s_logger.debug(e.getMessage());
        	return null;
        }
    }
    
    private String allocatePrivateIpAddress(HostVO routingHost, DomainRouterVO router) {
    	_hostDao.loadDetails(routingHost);
    	String privateIpStrategy = routingHost.getDetails().get(RouterPrivateIpStrategy.class.getCanonicalName());
    	RouterPrivateIpStrategy strategy = RouterPrivateIpStrategy.valueOf(privateIpStrategy);
    	switch (strategy) {
    	case DcGlobal:
    		return _dcDao.allocatePrivateIpAddress(router.getDataCenterId(), routingHost.getPodId(), router.getId());
    	case HostLocal:
    		return RouterPrivateIpStrategy.DummyPrivateIp;
    	default:
    		return null;
    	}
    }
    
    @Override @DB
    public DomainRouterVO start(long routerId) throws StorageUnavailableException, ConcurrentOperationException {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Start router " + routerId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "domain_router", routerId);
        }
    	
    	DomainRouterVO router = _routerDao.acquire(routerId);
        if (router == null) {
        	s_logger.debug("Unable to lock the router " + routerId);
        	return router;
        }
        if(s_logger.isDebugEnabled())
        	s_logger.debug("Lock on router " + routerId + " is acquired");
        
        try {
	        final State state = router.getState();
	        if (state == State.Running) {
	            if (s_logger.isDebugEnabled()) {
	                s_logger.debug("Router is already started: " + router.toString());
	            }
	            return router;
	        }
	        
	        if (state == State.Destroyed || state == State.Expunging || router.getRemoved() != null) {
	        	s_logger.debug("Starting a router that's can not be started: " + router.toString());
	        	return null;
	        }
	
	        if (state.isTransitional()) {
	        	throw new ConcurrentOperationException("Someone else is starting the router: " + router.toString());
	        }
	
            final HostPodVO pod = _podDao.findById(router.getPodId());
            final HashSet<Host> avoid = new HashSet<Host>();
            final VMTemplateVO template = _templateDao.findById(router.getTemplateId());
            final DataCenterVO dc = _dcDao.findById(router.getDataCenterId());
            final StoragePoolVO sp = _storagePoolDao.findById(router.getPoolId());
            
	        HostVO routingHost = (HostVO)_agentMgr.findHost(Host.Type.Routing, dc, pod, sp, _offering, _diskOffering, template, router, null, avoid);

	        if (routingHost == null) {
	        	s_logger.error("Unable to find a host to start " + router.toString());
	        	return null;
	        }
	        
	        if (!_routerDao.updateIf(router, Event.StartRequested, routingHost.getId())) {
	            s_logger.debug("Unable to start router " + router.toString() + " because it is not in a startable state");
	            throw new ConcurrentOperationException("Someone else is starting the router: " + router.toString());
	        }
	
	        String vnet = null;
	        boolean vnetAllocated = false;
	        final boolean mirroredVols = router.isMirroredVols();
	        boolean started = false;
	        try {
	            final EventVO event = new EventVO();
	            event.setUserId(1L);
	            event.setAccountId(router.getAccountId());
	            event.setType(EventTypes.EVENT_ROUTER_START);
	
	            final List<UserVmVO> vms = _vmDao.listBy(routerId, State.Starting, State.Running, State.Stopped, State.Stopping);
	            if (vms.size() != 0) { // Find it in the existing network.
	                for (final UserVmVO vm : vms) {
	                    if (vm.getVnet() != null) {
	                        vnet = vm.getVnet();
	                    }
	                }
	            }
	
	            String routerMacAddress = null;
	            if (vnet == null && router.getRole() == Role.DHCP_FIREWALL_LB_PASSWD_USERDATA) { // If not found, then get another one.
	                vnet = _dcDao.allocateVnet(router.getDataCenterId(), router.getAccountId());
	                vnetAllocated = true;
	                routerMacAddress = getRouterMacForVnet(dc, vnet);
	            } else if (router.getRole() == Role.DHCP_USERDATA) {
	            	if (!Vlan.UNTAGGED.equals(router.getVlanId())) {
	            		vnet = router.getVlanId().trim();
	            	} else {
	            		vnet = Vlan.UNTAGGED;
	            	}
	            	routerMacAddress = router.getGuestMacAddress();
	            } else if (vnet != null && router.getRole() == Role.DHCP_FIREWALL_LB_PASSWD_USERDATA) {
	                routerMacAddress = getRouterMacForVnet(dc, vnet);
	            }
	
	            if (vnet == null) {
	                s_logger.error("Unable to get another vnet while starting router " + router.getName());
	                return null;
	            }
	
	           
	            List<VolumeVO> vols = _volsDao.findByInstanceAndType(routerId, VolumeType.ROOT);
	
	            final String [] storageIps = new String[2];
	            final VolumeVO vol = vols.get(0);
	            
	            HostVO stor1 = _hostDao.findById(vol.getHostId());
	            HostVO stor2 = null;
	            if (mirroredVols && (vols.size() == 2)) {
	            	stor2 = _hostDao.findById(vol.getHostId());
	            }
	
	            vols = _volsDao.findByInstance(routerId);
	
	            Answer answer = null;
	            int retry = _retry;

	            do {
	                if (s_logger.isDebugEnabled()) {
	                    s_logger.debug("Trying to start router on host " + routingHost.getName());
	                }
	                
	                String privateIpAddress = allocatePrivateIpAddress(routingHost, router);
	                if (privateIpAddress == null) {
	                    s_logger.error("Unable to allocate a private ip address while creating router for pod " + routingHost.getPodId());
	                    avoid.add(routingHost);
	                    continue;
	                }

	                if (s_logger.isDebugEnabled()) {
	                    s_logger.debug("Private Ip Address allocated: " + privateIpAddress);
	                }

	                router.setPrivateIpAddress(privateIpAddress);
	                router.setGuestMacAddress(routerMacAddress);
	                router.setVnet(vnet);
	                final String name = VirtualMachineName.attachVnet(router.getName(), vnet);
	                router.setInstanceName(name);
	                
	                storageIps[0] = _storageMgr.chooseStorageIp(router, routingHost, stor1);
	                if (stor2 != null) {
	                	storageIps[1] = _storageMgr.chooseStorageIp(router, routingHost, stor2);
	                }
	                router.setStorageIp(storageIps[0]);
	
	                _routerDao.updateIf(router, Event.OperationRetry, routingHost.getId());

	                Map<String, Integer> mappings = _storageMgr.share(router, vols, routingHost, true);
	                if (mappings == null) {
	                	s_logger.debug("Unable to share volumes to host " + routingHost.getId());
	                	continue;
	                }
	
	                final StartRouterCommand cmdStartRouter = new StartRouterCommand(router, name, storageIps, vols, mappings, mirroredVols);
	                answer = _agentMgr.easySend(routingHost.getId(), cmdStartRouter);
	                if (answer != null){
                		if (answer instanceof StartRouterAnswer){
                			StartRouterAnswer rAnswer = (StartRouterAnswer)answer;
                			if (rAnswer.getPrivateIpAddress() != null) {
                			    router.setPrivateIpAddress(rAnswer.getPrivateIpAddress());
                			}
                			if (rAnswer.getPrivateMacAddress() != null) {
                			    router.setPrivateMacAddress(rAnswer.getPrivateMacAddress());
                			}
                		}
	                	if (resendRouterState(router)) {
	                		if (s_logger.isDebugEnabled()) {
	                			s_logger.debug("Router " + router.getName() + " started on " + routingHost.getName());
	                		}
	                		started = true;
	                		break;
	                	} else {
	                		if (s_logger.isDebugEnabled()) {
	                			s_logger.debug("Router " + router.getName() + " started on " + routingHost.getName() + " but failed to program rules");
	                		}
	                		sendStopCommand(router);
	                	}
	                }
	
	                avoid.add(routingHost);
	                
	                router.setPrivateIpAddress(null);
	                _dcDao.releasePrivateIpAddress(privateIpAddress, router.getDataCenterId(), router.getId());
	
	                _storageMgr.unshare(router, vols, routingHost);
	            }while (--retry > 0 && (routingHost = (HostVO)_agentMgr.findHost(Host.Type.Routing, dc, pod, sp,  _offering, _diskOffering, template, router, null, avoid)) != null);

	
	            if (routingHost == null || retry < 0) {
	                event.setDescription("unable to start Domain Router: " + router.getName());
	                event.setLevel(EventVO.LEVEL_ERROR);
	                _eventDao.persist(event);
	                throw new ExecutionException("Couldn't find a routingHost");
	            }
	
	            _routerDao.updateIf(router, Event.OperationSucceeded, routingHost.getId());
	            if (s_logger.isDebugEnabled()) {
	                s_logger.debug("Router " + router.toString() + " is now started on " + routingHost.toString());
	            }
	            
	            event.setDescription("successfully started Domain Router: " + router.getName());
	            _eventDao.persist(event);
	
	            return _routerDao.findById(routerId);
	        } catch (final Throwable th) {
	        	
	        	Transaction txn = Transaction.currentTxn();
        		if (!started) {
		        	txn.start();
		            if (vnetAllocated == true && vnet != null) {
		                _dcDao.releaseVnet(vnet, router.getDataCenterId(), router.getAccountId());
		            }
		
		            router.setVnet(null);
		            String privateIpAddress = router.getPrivateIpAddress();
		            
		            router.setPrivateIpAddress(null);
		            
		            if (privateIpAddress != null) {
		            	_dcDao.releasePrivateIpAddress(privateIpAddress, router.getDataCenterId(), router.getId());
		            }
		            
		
		            if (_routerDao.updateIf(router, Event.OperationFailed, null)) {
			            txn.commit();
		            }
        		}
		
	            if (th instanceof ExecutionException) {
	                s_logger.error("Error while starting router due to " + th.getMessage());
	            } else if (th instanceof ConcurrentOperationException) {
	            	throw (ConcurrentOperationException)th;
	            } else if (th instanceof StorageUnavailableException) {
	            	throw (StorageUnavailableException)th;
	            } else {
	                s_logger.error("Error while starting router", th);
	            }
	            return null;
	        }
        } finally {
        	if (router != null) {
        		
                if(s_logger.isDebugEnabled())
                	s_logger.debug("Releasing lock on router " + routerId);
        		_routerDao.release(routerId);
        	}
        }
    }

	private String getRouterMacForVnet(final DataCenterVO dc, final String vnet) {
		final long vnetId = Long.parseLong(vnet);
		final long routerMac = (NetUtils.mac2Long(dc.getRouterMacAddress()) & (0x00ffff0000ffffl)) | ((vnetId & 0xffff) << 16);
		return NetUtils.long2Mac(routerMac);
	}

    private boolean resendRouterState(final DomainRouterVO router) {
        //source NAT address is stored in /proc/cmdline of the domR and gets
        //reassigned upon powerup. Source NAT rule gets configured in StartRouter command
        final List<IPAddressVO> ipAddrs = _ipAddressDao.listByAccountDcId(router.getAccountId(), router.getDataCenterId());
        final List<String> ipAddrList = new ArrayList<String>();
        for (final IPAddressVO ipVO:ipAddrs){
            ipAddrList.add(ipVO.getAddress());
        }
        if (!ipAddrList.isEmpty()) {
            final boolean success = associateIP(router, ipAddrList, true, false);
            if (!success) {
                return false;
            }
        }
        final List<FirewallRuleVO> fwRules = new ArrayList<FirewallRuleVO>();
        for (final IPAddressVO ipVO: ipAddrs) {
            fwRules.addAll(_rulesDao.listIPForwarding(ipVO.getAddress()));
        }
        final List<FirewallRuleVO> result = updateFirewallRules(router.getPublicIpAddress(), fwRules, router);
        if (result.size() != fwRules.size()) {
            return false;
        }
        return resendDhcpEntries(router);
      
    }
    
    private boolean resendDhcpEntries(final DomainRouterVO router){
    	final List<UserVmVO> vms = _vmDao.listByRouterId(router.getId());
    	final List<Command> cmdList = new ArrayList<Command>();
    	for (UserVmVO vm: vms) {
    		if (vm.getGuestIpAddress() == null || vm.getGuestMacAddress() == null || vm.getName() == null)
    			continue;
    		DhcpEntryCommand decmd = new DhcpEntryCommand(vm.getGuestMacAddress(), vm.getGuestIpAddress(), router.getPrivateIpAddress(), vm.getName());
    		cmdList.add(decmd);
    	}
        final Command [] cmds = new Command[cmdList.size()];
        Answer [] answers = null;
        try {
            answers = _agentMgr.send(router.getHostId(), cmdList.toArray(cmds), false);
        } catch (final AgentUnavailableException e) {
            s_logger.warn("agent unavailable", e);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
        }
        if (answers == null ){
            return false;
        }
        int i=0;
        while (i < cmdList.size()) {
        	Answer ans = answers[i];
        	i++;
        	if ((ans != null) && (ans.getResult())) {
        		continue;
        	} else {
        		return false;
        	}
        }
        return true;
    }
    
    private boolean resendUserData(final DomainRouterVO router){
    	final List<UserVmVO> vms = _vmDao.listByRouterId(router.getId());
    	final List<Command> cmdList = new ArrayList<Command>();
    	for (UserVmVO vm: vms) {
    		if (vm.getGuestIpAddress() == null || vm.getGuestMacAddress() == null || vm.getName() == null)
    			continue;
    		if (vm.getUserData() == null)
    			continue;
    		UserDataCommand userDataCmd = new UserDataCommand(vm.getUserData(), vm.getGuestIpAddress(), router.getPrivateIpAddress(), vm.getName());
    		cmdList.add(userDataCmd);
    	}
        final Command [] cmds = new Command[cmdList.size()];
        Answer [] answers = null;
        try {
            answers = _agentMgr.send(router.getHostId(), cmdList.toArray(cmds), false);
        } catch (final AgentUnavailableException e) {
            s_logger.warn("agent unavailable", e);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
        }
        if (answers == null ){
            return false;
        }
        int i=0;
        while (i < cmdList.size()) {
        	Answer ans = answers[i];
        	i++;
        	if ((ans != null) && (ans.getResult())) {
        		continue;
        	} else {
        		return false;
        	}
        }
        return true;
    }

    @Override
    public boolean stopRouter(final long routerId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Stop router " + routerId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "domain_router", routerId);
        }
    	
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Stopping router " + routerId);
        }
        
        return stop(_routerDao.findById(routerId));
    }

    @DB
	public void processStopOrRebootAnswer(final DomainRouterVO router, Answer answer) {
		final Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            final UserStatisticsVO userStats = _userStatsDao.lock(router.getAccountId(), router.getDataCenterId());
            if (userStats != null) {
                final RebootAnswer sa = (RebootAnswer)answer;
                final Long received = sa.getBytesReceived();
                long netBytes = 0;
                if (received != null) {
                	if (received.longValue() >= userStats.getCurrentBytesReceived()) {
                    	 netBytes = received.longValue();
                	} else {
                		netBytes = userStats.getCurrentBytesReceived() + received;
                	}
                } else {
                	netBytes = userStats.getCurrentBytesReceived();
                }
                userStats.setCurrentBytesReceived(0);
                userStats.setNetBytesReceived(userStats.getNetBytesReceived() + netBytes);
                
                final Long sent = sa.getBytesSent();
                
                if (sent != null) {
                	if (sent.longValue() >= userStats.getCurrentBytesSent()) {
                   	 	netBytes = sent.longValue();
                	} else {
                		netBytes = userStats.getCurrentBytesSent() + sent;
                	}
               } else {
               		netBytes = userStats.getCurrentBytesSent();
                }
                userStats.setNetBytesSent(userStats.getNetBytesSent() + netBytes);
                userStats.setCurrentBytesSent(0);
                _userStatsDao.update(userStats.getId(), userStats);
            } else {
                s_logger.warn("User stats were not created for account " + router.getAccountId() + " and dc " + router.getDataCenterId());
            }
            txn.commit();
        } catch (final Exception e) {
            throw new VmopsRuntimeException("Problem getting stats after reboot/stop ", e);
        }
	}

    @Override
    public boolean getRouterStatistics(final long vmId, final Map<String, long[]> netStats, final Map<String, long[]> diskStats) {
        final DomainRouterVO router = _routerDao.findById(vmId);

        if (router == null || router.getState() != State.Running || router.getHostId() == null) {
            return true;
        }

        /*
        final GetVmStatsCommand cmd = new GetVmStatsCommand(router, router.getInstanceName());
        final Answer answer = _agentMgr.easySend(router.getHostId(), cmd);
        if (answer == null) {
            return false;
        }

        final GetVmStatsAnswer stats = (GetVmStatsAnswer)answer;

        netStats.putAll(stats.getNetworkStats());
        diskStats.putAll(stats.getDiskStats());
        */

        return true;
    }


    @Override
    public boolean rebootRouter(final long routerId) {
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("Reboot router " + routerId + ", update async job-" + job.getId());
            _asyncMgr.updateAsyncJobAttachment(job.getId(), "domain_router", routerId);
        }
    	
        final DomainRouterVO router = _routerDao.findById(routerId);

        if (router == null || router.getState() == State.Destroyed) {
            return false;
        }

        final EventVO event = new EventVO();
        event.setUserId(1L);
        event.setAccountId(router.getAccountId());
        event.setType(EventTypes.EVENT_ROUTER_REBOOT);

        if (router.getState() == State.Running && router.getHostId() != null) {
            final RebootRouterCommand cmd = new RebootRouterCommand(router.getInstanceName(), router.getPrivateIpAddress());
            final RebootAnswer answer = (RebootAnswer)_agentMgr.easySend(router.getHostId(), cmd);

            if (answer != null &&  resendRouterState(router)) {
            	processStopOrRebootAnswer(router, answer);
                event.setDescription("successfully rebooted Domain Router : " + router.getName());
                _eventDao.persist(event);
                return true;
            } else {
                event.setDescription("failed to reboot Domain Router : " + router.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                return false;
            }
        } else {
            return startRouter(routerId) != null;
        }
    }

    @Override
    public boolean associateIP(final DomainRouterVO router, final List<String> ipAddrList, final boolean add, final boolean firstIp) {
        final Command [] cmds = new Command[ipAddrList.size()];
        int i=0;
        boolean sourceNat = false;
        for (final String ipAddress: ipAddrList) {
        	if (ipAddress.equalsIgnoreCase(router.getPublicIpAddress()))
        		sourceNat=true;
        
        	IPAddressVO ip = _ipAddressDao.findById(ipAddress);
    		VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
    		String vlanNetmask = vlan.getVlanNetmask();
    		
    		// If this is not a source NAT IP address and this is an add for the first IP in a VLAN, send down the VLAN's ID and gateway
    		String vlanId = null;
        	String vlanGateway = null;
        	
        	if (!sourceNat && firstIp) {
    			vlanId = vlan.getVlanId();
    			vlanGateway = vlan.getVlanGateway();
        	}
        	
            final IPAssocCommand cmd = new IPAssocCommand(router.getInstanceName(), router.getPrivateIpAddress(), ipAddress, add, sourceNat, vlanId, vlanGateway, vlanNetmask);
            cmds[i++] = cmd;
            sourceNat = false;
        }

        Answer[] answers = null;
        try {
            answers = _agentMgr.send(router.getHostId(), cmds, false);
        } catch (final AgentUnavailableException e) {
            s_logger.warn("Agent unavailable", e);
            return false;
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
            return false;
        }

        if (answers == null) {
            return false;
        }

        if (answers.length != ipAddrList.size()) {
            return false;
        }
        for (int i1=0; i1 < answers.length; i1++) {
            final Answer ans = answers[i1];
            if (ans.getResult() != true){
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean updateFirewallRule(final FirewallRuleVO rule, String oldPrivateIP, String oldPrivatePort) {

        final IPAddressVO ipVO = _ipAddressDao.findById(rule.getPublicIpAddress());
        if (ipVO == null || ipVO.getAllocated() == null) {
            return false;
        }

        final DomainRouterVO router = _routerDao.findBy(ipVO.getAccountId(), ipVO.getDataCenterId());
        Long hostId = router.getHostId();
        if (router == null || router.getHostId() == null) {
        	if (!rule.isEnabled()) {
        		s_logger.debug("router is not available");

                final IPAddressVO addr = _ipAddressDao.findById(rule.getPublicIpAddress());
                if (addr == null || addr.getAllocated() == null) {
                    s_logger.warn("Unable to find public ip address: " + rule.getPublicIpAddress());
                    return false;
                }

                final List<HostVO> hosts = _hostDao.listByTypeDataCenter(Host.Type.Routing, addr.getDataCenterId());
                if (hosts.size() == 0) {
                    s_logger.warn("Unable to find a routing host in the data center " + addr.getDataCenterId());
                    return false;
                }
                hostId = hosts.get(0).getId();
        	} else {
        		return false;
        	}
        }
        if (rule.isForwarding()) {
            return updatePortForwardingRule(rule, router, hostId, oldPrivateIP, oldPrivatePort);
        } else {
            final List<FirewallRuleVO> fwRules = _rulesDao.listIPForwarding(ipVO.getAccountId(), ipVO.getDataCenterId());
 
            return updateLoadBalancerRules(fwRules, router, hostId);
        }
    }

    @Override
    public List<FirewallRuleVO> updateFirewallRules(final String publicIpAddress, final List<FirewallRuleVO> fwRules, final DomainRouterVO router) {
        final List<FirewallRuleVO> result = new ArrayList<FirewallRuleVO>();
        if (fwRules.size() == 0) {
            return result;
        }

        if (router == null || router.getHostId() == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("router is not available");
            }

            String routerInstanceName = null;
            if (router != null) {
                routerInstanceName = router.getInstanceName();
            }

            final IPAddressVO addr = _ipAddressDao.findById(publicIpAddress);
            if (addr == null || addr.getAllocated() == null) {
                s_logger.warn("Unable to find public ip address: " + publicIpAddress);
                return fwRules;
            }

            final List<HostVO> hosts = _hostDao.listByTypeDataCenter(Host.Type.Routing, addr.getDataCenterId());
            if (hosts.size() == 0) {
                s_logger.warn("Unable to find a routing host in the data center " + addr.getDataCenterId());
                return fwRules;
            }

            return updateFirewallRules(hosts.get(0), routerInstanceName, null, fwRules);
        } else {
            final HostVO host = _hostDao.findById(router.getHostId());
            return updateFirewallRules(host, router.getInstanceName(), router.getPrivateIpAddress(), fwRules);
        }
    }

    public List<FirewallRuleVO> updateFirewallRules(final HostVO host, final String routerName, final String routerIp, final List<FirewallRuleVO> fwRules) {
        final List<FirewallRuleVO> result = new ArrayList<FirewallRuleVO>();
        if (fwRules.size() == 0) {
            s_logger.debug("There are no firewall rules");
            return result;
        }

        final List<Command> cmdList = new ArrayList<Command>();
        final List<FirewallRuleVO> lbRules = new ArrayList<FirewallRuleVO>();
        final List<FirewallRuleVO> fwdRules = new ArrayList<FirewallRuleVO>();
        
        int i=0;
        for (FirewallRuleVO rule : fwRules) {
        	// Determine the VLAN netmask of the rule's public IP address
        	IPAddressVO ip = _ipAddressDao.findById(rule.getPublicIpAddress());
        	VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
        	String vlanNetmask = vlan.getVlanNetmask();
        	
            if (rule.isForwarding()) {
            	rule.setVlanNetmask(vlanNetmask);
                fwdRules.add(rule);
                final SetFirewallRuleCommand cmd = new SetFirewallRuleCommand(routerName, routerIp, rule);
                cmdList.add(cmd);
            } else {
            	rule.setVlanNetmask(vlanNetmask);
                lbRules.add(rule);
            }
            
        }
        if (lbRules.size() > 0) { //at least one load balancer rule
            final LoadBalancerConfigurator cfgrtr = new HAProxyConfigurator();
            final String [] cfg = cfgrtr.generateConfiguration(fwRules);
            final String [][] addRemoveRules = cfgrtr.generateFwRules(fwRules);
            final LoadBalancerCfgCommand cmd = new LoadBalancerCfgCommand(cfg, addRemoveRules, routerIp);
            cmdList.add(cmd);
        }
        final Command [] cmds = new Command[cmdList.size()];
        Answer [] answers = null;
        try {
            answers = _agentMgr.send(host.getId(), cmdList.toArray(cmds), false);
        } catch (final AgentUnavailableException e) {
            s_logger.warn("agent unavailable", e);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
        }
        if (answers == null ){
            return result;
        }
        i=0;
        for (final FirewallRuleVO rule:fwdRules){
            final Answer ans = answers[i++];
            if (ans != null) {
                if (ans.getResult()) {
                    result.add(rule);
                } else {
                    s_logger.warn("Unable to update firewall rule: " + rule.toString());
                }
            }
        }
        if (i == (answers.length-1)) {
            final Answer lbAnswer = answers[i];
            if (lbAnswer.getResult()) {
                result.addAll(lbRules);
            } else {
                s_logger.warn("Unable to update lb rules.");
            }
        }
        return result;
    }

    private boolean updatePortForwardingRule(final FirewallRuleVO rule, final DomainRouterVO router, Long hostId, String oldPrivateIP, String oldPrivatePort) {
    	// Determine the firewallIpAddress, firewallUser, and firewallPassword from the VLAN of the rule's public IP address
    	IPAddressVO ip = _ipAddressDao.findById(rule.getPublicIpAddress());
    	VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
    	rule.setVlanNetmask(vlan.getVlanNetmask());
    	
        final SetFirewallRuleCommand cmd = new SetFirewallRuleCommand(router.getInstanceName(), router.getPrivateIpAddress(), rule, oldPrivateIP, oldPrivatePort);
        final Answer ans = _agentMgr.easySend(hostId, cmd);
        if (ans == null) {
            return false;
        } else {
            return ans.getResult();
        }
    }

    public List<FirewallRuleVO>  updatePortForwardingRules(final List<FirewallRuleVO> fwRules, final DomainRouterVO router, Long hostId ){
        final List<FirewallRuleVO> fwdRules = new ArrayList<FirewallRuleVO>();
        final List<FirewallRuleVO> result = new ArrayList<FirewallRuleVO>();

        if (fwRules.size() == 0) {
            return result;
        }

        final Command [] cmds = new Command[fwRules.size()];
        int i=0;
        for (final FirewallRuleVO rule: fwRules) {
			// Determine the firewallIpAddress, firewallUser, and firewallPassword from the VLAN of the rule's public IP address
        	IPAddressVO ip = _ipAddressDao.findById(rule.getPublicIpAddress());
        	VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
        	String vlanNetmask = vlan.getVlanNetmask();
        	
            if (rule.isForwarding()) {
            	rule.setVlanNetmask(vlanNetmask);
                fwdRules.add(rule);
                final SetFirewallRuleCommand cmd = new SetFirewallRuleCommand(router.getInstanceName(), router.getPrivateIpAddress(), rule);
                cmds[i++] = cmd;
            }
        }
        final Answer [] answers = null;
        try {
            _agentMgr.send(hostId, cmds, false);
        } catch (final AgentUnavailableException e) {
            s_logger.warn("agent unavailable", e);
        } catch (final OperationTimedoutException e) {
            s_logger.warn("Timed Out", e);
        }
        if (answers == null ){
            return result;
        }
        i=0;
        for (final FirewallRuleVO rule:fwdRules){
            final Answer ans = answers[i++];
            if (ans != null) {
                if (ans.getResult()) {
                    result.add(rule);
                }
            }
        }
        return result;
    }

    @Override
    public boolean executeAssignToLoadBalancer(AssignToLoadBalancerExecutor executor, LoadBalancerParam param) {
        try {
            executor.getAsyncJobMgr().getExecutorContext().getManagementServer().assignToLoadBalancer(param.getUserId(), param.getLoadBalancerId(), param.getInstanceIdList());
            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(), AsyncJobResult.STATUS_SUCCEEDED, 0, "success");
        } catch (InvalidParameterValueException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId() + ": " + e.getMessage());
            }

            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.PARAM_ERROR, "Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId());
        } catch (NetworkRuleConflictException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId() + ": " + e.getMessage());
            }

            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.NET_CONFLICT_LB_RULE_ERROR, e.getMessage());
        } catch (InternalErrorException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId() + ": " + e.getMessage());
            }

            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(), AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
        } catch (PermissionDeniedException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId() + ": " + e.getMessage());
            }
            
            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
                AsyncJobResult.STATUS_FAILED, BaseCmd.ACCOUNT_ERROR, e.getMessage());
        } catch(Exception e) {
            s_logger.warn("Unable to assign vms " + StringUtils.join(param.getInstanceIdList(), ",") + " to load balancer " + param.getLoadBalancerId() + ": " + e.getMessage(), e);
            executor.getAsyncJobMgr().completeAsyncJob(executor.getJob().getId(),
                    AsyncJobResult.STATUS_FAILED, BaseCmd.INTERNAL_ERROR, e.getMessage());
        }
        return true;
    }

    @Override @DB
    public boolean releasePublicIpAddress(long userId, final String ipAddress) {
        final Transaction txn = Transaction.currentTxn();

        IPAddressVO ip = null;
        try {
            ip = _ipAddressDao.acquire(ipAddress);
            
            if (ip == null) {
                s_logger.warn("Unable to find allocated ip: " + ipAddress);
                return false;
            }

            if(s_logger.isDebugEnabled())
            	s_logger.debug("lock on ip " + ipAddress + " is acquired");
            
            if (ip.getAllocated() == null) {
                s_logger.warn("ip: " + ipAddress + " is already allocated");
                return false;
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Releasing ip " + ipAddress + "; sourceNat = " + ip.isSourceNat());
            }

            final List<String> ipAddrs = new ArrayList<String>();
            ipAddrs.add(ip.getAddress());
            final List<FirewallRuleVO> firewallRules = _rulesDao.listIPForwardingForUpdate(ipAddress);

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Found firewall rules: " + firewallRules.size());
            }

            for (final FirewallRuleVO fw: firewallRules) {
                fw.setEnabled(false);
            }

            DomainRouterVO router = null;
            if (ip.isSourceNat()) {
                router = _routerDao.findByPublicIpAddress(ipAddress);
                if (router != null) {
                	if (router.getPublicIpAddress() != null) {
                		return false;
                	}
                }
            } else {
                router = _routerDao.findBy(ip.getAccountId(), ip.getDataCenterId());
            }

            // Now send the updates  down to the domR (note: we still hold locks on address and firewall)
            final List<FirewallRuleVO> result = updateFirewallRules(ipAddress, firewallRules, router);

            for (final FirewallRuleVO rule: result) {
                _rulesDao.remove(rule.getId());

                // Save and create the event
                String ruleName = (rule.isForwarding() ? "ip forwarding" : "load balancer");
                String description = "deleted " + ruleName + " rule [" + rule.getPublicIpAddress() + ":" + rule.getPublicPort()
                            + "]->[" + rule.getPrivateIpAddress() + ":" + rule.getPrivatePort() + "]" + " "
                            + rule.getProtocol();

                // save off an event for removing the network rule
                EventVO event = new EventVO();
                event.setUserId(userId);
                event.setAccountId(ip.getAccountId());
                event.setType(EventTypes.EVENT_NET_RULE_DELETE);
                event.setDescription(description);
                event.setLevel(EventVO.LEVEL_INFO);
                _eventDao.persist(event);
            }

            // We've deleted all the rules for the given public IP, so remove any security group mappings for that public IP
            List<SecurityGroupVMMapVO> securityGroupMappings = _securityGroupVMMapDao.listByIp(ipAddress);
            for (SecurityGroupVMMapVO securityGroupMapping : securityGroupMappings) {
                _securityGroupVMMapDao.remove(securityGroupMapping.getId());

                // save off an event for removing the security group
                EventVO event = new EventVO();
                event.setUserId(userId);
                event.setAccountId(ip.getAccountId());
                event.setType(EventTypes.EVENT_SECURITY_GROUP_REMOVE);
                event.setDescription("Successfully removed security group " + Long.valueOf(securityGroupMapping.getSecurityGroupId()).toString() + " from virtual machine " + Long.valueOf(securityGroupMapping.getInstanceId()).toString());
                event.setLevel(EventVO.LEVEL_INFO);
                _eventDao.persist(event);
            }

            List<LoadBalancerVO> loadBalancers = _loadBalancerDao.listByIpAddress(ipAddress);
            for (LoadBalancerVO loadBalancer : loadBalancers) {
                _loadBalancerDao.remove(loadBalancer.getId());

                // save off an event for removing the load balancer
                EventVO event = new EventVO();
                event.setUserId(userId);
                event.setAccountId(ip.getAccountId());
                event.setType(EventTypes.EVENT_LOAD_BALANCER_DELETE);
                event.setDescription("Successfully deleted load balancer " + loadBalancer.getId().toString());
                event.setLevel(EventVO.LEVEL_INFO);
                _eventDao.persist(event);
            }

            if (result.size() != firewallRules.size()) {
                s_logger.warn("Update firewall rules: sent = " + firewallRules.size() + " returned = " + result.size());
                return false;
            }

            if (router != null && router.getHostId() != null) {
            	if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Disassociate ip " + router.getName());
                }
            	
    			// Figure out if this is the last allocated IP in its VLAN
    			boolean firstIp = (_ipAddressDao.countIPs(ip.getDataCenterId(), ip.getVlanDbId(), true) == 1);
            	
                if (associateIP(router, ipAddrs, false, firstIp)) {
                	_ipAddressDao.unassignIpAddress(ipAddress);
                } else {
                	if (s_logger.isDebugEnabled()) {
                		s_logger.debug("Unable to dissociate IP : " + ipAddress + " due to failing to dissociate with router: " + router.getName());
                	}

                	final EventVO event = new EventVO();
                    event.setUserId(userId);
                    event.setAccountId(ip.getAccountId());
                    event.setType(EventTypes.EVENT_NET_IP_RELEASE);
                    event.setLevel(EventVO.LEVEL_ERROR);
                    event.setParameters("address=" + ipAddress + "\nsourceNat="+ip.isSourceNat());
                    event.setDescription("failed to released a public ip: " + ipAddress + " due to failure to disassociate with router " + router.getName());
                    _eventDao.persist(event);

                    return false;
                }
            } else {
            	_ipAddressDao.unassignIpAddress(ipAddress);
            }

            final EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(ip.getAccountId());
            event.setType(EventTypes.EVENT_NET_IP_RELEASE);
            event.setParameters("address=" + ipAddress + "\nsourceNat="+ip.isSourceNat());
            event.setDescription("released a public ip: " + ipAddress);
            _eventDao.persist(event);

            return true;
        } catch (final Throwable e) {
            s_logger.warn("ManagementServer error", e);
            return false;
        } finally {
        	if(ip != null) {
	            if(s_logger.isDebugEnabled())
	            	s_logger.debug("Releasing lock on ip " + ipAddress);
	            _ipAddressDao.release(ipAddress);
        	}
        }
    }

    @Override
    public DomainRouterVO getRouter(final long routerId) {
        return _routerDao.findById(routerId);
    }

    @Override
    public List<? extends DomainRouter> getRouters(final long hostId) {
        return _routerDao.listByHostId(hostId);
    }

    public boolean updateLoadBalancerRules(final List<FirewallRuleVO> fwRules, final DomainRouterVO router, Long hostId) {
    	
    	for (FirewallRuleVO rule : fwRules) {
    		// Determine the the VLAN netmask of the rule's public IP address
        	IPAddressVO ip = _ipAddressDao.findById(rule.getPublicIpAddress());
        	VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
        	String vlanNetmask = vlan.getVlanNetmask();
        	
        	rule.setVlanNetmask(vlanNetmask);
    	}
    	
        final LoadBalancerConfigurator cfgrtr = new HAProxyConfigurator();
        final String [] cfg = cfgrtr.generateConfiguration(fwRules);
        final String [][] addRemoveRules = cfgrtr.generateFwRules(fwRules);
        final LoadBalancerCfgCommand cmd = new LoadBalancerCfgCommand(cfg, addRemoveRules, router.getPrivateIpAddress());
        final Answer ans = _agentMgr.easySend(hostId, cmd);
        if (ans == null) {
            return false;
        } else {
            return ans.getResult();
        }
    }

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        _name = name;

        _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("RouterMonitor"));

        final ComponentLocator locator = ComponentLocator.getCurrentLocator();
        _configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }
        
        _configMgr = locator.getManager(ConfigurationManager.class);
        if (_configMgr == null) {
        	throw new ConfigurationException("Unable to get the configuration manager.");
        }

        final Map<String, String> configs = _configDao.getConfiguration("AgentManager", params);

        _routerTemplateId = NumbersUtil.parseInt(configs.get("router.template.id"), 1);

        _routerRamSize = NumbersUtil.parseInt(configs.get("router.ram.size"), 128);

        String value = configs.get("guest.ip.network");
        _guestIpAddress = value != null ? value : "10.1.1.1";

        value = configs.get("guest.netmask");
        _guestNetmask = value != null ? value : "255.255.255.0";

        value = configs.get("start.retry");
        _retry = NumbersUtil.parseInt(value, 2);

        value = configs.get("router.stats.interval");
        final int routerStatsInterval = NumbersUtil.parseInt(value, 300);

        value = configs.get("router.cleanup.interval");
        _routerCleanupInterval = NumbersUtil.parseInt(value, 3600);

        _domain = configs.get("domain");
        if (_domain == null) {
            _domain = "foo.com";
        }

        _instance = configs.get("instance.name");
        if (_instance == null) {
            _instance = "DEFAULT";
        }

        s_logger.info("Router configurations: " + "ramsize=" + _routerRamSize + "; templateId=" + _routerTemplateId +  "; ip address=" + _guestIpAddress);

        _hostDao = locator.getDao(HostDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + HostDao.class.getName());
        }

        _routerDao = locator.getDao(DomainRouterDao.class);
        if (_hostDao == null) {
            throw new ConfigurationException("Unable to get " + DomainRouterDao.class.getName());
        }
        
        _storagePoolDao = locator.getDao(StoragePoolDao.class);
        if (_storagePoolDao == null) {
            throw new ConfigurationException("Unable to find " + StoragePoolDao.class);
        }

        _podDao = locator.getDao(HostPodDao.class);
        if (_podDao == null) {
            throw new ConfigurationException("Unable to get " + HostPodDao.class.getName());
        }

        _userDao = locator.getDao(UserDao.class);
        if (_userDao == null) {
            throw new ConfigurationException("Unable to get " + UserDao.class.getName());
        }

        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("Unable to get " + AccountDao.class.getName());
        }
        
        _domainDao = locator.getDao(DomainDao.class);
        if (_domainDao == null) {
            throw new ConfigurationException("Unable to get " + DomainDao.class.getName());
        }
        
        _limitDao = locator.getDao(ResourceLimitDao.class);
        if (_limitDao == null) {
            throw new ConfigurationException("Unable to get " + ResourceLimitDao.class.getName());
        }

        _userStatsDao = locator.getDao(UserStatisticsDao.class);
        if (_userStatsDao == null) {
            throw new ConfigurationException("Unable to get " + UserStatisticsDao.class.getName());
        }

        _rulesDao = locator.getDao(FirewallRulesDao.class);
        if (_rulesDao == null) {
            throw new ConfigurationException("Unable to get " + FirewallRulesDao.class.getName());
        }

        _securityGroupVMMapDao = locator.getDao(SecurityGroupVMMapDao.class);
        if (_securityGroupVMMapDao == null) {
            throw new ConfigurationException("Unable to get " + SecurityGroupVMMapDao.class.getName());
        }

        _loadBalancerDao = locator.getDao(LoadBalancerDao.class);
        if (_loadBalancerDao == null) {
            throw new ConfigurationException("Unable to get " + LoadBalancerDao.class.getName());
        }

        _ipAddressDao = locator.getDao(IPAddressDao.class);
        if (_ipAddressDao == null) {
            throw new ConfigurationException("Unable to get " + IPAddressDao.class.getName());
        }

        _dcDao = locator.getDao(DataCenterDao.class);
        if (_dcDao == null) {
            throw new ConfigurationException("Unable to get " + DataCenterDao.class.getName());
        }
        
        _vlanDao = locator.getDao(VlanDao.class);
        if (_vlanDao == null) {
        	throw new ConfigurationException("Unable to get " + VlanDao.class.getName());
        }

        _volsDao = locator.getDao(VolumeDao.class);
        if (_volsDao == null) {
            throw new ConfigurationException("Unable to get " + VolumeDao.class.getName());
        }

        _templateDao = locator.getDao(VMTemplateDao.class);
        if (_templateDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplateDao.class.getName());
        }

        _diskDao = locator.getDao(DiskTemplateDao.class);
        if (_diskDao == null) {
            throw new ConfigurationException("Unable to get " + DiskTemplateDao.class.getName());
        }

        _vmTemplateHostDao = locator.getDao(VMTemplateHostDao.class);
        if (_vmTemplateHostDao == null) {
            throw new ConfigurationException("Unable to get " + VMTemplateHostDao.class.getName());
        }

        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
            throw new ConfigurationException("unable to get " + EventDao.class.getName());
        }

        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("Unable to get " + UserVmDao.class.getName());
        }

        final UserStatisticsDao statsDao = locator.getDao(UserStatisticsDao.class);
        if (statsDao == null) {
            throw new ConfigurationException("Unable to get " + UserStatisticsDao.class.getName());
        }

        _capacityDao = locator.getDao(CapacityDao.class);
        if (_capacityDao == null) {
            throw new ConfigurationException("Unable to get " + CapacityDao.class.getName());
        }

        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
            throw new ConfigurationException("Unable to get " + AgentManager.class.getName());
        }

        _agentMgr.registerForHostEvents(ComponentLocator.inject(RouterStatsListener.class, routerStatsInterval), true, false);
        
        _agentMgr.registerForHostEvents(new VlanMonitor(this, _hostDao, _vlanDao), true, false);

        
        _storageMgr = locator.getManager(StorageManager.class);
        if (_storageMgr == null) {
        	throw new ConfigurationException("Unable to get " + StorageManager.class.getName());
        }

        _haMgr = locator.getManager(HighAvailabilityManager.class);
        if (_haMgr == null) {
        	throw new ConfigurationException("Unable to get " + HighAvailabilityManager.class.getName());
        }
        
        _haMgr.registerHandler(VirtualMachine.Type.DomainRouter, this);

        _alertMgr = locator.getManager(AlertManager.class);
        if (_alertMgr == null) {
            throw new ConfigurationException("Unable to get " + AlertManager.class.getName());
        }
        
        _accountMgr = locator.getManager(AccountManager.class);
        if (_accountMgr == null) {
            throw new ConfigurationException("Unable to get " + AccountManager.class.getName());
        }
        
        _asyncMgr = locator.getManager(AsyncJobManager.class);
		if (_asyncMgr == null) {
			throw new ConfigurationException("Unable to get " + AsyncJobManager.class.getName());
		}

        _offering = new ServiceOfferingVO(null, "Fake Offering For DomR", 1, _routerRamSize, 0, 0, 0, true, null, false);
        _diskOffering = new DiskOfferingVO(1, "Fake Disk Offering for DomR", "Fake Disk Offering for DomR", 0, false);
        _template = _templateDao.findById(_routerTemplateId);
        if (_template == null) {
            throw new ConfigurationException("Unable to find the template for the router: " + _routerTemplateId);
        }

        s_logger.info("Network Manager is configured.");

        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        _executor.scheduleAtFixedRate(new RouterCleanupTask(), _routerCleanupInterval, _routerCleanupInterval, TimeUnit.SECONDS);

        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    protected NetworkManagerImpl() {
    }

    @Override
    public Command cleanup(final DomainRouterVO vm, final String vmName) {
        if (vmName != null) {
            return new StopCommand(vm, vmName, VirtualMachineName.getVnet(vmName));
        } else if (vm != null) {
            final DomainRouterVO vo = vm;
            return new StopCommand(vo, vo.getVnet());
        } else {
            throw new VmopsRuntimeException("Shouldn't even be here!");
        }
    }

    @Override
    public void completeStartCommand(final DomainRouterVO router) {
        _routerDao.updateIf(router, Event.AgentReportRunning, router.getHostId());
    }

    @Override
    public void completeStopCommand(final DomainRouterVO router) {
    	completeStopCommand(router, Event.AgentReportStopped);
    }
    
    @DB
    public void completeStopCommand(final DomainRouterVO router, final Event ev) {
        final long routerId = router.getId();

        final Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            if (_vmDao.listBy(routerId, State.Starting, State.Running).size() == 0) {
                _dcDao.releaseVnet(router.getVnet(), router.getDataCenterId(), router.getAccountId());
            }

            router.setVnet(null);
            router.setStorageIp(null);
            
            String privateIpAddress = router.getPrivateIpAddress();
            
            if (privateIpAddress != null) {
            	_dcDao.releasePrivateIpAddress(privateIpAddress, router.getDataCenterId(), router.getId());
            }
            router.setPrivateIpAddress(null);

            if (!_routerDao.updateIf(router, ev, null)) {
            	s_logger.debug("Router is not updated");
            	return;
            }
            txn.commit();
        } catch (final Exception e) {
            throw new VmopsRuntimeException("Unable to complete stop", e);
        }

        if (_storageMgr.unshare(router, null) == null) {
            s_logger.warn("Unable to set share to false for " + router.getId() + " on host ");
        }
    }

    @Override
    public DomainRouterVO get(final long id) {
        return getRouter(id);
    }

    @Override
    public Long convertToId(final String vmName) {
        if (!VirtualMachineName.isValidRouterName(vmName, _instance)) {
            return null;
        }

        return VirtualMachineName.getRouterId(vmName);
    }
    
    private boolean sendStopCommand(DomainRouterVO router) {
        final StopCommand stop = new StopCommand(router, router.getInstanceName(), router.getVnet());
    	
        Answer answer = null;
        boolean stopped = false;
        try {
            answer = _agentMgr.send(router.getHostId(), stop);
            if (!answer.getResult()) {
                s_logger.error("Unable to stop router");
            } else {
            	stopped = true;
            }
        } catch (AgentUnavailableException e) {
            s_logger.warn("Unable to reach agent to stop vm: " + router.getId());
        } catch (OperationTimedoutException e) {
            s_logger.warn("Unable to reach agent to stop vm: " + router.getId());
            s_logger.error("Unable to stop router");
        }
        
        return stopped;
    }

    @Override
    @DB
    public boolean stop(DomainRouterVO router) {
    	long routerId = router.getId();
    	
        router = _routerDao.acquire(routerId);
        if (router == null) {
            s_logger.debug("Unable to acquire lock on router " + routerId);
            return false;
        }
        try {
            
        	if(s_logger.isDebugEnabled())
        		s_logger.debug("Lock on router " + routerId + " for stop is acquired");
        	
            if (router.getRemoved() != null) {
                s_logger.debug("router " + routerId + " is removed");
                return false;
            }
    
            final Long hostId = router.getHostId();
            final State state = router.getState();
            if (state == State.Stopped || state == State.Destroyed || state == State.Expunging || router.getRemoved() != null) {
                s_logger.debug("Router was either not found or the host id is null");
                return true;
            }
    
            final EventVO event = new EventVO();
            event.setUserId(1L);
            event.setAccountId(router.getAccountId());
            event.setType(EventTypes.EVENT_ROUTER_STOP);
    
            if (!_routerDao.updateIf(router, Event.StopRequested, hostId)) {
                s_logger.debug("VM " + router.toString() + " is not in a state to be stopped.");
                return false;
            }
    
            if (hostId == null) {
                s_logger.debug("VM " + router.toString() + " doesn't have a host id");
                return false;
            }
    
            final StopCommand stop = new StopCommand(router, router.getInstanceName(), router.getVnet());
    
            Answer answer = null;
            boolean stopped = false;
            try {
                answer = _agentMgr.send(hostId, stop);
                if (!answer.getResult()) {
                    s_logger.error("Unable to stop router");
                    event.setDescription("failed to stop Domain Router : " + router.getName());
                    event.setLevel(EventVO.LEVEL_ERROR);
                    _eventDao.persist(event);
                } else {
                    stopped = true;
                }
            } catch (AgentUnavailableException e) {
                s_logger.warn("Unable to reach agent to stop vm: " + router.getId());
            } catch (OperationTimedoutException e) {
                s_logger.warn("Unable to reach agent to stop vm: " + router.getId());
                s_logger.error("Unable to stop router");
            }
    
            if (!stopped) {
                event.setDescription("failed to stop Domain Router : " + router.getName());
                event.setLevel(EventVO.LEVEL_ERROR);
                _eventDao.persist(event);
                _routerDao.updateIf(router, Event.OperationFailed, router.getHostId());
                return false;
            }
    
            completeStopCommand(router, Event.OperationSucceeded);
            event.setDescription("successfully stopped Domain Router : " + router.getName());
            _eventDao.persist(event);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Router " + router.toString() + " is stopped");
            }
    
            processStopOrRebootAnswer(router, answer);
        } finally {
            if(s_logger.isDebugEnabled())
                s_logger.debug("Release lock on router " + routerId + " for stop");
            _routerDao.release(routerId);
        }
        return true;
    }

    @Override
    public HostVO prepareForMigration(final DomainRouterVO router) throws StorageUnavailableException {
        final long routerId = router.getId();
        final boolean mirroredVols = router.isMirroredVols();
        final DataCenterVO dc = _dcDao.findById(router.getDataCenterId());
        final HostPodVO pod = _podDao.findById(router.getPodId());
        final StoragePoolVO sp = _storagePoolDao.findById(router.getPoolId());

        final List<VolumeVO> vols = _volsDao.findByInstance(routerId);

        final String [] storageIps = new String[2];
        final VolumeVO vol = vols.get(0);
        storageIps[0] = vol.getHostIp();
        if (mirroredVols && (vols.size() == 2)) {
            storageIps[1] = vols.get(1).getHostIp();
        }

        final PrepareForMigrationCommand cmd = new PrepareForMigrationCommand(router.getInstanceName(), router.getVnet(), storageIps, vols, null, mirroredVols);

        HostVO routingHost = null;
        final HashSet<Host> avoid = new HashSet<Host>();

        final HostVO fromHost = _hostDao.findById(router.getHostId());
        avoid.add(fromHost);
        
        List<String> currentHostVlanIds = _hostDao.getVlanIds(router.getHostId());

        while ((routingHost = (HostVO)_agentMgr.findHost(Host.Type.Routing, dc, pod, sp, _offering, _diskOffering, _template, router, fromHost, avoid)) != null) {
            avoid.add(routingHost);
            
        	if (routingHost.getPodId() != fromHost.getPodId()) {
        		s_logger.debug("Cannot migrate to a server that is in another pod");
        		avoid.add(routingHost);
        		continue;
        	}
        	
        	List<String> migrationHostVlanIds = _hostDao.getVlanIds(routingHost.getId().longValue());
        	if (!migrationHostVlanIds.containsAll(currentHostVlanIds)) {
        		s_logger.debug("Cannot migrate router to host " + routingHost.getName() + " because it is missing some VLAN IDs. Skipping host...");
        		avoid.add(routingHost);
        		continue;
        	}
        	
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Trying to migrate router to host " + routingHost.getName());
            }

            Map<String, Integer> mappings = _storageMgr.share(router, vols, routingHost, false);
            if (mappings == null) {
                s_logger.warn("Can not share " + router.getId() + " on host " + vol.getHostId());
                throw new StorageUnavailableException(vol.getHostId());
            }
            cmd.setMappings(mappings);

            final Answer answer = _agentMgr.easySend(routingHost.getId(), cmd);
            if (answer != null && answer.getResult()) {
                return routingHost;
            }

            _storageMgr.unshare(router, vols, routingHost);
        }

        return null;
    }

    @Override
    public boolean migrate(final DomainRouterVO router, final HostVO host) {
        final HostVO fromHost = _hostDao.findById(router.getHostId());

    	if (!_routerDao.updateIf(router, Event.MigrationRequested, router.getHostId())) {
    		s_logger.debug("State for " + router.toString() + " has changed so migration can not take place.");
    		return false;
    	}
    	
        final MigrateCommand cmd = new MigrateCommand(router.getInstanceName(), host.getPrivateIpAddress());
        final Answer answer = _agentMgr.easySend(fromHost.getId(), cmd);
        if (answer == null) {
            return false;
        }

        final List<VolumeVO> vols = _volsDao.findByInstance(router.getId());
        if (vols.size() == 0) {
            return true;
        }

        _storageMgr.unshare(router, vols, fromHost);

        return true;
    }

    @Override
    public boolean completeMigration(final DomainRouterVO router, final HostVO host) throws OperationTimedoutException, AgentUnavailableException {
        final CheckVirtualMachineCommand cvm = new CheckVirtualMachineCommand(router.getInstanceName());
        final CheckVirtualMachineAnswer answer = (CheckVirtualMachineAnswer)_agentMgr.send(host.getId(), cvm);
        if (answer == null || !answer.getResult()) {
            s_logger.debug("Unable to complete migration for " + router.getId());
            _routerDao.updateIf(router, Event.AgentReportStopped, null);
            return false;
        }

        final State state = answer.getState();
        if (state == State.Stopped) {
            s_logger.warn("Unable to complete migration as we can not detect it on " + host.getId());
            _routerDao.updateIf(router, Event.AgentReportStopped, null);
            return false;
        }

        _routerDao.updateIf(router, Event.OperationSucceeded, host.getId());

        return true;
    }

    protected class RouterCleanupTask implements Runnable {

        public RouterCleanupTask() {
        }

        public void run() {
            try {
                final List<Long> ids = _routerDao.findLonelyRouters();
                s_logger.info("Found " + ids.size() + " routers to stop. ");
    
                for (final Long id : ids) {
                    stopRouter(id);
                }
                s_logger.info("Done my job.  Time to rest.");
            } catch (Exception e) {
                s_logger.warn("Unable to stop routers.  Will retry. ", e);
            }
        }
    }

	@Override
	public boolean addDhcpEntry(final long routerHostId, final String routerIp, String vmName, String vmMac, String vmIp) {
        final DhcpEntryCommand dhcpEntry = new DhcpEntryCommand(vmMac, vmIp, routerIp, vmName);


        final Answer answer = _agentMgr.easySend(routerHostId, dhcpEntry);

        // TODO: Keshav, you need to handle errors if any.  Otherwise this assumes that
        // any answers means the command was executed successfully.
        if (answer != null) {
        	return true;
        }

		return false;
	}
	
	@Override
	public DomainRouterVO addVirtualMachineToGuestNetwork(UserVmVO vm, String password) throws ConcurrentOperationException {
        try {
        	DomainRouterVO router = start(vm.getDomainRouterId());
	        if (router == null) {
        		s_logger.error("Can't find a domain router to start VM: " + vm.getName());
        		return null;
	        }
	        
	        if (vm.getGuestMacAddress() == null){
	        	String vmMacAddress = NetUtils.long2Mac((NetUtils.mac2Long(router.getGuestMacAddress()) & 0xffffffff0000L) | (NetUtils.ip2Long(vm.getGuestIpAddress()) & 0xffff));
	        	vm.setGuestMacAddress(vmMacAddress);
	        }
	        String userData = vm.getUserData();
	        int cmdsLength = (password == null ? 0:1) + (userData == null ? 0:1 );
	        Command[] cmds = new Command[++cmdsLength];
	        int cmdIndex = 0;
	        cmds[cmdIndex] = new DhcpEntryCommand(vm.getGuestMacAddress(), vm.getGuestIpAddress(), router.getPrivateIpAddress(), vm.getName());
	        if (password != null) {
	            final String encodedPassword = rot13(password);
	        	cmds[++cmdIndex] = new SavePasswordCommand(encodedPassword, vm.getPrivateIpAddress(), router.getPrivateIpAddress(), vm.getName());
	        }
	        
	        if (userData != null) {
	        	cmds[++cmdIndex] = new UserDataCommand(userData, vm.getPrivateIpAddress(), router.getPrivateIpAddress(), vm.getName());
	        }
	        
	        Answer[] answers = _agentMgr.send(router.getHostId(), cmds, true);
	        if (!answers[0].getResult()) {
	        	s_logger.error("Unable to set dhcp entry for " + vm.getId() + " - " + vm.getName() +" on domR: " + router.getName() + " due to " + answers[0].getDetails());
	        	return null;
	        }
	        
	        if (password != null && !answers[1].getResult()) {
	        	s_logger.error("Unable to set password for " + vm.getId() + " - " + vm.getName() + " due to " + answers[0].getDetails());
	        	return null;
	        }
	        return router;
        } catch (StorageUnavailableException e) {
        	s_logger.error("Unable to start router " + vm.getDomainRouterId() + " because storage is unavailable.");
        	return null;
        } catch (AgentUnavailableException e) {
        	s_logger.error("Unable to setup the router " + vm.getDomainRouterId() + " for vm " + vm.getId() + " - " + vm.getName() + " because agent is unavailable");
        	return null;
		} catch (OperationTimedoutException e) {
        	s_logger.error("Unable to setup the router " + vm.getDomainRouterId() + " for vm " + vm.getId() + " - " + vm.getName() + " because agent is too busy");
        	return null;
		}
	}
	

	
	public void releaseVirtualMachineFromGuestNetwork(UserVmVO vm) {
	}

}
