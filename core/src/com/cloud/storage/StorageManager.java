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
package com.cloud.storage;

import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.exception.InternalErrorException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceInUseException;
import com.cloud.exception.StorageUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.HostVO;
import com.cloud.service.ServiceOffering;
import com.cloud.service.ServiceOfferingVO;
import com.cloud.user.Account;
import com.cloud.utils.Pair;
import com.cloud.utils.component.Manager;
import com.cloud.utils.exception.ExecutionException;
import com.cloud.vm.VMInstanceVO;

public interface StorageManager extends Manager {
	/**
	 * Calls the storage agent and makes the volumes sharable with this host.
	 * 
	 * @param vm vm that owns the volumes
	 * @param vols volumes to share
	 * @param host host to share the volumes to.
	 * @param cancelPrevious cancel the previous shares?
	 * @return true if works.
	 * 
	 * @throws StorageUnavailableException if the storage server is unavailable.
	 */
	boolean share(VMInstanceVO vm, List<VolumeVO> vols, HostVO host, boolean cancelPrevious) throws StorageUnavailableException;

	/**
	 * Calls the storage server to unshare volumes to the host.
	 * 
	 * @param vm vm that owns the volumes.
	 * @param vols volumes to remove from share.
	 * @param host host to unshare the volumes to.
	 * @return true if it worked; false if not.
	 */
	boolean unshare(VMInstanceVO vm, List<VolumeVO> vols, HostVO host);
	
	/**
	 * unshares the storage volumes of a certain vm to the host.
	 * 
	 * @param vm vm to unshare.
	 * @param host host.
	 * @return List<VolumeVO> if succeeded. null if not.
	 */
	List<VolumeVO> unshare(VMInstanceVO vm, HostVO host);
	
	/**
     * destroy the storage volumes of a certain vm.
     * 
     * @param vm vm to destroy.
     * @param vols volumes to remove from storage pool    
     */
	void destroy(VMInstanceVO vm, List<VolumeVO> vols);
	
	/**
	 * Creates the storage needed by the vm
	 */
	long create(Account account, VMInstanceVO vm, VMTemplateVO template, DataCenterVO dc, HostPodVO pod, ServiceOfferingVO offering, DiskOfferingVO diskOffering) throws StorageUnavailableException, ExecutionException;
	
	HostVO findHost(DataCenterVO dc, HostPodVO pod, ServiceOffering offering, DiskOfferingVO dataDiskOffering, VMTemplateVO template, DiskOfferingVO rootDiskOffering, Set<Host> avoid);
	
	/**
	 * Finds a host where the specified pool is visible
	 * @param pool
	 * @return host ID, or null if no host was found
	 */
	Long findHostIdForStoragePool(StoragePoolVO pool);
	
	/**
	 * Create StoragePool based on uri
	 * 
	 * @param zoneId
	 * @param podId
	 * @param poolName
	 * @param uriString
	 * @return
	 * @throws ResourceInUseException, IllegalArgumentException
	 * @throws ResourceAllocationException 
	 */
	StoragePoolVO createPool(long zoneId, long podId, String poolName, URI uri) throws ResourceInUseException, IllegalArgumentException, UnknownHostException, ResourceAllocationException;
	
    /**
     * Get the storage ip address to connect to.
     * @param vm vm to run.
     * @param host host to run it on.
     * @param storage storage that contains the vm.
     * @return ip address if it can be determined.  null if not.
     */
    public String chooseStorageIp(VMInstanceVO vm, Host host, Host storage);
	

	/**
	 * Find a storage pool to allocate volumes for a VM and actually create a volume in that pool
	 * @param dc data center / availability zone
	 * @param pod ignored
	 * @param offering  the service offering (cpu/mem)
	 * @param dataDiskOffering the data disk offering
	 * @param vm the VM for which the volumes are being allocated (null if allocating volumes that won't immediately be attached to any VM)
	 * @param template template
	 * @param rootDiskOffering the root disk offering (for ISO-based VMs)
	 * @param avoid
	 * @return pool where the volumes
	 */
	public StoragePoolVO findStoragePool(DataCenterVO dc, HostPodVO pod,
			ServiceOffering offering, DiskOfferingVO dataDiskOffering,
			VMInstanceVO vm, VMTemplateVO template, DiskOfferingVO rootDiskOffering,
			Set<StoragePool> avoid);


    /** Returns the absolute path of the specified ISO
     * @param templateId - the ID of the template that represents the ISO
     * @param datacenterId
     * @return absolute ISO path
     */
	public String getAbsoluteIsoPath(long templateId, long dataCenterId);
	
	/**
	 * Returns the URL of the secondary storage host
	 * @param zoneId
	 * @return URL
	 */
	public String getSecondaryStorageURL(long zoneId);
	
	/**
	 * Returns a name label for the volume
	 * @param volume - if root volume, disktype will be "ROOT". if data volume, disktype will be "DATA".
	 * @param vm - if present, the name label will be [vmname]-[disktype]. if null, the name label will be "detached".
	 * @return name label
	 */
	public String getVolumeNameLabel(VolumeVO volume, VMInstanceVO vm);
	
	/**
	 * Returns the secondary storage host
	 * @param zoneId
	 * @return secondary storage host
	 */
	public HostVO getSecondaryStorageHost(long zoneId);

	/**
	 * Create the volumes for a user VM based on service offering in a particular data center
	 * 
	 * @return true if successful
	 */
	public long createUserVM(Account account, VMInstanceVO vm,
			VMTemplateVO template, DataCenterVO dc, HostPodVO pod,
			ServiceOffering offering, DiskOfferingVO rootDiskOffering,
			DiskOfferingVO dataDiskOffering, List<StoragePoolVO> avoids);

	/**
	 * Create the volumes for a user VM based on service offering in a storage pool in a particular data center
	 * 
	 * @return true if successful
	 */
	public long createInPool(Account account, VMInstanceVO vm,
			VMTemplateVO template, DataCenterVO dc, HostPodVO pod,
			ServiceOffering offering, DiskOfferingVO rootDiskOffering, DiskOfferingVO dataDiskOffering, List<StoragePoolVO> avoids);

	
	/** Choose a host to operate on a storage pool
	 * @param poolVO  the pool
	 * @param avoidHosts  a list of host ids to avoid
	 * @return
	 */
	public StoragePoolHostVO chooseHostForStoragePool(StoragePoolVO poolVO, List<Long> avoidHosts);

	/**
	 * This method sends the given command on all the hosts in the primary storage pool given until is succeeds on any one.
	 * If the command doesn't succeed on any, it return null. All exceptions are swallowed. Any errors are expected be be in 
	 * answer.getDetails(), if it's not null.
	 * @param poolId        The primary storage pool. The cmd uses this for some reason.
	 * @param cmd           Any arbitrary command which needs access to the volumes on the given storage pool.
	 * @param basicErrMsg   The cmd specific error msg to spew out in case of any exception.
	 * @return The answer for that command, could be success or failure.
	 */
	Answer sendToStorageHostsOnPool(Long poolId, Command cmd, String basicErrMsg);
	Answer sendToStorageHostsOnPool(Long poolId, Command cmd, String basicErrMsg, int retriesPerHost, int pauseBeforeRetry);

	/**
	 * Add a pool to a host
	 * @param hostId
	 * @param pool
	 */
	boolean addPoolToHost(long hostId, StoragePoolVO pool);
	
	/**
	 * Moves a volume from its current storage pool to the specified storage pool.
	 * @param volume
	 * @param pool
	 * @return VolumeVO
	 */
	VolumeVO moveVolume(VolumeVO volume, StoragePoolVO pool) throws InternalErrorException;
	
	/**
	 * Creates a new volume in a pool in the specified zone
	 * @param accountId
	 * @param userId
	 * @param name
	 * @param dc
	 * @param diskOffering
	 * @return VolumeVO
	 */
	VolumeVO createVolumeInPool(long accountId, long userId, String name, DataCenterVO dc, DiskOfferingVO diskOffering);
	
	/**
	 * Deletes the specified volume on its pool
	 * @param volume
	 */
	void deleteVolumeInPool(VolumeVO volume) throws InternalErrorException;
	
	/** Create capacity entries in the op capacity table
	 * @param storagePool
	 */
	public void createCapacityEntry(StoragePoolVO storagePool);

	/** Choose a host to operate on a storage pool
	 * @param poolId
	 * @return
	 */
	public Long chooseHostForStoragePool(Long poolId);
	
	/**
	 * Checks that the volume is stored on a shared storage pool
	 * @param volume
	 * @return true if the volume is on a shared storage pool, false otherwise
	 */
	boolean volumeOnSharedStoragePool(VolumeVO volume);
	
	/**
	 * Checks that one of the following is true:
	 * 1. The volume is not attached to any VM
	 * 2. The volume is attached to a VM that is running on a host with the KVM hypervisor, and the VM is stopped
	 * 3. The volume is attached to a VM that is running on a host with the XenServer hypervisor (the VM can be stopped or running)
	 * @return true if one of the above conditions is true
	 */
	boolean volumeInactive(VolumeVO volume);
	
	List<Pair<VolumeVO, StoragePoolVO>> isStoredOn(VMInstanceVO vm);

    /**
	 * Cleans up storage pools by removing unused templates.
	 * @param recurring - true if this cleanup is part of a recurring garbage collection thread
	 */
	void cleanupStorage(boolean recurring);
	
	/**
	 * Delete the storage pool
	 * @param id -- id associated
	 */
	public boolean deletePool(long id);

}
