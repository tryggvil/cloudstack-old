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

package com.vmops.agent.manager.allocator.impl;

import java.util.Set;

import javax.ejb.Local;

import org.apache.log4j.NDC;

import com.vmops.agent.manager.allocator.HostAllocator;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;

@Local(value={HostAllocator.class})
public class FirstFitRoutingAllocator extends FirstFitAllocator {
    @Override
    public Host allocateTo(ServiceOffering offering, DiskOfferingVO diskOffering, Host.Type type, DataCenterVO dc, HostPodVO pod,
    		StoragePoolVO sp, VMTemplateVO template, Set<Host> avoid) {
        try {
            NDC.push("FirstFitRoutingAllocator");
            if (type != Host.Type.Routing) {
                // FirstFitRoutingAllocator is to find space on routing capable hosts only
                return null;
            }
            //all hosts should be of type routing anyway.
            return super.allocateTo(offering, diskOffering, type, dc, pod, sp, template, avoid);
        } finally {
            NDC.pop();
        }
    }
}
