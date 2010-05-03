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

package com.vmops.async;

import com.vmops.agent.AgentManager;
import com.vmops.event.dao.EventDao;
import com.vmops.network.NetworkManager;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.server.ManagementServer;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.storage.snapshot.SnapshotManager;
import com.vmops.user.AccountManager;
import com.vmops.user.dao.AccountDao;
import com.vmops.utils.component.Manager;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.dao.DomainRouterDao;
import com.vmops.vm.dao.UserVmDao;

public interface AsyncJobExecutorContext extends Manager {
	public ManagementServer getManagementServer();
	public AgentManager getAgentMgr();
	public NetworkManager getNetworkMgr();
	public UserVmManager getVmMgr();
	public SnapshotManager getSnapshotMgr();
	public AccountManager getAccountMgr();
	public EventDao getEventDao();
	public UserVmDao getVmDao();
	public AccountDao getAccountDao();
	public VolumeDao getVolumeDao();
    public DomainRouterDao getRouterDao();
    public IPAddressDao getIpAddressDao();
}
