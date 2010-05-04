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

package com.vmops.async.executor;

import com.vmops.api.BaseCmd;
import com.vmops.server.ManagementServer;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.user.Account;
import com.vmops.vm.UserVmVO;

public class VMExecutorHelper {
	public static VMOperationResultObject composeResultObject(ManagementServer managementServer, UserVmVO vm) {
		
		VMOperationResultObject resultObject = new VMOperationResultObject();
		
		resultObject.setId(vm.getId());
		resultObject.setName(vm.getName());
		resultObject.setCreated(vm.getCreated());
		resultObject.setZoneId(vm.getDataCenterId());
		resultObject.setZoneName(managementServer.findDataCenterById(vm.getDataCenterId()).getName());
		resultObject.setIpAddress(vm.getPrivateIpAddress());
		resultObject.setServiceOfferingId(vm.getServiceOfferingId());
		resultObject.setHaEnabled(vm.isHaEnabled());
		if (vm.getDisplayName() == null || vm.getDisplayName().length() == 0) {
			resultObject.setDisplayName(vm.getName());
		}
		else {
			resultObject.setDisplayName(vm.getDisplayName());
		}
		
		if (vm.getGroup() != null) {
			resultObject.setGroup(vm.getGroup());
		}
		
		if(vm.getState() != null)
			resultObject.setState(vm.getState().toString());
		
        VMTemplateVO template = managementServer.findTemplateById(vm.getTemplateId());
        
        Account acct = managementServer.findAccountById(Long.valueOf(vm.getAccountId()));
        if (acct != null) {
        	resultObject.setAccount(acct.getAccountName());
        	resultObject.setDomainId(acct.getDomainId());
        	resultObject.setDomain(managementServer.findDomainIdById(acct.getDomainId()).getName());
        }
        
        if ( BaseCmd.isAdmin(acct.getType()) && (vm.getHostId() != null)) {
        	resultObject.setHostname(managementServer.getHostBy(vm.getHostId()).getName());
        	resultObject.setHostid(vm.getHostId());
        }
        
        String templateName = "ISO Boot";
        boolean templatePasswordEnabled = false;
        String templateDisplayText = "ISO Boot";
        
        if (template != null) {
        	templateName = template.getName();
        	templatePasswordEnabled = template.getEnablePassword();
        	templateDisplayText = template.getDisplayText();
        	 if (templateDisplayText == null) {
             	templateDisplayText = templateName;
             }
        }
       
        resultObject.setTemplateId(vm.getTemplateId());
        resultObject.setTemplateName(templateName);
        resultObject.setTemplateDisplayText(templateDisplayText);
        resultObject.setPasswordEnabled(templatePasswordEnabled);
        
        String isoName = null;
        if (vm.getIsoId() != null) {
            VMTemplateVO iso = managementServer.findTemplateById(vm.getIsoId().longValue());
            if (iso != null) {
            	isoName = iso.getName();
            }
        }
        
        resultObject.setIsoId(vm.getIsoId());
        resultObject.setIsoName(isoName);
        
        
        ServiceOfferingVO offering = managementServer.findServiceOfferingById(vm.getServiceOfferingId());
        resultObject.setServiceOfferingId(vm.getServiceOfferingId());
        resultObject.setServiceOfferingName(offering.getName());

        resultObject.setCpuNumber(String.valueOf(offering.getCpu()));
        resultObject.setCpuSpeed(String.valueOf(offering.getSpeed()));
        resultObject.setMemory(String.valueOf(offering.getRamSize()));
        
		return resultObject;
	}
}
