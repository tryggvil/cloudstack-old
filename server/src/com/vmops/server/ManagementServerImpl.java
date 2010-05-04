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
package com.vmops.server;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.google.gson.Gson;
import com.vmops.agent.AgentManager;
import com.vmops.agent.api.GetVncPortAnswer;
import com.vmops.agent.api.GetVncPortCommand;
import com.vmops.alert.AlertManager;
import com.vmops.alert.AlertVO;
import com.vmops.alert.dao.AlertDao;
import com.vmops.api.BaseCmd;
import com.vmops.api.commands.AssociateIPAddrCmd;
import com.vmops.api.commands.CreateNetworkRuleCmd;
import com.vmops.api.commands.CreateTemplateCmd;
import com.vmops.api.commands.CreateVolumeCmd;
import com.vmops.api.commands.DeleteIsoCmd;
import com.vmops.api.commands.DeleteTemplateCmd;
import com.vmops.api.commands.DeleteUserCmd;
import com.vmops.api.commands.DeployVMCmd;
import com.vmops.api.commands.StartConsoleProxyCmd;
import com.vmops.api.commands.StartRouterCmd;
import com.vmops.api.commands.StartVMCmd;
import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.async.AsyncJobExecutor;
import com.vmops.async.AsyncJobManager;
import com.vmops.async.AsyncJobResult;
import com.vmops.async.AsyncJobVO;
import com.vmops.async.BaseAsyncJobExecutor;
import com.vmops.async.dao.AsyncJobDao;
import com.vmops.async.executor.AssociateIpAddressParam;
import com.vmops.async.executor.AttachISOParam;
import com.vmops.async.executor.CopyTemplateParam;
import com.vmops.async.executor.CreateOrUpdateRuleParam;
import com.vmops.async.executor.CreatePrivateTemplateParam;
import com.vmops.async.executor.DeleteDomainParam;
import com.vmops.async.executor.DeleteRuleParam;
import com.vmops.async.executor.DeleteTemplateParam;
import com.vmops.async.executor.DeployVMParam;
import com.vmops.async.executor.DisassociateIpAddressParam;
import com.vmops.async.executor.LoadBalancerParam;
import com.vmops.async.executor.RecurringSnapshotParam;
import com.vmops.async.executor.ResetVMPasswordParam;
import com.vmops.async.executor.SecurityGroupParam;
import com.vmops.async.executor.SnapshotOperationParam;
import com.vmops.async.executor.UpgradeVMParam;
import com.vmops.async.executor.VMOperationParam;
import com.vmops.async.executor.VolumeOperationParam;
import com.vmops.async.executor.VolumeOperationParam.VolumeOp;
import com.vmops.capacity.CapacityVO;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.configuration.ConfigurationManager;
import com.vmops.configuration.ConfigurationVO;
import com.vmops.configuration.ResourceLimitVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.configuration.dao.ResourceLimitDao;
import com.vmops.consoleproxy.ConsoleProxyManager;
import com.vmops.dc.DataCenterIpAddressVO;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.DataCenterIpAddressDaoImpl;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.domain.DomainVO;
import com.vmops.domain.dao.DomainDao;
import com.vmops.event.EventTypes;
import com.vmops.event.EventVO;
import com.vmops.event.dao.EventDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.InsufficientAddressCapacityException;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.exception.NetworkRuleConflictException;
import com.vmops.exception.PermissionDeniedException;
import com.vmops.exception.ResourceAllocationException;
import com.vmops.exception.ResourceInUseException;
import com.vmops.host.Host;
import com.vmops.host.HostStats;
import com.vmops.host.HostVO;
import com.vmops.host.dao.HostDao;
import com.vmops.info.ConsoleProxyInfo;
import com.vmops.network.FirewallRuleVO;
import com.vmops.network.IPAddressVO;
import com.vmops.network.LoadBalancerVMMapVO;
import com.vmops.network.LoadBalancerVO;
import com.vmops.network.NetworkManager;
import com.vmops.network.NetworkRuleConfigVO;
import com.vmops.network.SecurityGroupVMMapVO;
import com.vmops.network.SecurityGroupVO;
import com.vmops.network.dao.FirewallRulesDao;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.network.dao.LoadBalancerDao;
import com.vmops.network.dao.LoadBalancerVMMapDao;
import com.vmops.network.dao.NetworkRuleConfigDao;
import com.vmops.network.dao.SecurityGroupDao;
import com.vmops.network.dao.SecurityGroupVMMapDao;
import com.vmops.pricing.PricingVO;
import com.vmops.pricing.dao.PricingDao;
import com.vmops.serializer.GsonHelper;
import com.vmops.server.auth.UserAuthenticator;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.service.ServiceOffering.GuestIpType;
import com.vmops.service.dao.ServiceOfferingDao;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.DiskTemplateVO;
import com.vmops.storage.GuestOS;
import com.vmops.storage.GuestOSCategoryVO;
import com.vmops.storage.GuestOSVO;
import com.vmops.storage.InsufficientStorageCapacityException;
import com.vmops.storage.LaunchPermissionVO;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.SnapshotScheduleVO;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.Storage;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.StorageStats;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.VolumeStats;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Storage.FileSystem;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.storage.dao.DiskOfferingDao;
import com.vmops.storage.dao.DiskTemplateDao;
import com.vmops.storage.dao.GuestOSCategoryDao;
import com.vmops.storage.dao.GuestOSDao;
import com.vmops.storage.dao.LaunchPermissionDao;
import com.vmops.storage.dao.SnapshotDao;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.storage.dao.VMTemplateDao.TemplateFilter;
import com.vmops.storage.secondary.SecondaryStorageVmManager;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.template.TemplateManager;
import com.vmops.user.Account;
import com.vmops.user.AccountManager;
import com.vmops.user.AccountVO;
import com.vmops.user.ScheduledVolumeBackup;
import com.vmops.user.User;
import com.vmops.user.UserAccount;
import com.vmops.user.UserAccountVO;
import com.vmops.user.UserContext;
import com.vmops.user.UserStatisticsVO;
import com.vmops.user.UserVO;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserAccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.user.dao.UserStatisticsDao;
import com.vmops.utils.DateUtil;
import com.vmops.utils.EnumUtils;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Pair;
import com.vmops.utils.StringUtils;
import com.vmops.utils.DateUtil.IntervalType;
import com.vmops.utils.component.Adapters;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GlobalLock;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.MacAddress;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.ConsoleProxyVO;
import com.vmops.vm.DomainRouter;
import com.vmops.vm.DomainRouterVO;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.UserVm;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;
import com.vmops.vm.dao.ConsoleProxyDao;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;

public class ManagementServerImpl implements ManagementServer {
    public static final Logger s_logger = Logger.getLogger(ManagementServerImpl.class.getName());

    private final AccountManager _accountMgr;
    private final AgentManager _agentMgr;
    private final ConfigurationManager _configMgr;
    private final FirewallRulesDao _firewallRulesDao;
    private final SecurityGroupDao _securityGroupDao;
    private final LoadBalancerDao _loadBalancerDao;
    private final NetworkRuleConfigDao _networkRuleConfigDao;
    private final SecurityGroupVMMapDao _securityGroupVMMapDao;
    private final IPAddressDao _publicIpAddressDao;
    private final DataCenterIpAddressDaoImpl _privateIpAddressDao;
    private final LoadBalancerVMMapDao _loadBalancerVMMapDao;
    private DomainRouterDao _routerDao;
    private final ConsoleProxyDao _consoleProxyDao;
    private final EventDao _eventDao;
    private final DataCenterDao _dcDao;
    private final VlanDao _vlanDao;
    private final HostDao _hostDao;
    private final UserDao _userDao;
    private final UserVmDao _userVmDao;
    private final ConfigurationDao _configDao;
    private final NetworkManager _networkMgr;
    private final UserVmManager _vmMgr;
    private final ConsoleProxyManager _consoleProxyMgr;
    private final SecondaryStorageVmManager _secStorageVmMgr;
    private final ServiceOfferingDao _offeringsDao;
    private final DiskOfferingDao _diskOfferingDao;
    private final VMTemplateDao _templateDao;
    private final VMTemplateHostDao _templateHostDao;
    private final LaunchPermissionDao _launchPermissionDao;
    private final PricingDao _pricingDao;
    private final DomainDao _domainDao;
    private final AccountDao _accountDao;
    private final ResourceLimitDao _resourceLimitDao;
    private final UserAccountDao _userAccountDao;
    private final AlertDao _alertDao;
    private final CapacityDao _capacityDao;
    private final SnapshotDao _snapshotDao;
    private final GuestOSDao _guestOSDao;
    private final GuestOSCategoryDao _guestOSCategoryDao;
    private final StoragePoolDao _poolDao;
    private final StorageManager _storageMgr;
    private final UserVmDao _vmDao;

    private final Adapters<UserAuthenticator> _userAuthenticators;
    private final HostPodDao _hostPodDao;
    private final UserStatisticsDao _userStatsDao;
    private final VMInstanceDao _vmInstanceDao;
    private final VolumeDao _volumeDao;
    private final DiskTemplateDao _diskTemplateDao;
    private final AlertManager _alertMgr;
    private final AsyncJobDao _jobDao;
    private final AsyncJobManager _asyncMgr;
    private final TemplateManager _tmpltMgr;
    private final SnapshotManager _snapMgr;

    private final ScheduledExecutorService _executor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("AccountChecker"));

    private final StatsCollector _statsCollector;

    private final Map<String, String> _configs;

    private String _domain;
    private int _consoleProxyPort = ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT;
    // private int _consoleProxyUrlPort =
    // ConsoleProxyManager.DEFAULT_PROXY_URL_PORT;

    private long _defaultDataCenterId = -1;
    private final String _defaultZone;
    private final int _routerRamSize;
    private final int _proxyRamSize;

    private final int _maxVolumeSizeInGb;
    private final Map<Long, List<Long>> _loadBalancerVMInProg; // map of load balancer id to list of vms currently being
                                                               // added to the load balancer

    protected ManagementServerImpl() {
        ComponentLocator locator = ComponentLocator.getLocator(Name);
        _configDao = locator.getDao(ConfigurationDao.class);
        _routerDao = locator.getDao(DomainRouterDao.class);
        _eventDao = locator.getDao(EventDao.class);
        _dcDao = locator.getDao(DataCenterDao.class);
        _vlanDao = locator.getDao(VlanDao.class);
        _hostDao = locator.getDao(HostDao.class);
        _hostPodDao = locator.getDao(HostPodDao.class);
        _jobDao = locator.getDao(AsyncJobDao.class);

        _accountMgr = locator.getManager(AccountManager.class);
        _agentMgr = locator.getManager(AgentManager.class);
        _configMgr = locator.getManager(ConfigurationManager.class);
        _networkMgr = locator.getManager(NetworkManager.class);
        _vmMgr = locator.getManager(UserVmManager.class);
        _consoleProxyMgr = locator.getManager(ConsoleProxyManager.class);
        _secStorageVmMgr = locator.getManager(SecondaryStorageVmManager.class);
        _storageMgr = locator.getManager(StorageManager.class);
        _firewallRulesDao = locator.getDao(FirewallRulesDao.class);
        _securityGroupDao = locator.getDao(SecurityGroupDao.class);
        _loadBalancerDao = locator.getDao(LoadBalancerDao.class);
        _networkRuleConfigDao = locator.getDao(NetworkRuleConfigDao.class);
        _securityGroupVMMapDao = locator.getDao(SecurityGroupVMMapDao.class);
        _publicIpAddressDao = locator.getDao(IPAddressDao.class);
        _privateIpAddressDao = locator.getDao(DataCenterIpAddressDaoImpl.class);
        _loadBalancerVMMapDao = locator.getDao(LoadBalancerVMMapDao.class);
        _routerDao = locator.getDao(DomainRouterDao.class);
        _consoleProxyDao = locator.getDao(ConsoleProxyDao.class);
        _userDao = locator.getDao(UserDao.class);
        _userVmDao = locator.getDao(UserVmDao.class);
        _offeringsDao = locator.getDao(ServiceOfferingDao.class);
        _diskOfferingDao = locator.getDao(DiskOfferingDao.class);
        _templateDao = locator.getDao(VMTemplateDao.class);
        _templateHostDao = locator.getDao(VMTemplateHostDao.class);
        _launchPermissionDao = locator.getDao(LaunchPermissionDao.class);
        _pricingDao = locator.getDao(PricingDao.class);
        _domainDao = locator.getDao(DomainDao.class);
        _accountDao = locator.getDao(AccountDao.class);
        _resourceLimitDao = locator.getDao(ResourceLimitDao.class);
        _userAccountDao = locator.getDao(UserAccountDao.class);
        _alertDao = locator.getDao(AlertDao.class);
        _capacityDao = locator.getDao(CapacityDao.class);
        _snapshotDao = locator.getDao(SnapshotDao.class);
        _guestOSDao = locator.getDao(GuestOSDao.class);
        _guestOSCategoryDao = locator.getDao(GuestOSCategoryDao.class);
        _poolDao = locator.getDao(StoragePoolDao.class);
        _vmDao = locator.getDao(UserVmDao.class);

        _configs = _configDao.mapByComponent(Name);
        _userStatsDao = locator.getDao(UserStatisticsDao.class);
        _vmInstanceDao = locator.getDao(VMInstanceDao.class);
        _volumeDao = locator.getDao(VolumeDao.class);
        _diskTemplateDao = locator.getDao(DiskTemplateDao.class);
        _alertMgr = locator.getManager(AlertManager.class);
        _asyncMgr = locator.getManager(AsyncJobManager.class);
        _tmpltMgr = locator.getManager(TemplateManager.class);
        _snapMgr = locator.getManager(SnapshotManager.class);

        _userAuthenticators = locator.getAdapters(UserAuthenticator.class);
        if (_userAuthenticators == null || !_userAuthenticators.isSet()) {
            s_logger.error("Unable to find an user authenticator.");
        }

        _domain = _configs.get("domain");
        if (_domain == null) {
            _domain = ".myvm.com";
        }
        if (!_domain.startsWith(".")) {
            _domain = "." + _domain;
        }

        _defaultZone = _configs.get("default.zone");
        if (_defaultZone == null) {
            throw new VmopsRuntimeException("Can't find the default zone");
        }

        String value = _configs.get("consoleproxy.port");
        if (value != null)
            _consoleProxyPort = NumbersUtil.parseInt(value, ConsoleProxyManager.DEFAULT_PROXY_VNC_PORT);

        // value = _configs.get("consoleproxy.url.port");
        // if(value != null)
        // _consoleProxyUrlPort = NumbersUtil.parseInt(value,
        // ConsoleProxyManager.DEFAULT_PROXY_URL_PORT);

        value = _configs.get("account.cleanup.interval");
        int cleanup = NumbersUtil.parseInt(value, 60 * 60 * 24); // 1 hour.

        // Parse the max number of UserVMs and public IPs from server-setup.xml,
        // and set them in the right places

        String maxVolumeSizeInGbString = _configs.get("max.volume.size.gb");
        int maxVolumeSizeGb = NumbersUtil.parseInt(maxVolumeSizeInGbString, 2000);

        _maxVolumeSizeInGb = maxVolumeSizeGb;

        _routerRamSize = NumbersUtil.parseInt(_configs.get("router.ram.size"), 128);
        _proxyRamSize = NumbersUtil.parseInt(_configs.get("consoleproxy.ram.size"), ConsoleProxyManager.DEFAULT_PROXY_VM_RAMSIZE);

        _statsCollector = StatsCollector.getInstance(_configs);
        _executor.scheduleAtFixedRate(new AccountCleanupTask(), cleanup, cleanup, TimeUnit.SECONDS);

        _loadBalancerVMInProg = new HashMap<Long, List<Long>>();
    }

    protected Map<String, String> getConfigs() {
        return _configs;
    }

    @Override
    public List<? extends Host> discoverHosts(long dcId, Long podId, String url, String username, String password) {
        // TODO: parameter checks.
        return _agentMgr.discoverHosts(dcId, podId, url, username, password);
    }

    @Override
    public StorageStats getStorageStatistics(long hostId) {
        return _statsCollector.getStorageStats(hostId);
    }
    
    @Override
    public GuestOSCategoryVO getHostGuestOSCategory(long hostId) {
    	Long guestOSCategoryID = _agentMgr.getGuestOSCategoryId(hostId);
    	
    	if (guestOSCategoryID != null) {
    		return _guestOSCategoryDao.findById(guestOSCategoryID);
    	} else {
    		return null;
    	}
    }

    @Override
    public HostStats getHostStatistics(long hostId) {
        return _statsCollector.getHostStats(hostId);
    }

    @Override
    public VolumeStats[] getVolumeStatistics(long[] volIds) {
        return _statsCollector.getVolumeStats(volIds);
    }

    @Override
    public User createUserAPI(String username, String password, String firstName, String lastName, Long domainId, String accountName, short userType, String email) {
        Long accountId = null;
        try {
            if (accountName == null) {
                accountName = username;
            }
            if (domainId == null) {
                domainId = DomainVO.ROOT_DOMAIN;
            }

            Account account = _accountDao.findActiveAccount(accountName, domainId);
            if (account != null) {
                if (account.getType() != userType) {
                    throw new VmopsRuntimeException("Account " + accountName + " is not the correct account type for user " + username);
                }
                accountId = account.getId();
            }

            if (!_userAccountDao.validateUsernameInDomain(username, domainId)) {
                throw new VmopsRuntimeException("The user " + username + " already exists in domain " + domainId);
            }

            if (accountId == null) {
                if ((userType < Account.ACCOUNT_TYPE_NORMAL) || (userType > Account.ACCOUNT_TYPE_READ_ONLY_ADMIN)) {
                    throw new VmopsRuntimeException("Invalid account type " + userType + " given; unable to create user");
                }

                // create a new account for the user
                AccountVO newAccount = new AccountVO();
                if (domainId == null) {
                    // root domain is default
                    domainId = DomainVO.ROOT_DOMAIN;
                }

                if ((domainId != DomainVO.ROOT_DOMAIN) && (userType == Account.ACCOUNT_TYPE_ADMIN)) {
                    throw new VmopsRuntimeException("Invalid account type " + userType + " given for an account in domain " + domainId + "; unable to create user.");
                }

                newAccount.setAccountName(accountName);
                newAccount.setDomainId(domainId);
                newAccount.setType(userType);
                newAccount.setState("enabled");
                accountId = _accountDao.persist(newAccount);
            }

            if (accountId == null) {
                throw new VmopsRuntimeException("Failed to create account for user: " + username + "; unable to create user");
            }

            UserVO user = new UserVO();
            user.setUsername(username);
            user.setPassword(password);
            user.setState("enabled");
            user.setFirstname(firstName);
            user.setLastname(lastName);
            user.setAccountId(accountId.longValue());
            user.setEmail(email);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Creating user: " + username + ", account: " + accountName + " (id:" + accountId + "), domain: " + domainId);
            }

            UserVO dbUser = _userDao.create(user);

            if (!user.getPassword().equals(dbUser.getPassword())) {
                throw new VmopsRuntimeException("The user " + username + " being creating is using a password that is different than what's in the db");
            }

            saveEvent(new Long(1), new Long(1), domainId, EventVO.LEVEL_INFO, EventTypes.EVENT_USER_CREATE, "User, " + username + " for accountId = " + accountId
                    + " and domainId = " + domainId + " was created.");
            return dbUser;
        } catch (Exception e) {
            saveEvent(new Long(1), new Long(1), domainId, EventVO.LEVEL_ERROR, EventTypes.EVENT_USER_CREATE, "Error creating user, " + username + " for accountId = " + accountId
                    + " and domainId = " + domainId);
            if (e instanceof VmopsRuntimeException) {
                s_logger.info("unable to create user: " + e);
            } else {
                s_logger.warn("unknown exception creating user", e);
            }
            throw new VmopsRuntimeException(e.getMessage());
        }
    }

    @Override
    public boolean prepareForMaintenance(long hostId) {
        try {
            return _agentMgr.maintain(hostId);
        } catch (AgentUnavailableException e) {
            return false;
        }
    }

    @Override
    public long prepareForMaintenanceAsync(long hostId) {
        Long param = new Long(hostId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("PrepareMaintenance");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public boolean maintenanceCompleted(long hostId) {
        return _agentMgr.cancelMaintenance(hostId);
    }

    @Override
    public long maintenanceCompletedAsync(long hostId) {
        Long param = new Long(hostId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CompleteMaintenance");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public User createUser(String username, String password, String firstName, String lastName, Long domain, String accountName, short userType, String email) {
        return createUserAPI(username, StringToMD5(password), firstName, lastName, domain, accountName, userType, email);
    }

    @Override
    public String updateAdminPassword(long userId, String oldPassword, String newPassword) {
        // String old = StringToMD5(oldPassword);
        // User user = getUser(userId);
        // if (old.equals(user.getPassword())) {
        UserVO userVO = _userDao.createForUpdate(userId);
        userVO.setPassword(StringToMD5(newPassword));
        _userDao.update(userId, userVO);
        return newPassword;
        // } else {
        // return null;
        // }
    }

    private String StringToMD5(String string) {
        MessageDigest md5;

        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new VmopsRuntimeException("Error", e);
        }

        md5.reset();
        BigInteger pwInt = new BigInteger(1, md5.digest(string.getBytes()));

        // make sure our MD5 hash value is 32 digits long...
        StringBuffer sb = new StringBuffer();
        String pwStr = pwInt.toString(16);
        int padding = 32 - pwStr.length();
        for (int i = 0; i < padding; i++) {
            sb.append('0');
        }
        sb.append(pwStr);
        return sb.toString();
    }

    @Override
    public User getUser(long userId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieiving user with id: " + userId);
        }

        UserVO user = _userDao.getUser(userId);
        if (user == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find user with id " + userId);
            }
            return null;
        }

        return user;
    }

    public User getUser(long userId, boolean active) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieiving user with id: " + userId + " and active = " + active);
        }

        if (active) {
            return _userDao.getUser(userId);
        } else {
            return _userDao.findById(userId);
        }
    }

    @Override
    public UserAccount getUserAccount(String username, Long domainId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieiving user: " + username + " in domain " + domainId);
        }

        UserAccount userAccount = _userAccountDao.getUserAccount(username, domainId);
        if (userAccount == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find user with name " + username + " in domain " + domainId);
            }
            return null;
        }

        return userAccount;
    }

    private UserAccount getUserAccount(String username, String password, Long domainId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieiving user: " + username + " in domain " + domainId);
        }

        UserAccount userAccount = _userAccountDao.getUserAccount(username, domainId);
        if (userAccount == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find user with name " + username + " in domain " + domainId);
            }
            return null;
        }

        if (!userAccount.getState().equals("enabled") || !userAccount.getAccountState().equals("enabled")) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("user " + username + " in domain " + domainId + " is disabled/locked (or account is disabled/locked), returning null");
            }
            return null;
        }

        // FIXME: Hmmmm, this doesn't work so well for delegated admin if the
        // reseller's password is encrypted using non-MD5
        if (BaseCmd.isAdmin(userAccount.getType())) {
            String hashed = StringToMD5(password);

            if (!userAccount.getPassword().equals(hashed)) {
                s_logger.debug("password does not match");
                return null;
            } else {
                return userAccount;
            }
        } else {
            // We only use the first adapter even if multiple have been
            // configured
            Enumeration<UserAuthenticator> en = _userAuthenticators.enumeration();
            UserAuthenticator authenticator = en.nextElement();
            boolean authenticated = authenticator.authenticate(username, password, domainId);

            if (authenticated) {
                return userAccount;
            } else {
                return null;
            }
        }
    }

    @Override
    public boolean deleteUser(long userId) {
        UserAccount userAccount = null;
        Long accountId = null;
        String username = null;
        try {
            UserVO user = _userDao.findById(userId);
            if (user == null || user.getRemoved() != null) {
                return true;
            }
            username = user.getUsername();
            boolean result = _userDao.remove(userId);
            if (!result) {
                s_logger.error("Unable to remove the user with id: " + userId + "; username: " + user.getUsername());
                return false;
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("User is removed, id: " + userId + "; username: " + user.getUsername());
            }

            accountId = user.getAccountId();
            userAccount = _userAccountDao.findById(userId);

            List<UserVO> users = _userDao.listByAccount(accountId);
            if (users.size() != 0) {
                s_logger.debug("User (" + userId + "/" + user.getUsername() + ") is deleted but there's still other users in the account so not deleting account.");
                return true;
            }

            result = _accountDao.remove(accountId);
            if (!result) {
                s_logger.error("Unable to delete account " + accountId);
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Remove account " + accountId);
            }

            AccountVO account = _accountDao.findById(accountId);
            deleteAccount(account);
            saveEvent(Long.valueOf(1), Long.valueOf(1), userAccount.getDomainId(), EventVO.LEVEL_INFO, EventTypes.EVENT_USER_DELETE, "User " + username + " (id: " + userId
                    + ") for accountId = " + accountId + " and domainId = " + userAccount.getDomainId() + " was deleted.");
            return true;
        } catch (Exception e) {
            s_logger.error("exception deleting user: " + userId, e);
            long domainId = 0L;
            if (userAccount != null)
                domainId = userAccount.getDomainId();
            saveEvent(Long.valueOf(1), Long.valueOf(1), domainId, EventVO.LEVEL_INFO, EventTypes.EVENT_USER_DELETE, "Error deleting user " + username + " (id: " + userId
                    + ") for accountId = " + accountId + " and domainId = " + domainId);
            return false;
        }
    }

    @Override
    public long deleteUserAsync(long userId) {
        Long param = new Long(userId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteUser");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(DeleteUserCmd.getStaticName());

        return _asyncMgr.submitAsyncJob(job);
    }

    public boolean deleteAccount(AccountVO account) {
        long accountId = account.getId();
        boolean cleanup = false;
        try {
            List<UserVmVO> vms = _userVmDao.listByAccountId(accountId);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Destroying # of vms (accountId=" + accountId + "): " + vms.size());
            }

            for (UserVmVO vm : vms) {
                if (!_vmMgr.destroyVirtualMachine(1L, vm.getId())) { // only admins can delete users, pass in userId 1
                    s_logger.error("Unable to destroy vm: " + vm.getId());
                    if (!cleanup) {
                        cleanup = true;
                        account.setNeedsCleanup(true);
                        _accountDao.update(accountId, account);
                    }

                }
            }

            List<DomainRouterVO> routers = _routerDao.listBy(accountId);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Destroying # of routers (accountId=" + accountId + "): " + routers.size());
            }
            boolean routersCleanedUp = true;
            for (DomainRouterVO router : routers) {
                if (!_networkMgr.releaseRouter(router.getId())) {
                    s_logger.error("Unable to destroy router: " + router.getId());
                    routersCleanedUp = false;
                    if (!cleanup) {
                        cleanup = true;
                        account.setNeedsCleanup(true);
                        _accountDao.update(accountId, account);
                    }
                }
            }

            if (routersCleanedUp) {
                List<IPAddressVO> nonSourceNatIps = _publicIpAddressDao.listByAccountIdSourceNat(accountId, false);
                List<IPAddressVO> sourceNatIps = _publicIpAddressDao.listByAccountIdSourceNat(accountId, true);
                int numIps = 0;
                if (nonSourceNatIps != null) {
                    numIps += nonSourceNatIps.size();
                }
                if (sourceNatIps != null) {
                    numIps += sourceNatIps.size();
                }
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Releasing # of public addresses (accountId=" + accountId + "): " + numIps);
                }

                for (IPAddressVO ip : nonSourceNatIps) {
                    if (!_networkMgr.releasePublicIpAddress(1L, ip.getAddress())) { // pass userId 1 as only admins can delete a user
                        s_logger.error("Unable to release non source nat ip: " + ip.getAddress());
                        if (!cleanup) {
                            cleanup = true;
                            account.setNeedsCleanup(true);
                            _accountDao.update(accountId, account);
                        }
                    } else {
                    	_accountMgr.decrementResourceCount(accountId, ResourceType.public_ip);
                    }
                }
                for (IPAddressVO ip : sourceNatIps) {
                    if (!_networkMgr.releasePublicIpAddress(1L, ip.getAddress())) { // pass userId 1 as only admins can delete a user
                        s_logger.error("Unable to release source nat ip: " + ip.getAddress());
                        if (!cleanup) {
                            cleanup = true;
                            account.setNeedsCleanup(true);
                            _accountDao.update(accountId, account);
                        }
                    } else {
                    	_accountMgr.decrementResourceCount(accountId, ResourceType.public_ip);
                    }
                }
            }
            List<SecurityGroupVO> securityGroups = _securityGroupDao.listByAccountId(accountId);
            if (securityGroups != null) {
                for (SecurityGroupVO securityGroup : securityGroups) {
                    _networkRuleConfigDao.deleteBySecurityGroup(securityGroup.getId().longValue());
                    _securityGroupDao.remove(securityGroup.getId());
                }
            }

            // clean up templates
            List<VMTemplateVO> userTemplates = _templateDao.listByAccountId(accountId);
            if (userTemplates != null) {
                for (VMTemplateVO userTemplate : userTemplates) {
                    _templateDao.remove(userTemplate.getId());
                }
            }

            return true;
        } finally {
            s_logger.info("Cleanup for account " + account.getId() + (cleanup ? " is needed." : " is not needed."));
            account.setNeedsCleanup(cleanup);
            _accountDao.update(accountId, account);
        }
    }

    @Override
    public boolean disableUser(long userId) {
        if (userId <= 2) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("disableUser -- invalid user id: " + userId);
            }
            return false;
        }

        return doSetUserStatus(userId, Account.ACCOUNT_STATE_DISABLED);
    }

    @Override
    public long disableUserAsync(long userId) {
        Long param = new Long(userId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DisableUser");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public boolean enableUser(long userId) {
        boolean success = false;
        success = doSetUserStatus(userId, Account.ACCOUNT_STATE_ENABLED);

        // make sure the account is enabled too
        UserVO user = _userDao.findById(userId);
        if (user != null) {
            success = (success && enableAccount(user.getAccountId()));
        } else {
            s_logger.warn("Unable to find user with id: " + userId);
        }
        return success;
    }

    @Override
    public boolean lockUser(long userId) {
        boolean success = false;

        // make sure the account is enabled too
        UserVO user = _userDao.findById(userId);
        if (user != null) {
            // if the user is either locked already or disabled already, don't change state...only lock currently enabled users
            if (user.getState().equals(Account.ACCOUNT_STATE_LOCKED)) {
                // already locked...no-op
                return true;
            } else if (user.getState().equals(Account.ACCOUNT_STATE_ENABLED)) {
                success = doSetUserStatus(userId, Account.ACCOUNT_STATE_LOCKED);

                boolean lockAccount = true;
                List<UserVO> allUsersByAccount = _userDao.listByAccount(user.getAccountId());
                for (UserVO oneUser : allUsersByAccount) {
                    if (oneUser.getState().equals(Account.ACCOUNT_STATE_ENABLED)) {
                        lockAccount = false;
                        break;
                    }
                }

                if (lockAccount) {
                    success = (success && lockAccount(user.getAccountId()));
                }
            } else {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Attempting to lock a non-enabled user, current state is " + user.getState() + " (userId: " + userId + "), locking failed.");
                }
            }
        } else {
            s_logger.warn("Unable to find user with id: " + userId);
        }
        return success;
    }

    private boolean doSetUserStatus(long userId, String state) {
        UserVO userForUpdate = _userDao.createForUpdate();
        userForUpdate.setState(state);
        return _userDao.update(Long.valueOf(userId), userForUpdate);
    }

    @Override
    public boolean disableAccount(long accountId) {
        boolean success = false;
        if (accountId <= 2) {
            if (s_logger.isInfoEnabled()) {
                s_logger.info("disableAccount -- invalid account id: " + accountId);
            }
            return false;
        }

        AccountVO account = _accountDao.findById(accountId);
        if ((account == null) || account.getState().equals(Account.ACCOUNT_STATE_DISABLED)) {
            success = true;
        } else {
            AccountVO acctForUpdate = _accountDao.createForUpdate();
            acctForUpdate.setState(Account.ACCOUNT_STATE_DISABLED);
            success = _accountDao.update(Long.valueOf(accountId), acctForUpdate);

            success = (success && doDisableAccount(accountId));
        }
        return success;
    }

    @Override
    public long disableAccountAsync(long accountId) {
        Long param = new Long(accountId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DisableAccount");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public boolean updateAccount(long accountId, String accountName) {
        boolean success = false;
        AccountVO account = _accountDao.findById(accountId);
        if ((account == null) || (account.getAccountName().equals(accountName))) {
            success = true;
        } else {
            AccountVO acctForUpdate = _accountDao.createForUpdate();
            acctForUpdate.setAccountName(accountName);
            success = _accountDao.update(Long.valueOf(accountId), acctForUpdate);
        }
        return success;
    }

    private boolean doDisableAccount(long accountId) {
        List<UserVmVO> vms = _userVmDao.listByAccountId(accountId);
        boolean success = true;
        for (UserVmVO vm : vms) {
            try {
                success = (success && _vmMgr.stop(vm));
            } catch (AgentUnavailableException aue) {
                s_logger.warn("Agent running on host " + vm.getHostId() + " is unavailable, unable to stop vm " + vm.getName());
                success = false;
            }
        }

        List<DomainRouterVO> routers = _routerDao.listBy(accountId);
        for (DomainRouterVO router : routers) {
            success = (success && _networkMgr.stopRouter(router.getId()));
        }

        return success;
    }

    @Override
    public boolean enableAccount(long accountId) {
        boolean success = false;
        AccountVO acctForUpdate = _accountDao.createForUpdate();
        acctForUpdate.setState(Account.ACCOUNT_STATE_ENABLED);
        success = _accountDao.update(Long.valueOf(accountId), acctForUpdate);
        return success;
    }

    @Override
    public boolean lockAccount(long accountId) {
        boolean success = false;
        Account account = _accountDao.findById(accountId);
        if (account != null) {
            if (account.getState().equals(Account.ACCOUNT_STATE_LOCKED)) {
                return true; // already locked, no-op
            } else if (account.getState().equals(Account.ACCOUNT_STATE_ENABLED)) {
                AccountVO acctForUpdate = _accountDao.createForUpdate();
                acctForUpdate.setState(Account.ACCOUNT_STATE_LOCKED);
                success = _accountDao.update(Long.valueOf(accountId), acctForUpdate);
            } else {
                if (s_logger.isInfoEnabled()) {
                    s_logger.info("Attempting to lock a non-enabled account, current state is " + account.getState() + " (accountId: " + accountId + "), locking failed.");
                }
            }
        } else {
            s_logger.warn("Failed to lock account " + accountId + ", account not found.");
        }
        return success;
    }

    @Override
    public boolean updateUser(long userId, String username, String password, String firstname, String lastname, String email) {
        UserVO user = _userDao.findById(userId);
        Long accountId = user.getAccountId();

        if (s_logger.isDebugEnabled()) {
            s_logger.debug("updating user with id: " + userId);
        }
        UserAccount userAccount = _userAccountDao.findById(userId);
        try {
            _userDao.update(userId, username, password, firstname, lastname, email, accountId);
            saveEvent(new Long(1), Long.valueOf(1), userAccount.getDomainId(), EventVO.LEVEL_INFO, EventTypes.EVENT_USER_UPDATE, "User, " + username + " for accountId = "
                    + accountId + " and domainId = " + userAccount.getDomainId() + " was updated.");
        } catch (Throwable th) {
            s_logger.error("error updating user", th);
            saveEvent(Long.valueOf(1), Long.valueOf(1), userAccount.getDomainId(), EventVO.LEVEL_ERROR, EventTypes.EVENT_USER_UPDATE, "Error updating user, " + username
                    + " for accountId = " + accountId + " and domainId = " + userAccount.getDomainId());
            return false;
        }
        return true;
    }

    private Long saveEvent(Long userId, Long accountId, Long domainId, String type, String description) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setDescription(description);
        return _eventDao.persist(event);
    }

    private Long saveEvent(Long userId, Long accountId, Long domainId, String level, String type, String description) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setDescription(description);
        event.setLevel(level);
        return _eventDao.persist(event);
    }

    private Long saveEvent(Long userId, Long accountId, Long domainId, String level, String type, String description, String params) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setDescription(description);
        event.setLevel(level);
        event.setParameters(params);
        return _eventDao.persist(event);
    }

    private Long saveEvent(Long userId, long accountId, String level, String type, String description, String params) {
        Account account = _accountDao.findById(accountId);
        return saveEvent(userId, account.getId(), account.getDomainId(), level, type, description, params);
    }

    @Override
    public Pair<User, Account> findUserByApiKey(String apiKey) {
        return _accountDao.findUserAccountByApiKey(apiKey);
    }

    @Override
    public Account getAccount(long accountId) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieiving account with id: " + accountId);
        }

        AccountVO account = _accountDao.findById(Long.valueOf(accountId));
        if (account == null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Unable to find account with id " + accountId);
            }
            return null;
        }

        return account;
    }

    @Override
    public String createApiKey(Long userId) {
        User user = findUserById(userId);
        try {
            UserVO updatedUser = _userDao.createForUpdate();

            String encodedKey = null;
            Pair<User, Account> userAcct = null;
            int retryLimit = 10;
            do {
                // FIXME: what algorithm should we use for API keys?
                KeyGenerator generator = KeyGenerator.getInstance("HmacSHA1");
                SecretKey key = generator.generateKey();
                encodedKey = Base64.encodeBase64URLSafeString(key.getEncoded());
                userAcct = _accountDao.findUserAccountByApiKey(encodedKey);
                retryLimit--;
            } while ((userAcct != null) && (retryLimit >= 0));

            if (userAcct != null) {
                return null;
            }
            updatedUser.setApiKey(encodedKey);
            _userDao.update(user.getId(), updatedUser);
            return encodedKey;
        } catch (NoSuchAlgorithmException ex) {
            s_logger.error("error generating secret key for user: " + user.getUsername(), ex);
        }
        return null;
    }

    @Override
    public String createSecretKey(Long userId) {
        User user = findUserById(userId);
        try {
            UserVO updatedUser = _userDao.createForUpdate();

            String encodedKey = null;
            int retryLimit = 10;
            UserVO userBySecretKey = null;
            do {
                KeyGenerator generator = KeyGenerator.getInstance("HmacSHA1");
                SecretKey key = generator.generateKey();
                encodedKey = Base64.encodeBase64URLSafeString(key.getEncoded());
                userBySecretKey = _userDao.findUserBySecretKey(encodedKey);
                retryLimit--;
            } while ((userBySecretKey != null) && (retryLimit >= 0));

            if (userBySecretKey != null) {
                return null;
            }

            updatedUser.setSecretKey(encodedKey);
            _userDao.update(user.getId(), updatedUser);
            return encodedKey;
        } catch (NoSuchAlgorithmException ex) {
            s_logger.error("error generating secret key for user: " + user.getUsername(), ex);
        }
        return null;
    }

    @Override
    @DB
    public String associateIpAddress(long userId, long accountId, long domainId, long zoneId) throws ResourceAllocationException, InsufficientAddressCapacityException,
            InvalidParameterValueException, InternalErrorException {
        Transaction txn = Transaction.currentTxn();
        AccountVO account = null;
        try {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Associate IP address called for user " + userId + " account " + accountId);
            }
            zoneId = validateDataCenterId(zoneId);
            account = _accountDao.acquire(accountId);

            if (account == null) {
                s_logger.warn("Unable to lock account: " + accountId);
                throw new InternalErrorException("Unable to acquire account lock");
            }

            s_logger.debug("Associate IP address lock acquired");

            // Check that the maximum number of public IPs for the given
            // accountId will not be exceeded
            if (_accountMgr.resourceLimitExceeded(account, ResourceType.public_ip)) {
                ResourceAllocationException rae = new ResourceAllocationException("Maximum number of public IP addresses for account: " + account.getAccountName()
                        + " has been exceeded.");
                rae.setResourceType("ip");
                throw rae;
            }

            String ipAddress = null;
            DomainRouterVO router = _routerDao.findBy(accountId, zoneId);
            if (router == null) {
                throw new InvalidParameterValueException("No instances found in specified zone");
            }

            if (router.getHostId() == null) {
                throw new InternalErrorException("The router with the specified account ID and zone ID is stopped. Please start the router and try again.");
            }

            // Figure out which VLAN to grab an IP address from
            long vlanDbIdToUse = _networkMgr.findNextVlan(zoneId);

            txn.start();

            ipAddress = _publicIpAddressDao.assignIpAddress(accountId, domainId, vlanDbIdToUse, false);

            if (ipAddress == null) {
                throw new InsufficientAddressCapacityException("Unable to allocate public ip");
            } else {
            	_accountMgr.incrementResourceCount(accountId, ResourceType.public_ip);
            }

            IPAddressVO ip = _publicIpAddressDao.findById(ipAddress);

            boolean success;
            String errorMsg = "";

            // Make sure that the router's host has the VLAN's ID enabled
            VlanVO vlan = _vlanDao.findById(new Long(ip.getVlanDbId()));
            String vlanId = vlan.getVlanId();
            List<String> vlanIdsOnHost = _hostDao.getVlanIds(router.getHostId().longValue());
            if (!vlanIdsOnHost.contains(vlanId)) {
                success = false;
                errorMsg = "Unable to assign public IP address. The host of your DomR does not have the IP's VLAN enabled.";
            } else {
                success = true;
            }

            // Figure out if this will be the first allocated IP in its VLAN
            boolean firstIp = (_publicIpAddressDao.countIPs(ip.getDataCenterId(), ip.getVlanDbId(), true) == 1);

            List<String> ipAddrs = new ArrayList<String>();
            ipAddrs.add(ipAddress);

            if (success) {
                success = _networkMgr.associateIP(router, ipAddrs, true, firstIp);
                if (!success)
                    errorMsg = "Unable to assign public IP address.";
            }

            EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(accountId);
            event.setType(EventTypes.EVENT_NET_IP_ASSIGN);
            event.setParameters("address=" + ipAddress + "\nsourceNat=" + false + "\ndcId=" + zoneId);

            if (!success) {
                _publicIpAddressDao.unassignIpAddress(ipAddress);
                ipAddress = null;
                _accountMgr.decrementResourceCount(accountId, ResourceType.public_ip);

                event.setLevel(EventVO.LEVEL_ERROR);
                event.setDescription(errorMsg);
                _eventDao.persist(event);
                txn.commit();

                throw new InternalErrorException(errorMsg);
            } else {
                event.setDescription("Assigned a public IP address: " + ipAddress);
                _eventDao.persist(event);
            }

            txn.commit();
            return ipAddress;

        } catch (ResourceAllocationException rae) {
            s_logger.error("Associate IP threw a ResourceAllocationException.", rae);
            throw rae;
        } catch (InsufficientAddressCapacityException iace) {
            s_logger.error("Associate IP threw an InsufficientAddressCapacityException.", iace);
            throw iace;
        } catch (InvalidParameterValueException ipve) {
            s_logger.error("Associate IP threw an InvalidParameterValueException.", ipve);
            throw ipve;
        } catch (InternalErrorException iee) {
            s_logger.error("Associate IP threw an InternalErrorException.", iee);
            throw iee;
        } catch (Throwable t) {
            s_logger.error("Associate IP address threw an exception.", t);
            throw new InternalErrorException("Associate IP address exception");
        } finally {
            if (account != null) {
                _accountDao.release(accountId);
                s_logger.debug("Associate IP address lock released");
            }
        }
    }

    @Override
    public long associateIpAddressAsync(long userId, long accountId, long domainId, long zoneId) {
        AssociateIpAddressParam param = new AssociateIpAddressParam(userId, accountId, domainId, zoneId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("AssociateIpAddress");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(AssociateIPAddrCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    @DB
    public boolean disassociateIpAddress(long userId, long accountId, String publicIPAddress) throws PermissionDeniedException, IllegalArgumentException {
        Transaction txn = Transaction.currentTxn();
        try {
            IPAddressVO ipVO = _publicIpAddressDao.findById(publicIPAddress);
            if (ipVO == null) {
                return false;
            }

            if (ipVO.getAllocated() == null) {
                return true;
            }

            AccountVO accountVO = _accountDao.findById(accountId);
            if (accountVO == null) {
                return false;
            }

            if ((ipVO.getAccountId() == null) || (ipVO.getAccountId().longValue() != accountId)) {
                // FIXME: is the user visible in the admin account's domain????
                if (!BaseCmd.isAdmin(accountVO.getType())) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug("permission denied disassociating IP address " + publicIPAddress + "; acct: " + accountId + "; ip (acct / dc / dom / alloc): "
                                + ipVO.getAccountId() + " / " + ipVO.getDataCenterId() + " / " + ipVO.getDomainId() + " / " + ipVO.getAllocated());
                    }
                    throw new PermissionDeniedException("User/account does not own supplied address");
                }
            }

            if (ipVO.getAllocated() == null) {
                return true;
            }

            if (ipVO.isSourceNat()) {
                throw new IllegalArgumentException("ip address is used for source nat purposes and can not be disassociated.");
            }

            txn.start();
            boolean success = _networkMgr.releasePublicIpAddress(userId, publicIPAddress);
            if (success)
            	_accountMgr.decrementResourceCount(accountId, ResourceType.public_ip);
            txn.commit();
            return success;

        } catch (PermissionDeniedException pde) {
            throw pde;
        } catch (IllegalArgumentException iae) {
            throw iae;
        } catch (Throwable t) {
            s_logger.error("Disassociate IP address threw an exception.");
            throw new IllegalArgumentException("Disassociate IP address threw an exception");
        }
    }

    @Override
    public long disassociateIpAddressAsync(long userId, long accountId, String ipAddress) {
        DisassociateIpAddressParam param = new DisassociateIpAddressParam(userId, accountId, ipAddress);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DisassociateIpAddress");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public VlanVO createVlan(Long zoneId, String vlanId, String vlanGateway, String vlanNetmask, String description, String name) throws InvalidParameterValueException {
        // Check if the zone is valid
        DataCenterVO zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new InvalidParameterValueException("Please specify a valid zone");
        }

        return _networkMgr.addOrDeleteVlan(zoneId, true, vlanId, vlanGateway, vlanNetmask, description, name);
    }

    @Override
    public VlanVO deleteVlan(Long vlanDbId) throws InvalidParameterValueException {
        VlanVO vlan = _vlanDao.findById(vlanDbId);

        // Check if the VLAN is valid
        if (vlan == null) {
            throw new InvalidParameterValueException("Please specify a valid VLAN id");
        }

        long zoneId = vlan.getDataCenterId();
        String vlanGateway = vlan.getVlanGateway();
        String vlanNetmask = vlan.getVlanNetmask();
        String description = vlan.getDescription();
        String name = vlan.getVlanName();

        return _networkMgr.addOrDeleteVlan(zoneId, false, vlan.getVlanId(), vlanGateway, vlanNetmask, description, name);
    }

    @Override
    public VolumeVO createVolume(long accountId, long userId, String name, long zoneId, long diskOfferingId) throws InternalErrorException {
        DataCenterVO zone = _dcDao.findById(zoneId);
        DiskOfferingVO diskOffering = _diskOfferingDao.findById(diskOfferingId);
        VolumeVO createdVolume = _storageMgr.createVolumeInPool(accountId, userId, name, zone, diskOffering);

        if (createdVolume != null)
            return createdVolume;
        else
            throw new InternalErrorException("Failed to create volume.");
    }

    @Override
    public long createVolumeAsync(long accountId, long userId, String name, long zoneId, long diskOfferingId) throws InvalidParameterValueException, InternalErrorException, ResourceAllocationException {
        // Check that the account is valid
    	AccountVO account = _accountDao.findById(accountId);
    	if (account == null) {
    		throw new InvalidParameterValueException("Please specify a valid account.");
    	}
    	
    	// Check that the zone is valid
        DataCenterVO zone = _dcDao.findById(zoneId);
        if (zone == null) {
            throw new InvalidParameterValueException("Please specify a valid zone.");
        }
        
        // Check that the the disk offering is specified
        DiskOfferingVO diskOffering = _diskOfferingDao.findById(diskOfferingId);
        if (diskOffering == null) {
            throw new InvalidParameterValueException("Please specify a valid disk offering.");
        }
            
        // Check that there is a shared primary storage pool in the specified zone
        List<StoragePoolVO> storagePools = _poolDao.listByDataCenterId(zoneId);
        boolean sharedPoolExists = false;
        for (StoragePoolVO storagePool : storagePools) {
        	if (storagePool.isShared()) {
        		sharedPoolExists = true;
        	}
        }
        
        if (!sharedPoolExists) {
        	throw new InvalidParameterValueException("Please specify a zone that has at least one shared primary storage pool.");
        }
        
        // Check that the resource limit for volumes won't be exceeded
        if (_accountMgr.resourceLimitExceeded(account, ResourceType.volume)) {
        	ResourceAllocationException rae = new ResourceAllocationException("Maximum number of volumes for account: " + account.getAccountName() + " has been exceeded.");
        	rae.setResourceType("volume");
        	throw rae;
        }

        VolumeOperationParam param = new VolumeOperationParam();
        param.setOp(VolumeOp.Create);
        param.setAccountId(accountId);
        param.setUserId(userId);
        param.setName(name);
        param.setZoneId(zoneId);
        param.setDiskOfferingId(diskOfferingId);

        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("VolumeOperation");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateVolumeCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public long createVolumeFromSnapshotAsync(long accountId, long userId, long snapshotId) throws InternalErrorException {
        return _snapMgr.createVolumeFromSnapshotAsync(accountId, userId, snapshotId);
    }

    

    @Override
    public VolumeVO findRootVolume(long vmId) {
        List<VolumeVO> volumes = _volumeDao.findByInstanceAndType(vmId, VolumeType.ROOT);
        if (volumes != null && volumes.size() == 1)
            return volumes.get(0);
        else
            return null;
    }

    @Override
    public void deleteVolume(long volumeId) throws InternalErrorException {
        VolumeVO volume = _volumeDao.findById(volumeId);
        _storageMgr.deleteVolumeInPool(volume);
    }

    @Override
    public long deleteVolumeAsync(long volumeId) throws InvalidParameterValueException {
        // Check that the volume is valid
        VolumeVO volume = _volumeDao.findById(volumeId);
        if (volume == null)
            throw new InvalidParameterValueException("Please specify a valid volume ID.");

        // Check that the volume is stored on shared storage
        if (!_storageMgr.volumeOnSharedStoragePool(volume)) {
            throw new InvalidParameterValueException("Please specify a volume that has been created on a shared storage pool.");
        }

        // Check that the volume is not currently attached to any VM
        if (volume.getInstanceId() != null)
            throw new InvalidParameterValueException("Please specify a volume that is not attached to any VM.");

        // Check that the volume is not already destroyed
        if (volume.getDestroyed())
            throw new InvalidParameterValueException("Please specify a volume that is not already destroyed.");

        VolumeOperationParam param = new VolumeOperationParam();
        param.setOp(VolumeOp.Delete);
        param.setVolumeId(volumeId);

        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("VolumeOperation");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public List<IPAddressVO> listPublicIpAddressesBy(Long accountId, boolean allocatedOnly, Long zoneId, Long vlanDbId) {
        SearchCriteria sc = _publicIpAddressDao.createSearchCriteria();

        if (accountId != null)
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        if (zoneId != null)
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zoneId);
        if (vlanDbId != null)
            sc.addAnd("vlanDbId", SearchCriteria.Op.EQ, vlanDbId);
        if (allocatedOnly)
            sc.addAnd("allocated", SearchCriteria.Op.NNULL);

        return _publicIpAddressDao.search(sc, null);
    }

    @Override
    public List<DataCenterIpAddressVO> listPrivateIpAddressesBy(Long podId, Long zoneId) {
        if (podId != null && zoneId != null)
            return _privateIpAddressDao.listByPodIdDcId(podId.longValue(), zoneId.longValue());
        else
            return new ArrayList<DataCenterIpAddressVO>();
    }

    @Override
    public String generateRandomPassword() {
        Random r = new Random();
        StringBuffer password = new StringBuffer();

        // Generate random 3-character string with a lowercase character,
        // uppercase character, and a digit

        // Generate a random lowercase character
        int lowercase = generateLowercaseChar(r);
        // Generate a random uppercase character ()
        int uppercase = generateUppercaseChar(r);
        // Generate a random digit between 2 and 9
        int digit = r.nextInt(8) + 2;

        // Append to the password
        password.append((char) lowercase);
        password.append((char) uppercase);
        password.append(digit);

        // Generate a random 6-character string with only lowercase
        // characters
        for (int i = 0; i < 6; i++) {
            // Generate a random lowercase character (don't allow lowercase
            // "l" or lowercase "o")
            lowercase = generateLowercaseChar(r);
            // Append to the password
            password.append((char) lowercase);
        }

        return password.toString();
    }

    private char generateLowercaseChar(Random r) {
        // Don't allow lowercase "l" or lowercase "o"
        int lowercase = -1;
        while (lowercase == -1 || lowercase == 108 || lowercase == 111)
            lowercase = r.nextInt(26) + 26 + 71;
        return ((char) lowercase);
    }

    private char generateUppercaseChar(Random r) {
        // Don't allow uppercase "I" or uppercase "O"
        int uppercase = -1;
        while (uppercase == -1 || uppercase == 73 || uppercase == 79)
            uppercase = r.nextInt(26) + 65;
        return ((char) uppercase);
    }

    @Override
    public boolean resetVMPassword(long userId, long vmId, String password) {
        if (password == null || password.equals("")) {
            return false;
        }
        boolean succeed = _vmMgr.resetVMPassword(userId, vmId, password);

        // Log event
        UserVmVO userVm = _userVmDao.findById(vmId);
        if (userVm != null) {
            if (succeed) {
                saveEvent(userId, userVm.getAccountId(), EventVO.LEVEL_INFO, EventTypes.EVENT_VM_RESETPASSWORD, "successfully reset password for VM : " + userVm.getName(), null);
            } else {
                saveEvent(userId, userVm.getAccountId(), EventVO.LEVEL_ERROR, EventTypes.EVENT_VM_RESETPASSWORD, "unable to reset password for VM : " + userVm.getName(), null);
            }
        } else {
            s_logger.warn("Unable to find vm = " + vmId + " to reset password");
        }
        return succeed;
    }

    @Override
    public void attachVolumeToVM(long vmId, long volumeId) throws InternalErrorException {
        _vmMgr.attachVolumeToVM(vmId, volumeId);
    }

    @Override
    public long attachVolumeToVMAsync(long vmId, long volumeId) throws InvalidParameterValueException {
        VolumeVO volume = _volumeDao.findById(volumeId);

        // Check that the volume is a data volume
        if (volume == null || volume.getVolumeType() != VolumeType.DATADISK) {
            throw new InvalidParameterValueException("Please specify a valid data volume.");
        }

        // Check that the volume is stored on shared storage
        if (!_storageMgr.volumeOnSharedStoragePool(volume)) {
            throw new InvalidParameterValueException("Please specify a volume that has been created on a shared storage pool.");
        }

        // Check that the VM is a UserVM
        UserVmVO vm = _userVmDao.findById(vmId);
        if (vm == null || vm.getType() != VirtualMachine.Type.User) {
            throw new InvalidParameterValueException("Please specify a valid User VM.");
        }

        // Check that the volume is not currently attached to any VM
        if (volume.getInstanceId() != null) {
            throw new InvalidParameterValueException("Please specify a volume that is not attached to any VM.");
        }

        // Check that the volume is not destroyed
        if (volume.getDestroyed()) {
            throw new InvalidParameterValueException("Please specify a volume that is not destroyed.");
        }

        // Check that the VM has less than 6 data volumes attached
        List<VolumeVO> existingDataVolumes = _volumeDao.findByInstanceAndType(vmId, VolumeType.DATADISK);
        if (existingDataVolumes.size() >= 6) {
            throw new InvalidParameterValueException("The specified VM already has the maximum number of data disks (6). Please specify another VM.");
        }
        
        // Check that the VM and the volume are in the same pod
        if (vm.getPodId() != volume.getPodId()) {
        	throw new InvalidParameterValueException("The specified VM is in a different pod than the volume.");
        }

        VolumeOperationParam param = new VolumeOperationParam();
        param.setOp(VolumeOp.Attach);
        param.setVmId(vmId);
        param.setVolumeId(volumeId);

        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("VolumeOperation");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public void detachVolumeFromVM(long volumeId) throws InternalErrorException {
        _vmMgr.detachVolumeFromVM(volumeId);
    }

    @Override
    public long detachVolumeFromVMAsync(long volumeId) throws InvalidParameterValueException {
        VolumeVO volume = _volumeDao.findById(volumeId);

        // Check that the volume is a data volume
        if (volume.getVolumeType() != VolumeType.DATADISK) {
            throw new InvalidParameterValueException("Please specify a data volume.");
        }

        // Check that the volume is stored on shared storage
        if (!_storageMgr.volumeOnSharedStoragePool(volume)) {
            throw new InvalidParameterValueException("Please specify a volume that has been created on a shared storage pool.");
        }

        Long vmId = volume.getInstanceId();

        // Check that the volume is currently attached to a VM
        if (vmId == null) {
            throw new InvalidParameterValueException("The specified volume is not attached to a VM.");
        }

        VolumeOperationParam param = new VolumeOperationParam();
        param.setOp(VolumeOp.Detach);
        param.setVolumeId(volumeId);

        Gson gson = GsonHelper.getBuilder().create();

		AsyncJobVO job = new AsyncJobVO();
		job.setUserId(UserContext.current().getUserId());
		job.setAccountId(UserContext.current().getAccountId());
		job.setCmd("VolumeOperation");
		job.setCmdInfo(gson.toJson(param));
		
		return _asyncMgr.submitAsyncJob(job);
	}

    @Override
    public boolean attachISOToVM(long vmId, long userId, long isoId, boolean attach) {
    	UserVmVO vm = _userVmDao.findById(vmId);
    	VMTemplateVO iso = _templateDao.findById(isoId);
        boolean success = _vmMgr.attachISOToVM(vmId, isoId, attach);

        if (success) {
            VMInstanceVO updatedInstance = _vmInstanceDao.createForUpdate();
            if (attach) {
                updatedInstance.setIsoId(iso.getId().longValue());
            } else {
                updatedInstance.setIsoId(null);
            }
            _vmInstanceDao.update(vmId, updatedInstance);

            if (attach) {
                saveEvent(userId, vm.getAccountId(), EventVO.LEVEL_INFO, EventTypes.EVENT_ISO_ATTACH, "Successfully attached ISO: " + iso.getName() + " to VM with ID: " + vmId,
                        null);
            } else {
                saveEvent(userId, vm.getAccountId(), EventVO.LEVEL_INFO, EventTypes.EVENT_ISO_DETACH, "Successfully detached ISO from VM with ID: " + vmId, null);
            }
        } else {
            if (attach) {
                saveEvent(userId, vm.getAccountId(), EventVO.LEVEL_ERROR, EventTypes.EVENT_ISO_ATTACH, "Failed to attach ISO: " + iso.getName() + " to VM with ID: " + vmId, null);
            } else {
                saveEvent(userId, vm.getAccountId(), EventVO.LEVEL_ERROR, EventTypes.EVENT_ISO_DETACH, "Failed to detach ISO from VM with ID: " + vmId, null);
            }
        }

        return success;
    }

    @Override
    public long attachISOToVMAsync(long vmId, long userId, long isoId) throws InvalidParameterValueException {
        UserVmVO vm = _userVmDao.findById(vmId);
        if (vm == null) {
            throw new InvalidParameterValueException("Unable to find VM with ID " + vmId);
        }

        VMTemplateVO iso = _templateDao.findById(isoId);
        if (iso == null) {
            throw new InvalidParameterValueException("Unable to find ISO with id " + isoId);
        }

        AccountVO account = _accountDao.findById(vm.getAccountId());
        if (account == null) {
            throw new InvalidParameterValueException("Unable to find account for VM with ID " + vmId);
        }
        
        State vmState = vm.getState();
        if (vmState != State.Running && vmState != State.Stopped) {
        	throw new InvalidParameterValueException("Please specify a VM that is either Stopped or Running.");
        }
            
        AttachISOParam param = new AttachISOParam(vmId, userId, isoId, true);
        Gson gson = GsonHelper.getBuilder().create();

		AsyncJobVO job = new AsyncJobVO();
		job.setUserId(UserContext.current().getUserId());
		job.setAccountId(UserContext.current().getAccountId());
		job.setCmd("AttachISO");
		job.setCmdInfo(gson.toJson(param));
		return _asyncMgr.submitAsyncJob(job, true);
	}

    @Override
    public long detachISOFromVMAsync(long vmId, long userId) throws InvalidParameterValueException {
        UserVm userVM = _userVmDao.findById(vmId);
        if (userVM == null) {
            throw new InvalidParameterValueException("Please specify a valid VM.");
        }

        Long isoId = userVM.getIsoId();
        if (isoId == null) {
            throw new InvalidParameterValueException("Please specify a valid ISO.");
        }
        
        State vmState = userVM.getState();
        if (vmState != State.Running && vmState != State.Stopped) {
        	throw new InvalidParameterValueException("Please specify a VM that is either Stopped or Running.");
        }

        AttachISOParam param = new AttachISOParam(vmId, userId, isoId.longValue(), false);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("AttachISO");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public long resetVMPasswordAsync(long userId, long vmId, String password) {
        ResetVMPasswordParam param = new ResetVMPasswordParam(userId, vmId, password);
        Gson gson = GsonHelper.getBuilder().create();

		AsyncJobVO job = new AsyncJobVO();
		job.setUserId(UserContext.current().getUserId());
		job.setAccountId(UserContext.current().getAccountId());
		job.setCmd("ResetVMPassword");
		job.setCmdInfo(gson.toJson(param));
		
		return _asyncMgr.submitAsyncJob(job, true);
	}

    private boolean validPassword(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (password.charAt(i) == ' ') {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean reconnect(long hostId) {
        try {
            return _agentMgr.reconnect(hostId);
        } catch (AgentUnavailableException e) {
            return false;
        }
    }

    @Override
    public long reconnectAsync(long hostId) {
        Long param = new Long(hostId);
        Gson gson = GsonHelper.getBuilder().create();

		AsyncJobVO job = new AsyncJobVO();
		job.setUserId(UserContext.current().getUserId());
		job.setAccountId(UserContext.current().getAccountId());
		job.setCmd("Reconnect");
		job.setCmdInfo(gson.toJson(param));
		
		return _asyncMgr.submitAsyncJob(job);
	}

    @Override
    public UserVm deployVirtualMachine(long userId, long accountId, long dataCenterId, long serviceOfferingId, long dataDiskOfferingId, long templateId, long rootDiskOfferingId,
            String domain, String password, String displayName, String group, String userData) throws ResourceAllocationException, InvalidParameterValueException, InternalErrorException,
            InsufficientStorageCapacityException, PermissionDeniedException {

        dataCenterId = validateDataCenterId(dataCenterId);

        AccountVO account = _accountDao.findById(accountId);
        DataCenterVO dc = _dcDao.findById(dataCenterId);
        ServiceOfferingVO offering = _offeringsDao.findById(serviceOfferingId);
        VMTemplateVO template = _templateDao.findById(templateId);

        // Make sure a valid template ID was specified
        if (template == null) {
            throw new InvalidParameterValueException("Please specify a valid template or ISO ID.");
        }
        byte [] decodedUserData = null;
        if (userData != null) {
        	if (userData.length() >= 2* UserVmManager.MAX_USER_DATA_LENGTH_BYTES) {
        		throw new InvalidParameterValueException("User data is too long");
        	}
        	decodedUserData = org.apache.commons.codec.binary.Base64.decodeBase64(userData.getBytes());
        	if (decodedUserData.length > UserVmManager.MAX_USER_DATA_LENGTH_BYTES){
        		throw new InvalidParameterValueException("User data is too long");
        	}
			
        }

        boolean isIso = Storage.ImageFormat.ISO.equals(template.getFormat());
        DiskOfferingVO dataDiskOffering = _diskOfferingDao.findById(dataDiskOfferingId);

        // If an ISO path was passed in, create an empty data disk offering
        if (isIso) {
            dataDiskOffering = new DiskOfferingVO(1, "Empty Disk Offering", "Empty", 0, false);
            dataDiskOffering.setId(new Long(-1));
        }
        
        // If an ISO path was passed in, then a root disk offering must be passed in
        DiskOfferingVO rootDiskOffering = null;
        if (isIso) {
            rootDiskOffering = _diskOfferingDao.findById(rootDiskOfferingId);
        }

        // TODO: Checks such as is the user allowed to use the template and purchase the service offering id.

        if (domain == null) {
            domain = "v" + Long.toHexString(accountId) + _domain;
        }

        // Check that the password was passed in and is valid
        if (!template.getEnablePassword()) {
            password = "saved_password";
        }

        if (password == null || password.equals("") || (!validPassword(password))) {
            throw new InvalidParameterValueException("A valid password for this virtual machine was not provided.");
        }
        UserStatisticsVO stats = _userStatsDao.findBy(account.getId(), dataCenterId);
        if (stats == null) {
            stats = new UserStatisticsVO(account.getId(), dataCenterId);
            _userStatsDao.persist(stats);
        }
        
    	Long vmId = _vmDao.getNextInSequence(Long.class, "id");
    	
        // check if we are within context of async-execution
        AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
        if (asyncExecutor != null) {
            AsyncJobVO job = asyncExecutor.getJob();

            if (s_logger.isInfoEnabled())
                s_logger.info("DeployVM acquired a new instance " + vmId + ", update async job-" + job.getId() + " progress status");

            _asyncMgr.updateAsyncJobAttachment(job.getId(), "vm_instance", vmId);
            _asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, vmId);
        }

        HashMap<Long, StoragePoolVO> avoids = new HashMap<Long, StoragePoolVO>();

        for (int retry = 0; retry < 5; retry++) {
            String externalIp = null;
            UserVmVO created = null;

            ArrayList<StoragePoolVO> a = new ArrayList<StoragePoolVO>(avoids.values());
            if (offering.getGuestIpType() == GuestIpType.Virtualized) {
                try {
                    externalIp = _networkMgr.assignSourceNatIpAddress(account, dc, domain, offering);
                } catch (ResourceAllocationException rae) {
                    throw rae;
                }

                if (externalIp == null) {
                    throw new InternalErrorException("Unable to allocate a source nat ip address");
                }

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Source Nat acquired: " + externalIp);
                }

                try {
                    created = _vmMgr.createVirtualMachine(vmId, userId, account, dc, offering, dataDiskOffering, template, rootDiskOffering, displayName, group, userData, a);
                } catch (ResourceAllocationException rae) {
                    throw rae;
                }
            } else {

                try {
                    created = _vmMgr.createDirectlyAttachedVM(vmId, userId, account, dc, offering, dataDiskOffering, template, rootDiskOffering, displayName, group, userData, a);
                } catch (ResourceAllocationException rae) {
                    throw rae;
                }
            }

            if (created == null) {
                throw new InternalErrorException("Unable to create VM for account (" + accountId + "): " + account.getAccountName());
            }

            if (s_logger.isDebugEnabled()) {
                s_logger.debug("VM created: " + created.getId() + "-" + created.getName());
            }

            UserVm started = null;
            if (isIso) {
                String isoPath = _storageMgr.getAbsoluteIsoPath(templateId, dataCenterId);
                started = _vmMgr.startVirtualMachine(userId, created.getId(), password, isoPath);
            } else {
                started = _vmMgr.startVirtualMachine(userId, created.getId(), password, null);
            }
            if (started == null) {
                List<Pair<VolumeVO, StoragePoolVO>> disks = _storageMgr.isStoredOn(created);
                // NOTE: We now destroy a VM if the deploy process fails at any step. We now
                // have a lazy delete so there is still some time to figure out what's wrong.
                _vmMgr.destroyVirtualMachine(userId, created.getId());

                boolean retryCreate = true;
                for (Pair<VolumeVO, StoragePoolVO> disk : disks) {
                    if (disk.second().isLocal()) {
                        avoids.put(disk.second().getId(), disk.second());
                    } else {
                        retryCreate = false;
                    }
                }

                if (retryCreate) {
                    continue;
                } else {
                    throw new InternalErrorException("Unable to start the VM " + created.getId() + "-" + created.getName());
                }
            } else {
                if (isIso) {
                    VMInstanceVO updatedInstance = _vmInstanceDao.createForUpdate();
                    updatedInstance.setIsoId(templateId);
                    _vmInstanceDao.update(started.getId(), updatedInstance);
                    started = _userVmDao.findById(started.getId());
                }
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("VM started: " + started.getId() + "-" + started.getName());
            }
            return started;
        }

        return null;
    }

    @Override
    public long deployVirtualMachineAsync(long userId, long accountId, long dataCenterId, long serviceOfferingId, long dataDiskOfferingId, long templateId,
            long rootDiskOfferingId, String domain, String password, String displayName, String group, String userData)  throws InvalidParameterValueException, PermissionDeniedException {

    	AccountVO account = _accountDao.findById(accountId);
        if (account == null) {
            throw new InvalidParameterValueException("Unable to find account: " + accountId);
        }

        DataCenterVO dc = _dcDao.findById(dataCenterId);
        if (dc == null) {
            throw new InvalidParameterValueException("Unable to find zone: " + dataCenterId);
        }

        ServiceOfferingVO offering = _offeringsDao.findById(serviceOfferingId);
        if (offering == null) {
            throw new InvalidParameterValueException("Unable to find service offering: " + serviceOfferingId);
        }

        VMTemplateVO template = _templateDao.findById(templateId);
        // Make sure a valid template ID was specified
        if (template == null) {
            throw new InvalidParameterValueException("Please specify a valid template or ISO ID.");
        }

        boolean isIso = Storage.ImageFormat.ISO.equals(template.getFormat());
        
        if (isIso && !template.isBootable()) {
        	throw new InvalidParameterValueException("Please specify a bootable ISO.");
        }

        // If an ISO path was not passed in, a data disk offering must be passed in
        // If an ISO path was passed in, a data disk offering must not be passed in
        DiskOfferingVO dataDiskOffering = _diskOfferingDao.findById(dataDiskOfferingId);
        if (!isIso && dataDiskOffering == null) {
            throw new InvalidParameterValueException("Unable to find data disk offering: " + dataDiskOfferingId);
        } else if (isIso && dataDiskOffering != null) {
            throw new InvalidParameterValueException("A data disk offering may not be passed in if this VM is to be booted from an ISO.");
        }
        
        // validate that the template is usable by the account
        if (!template.isPublicTemplate()) {
            Long templateOwner = template.getAccountId();
            if (!BaseCmd.isAdmin(account.getType()) && ((templateOwner == null) || (templateOwner.longValue() != accountId))) {
                // since the current account is not the owner of the template, check the launch permissions table to see if the
                // account can launch a VM from this template
                LaunchPermissionVO permission = _launchPermissionDao.findByTemplateAndAccount(templateId, account.getId().longValue());
                if (permission == null) {
                    throw new PermissionDeniedException("Account " + account.getAccountName() + " does not have permission to launch instances from template " + template.getName());
                }
            }
        }
        
        // If an ISO path was passed in, then a root disk offering must be passed in
        DiskOfferingVO rootDiskOffering = null;
        if (isIso) {
            rootDiskOffering = _diskOfferingDao.findById(rootDiskOfferingId);
            if (rootDiskOffering == null) {
                throw new InvalidParameterValueException("ISO path was specified, so root disk offering is required. Unable to find root disk offering with ID "
                        + rootDiskOfferingId);
            }
        }
        byte [] decodedUserData = null;
        if (userData != null) {
        	if (userData.length() >= 2* UserVmManager.MAX_USER_DATA_LENGTH_BYTES) {
        		throw new InvalidParameterValueException("User data is too long");
        	}
        	decodedUserData = org.apache.commons.codec.binary.Base64.decodeBase64(userData.getBytes());
        	if (decodedUserData.length > UserVmManager.MAX_USER_DATA_LENGTH_BYTES){
        		throw new InvalidParameterValueException("User data is too long");
        	}
        	if (decodedUserData.length < 1) {
        		throw new InvalidParameterValueException("User data is too short");
        	}
			
        }

    	
        DeployVMParam param = new DeployVMParam(userId, accountId, dataCenterId, serviceOfferingId, dataDiskOfferingId, templateId, rootDiskOfferingId, domain, password,
                displayName, group, userData);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeployVM");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(DeployVMCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public UserVm startVirtualMachine(long userId, long vmId, String isoPath) throws InternalErrorException {
        return _vmMgr.startVirtualMachine(userId, vmId, isoPath);
    }

    @Override
    public long startVirtualMachineAsync(long userId, long vmId, String isoPath) {
        VMOperationParam param = new VMOperationParam(userId, vmId, isoPath);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StartVM");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(StartVMCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean stopVirtualMachine(long userId, long vmId) {
        return _vmMgr.stopVirtualMachine(userId, vmId);
    }

    @Override
    public long stopVirtualMachineAsync(long userId, long vmId) {
        VMOperationParam param = new VMOperationParam(userId, vmId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StopVM");
        job.setCmdInfo(gson.toJson(param));
        
        // use the same result result object name as StartVMCmd
        job.setCmdOriginator(StartVMCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean rebootVirtualMachine(long userId, long vmId) {
        return _vmMgr.rebootVirtualMachine(userId, vmId);
    }

    @Override
    public long rebootVirtualMachineAsync(long userId, long vmId) {
        VMOperationParam param = new VMOperationParam(userId, vmId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RebootVM");
        job.setCmdInfo(gson.toJson(param));
        
        // use the same result result object name as StartVMCmd
        job.setCmdOriginator(StartVMCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean destroyVirtualMachine(long userId, long vmId) {
        return _vmMgr.destroyVirtualMachine(userId, vmId);
    }

    @Override
    public long destroyVirtualMachineAsync(long userId, long vmId) {
        VMOperationParam param = new VMOperationParam(userId, vmId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DestroyVM");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean recoverVirtualMachine(long vmId) throws ResourceAllocationException {
        return _vmMgr.recoverVirtualMachine(vmId);
    }

    @Override
    public String upgradeVirtualMachine(long userId, long vmId, long serviceOfferingId) {
        String result = _vmMgr.upgradeVirtualMachine(vmId, serviceOfferingId);

        UserVmVO userVm = _userVmDao.findById(vmId);
        if (result.equals("Upgrade successful")) {
            String params = "id=" + vmId + "\nvmName=" + userVm.getName() + "\nsoId=" + serviceOfferingId + "\ntId=" + userVm.getTemplateId() + "\ndcId="
                    + userVm.getDataCenterId();
            this.saveEvent(userId, userVm.getAccountId(), EventVO.LEVEL_INFO, EventTypes.EVENT_VM_UPGRADE, "Successfully upgrade service offering on VM : " + userVm.getName(),
                    params);
        }
        return result;
    }

    @Override
    public long upgradeVirtualMachineAsync(long userId, long vmId, long serviceOfferingId) {
        UpgradeVMParam param = new UpgradeVMParam(userId, vmId, serviceOfferingId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("UpgradeVM");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public void updateVirtualMachine(long vmId, String displayName, String group, boolean enable, Long userId, long accountId) {
        VMInstanceVO vm = _vmInstanceDao.findById(vmId);
        if (vm == null) {
            throw new VmopsRuntimeException("Unable to find virual machine with id " + vmId);
        }

        boolean haEnabled = vm.isHaEnabled();
        _vmInstanceDao.updateVM(vmId, displayName, group, enable);
        if (haEnabled != enable) {
            String description = null;
            String type = null;
            if (enable) {
                description = "Successfully enabled HA for virtual machine " + vm.getName();
                type = EventTypes.EVENT_VM_ENABLE_HA;
            } else {
                description = "Successfully disabled HA for virtual machine " + vm.getName();
                type = EventTypes.EVENT_VM_DISABLE_HA;
            }
            // create a event for the change in HA Enabled flag
            saveEvent(userId, accountId, EventVO.LEVEL_INFO, type, description, null);
        }
    }

    @Override
    public DomainRouter startRouter(long routerId) throws InternalErrorException {
        return _networkMgr.startRouter(routerId);
    }

    @Override
    public long startRouterAsync(long routerId) {
        VMOperationParam param = new VMOperationParam(0, routerId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StartRouter");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(StartRouterCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean stopRouter(long routerId) {
        return _networkMgr.stopRouter(routerId);
    }

    @Override
    public long stopRouterAsync(long routerId) {
        VMOperationParam param = new VMOperationParam(0, routerId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StopRouter");
        job.setCmdInfo(gson.toJson(param));
        // use the same result object name as StartRouterCmd
        job.setCmdOriginator(StartRouterCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean rebootRouter(long routerId) throws InternalErrorException {
        return _networkMgr.rebootRouter(routerId);
    }

    @Override
    public long rebootRouterAsync(long routerId) {
        VMOperationParam param = new VMOperationParam(0, routerId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RebootRouter");
        job.setCmdInfo(gson.toJson(param));
        // use the same result object name as StartRouterCmd
        job.setCmdOriginator(StartRouterCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public boolean destroyRouter(long routerId) {
        return _networkMgr.destroyRouter(routerId);
    }

    @Override
    public DomainRouterVO findDomainRouterBy(long accountId, long dataCenterId) {
        dataCenterId = validateDataCenterId(dataCenterId);
        return _routerDao.findBy(accountId, dataCenterId);
    }

    @Override
    public DomainRouterVO findDomainRouterById(long domainRouterId) {
        return _routerDao.findById(domainRouterId);
    }

    @Override
    public DataCenterVO getDataCenterBy(long dataCenterId) {
        dataCenterId = validateDataCenterId(dataCenterId);
        return _dcDao.findById(dataCenterId);
    }

    @Override
    public HostPodVO getPodBy(long podId) {        
        return _hostPodDao.findById(podId);
    }

    
    @Override
    public List<DataCenterVO> listDataCenters() {
        return _dcDao.listAllActive();
    }

    @Override
    public List<DataCenterVO> listDataCentersBy(long accountId) {
        List<DataCenterVO> dcs = _dcDao.listAllActive();
        List<DomainRouterVO> routers = _routerDao.listBy(accountId);
        for (Iterator<DataCenterVO> iter = dcs.iterator(); iter.hasNext();) {
            DataCenterVO dc = iter.next();
            boolean found = false;
            for (DomainRouterVO router : routers) {
                if (dc.getId() == router.getDataCenterId()) {
                    found = true;
                    break;
                }
            }
            if (!found)
                iter.remove();
        }
        return dcs;
    }

    @Override
    public HostVO getHostBy(long hostId) {
        return _hostDao.findById(hostId);
    }

    public void updateHost(long hostId, long guestOSCategoryId) throws InvalidParameterValueException {
    	// Verify that the guest OS Category exists
    	if (guestOSCategoryId > 0) {
    		if (_guestOSCategoryDao.findById(guestOSCategoryId) == null) {
    			throw new InvalidParameterValueException("Please specify a valid guest OS category.");
    		}
    	}
    	
    	_agentMgr.updateHost(hostId, guestOSCategoryId);
    }
    
    public boolean deleteHost(long hostId) {
        return _agentMgr.deleteHost(hostId);
    }

    private long validateDataCenterId(long dataCenterId) {
        // if the dataCenterId is -1, we will use the default
        if (dataCenterId == -1) {
            if (_defaultDataCenterId == -1) {
                _defaultDataCenterId = _dcDao.findByName(_defaultZone).getId();
            }
            return _defaultDataCenterId;
        }
        return dataCenterId;
    }

    @Override
    public long getId() {
        return MacAddress.getMacAddress().toLong();
    }

    protected void checkPortParameters(String publicPort, String privatePort, String privateIp, String proto) throws InvalidParameterValueException {

        if (!NetUtils.isValidPort(publicPort)) {
            throw new InvalidParameterValueException("publicPort is an invalid value");
        }
        if (!NetUtils.isValidPort(privatePort)) {
            throw new InvalidParameterValueException("privatePort is an invalid value");
        }

        s_logger.debug("Checking if " + privateIp + " is a valid private IP address. Guest IP address is: " + _configs.get("guest.ip.network"));

        if (!NetUtils.isValidPrivateIp(privateIp, _configs.get("guest.ip.network"))) {
            throw new InvalidParameterValueException("Invalid private ip address");
        }
        if (!NetUtils.isValidProto(proto)) {
            throw new InvalidParameterValueException("Invalid protocol");
        }
    }

    @Override
    @DB
    public void assignSecurityGroup(Long userId, Long securityGroupId, List<Long> securityGroupIdList, String publicIp, Long vmId) throws PermissionDeniedException,
            NetworkRuleConflictException, InvalidParameterValueException, InternalErrorException {
        boolean locked = false;
        Transaction txn = Transaction.currentTxn();
        try {
            UserVmVO userVm = _userVmDao.findById(vmId);
            if (userVm == null) {
                s_logger.warn("Unable to find virtual machine with id " + vmId);
                throw new InvalidParameterValueException("Unable to find virtual machine with id " + vmId);
            }

            State vmState = userVm.getState();
            switch (vmState) {
            case Destroyed:
            case Error:
            case Expunging:
            case Unknown:
                throw new InvalidParameterValueException("Unable to assign security group(s) '"
                        + ((securityGroupId == null) ? StringUtils.join(securityGroupIdList, ",") : securityGroupId) + "' to virtual machine " + vmId
                        + " due to virtual machine being in an invalid state for assigning a security group (" + vmState + ")");
            }

            DomainRouterVO router = _routerDao.findById(userVm.getDomainRouterId());
            if ((router == null) || (router.getHostId() == null)) {
                s_logger.warn("Unable to find router (" + userVm.getDomainRouterId() + ") for virtual machine with id " + vmId);
                throw new InvalidParameterValueException("Unable to find router (" + userVm.getDomainRouterId() + ") for virtual machine with id " + vmId);
            }

            IPAddressVO ipVO = _publicIpAddressDao.acquire(publicIp);
            if ((ipVO == null) || (ipVO.getAllocated() == null)) {
                // throw this exception because hackers can use the api to probe for allocated ips
                throw new PermissionDeniedException("User does not own supplied address");
            }
            locked = true;

            if ((ipVO.getAccountId() == null) || (ipVO.getAccountId().longValue() != userVm.getAccountId())) {
                throw new PermissionDeniedException("User does not own supplied address");
            }

            txn.start();

            if (securityGroupId == null) {
                // - send one command to agent to remove *all* rules for
                // publicIp/vm combo
                // - add back all rules based on list passed in
                List<FirewallRuleVO> fwRulesToRemove = _firewallRulesDao.listForwardingByPubAndPrivIp(true, publicIp, userVm.getGuestIpAddress());
                for (FirewallRuleVO fwRule : fwRulesToRemove) {
                    fwRule.setEnabled(false);
                }

                List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(null, fwRulesToRemove, router);
                if ((updatedRules != null) && (updatedRules.size() != fwRulesToRemove.size())) {
                    throw new InternalErrorException("Unable to clean up network rules for public IP " + publicIp + " and guest vm " + userVm.getName()
                            + " while applying security group(s) '" + ((securityGroupId == null) ? StringUtils.join(securityGroupIdList, ",") : securityGroupId) + "'");
                } else {
                    // Save and create the event
                    String description;
                    String type = EventTypes.EVENT_NET_RULE_DELETE;
                    String ruleName = "ip forwarding";
                    String level = EventVO.LEVEL_INFO;
                    Account account = _accountDao.findById(userVm.getAccountId());

                    for (FirewallRuleVO fwRule : updatedRules) {
                        _firewallRulesDao.remove(fwRule.getId());

                        description = "deleted " + ruleName + " rule [" + fwRule.getPublicIpAddress() + ":" + fwRule.getPublicPort() + "]->[" + fwRule.getPrivateIpAddress() + ":"
                                + fwRule.getPrivatePort() + "]" + " " + fwRule.getProtocol();

                        saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                    }
                }

                List<SecurityGroupVMMapVO> sgVmMappings = _securityGroupVMMapDao.listByIpAndInstanceId(publicIp, vmId);
                for (SecurityGroupVMMapVO sgVmMapping : sgVmMappings) {
                    boolean success = _securityGroupVMMapDao.remove(sgVmMapping.getId());

                    SecurityGroupVO securityGroup = _securityGroupDao.findById(sgVmMapping.getSecurityGroupId());

                    // save off an event for removing the security group
                    EventVO event = new EventVO();
                    event.setUserId(userId);
                    event.setAccountId(userVm.getAccountId());
                    event.setType(EventTypes.EVENT_SECURITY_GROUP_REMOVE);
                    String sgRemoveLevel = EventVO.LEVEL_INFO;
                    String sgRemoveDesc = "Successfully removed ";
                    if (!success) {
                        sgRemoveLevel = EventVO.LEVEL_ERROR;
                        sgRemoveDesc = "Failed to remove ";
                    }
                    String params = "sgId="+securityGroup.getId()+"\nvmId"+vmId;
                    event.setParameters(params);
                    event.setDescription(sgRemoveDesc + "security group " + securityGroup.getName() + " from virtual machine " + userVm.getName());
                    event.setLevel(sgRemoveLevel);
                    _eventDao.persist(event);
                }
            }

            List<Long> finalSecurityGroupIdList = new ArrayList<Long>();
            if (securityGroupId != null) {
                finalSecurityGroupIdList.add(securityGroupId);
            } else {
                finalSecurityGroupIdList.addAll(securityGroupIdList);
            }

            for (Long sgId : finalSecurityGroupIdList) {
                if (sgId.longValue() == 0) {
                    // group id of 0 means to remove all groups, which we just did above
                    break;
                }

                SecurityGroupVO securityGroup = _securityGroupDao.findById(Long.valueOf(sgId));
                if (securityGroup == null) {
                    s_logger.warn("Unable to find security group with id " + sgId);
                    throw new InvalidParameterValueException("Unable to find security group with id " + sgId);
                }

                if (!_domainDao.isChildDomain(securityGroup.getDomainId(), userVm.getDomainId())) {
                    s_logger.warn("Unable to assign security group " + sgId + " to user vm " + vmId + ", user vm's domain (" + userVm.getDomainId()
                            + ") is not in the domain of the security group (" + securityGroup.getDomainId() + ")");
                    throw new InvalidParameterValueException("Unable to assign security group " + sgId + " to user vm " + vmId + ", user vm's domain (" + userVm.getDomainId()
                            + ") is not in the domain of the security group (" + securityGroup.getDomainId() + ")");
                }

                // check for ip address/port conflicts by checking exising forwarding and loadbalancing rules
                List<FirewallRuleVO> existingRulesOnPubIp = _firewallRulesDao.listIPForwarding(publicIp);
                Map<String, Pair<String, String>> mappedPublicPorts = new HashMap<String, Pair<String, String>>();

                if (existingRulesOnPubIp != null) {
                    for (FirewallRuleVO fwRule : existingRulesOnPubIp) {
                        mappedPublicPorts.put(fwRule.getPublicPort(), new Pair<String, String>(fwRule.getPrivateIpAddress(), fwRule.getPrivatePort()));
                    }
                }

                List<LoadBalancerVO> loadBalancers = _loadBalancerDao.listByIpAddress(publicIp);
                if (loadBalancers != null) {
                    for (LoadBalancerVO loadBalancer : loadBalancers) {
                        // load balancers don't have to be applied to an
                        // instance for there to be a conflict on the load
                        // balancers ip/port, so just
                        // map the public port to a pair of empty strings
                        mappedPublicPorts.put(loadBalancer.getPublicPort(), new Pair<String, String>("", ""));
                    }
                }

                List<FirewallRuleVO> firewallRulesToApply = new ArrayList<FirewallRuleVO>();
                List<NetworkRuleConfigVO> netRules = _networkRuleConfigDao.listBySecurityGroupId(sgId);
                for (NetworkRuleConfigVO netRule : netRules) {
                    Pair<String, String> privateIpPort = mappedPublicPorts.get(netRule.getPublicPort());
                    if (privateIpPort != null) {
                        if (privateIpPort.first().equals(userVm.getGuestIpAddress()) && privateIpPort.second().equals(netRule.getPrivatePort())) {
                            continue; // already mapped
                        } else {
                            throw new NetworkRuleConflictException("An existing network rule for " + publicIp + ":" + netRule.getPublicPort()
                                    + " already exists, found while trying to apply firewall rule " + netRule.getId() + " from security group " + securityGroup.getName() + ".");
                        }
                    }

                    FirewallRuleVO newFwRule = new FirewallRuleVO();
                    newFwRule.setEnabled(true);
                    newFwRule.setForwarding(true);
                    newFwRule.setPrivatePort(netRule.getPrivatePort());
                    newFwRule.setProtocol(netRule.getProtocol());
                    newFwRule.setPublicPort(netRule.getPublicPort());
                    newFwRule.setPublicIpAddress(publicIp);
                    newFwRule.setPrivateIpAddress(userVm.getGuestIpAddress());
                    newFwRule.setGroupId(netRule.getSecurityGroupId());

                    firewallRulesToApply.add(newFwRule);
                }

                List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(publicIp, firewallRulesToApply, router);

                // Save and create the event
                String description;
                String type = EventTypes.EVENT_NET_RULE_ADD;
                String ruleName = "ip forwarding";
                String level = EventVO.LEVEL_INFO;
                Account account = _accountDao.findById(userVm.getAccountId());

                // Save off information for the event that the security group was applied
                EventVO event = new EventVO();
                event.setUserId(userId);
                event.setAccountId(userVm.getAccountId());
                event.setType(EventTypes.EVENT_SECURITY_GROUP_APPLY);

                if (updatedRules != null) {
                    SecurityGroupVMMapVO sgVmMap = new SecurityGroupVMMapVO(sgId, publicIp, vmId);
                    _securityGroupVMMapDao.persist(sgVmMap);

                    for (FirewallRuleVO updatedRule : updatedRules) {
                        if (updatedRule.getId() == null) {
                            _firewallRulesDao.persist(updatedRule);

                            description = "created new " + ruleName + " rule [" + updatedRule.getPublicIpAddress() + ":" + updatedRule.getPublicPort() + "]->["
                                    + updatedRule.getPrivateIpAddress() + ":" + updatedRule.getPrivatePort() + "]" + " " + updatedRule.getProtocol();

                            saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                        }
                    }
                }

                if ((updatedRules != null) && (updatedRules.size() == firewallRulesToApply.size())) {
                    event.setDescription("Successfully applied security group " + securityGroup.getName() + " to virtual machine " + userVm.getName());
                    String params = "sgId="+securityGroup.getId()+"\nvmId="+vmId+"\nnumRules="+updatedRules.size()+"\ndcId="+userVm.getDataCenterId();
                    event.setParameters(params);
                    event.setLevel(EventVO.LEVEL_INFO);
                    _eventDao.persist(event);
                } else {
                    s_logger.warn("Failed to apply security group " + sgId + " to guest virtual machine " + vmId);

                    event.setDescription("Failed to apply security group " + securityGroup.getName() + " to virtual machine " + userVm.getName());
                    event.setLevel(EventVO.LEVEL_ERROR);
                    _eventDao.persist(event);

                    throw new InternalErrorException("Failed to apply security group " + sgId + " to guest virtual machine " + vmId);
                }
            }

            txn.commit();
        } catch (Throwable e) {
            txn.rollback();
            if (e instanceof NetworkRuleConflictException) {
                throw (NetworkRuleConflictException) e;
            } else if (e instanceof InvalidParameterValueException) {
                throw (InvalidParameterValueException) e;
            } else if (e instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e;
            } else if (e instanceof InternalErrorException) {
                s_logger.warn("ManagementServer error", e);
                throw (InternalErrorException) e;
            }
            s_logger.warn("ManagementServer error", e);
        } finally {
            if (locked) {
                _publicIpAddressDao.release(publicIp);
            }
        }
    }

    @Override
    public long assignSecurityGroupAsync(Long userId, Long securityGroupId, List<Long> securityGroupIdList, String publicIp, Long vmId) {
        SecurityGroupParam param = new SecurityGroupParam(userId, securityGroupId, securityGroupIdList, publicIp, vmId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("AssignSecurityGroup");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    @DB
    public void removeSecurityGroup(long userId, long securityGroupId, String publicIp, long vmId) throws InvalidParameterValueException, PermissionDeniedException {
        // This gets complicated with overlapping rules. As an example:
        // security group 1 has the following port mappings: 22->22 on TCP,
        // 23->23 on TCP, 80->8080 on TCP
        // security group 2 has the following port mappings: 22->22 on TCP,
        // 7891->7891 on TCP
        // User assigns group 1 & 2 on 192.168.10.120 to vm 1
        // Later, user removed group 1 from 192.168.10.120 and vm 1
        // Final valid port mappings should be 22->22 and 7891->7891 which both
        // come from security group 2. The mapping
        // for port 22 should not be removed.

        boolean locked = false;
        UserVm userVm = _userVmDao.findById(vmId);
        if (userVm == null) {
            throw new InvalidParameterValueException("Unable to find vm: " + vmId);
        }

        SecurityGroupVO securityGroup = _securityGroupDao.findById(Long.valueOf(securityGroupId));
        if (securityGroup == null) {
            throw new InvalidParameterValueException("Unable to find security group: " + securityGroupId);
        }

        DomainRouterVO router = _routerDao.findById(userVm.getDomainRouterId());
        if ((router == null) || (router.getHostId() == null)) {
            throw new InvalidParameterValueException("Unable to find router for ip address: " + publicIp);
        }

        IPAddressVO ipVO = _publicIpAddressDao.acquire(publicIp);
        if (ipVO == null || ipVO.getAllocated() == null) {
            // throw this exception because hackers can use the api to probe
            // for allocated ips
            throw new PermissionDeniedException("User does not own supplied address");
        }

        locked = true;
        if ((ipVO.getAccountId() == null) || (ipVO.getAccountId().longValue() != userVm.getAccountId())) {
            throw new PermissionDeniedException("User/account does not own supplied address");
        }

        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            {
                // - send one command to agent to remove *all* rules for
                // publicIp/vm combo
                // - add back all rules based on existing SG mappings
                List<FirewallRuleVO> fwRulesToRemove = _firewallRulesDao.listForwardingByPubAndPrivIp(true, publicIp, userVm.getGuestIpAddress());
                for (FirewallRuleVO fwRule : fwRulesToRemove) {
                    fwRule.setEnabled(false);
                }

                List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(null, fwRulesToRemove, router);

                // Save and create the event
                String description;
                String type = EventTypes.EVENT_NET_RULE_DELETE;
                String ruleName = "ip forwarding";
                String level = EventVO.LEVEL_INFO;
                Account account = _accountDao.findById(userVm.getAccountId());

                for (FirewallRuleVO fwRule : updatedRules) {
                    _firewallRulesDao.remove(fwRule.getId());

                    description = "deleted " + ruleName + " rule [" + fwRule.getPublicIpAddress() + ":" + fwRule.getPublicPort() + "]->[" + fwRule.getPrivateIpAddress() + ":"
                            + fwRule.getPrivatePort() + "]" + " " + fwRule.getProtocol();

                    saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                }
            }

            // since we know these groups all pass muster, just keep track
            // of the public ports we are mapping on this public IP and
            // don't duplicate
            List<String> alreadyMappedPorts = new ArrayList<String>();
            List<FirewallRuleVO> fwRulesToAdd = new ArrayList<FirewallRuleVO>();
            List<SecurityGroupVMMapVO> sgVmMappings = _securityGroupVMMapDao.listByIpAndInstanceId(publicIp, vmId);
            for (SecurityGroupVMMapVO sgVmMapping : sgVmMappings) {
                if (sgVmMapping.getSecurityGroupId() == securityGroupId) {
                    _securityGroupVMMapDao.remove(sgVmMapping.getId());
                } else {
                    List<NetworkRuleConfigVO> netRules = _networkRuleConfigDao.listBySecurityGroupId(sgVmMapping.getSecurityGroupId());
                    for (NetworkRuleConfigVO netRule : netRules) {
                        if (!alreadyMappedPorts.contains(netRule.getPublicPort())) {
                            FirewallRuleVO newFwRule = new FirewallRuleVO();
                            newFwRule.setEnabled(true);
                            newFwRule.setForwarding(true);
                            newFwRule.setPrivatePort(netRule.getPrivatePort());
                            newFwRule.setProtocol(netRule.getProtocol());
                            newFwRule.setPublicPort(netRule.getPublicPort());
                            newFwRule.setPublicIpAddress(publicIp);
                            newFwRule.setPrivateIpAddress(userVm.getGuestIpAddress());
                            newFwRule.setGroupId(netRule.getSecurityGroupId());

                            fwRulesToAdd.add(newFwRule);

                            alreadyMappedPorts.add(netRule.getPublicPort());
                        }
                    }
                }
            }

            List<FirewallRuleVO> addedRules = _networkMgr.updateFirewallRules(publicIp, fwRulesToAdd, router);

            // Save and create the event
            String description;
            String type = EventTypes.EVENT_NET_RULE_ADD;
            String ruleName = "ip forwarding";
            String level = EventVO.LEVEL_INFO;
            Account account = _accountDao.findById(userVm.getAccountId());

            if ((addedRules != null) && !addedRules.isEmpty()) {
                for (FirewallRuleVO addedRule : addedRules) {
                    _firewallRulesDao.persist(addedRule);

                    description = "created new " + ruleName + " rule [" + addedRule.getPublicIpAddress() + ":" + addedRule.getPublicPort() + "]->["
                            + addedRule.getPrivateIpAddress() + ":" + addedRule.getPrivatePort() + "]" + " " + addedRule.getProtocol();

                    saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                }
            }

            // save off an event for removing the security group
            EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(userVm.getAccountId());
            event.setType(EventTypes.EVENT_SECURITY_GROUP_REMOVE);
            event.setDescription("Successfully removed security group " + securityGroup.getName() + " from virtual machine " + userVm.getName());
            event.setLevel(EventVO.LEVEL_INFO);
            String params = "sgId="+securityGroup.getId()+"\nvmId="+vmId;
            event.setParameters(params);
            _eventDao.persist(event);
            txn.commit();
        } catch (Exception ex) {
            txn.rollback();
            throw new VmopsRuntimeException("Unhandled exception", ex);
        } finally {
            if (locked) {
                _publicIpAddressDao.release(publicIp);
            }
        }
    }

    @Override
    public long removeSecurityGroupAsync(Long userId, long securityGroupId, String publicIp, long vmId) {
        SecurityGroupParam param = new SecurityGroupParam(userId, securityGroupId, null, publicIp, vmId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RemoveSecurityGroup");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public Long validateSecurityGroupsAndInstance(List<Long> securityGroupIds, Long instanceId) {
        if ((securityGroupIds == null) || securityGroupIds.isEmpty() || (instanceId == null)) {
            return null;
        }

        List<SecurityGroupVO> securityGroups = new ArrayList<SecurityGroupVO>();
        for (Long securityGroupId : securityGroupIds) {
            if (securityGroupId.longValue() == 0) {
                continue;
            }
            SecurityGroupVO securityGroup = _securityGroupDao.findById(securityGroupId);
            if (securityGroup == null) {
                return null;
            }
            securityGroups.add(securityGroup);
        }

        UserVm userVm = _userVmDao.findById(instanceId);
        if (userVm == null) {
            return null;
        }

        long accountId = userVm.getAccountId();
        for (SecurityGroupVO securityGroup : securityGroups) {
            Long sgAccountId = securityGroup.getAccountId();
            if ((sgAccountId != null) && (sgAccountId.longValue() != accountId)) {
                return null;
            }
        }
        return Long.valueOf(accountId);
    }

    @DB
    protected NetworkRuleConfigVO createNetworkRuleConfig(long userId, long securityGroupId, String port, String privatePort, String protocol, String algorithm)
            throws NetworkRuleConflictException {
        if (protocol == null) {
            protocol = "TCP";
        }

        Long ruleId = null;
        Transaction txn = Transaction.currentTxn();
        try {
            List<NetworkRuleConfigVO> existingRules = _networkRuleConfigDao.listBySecurityGroupId(securityGroupId);
            for (NetworkRuleConfigVO existingRule : existingRules) {
                if (existingRule.getPublicPort().equals(port) && existingRule.getProtocol().equals(protocol)) {
                    throw new NetworkRuleConflictException("port conflict, security group contains a rule on public port " + port + " for protocol " + protocol);
                }
            }

            txn.start();
            NetworkRuleConfigVO netRule = new NetworkRuleConfigVO(securityGroupId, port, privatePort, protocol);
            netRule.setCreateStatus(AsyncInstanceCreateStatus.Creating);
            ruleId = _networkRuleConfigDao.persist(netRule);
            txn.commit();

            // check if we are within context of async-execution
            AsyncJobExecutor asyncExecutor = BaseAsyncJobExecutor.getCurrentExecutor();
            if (asyncExecutor != null) {
                AsyncJobVO job = asyncExecutor.getJob();

                if (s_logger.isInfoEnabled())
                    s_logger.info("Created a new network rule instance " + ruleId + ", update async job-" + job.getId() + " progress status");

                _asyncMgr.updateAsyncJobAttachment(job.getId(), "network_rule_config", ruleId);
                _asyncMgr.updateAsyncJobStatus(job.getId(), BaseCmd.PROGRESS_INSTANCE_CREATED, ruleId);
            }

            txn.start();
            if (ruleId != null) {
                List<SecurityGroupVMMapVO> sgMappings = _securityGroupVMMapDao.listBySecurityGroup(securityGroupId);
                if ((sgMappings != null) && !sgMappings.isEmpty()) {
                    for (SecurityGroupVMMapVO sgMapping : sgMappings) {

                        // check for ip address/port conflicts by checking
                        // exising forwarding and loadbalancing rules
                        List<FirewallRuleVO> existingRulesOnPubIp = _firewallRulesDao.listIPForwarding(sgMapping.getIpAddress());
                        Map<String, Pair<String, String>> mappedPublicPorts = new HashMap<String, Pair<String, String>>();

                        if (existingRulesOnPubIp != null) {
                            for (FirewallRuleVO fwRule : existingRulesOnPubIp) {
                                mappedPublicPorts.put(fwRule.getPublicPort(), new Pair<String, String>(fwRule.getPrivateIpAddress(), fwRule.getPrivatePort()));
                            }
                        }

                        UserVm userVm = _userVmDao.findById(sgMapping.getInstanceId());
                        if (userVm != null) {
                            Pair<String, String> privateIpPort = mappedPublicPorts.get(netRule.getPublicPort());
                            if (privateIpPort != null) {
                                if (privateIpPort.first().equals(userVm.getGuestIpAddress()) && privateIpPort.second().equals(netRule.getPrivatePort())) {
                                    continue; // already mapped
                                } else {
                                    throw new NetworkRuleConflictException("An existing network rule for " + sgMapping.getIpAddress() + ":" + netRule.getPublicPort()
                                            + " already exists, found while trying to apply firewall rule " + netRule.getId() + " from security group "
                                            + sgMapping.getSecurityGroupId() + ".");
                                }
                            }

                            FirewallRuleVO newFwRule = new FirewallRuleVO();
                            newFwRule.setEnabled(true);
                            newFwRule.setForwarding(true);
                            newFwRule.setPrivatePort(netRule.getPrivatePort());
                            newFwRule.setProtocol(netRule.getProtocol());
                            newFwRule.setPublicPort(netRule.getPublicPort());
                            newFwRule.setPublicIpAddress(sgMapping.getIpAddress());
                            newFwRule.setPrivateIpAddress(userVm.getGuestIpAddress());
                            newFwRule.setGroupId(netRule.getSecurityGroupId());

                            boolean success = _networkMgr.updateFirewallRule(newFwRule, null, null);

                            // Save and create the event
                            String description;
                            String ruleName = "ip forwarding";
                            String level = EventVO.LEVEL_INFO;
                            Account account = _accountDao.findById(userVm.getAccountId());

                            if (success == true) {
                                _firewallRulesDao.persist(newFwRule);
                                description = "created new " + ruleName + " rule [" + newFwRule.getPublicIpAddress() + ":" + newFwRule.getPublicPort() + "]->["
                                        + newFwRule.getPrivateIpAddress() + ":" + newFwRule.getPrivatePort() + "]" + " " + newFwRule.getProtocol();
                            } else {
                                level = EventVO.LEVEL_ERROR;
                                description = "failed to create new " + ruleName + " rule [" + newFwRule.getPublicIpAddress() + ":" + newFwRule.getPublicPort() + "]->["
                                        + newFwRule.getPrivateIpAddress() + ":" + newFwRule.getPrivatePort() + "]" + " " + newFwRule.getProtocol();
                            }

                            saveEvent(Long.valueOf(userId), account.getId(), account.getDomainId(), level, EventTypes.EVENT_NET_RULE_ADD, description);
                        }
                    }
                }

                NetworkRuleConfigVO rule = _networkRuleConfigDao.findById(ruleId);
                rule.setCreateStatus(AsyncInstanceCreateStatus.Created);
                _networkRuleConfigDao.update(ruleId, rule);
            }

            txn.commit();
        } catch (Exception ex) {
            txn.rollback();

            txn.start();
            NetworkRuleConfigVO rule = _networkRuleConfigDao.findById(ruleId);
            rule.setCreateStatus(AsyncInstanceCreateStatus.Corrupted);
            _networkRuleConfigDao.update(ruleId, rule);
            txn.commit();

            if (ex instanceof NetworkRuleConflictException) {
                throw (NetworkRuleConflictException) ex;
            }
            s_logger.error("Unexpected exception creating network rule (sgId:" + securityGroupId + ",port:" + port + ",privatePort:" + privatePort + ",protocol:" + protocol + ")",
                    ex);
        }

        return _networkRuleConfigDao.findById(ruleId);
    }

    @Override
    public boolean deleteNetworkRuleConfig(long userId, long networkRuleId) {
        boolean allSuccessful = true;
        try {
            NetworkRuleConfigVO netRule = _networkRuleConfigDao.findById(networkRuleId);
            if (netRule != null) {
                List<SecurityGroupVMMapVO> sgMappings = _securityGroupVMMapDao.listBySecurityGroup(netRule.getSecurityGroupId());
                if ((sgMappings != null) && !sgMappings.isEmpty()) {
                    for (SecurityGroupVMMapVO sgMapping : sgMappings) {
                        UserVm userVm = _userVmDao.findById(sgMapping.getInstanceId());
                        if (userVm != null) {
                            List<FirewallRuleVO> fwRules = _firewallRulesDao.listIPForwarding(sgMapping.getIpAddress(), netRule.getPublicPort(), true);
                            FirewallRuleVO rule = null;
                            for (FirewallRuleVO fwRule : fwRules) {
                                if (fwRule.getPrivatePort().equals(netRule.getPrivatePort()) && fwRule.getPrivateIpAddress().equals(userVm.getGuestIpAddress())) {
                                    rule = fwRule;
                                    break;
                                }
                            }

                            if (rule != null) {
                                rule.setEnabled(false);
                                boolean success = _networkMgr.updateFirewallRule(rule, null, null);

                                // Save and create the event
                                String description;
                                String ruleName = "ip forwarding";
                                String level = EventVO.LEVEL_INFO;
                                Account account = _accountDao.findById(userVm.getAccountId());

                                if (success == true) {
                                    _firewallRulesDao.remove(rule.getId());
                                    description = "deleted " + ruleName + " rule [" + rule.getPublicIpAddress() + ":" + rule.getPublicPort() + "]->[" + rule.getPrivateIpAddress()
                                            + ":" + rule.getPrivatePort() + "]" + " " + rule.getProtocol();
                                } else {
                                    level = EventVO.LEVEL_ERROR;
                                    description = "failed to delete " + ruleName + " rule [" + rule.getPublicIpAddress() + ":" + rule.getPublicPort() + "]->["
                                            + rule.getPrivateIpAddress() + ":" + rule.getPrivatePort() + "]" + " " + rule.getProtocol();
                                }

                                saveEvent(Long.valueOf(userId), account.getId(), account.getDomainId(), level, EventTypes.EVENT_NET_RULE_DELETE, description);

                                allSuccessful = allSuccessful && success;
                            }
                        }
                    }
                }
                allSuccessful = allSuccessful && _networkRuleConfigDao.remove(netRule.getId());
            }
        } catch (Exception ex) {
            s_logger.error("Unexpected exception deleting network rule " + networkRuleId, ex);
        }

        return allSuccessful;
    }

    @Override
    public long deleteNetworkRuleConfigAsync(long userId, Account account, Long networkRuleId) throws PermissionDeniedException {
        // do a quick permissions check to make sure the account is either an
        // admin or the owner of the security group to which the network rule
        // belongs
        NetworkRuleConfigVO netRule = _networkRuleConfigDao.findById(networkRuleId);
        if (netRule != null) {
            SecurityGroupVO sg = _securityGroupDao.findById(netRule.getSecurityGroupId());
            if (account != null) {
                if (!BaseCmd.isAdmin(account.getType())) {
                    if ((sg.getAccountId() == null) || (sg.getAccountId().longValue() != account.getId().longValue())) {
                        throw new PermissionDeniedException("Unable to delete network rule " + networkRuleId + "; account: " + account.getAccountName() + " is not the owner");
                    }
                } else if (!isChildDomain(account.getDomainId(), sg.getDomainId())) {
                    throw new PermissionDeniedException("Unable to delete network rule " + networkRuleId + "; account: " + account.getAccountName() + " is not an admin in the domain hierarchy.");
                }
            }
        }

        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteNetworkRuleConfig");
        job.setCmdInfo(gson.toJson(networkRuleId));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @DB
    protected boolean deleteIpForwardingRule(long userId, long accountId, String publicIp, String publicPort, String privateIp, String privatePort, String proto)
            throws PermissionDeniedException, InvalidParameterValueException, InternalErrorException {

        Transaction txn = Transaction.currentTxn();
        boolean locked = false;
        try {
            AccountVO accountVO = _accountDao.findById(accountId);
            if (accountVO == null) {
                // throw this exception because hackers can use the api to probe
                // for existing user ids
                throw new PermissionDeniedException("Account does not own supplied address");
            }
            // although we are not writing these values to the DB, we will check
            // them out of an abundance
            // of caution (may not be warranted)
            if (!NetUtils.isValidPort(publicPort) || !NetUtils.isValidPort(privatePort)) {
                throw new InvalidParameterValueException("Invalid value for port");
            }
            if (!NetUtils.isValidPrivateIp(privateIp, _configs.get("guest.ip.network"))) {
                throw new InvalidParameterValueException("Invalid private ip address");
            }
            if (!NetUtils.isValidProto(proto)) {
                throw new InvalidParameterValueException("Invalid protocol");
            }
            IPAddressVO ipVO = _publicIpAddressDao.acquire(publicIp);
            if (ipVO == null || ipVO.getAllocated() == null) {
                // throw this exception because hackers can use the api to probe
                // for allocated ips
                throw new PermissionDeniedException("User does not own supplied address");
            }

            locked = true;
            if ((ipVO.getAccountId() == null) || (ipVO.getAccountId().longValue() != accountId)) {
                // FIXME: if admin account, make sure the user is visible in the
                // admin's domain, or has that checking been done by this point?
                if (!BaseCmd.isAdmin(accountVO.getType())) {
                    throw new PermissionDeniedException("User/account does not own supplied address");
                }
            }

            txn.start();

            List<FirewallRuleVO> fwdings = _firewallRulesDao.listIPForwardingForUpdate(publicIp, publicPort, proto);
            FirewallRuleVO fwRule = null;
            if (fwdings.size() == 0) {
                throw new InvalidParameterValueException("No such rule");
            } else if (fwdings.size() == 1) {
                fwRule = fwdings.get(0);
                if (fwRule.getPrivateIpAddress().equalsIgnoreCase(privateIp) && fwRule.getPrivatePort().equals(privatePort)) {
                    _firewallRulesDao.delete(fwRule.getId());
                } else {
                    throw new InvalidParameterValueException("No such rule");
                }
            } else {
                throw new InternalErrorException("Multiple matches. Please contact support");
            }
            fwRule.setEnabled(false);
            boolean success = _networkMgr.updateFirewallRule(fwRule, null, null);
            if (!success) {
                throw new InternalErrorException("Failed to update router");
            }
            txn.commit();
            return success;
        } catch (Throwable e) {
            if (e instanceof InvalidParameterValueException) {
                throw (InvalidParameterValueException) e;
            } else if (e instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e;
            } else if (e instanceof InternalErrorException) {
                s_logger.warn("ManagementServer error", e);
                throw (InternalErrorException) e;
            }
            s_logger.warn("ManagementServer error", e);
        } finally {
            if (locked) {
                _publicIpAddressDao.release(publicIp);
            }
        }
        return false;
    }

    @DB
    private boolean deleteLoadBalancingRule(long userId, long accountId, String publicIp, String publicPort, String privateIp, String privatePort, String algo)
            throws PermissionDeniedException, InvalidParameterValueException, InternalErrorException {
        Transaction txn = Transaction.currentTxn();
        boolean locked = false;
        try {
            AccountVO accountVO = _accountDao.findById(accountId);
            if (accountVO == null) {
                // throw this exception because hackers can use the api to probe
                // for existing user ids
                throw new PermissionDeniedException("Account does not own supplied address");
            }
            // although we are not writing these values to the DB, we will check
            // them out of an abundance
            // of caution (may not be warranted)
            if (!NetUtils.isValidPort(publicPort) || !NetUtils.isValidPort(privatePort)) {
                throw new InvalidParameterValueException("Invalid value for port");
            }
            if (!NetUtils.isValidPrivateIp(privateIp, _configs.get("guest.ip.network"))) {
                throw new InvalidParameterValueException("Invalid private ip address");
            }
            if (!NetUtils.isValidAlgorithm(algo)) {
                throw new InvalidParameterValueException("Invalid protocol");
            }

            IPAddressVO ipVO = _publicIpAddressDao.acquire(publicIp);

            if (ipVO == null || ipVO.getAllocated() == null) {
                // throw this exception because hackers can use the api to probe
                // for allocated ips
                throw new PermissionDeniedException("User does not own supplied address");
            }

            locked = true;
            if ((ipVO.getAccountId() == null) || (ipVO.getAccountId().longValue() != accountId)) {
                // FIXME: the user visible from the admin account's domain? has
                // that check been done already?
                if (!BaseCmd.isAdmin(accountVO.getType())) {
                    throw new PermissionDeniedException("User does not own supplied address");
                }
            }

            List<FirewallRuleVO> fwdings = _firewallRulesDao.listLoadBalanceRulesForUpdate(publicIp, publicPort, algo);
            FirewallRuleVO fwRule = null;
            if (fwdings.size() == 0) {
                throw new InvalidParameterValueException("No such rule");
            }
            for (FirewallRuleVO frv : fwdings) {
                if (frv.getPrivateIpAddress().equalsIgnoreCase(privateIp) && frv.getPrivatePort().equals(privatePort)) {
                    fwRule = frv;
                    break;
                }
            }

            if (fwRule == null) {
                throw new InvalidParameterValueException("No such rule");
            }

            txn.start();

            fwRule.setEnabled(false);
            _firewallRulesDao.update(fwRule.getId(), fwRule);

            boolean success = _networkMgr.updateFirewallRule(fwRule, null, null);
            if (!success) {
                throw new InternalErrorException("Failed to update router");
            }
            _firewallRulesDao.delete(fwRule.getId());

            txn.commit();
            return success;
        } catch (Throwable e) {
            if (e instanceof InvalidParameterValueException) {
                throw (InvalidParameterValueException) e;
            } else if (e instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e;
            } else if (e instanceof InternalErrorException) {
                s_logger.warn("ManagementServer error", e);
                throw (InternalErrorException) e;
            }
            s_logger.warn("ManagementServer error", e);
        } finally {
            if (locked) {
                _publicIpAddressDao.release(publicIp);
            }
        }
        return false;
    }

    @Override
    public List<EventVO> getEvents(long userId, long accountId, Long domainId, String type, String level, Date startDate, Date endDate) {
        SearchCriteria sc = _eventDao.createSearchCriteria();
        if (userId > 0) {
            sc.addAnd("userId", SearchCriteria.Op.EQ, userId);
        }
        if (accountId > 0) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        }
        if (domainId != null) {
            sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
        }
        if (type != null) {
            sc.addAnd("type", SearchCriteria.Op.EQ, type);
        }
        if (level != null) {
            sc.addAnd("level", SearchCriteria.Op.EQ, level);
        }
        if (startDate != null && endDate != null) {
            startDate = massageDate(startDate, 0, 0, 0);
            endDate = massageDate(endDate, 23, 59, 59);
            sc.addAnd("createDate", SearchCriteria.Op.BETWEEN, startDate, endDate);
        } else if (startDate != null) {
            startDate = massageDate(startDate, 0, 0, 0);
            sc.addAnd("createDate", SearchCriteria.Op.GTEQ, startDate);
        } else if (endDate != null) {
            endDate = massageDate(endDate, 23, 59, 59);
            sc.addAnd("createDate", SearchCriteria.Op.LTEQ, endDate);
        }

        return _eventDao.search(sc, null);
    }

    private Date massageDate(Date date, int hourOfDay, int minute, int second) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, hourOfDay);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, second);
        return cal.getTime();
    }

    @Override
    public List<UserAccountVO> searchForUsers(Criteria c) {
        Filter searchFilter = new Filter(UserAccountVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object id = c.getCriteria(Criteria.ID);
        Object username = c.getCriteria(Criteria.USERNAME);
        Object type = c.getCriteria(Criteria.TYPE);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object account = c.getCriteria(Criteria.ACCOUNTNAME);
        Object state = c.getCriteria(Criteria.STATE);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<UserAccountVO> sb = _userAccountDao.createSearchBuilder();
        sb.and("username", sb.entity().getUsername(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);
        sb.and("domainId", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        sb.and("accountName", sb.entity().getAccountName(), SearchCriteria.Op.LIKE);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);

        if ((account == null) && (domainId != null)) {
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("username", "%" + keyword + "%");
        } else if (username != null) {
            sc.setParameters("username", "%" + username + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (type != null) {
            sc.setParameters("type", type);
        }

        if (account != null) {
            sc.setParameters("accountName", "%" + account + "%");
            if (domainId != null) {
                sc.setParameters("domainId", domainId);
            }
        } else if (domainId != null) {
            DomainVO domainVO = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domainVO.getPath() + "%");
        }

        if (state != null) {
            sc.setParameters("state", state);
        }

        return _userAccountDao.search(sc, searchFilter);
    }

    @Override
    public List<ServiceOfferingVO> searchForServiceOfferings(Criteria c) {
        Filter searchFilter = new Filter(ServiceOfferingVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _offeringsDao.createSearchCriteria();

        Object name = c.getCriteria(Criteria.NAME);
        Object vmIdObj = c.getCriteria(Criteria.INSTANCEID);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }

        if (vmIdObj != null) {
            UserVmVO vm = _userVmDao.findById((Long) vmIdObj);
            if (vm != null) {
                ServiceOfferingVO offering = _offeringsDao.findById(Long.valueOf(vm.getServiceOfferingId()));
                sc.addAnd("id", SearchCriteria.Op.NEQ, offering.getId());
            }
        }

        return _offeringsDao.search(sc, searchFilter);
    }

    @Override
    public List<HostVO> searchForServers(Criteria c) {
        Filter searchFilter = new Filter(HostVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _hostDao.createSearchCriteria();

        Object name = c.getCriteria(Criteria.NAME);
        Object type = c.getCriteria(Criteria.TYPE);
        Object state = c.getCriteria(Criteria.STATE);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object pod = c.getCriteria(Criteria.PODID);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }
        if (type != null) {
            sc.addAnd("type", SearchCriteria.Op.EQ, type);
        }
        if (state != null) {
            sc.addAnd("status", SearchCriteria.Op.EQ, state);
        }
        if (zone != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zone);
        }
        if (pod != null) {
            sc.addAnd("podId", SearchCriteria.Op.EQ, pod);
        }

        return _hostDao.search(sc, searchFilter);
    }

    @Override
    public List<HostPodVO> searchForPods(Criteria c) {
        Filter searchFilter = new Filter(HostPodVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _hostPodDao.createSearchCriteria();

        String podName = (String) c.getCriteria(Criteria.NAME);
        Long id = (Long) c.getCriteria(Criteria.ID);
        Long zoneId = (Long) c.getCriteria(Criteria.DATACENTERID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (podName != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + podName + "%");
        }

        if (zoneId != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zoneId);
        }

        return _hostPodDao.search(sc, searchFilter);
    }

    @Override
    public List<DataCenterVO> searchForZones(Criteria c) {
        Long dataCenterId = (Long) c.getCriteria(Criteria.DATACENTERID);

        if (dataCenterId != null) {
            DataCenterVO dc = _dcDao.findById(dataCenterId);
            List<DataCenterVO> datacenters = new ArrayList<DataCenterVO>();
            datacenters.add(dc);
            return datacenters;
        }

        Filter searchFilter = new Filter(DataCenterVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _dcDao.createSearchCriteria();

        String zoneName = (String) c.getCriteria(Criteria.ZONENAME);

        if (zoneName != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + zoneName + "%");
        }

        return _dcDao.search(sc, searchFilter);

    }

    @Override
    public List<VlanVO> searchForVlans(Criteria c) {
        Filter searchFilter = new Filter(VlanVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _vlanDao.createSearchCriteria();

        Object id = c.getCriteria(Criteria.ID);
        Object vlan = c.getCriteria(Criteria.VLAN);
        Object name = c.getCriteria(Criteria.NAME);
        Object zoneId = c.getCriteria(Criteria.DATACENTERID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("vlanName", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (zoneId != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zoneId);
        }

        if (name != null) {
            sc.addAnd("vlanName", SearchCriteria.Op.EQ, name);
        }

        if (vlan != null) {
            sc.addAnd("vlanId", SearchCriteria.Op.EQ, vlan);
        }

        return _vlanDao.search(sc, searchFilter);
    }

    @Override
    public List<ConfigurationVO> searchForConfigurations(Criteria c) {
        Filter searchFilter = new Filter(ConfigurationVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _configDao.createSearchCriteria();

        Object name = c.getCriteria(Criteria.NAME);
        Object category = c.getCriteria(Criteria.CATEGORY);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }

        if (category != null) {
            sc.addAnd("category", SearchCriteria.Op.EQ, category);
        }

        sc.addAnd("category", SearchCriteria.Op.NEQ, "Hidden");

        return _configDao.search(sc, searchFilter);
    }

    @Override
    public List<HostVO> searchForAlertServers(Criteria c) {
        Filter searchFilter = new Filter(HostVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _hostDao.createSearchCriteria();

        Object[] states = (Object[]) c.getCriteria(Criteria.STATE);

        if (states != null) {
            sc.addAnd("status", SearchCriteria.Op.IN, states);
        }

        return _hostDao.search(sc, searchFilter);
    }

    @Override
    public List<VMTemplateVO> searchForTemplates(Criteria c) {
        Filter searchFilter = new Filter(VMTemplateVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object name = c.getCriteria(Criteria.NAME);
        Object isReady = c.getCriteria(Criteria.READY);
        Object isPublic = c.getCriteria(Criteria.ISPUBLIC);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);
        Long creator = (Long) c.getCriteria(Criteria.CREATED_BY);

        SearchBuilder<VMTemplateVO> sb = _templateDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("ready", sb.entity().isReady(),  SearchCriteria.Op.EQ);
        sb.and("publicTemplate", sb.entity().isPublicTemplate(), SearchCriteria.Op.EQ);
        sb.and("format", sb.entity().getFormat(), SearchCriteria.Op.NEQ);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        
        SearchCriteria sc = sb.create();
        
        if (keyword != null) {
            sc.setParameters("name", "%" + keyword + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        }
        if (name != null) {
            sc.setParameters("name", "%" + name + "%");
        }
        if (isReady != null) {
            sc.setParameters("ready", isReady);
        }
        if (isPublic != null) {
            sc.setParameters("publicTemplate", isPublic);
        }
        if (creator != null) {
            sc.setParameters("accountId", creator);
        }

        sc.setParameters("format", ImageFormat.ISO);

        return _templateDao.search(sc, searchFilter);
    }

    @Override
    public List<VMTemplateVO> listTemplates(String name, String keyword, TemplateFilter templateFilter, boolean isIso, Boolean bootable, Long accountId, Integer pageSize, Long startIndex) {
        Account account = null;
        if (accountId != null) {
        	account = _accountDao.findById(accountId);
        }
        
    	return _templateDao.searchTemplates(name, keyword, templateFilter, isIso, bootable, account, pageSize, startIndex);
    }

    @Override
    public List<VMTemplateVO> listPermittedTemplates(long accountId) {
        return _launchPermissionDao.listPermittedTemplates(accountId);
    }

    @Override
    public List<VMTemplateHostVO> listTemplateHostBy(long templateId) {
        return _templateHostDao.listByTemplateId(templateId);
    }

    @Override
    public List<HostPodVO> listPods(long dataCenterId) {
        return _hostPodDao.listByDataCenterId(dataCenterId);
    }

    @Override
    public PricingVO findPricingByTypeAndId(String type, Long id) {
        return _pricingDao.findByTypeAndId(type, id);
    }

    @Override
    public Long createServiceOffering(Long id, String name, int cpu, int ramSize, int speed, String displayText, boolean localStorageRequired) {
        return _configMgr.createServiceOffering(id, name, cpu, ramSize, speed, displayText, localStorageRequired);
    }

    @Override
    public Long createPricing(Long id, float price, String priceUnit, String type, Long typeId, Date created) {
        PricingVO pricing = new PricingVO(id, price, priceUnit, type, typeId, created);
        return _pricingDao.persist(pricing);
    }

    @Override
    public void updateConfiguration(String name, String value) throws InvalidParameterValueException, InternalErrorException {
        _configMgr.updateConfiguration(name, value);
    }

    @Override
    public void updateServiceOffering(Long id, String name, int cpu, int ramSize, int speed, String displayText) {
        // ServiceOfferingVO offering = new ServiceOfferingVO(id, name, cpu,
        // ramSize, speed, diskSpace, false, displayText);
        ServiceOfferingVO offering = _offeringsDao.createForUpdate(id);
        offering.setName(name);
        offering.setCpu(cpu);
        offering.setRamSize(ramSize);
        offering.setSpeed(speed);
        offering.setDisplayText(displayText);
        _offeringsDao.update(id, offering);
    }

    private void updatePricing(Long id, float price, String priceUnit, String type, Long typeId, Date created) {
        PricingVO pricing = new PricingVO(id, price, priceUnit, type, typeId, created);
        _pricingDao.update(pricing);
    }

    @Override
    public void deleteServiceOffering(long offeringId) {
        _offeringsDao.remove(offeringId);
    }

    @Override
    public HostPodVO createPod(String podName, Long zoneId, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException {
        return _configMgr.createPod(podName, zoneId, cidr, startIp, endIp);
    }

    @Override
    public HostPodVO editPod(long podId, String newPodName, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException {
        return _configMgr.editPod(podId, newPodName, cidr, startIp, endIp);
    }

    @Override
    public void deletePod(long podId) throws InvalidParameterValueException, InternalErrorException {
        _configMgr.deletePod(podId);
    }

    @Override
    public DataCenterVO createZone(String zoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange) throws InvalidParameterValueException, InternalErrorException {
        return _configMgr.createZone(zoneName, dns1, dns2, dns3, dns4, vnetRange);
    }

    @Override
    public DataCenterVO editZone(Long zoneId, String newZoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange) throws InvalidParameterValueException, InternalErrorException {
        return _configMgr.editZone(zoneId, newZoneName, dns1, dns2, dns3, dns4, vnetRange);
    }

    @Override
    public void deleteZone(Long zoneId) throws InvalidParameterValueException, InternalErrorException {
        _configMgr.deleteZone(zoneId);
    }

    @Override
    public String changePublicIPRange(boolean add, Long vlanDbId, String startIP, String endIP) throws InvalidParameterValueException {
        return _configMgr.changePublicIPRange(add, vlanDbId, startIP, endIP);
    }

    @Override
    public String changePrivateIPRange(boolean add, Long podId, String startIP, String endIP) throws InvalidParameterValueException {
        return _configMgr.changePrivateIPRange(add, podId, startIP, endIP);
    }

    private List<UserVO> findUsersLike(String username) {
        return _userDao.findUsersLike(username);
    }

    @Override
    public User findUserById(Long userId) {
        return _userDao.findById(userId);
    }

    @Override
    public List<AccountVO> findAccountsLike(String accountName) {
        return _accountDao.findAccountsLike(accountName);
    }

    @Override
    public Account findActiveAccountByName(String accountName) {
        return _accountDao.findActiveAccountByName(accountName);
    }

    @Override
    public Account findActiveAccount(String accountName, Long domainId) {
        if (domainId == null) {
            domainId = DomainVO.ROOT_DOMAIN;
        }
        return _accountDao.findActiveAccount(accountName, domainId);
    }

    @Override
    public Account findAccountByName(String accountName, Long domainId) {
        if (domainId == null)
            domainId = DomainVO.ROOT_DOMAIN;
        return _accountDao.findAccount(accountName, domainId);
    }

    @Override
    public Account findAccountById(Long accountId) {
        return _accountDao.findById(accountId);
    }

    @Override
    public GuestOS findGuestOSById(Long id) {
        return this._guestOSDao.findById(id);
    }

    @Override
    public List<AccountVO> searchForAccounts(Criteria c) {
        Filter searchFilter = new Filter(AccountVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object id = c.getCriteria(Criteria.ID);
        Object accountname = c.getCriteria(Criteria.ACCOUNTNAME);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object type = c.getCriteria(Criteria.TYPE);
        Object state = c.getCriteria(Criteria.STATE);
        Object isCleanupRequired = c.getCriteria(Criteria.ISCLEANUPREQUIRED);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<AccountVO> sb = _accountDao.createSearchBuilder();
        sb.and("accountName", sb.entity().getAccountName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);
        sb.and("needsCleanup", sb.entity().getNeedsCleanup(), SearchCriteria.Op.EQ);

        if ((id == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("accountName", "%" + keyword + "%");
        } else if (accountname != null) {
            sc.setParameters("accountName", "%" + accountname + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);

            // I want to join on user_vm.domain_id = domain.id where domain.path like 'foo%'
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        if (type != null) {
            sc.setParameters("type", type);
        }

        if (state != null) {
            sc.setParameters("state", state);
        }

        if (isCleanupRequired != null) {
            sc.setParameters("needsCleanup", isCleanupRequired);
        }

        return _accountDao.search(sc, searchFilter);
    }

    @Override
    public Account findAccountByIpAddress(String ipAddress) {
        IPAddressVO address = _publicIpAddressDao.findById(ipAddress);
        if ((address != null) && (address.getAccountId() != null)) {
            return _accountDao.findById(address.getAccountId());
        }
        return null;
    }

    @Override
    public ResourceLimitVO updateResourceLimit(Long domainId, Long accountId, ResourceType type, Long max) throws InvalidParameterValueException {
        return _accountMgr.updateResourceLimit(domainId, accountId, type, max);
    }

    @Override
    public boolean deleteLimit(Long limitId) {
        // A limit ID must be passed in
        if (limitId == null)
            return false;

        return _resourceLimitDao.delete(limitId);
    }

    @Override
    public ResourceLimitVO findLimitById(long limitId) {
        return _resourceLimitDao.findById(limitId);
    }

    @Override
    public List<ResourceLimitVO> searchForLimits(Criteria c) {
        Filter searchFilter = new Filter(ResourceLimitVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Long domainId = (Long) c.getCriteria(Criteria.DOMAINID);
        Long accountId = (Long) c.getCriteria(Criteria.ACCOUNTID);
        ResourceType type = (ResourceType) c.getCriteria(Criteria.TYPE);

        // For 2.0, we are just limiting the scope to having an user retrieve
        // limits for himself and if limits don't exist, use the ROOT domain's limits.
        // - Will
        List<ResourceLimitVO> limits = new ArrayList<ResourceLimitVO>();
        if (accountId != null) {
        	SearchBuilder<ResourceLimitVO> sb = _resourceLimitDao.createSearchBuilder();
        	sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        	sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);

        	SearchCriteria sc = sb.create();

        	if (accountId != null) {
        		sc.setParameters("accountId", accountId);
        	} 

        	if (type != null) {
        		sc.setParameters("type", type);
        	}
        	
        	// Listing all limits for an account
        	if (type == null) {
        		List<ResourceLimitVO> userLimits = _resourceLimitDao.search(sc, searchFilter);
	        	List<ResourceLimitVO> rootLimits = _resourceLimitDao.listByDomainId(DomainVO.ROOT_DOMAIN);
	        	ResourceType resourceTypes[] = ResourceType.values();
        	
	        	for (ResourceType resourceType: resourceTypes) {
	        		boolean found = false;
	        		for (ResourceLimitVO userLimit : userLimits) {
	        			if (userLimit.getType() == resourceType) {
	        				limits.add(userLimit);
	        				found = true;
	        				break;
	        			}
	        		}
	        		if (!found) {
	        			// Check the ROOT domain
	        			for (ResourceLimitVO rootLimit : rootLimits) {
	        				if (rootLimit.getType() == resourceType) {
	        					limits.add(rootLimit);
	        					found = true;
	        					break;
	        				}
	        			}
	        		}
	        		if (!found) {
	        			limits.add(new ResourceLimitVO(domainId, accountId, resourceType, -1L));
	        		}
	        	}
        	} else {
        		AccountVO account = _accountDao.findById(accountId);
        		limits.add(new ResourceLimitVO(null, accountId, type, _accountMgr.findCorrectResourceLimit(account, type)));
        	}
        } else if (domainId != null) {
        	if (type == null) {
        		ResourceType resourceTypes[] = ResourceType.values();
        		List<ResourceLimitVO> domainLimits = _resourceLimitDao.listByDomainId(domainId);
        		for (ResourceType resourceType: resourceTypes) {
	        		boolean found = false;
	        		for (ResourceLimitVO domainLimit : domainLimits) {
	        			if (domainLimit.getType() == resourceType) {
	        				limits.add(domainLimit);
	        				found = true;
	        				break;
	        			}
	        		}
	        		if (!found) {
	        			limits.add(new ResourceLimitVO(domainId, null, resourceType, -1L));
	        		}
        		}
        	} else {
        		limits.add(_resourceLimitDao.findByDomainIdAndType(domainId, type));
        	}
        } 
        return limits;
    }

    @Override
    public long findCorrectResourceLimit(ResourceType type, long accountId) {
        AccountVO account = _accountDao.findById(accountId);
        
        if (account == null) {
            return -1;
        }
        
        return _accountMgr.findCorrectResourceLimit(account, type);
    }
    
    @Override
    public long getResourceCount(ResourceType type, long accountId) {
    	AccountVO account = _accountDao.findById(accountId);
        
        if (account == null) {
            return -1;
        }
        
        return _accountMgr.getResourceCount(account, type);
    }

    @Override
    public List<VMTemplateVO> listIsos(Criteria c) {
        Filter searchFilter = new Filter(VMTemplateVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        Boolean ready = (Boolean) c.getCriteria(Criteria.READY);
        Boolean isPublic = (Boolean) c.getCriteria(Criteria.ISPUBLIC);
        Long creator = (Long) c.getCriteria(Criteria.CREATED_BY);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchCriteria sc = _templateDao.createSearchCriteria();

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (creator != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, creator);
        }
        if (ready != null) {
            sc.addAnd("ready", SearchCriteria.Op.EQ, ready);
        }
        if (isPublic != null) {
            sc.addAnd("publicTemplate", SearchCriteria.Op.EQ, isPublic);
        }

        sc.addAnd("format", SearchCriteria.Op.EQ, ImageFormat.ISO);

        return _templateDao.search(sc, searchFilter);
    }

    @Override
    public List<UserStatisticsVO> listUserStatsBy(Long accountId) {
        return _userStatsDao.listBy(accountId);
    }

    @Override
    public List<VMInstanceVO> findVMInstancesLike(String vmInstanceName) {
        return _vmInstanceDao.findVMInstancesLike(vmInstanceName);
    }

    @Override
    public VMInstanceVO findVMInstanceById(long vmId) {
        return _vmInstanceDao.findById(vmId);
    }

    @Override
    public UserVmVO findUserVMInstanceById(long userVmId) {
        return _userVmDao.findById(userVmId);
    }

    @Override
    public ServiceOfferingVO findServiceOfferingById(long offeringId) {
        return _offeringsDao.findById(offeringId);
    }

    @Override
    public List<ServiceOfferingVO> listAllServiceOfferings() {
        return _offeringsDao.listAll();
    }

    @Override
    public List<HostVO> listAllActiveHosts() {
        return _hostDao.listAllActive();
    }

    @Override
    public DataCenterVO findDataCenterById(long dataCenterId) {
        return _dcDao.findById(dataCenterId);
    }

    @Override
    public VlanVO findVlanById(long vlanDbId) {
        return _vlanDao.findById(vlanDbId);
    }

    @Override
    public Long createTemplate(long userId, String displayText, boolean isPublic, boolean featured, String format, String diskType, String url, String chksum, boolean requiresHvm, int bits, boolean enablePassword, long guestOSId, boolean bootable) throws IllegalArgumentException, ResourceAllocationException {
        try {
            
            ImageFormat imgfmt = ImageFormat.valueOf(format.toUpperCase());
            if (imgfmt == null) {
                throw new IllegalArgumentException("Image format is incorrect " + format + ". Supported formats are " + EnumUtils.listValues(ImageFormat.values()));
            }
            
            FileSystem fileSystem = FileSystem.valueOf(diskType);
            if (fileSystem == null) {
                throw new IllegalArgumentException("File system is incorrect " + diskType + ". Supported file systems are " + EnumUtils.listValues(FileSystem.values()));
            }
            
            URI uri = new URI(url);
            if (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https") && !uri.getScheme().equalsIgnoreCase("file")) {
               throw new IllegalArgumentException("Unsupported scheme for url: " + url);
            }
            
            // Check that the resource limit for templates/ISOs won't be exceeded
        	AccountVO account = _accountDao.findById(userId);
            if (_accountMgr.resourceLimitExceeded(account, ResourceType.template)) {
            	ResourceAllocationException rae = new ResourceAllocationException("Maximum number of templates and ISOs for account: " + account.getAccountName() + " has been exceeded.");
            	rae.setResourceType("template");
            	throw rae;
            }
            
            return _tmpltMgr.create(userId, displayText, isPublic, featured, imgfmt, fileSystem, uri, chksum, requiresHvm, bits, enablePassword, guestOSId, bootable);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL " + url);
        }
    }

    @Override
    public void updateTemplate(Long id, String name, String displayText) {
        try {
            VMTemplateVO template = _templateDao.createForUpdate(id);
            template.setName(name);
            template.setDisplayText(displayText);
            _templateDao.update(id, template);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean deleteTemplate(long userId, long templateId, Long zoneId) throws InternalErrorException {
    	VMTemplateVO template = _templateDao.findById(templateId);
    
    	boolean success = _tmpltMgr.delete(templateId, zoneId);
    	
    	// Log an event
    	
		String params = "id=" + template.getId();
		Account account = _accountDao.findById(template.getAccountId());
		String eventType = "";
		String description = "";
		
		if (template.getFormat().equals(ImageFormat.ISO)){
			eventType = EventTypes.EVENT_ISO_DELETE;
			description = "ISO ";
		} else {
			eventType = EventTypes.EVENT_TEMPLATE_DELETE;
			description = "Template ";
		}

		if (success) {
			saveEvent(userId, account.getId(), account.getDomainId(), EventVO.LEVEL_INFO, eventType, description + template.getName() + " succesfully deleted.", params);
		} else {
			saveEvent(userId, account.getId(), account.getDomainId(), EventVO.LEVEL_ERROR, eventType, description + template.getName() + " was not deleted.", params);
		}

		return success;
    }
    
    @Override
    public long deleteTemplateAsync(long userId, long templateId, Long zoneId) throws InvalidParameterValueException {
    	UserVO user = _userDao.findById(userId);
    	if (user == null) {
    		throw new InvalidParameterValueException("Please specify a valid user.");
    	}
    	
    	VMTemplateVO template = _templateDao.findById(templateId);
    	if (template == null) {
    		throw new InvalidParameterValueException("Please specify a valid template.");
    	}
    	
    	if (template.getFormat() == ImageFormat.ISO) {
    		throw new InvalidParameterValueException("Please specify a valid template.");
    	}
    	
    	if (template.getCreateStatus() != AsyncInstanceCreateStatus.Created) {
    		throw new InvalidParameterValueException("Please specify a template that is installed.");
    	}
    	
    	if (template.getUniqueName().equals("routing")) {
    		throw new InvalidParameterValueException("The DomR template cannot be deleted.");
    	}
    	
    	if (zoneId != null && (_hostDao.findSecondaryStorageHost(zoneId) == null)) {
    		throw new InvalidParameterValueException("Failed to find a secondary storage host in the specified zone.");
    	}
    	
        DeleteTemplateParam param = new DeleteTemplateParam(userId, templateId, zoneId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteTemplate");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(DeleteTemplateCmd.getStaticName());
        
        return _asyncMgr.submitAsyncJob(job);
    }
    
    @Override
    public boolean copyTemplate(long userId, long templateId, long sourceZoneId, long destZoneId) throws InternalErrorException {
    	DataCenterVO destZone = _dcDao.findById(destZoneId);
    	VMTemplateVO template = _templateDao.findById(templateId);
    
    	boolean success = _tmpltMgr.copy(templateId, sourceZoneId, destZoneId);
    	
    	// Log an event
    	
		String params = "id=" + template.getId();
		Account account = _accountDao.findById(template.getAccountId());
		String eventType = "";
		String description = "";
		
		if (template.getFormat().equals(ImageFormat.ISO)){
			eventType = EventTypes.EVENT_ISO_COPY;
			description = "ISO ";
		} else {
			eventType = EventTypes.EVENT_TEMPLATE_COPY;
			description = "Template ";
		}

		if (success) {
			saveEvent(userId, account.getId(), account.getDomainId(), EventVO.LEVEL_INFO, eventType, description + template.getName() + " succesfully copied to zone: " + destZone.getName() + ".", params);
		} else {
			saveEvent(userId, account.getId(), account.getDomainId(), EventVO.LEVEL_ERROR, eventType, description + template.getName() + " was not copied to zone: " + destZone.getName() + ".", params);
		}

		return success;
    }
    
    @Override
    public long copyTemplateAsync(long userId, long templateId, long sourceZoneId, long destZoneId) throws InvalidParameterValueException {
    	UserVO user = _userDao.findById(userId);
    	if (user == null) {
    		throw new InvalidParameterValueException("Please specify a valid user.");
    	}
    	
    	VMTemplateVO template = _templateDao.findById(templateId);
    	if (template == null) {
    		throw new InvalidParameterValueException("Please specify a valid template.");
    	}
    	
    	DataCenterVO sourceZone = _dcDao.findById(sourceZoneId);
    	if (sourceZone == null) {
    		throw new InvalidParameterValueException("Please specify a valid source zone.");
    	}
    	
    	DataCenterVO destZone = _dcDao.findById(destZoneId);
    	if (destZone == null) {
    		throw new InvalidParameterValueException("Please specify a valid destination zone.");
    	}
    	
    	if (template.getCreateStatus() != AsyncInstanceCreateStatus.Created) {
    		throw new InvalidParameterValueException("Please specify a template that is installed.");
    	}
    	
    	if (_hostDao.findSecondaryStorageHost(sourceZoneId) == null) {
    		throw new InvalidParameterValueException("Failed to find a secondary storage host in the specified source zone.");
    	}
    	
    	if (_hostDao.findSecondaryStorageHost(destZoneId) == null) {
    		throw new InvalidParameterValueException("Failed to find a secondary storage host in the specified destination zone.");
    	}
    	
        CopyTemplateParam param = new CopyTemplateParam(userId, templateId, sourceZoneId, destZoneId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CopyTemplate");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(DeleteTemplateCmd.getStaticName());
        
        return _asyncMgr.submitAsyncJob(job);
    }
    
    @Override
    public long deleteIsoAsync(long userId, long isoId, Long zoneId) throws InvalidParameterValueException {
    	UserVO user = _userDao.findById(userId);
    	if (user == null) {
    		throw new InvalidParameterValueException("Please specify a valid user.");
    	}
    	
    	VMTemplateVO iso = _templateDao.findById(isoId);
    	if (iso == null) {
    		throw new InvalidParameterValueException("Please specify a valid ISO.");
    	}
    	
    	if (iso.getFormat() != ImageFormat.ISO) {
    		throw new InvalidParameterValueException("Please specify a valid ISO.");
    	}
    	
    	if (iso.getCreateStatus() != AsyncInstanceCreateStatus.Created) {
    		throw new InvalidParameterValueException("Please specify an ISO that is installed.");
    	}
    	
    	if (zoneId != null && (_hostDao.findSecondaryStorageHost(zoneId) == null)) {
    		throw new InvalidParameterValueException("Failed to find a secondary storage host in the specified zone.");
    	}
    	
    	DeleteTemplateParam param = new DeleteTemplateParam(userId, isoId, zoneId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteTemplate");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(DeleteIsoCmd.getStaticName());
        
        return _asyncMgr.submitAsyncJob(job);
    }

    @Override
    public VMTemplateVO findTemplateById(long templateId) {
        return _templateDao.findById(templateId);
    }

    @Override
    public List<UserVmVO> listUserVMsByHostId(long hostId) {
        return _userVmDao.listByHostId(hostId);
    }

    @Override
    public List<UserVmVO> searchForUserVMs(Criteria c) {
        Filter searchFilter = new Filter(UserVmVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchBuilder<UserVmVO> sb = _userVmDao.createSearchBuilder();

        // some criteria matter for generating the join condition
        Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID);
        Object domainId = c.getCriteria(Criteria.DOMAINID);

        // get the rest of the criteria
        Object id = c.getCriteria(Criteria.ID);
        Object name = c.getCriteria(Criteria.NAME);
        Object state = c.getCriteria(Criteria.STATE);
        Object notState = c.getCriteria(Criteria.NOTSTATE);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object pod = c.getCriteria(Criteria.PODID);
        Object hostId = c.getCriteria(Criteria.HOSTID);
        Object hostName = c.getCriteria(Criteria.HOSTNAME);
        Object keyword = c.getCriteria(Criteria.KEYWORD);
        Object isAdmin = c.getCriteria(Criteria.ISADMIN);

        sb.and("displayName", sb.entity().getDisplayName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("accountIdEQ", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("accountIdIN", sb.entity().getAccountId(), SearchCriteria.Op.IN);
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("stateEQ", sb.entity().getState(), SearchCriteria.Op.NEQ);
        sb.and("stateNEQ", sb.entity().getState(), SearchCriteria.Op.EQ);
        sb.and("stateNIN", sb.entity().getState(), SearchCriteria.Op.NIN);
        sb.and("dataCenterId", sb.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        sb.and("podId", sb.entity().getPodId(), SearchCriteria.Op.EQ);
        sb.and("hostIdEQ", sb.entity().getHostId(), SearchCriteria.Op.EQ);
        sb.and("hostIdIN", sb.entity().getHostId(), SearchCriteria.Op.IN);

        if ((accountIds == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        // populate the search criteria with the values passed in
        SearchCriteria sc = sb.create();

        if (keyword != null) {
            sc.setParameters("displayName", "%" + keyword + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        }
        if (accountIds != null) {
            if (accountIds.length == 1) {
                if (accountIds[0] != null) {
                    sc.setParameters("accountIdEQ", accountIds[0]);
                }
            } else {
                sc.setParameters("accountIdIN", accountIds);
            }
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);

            // I want to join on user_vm.domain_id = domain.id where domain.path like 'foo%'
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        if (name != null) {
            sc.setParameters("name", "%" + name + "%");
        }
        if (state != null) {
            if (notState != null && (Boolean) notState == true) {
                sc.setParameters("stateNEQ", state);
            } else {
                sc.setParameters("stateEQ", state);
            }
        }

        if ((isAdmin != null) && ((Boolean) isAdmin != true)) {
            sc.setParameters("stateNIN", "Destroyed", "Expunging");
        }

        if (zone != null) {
            sc.setParameters("dataCenterId", zone);
        }
        if (pod != null) {
            sc.setParameters("podId", pod);
        }

        if (hostId != null) {
            sc.setParameters("hostIdEQ", hostId);
        } else {
            if (hostName != null) {
                List<HostVO> hosts = _hostDao.findHostsLike((String) hostName);
                if (hosts != null & !hosts.isEmpty()) {
                    Long[] hostIds = new Long[hosts.size()];
                    for (int i = 0; i < hosts.size(); i++) {
                        HostVO host = hosts.get(i);
                        hostIds[i] = host.getId();
                    }
                    sc.setParameters("hostIdIN", (Object[]) hostIds);
                } else {
                    return new ArrayList<UserVmVO>();
                }
            }
        }

        return _userVmDao.search(sc, searchFilter);
    }

    @Override
    public List<FirewallRuleVO> listIPForwarding(String publicIPAddress, boolean forwarding) {
        return _firewallRulesDao.listIPForwarding(publicIPAddress, forwarding);
    }

    @Override
    public List<NetworkRuleConfigVO> searchForNetworkRules(Criteria c) {
        Filter searchFilter = new Filter(NetworkRuleConfigVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object groupId = c.getCriteria(Criteria.GROUPID);
        Object id = c.getCriteria(Criteria.ID);
        Object accountId = c.getCriteria(Criteria.ACCOUNTID);

        SearchBuilder<NetworkRuleConfigVO> sb = _networkRuleConfigDao.createSearchBuilder();
        if (id != null) {
            sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        }

        if (groupId != null) {
            sb.and("securityGroupId", sb.entity().getSecurityGroupId(), SearchCriteria.Op.EQ);
        }

        if (accountId != null) {
            // join with securityGroup table to make sure the account is the owner of the network rule
            SearchBuilder<SecurityGroupVO> securityGroupSearch = _securityGroupDao.createSearchBuilder();
            securityGroupSearch.and("accountId", securityGroupSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
            sb.join("groupId", securityGroupSearch, securityGroupSearch.entity().getId(), sb.entity().getSecurityGroupId());
        }

        SearchCriteria sc = sb.create();

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (groupId != null) {
            sc.setParameters("securityGroupId", groupId);
        }

        if (accountId != null) {
            sc.setJoinParameters("groupId", "accountId", accountId);
        }

        return _networkRuleConfigDao.search(sc, searchFilter);
    }

    @Override
    public List<EventVO> searchForEvents(Criteria c) {
        Filter searchFilter = new Filter(EventVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object[] userIds = (Object[]) c.getCriteria(Criteria.USERID);
        Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID);
        Object username = c.getCriteria(Criteria.USERNAME);
        Object accountName = c.getCriteria(Criteria.ACCOUNTNAME);
        Object type = c.getCriteria(Criteria.TYPE);
        Object level = c.getCriteria(Criteria.LEVEL);
        Date startDate = (Date) c.getCriteria(Criteria.STARTDATE);
        Date endDate = (Date) c.getCriteria(Criteria.ENDDATE);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<EventVO> sb = _eventDao.createSearchBuilder();
        sb.and("levelL", sb.entity().getLevel(), SearchCriteria.Op.LIKE);
        sb.and("userIdEQ", sb.entity().getUserId(), SearchCriteria.Op.EQ);
        sb.and("userIdIN", sb.entity().getUserId(), SearchCriteria.Op.IN);
        sb.and("accountIdEQ", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("accountIdIN", sb.entity().getAccountId(), SearchCriteria.Op.IN);
        sb.and("accountName", sb.entity().getAccountName(), SearchCriteria.Op.LIKE);
        sb.and("domainIdEQ", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        sb.and("type", sb.entity().getType(), SearchCriteria.Op.EQ);
        sb.and("levelEQ", sb.entity().getLevel(), SearchCriteria.Op.EQ);
        sb.and("createDateB", sb.entity().getCreateDate(), SearchCriteria.Op.BETWEEN);
        sb.and("createDateG", sb.entity().getCreateDate(), SearchCriteria.Op.GTEQ);
        sb.and("createDateL", sb.entity().getCreateDate(), SearchCriteria.Op.LTEQ);

        if ((accountIds == null) && (accountName == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("levelL", "%" + keyword + "%");
        } else if (level != null) {
            sc.setParameters("levelEQ", level);
        }

        if (userIds == null && username != null) {
            List<UserVO> users = findUsersLike((String) username);
            if (users == null || users.size() == 0) {
                return new ArrayList<EventVO>();
            }
            userIds = new Long[users.size()];
            for (int i = 0; i < users.size(); i++) {
                userIds[i] = users.get(i).getId();
            }
        }

        if (userIds != null) {
            if (userIds.length == 1) {
                if ((userIds[0] != null) && !((Long) userIds[0]).equals(Long.valueOf(-1))) {
                    sc.setParameters("userIdEQ", userIds[0]);
                }
            } else {
                sc.setParameters("userIdIN", userIds);
            }
        }
        if (accountIds != null) {
            if (accountIds.length == 1) {
                if ((accountIds[0] != null) && !((Long) accountIds[0]).equals(Long.valueOf(-1))) {
                    sc.setParameters("accountIdEQ", accountIds[0]);
                }
            } else {
                sc.setParameters("accountIdIN", accountIds);
            }
        } else if (domainId != null) {
            if (accountName != null) {
                sc.setParameters("domainIdEQ", domainId);
                sc.setParameters("accountName", "%" + accountName + "%");
                sc.addAnd("removed", SearchCriteria.Op.NULL);
            } else {
                DomainVO domain = _domainDao.findById((Long)domainId);
                sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
            }
        }
        if (type != null) {
            sc.setParameters("type", type);
        }
        
        if (startDate != null && endDate != null) {
            startDate = massageDate(startDate, 0, 0, 0);
            endDate = massageDate(endDate, 23, 59, 59);
            sc.setParameters("createDateB", startDate, endDate);
        } else if (startDate != null) {
            startDate = massageDate(startDate, 0, 0, 0);
            sc.setParameters("createDateG", startDate);
        } else if (endDate != null) {
            endDate = massageDate(endDate, 23, 59, 59);
            sc.setParameters("createDateL", endDate);
        }

        return _eventDao.searchAllEvents(sc, searchFilter);
    }

    @Override
    public List<DomainRouterVO> listRoutersByHostId(long hostId) {
        return _routerDao.listByHostId(hostId);
    }

    @Override
    public List<DomainRouterVO> listAllActiveRouters() {
        return _routerDao.listAllActive();
    }

    @Override
    public List<DomainRouterVO> searchForRouters(Criteria c) {
        Filter searchFilter = new Filter(DomainRouterVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID);
        Object name = c.getCriteria(Criteria.NAME);
        Object state = c.getCriteria(Criteria.STATE);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object pod = c.getCriteria(Criteria.PODID);
        Object hostId = c.getCriteria(Criteria.HOSTID);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<DomainRouterVO> sb = _routerDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.IN);
        sb.and("state", sb.entity().getState(), SearchCriteria.Op.EQ);
        sb.and("dataCenterId", sb.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        sb.and("podId", sb.entity().getPodId(), SearchCriteria.Op.EQ);
        sb.and("hostId", sb.entity().getHostId(), SearchCriteria.Op.EQ);

        if ((accountIds == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("name", "%" + keyword + "%");
        } else if (name != null) {
            sc.setParameters("name", "%" + name + "%");
        }

        if (accountIds != null) {
            sc.setParameters("accountId", accountIds);
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        if (state != null) {
            sc.setParameters("state", state);
        }
        if (zone != null) {
            sc.setParameters("dataCenterId", zone);
        }
        if (pod != null) {
            sc.setParameters("podId", pod);
        }
        if (hostId != null) {
            sc.setParameters("hostId", hostId);
        }

        return _routerDao.search(sc, searchFilter);
    }

    @Override
    public List<ConsoleProxyVO> searchForConsoleProxy(Criteria c) {
        Filter searchFilter = new Filter(ConsoleProxyVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _consoleProxyDao.createSearchCriteria();

        Object id = c.getCriteria(Criteria.ID);
        Object name = c.getCriteria(Criteria.NAME);
        Object state = c.getCriteria(Criteria.STATE);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object pod = c.getCriteria(Criteria.PODID);
        Object hostId = c.getCriteria(Criteria.HOSTID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }
        
        if(id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }
        if (state != null) {
            sc.addAnd("state", SearchCriteria.Op.EQ, state);
        }
        if (zone != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zone);
        }
        if (pod != null) {
            sc.addAnd("podId", SearchCriteria.Op.EQ, pod);
        }
        if (hostId != null) {
            sc.addAnd("hostId", SearchCriteria.Op.EQ, hostId);
        }

        return _consoleProxyDao.search(sc, searchFilter);
    }

    @Override
    public List<VolumeVO> findVolumesByHost(long hostId) {
        return _volumeDao.findByHost(hostId);
    }

    @Override
    public VolumeVO findVolumeById(long id) {
        return _volumeDao.findById(id);
    }

    @Override
    public List<VolumeVO> searchForVolumes(Criteria c) {
        Filter searchFilter = new Filter(VolumeVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        // SearchCriteria sc = _volumeDao.createSearchCriteria();

        Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID);
        Object type = c.getCriteria(Criteria.VTYPE);
        Long vmInstanceId = (Long) c.getCriteria(Criteria.INSTANCEID);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object pod = c.getCriteria(Criteria.PODID);
        Object hostId = c.getCriteria(Criteria.HOSTID);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);
        Object name = c.getCriteria(Criteria.NAME);

        // hack for now, this should be done better but due to needing a join I opted to
        // do this quickly and worry about making it pretty later
        SearchBuilder<VolumeVO> sb = _volumeDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("accountIdEQ", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("accountIdIN", sb.entity().getAccountId(), SearchCriteria.Op.IN);
        sb.and("volumeType", sb.entity().getVolumeType(), SearchCriteria.Op.LIKE);
        sb.and("instanceId", sb.entity().getInstanceId(), SearchCriteria.Op.EQ);
        sb.and("dataCenterId", sb.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        sb.and("podId", sb.entity().getPodId(), SearchCriteria.Op.EQ);
        sb.and("hostId", sb.entity().getHostId(), SearchCriteria.Op.EQ);

        // Don't return DomR and ConsoleProxy volumes
        sb.and("domRNameLabel", sb.entity().getNameLabel(), SearchCriteria.Op.NLIKE);
        sb.and("domPNameLabel", sb.entity().getNameLabel(), SearchCriteria.Op.NLIKE);

        // Only return Volumes that are in the "Created" state
        sb.and("status", sb.entity().getStatus(), SearchCriteria.Op.EQ);

        // Only return volumes that are not destroyed
        sb.and("destroyed", sb.entity().getDestroyed(), SearchCriteria.Op.EQ);

        if ((accountIds == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        // now set the SC criteria...
        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("name", "%" + keyword + "%");
        } else if (name != null) {
            sc.setParameters("name", "%" + name + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (accountIds != null) {
            if ((accountIds.length == 1) && (accountIds[0] != null)) {
                sc.setParameters("accountIdEQ", accountIds[0]);
            } else {
                sc.setParameters("accountIdIN", accountIds);
            }                
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }
        if (type != null) {
            sc.setParameters("volumeType", "%" + type + "%");
        }
        if (vmInstanceId != null) {
            sc.setParameters("instanceId", vmInstanceId);
        }
        if (zone != null) {
            sc.setParameters("dataCenterId", zone);
        }
        if (pod != null) {
            sc.setParameters("podId", pod);
        }
        if (hostId != null) {
            sc.setParameters("hostId", hostId);
        }
        
        // Don't return DomR and ConsoleProxy volumes
        sc.setParameters("domRNameLabel", "r-%");
        sc.setParameters("domPNameLabel", "v-%");
        
        // Only return Volumes that are in the "Created" state
        sc.setParameters("status", AsyncInstanceCreateStatus.Created);

        // Only return volumes that are not destroyed
        sc.setParameters("destroyed", false);

        return _volumeDao.search(sc, searchFilter);
    }

    @Override
    public boolean volumeIsOnSharedStorage(long volumeId) throws InvalidParameterValueException {
        // Check that the volume is valid
        VolumeVO volume = _volumeDao.findById(volumeId);
        if (volume == null) {
            throw new InvalidParameterValueException("Please specify a valid volume ID.");
        }

        return _storageMgr.volumeOnSharedStoragePool(volume);
    }

    @Override
    public HostPodVO findHostPodById(long podId) {
        return _hostPodDao.findById(podId);
    }
    
    @Override
    public HostVO findSecondaryStorageHosT(long zoneId) {
    	return _storageMgr.getSecondaryStorageHost(zoneId);
    }

    @Override
    public List<IPAddressVO> searchForIPAddresses(Criteria c) {
        Filter searchFilter = new Filter(IPAddressVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object address = c.getCriteria(Criteria.IPADDRESS);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object vlan = c.getCriteria(Criteria.VLAN);
        Object isAllocated = c.getCriteria(Criteria.ISALLOCATED);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<IPAddressVO> sb = _publicIpAddressDao.createSearchBuilder();
        sb.and("accountIdEQ", sb.entity().getAccountId(), SearchCriteria.Op.EQ);
        sb.and("accountIdIN", sb.entity().getAccountId(), SearchCriteria.Op.IN);
        sb.and("dataCenterId", sb.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        sb.and("address", sb.entity().getAddress(), SearchCriteria.Op.LIKE);
        sb.and("vlanDbId", sb.entity().getVlanDbId(), SearchCriteria.Op.EQ);

        if ((accountIds == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        if ((isAllocated != null) && ((Boolean) isAllocated == true)) {
            sb.and("allocated", sb.entity().getAllocated(), SearchCriteria.Op.NNULL);
        }

        SearchCriteria sc = sb.create();
        if (accountIds != null) {
            if ((accountIds.length == 1) && (accountIds[0] != null)) {
                sc.setParameters("accountIdEQ", accountIds[0]);
            } else {
                sc.setParameters("accountIdIN", accountIds);
            }                
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        if (zone != null) {
            sc.setParameters("dataCenterId", zone);
        }

        if ((address == null) && (keyword != null)) {
            address = keyword;
        }

        if (address != null) {
            sc.setParameters("address", address + "%");
        }

        if (vlan != null) {
            sc.setParameters("vlanDbId", vlan);
        }

        return _publicIpAddressDao.search(sc, searchFilter);
    }

    /*
     * Left in just in case we have to resurrect this code for demo purposes, but for now
     * 
     * @Override public List<UsageVO> searchForUsage(Criteria c) { Filter searchFilter = new Filter(UsageVO.class,
     * c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit()); SearchCriteria sc = new
     * SearchCriteria(UsageVO.class);
     * 
     * Object[] accountIds = (Object[]) c.getCriteria(Criteria.ACCOUNTID); Object startDate =
     * c.getCriteria(Criteria.STARTDATE); Object endDate = c.getCriteria(Criteria.ENDDATE); Object domainId =
     * c.getCriteria(Criteria.DOMAINID);
     * 
     * if (accountIds.length == 1) { if (accountIds[0] != null) { sc.addAnd("accountId", SearchCriteria.Op.EQ,
     * accountIds[0]); } } else { sc.addAnd("accountId", SearchCriteria.Op.IN, accountIds); } if (domainId != null) {
     * sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId); } if (startDate != null && endDate != null) {
     * sc.addAnd("startDate", SearchCriteria.Op.BETWEEN, startDate, endDate); sc.addAnd("startDate",
     * SearchCriteria.Op.BETWEEN, startDate, endDate); } else if (startDate != null) { sc.addAnd("startDate",
     * SearchCriteria.Op.LTEQ, startDate); sc.addAnd("endDate", SearchCriteria.Op.GTEQ, startDate); } else if (endDate
     * != null) { sc.addAnd("startDate", SearchCriteria.Op.LTEQ, endDate); sc.addAnd("endDate", SearchCriteria.Op.GTEQ,
     * endDate); }
     * 
     * List<UsageVO> usageRecords = null; Transaction txn = Transaction.currentTxn(Transaction.USAGE_DB); try {
     * usageRecords = _usageDao.search(sc, searchFilter); } finally { txn.close();
     * 
     * // switch back to VMOPS_DB txn = Transaction.currentTxn(Transaction.VMOPS_DB); txn.close(); }
     * 
     * return usageRecords; }
     */

    @Override
    public List<DiskTemplateVO> listAllActiveDiskTemplates() {
        return _diskTemplateDao.listAllActive();
    }

    @Override
    public UserAccount authenticateUser(String username, String password, String domainName) {
        DomainVO domain = _domainDao.findDomainByName(domainName);
        if (domain != null) {
            return authenticateUser(username, password, domain.getId());
        }
        return null;
    }

    @Override
    public UserAccount authenticateUser(String username, String password, Long domainId) {
        UserAccount user = getUserAccount(username, password, domainId);
        if (user != null) {
            saveEvent(user.getId(), user.getAccountId(), user.getDomainId(), EventTypes.EVENT_USER_LOGIN, "user has logged in");
            return user;
        } else {
            return null;
        }
    }

    @Override
    public void logoutUser(Long userId) {
        UserAccount userAcct = _userAccountDao.findById(userId);
        saveEvent(userId, userAcct.getAccountId(), userAcct.getDomainId(), EventTypes.EVENT_USER_LOGOUT, "user has logged out");
    }

    @Override
    public String updateTemplatePricing(long userId, Long id, float price) {
        VMTemplateVO template = _templateDao.findById(id);

        if (template == null) {
            return "Template (id=" + id + ") does not exist";
        }

        // update the price for the offering if it exists, else update it.
        PricingVO existingPrice = _pricingDao.findByTypeAndId("VMTemplate", id);
        DecimalFormat decimalFormat = new DecimalFormat("#.###");

        if (existingPrice == null) {
            PricingVO pricing = new PricingVO(null, new Float(decimalFormat.format(price)), "per hour", "VMTemplate", id, new Date());
            _pricingDao.persist(pricing);
        } else {
            updatePricing(existingPrice.getId(), new Float(decimalFormat.format(price)), "per hour", "VMTemplate", id, new Date());
        }

        UserAccount userAcct = _userAccountDao.findById(Long.valueOf(userId));

        saveEvent(userId, userAcct.getAccountId(), userAcct.getDomainId(), EventTypes.EVENT_TEMPLATE_UPDATE, "Set price of template:  " + template.getName() + " to " + price
                + " per hour");
        return null;
    }

    @Override
    public NetworkRuleConfigVO createOrUpdateRule(long userId, long securityGroupId, String address, String port, String privateIpAddress, String privatePort, String protocol,
            String algorithm) throws InvalidParameterValueException, PermissionDeniedException, NetworkRuleConflictException, InternalErrorException {
        NetworkRuleConfigVO rule = null;
        try {
            SecurityGroupVO sg = _securityGroupDao.findById(Long.valueOf(securityGroupId));
            if (sg == null) {
                throw new InvalidParameterValueException("security group " + securityGroupId + " does not exist");
            }
            if (!NetUtils.isValidPort(port)) {
                throw new InvalidParameterValueException("port is an invalid value");
            }
            if (!NetUtils.isValidPort(privatePort)) {
                throw new InvalidParameterValueException("privatePort is an invalid value");
            }
            if (protocol != null) {
                if (!NetUtils.isValidProto(protocol)) {
                    throw new InvalidParameterValueException("Invalid protocol");
                }
            }
            if (algorithm != null) {
                if (!NetUtils.isValidAlgorithm(algorithm)) {
                    throw new InvalidParameterValueException("Invalid algorithm");
                }
            }
            rule = createNetworkRuleConfig(userId, securityGroupId, port, privatePort, protocol, algorithm);
        } catch (Exception e) {
            if (e instanceof NetworkRuleConflictException) {
                throw (NetworkRuleConflictException) e;
            } else if (e instanceof InvalidParameterValueException) {
                throw (InvalidParameterValueException) e;
            } else if (e instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e;
            } else if (e instanceof InternalErrorException) {
                throw (InternalErrorException) e;
            }
        }
        return rule;
    }

    @Override
    public long createOrUpdateRuleAsync(boolean isForwarding, long userId, Long accountId, Long domainId, long securityGroupId, String address, String port,
            String privateIpAddress, String privatePort, String protocol, String algorithm) {

        CreateOrUpdateRuleParam param = new CreateOrUpdateRuleParam(isForwarding, userId, accountId, address, port, privateIpAddress, privatePort, protocol, algorithm, domainId,
                securityGroupId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CreateOrUpdateRule");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateNetworkRuleCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job);
    }

    public void deleteRule(long ruleId, long userId, long accountId) throws InvalidParameterValueException, PermissionDeniedException, InternalErrorException {
        Exception e = null;
        try {
            FirewallRuleVO rule = _firewallRulesDao.findById(ruleId);
            if (rule != null) {
                boolean success = false;

                try {
                    if (rule.isForwarding()) {
                        success = deleteIpForwardingRule(userId, accountId, rule.getPublicIpAddress(), rule.getPublicPort(), rule.getPrivateIpAddress(), rule.getPrivatePort(),
                                rule.getProtocol());
                    } else {
                        success = deleteLoadBalancingRule(userId, accountId, rule.getPublicIpAddress(), rule.getPublicPort(), rule.getPrivateIpAddress(), rule.getPrivatePort(),
                                rule.getAlgorithm());
                    }
                } catch (Exception ex) {
                    e = ex;
                }

                String description;
                String type = EventTypes.EVENT_NET_RULE_DELETE;
                String level = EventVO.LEVEL_INFO;
                String ruleName = rule.isForwarding() ? "ip forwarding" : "load balancer";

                if (success) {
                    String desc = "deleted " + ruleName + " rule [" + rule.getPublicIpAddress() + ":" + rule.getPublicPort() + "]->[" + rule.getPrivateIpAddress() + ":"
                            + rule.getPrivatePort() + "] " + rule.getProtocol();
                    if (!rule.isForwarding()) {
                        desc = desc + ", algorithm = " + rule.getAlgorithm();
                    }
                    description = desc;
                } else {
                    level = EventVO.LEVEL_ERROR;
                    String desc = "deleted " + ruleName + " rule [" + rule.getPublicIpAddress() + ":" + rule.getPublicPort() + "]->[" + rule.getPrivateIpAddress() + ":"
                            + rule.getPrivatePort() + "] " + rule.getProtocol();
                    if (!rule.isForwarding()) {
                        desc = desc + ", algorithm = " + rule.getAlgorithm();
                    }
                    description = desc;
                }

                Account account = _accountDao.findById(Long.valueOf(accountId));
                saveEvent(userId, accountId, account.getDomainId(), level, type, description);
            }
        } finally {
            if (e != null) {
                if (e instanceof InvalidParameterValueException) {
                    throw (InvalidParameterValueException) e;
                } else if (e instanceof PermissionDeniedException) {
                    throw (PermissionDeniedException) e;
                } else if (e instanceof InternalErrorException) {
                    throw (InternalErrorException) e;
                }
            }
        }
    }

    public long deleteRuleAsync(long id, long userId, long accountId) {
        DeleteRuleParam param = new DeleteRuleParam(id, userId, accountId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteRule");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    public List<VMTemplateVO> listAllTemplates() {
        return _templateDao.listAll();
    }

    public List<GuestOSVO> listAllGuestOS() {
        return _guestOSDao.listAll();
    }
    
    public List<GuestOSCategoryVO> listAllGuestOSCategories() {
    	return _guestOSCategoryDao.listAll();
    }
    
    public String getConfigurationValue(String name) {
    	return _configDao.getValue(name);
    }

    public ConsoleProxyInfo getConsoleProxy(long dataCenterId, long userVmId) {
        ConsoleProxyVO proxy = _consoleProxyMgr.assignProxy(dataCenterId, userVmId);
        if (proxy == null) {
            return null;
        } else {
            return new ConsoleProxyInfo(proxy.isSslEnabled(), proxy.getPublicIpAddress(), _consoleProxyPort, proxy.getPort());
        }
    }

    public ConsoleProxyVO startConsoleProxy(long instanceId) throws InternalErrorException {
        return _consoleProxyMgr.startProxy(instanceId);
    }

    public boolean stopConsoleProxy(long instanceId) {
        return _consoleProxyMgr.stopProxy(instanceId);
    }

    public boolean rebootConsoleProxy(long instanceId) {
        return _consoleProxyMgr.rebootProxy(instanceId);
    }

    public boolean destroyConsoleProxy(long instanceId) {
        return _consoleProxyMgr.destroyProxy(instanceId);
    }

    public long startConsoleProxyAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StartConsoleProxy");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(StartConsoleProxyCmd.getResultObjectName());
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long stopConsoleProxyAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StopConsoleProxy");
        job.setCmdInfo(gson.toJson(param));
        // use the same result object name as StartConsoleProxyCmd
        job.setCmdOriginator(StartConsoleProxyCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long rebootConsoleProxyAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RebootConsoleProxy");
        job.setCmdInfo(gson.toJson(param));
        // use the same result object name as StartConsoleProxyCmd
        job.setCmdOriginator(StartConsoleProxyCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long destroyConsoleProxyAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DestroyConsoleProxy");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job);
    }

    public String getConsoleAccessUrlRoot(long vmId) {
        VMInstanceVO vm = this.findVMInstanceById(vmId);
        if (vm != null) {
            ConsoleProxyInfo proxy = getConsoleProxy(vm.getDataCenterId(), vmId);
            if (proxy != null)
                return proxy.getProxyImageUrl();
        }
        return null;
    }

    public int getVncPort(VirtualMachine vm) {
        if (vm.getHostId() == null) {
        	s_logger.warn("VM " + vm.getName() + " does not have host, return -1 for its VNC port");
            return -1;
        }
        
        if(s_logger.isTraceEnabled())
        	s_logger.trace("Trying to retrieve VNC port from agent about VM " + vm.getName());
        
        GetVncPortAnswer answer = (GetVncPortAnswer) _agentMgr.easySend(vm.getHostId(), new GetVncPortCommand(vm.getId(), vm.getInstanceName()));
        int port = answer == null ? -1 : answer.getPort();
        
        if(s_logger.isTraceEnabled())
        	s_logger.trace("Retrieved VNC port about VM " + vm.getName() + " is " + port);
        
        return port;
    }

    public ConsoleProxyVO findConsoleProxyById(long instanceId) {
        return _consoleProxyDao.findById(instanceId);
    }

    public List<DomainVO> searchForDomains(Criteria c) {
        Filter searchFilter = new Filter(DomainVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        Long domainId = (Long) c.getCriteria(Criteria.ID);
        String domainName = (String) c.getCriteria(Criteria.NAME);
        Integer level = (Integer) c.getCriteria(Criteria.LEVEL);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<DomainVO> sb = _domainDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("level", sb.entity().getLevel(), SearchCriteria.Op.EQ);
        sb.and("path", sb.entity().getPath(), SearchCriteria.Op.LIKE);

        SearchCriteria sc = sb.create();

        if (keyword != null) {
            sc.setParameters("name", "%" + keyword + "%");
        } else if (domainName != null) {
            sc.setParameters("name", "%" + domainName + "%");
        }

        if (level != null) {
            sc.setParameters("level", level);
        }

        if ((domainName == null) && (level == null) && (domainId != null)) {
            DomainVO domain = _domainDao.findById(domainId);
            if (domain != null) {
                sc.setParameters("path", domain.getPath() + "%");
            }
        }

        return _domainDao.search(sc, searchFilter);
    }

    public List<DomainVO> searchForDomainChildren(Criteria c) {
        Filter searchFilter = new Filter(DomainVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        Long domainId = (Long) c.getCriteria(Criteria.ID);
        String domainName = (String) c.getCriteria(Criteria.NAME);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchCriteria sc = _domainDao.createSearchCriteria();

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (domainId != null) {
            sc.addAnd("parent", SearchCriteria.Op.EQ, domainId);
        }

        if (domainName != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + domainName + "%");
        }

        return _domainDao.search(sc, searchFilter);
	}
	
    @Override
    public DomainVO createDomain(String name, Long ownerId, Long parentId) {
        SearchCriteria sc = _domainDao.createSearchCriteria();
        sc.addAnd("name", SearchCriteria.Op.EQ, name);
        List<DomainVO> domains = _domainDao.search(sc, null);
        if ((domains == null) || domains.isEmpty()) {
            DomainVO domain = new DomainVO(name, ownerId, parentId);
            DomainVO dbDomain = _domainDao.create(domain);
            saveEvent(new Long(1), ownerId, domain.getId(), EventVO.LEVEL_INFO, EventTypes.EVENT_DOMAIN_CREATE, "Domain, " + name + " created with owner id = " + ownerId
                    + " and parentId " + parentId);
            return dbDomain;
        } else {
            saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_ERROR, EventTypes.EVENT_DOMAIN_CREATE, "Domain, " + name + " was not created with owner id = " + ownerId
                    + " and parentId " + parentId);
        }
        return null;
    }

    @Override
    public long deleteDomainAsync(Long domainId, Long ownerId, Boolean cleanup) {
        DeleteDomainParam param = new DeleteDomainParam(domainId, ownerId, cleanup);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteDomain");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);
    }

    // FIXME:  need userId so the event can be saved with proper id
    @Override
    public String deleteDomain(Long domainId, Long ownerId, Boolean cleanup) {
        try {
            DomainVO domain = _domainDao.findById(domainId);
            if (domain != null) {
                if ((cleanup != null) && cleanup.booleanValue()) {
                    boolean success = cleanupDomain(domainId, ownerId);
                    if (!success) {
                        saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_ERROR, EventTypes.EVENT_DOMAIN_DELETE, "Failed to clean up domain resources and sub domains, domain with id " + domainId + " was not deleted.");
                        return "Failed to clean up domain resources and sub domains, delete failed on domain with id " + domainId + ".";
                    }
                } else {
                    if (!_domainDao.remove(domainId)) {
                        saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_ERROR, EventTypes.EVENT_DOMAIN_DELETE, "Domain with id " + domainId + " was not deleted");
                        return "delete failed on domain with id " + domainId + "; please make sure all users have been removed from the domain before deleting";
                    } else {
                        saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_INFO, EventTypes.EVENT_DOMAIN_DELETE, "Domain with id " + domainId + " was deleted");
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            return "delete failed on domain with id " + domainId + "; please make sure all users have been removed from the domain before deleting";
        }
    }

    private boolean cleanupDomain(Long domainId, Long ownerId) {
        boolean success = true;
        {
            SearchCriteria sc = _domainDao.createSearchCriteria();
            sc.addAnd("parent", SearchCriteria.Op.EQ, domainId);
            List<DomainVO> domains = _domainDao.search(sc, null);

            // cleanup sub-domains first
            for (DomainVO domain : domains) {
                success = (success && cleanupDomain(domain.getId(), domain.getOwner()));
            }
        }

        {
            // delete users which will also delete accounts and release resources for those accounts
            SearchCriteria sc = _accountDao.createSearchCriteria();
            sc.addAnd("domainId", SearchCriteria.Op.EQ, domainId);
            List<AccountVO> accounts = _accountDao.search(sc, null);
            for (AccountVO account : accounts) {
                SearchCriteria userSc = _userDao.createSearchCriteria();
                userSc.addAnd("accountId", SearchCriteria.Op.EQ, account.getId());
                List<UserVO> users = _userDao.search(userSc, null);
                for (UserVO user : users) {
                    success = (success && deleteUser(user.getId()));
                }
            }
        }

        // delete the domain itself
        boolean deleteDomainSuccess = _domainDao.remove(domainId);
        if (!deleteDomainSuccess) {
            saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_ERROR, EventTypes.EVENT_DOMAIN_DELETE, "Domain with id " + domainId + " was not deleted");
        } else {
            saveEvent(new Long(1), ownerId, new Long(0), EventVO.LEVEL_INFO, EventTypes.EVENT_DOMAIN_DELETE, "Domain with id " + domainId + " was deleted");
        }

        return success && deleteDomainSuccess;
    }

    public void updateDomain(Long domainId, String domainName) {
        _domainDao.update(domainId, domainName);
        DomainVO domain = _domainDao.findById(domainId);
        saveEvent(new Long(1), domain.getOwner(), domain.getId(), EventVO.LEVEL_INFO, EventTypes.EVENT_DOMAIN_UPDATE, "Domain, " + domainName + " was updated");
    }

    public Long findDomainIdByAccountId(Long accountId) {
        if (accountId == null) {
            return null;
        }

        AccountVO account = _accountDao.findById(accountId);
        if (account != null) {
            return account.getDomainId();
        }

        return null;
    }

    public DomainVO findDomainIdById(Long domainId) {
        return _domainDao.findById(domainId);
    }
    
    public DomainVO findDomainByName(String domain) {
        return _domainDao.findDomainByName(domain);
    }

    @Override
    public List<AlertVO> searchForAlerts(Criteria c) {
        Filter searchFilter = new Filter(AlertVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _alertDao.createSearchCriteria();

        Object type = c.getCriteria(Criteria.TYPE);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("subject", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (type != null) {
            sc.addAnd("type", SearchCriteria.Op.EQ, type);
        }

        return _alertDao.search(sc, searchFilter);
    }

    @Override
    public List<CapacityVO> listCapacities(Criteria c) {
        // make sure capacity is accurate before displaying it anywhere
        // NOTE: listCapacities is currently called by the UI only, so this
        // shouldn't be called much since it checks all hosts/VMs
        // to figure out what has been allocated.
        _alertMgr.recalculateCapacity();

        Filter searchFilter = new Filter(CapacityVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _capacityDao.createSearchCriteria();

        Object type = c.getCriteria(Criteria.TYPE);
        Object zoneId = c.getCriteria(Criteria.DATACENTERID);
        Object podId = c.getCriteria(Criteria.PODID);
        Object hostId = c.getCriteria(Criteria.HOSTID);

        if (type != null) {
            sc.addAnd("capacityType", SearchCriteria.Op.EQ, type);
        }

        if (zoneId != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zoneId);
        }

        if (podId != null) {
            sc.addAnd("podId", SearchCriteria.Op.EQ, podId);
        }

        if (hostId != null) {
            sc.addAnd("hostOrPoolId", SearchCriteria.Op.EQ, hostId);
        }

        return _capacityDao.search(sc, searchFilter);
    }

    public Integer[] countRoutersAndProxies(Long hostId) {
        Integer[] routersAndProxies = _vmInstanceDao.countRoutersAndProxies(hostId);

        Integer[] routersAndProxiesAndMemory = new Integer[4];
        routersAndProxiesAndMemory[0] = routersAndProxies[0];
        routersAndProxiesAndMemory[1] = routersAndProxies[1];
        routersAndProxiesAndMemory[2] = Integer.valueOf(_routerRamSize);
        routersAndProxiesAndMemory[3] = Integer.valueOf(_proxyRamSize);

        return routersAndProxiesAndMemory;
    }

    @Override
    public long createSnapshotAsync(long userId, long volumeId) throws InvalidParameterValueException, ResourceAllocationException {
        List<Long> policies = new ArrayList<Long>();
        // Special policy id for manual snapshot.
        policies.add(SnapshotManager.MANUAL_POLICY_ID);
        return _snapMgr.createSnapshotAsync(userId, volumeId, policies);
    }

    @Override
    public SnapshotVO createTemplateSnapshot(Long userId, long volumeId) {
        return _vmMgr.createTemplateSnapshot(userId, volumeId);
    }
    
    @Override
    public boolean destroyTemplateSnapshot(Long userId, long snapshotId) {
        return _vmMgr.destroyTemplateSnapshot(userId, snapshotId);
    }

    @Override
    public long deleteSnapshotAsync(long userId, long snapshotId) {
        // Precondition: snapshotId is valid
        // Manual snapshots have a special policy Id 1
        long policyId = SnapshotManager.MANUAL_POLICY_ID;
        return _snapMgr.deleteSnapshot(userId, snapshotId, policyId);
    }


    @Override
    public boolean rollbackToSnapshot(Long userId, long snapshotId) {
        return _vmMgr.rollbackToSnapshot(userId, snapshotId);
    }

    @Override
    public long rollbackToSnapshotAsync(Long userId, long volumeId, long snapshotId) {
        // For manual snapshots, the special policyId is 1
        long policyId = SnapshotManager.MANUAL_POLICY_ID;
        SnapshotOperationParam param = new SnapshotOperationParam(userId, volumeId, snapshotId, policyId);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RollbackSnapshot");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public List<SnapshotVO> listSnapshots(Criteria c, Long volumeId, String intervalType) throws InvalidParameterValueException {
        Filter searchFilter = new Filter(SnapshotVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        if(intervalType!=null && volumeId != null){
            IntervalType type =  DateUtil.getIntervalType(intervalType);
            if(type == null){
                    throw new InvalidParameterValueException("Unsupported interval type " + intervalType);
            }
            SnapshotPolicyVO snapPolicy = _snapMgr.getPolicyForVolumeByInterval(volumeId, (short)type.ordinal());
            if(snapPolicy == null){
                    s_logger.warn("Policy with interval "+ intervalType +" not assigned to volume: "+volumeId);
                    return null;
            }
            return _snapMgr.listSnapsforPolicy(snapPolicy.getId(), searchFilter);
        }

        if(volumeId != null){
        	return _snapshotDao.listByVolumeId(searchFilter, volumeId);
        } else {
        	return _snapshotDao.listAllActive(searchFilter);
        }
    }

    @Override
    public Snapshot findSnapshotById(long snapshotId) {
        SnapshotVO snapshot = _snapshotDao.findById(snapshotId);
        if (snapshot != null && snapshot.getRemoved() == null) {
            return snapshot;
        }
        else {
            return null;
        }
    }

    @Override
    public void scheduleRecurringSnapshots(Long userId, long vmId, int hourlyMax, int dailyMax, int weeklyMax, int monthlyMax) {
        _vmMgr.scheduleRecurringSnapshot(userId, vmId, hourlyMax, dailyMax, weeklyMax, monthlyMax);
    }

    @Override
    public long scheduleRecurringSnapshotsAsync(Long userId, long vmId, int hourlyMax, int dailyMax, int weeklyMax, int monthlyMax) {
        RecurringSnapshotParam param = new RecurringSnapshotParam(userId, vmId, hourlyMax, dailyMax, weeklyMax, monthlyMax);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        
        job.setCmd("ScheduleRecurringSnapshot");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public ScheduledVolumeBackup findRecurringSnapshotSchedule(long instanceId) {
        return _vmMgr.findRecurringSnapshotSchedule(instanceId);
    }

    @Override
    public VMTemplateVO createPrivateTemplate(VMTemplateVO template, Long userId, long snapshotId, String name, String description) throws InvalidParameterValueException {

        return _vmMgr.createPrivateTemplate(template, userId, snapshotId, name, description);
    }

    @Override
    public long createPrivateTemplateAsync(Long userId, long volumeId, String name, String description, long guestOSId, Boolean requiresHvm, Integer bits, Boolean passwordEnabled, boolean isPublic, boolean featured)
            throws InvalidParameterValueException, ResourceAllocationException {
        if (name.length() > 32 || !name.matches("^[\\p{Alnum} ._-]+")) {
            throw new InvalidParameterValueException("Only alphanumeric, space, dot, dashes and underscore characters allowed");
        }

        // Check that the volume is valid
        VolumeVO volume = _volumeDao.findById(volumeId);
        if (volume == null) {
            throw new InvalidParameterValueException("Please specify a valid volume.");
        }

        // Check that the volume will not be active when the private template is created
        if (!_storageMgr.volumeInactive(volume)) {
            String msg = "Unable to create private template for volume: " + volume.getName() + "; volume is attached to a non-stopped VM.";

            if (s_logger.isInfoEnabled()) {
                s_logger.info(msg);
            }

            throw new VmopsRuntimeException(msg);
        }

        // Check that the guest OS is valid
        GuestOSVO guestOS = _guestOSDao.findById(guestOSId);
        if (guestOS == null) {
            throw new InvalidParameterValueException("Please specify a valid guest OS.");
        }       

        CreatePrivateTemplateParam param = new CreatePrivateTemplateParam(userId, volumeId, guestOSId, name, description, requiresHvm, bits, passwordEnabled, isPublic, featured);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("CreatePrivateTemplate");
        job.setCmdInfo(gson.toJson(param));
        job.setCmdOriginator(CreateTemplateCmd.getResultObjectName());
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public DiskOfferingVO findDiskOfferingById(long diskOfferingId) {
        return _diskOfferingDao.findById(diskOfferingId);
    }

    @Override
    @DB
    public boolean updateTemplatePermissions(long templateId, String operation, Boolean isPublic, Boolean isFeatured, List<String> accountNames) throws InvalidParameterValueException,
            PermissionDeniedException, InternalErrorException {
        Transaction txn = Transaction.currentTxn();
        VMTemplateVO template = _templateDao.findById(templateId);
        if (template == null) {
            throw new InvalidParameterValueException("Unable to find template with id " + templateId);
        }

        Long accountId = template.getAccountId();
        if (accountId == null) {
            // if there is know owner of the template then it's probably
            // already a public template (or domain private template)
            // so publishing to individual users is irrelevant
            throw new InvalidParameterValueException("Update template permissions is an invalid operation on template " + template.getName());
        }

        Account account = _accountDao.findById(accountId);
        if (account == null) {
            throw new PermissionDeniedException("Unable to verify owner of template " + template.getName());
        }

        VMTemplateVO updatedTemplate = _templateDao.createForUpdate();
        
        if (isPublic != null) {
            updatedTemplate.setPublicTemplate(isPublic.booleanValue());
        }
        
        if (isFeatured != null) {
        	updatedTemplate.setFeatured(isFeatured.booleanValue());
        }
        
        _templateDao.update(template.getId(), updatedTemplate);

        Long domainId = account.getDomainId();
        if ("add".equalsIgnoreCase(operation)) {
            txn.start();
            for (String accountName : accountNames) {
                Account permittedAccount = _accountDao.findActiveAccount(accountName, domainId);
                if (permittedAccount != null) {
                    if (permittedAccount.getId().longValue() == account.getId().longValue()) {
                        continue; // don't grant permission to the template owner, they implicitly have permission
                    }
                    LaunchPermissionVO existingPermission = _launchPermissionDao.findByTemplateAndAccount(templateId, permittedAccount.getId().longValue());
                    if (existingPermission == null) {
                        LaunchPermissionVO launchPermission = new LaunchPermissionVO(templateId, permittedAccount.getId().longValue());
                        _launchPermissionDao.persist(launchPermission);
                    }
                } else {
                    txn.rollback();
                    throw new InvalidParameterValueException("Unable to grant a launch permission to account " + accountName + ", account not found.  "
                            + "No permissions updated, please verify the account names and retry.");
                }
            }
            txn.commit();
        } else if ("remove".equalsIgnoreCase(operation)) {
            try {
                List<Long> accountIds = new ArrayList<Long>();
                for (String accountName : accountNames) {
                    Account permittedAccount = _accountDao.findActiveAccount(accountName, domainId);
                    if (permittedAccount != null) {
                        accountIds.add(permittedAccount.getId());
                    }
                }
                _launchPermissionDao.removePermissions(templateId, accountIds);
            } catch (VmopsRuntimeException ex) {
                throw new InternalErrorException("Internal error removing launch permissions for template " + template.getName());
            }
        } else if ("reset".equalsIgnoreCase(operation)) {
            // do we care whether the owning account is an admin? if the
            // owner is an admin, will we still set public to false?
            updatedTemplate = _templateDao.createForUpdate();
            updatedTemplate.setPublicTemplate(false);
            updatedTemplate.setFeatured(false);
            _templateDao.update(template.getId(), updatedTemplate);
            _launchPermissionDao.removeAllPermissions(templateId);
        }
        return true;
    }

    @Override
    public List<String> listTemplatePermissions(long templateId) {
        List<String> accountNames = new ArrayList<String>();

        List<LaunchPermissionVO> permissions = _launchPermissionDao.findByTemplate(templateId);
        if ((permissions != null) && !permissions.isEmpty()) {
            for (LaunchPermissionVO permission : permissions) {
                Account acct = _accountDao.findById(permission.getAccountId());
                accountNames.add(acct.getAccountName());
            }
        }
        return accountNames;
    }

    @Override
    public List<DiskOfferingVO> listDiskOfferingByInstanceId(Long instanceId) {
        if (instanceId == null) {
            return null;
        }

        return _diskOfferingDao.listByInstanceId(instanceId.longValue());
    }

    @Override
    public List<DiskOfferingVO> searchForDiskOfferings(Criteria c) {
        Filter searchFilter = new Filter(DiskOfferingVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchBuilder<DiskOfferingVO> sb = _diskOfferingDao.createSearchBuilder();

        // SearchBuilder and SearchCriteria are now flexible so that the search builder can be built with all possible
        // search terms and only those with criteria can be set.  The proper SQL should be generated as a result.
        Object name = c.getCriteria(Criteria.NAME);
        //Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);

        // FIXME:  disk offerings should search back up the hierarchy for available disk offerings...
        /*
        sb.addAnd("domainId", sb.entity().getDomainId(), SearchCriteria.Op.EQ);
        if (domainId != null) {
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.addAnd("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }
        */

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("name", "%" + keyword + "%");
        } else if (name != null) {
            sc.setParameters("name", "%" + name + "%");
        }

        if (id != null) {
            sc.setParameters("id", id);
        }

        // FIXME:  disk offerings should search back up the hierarchy for available disk offerings...
        /*
        if (domainId != null) {
            sc.setParameters("domainId", domainId);
            //
            //DomainVO domain = _domainDao.findById((Long)domainId);
            //
            // I want to join on user_vm.domain_id = domain.id where domain.path like 'foo%'
            //sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
            //
        }
        */

        return _diskOfferingDao.search(sc, searchFilter);
    }

    @Override
    public DiskOfferingVO createDiskOffering(long domainId, String name, String description, int numGibibytes, boolean mirrored) throws InvalidParameterValueException {
        if (numGibibytes < 1) {
            throw new InvalidParameterValueException("Please specify a disk size of at least 1 Gb.");
        } else if (numGibibytes > _maxVolumeSizeInGb) {
        	throw new InvalidParameterValueException("The maximum size for a disk is " + _maxVolumeSizeInGb + " Gb.");
        }

        return _configMgr.createDiskOffering(domainId, name, description, numGibibytes, mirrored);
    }

    @Override
    public void updateDiskOffering(long id, String name, String description) {
        // ServiceOfferingVO offering = new ServiceOfferingVO(id, name, cpu,
        // ramSize, speed, diskSpace, false, displayText);
        DiskOfferingVO offering = _diskOfferingDao.createForUpdate(id);
        offering.setName(name);
        offering.setDisplayText(description);
        _diskOfferingDao.update(id, offering);
    }

    @Override
    public boolean deleteDiskOffering(long id) {
        return _diskOfferingDao.remove(Long.valueOf(id));
    }

    @Override
    public AsyncJobResult queryAsyncJobResult(long jobId) throws PermissionDeniedException {
        AsyncJobVO job = _asyncMgr.getAsyncJob(jobId);
        if (job == null) {
            if (s_logger.isDebugEnabled())
                s_logger.debug("queryAsyncJobResult error: Permission denied, invalid job id " + jobId);

            throw new PermissionDeniedException("Permission denied, invalid job id " + jobId);
        }

        // treat any requests from API server as trusted requests
        if (!UserContext.current().isApiServer() && job.getAccountId() != UserContext.current().getAccountId()) {
            if (s_logger.isDebugEnabled())
                s_logger.debug("queryAsyncJobResult error: Permission denied, invalid ownership for job " + jobId);

            throw new PermissionDeniedException("Permission denied, invalid job ownership, job id: " + jobId);
        }
        return _asyncMgr.queryAsyncJobResult(jobId);
    }

    @Override
    public AsyncJobVO findInstancePendingAsyncJob(String instanceType, long instanceId) {
        return _asyncMgr.findInstancePendingAsyncJob(instanceType, instanceId);
    }

    @Override
    public AsyncJobVO findAsyncJobById(long jobId) {
        return _asyncMgr.getAsyncJob(jobId);
    }

    @Override
    public SecurityGroupVO createSecurityGroup(String name, String description, Long domainId, Long accountId) {
        SecurityGroupVO group = new SecurityGroupVO(name, description, domainId, accountId);
        Long id = _securityGroupDao.persist(group);
        return _securityGroupDao.findById(id);
    }

    @Override
    public boolean deleteSecurityGroup(Long accountId, long securityGroupId) throws PermissionDeniedException, InternalErrorException {
        SecurityGroupVO securityGroup = _securityGroupDao.findById(Long.valueOf(securityGroupId));
        if (securityGroup == null) {
            return true; // already deleted, return true
        }
        if (accountId != null) {
            Account account = _accountDao.findById(accountId);
            if (!BaseCmd.isAdmin(account.getType())
                    && ((securityGroup.getAccountId() == null) || (accountId.longValue() != securityGroup.getAccountId().longValue()))) {
                throw new PermissionDeniedException("Unable to delete security group " + securityGroup.getName() + ", not the owner");
            }
        }

        List<SecurityGroupVMMapVO> sgVmMappings = _securityGroupVMMapDao.listBySecurityGroup(securityGroupId);
        if ((sgVmMappings != null) && !sgVmMappings.isEmpty()) {
            throw new InternalErrorException("Unable to delete security group " + securityGroup.getName() + ", group is assigned to instances");
        }

        _networkRuleConfigDao.deleteBySecurityGroup(securityGroupId);
        return _securityGroupDao.remove(Long.valueOf(securityGroupId));
    }

    @Override
    public List<SecurityGroupVO> listSecurityGroups(Long accountId, Long domainId) {
        if (accountId != null) {
            Account acct = _accountDao.findById(accountId);
            domainId = acct.getDomainId();
        }
        return _securityGroupDao.listAvailableGroups(accountId, domainId);
    }

    @Override
    public List<SecurityGroupVO> searchForSecurityGroups(Criteria c) {
        Filter searchFilter = new Filter(SecurityGroupVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object accountId = c.getCriteria(Criteria.ACCOUNTID);
        Object name = c.getCriteria(Criteria.NAME);
        Object id = c.getCriteria(Criteria.ID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<SecurityGroupVO> sb = _securityGroupDao.createSearchBuilder();
        sb.and("name", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);

        if ((accountId == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        } else  if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, name + "%");
        }

        if (id != null) {
            sc.addAnd("id", SearchCriteria.Op.EQ, id);
        }

        if (accountId != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        return _securityGroupDao.search(sc, searchFilter);
    }

    @Override
    public List<SecurityGroupVO> searchForSecurityGroupsByVM(Criteria c) {
        Filter searchFilter = new Filter(SecurityGroupVMMapVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _securityGroupVMMapDao.createSearchCriteria();

        Object instanceId = c.getCriteria(Criteria.INSTANCEID);
        Object ipAddress = c.getCriteria(Criteria.ADDRESS);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (instanceId != null) {
            sc.addAnd("instanceId", SearchCriteria.Op.EQ, instanceId);
        }

        if (ipAddress != null) {
            sc.addAnd("ipAddress", SearchCriteria.Op.EQ, ipAddress);
        }

        List<SecurityGroupVO> securityGroups = new ArrayList<SecurityGroupVO>();
        List<SecurityGroupVMMapVO> sgVmMappings = _securityGroupVMMapDao.search(sc, searchFilter);
        if (sgVmMappings != null) {
            for (SecurityGroupVMMapVO sgVmMapping : sgVmMappings) {
                securityGroups.add(_securityGroupDao.findById(sgVmMapping.getSecurityGroupId()));
            }
        }
        return securityGroups;
    }

    @Override
    public boolean isSecurityGroupNameInUse(Long domainId, Long accountId, String name) {
        if (domainId == null) {
            domainId = DomainVO.ROOT_DOMAIN;
        }

        return _securityGroupDao.isNameInUse(accountId, domainId, name);
    }

    @Override
    public SecurityGroupVO findSecurityGroupById(Long groupId) {
        return _securityGroupDao.findById(groupId);
    }

    @Override
    public LoadBalancerVO findLoadBalancer(Long accountId, String name) {
        SearchCriteria sc = _loadBalancerDao.createSearchCriteria();
        sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        sc.addAnd("name", SearchCriteria.Op.EQ, name);
        List<LoadBalancerVO> loadBalancers = _loadBalancerDao.search(sc, null);
        if ((loadBalancers != null) && !loadBalancers.isEmpty()) {
            return loadBalancers.get(0);
        }
        return null;
    }

    @Override
    public LoadBalancerVO findLoadBalancerById(long loadBalancerId) {
        return _loadBalancerDao.findById(Long.valueOf(loadBalancerId));
    }

    @Override
    @DB
    public LoadBalancerVO createLoadBalancer(Long userId, Long accountId, String name, String description, String ipAddress, String publicPort, String privatePort, String algorithm)
            throws InvalidParameterValueException, PermissionDeniedException {
        if (accountId == null) {
            throw new InvalidParameterValueException("accountId not specified");
        }
        if (!NetUtils.isValidIp(ipAddress)) {
            throw new InvalidParameterValueException("invalid ip address");
        }
        if (!NetUtils.isValidPort(publicPort)) {
            throw new InvalidParameterValueException("publicPort is an invalid value");
        }
        if (!NetUtils.isValidPort(privatePort)) {
            throw new InvalidParameterValueException("privatePort is an invalid value");
        }
        if ((algorithm == null) || !NetUtils.isValidAlgorithm(algorithm)) {
            throw new InvalidParameterValueException("Invalid algorithm");
        }

        boolean locked = false;
        try {
            LoadBalancerVO exitingLB = _loadBalancerDao.findByIpAddressAndPublicPort(ipAddress, publicPort);
            if (exitingLB != null) {
                throw new InvalidParameterValueException("IP Address/public port already load balanced by an existing load balancer");
            }

            List<FirewallRuleVO> existingFwRules = _firewallRulesDao.listIPForwarding(ipAddress, publicPort, true);
            if ((existingFwRules != null) && !existingFwRules.isEmpty()) {
                long groupId = existingFwRules.get(0).getGroupId();
                SecurityGroupVO securityGroup = _securityGroupDao.findById(groupId);
                throw new InvalidParameterValueException("IP Address (" + ipAddress + ") and port (" + publicPort + ") already in used by security group "
                        + securityGroup.getName());
            }

            IPAddressVO addr = _publicIpAddressDao.acquire(ipAddress);

            if (addr == null) {
                throw new PermissionDeniedException("User does not own ip address " + ipAddress);
            }

            locked = true;
            if ((addr.getAllocated() == null) || !accountId.equals(addr.getAccountId())) {
                throw new PermissionDeniedException("User does not own ip address " + ipAddress);
            }

            LoadBalancerVO loadBalancer = new LoadBalancerVO(name, description, accountId.longValue(), ipAddress, publicPort, privatePort, algorithm);
            Long id = _loadBalancerDao.persist(loadBalancer);

            // Save off information for the event that the security group was
            // applied
            EventVO event = new EventVO();
            event.setUserId(userId);
            event.setAccountId(accountId);
            event.setType(EventTypes.EVENT_LOAD_BALANCER_CREATE);

            if (id == null) {
                event.setDescription("Failed to create load balancer " + loadBalancer.getName() + " on ip address " + ipAddress + "[" + publicPort + "->" + privatePort + "]");
                event.setLevel(EventVO.LEVEL_ERROR);
            } else {
                event.setDescription("Successfully created load balancer " + loadBalancer.getName() + " on ip address " + ipAddress + "[" + publicPort + "->" + privatePort + "]");
                String params = "id="+loadBalancer.getId()+"\ndcId="+addr.getDataCenterId();
                event.setParameters(params);
                event.setLevel(EventVO.LEVEL_INFO);
            }
            _eventDao.persist(event);

            return _loadBalancerDao.findById(id);
        } finally {
            if (locked) {
                _publicIpAddressDao.release(ipAddress);
            }
        }
    }

    @Override @DB
    public void assignToLoadBalancer(long userId, long loadBalancerId, List<Long> instanceIds) throws NetworkRuleConflictException, InternalErrorException,
            PermissionDeniedException, InvalidParameterValueException {
        Transaction txn = Transaction.currentTxn();
        try {
            List<FirewallRuleVO> firewallRulesToApply = new ArrayList<FirewallRuleVO>();
            long accountId = 0;
            DomainRouterVO router = null;

            LoadBalancerVO loadBalancer = _loadBalancerDao.findById(Long.valueOf(loadBalancerId));
            if (loadBalancer == null) {
                s_logger.warn("Unable to find load balancer with id " + loadBalancerId);
                return;
            }

            List<LoadBalancerVMMapVO> mappedInstances = _loadBalancerVMMapDao.listByLoadBalancerId(loadBalancerId);
            Set<Long> mappedInstanceIds = new HashSet<Long>();
            if (mappedInstances != null) {
                for (LoadBalancerVMMapVO mappedInstance : mappedInstances) {
                    mappedInstanceIds.add(Long.valueOf(mappedInstance.getInstanceId()));
                }
            }

            for (Long instanceId : instanceIds) {
                if (mappedInstanceIds.contains(instanceId)) {
                    continue;
                }

                UserVmVO userVm = _userVmDao.findById(instanceId);
                if (userVm == null) {
                    s_logger.warn("Unable to find virtual machine with id " + instanceId);
                    throw new InvalidParameterValueException("Unable to find virtual machine with id " + instanceId);
                }

                if (accountId == 0) {
                    accountId = userVm.getAccountId();
                } else if (accountId != userVm.getAccountId()) {
                    s_logger.warn("guest vm " + userVm.getName() + " (id:" + userVm.getId() + ") belongs to account " + userVm.getAccountId()
                            + ", previous vm in list belongs to account " + accountId);
                    throw new InvalidParameterValueException("guest vm " + userVm.getName() + " (id:" + userVm.getId() + ") belongs to account " + userVm.getAccountId()
                            + ", previous vm in list belongs to account " + accountId);
                }

                DomainRouterVO nextRouter = _routerDao.findById(userVm.getDomainRouterId());
                if (nextRouter == null) {
                    s_logger.warn("Unable to find router (" + userVm.getDomainRouterId() + ") for virtual machine with id " + instanceId);
                    throw new InvalidParameterValueException("Unable to find router (" + userVm.getDomainRouterId() + ") for virtual machine with id " + instanceId);
                }

                if (router == null) {
                    router = nextRouter;

                    // Make sure owner of router is owner of load balancer.  Since we are already checking that all VMs belong to the same router, by checking router
                    // ownership once we'll make sure all VMs belong to the owner of the load balancer.
                    if (router.getAccountId() != loadBalancer.getAccountId()) {
                        throw new InvalidParameterValueException("guest vm " + userVm.getName() + " (id:" + userVm.getId() + ") does not belong to the owner of load balancer " +
                                loadBalancer.getName() + " (owner is account id " + loadBalancer.getAccountId() + ")");
                    }
                } else if (router.getId().longValue() != nextRouter.getId().longValue()) {
                    throw new InvalidParameterValueException("guest vm " + userVm.getName() + " (id:" + userVm.getId() + ") belongs to router " + nextRouter.getName()
                            + ", previous vm in list belongs to router " + router.getName());
                }

                // check for ip address/port conflicts by checking exising
                // forwarding and loadbalancing rules
                String ipAddress = loadBalancer.getIpAddress();
                String privateIpAddress = userVm.getGuestIpAddress();
                List<FirewallRuleVO> existingRulesOnPubIp = _firewallRulesDao.listIPForwarding(ipAddress);

                if (existingRulesOnPubIp != null) {
                    for (FirewallRuleVO fwRule : existingRulesOnPubIp) {
                        if (!((fwRule.getGroupId() == loadBalancer.getId().longValue()) && (fwRule.isForwarding() == false))) {
                            // if the rule is not for the current load balancer,
                            // check to see if the private IP is our target IP,
                            // in which case we have a conflict
                            if (fwRule.getPublicPort().equals(loadBalancer.getPublicPort())) {
                                throw new NetworkRuleConflictException("An existing network rule for " + ipAddress + ":" + loadBalancer.getPublicPort()
                                        + " exists, found while trying to apply load balancer " + loadBalancer.getName() + " (id:" + loadBalancer.getId() + ") to instance "
                                        + userVm.getName() + ".");
                            }
                        } else if (fwRule.getPrivateIpAddress().equals(privateIpAddress) && fwRule.getPrivatePort().equals(loadBalancer.getPrivatePort()) && fwRule.isEnabled()) {
                            // for the current load balancer, don't add the same
                            // instance to the load balancer more than once
                            continue;
                        }
                    }
                }

                FirewallRuleVO newFwRule = new FirewallRuleVO();
                newFwRule.setAlgorithm(loadBalancer.getAlgorithm());
                newFwRule.setEnabled(true);
                newFwRule.setForwarding(false);
                newFwRule.setPrivatePort(loadBalancer.getPrivatePort());
                newFwRule.setPublicPort(loadBalancer.getPublicPort());
                newFwRule.setPublicIpAddress(loadBalancer.getIpAddress());
                newFwRule.setPrivateIpAddress(userVm.getGuestIpAddress());
                newFwRule.setGroupId(loadBalancer.getId());

                firewallRulesToApply.add(newFwRule);
            }

            // if there's no work to do, bail out early rather than
            // reconfiguring the proxy with the existing rules
            if (firewallRulesToApply.isEmpty()) {
                return;
            }

            List<FirewallRuleVO> lbRules = _firewallRulesDao.listByLoadBalancerId(loadBalancerId);
            firewallRulesToApply.addAll(lbRules);

            txn.start();

            List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(loadBalancer.getIpAddress(), firewallRulesToApply, router);

            // Save and create the event
            String description;
            String type = EventTypes.EVENT_NET_RULE_ADD;
            String ruleName = "load balancer";
            String level = EventVO.LEVEL_INFO;
            Account account = _accountDao.findById(accountId);

            if ((updatedRules != null) && (updatedRules.size() == firewallRulesToApply.size())) {
                // flag the instances as mapped to the load balancer
                for (Long instanceId : instanceIds) {
                    LoadBalancerVMMapVO loadBalancerMapping = new LoadBalancerVMMapVO(loadBalancerId, instanceId.longValue());
                    _loadBalancerVMMapDao.persist(loadBalancerMapping);
                }

                for (FirewallRuleVO updatedRule : updatedRules) {
                    if (updatedRule.getId() == null) {
                        _firewallRulesDao.persist(updatedRule);

                        description = "created new " + ruleName + " rule [" + updatedRule.getPublicIpAddress() + ":" + updatedRule.getPublicPort() + "]->["
                                + updatedRule.getPrivateIpAddress() + ":" + updatedRule.getPrivatePort() + "]" + " " + updatedRule.getProtocol();

                        saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                    }
                }
            } else {
                // remove the instanceIds from the load balancer since there was a failure
                _loadBalancerVMMapDao.remove(loadBalancerId, instanceIds);

                s_logger.warn("Failed to apply load balancer " + loadBalancer.getName() + " (id:" + loadBalancerId + ") to guest virtual machines "
                        + StringUtils.join(instanceIds, ","));
                throw new InternalErrorException("Failed to apply load balancer " + loadBalancer.getName() + " (id:" + loadBalancerId + ") to guest virtual machine "
                        + StringUtils.join(instanceIds, ","));
            }

            synchronized (_loadBalancerVMInProg) {
                // now that we are done adding instances (regardless of success/failure), remove them from the
                // in-memory map
                List<Long> applyingInstanceIds = _loadBalancerVMInProg.get(Long.valueOf(loadBalancerId));
                if (applyingInstanceIds != null) {
                    for (Long instanceId : instanceIds) {
                        applyingInstanceIds.remove(instanceId);
                    }
                    if (applyingInstanceIds.isEmpty()) {
                        _loadBalancerVMInProg.remove(Long.valueOf(loadBalancerId));
                    } else {
                        _loadBalancerVMInProg.put(Long.valueOf(loadBalancerId), applyingInstanceIds);
                    }
                }
            }

            txn.commit();
        } catch (Throwable e) {
            txn.rollback();
            if (e instanceof NetworkRuleConflictException) {
                throw (NetworkRuleConflictException) e;
            } else if (e instanceof InvalidParameterValueException) {
                throw (InvalidParameterValueException) e;
            } else if (e instanceof PermissionDeniedException) {
                throw (PermissionDeniedException) e;
            } else if (e instanceof InternalErrorException) {
                s_logger.warn("ManagementServer error", e);
                throw (InternalErrorException) e;
            }
            s_logger.warn("ManagementServer error", e);
        }
    }

    @Override
    public long assignToLoadBalancerAsync(long userId, long loadBalancerId, List<Long> instanceIds) {
        LoadBalancerVO loadBalancer = _loadBalancerDao.findById(loadBalancerId);
        IPAddressVO ipAddress = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
        DomainRouterVO router = _routerDao.findBy(loadBalancer.getAccountId(), ipAddress.getDataCenterId());

        synchronized (_loadBalancerVMInProg) {
            List<Long> currentInstanceIds = _loadBalancerVMInProg.get(Long.valueOf(loadBalancerId));
            if (currentInstanceIds == null) {
                currentInstanceIds = new ArrayList<Long>();
            }
            currentInstanceIds.addAll(instanceIds);
            _loadBalancerVMInProg.put(Long.valueOf(loadBalancerId), currentInstanceIds);
        }

        LoadBalancerParam param = new LoadBalancerParam(userId, router.getId(), loadBalancerId, instanceIds);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("AssignToLoadBalancer");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override @DB
    public boolean removeFromLoadBalancer(long userId, long loadBalancerId, List<Long> instanceIds) throws InvalidParameterValueException {
        Transaction txn = Transaction.currentTxn();
        boolean success = true;
        try {
            LoadBalancerVO loadBalancer = _loadBalancerDao.findById(loadBalancerId);
            if (loadBalancer == null) {
                return false;
            }

            IPAddressVO ipAddress = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
            if (ipAddress == null) {
                return false;
            }

            DomainRouterVO router = _routerDao.findBy(ipAddress.getAccountId(), ipAddress.getDataCenterId());
            if (router == null) {
                return false;
            }

            txn.start();
            for (Long instanceId : instanceIds) {
                UserVm userVm = _userVmDao.findById(instanceId);
                if (userVm == null) {
                    s_logger.warn("Unable to find virtual machine with id " + instanceId);
                    throw new InvalidParameterValueException("Unable to find virtual machine with id " + instanceId);
                }
                FirewallRuleVO fwRule = _firewallRulesDao.findByGroupAndPrivateIp(loadBalancerId, userVm.getGuestIpAddress(), false);
                if (fwRule != null) {
                    fwRule.setEnabled(false);
                    _firewallRulesDao.update(fwRule.getId(), fwRule);
                }
            }

            List<FirewallRuleVO> allLbRules = _firewallRulesDao.listByLoadBalancerId(loadBalancerId);
            List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(loadBalancer.getIpAddress(), allLbRules, router);

            // remove all the loadBalancer->VM mappings
            _loadBalancerVMMapDao.remove(loadBalancerId, instanceIds);

            // Save and create the event
            String description;
            String type = EventTypes.EVENT_NET_RULE_DELETE;
            String ruleName = "load balancer";
            String level = EventVO.LEVEL_INFO;
            Account account = _accountDao.findById(loadBalancer.getAccountId());

            for (FirewallRuleVO updatedRule : updatedRules) {
                if (!updatedRule.isEnabled()) {
                    _firewallRulesDao.remove(updatedRule.getId());

                    description = "deleted " + ruleName + " rule [" + updatedRule.getPublicIpAddress() + ":" + updatedRule.getPublicPort() + "]->["
                            + updatedRule.getPrivateIpAddress() + ":" + updatedRule.getPrivatePort() + "]" + " " + updatedRule.getProtocol();

                    saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                }
            }
            txn.commit();
        } catch (Exception ex) {
            s_logger.warn("Failed to delete load balancing rule with exception: " + ex.getMessage());
            success = false;
            txn.rollback();
        }
        return success;
    }

    @Override
    public long removeFromLoadBalancerAsync(long userId, long loadBalancerId, List<Long> instanceIds) {
        LoadBalancerVO loadBalancer = _loadBalancerDao.findById(loadBalancerId);
        IPAddressVO ipAddress = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
        DomainRouterVO router = _routerDao.findBy(loadBalancer.getAccountId(), ipAddress.getDataCenterId());
        LoadBalancerParam param = new LoadBalancerParam(userId, router.getId(), loadBalancerId, instanceIds);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RemoveFromLoadBalancer");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override @DB
    public boolean deleteLoadBalancer(long userId, long loadBalancerId) {
        Transaction txn = Transaction.currentTxn();
        LoadBalancerVO loadBalancer = null;
        try {
            loadBalancer = _loadBalancerDao.findById(loadBalancerId);
            if (loadBalancer == null) {
                return false;
            }

            IPAddressVO ipAddress = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
            if (ipAddress == null) {
                return false;
            }

            DomainRouterVO router = _routerDao.findBy(ipAddress.getAccountId(), ipAddress.getDataCenterId());
            List<FirewallRuleVO> fwRules = _firewallRulesDao.listByLoadBalancerId(loadBalancerId);

            txn.start();

            if ((fwRules != null) && !fwRules.isEmpty()) {
                for (FirewallRuleVO fwRule : fwRules) {
                    fwRule.setEnabled(false);
                    _firewallRulesDao.update(fwRule.getId(), fwRule);
                }

                List<FirewallRuleVO> updatedRules = _networkMgr.updateFirewallRules(loadBalancer.getIpAddress(), fwRules, router);

                // remove all loadBalancer->VM mappings
                _loadBalancerVMMapDao.remove(loadBalancerId);

                // Save and create the event
                String description;
                String type = EventTypes.EVENT_NET_RULE_DELETE;
                String ruleName = "load balancer";
                String level = EventVO.LEVEL_INFO;
                Account account = _accountDao.findById(loadBalancer.getAccountId());

                if ((updatedRules != null) && !updatedRules.isEmpty()) {
                    for (FirewallRuleVO updatedRule : updatedRules) {
                        _firewallRulesDao.remove(updatedRule.getId());

                        description = "deleted " + ruleName + " rule [" + updatedRule.getPublicIpAddress() + ":" + updatedRule.getPublicPort() + "]->["
                                + updatedRule.getPrivateIpAddress() + ":" + updatedRule.getPrivatePort() + "]" + " " + updatedRule.getProtocol();

                        saveEvent(userId, account.getId(), account.getDomainId(), level, type, description);
                    }
                }
            }

            txn.commit();
        } catch (Exception ex) {
            txn.rollback();
            s_logger.error("Unexpected exception deleting load balancer " + loadBalancerId, ex);
            return false;
        }
        boolean success = _loadBalancerDao.remove(loadBalancerId);

        // save off an event for removing the security group
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(loadBalancer.getAccountId());
        event.setType(EventTypes.EVENT_LOAD_BALANCER_DELETE);
        if (success) {
            event.setLevel(EventVO.LEVEL_INFO);
            String params = "id="+loadBalancer.getId();
            event.setParameters(params);
            event.setDescription("Successfully deleted load balancer " + loadBalancer.getName() + " (id:" + loadBalancer.getId() + ")");
        } else {
            event.setLevel(EventVO.LEVEL_ERROR);
            event.setDescription("Failed to delete load balancer " + loadBalancer.getName() + " (id:" + loadBalancer.getId() + ")");
        }
        _eventDao.persist(event);
        return success;
    }

    @Override
    public long deleteLoadBalancerAsync(long userId, long loadBalancerId) {
        LoadBalancerVO loadBalancer = _loadBalancerDao.findById(loadBalancerId);
        IPAddressVO ipAddress = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
        DomainRouterVO router = _routerDao.findBy(loadBalancer.getAccountId(), ipAddress.getDataCenterId());
        LoadBalancerParam param = new LoadBalancerParam(userId, router.getId(), loadBalancerId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
    	job.setUserId(UserContext.current().getUserId());
    	job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DeleteLoadBalancer");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    @Override
    public List<UserVmVO> listLoadBalancerInstances(long loadBalancerId, boolean applied) {
        LoadBalancerVO loadBalancer = _loadBalancerDao.findById(loadBalancerId);
        if (loadBalancer == null) {
            return null;
        }

        List<UserVmVO> loadBalancerInstances = new ArrayList<UserVmVO>();
        synchronized (_loadBalancerVMInProg) {
            List<Long> appliedInstanceIdList = _loadBalancerDao.listInstancesByLoadBalancer(loadBalancerId);
            List<Long> applyingInstanceIds = _loadBalancerVMInProg.get(Long.valueOf(loadBalancerId));
            if (applyingInstanceIds != null) {
                for (Long applyingInstanceId : applyingInstanceIds) {
                    appliedInstanceIdList.add(applyingInstanceId);
                }
            }

            IPAddressVO addr = _publicIpAddressDao.findById(loadBalancer.getIpAddress());
            List<UserVmVO> userVms = _userVmDao.listByAccountAndDataCenter(loadBalancer.getAccountId(), addr.getDataCenterId());

            for (UserVmVO userVm : userVms) {
                // if the VM is destroyed, being expunged, in an error state, or in an unknown state, skip it
                switch (userVm.getState()) {
                case Destroyed:
                case Expunging:
                case Error:
                case Unknown:
                    continue;
                }

                boolean isApplied = appliedInstanceIdList.contains(userVm.getId());
                if (!applied && !isApplied) {
                    loadBalancerInstances.add(userVm);
                } else if (applied && isApplied) {
                    loadBalancerInstances.add(userVm);
                }
            }
        }

        return loadBalancerInstances;
    }

    @Override
    public List<LoadBalancerVO> searchForLoadBalancers(Criteria c) {
        Filter searchFilter = new Filter(LoadBalancerVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());

        Object accountId = c.getCriteria(Criteria.ACCOUNTID);
        Object id = c.getCriteria(Criteria.ID);
        Object name = c.getCriteria(Criteria.NAME);
        Object domainId = c.getCriteria(Criteria.DOMAINID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        SearchBuilder<LoadBalancerVO> sb = _loadBalancerDao.createSearchBuilder();
        sb.and("nameLIKE", sb.entity().getName(), SearchCriteria.Op.LIKE);
        sb.and("id", sb.entity().getId(), SearchCriteria.Op.EQ);
        sb.and("nameEQ", sb.entity().getName(), SearchCriteria.Op.EQ);
        sb.and("accountId", sb.entity().getAccountId(), SearchCriteria.Op.EQ);

        if ((accountId == null) && (domainId != null)) {
            // if accountId isn't specified, we can do a domain match for the admin case
            SearchBuilder<DomainVO> domainSearch = _domainDao.createSearchBuilder();
            domainSearch.and("path", domainSearch.entity().getPath(), SearchCriteria.Op.LIKE);
            sb.join("domainSearch", domainSearch, sb.entity().getDomainId(), domainSearch.entity().getId());
        }

        SearchCriteria sc = sb.create();
        if (keyword != null) {
            sc.setParameters("nameLIKE", "%" + keyword + "%");
        } else if (name != null) {
            sc.setParameters("nameEQ", name);
        }

        if (id != null) {
            sc.setParameters("id", id);
        }

        if (accountId != null) {
            sc.setParameters("accountId", accountId);
        } else if (domainId != null) {
            DomainVO domain = _domainDao.findById((Long)domainId);
            sc.setJoinParameters("domainSearch", "path", domain.getPath() + "%");
        }

        return _loadBalancerDao.search(sc, searchFilter);
    }

    @Override
    public String[] getApiConfig() {
        return new String[] { "commands.properties" };
    }

    protected class AccountCleanupTask implements Runnable {
        @Override
        public void run() {
            try {
                GlobalLock lock = GlobalLock.getInternLock("AccountCleanup");
                if (lock == null) {
                    s_logger.debug("Couldn't get the global lock");
                    return;
                }

                if (!lock.lock(30)) {
                    s_logger.debug("Couldn't lock the db");
                    return;
                }

                try {
                    List<AccountVO> accounts = _accountDao.findCleanups();
                    s_logger.info("Found " + accounts.size() + " accounts to cleanup");
                    for (AccountVO account : accounts) {
                        s_logger.debug("Cleaning up " + account.getId());
                        try {
                            deleteAccount(account);
                        } catch (Exception e) {
                            s_logger.error("Skipping due to error on account " + account.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    s_logger.error("Exception ", e);
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                s_logger.error("Exception ", e);
            }
        }
    }

    @Override
    public StoragePoolVO findPoolById(Long id) {
        return _poolDao.findById(id);
    }

    @Override
    public StoragePoolVO addPool(Long zoneId, Long podId, String poolName, String storageUri) throws ResourceInUseException, URISyntaxException, IllegalArgumentException {
        URI uri = new URI(storageUri);
        return _storageMgr.createPool(zoneId.longValue(), podId.longValue(), poolName, uri);
    }

    @Override
    public List<? extends StoragePoolVO> searchForStoragePools(Criteria c) {
        Filter searchFilter = new Filter(StoragePoolVO.class, c.getOrderBy(), c.getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _poolDao.createSearchCriteria();

        Object name = c.getCriteria(Criteria.NAME);
        Object host = c.getCriteria(Criteria.HOST);
        Object path = c.getCriteria(Criteria.PATH);
        Object zone = c.getCriteria(Criteria.DATACENTERID);
        Object keyword = c.getCriteria(Criteria.KEYWORD);

        if (keyword != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + keyword + "%");
        }

        if (name != null) {
            sc.addAnd("name", SearchCriteria.Op.LIKE, "%" + name + "%");
        }
        if (host != null) {
            sc.addAnd("host", SearchCriteria.Op.EQ, host);
        }
        if (path != null) {
            sc.addAnd("path", SearchCriteria.Op.EQ, path);
        }
        if (zone != null) {
            sc.addAnd("dataCenterId", SearchCriteria.Op.EQ, zone);
        }

        return _poolDao.search(sc, searchFilter);
    }

    @Override
    public StorageStats getStoragePoolStatistics(long id) {
        return _statsCollector.getStoragePoolStats(id);
    }

    @Override
    public List<AsyncJobVO> searchForAsyncJobs(Criteria c) {
        Filter searchFilter = new Filter(AsyncJobVO.class, c.getOrderBy(), c
                .getAscending(), c.getOffset(), c.getLimit());
        SearchCriteria sc = _jobDao.createSearchCriteria();

        Object accountId = c.getCriteria(Criteria.ACCOUNTID);
        Object userId = c.getCriteria(Criteria.USERID);
        Object status = c.getCriteria(Criteria.STATUS);
        Object keyword = c.getCriteria(Criteria.KEYWORD);
        
        if (keyword != null) {
            sc.addAnd("cmd", SearchCriteria.Op.LIKE, "%" + keyword+ "%");
        }

        if (accountId != null) {
            sc.addAnd("accountId", SearchCriteria.Op.EQ, accountId);
        }

        if (userId != null) {
            sc.addAnd("userId", SearchCriteria.Op.EQ, userId);
        }
        
        if(status != null) {
            sc.addAnd("status", SearchCriteria.Op.EQ, status);
        }
        
        return _jobDao.search(sc, searchFilter);
    }

    @Override
    public SnapshotPolicyVO createSnapshotPolicy(long volumeId, String schedule, String intervalType, int maxSnaps) throws InvalidParameterValueException {
    	IntervalType type =  DateUtil.getIntervalType(intervalType);
    	if(type == null){
    		throw new InvalidParameterValueException("Unsupported interval type " + intervalType);
    	}
    	try {
    		DateUtil.getNextRunTime(type, schedule, null);
    	} catch (Exception e){
    		throw new InvalidParameterValueException("Invalid schedule: "+ schedule +" for interval type: " + intervalType);
    	}
    	int intervalMaxSnaps = _snapMgr.getIntervalMaxSnaps(type); 
    	if(maxSnaps > intervalMaxSnaps){
    		throw new InvalidParameterValueException("maxSnaps exceeds limit: "+ intervalMaxSnaps +" for interval type: " + intervalType);
    	}
    	if(_snapMgr.getPolicyForVolumeByInterval(volumeId, (short)type.ordinal()) != null){
    	    throw new InvalidParameterValueException("Policy with interval type: " + intervalType+" already exists for volume: "+volumeId);
    	}
    	return _snapMgr.createPolicy(volumeId, schedule, (short)type.ordinal() , maxSnaps);
    }    

	@Override
	public boolean deleteSnapshotPolicies(long userId, List<Long> policyIds) {
		boolean result = true;
		for (long policyId : policyIds) {
			if (!_snapMgr.deletePolicy(policyId)) {
				result = false;
				s_logger.warn("Failed to delete snapshot policy with Id: " + policyId);
			}
		}
		return result;
	}

	@Override
	public String getSnapshotIntervalTypes(long snapshotId){
	    String intervalTypes = "";
	    List<SnapshotPolicyVO> policies = _snapMgr.listPoliciesforSnapshot(snapshotId);
	    for (SnapshotPolicyVO policy : policies){
	        if(!intervalTypes.isEmpty()){
	            intervalTypes += ",";
	        }
	        if(policy.getId() == SnapshotManager.MANUAL_POLICY_ID){
	            intervalTypes+= "MANUAL";
	        }
	        else {
	            intervalTypes += DateUtil.getIntervalType(policy.getInterval()).toString();
	        }
	    }
	    return intervalTypes;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SnapshotScheduleVO> findRecurringSnapshotSchedule(Long volumeId, Long policyId) {
	    return _snapMgr.findRecurringSnapshotSchedule(volumeId, policyId);
	}

	@Override
	public List<SnapshotPolicyVO> listSnapshotPolicies(long volumeId) {
    	if( _volumeDao.findById(volumeId) == null){
    		return null;
    	}
		return _snapMgr.listPoliciesforVolume(volumeId);
	}
    
    @Override
    public boolean isChildDomain(Long parentId, Long childId) {
        return _domainDao.isChildDomain(parentId, childId);
    }
    
    public SecondaryStorageVmVO startSecondaryStorageVm(long instanceId) throws InternalErrorException {
        return _secStorageVmMgr.startSecStorageVm(instanceId);
    }

    public boolean stopSecondaryStorageVm(long instanceId) {
        return _secStorageVmMgr.stopSecStorageVm(instanceId);
    }

    public boolean rebootSecondaryStorageVm(long instanceId) {
        return _secStorageVmMgr.rebootSecStorageVm(instanceId);
    }

    public boolean destroySecondaryStorageVm(long instanceId) {
        return _secStorageVmMgr.destroySecStorageVm(instanceId);
    }

    public long startSecondaryStorageVmAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StartSecondaryStorageVm");
        job.setCmdInfo(gson.toJson(param));
        //job.setCmdOriginator(StartSecondaryStorageVmCmd.getResultObjectName()); TODO
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long stopSecondaryStorageVmAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("StopSecondaryStorageVm");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long rebootSecondaryStorageVmAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("RebootSecondaryStorageVm");
        job.setCmdInfo(gson.toJson(param));
        
        return _asyncMgr.submitAsyncJob(job, true);
    }

    public long destroySecondaryStorageVmAsync(long instanceId) {
        VMOperationParam param = new VMOperationParam(0, instanceId, null);
        Gson gson = GsonHelper.getBuilder().create();

        AsyncJobVO job = new AsyncJobVO();
        job.setUserId(UserContext.current().getUserId());
        job.setAccountId(UserContext.current().getAccountId());
        job.setCmd("DestroySecondaryStorageVm");
        job.setCmdInfo(gson.toJson(param));
        return _asyncMgr.submitAsyncJob(job);

    }
}

