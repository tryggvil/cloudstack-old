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

package com.vmops.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.vmops.configuration.Config;
import com.vmops.configuration.ConfigurationManager;
import com.vmops.configuration.ConfigurationManagerImpl;
import com.vmops.configuration.ConfigurationVO;
import com.vmops.configuration.ResourceCount.ResourceType;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.dc.DataCenterVO;
import com.vmops.dc.VlanVO;
import com.vmops.dc.dao.DataCenterDao;
import com.vmops.domain.DomainVO;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.storage.SnapshotPolicyVO;
import com.vmops.storage.dao.SnapshotPolicyDao;
import com.vmops.user.dao.UserDao;
import com.vmops.utils.PropertiesUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.DB;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.net.NetUtils;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;

public class ConfigurationServerImpl implements ConfigurationServer {
	public static final Logger s_logger = Logger.getLogger(ConfigurationServerImpl.class.getName());
	
	private final ConfigurationDao _configDao;
	private final ConfigurationManager _configMgr;
	private final UserDao _userDao;
	private final DataCenterDao _zoneDao;
	private final SnapshotPolicyDao _snapPolicyDao;
	
	public ConfigurationServerImpl() {
		ComponentLocator locator = ComponentLocator.getLocator(Name);
		_configDao = locator.getDao(ConfigurationDao.class);
		_configMgr = locator.getManager(ConfigurationManager.class);
		_userDao = locator.getDao(UserDao.class);
		_zoneDao = locator.getDao(DataCenterDao.class);
		_snapPolicyDao = locator.getDao(SnapshotPolicyDao.class);
	}

	public void persistDefaultValues() throws InvalidParameterValueException, InternalErrorException {
		
		// Get init
		String init = _configDao.getValue("init");
		
		if (init.equals("false")) {
			s_logger.debug("ConfigurationServer is saving default values to the database.");
			
			// Save default Configuration Table values
			List<String> categories = Config.getCategories();
			for (String category : categories) {
				// If this is not a premium environment, don't insert premium configuration values
				if (!_configMgr.isPremium() && category.equals("Premium")) {
					continue;
				}
				
				List<Config> configs = Config.getConfigs(category);
				for (Config c : configs) {
					String name = c.getName();
					
					// If the value is already in the table, don't reinsert it
					if (_configDao.getValue(name) != null) {
						continue;
					}
					
					String instance = "DEFAULT";
					String component = c.getComponent();
					String value = c.getDefaultValue();
					String description = c.getDescription();
					ConfigurationVO configVO = new ConfigurationVO(category, instance, component, name, value, description);
					_configDao.persist(configVO);
				}
			}
			
			// If this is a premium environment, set the network type to be "vlan"
			if (_configMgr.isPremium()) {
				_configDao.update("network.type", "vlan");
				s_logger.debug("ConfigurationServer changed the network type to \"vlan\".");
				
				_configDao.update("hypervisor.type", "xenserver");
				s_logger.debug("ConfigurationServer changed the hypervisor type to \"xenserver\".");
			}
			
			// Save default service offerings
			_configMgr.createServiceOffering(null, "Small", 1, 512, 500, "Small Offering, $0.05 per hour", false);
			_configMgr.createServiceOffering(null, "Medium", 1, 1024, 1000, "Medium Offering, $0.10 per hour", false);
			_configMgr.createServiceOffering(null, "Large", 2, 2048, 2000, "Large Offering, $0.15 per hour", false);
			
			// Save default disk offerings
			_configMgr.createDiskOffering(DomainVO.ROOT_DOMAIN, "Small", "Small Disk, 5 GB", 5, false);
			_configMgr.createDiskOffering(DomainVO.ROOT_DOMAIN, "Medium", "Medium Disk, 20 GB", 20, false);
			_configMgr.createDiskOffering(DomainVO.ROOT_DOMAIN, "Large", "Large Disk, 100 GB", 100, false);
			
			// Save the mount parent to the configuration table
			String mountParent = getMountParent();
			if (mountParent != null) {
				_configMgr.updateConfiguration("mount.parent", mountParent);
				s_logger.debug("ConfigurationServer saved \"" + mountParent + "\" as mount.parent.");
			} else {
				s_logger.debug("ConfigurationServer could not detect mount.parent.");
			}
			
			String hostIpAdr = getHost();
			if (hostIpAdr != null) {
				_configMgr.updateConfiguration("host", hostIpAdr);
				s_logger.debug("ConfigurationServer saved \"" + hostIpAdr + "\" as host.");
			}
			
			// Get the gateway and netmask of this machine
			String[] gatewayAndNetmask = getGatewayAndNetmask();

			if (gatewayAndNetmask != null) {
				String gateway = gatewayAndNetmask[0];
				String netmask = gatewayAndNetmask[1];
				long cidrSize = NetUtils.getCidrSize(netmask);
				
				// Create a default zone
				String dns = getDNS();
				if (dns == null) {
					dns = "4.2.2.2";
				}
				DataCenterVO zone = _configMgr.createZone("Default", dns, null, null, null, "1000-2000");
				
				// Create a default pod
				String networkType = _configDao.getValue("network.type");
				if (networkType != null && networkType.equals("vnet")) {
					_configMgr.createPod("Default", zone.getId(), "169.254.1.0/24", "169.254.1.2", "169.254.1.254");
				} else {
					_configMgr.createPod("Default", zone.getId(), gateway + "/" + cidrSize, null, null);
				}
				
				s_logger.debug("ConfigurationServer saved a default pod and zone, with gateway: " + gateway + " and netmask: " + netmask);		
			} else {
				s_logger.debug("ConfigurationServer could not detect the gateway and netmask of the management server.");
			}
			
		     //Add default manual snapshot policy
	        SnapshotPolicyVO snapPolicy = new SnapshotPolicyVO(0L, "", (short)4, 0);
	        _snapPolicyDao.persist(snapPolicy);
		}
			
		// Create system user and admin user
		saveUser();
			
		// Set init to true
		_configDao.update("init", "true");
	}
	
	private String[] getGatewayAndNetmask() {
		String defaultRoute = Script.runSimpleBashScript("route | grep default");
		
		if (defaultRoute == null) {
			return null;
		}
		
		String[] defaultRouteList = defaultRoute.split("\\s+");
		
		if (defaultRouteList.length != 8) {
			return null;
		}
		
		String gateway = defaultRouteList[1];
		String ethDevice = defaultRouteList[7];
		String netmask = null;
		
		if (ethDevice != null) {
			netmask = Script.runSimpleBashScript("ifconfig " + ethDevice + " | grep Mask | awk '{print $4}' | cut -d':' -f2");
		}
			
		if (gateway == null || netmask == null) {
			return null;
		} else if (!NetUtils.isValidIp(gateway) || !NetUtils.isValidNetmask(netmask)) {
			return null;
		} else {
			return new String[] {gateway, netmask};
		}
	}
	
	private String getEthDevice() {
		String defaultRoute = Script.runSimpleBashScript("route | grep default");
		
		if (defaultRoute == null) {
			return null;
		}
		
		String[] defaultRouteList = defaultRoute.split("\\s+");
		
		if (defaultRouteList.length != 8) {
			return null;
		}
		
		return defaultRouteList[7];
	}
	
	private String getMountParent() {
		return getEnvironmentProperty("mount.parent");
	}
	
	private String getEnvironmentProperty(String name) {
		try {
			final File propsFile = PropertiesUtil.findConfigFile("environment.properties");
			
			if (propsFile == null) {
				return null;
			} else {
				final FileInputStream finputstream = new FileInputStream(propsFile);
				final Properties props = new Properties();
				props.load(finputstream);
				finputstream.close();
				return props.getProperty("mount.parent");
			}
		} catch (IOException e) {
			return null;
		}
	}
	
	private String getDNS() {
		String dnsLine = Script.runSimpleBashScript("grep nameserver /etc/resolv.conf");
		if (dnsLine == null) {
			return null;
		} else {
			String[] dnsLineArray = dnsLine.split(" ");
			if (dnsLineArray.length != 2) {
				return null;
			} else {
				return dnsLineArray[1];
			}
		}
	}
	
	@DB
	protected String getHost() {
		NetworkInterface nic = null;
		String pubNic = getEthDevice();
		
		if (pubNic == null) {
			return null;
		}
		
		try {
			nic = NetworkInterface.getByName(pubNic);
		} catch (final SocketException e) {
			return null;
		}
		
		String[] info = NetUtils.getNetworkParams(nic);
		return info[0];
	}
	
	@DB
	protected void saveUser() {
        // insert system account
        String insertSql = "INSERT INTO `vmops`.`account` (id, account_name, type, domain_id) VALUES (1, 'system', '1', '1')";
        Transaction txn = Transaction.currentTxn();
        try {
            PreparedStatement stmt = txn.prepareAutoCloseStatement(insertSql);
            stmt.executeUpdate();
        } catch (SQLException ex) {
        }
        // insert system user
        insertSql = "INSERT INTO `vmops`.`user` (id, username, password, account_id, firstname, lastname, created) VALUES (1, 'system', '', 1, 'system', 'vmops', now())";
	    txn = Transaction.currentTxn();
		try {
		    PreparedStatement stmt = txn.prepareAutoCloseStatement(insertSql);
		    stmt.executeUpdate();
		} catch (SQLException ex) {
		}
		
    	// insert admin user
        long id = 2;
        String username = "admin";
        String firstname = "admin";
        String lastname = "vmops";
        String password = "password";
        String email = "";
        
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            return;
        }
        
        md5.reset();
        BigInteger pwInt = new BigInteger(1, md5.digest(password.getBytes()));
        String pwStr = pwInt.toString(16);
        int padding = 32 - pwStr.length();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < padding; i++) {
            sb.append('0'); // make sure the MD5 password is 32 digits long
        }
        sb.append(pwStr);

        // create an account for the admin user first
        insertSql = "INSERT INTO `vmops`.`account` (id, account_name, type, domain_id) VALUES (" + id + ", '" + username + "', '1', '1')";
        txn = Transaction.currentTxn();
        try {
            PreparedStatement stmt = txn.prepareAutoCloseStatement(insertSql);
            stmt.executeUpdate();
        } catch (SQLException ex) {
        }

        // now insert the user
        insertSql = "INSERT INTO `vmops`.`user` (id, username, password, account_id, firstname, lastname, email, created) " +
                "VALUES (" + id + ",'" + username + "','" + sb.toString() + "', 2, '" + firstname + "','" + lastname + "','" + email + "',now())";

        txn = Transaction.currentTxn();
        try {
            PreparedStatement stmt = txn.prepareAutoCloseStatement(insertSql);
            stmt.executeUpdate();
        } catch (SQLException ex) {
        }
    }


	public static void main(String args[]){
		try {
			String password="password";
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.reset();
	        BigInteger pwInt = new BigInteger(1, md5.digest(password.getBytes()));
	        String pwStr = pwInt.toString(16);
	        int padding = 32 - pwStr.length();
	        StringBuffer sb = new StringBuffer();
	        for (int i = 0; i < padding; i++) {
	            sb.append('0'); // make sure the MD5 password is 32 digits long
	        }
	        sb.append(pwStr);
	        System.out.println("password: "+password+" is encrypted: "+sb);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
