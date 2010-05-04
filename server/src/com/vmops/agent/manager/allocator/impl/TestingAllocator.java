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

import java.util.Map;
import java.util.Set;

import javax.ejb.Local;

import com.vmops.agent.manager.allocator.HostAllocator;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.host.Host;
import com.vmops.host.dao.HostDao;
import com.vmops.service.ServiceOffering;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.vm.UserVm;

/**
 * @author ahuang
 *
 */
@Local(value={HostAllocator.class})
public class TestingAllocator implements HostAllocator {
    HostDao _hostDao;
    Long _computingHost;
    Long _storageHost;
    Long _routingHost;
    String _name;

    @Override
    public Host allocateTo(ServiceOffering offering, DiskOfferingVO diskOffering, Host.Type type, DataCenterVO dc, HostPodVO pod,
    		StoragePoolVO sp, VMTemplateVO template, Set<Host> avoid) {
        if (type == Host.Type.Computing && _computingHost != null) {
            return _hostDao.findById(_computingHost);
        } else if (type == Host.Type.Computing && _routingHost != null) {
            return _hostDao.findById(_routingHost);
        } else if (type == Host.Type.Storage && _storageHost != null) {
            return _hostDao.findById(_storageHost);
        }
        return null;
    }

    @Override
    public boolean isVirtualMachineUpgradable(UserVm vm, ServiceOffering offering) {
        // currently we do no special checks to rule out a VM being upgradable to an offering, so
        // return true
        return true;
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) {
        String value = (String)params.get(Host.Type.Computing.toString());
        _computingHost = (value != null) ? Long.parseLong(value) : null;

        value = (String)params.get(Host.Type.Routing.toString());
        _routingHost = (value != null) ? Long.parseLong(value) : null;

        value = (String)params.get(Host.Type.Storage.toString());
        _storageHost = (value != null) ? Long.parseLong(value) : null;
        
        ComponentLocator _locator = ComponentLocator.getCurrentLocator();
        _hostDao = _locator.getDao(HostDao.class);
        
        _name = name;
        
        return true;
    }

    @Override
    public String getName() {
        return _name;
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

}
