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

package com.vmops.configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.HostPodVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.dc.dao.DataCenterIpAddressDaoImpl;
import com.vmops.dc.dao.HostPodDao;
import com.vmops.dc.dao.VlanDao;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.network.IPAddressVO;
import com.vmops.network.dao.IPAddressDao;
import com.vmops.service.ServiceOfferingVO;
import com.vmops.service.dao.ServiceOfferingDao;
import com.vmops.storage.DiskOfferingVO;
import com.vmops.storage.dao.DiskOfferingDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.net.NetUtils;
import com.vmops.vm.VMInstanceVO;
import com.vmops.vm.dao.UserVmDao;
import com.vmops.vm.dao.VMInstanceDao;
import com.vmops.vm.dao.VMInstanceDaoImpl;

@Local(value={ConfigurationManager.class})
public class ConfigurationManagerImpl implements ConfigurationManager {
    public static final Logger s_logger = Logger.getLogger(ConfigurationManagerImpl.class.getName());

	String _name;
	ConfigurationDao _configDao;
	HostPodDao _podDao;
	DataCenterDao _zoneDao;
	ServiceOfferingDao _serviceOfferingDao;
	DiskOfferingDao _diskOfferingDao;
	VlanDao _vlanDao;
	IPAddressDao _publicIpAddressDao;
	DataCenterIpAddressDaoImpl _privateIpAddressDao;
	VMInstanceDao _vmInstanceDao;
	public boolean _premium;
 
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
    	_name = name;
    	final ComponentLocator locator = ComponentLocator.getCurrentLocator();
    	
    	_configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }
    	
    	_podDao = locator.getDao(HostPodDao.class);
        if (_podDao == null) {
            throw new ConfigurationException("Unable to get the pod dao.");
        }
        
        _zoneDao = locator.getDao(DataCenterDao.class);
        if (_zoneDao == null) {
            throw new ConfigurationException("Unable to get the zone dao.");
        }
        
        _serviceOfferingDao = locator.getDao(ServiceOfferingDao.class);
        if (_serviceOfferingDao == null) {
        	throw new ConfigurationException("Unable to get the service offering dao.");
        }
        
        _diskOfferingDao = locator.getDao(DiskOfferingDao.class);
        if (_diskOfferingDao == null) {
        	throw new ConfigurationException("Unable to get the disk offering dao.");
        }
        
        _vlanDao = locator.getDao(VlanDao.class);
        if (_vlanDao == null) {
        	throw new ConfigurationException("Unable to get the vlan dao.");
        }
        
        _publicIpAddressDao = locator.getDao(IPAddressDao.class);
        if (_publicIpAddressDao == null) {
            throw new ConfigurationException("Unable to get the public IP address dao.");
        }
        
        _privateIpAddressDao = locator.getDao(DataCenterIpAddressDaoImpl.class);
        if (_privateIpAddressDao == null) {
            throw new ConfigurationException("Unable to get the private IP address dao.");
        }
        
        _vmInstanceDao = locator.getDao(VMInstanceDao.class);
        if (_vmInstanceDao == null) {
        	throw new ConfigurationException("Unable to get the VM instance dao.");
        }
        
        Object premium = params.get("premium");
        _premium = (premium != null) && ((String) premium).equals("true");
        
    	return true;
    }
    
    @Override
    public String getName() {
        return _name;
    }
    
    @Override
    public boolean isPremium() {
    	return _premium;
    }
    
    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
    
    public void updateConfiguration(String name, String value) throws InvalidParameterValueException, InternalErrorException {
    	String validationMsg = validateConfigurationValue(name, value);
    	
    	if (validationMsg != null) {
    		throw new InvalidParameterValueException(validationMsg);
    	}
    	
    	if (!_configDao.update(name, value)) {
    		throw new InternalErrorException("Failed to update configuration value. Please contact VMOps Support.");
    	}
    }
    
    private String validateConfigurationValue(String name, String value) {
    	Config c = Config.getConfig(name);
    	
    	if (c == null) {
    		return "Invalid configuration variable.";
    	}
    	
    	Class<?> type = c.getType();
    	if (type.equals(Boolean.class)) {
    		if (!(value.equals("true") || value.equals("false"))) {
    			return "Please enter either \"true\" or \"false\".";
    		}
    	}
    	
    	String range = c.getRange();
    	if (range != null) {
    		if (range.equals("privateip")) {
     			if (!NetUtils.isSiteLocalAddress(value)) {
     				return "Please enter a site local IP address.";
     			}
     		} else if (range.equals("netmask")) {
    			if (!NetUtils.isValidNetmask(value)) {
    				return "Please enter a valid netmask.";
    			}
    		}
    	}
    	
    	return null;
    }
    
    private boolean podHasAllocatedPrivateIPs(long podId) {
    	HostPodVO pod = _podDao.findById(podId);
    	int count = _privateIpAddressDao.countIPs(podId, pod.getDataCenterId(), true);
    	return (count > 0);
    }
    
    @DB
    protected void checkIfPodIsDeletable(long podId) throws InternalErrorException {
    	List<List<String>> tablesToCheck = new ArrayList<List<String>>();
    	
    	HostPodVO pod = _podDao.findById(podId);
    	
    	// Check if there are allocated private IP addresses in the pod
    	if (_privateIpAddressDao.countIPs(podId, pod.getDataCenterId(), true) != 0) {
    		throw new InternalErrorException("There are private IP addresses allocated for this pod");
    	}

    	List<String> volumes = new ArrayList<String>();
    	volumes.add(0, "volumes");
    	volumes.add(1, "pod_id");
    	volumes.add(2, "there are storage volumes for this pod");
    	tablesToCheck.add(volumes);
    	
    	List<String> host = new ArrayList<String>();
    	host.add(0, "host");
    	host.add(1, "pod_id");
    	host.add(2, "there are servers running in this pod");
    	tablesToCheck.add(host);
    	
    	List<String> vmInstance = new ArrayList<String>();
    	vmInstance.add(0, "vm_instance");
    	vmInstance.add(1, "pod_id");
    	vmInstance.add(2, "there are virtual machines running in this pod");
    	tablesToCheck.add(vmInstance);
    	
    	List<String> alert = new ArrayList<String>();
		alert.add(0, "alert");
		alert.add(1, "pod_id");
		alert.add(2, "there are alerts for this pod");
		tablesToCheck.add(alert);
    	
    	for (List<String> table : tablesToCheck) {
    		String tableName = table.get(0);
    		String column = table.get(1);
    		String errorMsg = table.get(2);
    		
    		String dbName;
    		if (tableName.equals("event") || tableName.equals("vmops_usage") || tableName.equals("usage_vm_instance") ||
    			tableName.equals("usage_ip_address") || tableName.equals("usage_network") || tableName.equals("usage_job") ||
    			tableName.equals("account") || tableName.equals("user_statistics")) {
    			dbName = "vmops_usage";
    		} else {
    			dbName = "vmops";
    		}
    		
    		String selectSql = "SELECT * FROM `" + dbName + "`.`" + tableName + "` WHERE " + column + " = ?";
    		
            Transaction txn = Transaction.currentTxn();
    		try {
                PreparedStatement stmt = txn.prepareAutoCloseStatement(selectSql);
                stmt.setLong(1, podId);
                ResultSet rs = stmt.executeQuery();
                if (rs != null && rs.next()) {
                	throw new InternalErrorException("The pod cannot be edited because " + errorMsg);
                }
            } catch (SQLException ex) {
                throw new InternalErrorException("The Management Server failed to detect if pod is editable. Please contact VMOps Support.");
            }
    	}
    }
    
    private void checkPodAttributes(long podId, String podName, long zoneId, String cidr, String startIp, String endIp, boolean checkForDuplicates) throws InvalidParameterValueException {
    	// Check if the zone is valid
		if (!validZone(zoneId)) {
			throw new InvalidParameterValueException("Please specify a valid zone.");
		}

		if (checkForDuplicates) {
			// Check if the pod already exists
			if (validPod(podName, zoneId)) {
				throw new InvalidParameterValueException("A pod with name: " + podName + " already exists in zone " + zoneId + ". Please specify a different pod name. ");
			}
		}
		
		String cidrAddress;
		long cidrSize;
		// Get the individual cidrAddress and cidrSize values, if the CIDR is valid. If it's not valid, return an error.
		if (NetUtils.isValidCIDR(cidr)) {
			cidrAddress = getCidrAddress(cidr);
			cidrSize = getCidrSize(cidr);
		} else {
			throw new InvalidParameterValueException("Please enter a valid CIDR for pod: " + podName);
		}
		
		// Check if the IP range is valid
		if (startIp != null || endIp != null) {
			checkIpRange(startIp, endIp, cidrAddress, cidrSize);
		}
		
		// Check if the CIDR conflicts with the Guest Network or other pods
		HashMap<Long, List<Object>> currentPodCidrSubnets = _podDao.getCurrentPodCidrSubnets(zoneId, podId);
		List<Object> newCidrPair = new ArrayList<Object>();
		newCidrPair.add(0, cidrAddress);
		newCidrPair.add(1, new Long(cidrSize));
		currentPodCidrSubnets.put(new Long(-1), newCidrPair);
		checkPodCidrSubnets(zoneId, currentPodCidrSubnets);
    }
    
    @DB
    public void deletePod(long podId) throws InvalidParameterValueException, InternalErrorException {
    	// Make sure the pod exists
    	if (!validPod(podId)) {
    		throw new InvalidParameterValueException("A pod with ID: " + podId + " does not exist.");
    	}
    	
    	checkIfPodIsDeletable(podId);

    	_podDao.delete(podId);
    	
    	// Delete private IP addresses in the pod
    	_privateIpAddressDao.deleteIpAddressByPod(podId);
    }
    
    @DB
    public HostPodVO editPod(long podId, String newPodName, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException {
    	// Make sure the pod exists
    	if (!validPod(podId)) {
    		throw new InvalidParameterValueException("A pod with ID: " + podId + " does not exist.");
    	}
    	
    	// If the CIDR or private IP range is being updated, check if the pod has allocated private IP addresses
    	if (cidr != null || startIp != null || endIp != null) {
    		if (podHasAllocatedPrivateIPs(podId)) {
    			throw new InternalErrorException("The specified pod has allocated private IP addresses, so its CIDR and IP address range cannot be changed.");
    		}
    	}
    	
    	HostPodVO pod = _podDao.findById(podId);
    	String oldPodName = pod.getName();
    	long zoneId = pod.getDataCenterId();
    	
    	if (newPodName == null) {
    		newPodName = oldPodName;
    	}
    	
    	if (cidr == null) {
    		cidr = pod.getCidrAddress() + "/" + pod.getCidrSize();
    	}
    	
    	boolean checkForDuplicates = !oldPodName.equals(newPodName);
    	checkPodAttributes(podId, newPodName, pod.getDataCenterId(), cidr, startIp, endIp, checkForDuplicates);
    	
    	String cidrAddress = getCidrAddress(cidr);
    	long cidrSize = getCidrSize(cidr);
    	
    	if (startIp != null) {
			checkIpRange(startIp, endIp, cidrAddress, cidrSize);
			
			if (endIp == null) {
				endIp = NetUtils.getIpRangeEndIpFromCidr(cidrAddress, cidrSize);
			}
		} 
		
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			
			String ipRange;
			if (startIp != null) {
				// remove old private ip address
				_zoneDao.deletePrivateIpAddressByPod(pod.getId());
				
				// re-allocate private ip addresses for pod
				_zoneDao.addPrivateIpAddress(zoneId, pod.getId(), startIp, endIp);
				
				ipRange = startIp + "-";
				if (endIp != null) {
					ipRange += endIp;
				}
			} else {
				ipRange = pod.getDescription();
			}
			
	    	pod.setName(newPodName);
	    	pod.setDataCenterId(zoneId);
	    	pod.setCidrAddress(cidrAddress);
	    	pod.setCidrSize(cidrSize);
	    	pod.setDescription(ipRange);
	    	
	    	if (!_podDao.update(podId, pod)) {
	    		throw new InternalErrorException("Failed to edit pod. Please contact VMOps Support.");
	    	}
    	
	    	txn.commit();
		} catch(Exception e) {
			s_logger.error("Unable to edit pod due to " + e.getMessage(), e);
			txn.rollback();
			throw new InternalErrorException("Failed to edit pod. Please contact VMOps Support.");
		}
		
		return pod;
    }
    
    @DB
    public HostPodVO createPod(String podName, long zoneId, String cidr, String startIp, String endIp) throws InvalidParameterValueException, InternalErrorException {
    	checkPodAttributes(-1, podName, zoneId, cidr, startIp, endIp, true);
		
		String cidrAddress = getCidrAddress(cidr);
		long cidrSize = getCidrSize(cidr);
		
		if (startIp != null) {
			if (endIp == null) {
				endIp = NetUtils.getIpRangeEndIpFromCidr(cidrAddress, cidrSize);
			}
		} 
		
		// Create the new pod in the database
		String ipRange;
		if (startIp != null) {
			ipRange = startIp + "-";
			if (endIp != null) {
				ipRange += endIp;
			}
		} else {
			ipRange = "";
		}
		
		HostPodVO pod = new HostPodVO(podName, zoneId, cidrAddress, cidrSize, ipRange);
		Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			
			if (_podDao.persist(pod) == null) {
				txn.rollback();
				throw new InternalErrorException("Failed to create new pod. Please contact VMOps Support.");
			}
			
			if (startIp != null) {
				_zoneDao.addPrivateIpAddress(zoneId, pod.getId(), startIp, endIp);
			}
			
			txn.commit();

		} catch(Exception e) {
			txn.rollback();
			s_logger.error("Unable to create new pod due to " + e.getMessage(), e);
			throw new InternalErrorException("Failed to create new pod. Please contact VMOps Support.");
		}
		
		return pod;
    }
    
    private boolean zoneHasVMs(long zoneId) throws InternalErrorException {
    	List<VMInstanceVO> vmInstances = _vmInstanceDao.listByZoneId(zoneId);
    	return !vmInstances.isEmpty();
    }
    
    private boolean zoneHasAllocatedVnets(long zoneId) throws InternalErrorException {
    	return !_zoneDao.listAllocatedVnets(zoneId).isEmpty();
    }
    
    @DB
    protected void checkIfZoneIsDeletable(long zoneId) throws InternalErrorException {
    	List<List<String>> tablesToCheck = new ArrayList<List<String>>();
    	
    	List<String> alert = new ArrayList<String>();
		alert.add(0, "alert");
		alert.add(1, "data_center_id");
		alert.add(2, "there are alerts for this zone");
		tablesToCheck.add(alert);
    	
    	List<String> host = new ArrayList<String>();
    	host.add(0, "host");
    	host.add(1, "data_center_id");
    	host.add(2, "there are servers running in this zone");
    	tablesToCheck.add(host);
    	
    	List<String> hostPodRef = new ArrayList<String>();
		hostPodRef.add(0, "host_pod_ref");
		hostPodRef.add(1, "data_center_id");
		hostPodRef.add(2, "there are pods in this zone");
		tablesToCheck.add(hostPodRef);
    	
    	List<String> privateIP = new ArrayList<String>();
    	privateIP.add(0, "op_dc_ip_address_alloc");
    	privateIP.add(1, "data_center_id");
    	privateIP.add(2, "there are private IP addresses allocated for this zone");
    	tablesToCheck.add(privateIP);
    	
    	List<String> publicIP = new ArrayList<String>();
    	publicIP.add(0, "user_ip_address");
    	publicIP.add(1, "data_center_id");
    	publicIP.add(2, "there are public IP addresses allocated for this zone");
    	tablesToCheck.add(publicIP);
    	
    	List<String> userStatistics = new ArrayList<String>();
    	userStatistics.add(0, "user_statistics");
    	userStatistics.add(1, "data_center_id");
    	userStatistics.add(2, "there are user statistics for this zone");
    	tablesToCheck.add(userStatistics);
    	
    	List<String> vmInstance = new ArrayList<String>();
    	vmInstance.add(0, "vm_instance");
    	vmInstance.add(1, "data_center_id");
    	vmInstance.add(2, "there are virtual machines running in this zone");
    	tablesToCheck.add(vmInstance);
    	
    	List<String> volumes = new ArrayList<String>();
    	volumes.add(0, "volumes");
    	volumes.add(1, "data_center_id");
    	volumes.add(2, "there are storage volumes for this zone");
    	tablesToCheck.add(volumes);
    	
    	List<String> vnet = new ArrayList<String>();
    	vnet.add(0, "op_dc_vnet_alloc");
    	vnet.add(1, "data_center_id");
    	vnet.add(2, "there are allocated vnets for this zone");
    	tablesToCheck.add(vnet);

    	List<String> usage = new ArrayList<String>();
    	usage.add(0, "vmops_usage");
    	usage.add(1, "zone_id");
    	usage.add(2, "there are usage records for this zone");
    	tablesToCheck.add(usage);
    	
    	List<String> usageVmInstance = new ArrayList<String>();
    	usageVmInstance.add(0, "usage_vm_instance");
    	usageVmInstance.add(1, "zone_id");
    	usageVmInstance.add(2, "there are usage records for this zone");
    	tablesToCheck.add(usageVmInstance);
    	
    	List<String> usageNetwork = new ArrayList<String>();
    	usageNetwork.add(0, "usage_network");
    	usageNetwork.add(1, "zone_id");
    	usageNetwork.add(2, "there are usage records for this zone");
    	tablesToCheck.add(usageNetwork);
    	
    	List<String> usageIpAddress = new ArrayList<String>();
    	usageIpAddress.add(0, "usage_ip_address");
    	usageIpAddress.add(1, "zone_id");
    	usageIpAddress.add(2, "there are usage records for this zone");
    	tablesToCheck.add(usageIpAddress);
    	
    	for (List<String> table : tablesToCheck) {
    		String tableName = table.get(0);
    		String column = table.get(1);
    		String errorMsg = table.get(2);
    		
    		String dbName;
    		if (tableName.equals("event") || tableName.equals("vmops_usage") || tableName.equals("usage_vm_instance") ||
    			tableName.equals("usage_ip_address") || tableName.equals("usage_network") || tableName.equals("usage_job") ||
    			tableName.equals("account") || tableName.equals("user_statistics")) {
    			dbName = "vmops_usage";
    		} else {
    			dbName = "vmops";
    		}
    		
    		String selectSql = "SELECT * FROM `" + dbName + "`.`" + tableName + "` WHERE " + column + " = ?";
    		
    		if (tableName.equals("op_dc_vnet_alloc")) {
    			selectSql += " AND taken IS NOT NULL";
    		}
    		
            Transaction txn = Transaction.currentTxn();
    		try {
                PreparedStatement stmt = txn.prepareAutoCloseStatement(selectSql);
                stmt.setLong(1, zoneId);
                ResultSet rs = stmt.executeQuery();
                if (rs != null && rs.next()) {
                	throw new InternalErrorException("The zone is not deletable because " + errorMsg);
                }
            } catch (SQLException ex) {
            	throw new InternalErrorException("The Management Server failed to detect if zone is deletable. Please contact VMOps Support.");
            }
    	}
    
    }
    
    private void checkZoneParameters(String zoneName, String dns1, String dns2, String dns3, String dns4, boolean checkForDuplicates) throws InvalidParameterValueException {
    	if (checkForDuplicates) {
    		// Check if a zone with the specified name already exists
    		if (validZone(zoneName)) {
    			throw new InvalidParameterValueException("A zone with that name already exists. Please specify a unique zone name.");
    		}
    	}
    	
    	// Check IP validity for DNS addresses
    	
		if (dns1 != null  && !NetUtils.isValidIp(dns1)) {
			throw new InvalidParameterValueException("Please enter a valid IP address for DNS1");
		}
		
		if (dns2 != null  && !NetUtils.isValidIp(dns2)) {
			throw new InvalidParameterValueException("Please enter a valid IP address for DNS2");
		}
		
		if (dns3 != null  && !NetUtils.isValidIp(dns3)) {
			throw new InvalidParameterValueException("Please enter a valid IP address for DNS3");
		}
		
		if (dns4 != null  && !NetUtils.isValidIp(dns4)) {
			throw new InvalidParameterValueException("Please enter a valid IP address for DNS4");
		}
    }
    
    private void checkIpRange(String startIp, String endIp, String cidrAddress, long cidrSize) throws InvalidParameterValueException {
    	if (!NetUtils.isValidIp(startIp)) {
    		throw new InvalidParameterValueException("The start address of the IP range is not a valid IP address.");
    	}
    	
    	if (endIp != null && !NetUtils.isValidIp(endIp)) {
    		throw new InvalidParameterValueException("The end address of the IP range is not a valid IP address.");
    	}
    	
    	if (!NetUtils.getCidrSubNet(startIp, cidrSize).equalsIgnoreCase(NetUtils.getCidrSubNet(cidrAddress, cidrSize))) {
    		throw new InvalidParameterValueException("The start address of the IP range is not in the CIDR subnet.");
    	}
    	
    	if (endIp != null && !NetUtils.getCidrSubNet(endIp, cidrSize).equalsIgnoreCase(NetUtils.getCidrSubNet(cidrAddress, cidrSize))) {
    		throw new InvalidParameterValueException("The end address of the IP range is not in the CIDR subnet.");
    	}
    	
    	if (endIp != null && NetUtils.ip2Long(startIp) > NetUtils.ip2Long(endIp)) {
			throw new InvalidParameterValueException("The start IP address must have a lower value than the end IP address.");
		}
    	
    }
    
    @DB
    public void deleteZone(long zoneId) throws InvalidParameterValueException, InternalErrorException {
    	// Make sure the zone exists
    	if (!validZone(zoneId)) {
    		throw new InvalidParameterValueException("A zone with ID: " + zoneId + " does not exist.");
    	}
    	
    	checkIfZoneIsDeletable(zoneId);
    	
    	_zoneDao.delete(zoneId);
    	
    	// Delete vNet
        _zoneDao.deleteVnet(zoneId);
    }
    
    @Override
    public DataCenterVO editZone(long zoneId, String newZoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange) throws InvalidParameterValueException, InternalErrorException {
    	// Make sure the zone exists
    	if (!validZone(zoneId)) {
    		throw new InvalidParameterValueException("A zone with ID: " + zoneId + " does not exist.");
    	}
    	
    	// If DNS values are being changed, make sure there are no VMs in this zone
    	if (dns1 != null || dns2 != null || dns3 != null || dns4 != null) {
    		if (zoneHasVMs(zoneId)) {
    			throw new InternalErrorException("The zone is not editable because there are VMs running in the zone.");
    		}
    	}
    	
    	// If the Vnet range is being changed, make sure there are no allocated VNets
    	if (vnetRange != null) {
    		if (zoneHasAllocatedVnets(zoneId)) {
    			throw new InternalErrorException("The vlan range is not editable because there are allocated vlans.");
    		}
    	}
    	
    	DataCenterVO zone = _zoneDao.findById(zoneId);
    	String oldZoneName = zone.getName();
    	
    	if (newZoneName == null) {
    		newZoneName = oldZoneName;
    	}
    	
    	if (dns1 == null) {
    		dns1 = zone.getDns1();
    	}

    	boolean checkForDuplicates = !newZoneName.equals(oldZoneName);
    	checkZoneParameters(newZoneName, dns1, dns2, dns3, dns4, checkForDuplicates);

    	zone.setName(newZoneName);
    	zone.setDns1(dns1);
    	zone.setDns2(dns2);
    	zone.setDns3(dns3);
    	zone.setDns4(dns4);
    	if (vnetRange != null) {
    		zone.setVnet(vnetRange);
    	}
    	
    	if (!_zoneDao.update(zoneId, zone)) {
    		throw new InternalErrorException("Failed to edit zone. Please contact VMOps Support.");
    	}
    	
    	if (vnetRange != null) {
    		String[] tokens = vnetRange.split("-");
	    	int begin = Integer.parseInt(tokens[0]);
	    	int end = tokens.length == 1 ? (begin + 1) : Integer.parseInt(tokens[1]);
	    	
	    	_zoneDao.deleteVnet(zoneId);
	    	_zoneDao.addVnet(zone.getId(), begin, end);
    	}
    	
    	return zone;
    }
    
    @DB
    public DataCenterVO createZone(String zoneName, String dns1, String dns2, String dns3, String dns4, String vnetRange) throws InvalidParameterValueException, InternalErrorException {
    	
        int vnetStart, vnetEnd;
        if (vnetRange != null) {
            String[] tokens = vnetRange.split("-");
            
            vnetStart = Integer.parseInt(tokens[0]);
            if (tokens.length == 1) {
                vnetEnd = vnetStart + 1;
            } else {
                vnetEnd = Integer.parseInt(tokens[1]);
            }
        } else {
        	String networkType = _configDao.getValue("network.type");
        	if (networkType != null && networkType.equals("vnet")) {
        		vnetStart = 1000;
                vnetEnd = 2000;
        	} else {
        		throw new InvalidParameterValueException("Please specify a vlan range.");
        	}
        }
        
    	checkZoneParameters(zoneName, dns1, dns2, dns3, dns4, true);
		
		// Create the new zone in the database
		DataCenterVO zone = new DataCenterVO(null, zoneName, null, dns1, dns2, dns3, dns4, vnetRange);
		Long zoneId;
		if ((zoneId = _zoneDao.persist(zone)) == null) {
			throw new InternalErrorException("Failed to create new zone. Please contact VMOps Support.");
		}
		zone = _zoneDao.findById(zoneId);
		
		// Add vnet entries for the new zone
    	_zoneDao.addVnet(zone.getId(), vnetStart, vnetEnd);
		
		return zone;
    }
    
    public Long createServiceOffering(Long id, String name, int cpu, int ramSize, int speed, String displayText, boolean localStorageRequired) {
    	Map<String, String> configs = _configDao.mapByComponent("management-server");
    	String networkRateStr = configs.get("network.throttling.rate");
    	String multicastRateStr = configs.get("multicast.throttling.rate");
    	int networkRate = ((networkRateStr == null) ? 200 : Integer.parseInt(networkRateStr));
    	int multicastRate = ((multicastRateStr == null) ? 10 : Integer.parseInt(multicastRateStr));
    	ServiceOfferingVO offering = new ServiceOfferingVO(id, name, cpu, ramSize, speed, networkRate, multicastRate, false, displayText, localStorageRequired);
    	return _serviceOfferingDao.persist(offering);
    }
    
    public DiskOfferingVO createDiskOffering(long domainId, String name, String description, int numGibibytes, boolean mirrored) {
    	long diskSize = numGibibytes * 1024;
    	
		DiskOfferingVO newDiskOffering = new DiskOfferingVO(domainId, name, description, diskSize, mirrored);
		Long newId = _diskOfferingDao.persist(newDiskOffering);
		
		if (newId == null) {
			return null;
		}
		
		return _diskOfferingDao.findById(newId);
    }
    
    public String changePublicIPRange(boolean add, long vlanDbId, String startIP, String endIP) throws InvalidParameterValueException {
    	VlanVO vlan = _vlanDao.findById(vlanDbId);
    	
    	if (vlan == null) {
    		throw new InvalidParameterValueException("Please specify a valid VLAN id.");
    	}
    	
    	long zoneId = vlan.getDataCenterId();
    	
    	String error = checkPublicIpRangeErrors(add, vlanDbId, startIP, endIP);
		if (error != null) {
			throw new InvalidParameterValueException(error);
		}

		String changedPublicIPs = changeRange(add, "public", -1, zoneId, vlanDbId, startIP, endIP);
		
		if (changedPublicIPs == null) {
			throw new InvalidParameterValueException("Failed to change public IP range. Please contact VMOps Support.");
		} else {
			return changedPublicIPs;
		}
    }
    
    public String changePrivateIPRange(boolean add, long podId, String startIP, String endIP) throws InvalidParameterValueException {
    	String result = checkPrivateIpRangeErrors(add, podId, startIP, endIP);
		if (!result.equals("success"))
			throw new InvalidParameterValueException("Failed to change private IP range. Please contact VMOps Support.");
		
		long zoneId = _podDao.findById(podId).getDataCenterId();
		String changedPrivateIPs = changeRange(add, "private", podId, zoneId, -1, startIP, endIP);
		
		if (changedPrivateIPs == null) {
			throw new InvalidParameterValueException ("Failed to change private IP range. Please contact VMOps Support.");
		} else {
			return changedPrivateIPs;
		}
    }
    
    public VlanVO addOrDeleteVlan(long zoneId, boolean add, String vlanId, String vlanGateway, String vlanNetmask, String description, String name) throws InvalidParameterValueException{
    	// Check if the zone is valid
    	String zone = getZoneName(zoneId);
    	if (zone == null)
    		throw new InvalidParameterValueException("Please specify a valid zone");
    	
    	if (add) {
    		
    		// Make sure the gateway is valid
    		if (!NetUtils.isValidIp(vlanGateway))
    			throw new InvalidParameterValueException("Please specify a valid gateway");
    		
    		// Make sure the netmask is valid
    		if (!NetUtils.isValidIp(vlanNetmask))
    			throw new InvalidParameterValueException("Please specify a valid netmask");
    		
    		// Check if a vlan with the same vlanId already exists in the specified zone
    		if (getVlanDbId(zone, vlanId) != -1)
    			throw new InvalidParameterValueException("A VLAN with the specified VLAN ID already exists in zone " + zone + ".");
    		
    		// Check if another vlan in the same zone has the same subnet
    		String newVlanSubnet = NetUtils.getSubNet(vlanGateway, vlanNetmask);
    		List<VlanVO> vlans = _vlanDao.findByZone(zoneId);
    		for (VlanVO vlan : vlans) {
    			String currentVlanSubnet = NetUtils.getSubNet(vlan.getVlanGateway(), vlan.getVlanNetmask());
    			if (newVlanSubnet.equals(currentVlanSubnet))
    				throw new InvalidParameterValueException("The VLAN with ID " + vlan.getVlanId() + " in zone " + zone + " has the same subnet. Please specify a different gateway/netmask.");
    		}
    		
    		// Check if the vlan's subnet conflicts with the guest network
    		String guestIpNetwork = getGuestIpNetwork();
    		long guestCidrSize = getGuestCidrSize();
    		long vlanCidrSize = NetUtils.getCidrSize(vlanNetmask);
    		long cidrSizeToUse = -1;
			if (vlanCidrSize < guestCidrSize) cidrSizeToUse = vlanCidrSize;
			else cidrSizeToUse = guestCidrSize;
			String guestSubnet = NetUtils.getCidrSubNet(guestIpNetwork, cidrSizeToUse);
			
			// Check that newVlanSubnet does not equal guestSubnet
			if (newVlanSubnet.equals(guestSubnet)) {
				throw new InvalidParameterValueException("The new VLAN you have specified has the same subnet as the guest network. Please specify a different gateway/netmask.");
			}
    		
    		// Everything was fine, so persist the VLAN
    		VlanVO vlan = new VlanVO(vlanId, vlanGateway, vlanNetmask, zoneId, description, name);
    		_vlanDao.persist(vlan);
    		
    		return vlan;
    		
    	} else {
    		
    		// Check if a VLAN actually exists in the specified zone
    		long vlanDbId = getVlanDbId(zone, vlanId);
    		if (vlanDbId == -1)
    			throw new InvalidParameterValueException("A VLAN with ID " + vlanId + " does not exist in zone " + zone);
    		
    		// Check if there are any public IPs that are in the specified vlan.
    		List<IPAddressVO> ips = _publicIpAddressDao.listByVlanDbId(vlanDbId);
    		if (ips.size() != 0)
    			throw new InvalidParameterValueException( "Please delete all IP addresses that are in VLAN " + vlanId + " before deleting the VLAN.");
    		
    		// Delete the vlan
    		_vlanDao.delete(vlanDbId);
    		
    		return null;
    	}
    }

	private String changeRange(boolean add, String type, long podId, long zoneId, long vlanDbId, String startIP, String endIP) {
		
		// Go through all the IPs and add or delete them
		List<String> problemIPs = null;
		if (add) {
			problemIPs = saveIPRange(type, podId, zoneId, vlanDbId, startIP, endIP);
		} else {
			problemIPs = deleteIPRange(type, podId, zoneId, vlanDbId, startIP, endIP);
		}
		
		if (problemIPs == null) {
			return null;
		} else {
			return genChangeRangeSuccessString(problemIPs, add);
		}
	}
	
	@DB
	protected List<String> saveIPRange(String type, long podId, long zoneId, long vlanDbId, String startIP, String endIP) {
    	long startIPLong = NetUtils.ip2Long(startIP);
    	long endIPLong = startIPLong;
    	if (endIP != null) {
    		endIPLong = NetUtils.ip2Long(endIP);
    	}
    	
    	Transaction txn = Transaction.currentTxn();
    	List<String> problemIPs = null;
    	
    	if (type.equals("public")) {
    		problemIPs = savePublicIPRange(txn, startIPLong, endIPLong, zoneId, vlanDbId);
    	} else if (type.equals("private")) {
    		problemIPs = savePrivateIPRange(txn, startIPLong, endIPLong, podId, zoneId);
    	}
    	
    	return problemIPs;
    }
    
	@DB
	protected List<String> deleteIPRange(String type, long podId, long zoneId, long vlanDbId, String startIP, String endIP) {
    	long startIPLong = NetUtils.ip2Long(startIP);
    	long endIPLong = startIPLong;
    	if (endIP != null) endIPLong = NetUtils.ip2Long(endIP);
 
    	Transaction txn = Transaction.currentTxn();
    	List<String> problemIPs = null;
    	if (type.equals("public")) {
    		problemIPs = deletePublicIPRange(txn, startIPLong, endIPLong, vlanDbId);
    	} else if (type.equals("private")) {
    		problemIPs = deletePrivateIPRange(txn, startIPLong, endIPLong, podId, zoneId);
    	}
    	
    	return problemIPs;
    }
    
    private boolean isPublicIPAllocated(String ip, long vlanDbId, PreparedStatement stmt) {
		try {
        	stmt.clearParameters();
        	stmt.setString(1, ip);
        	stmt.setLong(2, vlanDbId);
        	ResultSet rs = stmt.executeQuery();
        	if (rs.next()) return (rs.getString("allocated") != null);
        	else return false;
        } catch (SQLException ex) {
        	System.out.println(ex.getMessage());
            return true;
        }
	}
	
	private boolean isPrivateIPAllocated(String ip, long podId, long zoneId, PreparedStatement stmt) {
		try {
			stmt.clearParameters();
        	stmt.setString(1, ip);
        	stmt.setLong(2, zoneId);
        	stmt.setLong(3, podId);
        	ResultSet rs = stmt.executeQuery();
        	if (rs.next()) return (rs.getString("taken") != null);
        	else return false;
        } catch (SQLException ex) {
        	System.out.println(ex.getMessage());
            return true;
        }
	}
    
    private List<String> deletePublicIPRange(Transaction txn, long startIP, long endIP, long vlanDbId) {
		String deleteSql = "DELETE FROM `vmops`.`user_ip_address` WHERE public_ip_address = ? AND vlan_db_id = ?";
		String isPublicIPAllocatedSelectSql = "SELECT * FROM `vmops`.`user_ip_address` WHERE public_ip_address = ? AND vlan_db_id = ?";
		
		List<String> problemIPs = new ArrayList<String>();
		PreparedStatement stmt = null;
		PreparedStatement isAllocatedStmt = null;
		
		Connection conn = null;
		try {
			conn = txn.getConnection();
			stmt = conn.prepareStatement(deleteSql);
			isAllocatedStmt = conn.prepareStatement(isPublicIPAllocatedSelectSql);
		} catch (SQLException e) {
			return null;
		}
		
		while (startIP <= endIP) {
			if (!isPublicIPAllocated(NetUtils.long2Ip(startIP), vlanDbId, isAllocatedStmt)) {
				try {
					stmt.clearParameters();
					stmt.setString(1, NetUtils.long2Ip(startIP));
					stmt.setLong(2, vlanDbId);
					stmt.executeUpdate();
				} catch (Exception ex) {
				}
			} else {
				problemIPs.add(NetUtils.long2Ip(startIP));
			}
			startIP += 1;
		}
			
        return problemIPs;
	}
	
	private List<String> deletePrivateIPRange(Transaction txn, long startIP, long endIP, long podId, long zoneId) {
		String deleteSql = "DELETE FROM `vmops`.`op_dc_ip_address_alloc` WHERE ip_address = ? AND pod_id = ? AND data_center_id = ?";
		String isPrivateIPAllocatedSelectSql = "SELECT * FROM `vmops`.`op_dc_ip_address_alloc` WHERE ip_address = ? AND data_center_id = ? AND pod_id = ?";
		
		List<String> problemIPs = new ArrayList<String>();
		PreparedStatement stmt = null;
		PreparedStatement isAllocatedStmt = null;
				
		Connection conn = null;
		try {
			conn = txn.getConnection();
			stmt = conn.prepareStatement(deleteSql);
			isAllocatedStmt = conn.prepareStatement(isPrivateIPAllocatedSelectSql);
		} catch (SQLException e) {
			return null;
		}
		
		while (startIP <= endIP) {
			if (!isPrivateIPAllocated(NetUtils.long2Ip(startIP), podId, zoneId, isAllocatedStmt)) {
				try {
					stmt.clearParameters();
					stmt.setString(1, NetUtils.long2Ip(startIP));
					stmt.setLong(2, podId);
					stmt.setLong(3, zoneId);
					stmt.executeUpdate();
				} catch (Exception ex) {
				}
			} else {
				problemIPs.add(NetUtils.long2Ip(startIP));
			}
        	startIP += 1;
		}

        return problemIPs;
	}
    
    private List<String> savePublicIPRange(Transaction txn, long startIP, long endIP, long zoneId, long vlanDbId) {
		String insertSql = "INSERT INTO `vmops`.`user_ip_address` (public_ip_address, data_center_id, vlan_db_id) VALUES (?, ?, ?)";
		List<String> problemIPs = new ArrayList<String>();
		PreparedStatement stmt = null;
		
		Connection conn = null;
		try {
			conn = txn.getConnection();
		} catch (SQLException e) {
			return null;
		}
        
        while (startIP <= endIP) {
        	try {
        		stmt = conn.prepareStatement(insertSql);
        		stmt.setString(1, NetUtils.long2Ip(startIP));
        		stmt.setLong(2, zoneId);
        		stmt.setLong(3, vlanDbId);
        		stmt.executeUpdate();
        		stmt.close();
        	} catch (Exception ex) {
        		problemIPs.add(NetUtils.long2Ip(startIP));
        	}
        	startIP += 1;
        }
        
        return problemIPs;
	}
	
	private List<String> savePrivateIPRange(Transaction txn, long startIP, long endIP, long podId, long zoneId) {
		String insertSql = "INSERT INTO `vmops`.`op_dc_ip_address_alloc` (ip_address, data_center_id, pod_id) VALUES (?, ?, ?)";
		List<String> problemIPs = new ArrayList<String>();
		PreparedStatement stmt = null;
		
        while (startIP <= endIP) {
        	try {
        		stmt = txn.prepareStatement(insertSql);
        		stmt.setString(1, NetUtils.long2Ip(startIP));
        		stmt.setLong(2, zoneId);
        		stmt.setLong(3, podId);
        		stmt.executeUpdate();
        		stmt.close();
        	} catch (Exception ex) {
        		 problemIPs.add(NetUtils.long2Ip(startIP));
        	}
        	startIP += 1;
        }
        
        return problemIPs;
	}
    
	private String genChangeRangeSuccessString(List<String> problemIPs, boolean add) {
		if (problemIPs == null) return "";
		
		if (problemIPs.size() == 0) {
			if (add) return "Successfully added all IPs in the specified range.";
			else return "Successfully deleted all IPs in the specified range.";
		} else {
			String successString = "";
			if (add) successString += "Failed to add the following IPs, because they are already in the database: ";
			else  successString += "Failed to delete the following IPs, because they are in use: ";
			
			for (int i = 0; i < problemIPs.size(); i++) {
				successString += problemIPs.get(i);
				if (i != (problemIPs.size() - 1)) successString += ", ";
			}
			
			successString += ". ";
			
			if (add) successString += "Successfully added all other IPs in the specified range.";
			else successString += "Successfully deleted all other IPs in the specified range.";
			
			return successString;
		}
	}
	
	private String checkPublicIpRangeErrors(boolean add, long vlanDbId, String startIP, String endIP) {
		// Check that the vlan ID is valid
		if (!validVlan(vlanDbId)) {
			return "Please specify a valid VLAN.";
		}
		
		// Check that the start and end IPs are valid
		if (!NetUtils.isValidIp(startIP)) {
			return "Please specify a valid start IP";
		}
		
		if (endIP != null && !NetUtils.isValidIp(endIP)) {
			return "Please specify a valid end IP";
		}
		
		if (endIP != null && !NetUtils.validIpRange(startIP, endIP)) {
			return "Please specify a valid IP range.";
		}
		
		// Check that the IPs that are being added are compatible with the VLAN's gateway and netmask
		String vlanGateway = getVlanGateway(vlanDbId);
		String vlanNetmask = getVlanNetmask(vlanDbId);

		if (vlanNetmask == null) {
			return "Please ensure that your VLAN's netmask is specified";
		}
		
		if (endIP != null && !NetUtils.sameSubnet(startIP, endIP, vlanNetmask)) {
			return "Please ensure that your start IP and end IP are in the same subnet, as per the VLAN's netmask.";
		}
		
		if (!NetUtils.sameSubnet(startIP, vlanGateway, vlanNetmask)) {
			return "Please ensure that your start IP is in the same subnet as your VLAN's gateway, as per the VLAN's netmask.";
		}
		
		if (endIP != null && !NetUtils.sameSubnet(endIP, vlanGateway, vlanNetmask)) {
			return "Please ensure that your end IP is in the same subnet as your VLAN's gateway, as per the VLAN's netmask.";
		}
		
		return null;
	}
	
	private String checkPrivateIpRangeErrors(boolean add, Long podId, String startIP, String endIP) {
		
		HostPodVO pod = _podDao.findById(podId);
		if (pod == null)
			return "Please specify a valid pod.";
		
		// Check that the start and end IPs are valid
		if (!NetUtils.isValidIp(startIP)) return "Please specify a valid start IP";
		if (endIP != null && !NetUtils.isValidIp(endIP)) return "Please specify a valid end IP";
		if (endIP != null && !NetUtils.validIpRange(startIP, endIP)) return "Please specify a valid IP range.";
		
		// Check that the IPs that are being added are compatible with the pod's CIDR
		String cidrAddress = getCidrAddress(podId);
		long cidrSize = getCidrSize(podId);

		if (endIP != null && !NetUtils.sameSubnetCIDR(startIP, endIP, cidrSize)) return "Please ensure that your start IP and end IP are in the same subnet, as per the pod's CIDR size.";
		if (!NetUtils.sameSubnetCIDR(startIP, cidrAddress, cidrSize)) return "Please ensure that your start IP is in the same subnet as the pod's CIDR address.";
		if (endIP != null && !NetUtils.sameSubnetCIDR(endIP, cidrAddress, cidrSize)) return "Please ensure that your end IP is in the same subnet as the pod's CIDR address.";
	
		// Everything was fine, so return "success"
		return "success";
	}
    
	private String getCidrAddress(String cidr) {
		String[] cidrPair = cidr.split("\\/");
		return cidrPair[0];
	}
	
	private long getCidrSize(String cidr) {
		String[] cidrPair = cidr.split("\\/");
		return Long.parseLong(cidrPair[1]);
	}
	
	private String getCidrAddress(long podId) {
		HostPodVO pod = _podDao.findById(podId);
		return pod.getCidrAddress();
	}
	
	private long getCidrSize(long podId) {
		HostPodVO pod = _podDao.findById(podId);
		return pod.getCidrSize();
	}
	
	private String getStartIp(String ipRange) {
		return (ipRange.split("\\-")[0]);
	}
	
	private String getEndIp(String ipRange) {
		String[] ipRangePair = ipRange.split("\\-");
		if (ipRangePair.length != 2) {
			return null;
		} else {
			return ipRangePair[1];
		}
	}
	
	private void checkPodCidrSubnets(long dcId, HashMap<Long, List<Object>> currentPodCidrSubnets) throws InvalidParameterValueException {
		// For each pod, return an error if any of the following is true:
		// 1. The pod's CIDR subnet conflicts with the guest network subnet
		// 2. The pod's CIDR subnet conflicts with the CIDR subnet of any other pod
		
		String zoneName = getZoneName(dcId);
		String guestIpNetwork = getGuestIpNetwork();
		long guestCidrSize = getGuestCidrSize();
		
		// Iterate through all pods in this zone
		for (Long podId : currentPodCidrSubnets.keySet()) {
			String podName;
			if (podId.longValue() == -1) podName = "newPod";
			else podName = getPodName(podId.longValue());
			
			List<Object> cidrPair = currentPodCidrSubnets.get(podId);
			String cidrAddress = (String) cidrPair.get(0);
			long cidrSize = ((Long) cidrPair.get(1)).longValue();
			
			long cidrSizeToUse = -1;
			if (cidrSize < guestCidrSize) cidrSizeToUse = cidrSize;
			else cidrSizeToUse = guestCidrSize;
			
			String cidrSubnet = NetUtils.getCidrSubNet(cidrAddress, cidrSizeToUse);
			String guestSubnet = NetUtils.getCidrSubNet(guestIpNetwork, cidrSizeToUse);
			
			// Check that cidrSubnet does not equal guestSubnet
			if (cidrSubnet.equals(guestSubnet)) {
				if (podName.equals("newPod")) {
					throw new InvalidParameterValueException("The subnet of the pod you are adding conflicts with the subnet of the Guest IP Network. Please specify a different CIDR.");
				} else {
					throw new InvalidParameterValueException("Warning: The subnet of pod " + podName + " in zone " + zoneName + " conflicts with the subnet of the Guest IP Network. Please change either the pod's CIDR or the Guest IP Network's subnet, and re-run install-vmops-management.");
				}
			}
			
			// Iterate through the rest of the pods
			for (Long otherPodId : currentPodCidrSubnets.keySet()) {
				if (podId.equals(otherPodId)) continue;
				
				// Check that cidrSubnet does not equal otherCidrSubnet
				List<Object> otherCidrPair = currentPodCidrSubnets.get(otherPodId);
				String otherCidrAddress = (String) otherCidrPair.get(0);
				long otherCidrSize = ((Long) otherCidrPair.get(1)).longValue();
				
				if (cidrSize < otherCidrSize) cidrSizeToUse = cidrSize;
				else cidrSizeToUse = otherCidrSize;
				
				cidrSubnet = NetUtils.getCidrSubNet(cidrAddress, cidrSizeToUse);
				String otherCidrSubnet = NetUtils.getCidrSubNet(otherCidrAddress, cidrSizeToUse);
				
				if (cidrSubnet.equals(otherCidrSubnet)) {
					String otherPodName = getPodName(otherPodId.longValue());
					if (podName.equals("newPod")) {
						throw new InvalidParameterValueException("The subnet of the pod you are adding conflicts with the subnet of pod " + otherPodName + " in zone " + zoneName + ". Please specify a different CIDR.");
					} else {
						throw new InvalidParameterValueException("Warning: The pods " + podName + " and " + otherPodName + " in zone " + zoneName + " have conflicting CIDR subnets. Please change the CIDR of one of these pods.");
					}
				}
			}
		}
		
	}
	
	private boolean validPod(long podId) {
		return (_podDao.findById(podId) != null);
	}
    
    private boolean validPod(String podName, long zoneId) {
    	if (!validZone(zoneId)) {
    		return false;
    	}
    	
		return (_podDao.findByName(podName, zoneId) != null);
	}
    
    private String getPodName(long podId) {
    	return _podDao.findById(new Long(podId)).getName();
    }
    
    private boolean validZone(String zoneName) {
    	return(_zoneDao.findByName(zoneName) != null);
    }
    
    private boolean validZone(long zoneId) {
    	return (_zoneDao.findById(zoneId) != null);
    }
    
    private boolean validVlan(long vlanDbId) {
    	return (_vlanDao.findById(new Long(vlanDbId)) != null);
    }
    
    private long getZoneId(String zoneName) {
    	DataCenterVO zone = _zoneDao.findByName(zoneName);
    	
    	if (zone == null)
    		return -1;
    	else
    		return zone.getId().longValue();
    }
    
    private String getZoneName(long zoneId) {
    	DataCenterVO zone = _zoneDao.findById(new Long(zoneId));
    	if (zone != null)
    		return zone.getName();
    	else
    		return null;
    }
    
    private long getVlanDbId(String zone, String vlanId) {
    	long zoneId = getZoneId(zone);
    	VlanVO vlan = _vlanDao.findByZoneAndVlanId(zoneId, vlanId);
    	
    	if (vlan == null)
    		return -1;
    	else
    		return vlan.getId();
    }
    
    private String getVlanGateway(long vlanDbId) {
    	return _vlanDao.findById(new Long(vlanDbId)).getVlanGateway();
    }
    
    private String getVlanNetmask(long vlanDbId) {
    	return _vlanDao.findById(new Long(vlanDbId)).getVlanNetmask();
    }
    
	private long getGuestCidrSize() {
		String guestNetmask = getGuestNetmask();
		return NetUtils.getCidrSize(guestNetmask);
	}
	    
	private String getGuestIpNetwork() {
	    return _configDao.getValue("guest.ip.network");
	}
	    
    private String getGuestNetmask() {
    	return _configDao.getValue("guest.netmask");
    }
	
}
