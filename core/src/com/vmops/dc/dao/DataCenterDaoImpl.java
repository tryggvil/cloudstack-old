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

package com.vmops.dc.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.dc.DataCenterIpAddressVO;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.DataCenterVnetVO;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NetUtils;

/**
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || mac.address.prefix | prefix to attach to all public and private mac addresses | number | 06 ||
 *  }
 **/
@Local(value={DataCenterDao.class})
public class DataCenterDaoImpl extends GenericDaoBase<DataCenterVO, Long> implements DataCenterDao {
    private static final Logger s_logger = Logger.getLogger(DataCenterDaoImpl.class);

    protected static final String UPDATE_MAC_ADDRESS_SQL = "UPDATE data_center set mac_address = LAST_INSERT_ID(mac_address + 1) WHERE id = ?";
    
    protected SearchBuilder<DataCenterVO> NameSearch;

    protected static final DataCenterIpAddressDaoImpl _ipAllocDao = ComponentLocator.inject(DataCenterIpAddressDaoImpl.class);
    protected static final DataCenterVnetDaoImpl _vnetAllocDao = ComponentLocator.inject(DataCenterVnetDaoImpl.class);
    protected long _prefix;

    @Override
    public DataCenterVO findByName(String name) {
    	SearchCriteria sc = NameSearch.create();
    	sc.setParameters("name", name);
        return findOneActiveBy(sc);
    }

    protected long getNextSequence(String sql, long zoneId) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);

            pstmt.setLong(1, zoneId);

            pstmt.executeUpdate();

            pstmt = txn.prepareAutoCloseStatement(SELECT_LAST_INSERT_ID_SQL);
            ResultSet rs = pstmt.executeQuery();
            if (rs == null || !rs.next()) {
                throw new VmopsRuntimeException("Unable to fetch a sequence with " + sql);
            }

            long result = rs.getLong(1);
            return result;
        } catch (SQLException e) {
            s_logger.warn("DB Exception", e);
            throw new VmopsRuntimeException("DB Exception ", e);
        }
    }

    @Override
    public void releaseVnet(String vnet, long dcId, long accountId) {
        _vnetAllocDao.release(vnet, dcId, accountId);
    }

    @Override
    public void releasePrivateIpAddress(String ipAddress, long dcId, Long instanceId) {
        _ipAllocDao.releaseIpAddress(ipAddress, dcId, instanceId);
    }
    
    @Override
    public boolean deletePrivateIpAddressByPod(long podId) {
    	return _ipAllocDao.deleteIpAddressByPod(podId);
    }

    @Override
    public String allocateVnet(long podId, long accountId) {
        DataCenterVnetVO vo = _vnetAllocDao.take(podId, accountId);
        if (vo == null) {
            return null;
        }

        return vo.getVnet();
    }

    @Override
    public String[] getNextAvailableMacAddressPair(long id) {
        return getNextAvailableMacAddressPair(id, 0);
    }

    @Override
    public String[] getNextAvailableMacAddressPair(long id, long mask) {
        long seq =  getNextSequence(UPDATE_MAC_ADDRESS_SQL, id);
        seq = seq | _prefix | ((id & 0x7f) << 32);
        seq |= mask;
        String[] pair = new String[2];
        pair[0] = NetUtils.long2Mac(seq);
        pair[1] = NetUtils.long2Mac(seq | 0x1l << 39);
        return pair;
    }

    @Override
    public String allocatePrivateIpAddress(long dcId, long podId, long instanceId) {
        DataCenterIpAddressVO vo = _ipAllocDao.takeIpAddress(dcId, podId, instanceId);
        if (vo == null) {
            return null;
        }
        return vo.getIpAddress();
    }
    
    @Override
    public void addVnet(long dcId, int start, int end) {
        _vnetAllocDao.add(dcId, start, end);
    }
    
    @Override
    public void deleteVnet(long dcId) {
    	_vnetAllocDao.delete(dcId);
    }
    
    @Override
    public List<DataCenterVnetVO> listAllocatedVnets(long dcId) {
    	return _vnetAllocDao.listAllocatedVnets(dcId);
    }
    
    @Override
    public void addPrivateIpAddress(long dcId,long podId, String start, String end) {
        _ipAllocDao.addIpRange(dcId, podId, start, end);
    }

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            return false;
        }
        
        String value = (String)params.get("mac.address.prefix");
        _prefix = (long)NumbersUtil.parseInt(value, 06) << 40;

        if (!_ipAllocDao.configure("Ip Alloc", params)) {
            return false;
        }

        if (!_vnetAllocDao.configure("vnet Alloc", params)) {
            return false;
        }
        return true;
    }
    
    protected DataCenterDaoImpl() {
        NameSearch = createSearchBuilder();
        NameSearch.addAnd("name", NameSearch.entity().getName(), SearchCriteria.Op.EQ);
        NameSearch.done();
    }
}
