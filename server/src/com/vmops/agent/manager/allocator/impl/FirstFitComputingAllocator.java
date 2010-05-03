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

package com.vmops.agent.manager.allocator.impl;

import java.util.List;
import java.util.Set;

import javax.ejb.Local;

import org.apache.log4j.NDC;

import com.vmops.agent.manager.allocator.HostAllocator;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.VMTemplateVO;

/**
 * An allocator that tries to find a fit on a computing host that specifically does *not* support routing.
 * @author kris
 *
 */
@Local(value={HostAllocator.class})
public class FirstFitComputingAllocator extends FirstFitAllocator {
    public Host allocateTo(ServiceOffering offering, Host.Type type, DataCenterVO dc, HostPodVO pod, VMTemplateVO template, Set<Host> avoid) {
        try {
            NDC.push("FirstFitComputingAllocator");
            if (type != Host.Type.Computing) {
                // FirstFitComputingAllocator should be used for user VMs and look for hosts specifically *not* capable of being routers
                return null;
            }

            // return *only* computing
            List<HostVO> hosts = _hostDao.listBy(type, dc.getId(), pod.getId());

            return allocateTo(offering, template, avoid, hosts);
        } finally {
            NDC.pop();
        }
    }
}
