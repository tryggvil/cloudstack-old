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

package com.vmops.storage.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.vmops.host.Status;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.utils.Pair;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;

@Local(value={StoragePoolHostDao.class})
public class StoragePoolHostDaoImpl extends GenericDaoBase<StoragePoolHostVO, Long> implements StoragePoolHostDao {
	public static final Logger s_logger = Logger.getLogger(StoragePoolHostDaoImpl.class.getName());
	
	protected final SearchBuilder<StoragePoolHostVO> PoolSearch;
	protected final SearchBuilder<StoragePoolHostVO> HostSearch;
	protected final SearchBuilder<StoragePoolHostVO> PoolHostSearch;
	
	protected static final String HOST_FOR_POOL_SEARCH=
		"SELECT * FROM storage_pool_host_ref ph,  host h where  ph.host_id = h.id and ph.pool_id=? and h.status=? ";
	
	protected static final String STORAGE_POOL_HOST_INFO =
    	"SELECT p.data_center_id,  count(ph.host_id) " +
    	" FROM storage_pool p, storage_pool_host_ref ph " +
    	" WHERE p.id = ph.pool_id AND p.data_center_id = ? " +
    	" GROUP by p.data_center_id";
	
	public StoragePoolHostDaoImpl () {
		PoolSearch = createSearchBuilder();
		PoolSearch.and("pool_id", PoolSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
		PoolSearch.done();
		
		HostSearch = createSearchBuilder();
		HostSearch.and("host_id", HostSearch.entity().getHostId(), SearchCriteria.Op.EQ);
		HostSearch.done();
		
		PoolHostSearch = createSearchBuilder();
		PoolHostSearch.and("pool_id", PoolHostSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
		PoolHostSearch.and("host_id", PoolHostSearch.entity().getHostId(), SearchCriteria.Op.EQ);
		PoolHostSearch.done();

	}
	


	@Override
	public List<StoragePoolHostVO> listByPoolId(long id) {
	    SearchCriteria sc = PoolSearch.create();
	    sc.setParameters("pool_id", id);
	    return listBy(sc);
	}

	@Override
	public List<StoragePoolHostVO> listByHostId(long hostId) {
	    SearchCriteria sc = HostSearch.create();
	    sc.setParameters("host_id", hostId);
	    return listBy(sc);
	}

	@Override
	public StoragePoolHostVO findByPoolHost(long poolId, long hostId) {
		SearchCriteria sc = PoolHostSearch.create();
	    sc.setParameters("pool_id", poolId);
	    sc.setParameters("host_id", hostId);
	    return findOneBy(sc);
	}
	
	@Override
	public List<StoragePoolHostVO> listByHostStatus(long poolId, Status hostStatus) {
        Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		List<StoragePoolHostVO> result = new ArrayList<StoragePoolHostVO>();
		ResultSet rs = null;
		try {
			String sql = HOST_FOR_POOL_SEARCH;
			pstmt = txn.prepareStatement(sql);
			
			pstmt.setLong(1, poolId);
			pstmt.setString(2, hostStatus.toString());
			rs = pstmt.executeQuery();
			while (rs.next()) {
                // result.add(toEntityBean(rs, false)); TODO: this is buggy in GenericDaoBase for hand constructed queries
				long id = rs.getLong(1); //ID column
				result.add(findById(id));
            }
		} catch (Exception e) {
			s_logger.warn("Exception: ", e);
		} finally {
			try {
				if (rs != null) {
					rs.close();
				}
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (SQLException e) {
			}
		}
		return result;

	}
	
	 @Override
	 public List<Pair<Long, Integer>> getDatacenterStoragePoolHostInfo(long dcId) {
		 ArrayList<Pair<Long, Integer>> l = new ArrayList<Pair<Long, Integer>>();

		 Transaction txn = Transaction.currentTxn();;
		 PreparedStatement pstmt = null;
		 try {
			 pstmt = txn.prepareAutoCloseStatement(STORAGE_POOL_HOST_INFO);
			 pstmt.setLong(1, dcId);

			 ResultSet rs = pstmt.executeQuery();
			 while(rs.next()) {
				 l.add(new Pair<Long, Integer>(rs.getLong(1), rs.getInt(2)));
			 }
		 } catch (SQLException e) {
		 } catch (Throwable e) {
		 }
		 return l;
	 }

}
