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
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.domain.DomainVO;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.server.Criteria;
import com.vmops.storage.VolumeVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;
import com.vmops.vm.VMInstanceVO;

public class ListVolumesCmd extends BaseCmd{
    public static final Logger s_logger = Logger.getLogger(ListVolumesCmd.class.getName());

    private static final String s_name = "listvolumesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.TYPE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ZONE_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.POD_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VIRTUAL_MACHINE_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.HOST_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGESIZE, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Long id = (Long)params.get(BaseCmd.Properties.ID.getName());
        String type = (String)params.get(BaseCmd.Properties.TYPE.getName());
        String name = (String)params.get(BaseCmd.Properties.NAME.getName());
        Long zoneId = (Long)params.get(BaseCmd.Properties.ZONE_ID.getName());
        Long podId = (Long)params.get(BaseCmd.Properties.POD_ID.getName());
        Long vmId = (Long)params.get(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        Long hostId = (Long)params.get(BaseCmd.Properties.HOST_ID.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());

        Long accountId = null;
        boolean isAdmin = false;
        if ((account == null) || isAdmin(account.getType())) {
            isAdmin = true;
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list volumes.");
                }
                if (accountName != null) {
                    Account userAccount = getManagementServer().findActiveAccount(accountName, domainId);
                    if (userAccount != null) {
                        accountId = userAccount.getId();
                    } else {
                        throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "could not find account " + accountName + " in domain " + domainId);
                    }
                }
            } else {
                domainId = ((account == null) ? DomainVO.ROOT_DOMAIN : account.getDomainId());
            }
        } else {
            accountId = account.getId();
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

        Criteria c = new Criteria("created", Boolean.FALSE, startIndex, Long.valueOf(pageSizeNum));

        c.addCriteria(Criteria.ACCOUNTID, accountIds);
        if (keyword != null) {
            c.addCriteria(Criteria.KEYWORD, keyword);
        } else {
            c.addCriteria(Criteria.ID, id);
            c.addCriteria(Criteria.INSTANCEID, vmId);
            c.addCriteria(Criteria.NAME, name);
            if (isAdmin) {
                c.addCriteria(Criteria.VTYPE, type);
                c.addCriteria(Criteria.DATACENTERID, zoneId);
                c.addCriteria(Criteria.PODID, podId);
                c.addCriteria(Criteria.HOSTID, hostId);
                c.addCriteria(Criteria.DOMAINID, domainId);
            }
        }

        List<VolumeVO> volumes = getManagementServer().searchForVolumes(c);

        if (volumes == null) {
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "unable to find volumes");
        }

        List<Pair<String, Object>> volumeTags = new ArrayList<Pair<String, Object>>();
        Object[] vTag = new Object[volumes.size()];
        int i = 0;
        for (VolumeVO volume : volumes) {
            List<Pair<String, Object>> volumeData = new ArrayList<Pair<String, Object>>();
            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), volume.getId()));

            if (volume.getName() != null) {
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), volume.getName()));
            } else {
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), ""));
            }

            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.TYPE.getName(), volume.getVolumeType()));
            //volumeData.add(new Pair<String, Object>(BaseCmd.Properties.HOST_NAME.getName(), getManagementServer().getHostBy(volume.getHostId()).getName()));

            Long instanceId = volume.getInstanceId();
            if (instanceId != null) {
                VMInstanceVO vm = getManagementServer().findVMInstanceById(instanceId);
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.VIRTUAL_MACHINE_ID.getName(), vm.getId()));
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.VIRTUAL_MACHINE_NAME.getName(), vm.getName()));
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.VIRTUAL_MACHINE_STATE.getName(), vm.getState()));
            }             

            // Show the virtual size of the volume
            long virtualSizeInBytes = volume.getSize();
            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.SIZE.getName(), virtualSizeInBytes));

            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(volume.getCreated())));
            Account accountTemp = getManagementServer().findAccountById(volume.getAccountId());

            if (accountTemp != null) {
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), accountTemp.getAccountName()));
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), accountTemp.getDomainId()));
                volumeData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(accountTemp.getDomainId()).getName()));
            }

            String storageType;
            try {
                storageType = getManagementServer().volumeIsOnSharedStorage(volume.getId()) ? "shared" : "local";
            } catch (InvalidParameterValueException e) {
                s_logger.error(e.getMessage(), e);
                throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Volume " + volume.getName() + " does not have a valid ID");
            }

            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.STORAGE_TYPE.getName(), storageType));
            volumeData.add(new Pair<String, Object>(BaseCmd.Properties.STORAGE.getName(), getManagementServer().findPoolById(volume.getPoolId()).getName()));

            vTag[i++] = volumeData;
        }
        Pair<String, Object> volumeTag = new Pair<String, Object>("volume", vTag);
        volumeTags.add(volumeTag);
        return volumeTags;
    }
}
