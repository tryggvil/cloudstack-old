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

package com.vmops.event.dao;

import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.event.EventVO;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchCriteria;

@Local(value={EventDao.class})
public class EventDaoImpl extends GenericDaoBase<EventVO, Long> implements EventDao {
	public static final Logger s_logger = Logger.getLogger(EventDaoImpl.class.getName());
	
	
	public EventDaoImpl () {}

	@Override
	@DB
	public List<EventVO> searchAllEvents(SearchCriteria sc, Filter filter) {
	    return listBy(sc, filter);
	}

	@Override
    public List<EventVO> search(final SearchCriteria sc, final Filter filter) {
	    return super.search(sc, filter);
	}
}
