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

package com.vmops.async;

import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import com.vmops.agent.AgentManager;
import com.vmops.async.dao.AsyncJobDao;
import com.vmops.event.dao.EventDao;
import com.vmops.network.NetworkManager;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.server.ManagementServer;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.user.AccountManager;
import com.vmops.user.dao.AccountDao;
import com.vmops.user.dao.UserDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;

@Local(value={AsyncJobExecutorContext.class})
public class AsyncJobExecutorContextImpl implements AsyncJobExecutorContext {
	private String _name;
	
    private AgentManager _agentMgr;
	private NetworkManager _networkMgr;
	private UserVmManager _vmMgr;
    private SnapshotManager _snapMgr;
	private AccountManager _accountMgr;
    private EventDao _eventDao;
    private UserVmDao _vmDao;
    private AccountDao _accountDao;
    private VolumeDao _volumeDao;
    private DomainRouterDao _routerDao;
    private IPAddressDao _ipAddressDao;
    private AsyncJobDao _jobDao;
    private UserDao _userDao;
    
    private ManagementServer _managementServer;
    
	@Override
	public ManagementServer getManagementServer() {
		return _managementServer;
	}

	@Override
	public AgentManager getAgentMgr() {
		return _agentMgr;
	}
	
	@Override
	public NetworkManager getNetworkMgr() {
		return _networkMgr;
	}
	
	@Override
	public UserVmManager getVmMgr() {
		return _vmMgr;
	}
	
	/**server/src/com/vmops/async/AsyncJobExecutorContext.java
     * @return the _snapMgr
     */
	@Override
    public SnapshotManager getSnapshotMgr() {
        return _snapMgr;
    }

    @Override
	public AccountManager getAccountMgr() {
		return _accountMgr;
	}
	
	@Override
	public EventDao getEventDao() {
		return _eventDao;
	}
	
	@Override
	public UserVmDao getVmDao() {
		return _vmDao;
	}
	
	@Override
	public AccountDao getAccountDao() {
		return _accountDao;
	}
	
	@Override
	public VolumeDao getVolumeDao() {
		return _volumeDao;
	}

	@Override
    public DomainRouterDao getRouterDao() {
		return _routerDao;
	}
	
	@Override
    public IPAddressDao getIpAddressDao() {
    	return _ipAddressDao;
    }
	
	@Override
    public AsyncJobDao getJobDao() {
    	return _jobDao;
    }
	
	@Override
    public UserDao getUserDao() {
    	return _userDao;
    }
	
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
    	_name = name;
		ComponentLocator locator = ComponentLocator.getCurrentLocator();
		
		_managementServer = (ManagementServer)ComponentLocator.getComponent("management-server");
        if (_managementServer == null) { 
            throw new ConfigurationException("unable to get " + ManagementServer.class.getName());
        }

        _agentMgr = locator.getManager(AgentManager.class);
        if (_agentMgr == null) {
            throw new ConfigurationException("unable to get " + AgentManager.class.getName());
        }
        
        _networkMgr = locator.getManager(NetworkManager.class);
        if (_networkMgr == null) {
            throw new ConfigurationException("unable to get " + NetworkManager.class.getName());
        }
        
        _vmMgr = locator.getManager(UserVmManager.class);
        if (_vmMgr == null) {
            throw new ConfigurationException("unable to get " + UserVmManager.class.getName());
        }
        
        _snapMgr = locator.getManager(SnapshotManager.class);
        if (_snapMgr == null) {
            throw new ConfigurationException("unable to get " + SnapshotManager.class.getName());
        }
        
        _accountMgr = locator.getManager(AccountManager.class);
        if (_accountMgr == null) {
            throw new ConfigurationException("unable to get " + AccountManager.class.getName());
        }
        
        _eventDao = locator.getDao(EventDao.class);
        if (_eventDao == null) {
            throw new ConfigurationException("unable to get " + EventDao.class.getName());
        }
        
        _vmDao = locator.getDao(UserVmDao.class);
        if (_vmDao == null) {
            throw new ConfigurationException("unable to get " + UserVmDao.class.getName());
        }
        
        _accountDao = locator.getDao(AccountDao.class);
        if (_accountDao == null) {
            throw new ConfigurationException("unable to get " + AccountDao.class.getName());
        }
        
        _volumeDao = locator.getDao(VolumeDao.class);
        if (_volumeDao == null) {
            throw new ConfigurationException("unable to get " + VolumeDao.class.getName());
        }
        
        _routerDao = locator.getDao(DomainRouterDao.class);
        if (_routerDao == null) {
            throw new ConfigurationException("unable to get " + DomainRouterDao.class.getName());
        }
        
        _ipAddressDao = locator.getDao(IPAddressDao.class);
        if (_ipAddressDao == null) {
            throw new ConfigurationException("unable to get " + IPAddressDao.class.getName());
        }
        
        _jobDao = locator.getDao(AsyncJobDao.class);
        if(_jobDao == null) {
            throw new ConfigurationException("unable to get " + AsyncJobDao.class.getName());
        }
        
        _userDao = locator.getDao(UserDao.class);
        if(_userDao == null) {
            throw new ConfigurationException("unable to get " + UserDao.class.getName());
        }
        
    	return true;
    }
	
    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
    
    @Override
    public String getName() {
    	return _name;
    }
}
