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
package com.vmops.agent.api;

/**
 * When a snapshot of a VDI is taken, it creates two new files,
 * a 'base copy' which contains all the new data since the time of the last snapshot and an 'empty snapshot' file.
 * Any new data is again written to the VDI with the same UUID. 
 * This class issues a command for copying the 'base copy' vhd file to secondary storage.
 * This currently assumes that both primary and secondary storage are mounted on the XenServer.  
 */
public class BackupSnapshotCommand extends Command {
    private String primaryStoragePoolUuid;
    private String snapshotUuid;
    private String volumeName;
    private String secondaryStoragePoolName;
    private String lastBackedUpSnapshotUuid;
    private String prevSnapshotUuid;
    private boolean isFirstSnapshotOfRootVolume;
    
    protected BackupSnapshotCommand() {
        
    }
    
    /**
     * @param primaryStoragePoolUuid   The UUID of the primary storage Pool
     * @param snapshotUuid             The UUID of the snapshot which is going to be backed up 
     * @param volumeName               The name of the volume whose snapshot was taken (something like i-3-SV-ROOT) 
     * @param secondaryStoragePoolName This is what shows up in the UI when you click on Secondary storage. 
     *                                 In the code, it is present as: In the vmops.host_details table, there is a field mount.parent. This is the value of that field
     *                                 If you have better ideas on how to get it, you are welcome.
     * @param lastBackedUpSnapshotUuid This is the UUID of the vhd file which was last backed up on secondary storage.
     *                                 It may not be the UUID of the base copy of the current snapshot, if no data was written since last snapshot.
     * @param prevSnapshotUuid         The UUID of the previous snapshot for this volume. This will be destroyed on the primary storage.
     * @param isFirstSnapshotOfRootVolume true if this is the first snapshot of a root volume. Set the parent of the backup to null.
     */
    public BackupSnapshotCommand(String primaryStoragePoolUuid,
                                 String snapshotUuid, 
                                 String volumeName,
                                 String secondaryStoragePoolName, 
                                 String lastBackedUpSnapshotUuid,
                                 String prevSnapshotUuid,
                                 boolean isFirstSnapshotOfRootVolume) 
    {
        this.primaryStoragePoolUuid = primaryStoragePoolUuid;
        this.snapshotUuid = snapshotUuid;
        this.volumeName = volumeName;
        this.secondaryStoragePoolName = secondaryStoragePoolName;
        this.lastBackedUpSnapshotUuid = lastBackedUpSnapshotUuid;
        this.prevSnapshotUuid = prevSnapshotUuid;
        this.isFirstSnapshotOfRootVolume = isFirstSnapshotOfRootVolume;
    }

    /**
     * @return the primaryStoragePoolUuid
     */
    public String getPrimaryStoragePoolUuid() {
        return primaryStoragePoolUuid;
    }

    /**
     * @return the snapshotUuid
     */
    public String getSnapshotUuid() {
        return snapshotUuid;
    }

    /**
     * @return the secondaryStoragePoolName
     */
    public String getSecondaryStoragePoolURL() {
        return secondaryStoragePoolName;
    }

    /**
     * @return the volumeName
     */
    public String getVolumeName() {
        return volumeName;
    }

    /**
     * @return the lastBackedUpSnapshotUuid
     */
    public String getLastBackedUpSnapshotUuid() {
        return lastBackedUpSnapshotUuid;
    }
    
    /**
     * @return the prevSnapshotUuid
     */
    public String getPrevSnapshotUuid() {
        return prevSnapshotUuid;
    }

    public boolean isFirstSnapshotOfRootVolume() {
        return isFirstSnapshotOfRootVolume;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean executeInSequence() {
        return false;
    }

}