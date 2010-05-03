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

package com.vmops.storage.resource;

import java.util.HashMap;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.CheckHealthAnswer;
import com.vmops.agent.api.CheckHealthCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetStorageStatsAnswer;
import com.vmops.agent.api.GetStorageStatsCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingStorageCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.storage.DownloadAnswer;
import com.vmops.agent.api.storage.DownloadCommand;
import com.vmops.agent.api.storage.DownloadProgressCommand;
import com.vmops.host.Host;
import com.vmops.host.Host.Type;
import com.vmops.resource.ServerResource;
import com.vmops.resource.ServerResourceBase;
import com.vmops.storage.Volume;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.storage.template.TemplateInfo;

public class DummySecondaryStorageResource extends ServerResourceBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(DummySecondaryStorageResource.class);
    
    String _dc;
    String _pod;
    String _guid;
    String _dummyPath;
    
	@Override
	protected String getDefaultScriptsDir() {
		return "dummy";
	}

	@Override
	public Answer executeRequest(Command cmd) {
        if (cmd instanceof DownloadProgressCommand) {
            return new DownloadAnswer(null, 100, cmd,
            		com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED,
            		"dummyFS",
            		"/dummy");
        } else if (cmd instanceof DownloadCommand) {
            return new DownloadAnswer(null, 100, cmd,
            		com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED,
            		"dummyFS",
            		"/dummy");
        } else if (cmd instanceof GetStorageStatsCommand) {
        	return execute((GetStorageStatsCommand)cmd);
        } else if (cmd instanceof CheckHealthCommand) {
            return new CheckHealthAnswer(cmd, true);
        } else {
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
	}

	@Override
	public PingCommand getCurrentStatus(long id) {
        return new PingStorageCommand(Host.Type.Storage, id, new HashMap<String, Boolean>());
	}

	@Override
	public Type getType() {
        return Host.Type.SecondaryStorage;
	}

	@Override
	public StartupCommand[] initialize() {
        final StartupStorageCommand cmd = new StartupStorageCommand("dummy",
        	StoragePoolType.NetworkFilesystem, 1024*1024*1024*100L,
        	new HashMap<String, TemplateInfo>());
        
        cmd.setResourceType(Volume.StorageResourceType.SECONDARY_STORAGE);
        cmd.setIqn(null);
        
        fillNetworkInformation(cmd);
        cmd.setDataCenter(_dc);
        cmd.setPod(_pod);
        cmd.setGuid(_guid);
        cmd.setName(_guid);
        cmd.setVersion(DummySecondaryStorageResource.class.getPackage().getImplementationVersion());
        /* gather TemplateInfo in second storage */
        final Map<String, TemplateInfo> tInfo = new HashMap<String, TemplateInfo>();
        cmd.setTemplateInfo(tInfo);
        cmd.getHostDetails().put("mount.parent", "dummy");
        cmd.getHostDetails().put("mount.path", "dummy");
        cmd.getHostDetails().put("orig.url", "dummy://dummy)");
        
        String tok[] = _dummyPath.split(":");
        cmd.setPrivateIpAddress(tok[0]);
        return new StartupCommand [] {cmd};
	}
	
    protected GetStorageStatsAnswer execute(GetStorageStatsCommand cmd) {
        long size = 1024*1024*1024*100L;
        return new GetStorageStatsAnswer(cmd, 0, size);
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        
        _guid = (String)params.get("guid");
        if (_guid == null) {
            throw new ConfigurationException("Unable to find the guid");
        }
        
        _dc = (String)params.get("zone");
        if (_dc == null) {
            throw new ConfigurationException("Unable to find the zone");
        }
        _pod = (String)params.get("pod");
        
        _dummyPath = (String)params.get("mount.path");
        if (_dummyPath == null) {
            throw new ConfigurationException("Unable to find mount.path");
        }
        return true;
    }
}
