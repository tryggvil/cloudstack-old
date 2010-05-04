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

import java.util.List;

import javax.ejb.Local;

import com.vmops.storage.StoragePoolVO;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.SearchCriteria.Op;

@Local(value={StoragePoolDao.class})
public class StoragePoolDaoImpl extends GenericDaoBase<StoragePoolVO, Long>  implements StoragePoolDao {
    protected final SearchBuilder<StoragePoolVO> NameSearch;
	protected final SearchBuilder<StoragePoolVO> UUIDSearch;
	protected final SearchBuilder<StoragePoolVO> DatacenterSearch;
	protected final SearchBuilder<StoragePoolVO> DcPodSearch;
    protected final SearchBuilder<StoragePoolVO> HostSearch;
    protected final SearchBuilder<StoragePoolVO> HostPathDcPodSearch;
    protected final SearchBuilder<StoragePoolVO> HostPathDcSearch;




    protected StoragePoolDaoImpl() {
    	NameSearch = createSearchBuilder();
        NameSearch.and("name", NameSearch.entity().getName(), SearchCriteria.Op.EQ);
        NameSearch.done();
        
    	UUIDSearch = createSearchBuilder();
        UUIDSearch.and("uuid", UUIDSearch.entity().getUuid(), SearchCriteria.Op.EQ);
        UUIDSearch.done();
        
    	DatacenterSearch = createSearchBuilder();
        DatacenterSearch.and("datacenterId", DatacenterSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DatacenterSearch.done();
        
    	DcPodSearch = createSearchBuilder();
    	DcPodSearch.and("datacenterId", DcPodSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
    	DcPodSearch.and("podId", DcPodSearch.entity().getPodId(), SearchCriteria.Op.EQ);
    	DcPodSearch.done();
        
        HostSearch = createSearchBuilder();
        HostSearch.and("host", HostSearch.entity().getHostAddress(), SearchCriteria.Op.EQ);
        HostSearch.done();
        
        HostPathDcPodSearch = createSearchBuilder();
        HostPathDcPodSearch.and("hostAddress", HostPathDcPodSearch.entity().getHostAddress(), SearchCriteria.Op.EQ);
        HostPathDcPodSearch.and("path", HostPathDcPodSearch.entity().getPath(), SearchCriteria.Op.EQ);
        HostPathDcPodSearch.and("datacenterId", HostPathDcPodSearch.entity().getDataCenterId(), Op.EQ);
        HostPathDcPodSearch.and("podId", HostPathDcPodSearch.entity().getPodId(), Op.EQ);
        HostPathDcPodSearch.done();
        
        HostPathDcSearch = createSearchBuilder();
        HostPathDcSearch.and("hostAddress", HostPathDcSearch.entity().getHostAddress(), SearchCriteria.Op.EQ);
        HostPathDcSearch.and("path", HostPathDcSearch.entity().getPath(), SearchCriteria.Op.EQ);
        HostPathDcSearch.and("datacenterId", HostPathDcSearch.entity().getDataCenterId(), Op.EQ);
        HostPathDcSearch.done();

    }
    
	@Override
	public List<StoragePoolVO> findPoolByName(String name) {
		SearchCriteria sc = NameSearch.create();
        sc.setParameters("name", name);
        return listBy(sc);
	}


	@Override
	public StoragePoolVO findPoolByUUID(String uuid) {
		SearchCriteria sc = UUIDSearch.create();
        sc.setParameters("uuid", uuid);
        return findOneBy(sc);
	}


	@Override
	public List<StoragePoolVO> listByDataCenterId(long datacenterId) {
		SearchCriteria sc = DatacenterSearch.create();
        sc.setParameters("datacenterId", datacenterId);
        return listBy(sc);
	}


	@Override
	public void updateAvailable(long id, long available) {
		StoragePoolVO pool = createForUpdate(id);
		pool.setAvailableBytes(available);
		update(id, pool);
	}


	@Override
	public void updateCapacity(long id, long capacity) {
		StoragePoolVO pool = createForUpdate(id);
		pool.setCapacityBytes(capacity);
		update(id, pool);

	}
	
    @Override
    public List<StoragePoolVO> listByStorageHost(String hostFqdnOrIp) {
        SearchCriteria sc = HostSearch.create();
        sc.setParameters("host", hostFqdnOrIp);
        return listBy(sc);
    }

    @Override
    public StoragePoolVO findPoolByHostPath(long datacenterId, Long podId, String host, String path) {
        SearchCriteria sc = HostPathDcPodSearch.create();
        sc.setParameters("hostAddress", host);
        sc.setParameters("path", path);
        sc.setParameters("datacenterId", datacenterId);
        sc.setParameters("podId", podId);
        
        return findOneBy(sc);
    }

	@Override
	public List<StoragePoolVO> listByDataCenterPodId(long datacenterId, long podId) {
		SearchCriteria sc = DcPodSearch.create();
        sc.setParameters("datacenterId", datacenterId);
        sc.setParameters("podId", podId);
        return listBy(sc);
	}

	@Override
	public List<StoragePoolVO> listPoolByHostPath(long dcId, String host, String path) {
        SearchCriteria sc = HostPathDcSearch.create();
        sc.setParameters("hostAddress", host);
        sc.setParameters("path", path);
        sc.setParameters("datacenterId", dcId);
        
        return listBy(sc);
	}
}
