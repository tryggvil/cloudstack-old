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
package com.cloud.configuration;

import com.cloud.dc.DataCenterVO;
import com.cloud.dc.HostPodVO;
import com.cloud.dc.VlanVO;
import com.cloud.exception.InternalErrorException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.storage.DiskOfferingVO;
import com.cloud.utils.component.Manager;

/**
 * ConfigurationManager handles adding pods/zones, changing IP ranges, enabling external firewalls, and editing configuration values
 *
 */
public interface ConfigurationManager extends Manager {
	
	/**
	 * Updates a configuration entry with a new value
	 * @param name
	 * @param value
	 */
	void updateConfiguration(String name, String value) throws InvalidParameterValueException, InternalErrorException;
	
	/**
	 * Creates a new service offering
	 * @param id
	 * @param name
	 * @param cpu
	 * @param ramSize
	 * @param speed
	 * @param displayText
	 * @param localStorageRequired
	 * @param offerHA
	 * @return ID
	 */
	Long createServiceOffering(Long id, String name, int cpu, int ramSize, int speed, String displayText, boolean localStorageRequired, boolean offerHA);
	
	/**
	 * Creates a new disk offering
	 * @param domainId
	 * @param name
	 * @param description
	 * @param numGibibytes
	 * @param mirrored
	 * @return ID
	 */
	DiskOfferingVO createDiskOffering(long domainId, String name, String description, int numGibibytes, boolean mirrored);
	
	/**
	 * Creates a new pod
	 * @param podName
	 * @param zoneId
	 * @param cidr
	 * @param startIp
	 * @param endIp
	 * @return Pod
	 */
	HostPodVO createPod(String podName, long zoneId, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException;
	
	/**
     * Edits a pod in the database. Will not allow you to edit pods that are being used anywhere in the system.
     * @param podId
     * @param newPodName
     * @param cidr
	 * @param startIp
	 * @param endIp
     * @return Pod
     */
	HostPodVO editPod(long podId, String newPodName, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException;
	
	 /**
     * Deletes a pod from the database. Will not allow you to delete pods that are being used anywhere in the system.
     * @param podId
     */
	void deletePod(long podId) throws InvalidParameterValueException, InternalErrorException;
	
	/**
	 * Creates a new zone
	 * @param zoneName
	 * @param dns1
	 * @param dns2
	 * @param dns3
	 * @param dns4
	 * @param vnetRange
	 * @param guestNetworkCidr
	 * @return Zone
	 */
	DataCenterVO createZone(String zoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange, String guestCidr) throws InvalidParameterValueException, InternalErrorException;
	
	/**
     * Edits a zone in the database. Will not allow you to edit DNS values if there are VMs in the specified zone.
     * @param zoneId
     * @param newZoneName
     * @param dns1
     * @param dns2
     * @param dns3
     * @param dns4
     * @param vnetRange
     * @return Zone
     * @return guestCidr
     */
	DataCenterVO editZone(long zoneId, String newZoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange, String guestCidr) throws InvalidParameterValueException, InternalErrorException;
	
	/**
     * Deletes a zone from the database. Will not allow you to delete zones that are being used anywhere in the system.
     * @param zoneId
     */
	void deleteZone(long zoneId) throws InvalidParameterValueException, InternalErrorException;
	
	/**
	 * Adds/deletes a vlan to/from the database. Will not all you to delete VLANs that are connected to any public IP addresses.
	 * @param zoneId
	 * @param add
	 * @param vlanId
	 * @param gateway
	 * @param description
	 * @param name
	 * @throws InvalidParameterValueException if no router for that user exists in the zone specified
	 * @return Vlan
	 */
	VlanVO addOrDeletePublicVlan(long zoneId, boolean add, String vlanId, String vlanGateway, String vlanNetmask, String description, String name) throws InvalidParameterValueException;
	
	/**
	 * Adds/deletes public IPs
	 * @param add - either true or false
	 * @param vlanDbId
	 * @param startIP
	 * @param endIP
	 * @return Message to display to user
	 * @throws InvalidParameterValueException if unable to add public ip range
	 */
	String changePublicIPRange(boolean add, long vlanDbId, String startIP, String endIP) throws InvalidParameterValueException;
	
	/**
	 * Adds/deletes private IPs
	 * @param add - either true or false
	 * @param podId
	 * @param startIP
	 * @param endIP
	 * @return Message to display to user
	 * @throws InvalidParameterValueException if unable to add private ip range
	 */
	String changePrivateIPRange(boolean add, long podId, String startIP, String endIP) throws InvalidParameterValueException;
	
	/**
	 * Returns a flag that describes whether the manager is being used in a Premium context or not.
	 * @return true for Premium, false for not
	 */
	boolean isPremium();

}
