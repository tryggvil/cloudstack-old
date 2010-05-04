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

package com.vmops.server.auth;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.server.ManagementServer;
import com.vmops.user.UserAccount;
import com.vmops.user.dao.UserAccountDao;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.exception.VmopsRuntimeException;

/**
 * Simple UserAuthenticator that performs a MD5 hash of the password before 
 * comparing it against the local database.
 * 
 * @author Will Chan
 */
@Local(value={UserAuthenticator.class})
public class MD5UserAuthenticator extends DefaultUserAuthenticator {
	public static final Logger s_logger = Logger.getLogger(MD5UserAuthenticator.class);
	
	private UserAccountDao _userAccountDao;
	
	@Override
	public boolean authenticate(String username, String password, Long domainId) {
		if (s_logger.isDebugEnabled()) {
            s_logger.debug("Retrieving user: " + username);
        }
        UserAccount user = _userAccountDao.getUserAccount(username, domainId);
        if (user == null) {
            s_logger.debug("Unable to find user with " + username + " in domain " + domainId);
            return false;
        }

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new VmopsRuntimeException("Error", e);
        }
        md5.reset();
        BigInteger pwInt = new BigInteger(1, md5.digest(password.getBytes()));

        // make sure our MD5 hash value is 32 digits long...
        StringBuffer sb = new StringBuffer();
        String pwStr = pwInt.toString(16);
        int padding = 32 - pwStr.length();
        for (int i = 0; i < padding; i++) {
            sb.append('0');
        }
        sb.append(pwStr);

        if (!user.getPassword().equals(sb.toString())) {
            s_logger.debug("Password does not match");
            return false;
        }
		return true;
	}

	public boolean configure(String name, Map<String, Object> params)
			throws ConfigurationException {
		super.configure(name, params);
		ComponentLocator locator = ComponentLocator.getLocator(ManagementServer.Name);
		_userAccountDao = locator.getDao(UserAccountDao.class);
		return true;
	}
}
