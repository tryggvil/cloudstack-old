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
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;

/*
 * Data Access Object for vm_instance table
 */
public interface VMInstanceDao extends GenericDao<VMInstanceVO, Long> {
    /**
     * What are the vms running on this host?
     * @param hostId host.
     * @return list of VMInstanceVO running on that host.
     */
	List<VMInstanceVO> listByHostId(long hostId);
	
	/**
	 * List VMs by zone ID
	 * @param zoneId
	 * @return list of VMInstanceVO in the specified zone
	 */
	List<VMInstanceVO> listByZoneId(long zoneId);
	
	/**
	 * List VMs by pool ID and template ID
	 * @param poolId
	 * @param templateId
	 * @return list of VMInstanceVO in the specified pool that use the specified template
	 */
	List<VMInstanceVO> listByPoolAndTemplateActive(long poolId, long templateId);
	
	/**
	 * List VMs by pool ID and ISO ID
	 * @param poolId
	 * @param isoId
	 * @return list of VMInstanceVO in the specified pool that use the specified ISO
	 */
	public List<VMInstanceVO> listByPoolAndISOActive(long poolId, long isoId);
   
    /**
	 * Updates display name and group for vm; enables/disables ha
	 * @param id vm id.
	 * @param displan name, group and enable for ha
	 */
    void updateVM(long id, String displayName, String group, boolean enalbe);
    
    boolean updateIf(VMInstanceVO vm, VirtualMachine.Event event, Long hostId);
    
    /**
     * Find vm instance with names like.
     * 
     * @param name name that fits SQL like.
     * @return list of VMInstanceVO
     */
    List<VMInstanceVO> findVMInstancesLike(String name);
    
    List<VMInstanceVO> findVMInTransition(Date time, State... states);

    /**
     * return the counts of domain routers and console proxies running on the host
     * @param hostId
     * @return
     */
    Integer[] countRoutersAndProxies(Long hostId);
}
