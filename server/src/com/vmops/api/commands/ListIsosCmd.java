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

package com.vmops.api.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.async.AsyncJobVO;
import com.vmops.domain.DomainVO;
import com.vmops.host.HostVO;
import com.vmops.storage.GuestOS;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.dao.VMTemplateDao.TemplateFilter;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ListIsosCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListIsosCmd.class.getName());

    private static final String s_name = "listisosresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_PUBLIC, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_READY, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.TEMPLATE_FILTER, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.BOOTABLE, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.KEYWORD, Boolean.FALSE));
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
    	String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
    	Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
    	Account account = (Account) params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());
    	Boolean isPublic = (Boolean) params.get(BaseCmd.Properties.IS_PUBLIC.getName());
    	Boolean isReady = (Boolean) params.get(BaseCmd.Properties.IS_READY.getName());
        String templateFilterString = (String) params.get(BaseCmd.Properties.TEMPLATE_FILTER.getName());
        Boolean bootable = (Boolean)params.get(BaseCmd.Properties.BOOTABLE.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
    	boolean isAdmin = false;

    	if (templateFilterString == null) {
        	if ((isPublic != null && isPublic) || (isReady != null && isReady)) {
        		templateFilterString = "community";
        	} else {
        		templateFilterString = "all";
        	}
        }
    	
    	TemplateFilter templateFilter;
        try {
        	templateFilter = TemplateFilter.valueOf(templateFilterString);
        } catch (IllegalArgumentException e) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "Please specify a valid template type.");
        }
    	
        Long accountId = null;
        if ((account == null) || isAdmin(account.getType())) {
            isAdmin = true;
            // validate domainId before proceeding
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list events.");
                }
                if (accountName != null) {
                    Account userAccount = getManagementServer().findAccountByName(accountName, domainId);
                    if (userAccount != null) {
                        accountId = userAccount.getId();
                    } else {
                        throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                }
            } else {
                domainId = ((account == null) ? DomainVO.ROOT_DOMAIN : account.getDomainId());
            }
        } else {
            accountId = account.getId();
            accountName = account.getAccountName();
            domainId = account.getDomainId();
        }

        List<VMTemplateVO> isos = null;
        try {
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

            isos = getManagementServer().listTemplates(null, keyword, templateFilter, true, bootable, accountId, pageSize, startIndex);
        } catch (Exception ex) {
            s_logger.error("Exception listing ISOs", ex);
            throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "Failed to list ISOs due to exception: " + ex.getMessage());
        }
        
        int numTags = 0;
        for (VMTemplateVO iso : isos) {
        	List<VMTemplateHostVO> isoHosts = getManagementServer().listTemplateHostBy(iso.getId());
        	numTags += isoHosts.size();
        }
        
        Object[] iTag = new Object[numTags];
        int i = 0;
        List<Pair<String, Object>> isoTags = new ArrayList<Pair<String, Object>>();
        for (VMTemplateVO iso : isos) {
        	List<VMTemplateHostVO> isoHosts = getManagementServer().listTemplateHostBy(iso.getId());
        	for (VMTemplateHostVO isoHost : isoHosts) {
        		List<Pair<String, Object>> isoData = new ArrayList<Pair<String, Object>>();
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), iso.getId().toString()));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), iso.getName()));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), iso.getDisplayText()));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.IS_PUBLIC.getName(), Boolean.valueOf(iso.isPublicTemplate()).toString()));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(iso.getCreated())));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.IS_READY.getName(), Boolean.valueOf(iso.isReady()).toString()));
        		isoData.add(new Pair<String, Object>(BaseCmd.Properties.BOOTABLE.getName(), Boolean.valueOf(iso.isBootable()).toString()));
        		
        		GuestOS os = getManagementServer().findGuestOSById(iso.getGuestOSId());
	            if(os != null)
	            	isoData.add(new Pair<String, Object>(BaseCmd.Properties.OS_TYPE_NAME.getName(), os.getDisplayName()));
	            else
	            	isoData.add(new Pair<String, Object>(BaseCmd.Properties.OS_TYPE_NAME.getName(), ""));

        		// add account ID and name
        		Account owner = getManagementServer().findAccountById(iso.getAccountId());
        		if (owner != null) {
        			isoData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), owner.getAccountName()));
                    isoData.add(new Pair<String, Object>(BaseCmd.Properties.DOMAIN_ID.getName(), owner.getDomainId()));
        		}
        		
        		// Add the zone ID
                HostVO host = getManagementServer().getHostBy(isoHost.getHostId());
                isoData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), host.getDataCenterId()));

                // If the user is an admin, add the template download status
                if (isAdmin || account.getId().longValue() == iso.getAccountId()) {
                	// add download status
                	if (!iso.isReady()) {
                		String isoStatus = "Processing";
                		if (isoHost.getDownloadState() == VMTemplateHostVO.Status.DOWNLOADED) {
                			isoStatus = "Download Complete";
                		} else if (isoHost.getDownloadState() == VMTemplateHostVO.Status.DOWNLOAD_IN_PROGRESS) {
                			if (isoHost.getDownloadPercent() == 100) {
                				isoStatus = "Installing Template";
                			} else {
                				isoStatus = isoHost.getDownloadPercent() + "% Downloaded";
                			}
                		} else {
                			isoStatus = isoHost.getErrorString();
                		}
                		isoData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_STATUS.getName(), isoStatus));
                	} else {
                		isoData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_STATUS.getName(), "Successfully Installed"));
                	}
                }

                AsyncJobVO asyncJob = getManagementServer().findInstancePendingAsyncJob("vm_template", iso.getId());
                if(asyncJob != null) {
                	isoData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), asyncJob.getId().toString()));
                	isoData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_STATUS.getName(), String.valueOf(asyncJob.getStatus())));
                }

                iTag[i++] = isoData;
        	}
        }
        
        Pair<String, Object> isoTag = new Pair<String, Object>("iso", iTag);
        isoTags.add(isoTag);
        
        return isoTags;
    }
}
