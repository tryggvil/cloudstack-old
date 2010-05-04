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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.ejb.Local;

import com.vmops.async.AsyncInstanceCreateStatus;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.MirrorState;
import com.vmops.storage.Volume.VolumeType;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.db.SearchCriteria.Op;
import com.vmops.utils.exception.VmopsRuntimeException;

@Local (value={VolumeDao.class})
public class VolumeDaoImpl extends GenericDaoBase<VolumeVO, Long> implements VolumeDao {
    protected SearchBuilder<VolumeVO> PathAndHostIdSearch;
    protected SearchBuilder<VolumeVO> AccountIdSearch;
    protected SearchBuilder<VolumeVO> AccountDataCenterSearch;
    protected SearchBuilder<VolumeVO> HostIdSearch;
    protected SearchBuilder<VolumeVO> PoolIdSearch;
    protected SearchBuilder<VolumeVO> InstanceIdSearch;
    protected SearchBuilder<VolumeVO> InstanceAndTypeSearch;
    protected SearchBuilder<VolumeVO> InstanceIdDestroyedSearch;
    protected SearchBuilder<VolumeVO> HostIdAndMirrStateSearch;
    protected SearchBuilder<VolumeVO> HostIdVmIdVolTypeSearch;
    protected SearchBuilder<VolumeVO> HostIdVmIdSearch;
    protected SearchBuilder<VolumeVO> MirrorSearch;

    protected static final String SELECT_VM_SQL = "SELECT DISTINCT instance_id from volumes v where v.host_id = ? and v.mirror_state = ?";
    protected static final String SELECT_VM_ID_SQL = "SELECT DISTINCT instance_id from volumes v where v.host_id = ?";

    
    @Override
    public List<Long> findVmsStoredOnHost(long hostId) {
        Transaction txn = Transaction.currentTxn();
        PreparedStatement pstmt = null;
        List<Long> result = new ArrayList<Long>();

        try {
            String sql = SELECT_VM_ID_SQL;
            pstmt = txn.prepareAutoCloseStatement(sql);
            pstmt.setLong(1, hostId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(rs.getLong(1));
            }
            return result;
        } catch (SQLException e) {
            throw new VmopsRuntimeException("DB Exception on: " + SELECT_VM_SQL, e);
        } catch (Throwable e) {
            throw new VmopsRuntimeException("Caught: " + SELECT_VM_SQL, e);
        }
    }

    @Override
    public List<VolumeVO> findByHost(long hostId) {
        SearchCriteria sc = HostIdSearch.create();
        sc.setParameters("hostId", hostId);
        return listActiveBy(sc);
    }

    @Override
    public List<VolumeVO> findByAccount(long accountId) {
        SearchCriteria sc = AccountIdSearch.create();
        sc.setParameters("accountId", accountId);
        return listActiveBy(sc);
    }
    
    @Override
    public List<VolumeVO> findByInstance(long id) {
        SearchCriteria sc = InstanceIdSearch.create();
        sc.setParameters("instanceId", id);
	    return listActiveBy(sc);
	}
    
    @Override
    public VolumeVO findByPath(long hostId, String path) {
        SearchCriteria sc = PathAndHostIdSearch.create();
        sc.setParameters("path", path);
        sc.setParameters("hostId", hostId);
        return findOneActiveBy(sc);
    }

	@Override
	public List<VolumeVO> findByInstanceAndType(long id, VolumeType vType) {
        SearchCriteria sc = InstanceAndTypeSearch.create();
        sc.setParameters("instanceId", id);
        sc.setParameters("vType", vType.toString());
	    return listActiveBy(sc);
	}
	
	@Override
	public List<VolumeVO> findByInstanceIdDestroyed(long vmId) {
		SearchCriteria sc = InstanceIdDestroyedSearch.create();
		sc.setParameters("instanceId", vmId);
		sc.setParameters("destroyed", true);
		return listBy(sc);
	}
	
	@Override
	public List<VolumeVO> findByAccountAndDataCenter(long accountId, long dcId) {
		SearchCriteria sc = AccountDataCenterSearch.create();
        sc.setParameters("account", accountId);
        sc.setParameters("dc", dcId);
        sc.setParameters("destroyed", false);
        sc.setParameters("status", AsyncInstanceCreateStatus.Created);
        
        return listBy(sc);
	}

	@Override
	public List<VolumeVO> findByHostAndMirrorState(long hostId, Volume.MirrorState mState) {
		SearchCriteria sc = HostIdAndMirrStateSearch.create();
        sc.setParameters("hostId", hostId);
        sc.setParameters("mirrorState", mState.toString());
	    return listActiveBy(sc);
	}

	@Override
	public List<Long> findVMInstancesByStorageHost(long hostId, Volume.MirrorState mirrState) {
		
		Transaction txn = Transaction.currentTxn();
		PreparedStatement pstmt = null;
        List<Long> result = new ArrayList<Long>();

		try {
			String sql = SELECT_VM_SQL;
			pstmt = txn.prepareAutoCloseStatement(sql);
			pstmt.setLong(1, hostId);
			pstmt.setString(2, mirrState.toString());
			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				result.add(rs.getLong(1));
			}
			return result;
		} catch (SQLException e) {
			throw new VmopsRuntimeException("DB Exception on: " + SELECT_VM_SQL, e);
		} catch (Throwable e) {
			throw new VmopsRuntimeException("Caught: " + SELECT_VM_SQL, e);
		}
	}

	@Override
	public VolumeVO findMirrorVolumeByTypeAndInstance(long hostId, long vmId, VolumeType vType) {
		SearchCriteria sc = HostIdVmIdVolTypeSearch.create();
        sc.setParameters("hostId", hostId);
        sc.setParameters("instanceId", vmId);
        sc.setParameters("vType", vType.toString());

	    return findOneBy(sc);
	}

	@Override
	public List<VolumeVO> findByHostIdAndInstance(long hostId, long vmId) {
		SearchCriteria sc = HostIdVmIdSearch.create();
        sc.setParameters("hostId", hostId);
        sc.setParameters("instanceId", vmId);

	    return listBy(sc);
	}

	@Override
	public List<VolumeVO> findStrandedMirrorVolumes() {
		SearchCriteria sc = MirrorSearch.create();
        sc.setParameters("mirrorState", MirrorState.ACTIVE.toString());

	    return listBy(sc);
	}
	
    @Override
    public void deleteVolumesByInstance(long instanceId) {
        Transaction txn = Transaction.currentTxn();
        try {
        	PreparedStatement stmt = txn.prepareAutoCloseStatement("delete from volumes where instance_id=?");
        	stmt.setLong(1, instanceId);
        	stmt.execute();
        } catch(Exception e) {
        	s_logger.error("Unable to delete volumes by VM instance", e);
        }
    }
    
    @Override
    public void attachVolume(long volumeId, long vmId, String nameLabel) {
    	VolumeVO volume = createForUpdate(volumeId);
    	volume.setInstanceId(vmId);
    	volume.setNameLabel(nameLabel);
    	update(volumeId, volume);
    }
    
    @Override
    public void detachVolume(long volumeId) {
    	VolumeVO volume = createForUpdate(volumeId);
    	volume.setInstanceId(null);
    	volume.setNameLabel("detached");
    	update(volumeId, volume);
    }
    
    @Override
    public void destroyVolume(long volumeId) {
    	VolumeVO volume = createForUpdate(volumeId);
    	volume.setDestroyed(true);
    	update(volumeId, volume);
    }
    
    @Override
    public void recoverVolume(long volumeId) {
    	VolumeVO volume = createForUpdate(volumeId);
    	volume.setDestroyed(false);
    	update(volumeId, volume);
    }
    
	protected VolumeDaoImpl() {
        PathAndHostIdSearch = createSearchBuilder();
        PathAndHostIdSearch.and("path", PathAndHostIdSearch.entity().getPath(), SearchCriteria.Op.EQ);
        PathAndHostIdSearch.and("hostId", PathAndHostIdSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        PathAndHostIdSearch.done();
        
        AccountIdSearch = createSearchBuilder();
        AccountIdSearch.and("accountId", AccountIdSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountIdSearch.done();
        
        AccountDataCenterSearch = createSearchBuilder();
        AccountDataCenterSearch.and("account", AccountDataCenterSearch.entity().getAccountId(), SearchCriteria.Op.EQ);
        AccountDataCenterSearch.and("dc", AccountDataCenterSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        AccountDataCenterSearch.and("destroyed", AccountDataCenterSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        AccountDataCenterSearch.and("status", AccountDataCenterSearch.entity().getStatus(), SearchCriteria.Op.EQ);
        AccountDataCenterSearch.done();
        
        HostIdSearch = createSearchBuilder();
        HostIdSearch.and("hostId", HostIdSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostIdSearch.done();
        
        PoolIdSearch = createSearchBuilder();
        PoolIdSearch.and("poolId", PoolIdSearch.entity().getPoolId(), SearchCriteria.Op.EQ);
        PoolIdSearch.done();
        
        HostIdAndMirrStateSearch = createSearchBuilder();
        HostIdAndMirrStateSearch.and("hostId", HostIdAndMirrStateSearch.entity().getHostId(), SearchCriteria.Op.EQ);
        HostIdAndMirrStateSearch.and("mirrorState", HostIdAndMirrStateSearch.entity().getMirrorState(), SearchCriteria.Op.EQ);
        HostIdAndMirrStateSearch.done();
        
        InstanceIdSearch = createSearchBuilder();
        InstanceIdSearch.and("instanceId", InstanceIdSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        InstanceIdSearch.done();
        
        InstanceAndTypeSearch= createSearchBuilder();
        InstanceAndTypeSearch.and("instanceId", InstanceAndTypeSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        InstanceAndTypeSearch.and("vType", InstanceAndTypeSearch.entity().getVolumeType(), SearchCriteria.Op.EQ);
        InstanceAndTypeSearch.done();
        
        InstanceIdDestroyedSearch = createSearchBuilder();
        InstanceIdDestroyedSearch.and("instanceId", InstanceIdDestroyedSearch.entity().getInstanceId(), SearchCriteria.Op.EQ);
        InstanceIdDestroyedSearch.and("destroyed", InstanceIdDestroyedSearch.entity().getDestroyed(), SearchCriteria.Op.EQ);
        InstanceIdDestroyedSearch.done();
        
        HostIdVmIdVolTypeSearch= createSearchBuilder();
        HostIdVmIdVolTypeSearch.and("hostId", HostIdVmIdVolTypeSearch.entity().getHostId(), Op.EQ);
        HostIdVmIdVolTypeSearch.and("instanceId", HostIdVmIdVolTypeSearch.entity().getInstanceId(), Op.EQ);
        HostIdVmIdVolTypeSearch.and("vType", HostIdVmIdVolTypeSearch.entity().getVolumeType(), Op.EQ);
        HostIdVmIdVolTypeSearch.done();
        
        HostIdVmIdSearch= createSearchBuilder();
        HostIdVmIdSearch.and("hostId", HostIdVmIdSearch.entity().getHostId(), Op.EQ);
        HostIdVmIdSearch.and("instanceId", HostIdVmIdSearch.entity().getInstanceId(), Op.EQ);
        HostIdVmIdSearch.done();
        
        MirrorSearch = createSearchBuilder();
        MirrorSearch.and("mirrorVolume", MirrorSearch.entity().getMirrorVolume(), Op.NULL);
        MirrorSearch.and("mirrorState", MirrorSearch.entity().getMirrorState(), Op.EQ);
        MirrorSearch.done();
       
	}

	@Override
	public List<VolumeVO> findByPool(long poolId) {
        SearchCriteria sc = PoolIdSearch.create();
        sc.setParameters("poolId", poolId);
        return listActiveBy(sc);
	}
}
