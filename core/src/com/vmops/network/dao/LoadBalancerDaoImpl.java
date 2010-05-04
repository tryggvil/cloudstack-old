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

package com.vmops.network.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;

import com.vmops.network.LoadBalancerVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;

@Local(value={LoadBalancerDao.class})
public class LoadBalancerDaoImpl extends GenericDaoBase<LoadBalancerVO, Long> implements LoadBalancerDao {
    private static final String LIST_INSTNACES_BY_LOAD_BALANCER = "SELECT vm.id " +
                                                                  "    FROM vm_instance vm, load_balancer lb, ip_forwarding fwd, user_ip_address ip " +
                                                                  "    WHERE lb.id = ? AND " +
                                                                  "          fwd.group_id = lb.id AND " +
                                                                  "          fwd.forwarding = 0 AND " +
                                                                  "          fwd.private_ip_address = vm.private_ip_address AND " +
                                                                  "          lb.ip_address = ip.public_ip_address AND " +
                                                                  "          ip.data_center_id = vm.data_center_id ";
    private final SearchBuilder<LoadBalancerVO> ListByIp;
    private final SearchBuilder<LoadBalancerVO> IpAndPublicPortSearch;

    protected LoadBalancerDaoImpl() {
        ListByIp  = createSearchBuilder();
        ListByIp.and("ipAddress", ListByIp.entity().getIpAddress(), SearchCriteria.Op.EQ);
        ListByIp.done();

        IpAndPublicPortSearch = createSearchBuilder();
        IpAndPublicPortSearch.and("ipAddress", IpAndPublicPortSearch.entity().getIpAddress(), SearchCriteria.Op.EQ);
        IpAndPublicPortSearch.and("publicPort", IpAndPublicPortSearch.entity().getPublicPort(), SearchCriteria.Op.EQ);
        IpAndPublicPortSearch.done();
    }

    @Override
    public List<Long> listInstancesByLoadBalancer(long loadBalancerId) {
        Transaction txn = Transaction.currentTxn();
        String sql = LIST_INSTNACES_BY_LOAD_BALANCER;
        PreparedStatement pstmt = null;
        List<Long> instanceList = new ArrayList<Long>();
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, loadBalancerId);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Long vmId = rs.getLong(1);
                instanceList.add(vmId);
            }
        } catch (Exception ex) {
            s_logger.error("error getting recent usage network stats", ex);
        }
        return instanceList;
    }

    @Override
    public List<LoadBalancerVO> listByIpAddress(String ipAddress) {
        SearchCriteria sc = ListByIp.create();
        sc.setParameters("ipAddress", ipAddress);
        return listActiveBy(sc);
    }

    @Override
    public LoadBalancerVO findByIpAddressAndPublicPort(String ipAddress, String publicPort) {
        SearchCriteria sc = IpAndPublicPortSearch.create();
        sc.setParameters("ipAddress", ipAddress);
        sc.setParameters("publicPort", publicPort);
        return findOneActiveBy(sc);
    }
}
