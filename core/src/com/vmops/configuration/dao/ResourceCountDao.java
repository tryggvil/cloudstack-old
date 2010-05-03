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

package com.vmops.configuration.dao;

import com.vmops.configuration.ResourceCountVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.utils.db.GenericDao;

public interface ResourceCountDao extends GenericDao<ResourceCountVO, Long> {

	public long getCount(long accountId, ResourceType type);
	public void updateCount(long accountId, ResourceType type, boolean increment, long delta);
}
