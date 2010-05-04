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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.vmops.api.BaseCmd;
import com.vmops.api.ServerApiException;
import com.vmops.async.AsyncJobVO;
import com.vmops.host.HostVO;
import com.vmops.storage.GuestOS;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.dao.VMTemplateDao.TemplateFilter;
import com.vmops.user.Account;
import com.vmops.utils.Pair;

public class ListTemplatesCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(ListTemplatesCmd.class.getName());

    private static final String s_name = "listtemplatesresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT_OBJ, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ACCOUNT, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.DOMAIN_ID, Boolean.FALSE));
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.ID, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.NAME, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_PUBLIC, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.IS_READY, Boolean.FALSE));
    	s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.TEMPLATE_FILTER, Boolean.FALSE));
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
        Account account = (Account)params.get(BaseCmd.Properties.ACCOUNT_OBJ.getName());
        String accountName = (String)params.get(BaseCmd.Properties.ACCOUNT.getName());
        Long domainId = (Long)params.get(BaseCmd.Properties.DOMAIN_ID.getName());
        Long id = (Long) params.get(BaseCmd.Properties.ID.getName());
    	String name = (String) params.get(BaseCmd.Properties.NAME.getName());
    	Boolean isPublic = (Boolean) params.get(BaseCmd.Properties.IS_PUBLIC.getName());
    	Boolean isReady = (Boolean) params.get(BaseCmd.Properties.IS_READY.getName());
    	String templateFilterString = (String) params.get(BaseCmd.Properties.TEMPLATE_FILTER.getName());
        String keyword = (String)params.get(BaseCmd.Properties.KEYWORD.getName());
        Integer page = (Integer)params.get(BaseCmd.Properties.PAGE.getName());
        Integer pageSize = (Integer)params.get(BaseCmd.Properties.PAGESIZE.getName());
        List<VMTemplateVO> templates = new ArrayList<VMTemplateVO>();
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
            if (domainId != null) {
                if ((account != null) && !getManagementServer().isChildDomain(account.getDomainId(), domainId)) {
                    throw new ServerApiException(BaseCmd.PARAM_ERROR, "Invalid domain id (" + domainId + ") given, unable to list templates.");
                }

                if (accountName != null) {
                    account = getManagementServer().findActiveAccount(accountName, domainId);
                    if (account == null) {
                        throw new ServerApiException(BaseCmd.PARAM_ERROR, "Unable to find account " + accountName + " in domain " + domainId);
                    }
                    accountId = account.getId();
                } 
            }
        } else {
            accountId = account.getId();
            accountName = account.getAccountName();
            domainId = account.getDomainId();
        }
        
        /*
        if (accountId == null && (templateFilter == TemplateFilter.self || templateFilter == TemplateFilter.selfexecutable)) {
        	throw new ServerApiException(BaseCmd.PARAM_ERROR, "Please specify a valid account.");
        }
        */

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

        //check if domR template is ready in the system
        VMTemplateVO domRTemplate = getManagementServer().findTemplateById(1);

        //remove all the templates from the list if domR template is not ready and showAll is false
        if (domRTemplate != null && !domRTemplate.isReady() && templateFilter == TemplateFilter.selfexecutable) {
            templates = new ArrayList<VMTemplateVO>();
        } else {
            if (id != null) {
                templates.add(getManagementServer().findTemplateById(id.longValue()));
            } else {
                templates = getManagementServer().listTemplates(name, keyword, templateFilter, false, null, accountId, Integer.valueOf(pageSizeNum), startIndex);
            }

            // if showAll is false, for admins remove the domR template from the list of VMs
            if (templateFilter == TemplateFilter.selfexecutable) {
                Iterator<VMTemplateVO> iter = templates.iterator();
                while (iter.hasNext()) {
                    VMTemplateVO nextTemplate = iter.next();
                    if (nextTemplate.getId().longValue() == 1L) {
                        iter.remove();
                        break;
                    }
                }
            }

            if (templates == null) {
                throw new ServerApiException(BaseCmd.INTERNAL_ERROR, "unable to find templates");
            }
        }
        
        int numTags = 0;
        for (VMTemplateVO template : templates) {
        	List<VMTemplateHostVO> templateHosts = getManagementServer().listTemplateHostBy(template.getId());
        	numTags += (templateHosts.size());
        }
        
        Object[] tTag = new Object[numTags];
        int i = 0;
        List<Pair<String, Object>> templateTags = new ArrayList<Pair<String, Object>>();
        for (VMTemplateVO template : templates) {
        	List<VMTemplateHostVO> templateHosts = getManagementServer().listTemplateHostBy(template.getId());
        	for (VMTemplateHostVO templateHost : templateHosts) {
        		List<Pair<String, Object>> templateData = new ArrayList<Pair<String, Object>>();
        		templateData.add(new Pair<String, Object>(BaseCmd.Properties.ID.getName(), template.getId().toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.NAME.getName(), template.getName()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.DISPLAY_TEXT.getName(), template.getDisplayText()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_PUBLIC.getName(), Boolean.valueOf(template.isPublicTemplate()).toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.REQUIRES_HVM.getName(), new Boolean(template.requiresHvm()).toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.BITS.getName(), Integer.valueOf(template.getBits()).toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.CREATED.getName(), getDateString(template.getCreated())));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_READY.getName(), Boolean.valueOf(template.isReady()).toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.IS_FEATURED.getName(), Boolean.valueOf(template.isFeatured()).toString()));
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.PASSWORD_ENABLED.getName(), Boolean.valueOf(template.getEnablePassword()).toString()));
                
                GuestOS os = getManagementServer().findGuestOSById(template.getGuestOSId());
                if (os != null) {
                    templateData.add(new Pair<String, Object>(BaseCmd.Properties.OS_TYPE_NAME.getName(), os.getDisplayName()));
                } else {
                    templateData.add(new Pair<String, Object>(BaseCmd.Properties.OS_TYPE_NAME.getName(), ""));
                }
                
                // add account ID and name
                Account owner = getManagementServer().findAccountById(template.getAccountId());
                if (owner != null) {
                    templateData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT_ID.getName(), owner.getId()));
                    templateData.add(new Pair<String, Object>(BaseCmd.Properties.ACCOUNT.getName(), owner.getAccountName()));
                }
                
                // Add the zone ID
                HostVO host = getManagementServer().getHostBy(templateHost.getHostId());
                templateData.add(new Pair<String, Object>(BaseCmd.Properties.ZONE_ID.getName(), host.getDataCenterId()));
        		
                // If the user is an admin, add the template download status
                if (isAdmin || account.getId().longValue() == template.getAccountId()) {
                    // add download status
                    if (!template.isReady()) {
                        String templateStatus = "Processing";
                        if (templateHost.getDownloadState() == VMTemplateHostVO.Status.DOWNLOADED) {
                            templateStatus = "Download Complete";
                        } else if (templateHost.getDownloadState() == VMTemplateHostVO.Status.DOWNLOAD_IN_PROGRESS) {
                            if (templateHost.getDownloadPercent() == 100) {
                                templateStatus = "Installing Template";
                            } else {
                                templateStatus = templateHost.getDownloadPercent() + "% Downloaded";
                            }
                        } else {
                            templateStatus = templateHost.getErrorString();
                        }
                        templateData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_STATUS.getName(), templateStatus));
                    } else {
                    	templateData.add(new Pair<String, Object>(BaseCmd.Properties.TEMPLATE_STATUS.getName(), "Successfully Installed"));
                    }
                }
                
                AsyncJobVO asyncJob = getManagementServer().findInstancePendingAsyncJob("vm_template", template.getId());
                if(asyncJob != null) {
                	templateData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), asyncJob.getId().toString()));
                	templateData.add(new Pair<String, Object>(BaseCmd.Properties.JOB_STATUS.getName(), String.valueOf(asyncJob.getStatus())));
                }
                
                tTag[i++] = templateData;     
        	}
        }
        
        Pair<String, Object> templateTag = new Pair<String, Object>("template", tTag);
        templateTags.add(templateTag);

        return templateTags;
    }
}
