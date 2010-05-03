/**
 * Copyright (c) 2008, 2009, VMOps Inc.
 *
 * This code is Copyrighted and must not be reused, modified, or redistributed without the explicit consent of VMOps.
 */
package com.vmops.resource.xen.dao;

import javax.ejb.Local;

import com.vmops.resource.xen.XenServerPoolVO;
import com.vmops.utils.db.GenericDaoBase;

@Local(value=XenServerPoolDao.class)
public class XenServerPoolDaoImpl extends GenericDaoBase<XenServerPoolVO, String> implements XenServerPoolDao {
    protected XenServerPoolDaoImpl() {
    }
}
