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

package com.vmops.agent;

import java.util.Map;
import java.util.Properties;

import com.vmops.utils.backoff.BackoffAlgorithm;

public interface IAgentShell {
	public Map<String, Object> getCmdLineProperties();
    public Properties getProperties();
    public String getPersistentProperty(String prefix, String name);
    public void setPersistentProperty(String prefix, String name, String value);
    
    public String getHost();
    public int getPort();
    public int getWorkers();
    public int getProxyPort();
    public String getGuid();
    public String getZone();
    public String getPod();
    
    public BackoffAlgorithm getBackoffAlgorithm();
    public int getPingRetries();
    
    public void upgradeAgent(final String url);
    public String getVersion();
}
