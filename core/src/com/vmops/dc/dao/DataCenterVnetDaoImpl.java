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
package com.vmops.dc.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Formatter;
import java.util.List;

import com.vmops.dc.DataCenterVnetVO;
import com.vmops.exception.InternalErrorException;
import com.vmops.utils.db.GenericDao;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 * DataCenterVnetDaoImpl maintains the one-to-many relationship between
 * data center and the vnet that appears within its network.
 */
public class DataCenterVnetDaoImpl extends GenericDaoBase<DataCenterVnetVO, Long> implements GenericDao<DataCenterVnetVO, Long> {
    private final SearchBuilder<DataCenterVnetVO> FreeVnetSearch;
    private final SearchBuilder<DataCenterVnetVO> VnetDcSearch;
    private final SearchBuilder<DataCenterVnetVO> DcSearchAllocated;
    
    public List<DataCenterVnetVO> listAllocatedVnets(long dcId) {
    	SearchCriteria sc = DcSearchAllocated.create();
    	sc.setParameters("dc", dcId);
    	return listActiveBy(sc);
    }
    
    public void add(long dcId, int start, int end) {
        String insertVnet = "INSERT INTO `vmops`.`op_dc_vnet_alloc` (vnet, data_center_id) VALUES ( ?, ?)";
        
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            PreparedStatement stmt = txn.prepareAutoCloseStatement(insertVnet);
            for (int i = start; i < end; i++) {
                stmt.setString(1, String.valueOf(i));
                stmt.setLong(2, dcId);
                stmt.addBatch();
            }
            stmt.executeBatch();
            txn.commit();
        } catch (SQLException e) {
            throw new VmopsRuntimeException("Exception caught adding vnet ", e);
        }
    }
    
    public void delete(long dcId) {
    	String deleteVnet = "DELETE FROM `vmops`.`op_dc_vnet_alloc` WHERE data_center_id = ?";

        Transaction txn = Transaction.currentTxn();
        try {
            PreparedStatement stmt = txn.prepareAutoCloseStatement(deleteVnet);
            stmt.setLong(1, dcId);
            stmt.executeUpdate();
        } catch (SQLException e) {
        	throw new VmopsRuntimeException("Exception caught deleting vnet ", e);
        }
    }

    public DataCenterVnetVO take(long dcId, long accountId) {
        SearchCriteria sc = FreeVnetSearch.create();
        sc.setParameters("dc", dcId);
        Date now = new Date();
        Transaction txn = Transaction.currentTxn();
        try {
            txn.start();
            DataCenterVnetVO vo = lock(sc, true);
            if (vo == null) {
                return null;
            }

            vo.setTakenAt(now);
            vo.setAccountId(accountId);
            update(vo.getId(), vo);
            txn.commit();
            return vo;

        } catch (Exception e) {
            throw new VmopsRuntimeException("Caught Exception ", e);
        }
    }

    public void release(String vnet, long dcId, long accountId) {
        SearchCriteria sc = VnetDcSearch.create();
        sc.setParameters("vnet", vnet);
        sc.setParameters("dc", dcId);
        sc.setParameters("account", accountId);

        DataCenterVnetVO vo = findOneBy(sc);
        if (vo == null) {
            return;
        }

        vo.setTakenAt(null);
        vo.setAccountId(null);
        update(vo.getId(), vo);
    }

    protected DataCenterVnetDaoImpl() {
    	super();
        DcSearchAllocated = createSearchBuilder();
        DcSearchAllocated.and("dc", DcSearchAllocated.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DcSearchAllocated.and("allocated", DcSearchAllocated.entity().getTakenAt(), SearchCriteria.Op.NNULL);
        DcSearchAllocated.done();
        
        FreeVnetSearch = createSearchBuilder();
        FreeVnetSearch.and("dc", FreeVnetSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        FreeVnetSearch.and("taken", FreeVnetSearch.entity().getTakenAt(), SearchCriteria.Op.NULL);
        FreeVnetSearch.done();

        VnetDcSearch = createSearchBuilder();
        VnetDcSearch.and("vnet", VnetDcSearch.entity().getVnet(), SearchCriteria.Op.EQ);
        VnetDcSearch.and("dc", VnetDcSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        VnetDcSearch.and("taken", VnetDcSearch.entity().getTakenAt(), SearchCriteria.Op.NNULL);
        VnetDcSearch.and("account", VnetDcSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        VnetDcSearch.done();
    }
}
