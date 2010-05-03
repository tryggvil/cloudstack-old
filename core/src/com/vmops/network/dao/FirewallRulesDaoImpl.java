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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import com.vmops.network.FirewallRuleVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.exception.VmopsRuntimeException;

@Local(value = { FirewallRulesDao.class })
public class FirewallRulesDaoImpl extends GenericDaoBase<FirewallRuleVO, Long> implements FirewallRulesDao {

    public static String SELECT_IP_FORWARDINGS_BY_USERID_SQL   = null;
    public static String SELECT_IP_FORWARDINGS_BY_USERID_AND_DCID_SQL = null;

    public static final String           DELETE_IP_FORWARDING_BY_IPADDRESS_SQL = "DELETE FROM ip_forwarding WHERE public_ip_address = ?";
    public static final String           DISABLE_IP_FORWARDING_BY_IPADDRESS_SQL = "UPDATE  ip_forwarding set enabled=0 WHERE public_ip_address = ?";


    protected SearchBuilder<FirewallRuleVO> FWByIPSearch;
    protected SearchBuilder<FirewallRuleVO> FWByIPAndForwardingSearch;
    protected SearchBuilder<FirewallRuleVO> FWByIPPortAndForwardingSearch;
    protected SearchBuilder<FirewallRuleVO> FWByIPPortProtoSearch;
    protected SearchBuilder<FirewallRuleVO> FWByIPPortAlgoSearch;
    protected SearchBuilder<FirewallRuleVO> FWByPrivateIPSearch;
    protected SearchBuilder<FirewallRuleVO> RulesExcludingPubIpPort;
    protected SearchBuilder<FirewallRuleVO> FWByGroupId;
    protected SearchBuilder<FirewallRuleVO> FWByGroupAndPrivateIp;

    protected FirewallRulesDaoImpl() {
    }
    
    @Override
	public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            return false;
        }
        
        SELECT_IP_FORWARDINGS_BY_USERID_SQL = buildSelectByUserIdSql();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(SELECT_IP_FORWARDINGS_BY_USERID_SQL);
        }
        
        SELECT_IP_FORWARDINGS_BY_USERID_AND_DCID_SQL = buildSelectByUserIdAndDatacenterIdSql();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(SELECT_IP_FORWARDINGS_BY_USERID_AND_DCID_SQL);
        }

        FWByIPSearch = createSearchBuilder();
        FWByIPSearch.addAnd("publicIpAddress", FWByIPSearch.entity().getPublicIpAddress(), SearchCriteria.Op.EQ);
        FWByIPSearch.done();
        
        FWByIPAndForwardingSearch = createSearchBuilder();
        FWByIPAndForwardingSearch.addAnd("publicIpAddress", FWByIPAndForwardingSearch.entity().getPublicIpAddress(), SearchCriteria.Op.EQ);
        FWByIPAndForwardingSearch.addAnd("forwarding", FWByIPAndForwardingSearch.entity().isForwarding(), SearchCriteria.Op.EQ);
        FWByIPAndForwardingSearch.done();
        
        FWByIPPortAndForwardingSearch = createSearchBuilder();
        FWByIPPortAndForwardingSearch.addAnd("publicIpAddress", FWByIPPortAndForwardingSearch.entity().getPublicIpAddress(), SearchCriteria.Op.EQ);
        FWByIPPortAndForwardingSearch.addAnd("publicPort", FWByIPPortAndForwardingSearch.entity().getPublicPort(), SearchCriteria.Op.EQ);
        FWByIPPortAndForwardingSearch.addAnd("forwarding", FWByIPPortAndForwardingSearch.entity().isForwarding(), SearchCriteria.Op.EQ);
        FWByIPPortAndForwardingSearch.done();
        
        FWByIPPortProtoSearch = createSearchBuilder();
        FWByIPPortProtoSearch.addAnd("publicIpAddress", FWByIPPortProtoSearch.entity().getPublicIpAddress(), SearchCriteria.Op.EQ);
        FWByIPPortProtoSearch.addAnd("publicPort", FWByIPPortProtoSearch.entity().getPublicPort(), SearchCriteria.Op.EQ);
        FWByIPPortProtoSearch.addAnd("protocol", FWByIPPortProtoSearch.entity().getProtocol(), SearchCriteria.Op.EQ);
        FWByIPPortProtoSearch.done();
        
        FWByIPPortAlgoSearch = createSearchBuilder();
        FWByIPPortAlgoSearch.addAnd("publicIpAddress", FWByIPPortAlgoSearch.entity().getPublicIpAddress(), SearchCriteria.Op.EQ);
        FWByIPPortAlgoSearch.addAnd("publicPort", FWByIPPortAlgoSearch.entity().getPublicPort(), SearchCriteria.Op.EQ);
        FWByIPPortAlgoSearch.addAnd("algorithm", FWByIPPortAlgoSearch.entity().getAlgorithm(), SearchCriteria.Op.EQ);
        FWByIPPortAlgoSearch.done();

        FWByPrivateIPSearch = createSearchBuilder();
        FWByPrivateIPSearch.addAnd("privateIpAddress", FWByPrivateIPSearch.entity().getPrivateIpAddress(), SearchCriteria.Op.EQ);
        FWByPrivateIPSearch.done();

        RulesExcludingPubIpPort = createSearchBuilder();
        RulesExcludingPubIpPort.addAnd("publicIpAddress", RulesExcludingPubIpPort.entity().getPrivateIpAddress(), SearchCriteria.Op.EQ);
        RulesExcludingPubIpPort.addAnd("groupId", RulesExcludingPubIpPort.entity().getGroupId(), SearchCriteria.Op.NEQ);
        RulesExcludingPubIpPort.addAnd("forwarding", RulesExcludingPubIpPort.entity().isForwarding(), SearchCriteria.Op.EQ);
        RulesExcludingPubIpPort.done();

        FWByGroupId = createSearchBuilder();
        FWByGroupId.addAnd("groupId", FWByGroupId.entity().getGroupId(), SearchCriteria.Op.EQ);
        FWByGroupId.addAnd("forwarding", FWByGroupId.entity().isForwarding(), SearchCriteria.Op.EQ);
        FWByGroupId.done();

        FWByGroupAndPrivateIp = createSearchBuilder();
        FWByGroupAndPrivateIp.addAnd("groupId", FWByGroupAndPrivateIp.entity().getGroupId(), SearchCriteria.Op.EQ);
        FWByGroupAndPrivateIp.addAnd("privateIpAddress", FWByGroupAndPrivateIp.entity().getPrivateIpAddress(), SearchCriteria.Op.EQ);
        FWByGroupAndPrivateIp.addAnd("forwarding", FWByGroupAndPrivateIp.entity().isForwarding(), SearchCriteria.Op.EQ);
        FWByGroupAndPrivateIp.done();

        return true;
    }

    protected String buildSelectByUserIdSql() {
        StringBuilder sql = createPartialSelectSql(null, true);
        sql.insert(sql.length() - 6, ", user_ip_address ");
        sql.append("ip_forwarding.public_ip_address = user_ip_address.public_ip_address AND user_ip_address.account_id = ?");

        return sql.toString();
    }
    
    protected String buildSelectByUserIdAndDatacenterIdSql() {
    	return "SELECT i.id, i.group_id, i.public_ip_address, i.public_port, i.private_ip_address, i.private_port, i.enabled, i.protocol, i.forwarding, i.algorithm FROM ip_forwarding i, user_ip_address u WHERE i.public_ip_address=u.public_ip_address AND u.account_id=? AND u.data_center_id=?";
    }

    public List<FirewallRuleVO> listIPForwarding(String publicIPAddress, boolean forwarding) {
        SearchCriteria sc = FWByIPAndForwardingSearch.create();
        sc.setParameters("publicIpAddress", publicIPAddress);
        sc.setParameters("forwarding", forwarding);
        return listActiveBy(sc);
    }

    @Override
    public List<FirewallRuleVO> listIPForwarding(long userId) {
        Transaction txn = Transaction.currentTxn();
        List<FirewallRuleVO> forwardings = new ArrayList<FirewallRuleVO>();
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(SELECT_IP_FORWARDINGS_BY_USERID_SQL);
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                forwardings.add(toEntityBean(rs, false));
            }
        } catch (Exception e) {
        	s_logger.warn(e);
        }
        return forwardings;
    }
    
    public List<FirewallRuleVO> listIPForwarding(long userId, long dcId) {
    	Transaction txn = Transaction.currentTxn();
        List<FirewallRuleVO> forwardings = new ArrayList<FirewallRuleVO>();
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(SELECT_IP_FORWARDINGS_BY_USERID_AND_DCID_SQL);
            pstmt.setLong(1, userId);
            pstmt.setLong(2, dcId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                forwardings.add(toEntityBean(rs, false));
            }
        } catch (Exception e) {
        	s_logger.warn(e);
        }
        return forwardings;
    }

    @Override
    public void deleteIPForwardingByPublicIpAddress(String ipAddress) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(DELETE_IP_FORWARDING_BY_IPADDRESS_SQL);
            pstmt.setString(1, ipAddress);
            pstmt.executeUpdate();
        } catch (Exception e) {
        	s_logger.warn(e);
        }
    }

    @Override
    public List<FirewallRuleVO> listIPForwarding(String publicIPAddress) {
        SearchCriteria sc = FWByIPSearch.create();
        sc.setParameters("publicIpAddress", publicIPAddress);
        return listActiveBy(sc);
    }

	@Override
	public List<FirewallRuleVO> listIPForwardingForUpdate(String publicIPAddress) {
		SearchCriteria sc = FWByIPSearch.create();
        sc.setParameters("publicIpAddress", publicIPAddress);
        return listActiveBy(sc, null);
	}

	@Override
	public List<FirewallRuleVO> listIPForwardingForUpdate(String publicIp, boolean fwding) {
        SearchCriteria sc = FWByIPAndForwardingSearch.create();
        sc.setParameters("publicIpAddress", publicIp);
        sc.setParameters("forwarding", fwding);
        return search(sc, null);
	}

	@Override
	public List<FirewallRuleVO> listIPForwardingForUpdate(String publicIp,
			String publicPort, String proto) {
		SearchCriteria sc = FWByIPPortProtoSearch.create();
        sc.setParameters("publicIpAddress", publicIp);
        sc.setParameters("publicPort", publicPort);
        sc.setParameters("protocol", proto);
        return search(sc, null);
	}
	
	@Override
	public List<FirewallRuleVO> listLoadBalanceRulesForUpdate(String publicIp,
			String publicPort, String algo) {
		SearchCriteria sc = FWByIPPortAlgoSearch.create();
        sc.setParameters("publicIpAddress", publicIp);
        sc.setParameters("publicPort", publicPort);
        sc.setParameters("algorithm", algo);
        return listActiveBy(sc, null);
	}

	@Override
	public List<FirewallRuleVO> listIPForwarding(String publicIPAddress,
			String port, boolean forwarding) {
		SearchCriteria sc = FWByIPPortAndForwardingSearch.create();
        sc.setParameters("publicIpAddress", publicIPAddress);
        sc.setParameters("publicPort", port);
        sc.setParameters("forwarding", forwarding);

        return listActiveBy(sc);
	}

	@Override
	public void disableIPForwarding(String publicIPAddress) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        try {
        	txn.start();
            pstmt = txn.prepareAutoCloseStatement(DISABLE_IP_FORWARDING_BY_IPADDRESS_SQL);
            pstmt.setString(1, publicIPAddress);
            pstmt.executeUpdate();
            txn.commit();
        } catch (Exception e) {
            txn.rollback();
            throw new VmopsRuntimeException("DB Exception ", e);
        }
	}

	@Override
    public List<FirewallRuleVO> listRulesExcludingPubIpPort(String publicIpAddress, long securityGroupId) {
        SearchCriteria sc = RulesExcludingPubIpPort.create();
        sc.setParameters("publicIpAddress", publicIpAddress);
        sc.setParameters("groupId", securityGroupId);
        sc.setParameters("forwarding", false);
        return listActiveBy(sc);
    }

	@Override
    public List<FirewallRuleVO> listBySecurityGroupId(long securityGroupId) {
	    SearchCriteria sc = FWByGroupId.create();
	    sc.setParameters("groupId", securityGroupId);
        sc.setParameters("forwarding", Boolean.TRUE);
	    return listActiveBy(sc);
	}

    @Override
    public List<FirewallRuleVO> listForwardingByPubAndPrivIp(boolean forwarding, String publicIPAddress, String privateIp) {
        SearchCriteria sc = FWByIPAndForwardingSearch.create();
        sc.setParameters("publicIpAddress", publicIPAddress);
        sc.setParameters("forwarding", forwarding);
        sc.addAnd("privateIpAddress", SearchCriteria.Op.EQ, privateIp);
        return listActiveBy(sc);
    }

    @Override
    public List<FirewallRuleVO> listByLoadBalancerId(long loadBalancerId) {
        SearchCriteria sc = FWByGroupId.create();
        sc.setParameters("groupId", loadBalancerId);
        sc.setParameters("forwarding", Boolean.FALSE);
        return listActiveBy(sc);
    }

    @Override
    public FirewallRuleVO findByGroupAndPrivateIp(long groupId, String privateIp, boolean forwarding) {
        SearchCriteria sc = FWByGroupAndPrivateIp.create();
        sc.setParameters("groupId", groupId);
        sc.setParameters("privateIpAddress", privateIp);
        sc.setParameters("forwarding", forwarding);
        return findOneActiveBy(sc);
        
    }
}
