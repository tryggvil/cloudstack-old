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
import com.vmops.async.AsyncJobVO;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.server.Criteria;
import com.vmops.storage.Snapshot;
import com.vmops.storage.SnapshotVO;
import com.vmops.storage.VolumeVO;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ListSnapshotsCmd extends BaseCmd {
	public static final Logger s_logger = Logger.getLogger(ListSnapshotsCmd.class.getName());

    private static final String s_name = "listsnapshotsresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.VOLUME_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.INTERVAL_TYPE, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.TYPE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.PAGESIZE, Boolean.FALSE));
    }

    public String getName() {
        return s_name;
    }
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long volumeId = (Long)params.get(BaseCmd.Properties.VOLUME_ID.getName());
        String interval = (String)params.get(BaseCmd.Properties.INTERVAL_TYPE.getName());
        String type = (String)params.get(BaseCmd.Properties.TYPE.getName());
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());
        Long accountId = null;

        if ((account == null) || isAdmin(account.getType())) {
            // validate domainId before proceeding
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list snapshot policies.");
                }
                if (accountName != null) {
                    Account userAccount = getManagementServer().findAccountByName(accountName, domainId);
                    if (userAccount != null) {
                        accountId = userAccount.getId();
                    } else {
                        throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                }
            }
        } else {
            accountId = account.getId();
        }

        //Verify parameters
        if(volumeId != null){
        	VolumeVO volume = getManagementServer().findVolumeById(volumeId);
        	if (volume == null) {
        		throw new ServerApiException (BaseCmd.SNAPSHOT_INVALID_PARAM_ERROR, "unable to find a volume with id " + volumeId);
        	}
        	Long volumeAccountId = volume.getAccountId();
            if ((accountId != null) && (accountId.longValue() != volumeAccountId.longValue())) {
                throw new ServerApiException(BaseCmd.SNAPSHOT_INVALID_PARAM_ERROR, "unable to find a volume with id " + volumeId + " for this account");
            }
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

        if (type != null) {
        	c.addCriteria(Criteria.TYPE, type);
        }
        
        if (keyword != null) {
            c.addCriteria(Criteria.KEYWORD, keyword);
        }
        
        List<SnapshotVO> snapshots = null;
		try {
			snapshots = getManagementServer().listSnapshots(c, volumeId, interval);
		} catch (InvalidParameterValueException e) {
			throw new ServerApiException(SNAPSHOT_INVALID_PARAM_ERROR, e.getMessage());
		}

        if (snapshots == null) {
            throw new ServerApiException(BaseCmd.SNAPSHOT_LIST_ERROR, "unable to find snapshots for volume with id " + volumeId);
        }

        Object[] snapshotTag = new Object[snapshots.size()];
        int i = 0;

        for (Snapshot snapshot : snapshots) {
            List<Pair<String, Object>> snapshotData = new ArrayList<Pair<String, Object>>();
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), snapshot.getId().toString()));

            Account acct = getManagementServer().findAccountById(Long.valueOf(snapshot.getAccountId()));
            if (acct != null) {
                snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), acct.getAccountName()));
                snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), acct.getDomainId().toString()));
                snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN.getName(), getManagementServer().findDomainIdById(acct.getDomainId()).getName()));
            }
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.TYPE.getName(), snapshot.getSnapshotType()));
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.VOLUME_ID.getName(), snapshot.getVolumeId()));
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), snapshot.getCreated()));
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), snapshot.getName()));
            snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.PATH.getName(), snapshot.getPath()));
            
            AsyncJobVO asyncJob = getManagementServer().findInstancePendingAsyncJob("snapshot", snapshot.getId());
            if(asyncJob != null) {
            	snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), asyncJob.getId().toString()));
            	snapshotData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_STATUS.getName(), String.valueOf(asyncJob.getStatus())));
            } 

            snapshotTag[i++] = snapshotData;
        }
        List<Pair<String, Object>> returnTags = new ArrayList<Pair<String, Object>>();
        Pair<String, Object> snapshotTags = new Pair<String, Object>("snapshot", snapshotTag);
        returnTags.add(snapshotTags);
        return returnTags;
    }
}
