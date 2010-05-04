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
package com.vmops.vm.dao;

import java.util.Date;
import java.util.List;

import com.vmops.utils.db.GenericDao;
import com.vmops.vm.State;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VirtualMachine;

public interface UserVmDao extends GenericDao<UserVmVO, Long> {
    List<UserVmVO> listByAccountId(long id);

    List<UserVmVO> listByAccountAndDataCenter(long accountId, long dcId);
    List<UserVmVO> listByHostId(Long hostId);

    /**
     * Find vms under the same router in the state.
     * @param routerId id of the router.
     * @param state state that it's in.
     * @return list of userVmVO
     */
    List<UserVmVO> listBy(long routerId, State... state);

    UserVmVO findByName(String name);

    void updateProxyId(long id, Long proxyId, Date time);
    
    /**
     * This method is of supreme importance in the management of VMs.  It updates a uservm if and only if
     * the following condition are true.  If the update is complete, all changes to the uservm entity
     * are persisted.  The state is also changed to the new state.
     * 
     * 1. There's a transition from the current state via the event to a new state.
     * 2. The db has not changed on the current state, update time, and host id sent.
     * 
     * @param vm vm object to persist.
     * @param event
     * @param hostId
     * @return true if updated, false if not.
     */
    boolean updateIf(UserVmVO vm, VirtualMachine.Event event, Long hostId);
    
    List<UserVmVO> findDestroyedVms(Date date);

    /**
     * Find all vms that are running and using this ip address.
     * @param dcId datacenter id
     * @param podId pod id
     * @param ipAddress ip address of the storage server.
     */
    List<UserVmVO> findVMsUsingIpAddress(long dcId, long podId, String ipAddress);

	/**
	 * Find all vms that use a domain router
	 * @param routerId
	 * @return
	 */
	List<UserVmVO> listByRouterId(long routerId);
	
	/**
	 * List running VMs on the specified host
	 * @param id
	 * @return
	 */
	public List<UserVmVO> listRunningByHostId(long hostId);
}
