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
package com.vmops.agent.manager.allocator;

import java.util.Set;

import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StoragePool;
import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.component.Adapter;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.VirtualMachine;

public interface StoragePoolAllocator extends Adapter {
	
	StoragePool allocateToPool(ServiceOffering offering, DiskOfferingVO dataDiskOffering, DataCenterVO dc, HostPodVO pod, VMInstanceVO vm, VMTemplateVO template, DiskOfferingVO rootDiskOffering, Set<? extends StoragePool> avoid);
	
	String chooseStorageIp(VirtualMachine vm, Host host, Host storage);
}
