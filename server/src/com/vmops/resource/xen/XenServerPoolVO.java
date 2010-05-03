/**
 * Copyright (c) 2008, 2009, VMOps Inc.
 *
 * This code is Copyrighted and must not be reused, modified, or redistributed without the explicit consent of VMOps.
 */
package com.vmops.resource.xen;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="xen_server_pool")
public class XenServerPoolVO {
    
    @Id
    @Column(name="pool_uuid", updatable=false, nullable=false)
    String poolUuid;
    
    @Column(name="pod_id", updatable=false, nullable=false)
    long podId;
    
    @Column(name="name")
    String name;
    
    public XenServerPoolVO(String poolUuid, long podId) {
        this.poolUuid = poolUuid;
        this.podId = podId;
    }
    
    protected XenServerPoolVO() {
    }
    
    public long getPodId() {
        return podId;
    }
    
    public String getPoolUuid() {
        return poolUuid;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
}
