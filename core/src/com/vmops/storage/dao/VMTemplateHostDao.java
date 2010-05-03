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

import com.vmops.storage.VMTemplateHostVO;
import com.vmops.utils.db.GenericDao;

public interface VMTemplateHostDao extends GenericDao<VMTemplateHostVO, Long> {
	public List<VMTemplateHostVO> listByHostId(long id);
	
	public List<VMTemplateHostVO> listByTemplateId(long templateId);
	
	public VMTemplateHostVO findByHostTemplate(long hostId, long templateId);
	
	public List<VMTemplateHostVO> listByHostTemplate(long hostId, long templateId);

	
	public VMTemplateHostVO findByHostTemplatePool(long hostId, long templateId, long poolId);
	
	public  List<VMTemplateHostVO> listByTemplatePool(long templateId, long poolId);


	public void update(VMTemplateHostVO instance);

	public List<VMTemplateHostVO> listByTemplateStatus(long templateId, VMTemplateHostVO.Status downloadState);

	public List<VMTemplateHostVO> listByTemplateStatus(long templateId, long datacenterId, VMTemplateHostVO.Status downloadState);
	
	public List<VMTemplateHostVO> listByTemplateStatus(long templateId, long datacenterId, long podId, VMTemplateHostVO.Status downloadState);

	public List<VMTemplateHostVO> listByTemplateStates(long templateId, VMTemplateHostVO.Status ... states);

	
	boolean templateAvailable(long templateId, long hostId);

}
