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

package com.vmops.network.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.List;

import javax.ejb.Local;

import com.vmops.network.IPAddressVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;

@Local(value={IPAddressDao.class})
public class IPAddressDaoImpl extends GenericDaoBase<IPAddressVO, String> implements IPAddressDao {

	private static final String COUNT_ALL_PUBLIC_IPS = "SELECT count(*) from `vmops`.`user_ip_address` where data_center_id = ?";
	private static final String COUNT_ALL_PUBLIC_IPS_IN_VLAN = "SELECT count(*) from `vmops`.`user_ip_address` where data_center_id = ? AND vlan_db_id = ?";
	private static final String COUNT_ALLOCATED_PUBLIC_IPS = "SELECT count(*) from `vmops`.`user_ip_address` where data_center_id = ? AND allocated is not null";
	private static final String COUNT_ALLOCATED_PUBLIC_IPS_IN_VLAN = "SELECT count(*) from `vmops`.`user_ip_address` where data_center_id = ? AND vlan_db_id = ? AND allocated is not null";
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
	    AccountDcSearch.addAnd("accountId", AccountDcSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountDcSearch.addAnd("dataCenterId", AccountDcSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    AccountDcSearch.done();

	    AccountDcSnatSearch = createSearchBuilder();
	    AccountDcSnatSearch.addAnd("accountId", AccountDcSnatSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.addAnd("dataCenterId", AccountDcSnatSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.addAnd("sourceNat", AccountDcSnatSearch.entity().isSourceNat(), SearchCriteria.Op.EQ);
	    AccountDcSnatSearch.done();
	    
	    AddressSearch = createSearchBuilder();
	    AddressSearch.addAnd("address", AddressSearch.entity().getAddress(), SearchCriteria.Op.EQ);
	    AddressSearch.done();
	    
	    DcSearchAll = createSearchBuilder();
	    DcSearchAll.addAnd("dataCenterId", DcSearchAll.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    DcSearchAll.done();
	    
	    DcIpSearch = createSearchBuilder();
	    DcIpSearch.addAnd("dataCenterId", DcIpSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
	    DcIpSearch.addAnd("ipAddress", DcIpSearch.entity().getAddress(), SearchCriteria.Op.EQ);
	    DcIpSearch.done();
	    
	    VlanDbIdSearchUnallocated = createSearchBuilder();
	    VlanDbIdSearchUnallocated.addAnd("allocated", VlanDbIdSearchUnallocated.entity().getAllocated(), SearchCriteria.Op.NULL);
	    VlanDbIdSearchUnallocated.addAnd("vlanDbId", VlanDbIdSearchUnallocated.entity().getVlanDbId(), SearchCriteria.Op.EQ);
	    //VlanDbIdSearchUnallocated.addRetrieve("ipAddress", VlanDbIdSearchUnallocated.entity().getAddress());
	    VlanDbIdSearchUnallocated.done();
	    
	    VlanDbIdSearch = createSearchBuilder();
	    VlanDbIdSearch.addAnd("vlanDbId", VlanDbIdSearch.entity().getVlanDbId(), SearchCriteria.Op.EQ);
	    VlanDbIdSearch.done();
	    
	    AccountIdSearch = createSearchBuilder();
	    AccountIdSearch.addAnd("accountId", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
	    AccountIdSearch.done();

        AccountIdSourceNatSearch = createSearchBuilder();
        AccountIdSourceNatSearch.addAnd("accountId", AccountIdSourceNatSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSourceNatSearch.addAnd("sourceNat", AccountIdSourceNatSearch.entity().isSourceNat(), SearchCriteria.Op.EQ);
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
	
	@Override
	public int countIPs(long dcId, long vlanDbId, boolean onlyCountAllocated) {
		Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
		int ipCount = 0;
		try {
			String sql = "";
			
			if (onlyCountAllocated) {
				if (vlanDbId == -1)
					sql = COUNT_ALLOCATED_PUBLIC_IPS;
				else
					sql = COUNT_ALLOCATED_PUBLIC_IPS_IN_VLAN;
			} else {
				if (vlanDbId == -1)
					sql = COUNT_ALL_PUBLIC_IPS;
				else
					sql = COUNT_ALL_PUBLIC_IPS_IN_VLAN;
			}
			
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, dcId);
            if (vlanDbId != -1)
            	pstmt.setLong(2, vlanDbId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) ipCount = rs.getInt(1);
            
        } catch (Exception e) {
            s_logger.warn("Exception counting IP addresses", e);
        }
        return ipCount;
	}
}
