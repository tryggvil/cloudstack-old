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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;

import com.vmops.storage.DiskOfferingVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;

@Local(value={DiskOfferingDao.class})
public class DiskOfferingDaoImpl extends GenericDaoBase<DiskOfferingVO, Long> implements DiskOfferingDao {

    private static final String FIND_DISK_OFFERING_BY_INSTANCE_ID = "SELECT do.id, do.name, do.display_text, do.disk_size, do.mirrored" +
                                                                    "  FROM disk_offering do, vm_disk vmd" +
                                                                    "  WHERE ((vmd.instance_id = ?) AND (vmd.removed IS NULL) AND (vmd.disk_offering_id = do.id))";
    private final SearchBuilder<DiskOfferingVO> DomainIdSearch;

    protected DiskOfferingDaoImpl() {
        DomainIdSearch  = createSearchBuilder();
        DomainIdSearch.addAnd("domainId", DomainIdSearch.entity().getDomainId(), SearchCriteria.Op.EQ);
        DomainIdSearch.done();
    }

    @Override
    public List<DiskOfferingVO> listByInstanceId(long instanceId) {
        // FIXME:  this should return a list, but for now it's hard coded to 1 disk offering per instance
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        List<DiskOfferingVO> offerings = new ArrayList<DiskOfferingVO>();
        try {
            pstmt = txn.prepareAutoCloseStatement(FIND_DISK_OFFERING_BY_INSTANCE_ID);
            pstmt.setLong(1, instanceId);
            ResultSet rs = pstmt.executeQuery();
            DiskOfferingVO diskOffering = null;
            while (rs.next()) {
                diskOffering = new DiskOfferingVO();
                diskOffering.setId(rs.getLong(1));
                diskOffering.setName(rs.getString(2));
                diskOffering.setDisplayText(rs.getString(3));
                diskOffering.setDiskSize(rs.getLong(4));
                diskOffering.setMirrored(rs.getBoolean(5));
                offerings.add(diskOffering);
            }
        } catch (Exception e) {
            s_logger.warn(e);
        }
        return offerings;
    }

    @Override
    public List<DiskOfferingVO> listByDomainId(long domainId) {
        SearchCriteria sc = DomainIdSearch.create();
        sc.setParameters("domainId", domainId);
        // FIXME:  this should not be exact match, but instead should find all available disk offerings from parent domains
        return listActiveBy(sc);
    }
}
