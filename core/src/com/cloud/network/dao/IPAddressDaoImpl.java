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

package com.cloud.network.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import org.apache.log4j.Logger;

import com.cloud.network.IPAddressVO;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.Transaction;

@Local(value={IPAddressDao.class})
public class IPAddressDaoImpl extends GenericDaoBase<IPAddressVO, String> implements IPAddressDao {
    private static final Logger s_logger = Logger.getLogger(IPAddressDaoImpl.class);
	
	protected SearchBuilder<IPAddressVO> DcSearchAll;
	protected SearchBuilder<IPAddressVO> DcIpSearch;
	protected SearchBuilder<IPAddressVO> VlanDbIdSearch;
	protected SearchBuilder<IPAddressVO> VlanDbIdSearchUnallocated;
    protected SearchBuilder<IPAddressVO> AccountIdSearch;
    protected SearchBuilder<IPAddressVO> AccountIdSourceNatSearch;
    protected SearchBuilder<IPAddressVO> AccountDcSearch;
    protected SearchBuilder<IPAddressVO> AccountDcSnatSearch;
    protected SearchBuilder<IPAddressVO> AddressSearch;
    protected SearchBuilder<IPAddressVO> AccountZoneVlanSearch;

    // make it public for JUnit test
    public IPAddressDaoImpl() {
	    AccountDcSearch = createSearchBuilder();
	    AccountDcSearch.and("accountId", AccountDcSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountDcSearch.and("dataCenterId", AccountDcSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    AccountDcSearch.done();

	    AccountDcSnatSearch = createSearchBuilder();
	    AccountDcSnatSearch.and("accountId", AccountDcSnatSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.and("dataCenterId", AccountDcSnatSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.and("sourceNat", AccountDcSnatSearch.entity().isSourceNat(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.done();
	    
	    AddressSearch = createSearchBuilder();
	    AddressSearch.and("address", AddressSearch.entity().getAddress(), SearchCriteria.Op.EQ);
	    AddressSearch.done();
	    
	    DcSearchAll = createSearchBuilder();
	    DcSearchAll.and("dataCenterId", DcSearchAll.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    DcSearchAll.done();
	    
	    DcIpSearch = createSearchBuilder();
	    DcIpSearch.and("dataCenterId", DcIpSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    DcIpSearch.and("ipAddress", DcIpSearch.entity().getAddress(), SearchCriteria.Op.EQ);
	    DcIpSearch.done();
	    
	    VlanDbIdSearchUnallocated = createSearchBuilder();
	    VlanDbIdSearchUnallocated.and("allocated", VlanDbIdSearchUnallocated.entity().getAllocated(), SearchCriteria.Op.NULL);
	    VlanDbIdSearchUnallocated.and("vlanDbId", VlanDbIdSearchUnallocated.entity().getVlanDbId(), SearchCriteria.Op.EQ);
	    //VlanDbIdSearchUnallocated.addRetrieve("ipAddress", VlanDbIdSearchUnallocated.entity().getAddress());
	    VlanDbIdSearchUnallocated.done();
	    
	    VlanDbIdSearch = createSearchBuilder();
	    VlanDbIdSearch.and("vlanDbId", VlanDbIdSearch.entity().getVlanDbId(), SearchCriteria.Op.EQ);
	    VlanDbIdSearch.done();
	    
	    AccountIdSearch = createSearchBuilder();
	    AccountIdSearch.and("accountId", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountIdSearch.done();

        AccountIdSourceNatSearch = createSearchBuilder();
        AccountIdSourceNatSearch.and("accountId", AccountIdSourceNatSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSourceNatSearch.and("sourceNat", AccountIdSourceNatSearch.entity().isSourceNat(), SearchCriteria.Op.EQ);
        AccountIdSourceNatSearch.done();
    }
    
    public boolean mark(long dcId, String ip) {
        SearchCriteria sc = DcIpSearch.create();
        sc.setParameters("dataCenterId", dcId);
        sc.setParameters("ipAddress", ip);
        
        IPAddressVO vo = createForUpdate();
        vo.setAllocated(new Date());
        
        return update(vo, sc) >= 1;
    }

	@Override
    public String assignIpAddress(long accountId, long domainId, long vlanDbId, boolean sourceNat) {

		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
	        SearchCriteria sc = VlanDbIdSearchUnallocated.create();
	        sc.setParameters("vlanDbId", vlanDbId);
	        
			IPAddressVO ip = this.lock(sc, true);
			if(ip != null) {
				ip.setAccountId(accountId);
				ip.setAllocated(new Date());
				ip.setDomainId(domainId);
				ip.setSourceNat(sourceNat);
				
				if (!update(ip.getAddress(), ip)) {
					s_logger.debug("Unable to retrieve any ip addresses");
					return null;
				}
	
				txn.commit();
				return ip.getAddress();
			} else {
				txn.rollback();
				s_logger.error("Unable to find an available IP address with related vlan, vlanDbId: " + vlanDbId);
			}
		} catch (Exception e) {
			s_logger.warn("Unable to assign IP", e);
		}
		return null;
    }

	@Override
	public void unassignIpAddress(String ipAddress) {
		IPAddressVO address = createForUpdate();
	    address.setAccountId(null);
	    address.setDomainId(null);
	    address.setAllocated(null);
	    address.setSourceNat(false);
	    update(ipAddress, address);
	}

	@Override
	public List<IPAddressVO> listByAccountId(long accountId) {
	    if (accountId != -1) {
	        SearchCriteria sc = AccountIdSearch.create();
	        sc.setParameters("accountId", accountId);
	        return listBy(sc);
	    }
        return listAllActive();
	}

    @Override
    public List<IPAddressVO> listByAccountIdSourceNat(long accountId, boolean sourceNat) {
        if (accountId != -1) {
            SearchCriteria sc = AccountIdSourceNatSearch.create();
            sc.setParameters("accountId", accountId);
            sc.setParameters("sourceNat", sourceNat);
            return listBy(sc);
        }
        return listAllActive();
    }

    @Override
	public List<IPAddressVO> listByAccountDcId(long accountId, long dcId, boolean sourceNat) {
		SearchCriteria sc = AccountDcSnatSearch.create();
		sc.setParameters("accountId", accountId);
		sc.setParameters("dataCenterId", dcId);
		sc.setParameters("sourceNat", sourceNat);
		return listBy(sc);
	}

	@Override
	public List<IPAddressVO> listByAccountDcId(long accountId, long dcId) {
		SearchCriteria sc = AccountDcSearch.create();
		sc.setParameters("accountId", accountId);
		sc.setParameters("dataCenterId", dcId);
		return listBy(sc);
	}
	
	@Override
	public List<IPAddressVO> listByDcId(long dcId) {
		SearchCriteria sc = DcSearchAll.create();
		sc.setParameters("dataCenterId", dcId);
		return listBy(sc);
	}
	
	@Override
	public List<IPAddressVO> listByVlanDbId(long vlanDbId) {
		SearchCriteria sc = VlanDbIdSearch.create();
		sc.setParameters("vlanDbId", vlanDbId);
		return listBy(sc);
	}
	
	public List<IPAddressVO> listByDcIdIpAddress(long dcId, String ipAddress) {
		SearchCriteria sc = DcIpSearch.create();
		sc.setParameters("dataCenterId", dcId);
		sc.setParameters("ipAddress", ipAddress);
		return listBy(sc);
	}
	
	@Override @DB
	public int countIPs(long dcId, long vlanDbId, long accountId, boolean onlyCountAllocated) {
		Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		int ipCount = 0;
		try {
			String sql = "SELECT count(*) from `cloud`.`user_ip_address` where data_center_id = " + dcId;
			
			if (vlanDbId != -1) {
				sql += " AND vlan_db_id = " + vlanDbId;
			}
			
			if (accountId != -1) {
				sql += " AND account_id = " + accountId;
			}
			
			if (onlyCountAllocated) {
				sql += " AND allocated IS NOT NULL";
			}
			
            pstmt = txn.prepareAutoCloseStatement(sql);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
            	ipCount = rs.getInt(1);
            }
            
        } catch (Exception e) {
            s_logger.warn("Exception counting IP addresses", e);
        }
        
        return ipCount;
	}
}