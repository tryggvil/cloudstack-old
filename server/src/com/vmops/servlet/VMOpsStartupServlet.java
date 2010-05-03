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

package com.vmops.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.apache.log4j.Logger;

import com.vmops.api.ApiServer;
import com.vmops.exception.InternalErrorException;
import com.vmops.exception.InvalidParameterValueException;
import com.vmops.server.ConfigurationServer;
import com.vmops.server.ManagementServer;
import com.vmops.utils.SerialVersionUID;
import com.vmops.utils.component.ComponentLocator;

public class VMOpsStartupServlet extends HttpServlet {
	public static final Logger s_logger = Logger.getLogger(VMOpsStartupServlet.class.getName());
	
    static final long serialVersionUID = SerialVersionUID.VMOpsStartupServlet;
   
    protected static ComponentLocator s_locator;
    
	@Override
    public void init() throws ServletException {
		// Save Configuration Values
	    //ComponentLocator loc = ComponentLocator.getLocator(ConfigurationServer.Name);
	    ConfigurationServer c = (ConfigurationServer)ComponentLocator.getComponent(ConfigurationServer.Name);
	    //ConfigurationServer c = new ConfigurationServerImpl();
	    try {
	    	c.persistDefaultValues();
	    } catch (InvalidParameterValueException ipve) {
	    	throw new ServletException (ipve.getMessage());
	    } catch (InternalErrorException iee) {
	    	throw new ServletException (iee.getMessage());
	    }

	    s_locator = ComponentLocator.getLocator(ManagementServer.Name);
	    ManagementServer ms = (ManagementServer)ComponentLocator.getComponent(ManagementServer.Name);
	   
	    ApiServer.initApiServer(ms.getApiConfig());
	}
}
