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

import java.util.List;

import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.utils.db.GenericDao;
import com.vmops.vm.UserVmVO;

/**
 * @author ahuang
 *
 */
public interface VolumeDao extends GenericDao<VolumeVO, Long> {
    List<VolumeVO> findByAccount(long accountId);
    List<VolumeVO> findByHost(long hostId);
    List<VolumeVO> findByPool(long poolId);
    List<VolumeVO> findByHostAndMirrorState(long hostId, Volume.MirrorState mState);
    List<VolumeVO> findByInstance(long id);
    List<VolumeVO> findByInstanceAndType(long id, Volume.VolumeType vType);
    List<VolumeVO> findByInstanceIdDestroyed(long vmId);
    List<VolumeVO> findByAccountAndDataCenter(long accountId, long dcId);
    List<Long> findVMInstancesByStorageHost(long hostId, Volume.MirrorState mState);
    VolumeVO findMirrorVolumeByTypeAndInstance(long hostId, long vmId, Volume.VolumeType vType);
    List<VolumeVO> findByHostIdAndInstance(long hostId, long vmId);
    List<VolumeVO> findStrandedMirrorVolumes();
    VolumeVO findByPath(long hostId, String path);
    List<Long> findVmsStoredOnHost(long hostId);
    void deleteVolumesByInstance(long instanceId);
    void attachVolume(long volumeId, long vmId, String nameLabel);
    void detachVolume(long volumeId);
    void destroyVolume(long volumeId);
    void recoverVolume(long volumeId);
    void removeVolume(long volumeId);
}
