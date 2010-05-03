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
package com.vmops.vm.dao;

import java.util.List;


import com.vmops.utils.db.GenericDao;
import com.vmops.vm.SecondaryStorageVmVO;
import com.vmops.vm.State;
import com.vmops.vm.VirtualMachine;

public interface SecondaryStorageVmDao extends GenericDao<SecondaryStorageVmVO, Long> {
	
    public List<SecondaryStorageVmVO> getSecStorageVmListInStates(long dataCenterId, State... states);
    public List<SecondaryStorageVmVO> getSecStorageVmListInStates(State... states);
    
    public List<SecondaryStorageVmVO> listByHostId(long hostId);
    

    public List<Long> getRunningSecStorageVmListByMsid(long msid);
    
    public boolean updateIf(SecondaryStorageVmVO vm, VirtualMachine.Event event, Long hostId);
}
