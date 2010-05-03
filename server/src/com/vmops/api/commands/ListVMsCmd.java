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

package com.vmops.api.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.async.AsyncJobVO;
import com.vmops.domain.DomainVO;
import com.vmops.host.HostVO;
import com.vmops.server.Criteria;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;
import com.vmops.vm.UserVm;

public class ListVMsCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListVMsCmd.class.getName());

    private static final String s_name = "listvirtualmachinesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.STATE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.POD_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.HOST_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGESIZE, Boolean.FALSE));
    }

    @Override
	public String getName() {
        return s_name;
    }
    @Override
	public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
    	Long id = (Long)params.get(BaseCmd.Properties.ID.getName());
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        String name = (String) params.get(BaseCmd.Properties.NAME.getName());
        String state = (String) params.get(BaseCmd.Properties.STATE.getName());
        Long zoneId = (Long)params.get(BaseCmd.Properties.ZONE_ID.getName());
        Long podId = (Long)params.get(BaseCmd.Properties.POD_ID.getName());
        Long hostId = (Long)params.get(BaseCmd.Properties.HOST_ID.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());
        Long accountId = null;
        Boolean isAdmin = false;

        if ((account == null) || isAdmin(account.getType())) {
            isAdmin = true;
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list virtual machines.");
                }

                if (accountName != null) {
                    account = getManagementServer().findActiveAccount(accountName, domainId);
                    if (account == null) {
                        throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                    accountId = account.getId();
                }
            } else {
                domainId = ((account == null) ? DomainVO.ROOT_DOMAIN : account.getDomainId());
            }
        } else {
            accountName = account.getAccountName();
            accountId = account.getId();
            domainId = account.getDomainId();
        }

        Long[] accountIds = null;
        if (accountId != null) {
            accountIds = new Long[1];
            accountIds[0] = accountId;
        }

        Long startIndex = Long.valueOf(0);
        int pageSizeNum = 50;
    	if (pageSize != null) {
    		pageSizeNum = pageSize.intValue();
    	}
        if (page != null) {
            int pageNum = page.intValue();
            if (pageNum > 0) {
                startIndex = Long.valueOf(pageSizeNum * (pageNum-1));
            }
        }
        Criteria c = new Criteria("id", Boolean.TRUE, startIndex, Long.valueOf(pageSizeNum));
        
        if (keyword != null) {
        	c.addCriteria(Criteria.KEYWORD, keyword);
        } else {
        	c.addCriteria(Criteria.ID, id);
            c.addCriteria(Criteria.NAME, name);
            c.addCriteria(Criteria.STATE, state);
            c.addCriteria(Criteria.DATACENTERID, zoneId);

            // ignore these search requests if it's not an admin
            if (isAdmin == true) {
    	        c.addCriteria(Criteria.DOMAINID, domainId);
    	        c.addCriteria(Criteria.PODID, podId);
    	        c.addCriteria(Criteria.HOSTID, hostId);
            } 
        }

        c.addCriteria(Criteria.ACCOUNTID, accountIds);
        c.addCriteria(Criteria.ISADMIN, isAdmin); 

        List<? extends UserVm> virtualMachines = getManagementServer().searchForUserVMs(c);

        if (virtualMachines == null) {
            throw new ServerApiException(BaseCmd.VM_LIST_ERROR, "unable to find virtual machines for account id " + accountName.toString());
        }

        Object[] vmTag = new Object[virtualMachines.size()];
        int i = 0;

        HashMap<Long, HostVO> hostMap = new HashMap<Long, HostVO>();
        List<HostVO> hostList = getManagementServer().listAllActiveHosts();
        for (HostVO hostVO : hostList) {
        	hostMap.put(hostVO.getId(), hostVO);
        }

        for (UserVm vmInstance : virtualMachines) {
            List<Pair<String, Object>> vmData = new ArrayList<Pair<String, Object>>();
            AsyncJobVO asyncJob = getManagementServer().findInstancePendingAsyncJob("vm_instance", vmInstance.getId());
            if(asyncJob != null) {
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), asyncJob.getId().toString()));
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_STATUS.getName(), String.valueOf(asyncJob.getStatus())));
            } 

            vmData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), vmInstance.getId().toString()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), vmInstance.getName()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(vmInstance.getCreated())));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.IP_ADDRESS.getName(), vmInstance.getPrivateIpAddress()));
            if (vmInstance.getState() != null) {
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.STATE.getName(), vmInstance.getState().toString()));
            }

            Account acct = getManagementServer().findAccountById(Long.valueOf(vmInstance.getAccountId()));
            if (acct != null) {
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), acct.getAccountName()));
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), acct.getDomainId().toString()));
                vmData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(acct.getDomainId()).getName()));
            }
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.HA_ENABLE.getName(), Boolean.valueOf(vmInstance.isHaEnabled()).toString()));
            
            if (vmInstance.getDisplayName() != null) {
    			vmData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_NAME.getName(), vmInstance.getDisplayName()));
    		}
            else {
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_NAME.getName(), vmInstance.getName()));
            }

    		if (vmInstance.getGroup() != null) {
    			vmData.add(new Pair<String, Object>(BaseCmd.Properties.GROUP.getName(), vmInstance.getGroup()));
    		}

            // Data Center Info
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), Long.valueOf(vmInstance.getDataCenterId()).toString()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_NAME.getName(), getManagementServer().findDataCenterById(vmInstance.getDataCenterId()).getName()));
            //if user is an admin, display host id
            if ( (isAdmin == true) && (vmInstance.getHostId() != null)) {
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.HOST_ID.getName(), vmInstance.getHostId().toString()));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.HOST_NAME.getName(), getManagementServer().getHostBy(vmInstance.getHostId()).getName()));
            }

            // Template Info
            VMTemplateVO template = getManagementServer().findTemplateById(vmInstance.getTemplateId());
            if (template != null) {
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_ID.getName(), Long.valueOf(vmInstance.getTemplateId()).toString()));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_NAME.getName(), template.getName()));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_DISPLAY_TEXT.getName(), template.getDisplayText()));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.PASSWORD_ENABLED.getName(), template.getEnablePassword()));
            } else {
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_ID.getName(), "-1"));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_NAME.getName(), "ISO Boot"));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_DISPLAY_TEXT.getName(), "ISO Boot"));
            	vmData.add(new Pair<String, Object>(BaseCmd.Properties.PASSWORD_ENABLED.getName(), false));
            }

            // ISO Info
            if (vmInstance.getIsoId() != null) {
                VMTemplateVO iso = getManagementServer().findTemplateById(vmInstance.getIsoId().longValue());
                if (iso != null) {
                    vmData.add(new Pair<String, Object>(BaseCmd.Properties.ISO_ID.getName(), Long.valueOf(vmInstance.getIsoId()).toString()));
                    vmData.add(new Pair<String, Object>(BaseCmd.Properties.ISO_NAME.getName(), iso.getName()));
                }
            }

            // Service Offering Info
            ServiceOfferingVO offering = getManagementServer().findServiceOfferingById(vmInstance.getServiceOfferingId());
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.SERVICE_OFFERING_ID.getName(), Long.valueOf(vmInstance.getServiceOfferingId()).toString()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.SERVICE_OFFERING_NAME.getName(), offering.getName()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.CPU_NUMBER.getName(), Integer.valueOf(offering.getCpu()).toString()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.CPU_SPEED.getName(), Integer.valueOf(offering.getSpeed()).toString()));
            vmData.add(new Pair<String, Object>(BaseCmd.Properties.MEMORY.getName(), Integer.valueOf(offering.getRamSize()).toString()));

            vmTag[i++] = vmData;
        }
        List<Pair<String, Object>> returnTags = new ArrayList<Pair<String, Object>>();
        Pair<String, Object> vmTags = new Pair<String, Object>("virtualmachine", vmTag);
        returnTags.add(vmTags);
        return returnTags;
    }
}
