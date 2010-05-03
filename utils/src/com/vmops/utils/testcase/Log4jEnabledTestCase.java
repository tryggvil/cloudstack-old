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

package com.vmops.utils.testcase;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.log4j.xml.DOMConfigurator;

import junit.framework.TestCase;

public class Log4jEnabledTestCase extends TestCase {
	protected void setUp() {
		URL configUrl = System.class.getResource("/conf/log4j-vmops.xml");
		if(configUrl != null) {
			System.out.println("Configure log4j using log4j-vmops.xml");

			try {
				File file = new File(configUrl.toURI());
				
				System.out.println("Log4j configuration from : " + file.getAbsolutePath());
				DOMConfigurator.configureAndWatch(file.getAbsolutePath(), 10000);
			} catch (URISyntaxException e) {
				System.out.println("Unable to convert log4j configuration Url to URI");
			}
		} else {
			System.out.println("Configure log4j with default properties");
		}
	}
}
