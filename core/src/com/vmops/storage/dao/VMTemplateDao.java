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

package com.vmops.storage.dao;

import java.util.List;

import com.vmops.storage.VMTemplateVO;
import com.vmops.utils.db.GenericDao;

/*
 * Data Access Object for vm_templates table
 */
public interface VMTemplateDao extends GenericDao<VMTemplateVO, Long> {
	public List<VMTemplateVO> listByPublic();
	public VMTemplateVO findByName(String templateName);
	//public void update(VMTemplateVO template);
	public VMTemplateVO findRoutingTemplate();
	public VMTemplateVO findConsoleProxyTemplate();
	public String getRoutingTemplateUniqueName();
	public List<VMTemplateVO> findIsosByIdAndPath(Long domainId, Long accountId, String path);
	public List<VMTemplateVO> listReadyTemplates();
	public List<VMTemplateVO> listByAccountId(long accountId);
	public List<VMTemplateVO> searchTemplates(String name, String keyword, Boolean isReady, Boolean isPublic, boolean isIso, Boolean bootable, Long accountId, Integer pageSize, Long startIndex);
}
