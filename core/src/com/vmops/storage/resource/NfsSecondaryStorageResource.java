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

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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
import com.vmops.agent.api.storage.DeleteTemplateCommand;
import com.vmops.agent.api.storage.DownloadCommand;
import com.vmops.agent.api.storage.DownloadProgressCommand;
import com.vmops.host.Host;
import com.vmops.host.Host.Type;
import com.vmops.resource.ServerResource;
import com.vmops.resource.ServerResourceBase;
import com.vmops.storage.StorageLayer;
import com.vmops.storage.Volume;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.storage.template.DownloadManager;
import com.vmops.storage.template.DownloadManagerImpl;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;

public class NfsSecondaryStorageResource extends ServerResourceBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(NfsSecondaryStorageResource.class);
    int _timeout;
    
    String _instance;
    String _parent;
    
    String _dc;
    String _pod;
    String _guid;
    String _nfsPath;
    String _mountParent;
    Map<String, Object> _params;
    StorageLayer _storage;
    
    Random _rand = new Random(System.currentTimeMillis());
    
    DownloadManager _dlMgr;
    
    @Override
    public void disconnected() {
        if (_parent != null) {
            Script script = new Script(true, "umount", _timeout, s_logger);
            script.add(_parent);
            script.execute();
            
            File file = new File(_parent);
            file.delete();
        }
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof DownloadProgressCommand) {
            return _dlMgr.handleDownloadCommand((DownloadProgressCommand)cmd);
        } else if (cmd instanceof DownloadCommand) {
            return _dlMgr.handleDownloadCommand((DownloadCommand)cmd);
        } else if (cmd instanceof GetStorageStatsCommand) {
        	return execute((GetStorageStatsCommand)cmd);
        } else if (cmd instanceof CheckHealthCommand) {
            return new CheckHealthAnswer(cmd, true);
        } else if (cmd instanceof DeleteTemplateCommand) {
        	return execute((DeleteTemplateCommand) cmd);
        } else {
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
    }
    
    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
        final long usedSize = getUsedSize();
        final long totalSize = getTotalSize();
        if (usedSize == -1 || totalSize == -1) {
        	return new GetStorageStatsAnswer(cmd, "Unable to get storage stats");
        } else {
        	return new GetStorageStatsAnswer(cmd, totalSize, usedSize) ;
        }
    }
    
    protected Answer execute(final DeleteTemplateCommand cmd) {
    	String relativeTemplatePath = cmd.getTemplatePath();
    	String parent = _parent;
    	
    	if (relativeTemplatePath.startsWith(File.separator)) {
    		relativeTemplatePath = relativeTemplatePath.substring(1);
    	}
    	
    	if (!parent.endsWith(File.separator)) {
            parent += File.separator;
        }
    	
    	String absoluteTemplatePath = parent + relativeTemplatePath;
   
    	Script command = new Script("/bin/bash", _timeout, s_logger);
    	command.add("-c");
    	command.add("rm -f " + absoluteTemplatePath);
    	
    	String details = command.execute();
    	if (details == null) {
    		return new Answer(cmd, true, null);
    	} else {
    		return new Answer(cmd, false, details);
    	}
    }
    
    protected long getUsedSize() {
        Script command = new Script("/bin/bash", _timeout, s_logger);
        command.add("-c");
        command.add("df -Ph " + _parent + " | grep -v Used | awk '{print $3}' ");

    	final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
    	if (command.execute(parser) != null) {
    		return -1;
    	}
    	return convertFilesystemSize(parser.getLine());
    }
    
    protected long getTotalSize() {
        Script command = new Script("/bin/bash", _timeout, s_logger);
        command.add("-c");
        command.add("df -Ph " + _parent + " | grep -v Used | awk '{print $2}' ");

    	final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
    	if (command.execute(parser) != null) {
    		return -1;
    	}
    	return convertFilesystemSize(parser.getLine());
    }
    
    protected long convertFilesystemSize(final String size) {
        if (size == null || size.isEmpty()) {
            return -1;
        }

        long multiplier = 1;
        if (size.endsWith("T")) {
            multiplier = 1024l * 1024l * 1024l * 1024l;
        } else if (size.endsWith("G")) {
            multiplier = 1024l * 1024l * 1024l;
        } else if (size.endsWith("M")) {
            multiplier = 1024l * 1024l;
        } else {
            assert (false) : "Well, I have no idea what this is: " + size;
        }

        return (long)(Double.parseDouble(size.substring(0, size.length() - 1)) * multiplier);
    }
    

    @Override
    public Type getType() {
        return Host.Type.SecondaryStorage;
    }
    
    @Override
    public PingCommand getCurrentStatus(final long id) {
        return new PingStorageCommand(Host.Type.Storage, id, new HashMap<String, Boolean>());
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        super.configure(name, params);
        
        _params = params;
        String value = (String)params.get("scripts.timeout");
        _timeout = NumbersUtil.parseInt(value, 1440) * 1000;
        
        _storage = (StorageLayer)params.get(StorageLayer.InstanceConfigKey);
        if (_storage == null) {
            value = (String)params.get(StorageLayer.ClassConfigKey);
            if (value == null) {
                value = "com.vmops.storage.JavaStorageLayer";
            }
            
            try {
                Class<?> clazz = Class.forName(value);
                _storage = (StorageLayer)ComponentLocator.inject(clazz);
                _storage.configure("StorageLayer", params);
            } catch (ClassNotFoundException e) {
                throw new ConfigurationException("Unable to find class " + value);
            }
        }
        
        _guid = (String)params.get("guid");
        if (_guid == null) {
            throw new ConfigurationException("Unable to find the guid");
        }
        
        _dc = (String)params.get("zone");
        if (_dc == null) {
            throw new ConfigurationException("Unable to find the zone");
        }
        _pod = (String)params.get("pod");
        
        _instance = (String)params.get("instance");

        _mountParent = (String)params.get("mount.parent");
        if (_mountParent == null) {
            _mountParent = File.separator + "mnt";
        }
        
        if (_instance != null) {
            _mountParent = _mountParent + File.separator + _instance;
        }

        _nfsPath = (String)params.get("mount.path");
        if (_nfsPath == null) {
            throw new ConfigurationException("Unable to find mount.path");
        }
        
        _parent = mount(_nfsPath, _mountParent);
        if (_parent == null) {
            throw new ConfigurationException("Unable to create mount point");
        }
        
        s_logger.info("Mount point established at " + _parent);
        
        return true;
    }
    
    protected String mount(String path, String parent) {
        String mountPoint = null;
        for (int i = 0; i < 10; i++) {
            String mntPt = parent + File.separator + Integer.toHexString(_rand.nextInt(Integer.MAX_VALUE));
            File file = new File(mntPt);
            if (!file.exists()) {
                if (_storage.mkdir(mntPt)) {
                    mountPoint = mntPt;
                    break;
                }
            }
            s_logger.debug("Unable to create mount: " + mntPt);
        }
        
        if (mountPoint == null) {
            s_logger.warn("Unable to create a mount point");
            return null;
        }
       
        Script script = null;
        String result = null;
        script = new Script(true, "umount", _timeout, s_logger);
        script.add(path);
        result = script.execute();
        
        if( _parent != null ) {
            script = new Script("rmdir", _timeout, s_logger);
            script.add(_parent);
            result = script.execute();
        }
 
        script = new Script(true, "mount", _timeout, s_logger);
        script.add(path, mountPoint);
        result = script.execute();
        if (result != null) {
            s_logger.warn("Unable to mount " + path + " due to " + result);
            File file = new File(mountPoint);
            if (file.exists())
            	file.delete();
            return null;
        }
        
        // Change permissions for the mountpoint
        script = new Script(true, "chmod", _timeout, s_logger);
        script.add("777", mountPoint);
        result = script.execute();
        if (result != null) {
            s_logger.warn("Unable to set permissions for " + mountPoint + " due to " + result);
            return null;
        }
        
        return mountPoint;
    }
    
    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public StartupCommand[] initialize() {
        disconnected();
        
        _parent = mount(_nfsPath, _mountParent);
        
        if( _parent == null ) {
            s_logger.warn("Unable to mount the nfs server");
            return null;
        }
        
        try {
            _params.put("template.parent", _parent);
            _params.put(StorageLayer.InstanceConfigKey, _storage);
            _dlMgr = new DownloadManagerImpl();
            _dlMgr.configure("DownloadManager", _params);
        } catch (ConfigurationException e) {
            s_logger.warn("Caught problem while configuring folers", e);
            return null;
        }
        
        final StartupStorageCommand cmd = new StartupStorageCommand(_parent, StoragePoolType.NetworkFilesystem, getTotalSize(), new HashMap<String, TemplateInfo>());
        
        cmd.setResourceType(Volume.StorageResourceType.SECONDARY_STORAGE);
        cmd.setIqn(null);
        
        fillNetworkInformation(cmd);
        cmd.setDataCenter(_dc);
        cmd.setPod(_pod);
        cmd.setGuid(_guid);
        cmd.setName(_guid);
        cmd.setVersion(NfsSecondaryStorageResource.class.getPackage().getImplementationVersion());
        /* gather TemplateInfo in second storage */
        final Map<String, TemplateInfo> tInfo = _dlMgr.gatherTemplateInfo();
        cmd.setTemplateInfo(tInfo);
        cmd.getHostDetails().put("mount.parent", _mountParent);
        cmd.getHostDetails().put("mount.path", _nfsPath);
        String tok[] = _nfsPath.split(":");
        cmd.setNfsShare("nfs://" + tok[0] + tok[1]);
        if (cmd.getHostDetails().get("orig.url") == null) {
            if (tok.length != 2) {
                throw new VmopsRuntimeException("Not valid NFS path" + _nfsPath);
            }
            String nfsUrl = "nfs://" + tok[0] + tok[1];
            cmd.getHostDetails().put("orig.url", nfsUrl);
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(tok[0]);
            cmd.setPrivateIpAddress(addr.getHostAddress());
        } catch (UnknownHostException e) {
            cmd.setPrivateIpAddress(tok[0]);
        }
        return new StartupCommand [] {cmd};
    }

    @Override
    protected String getDefaultScriptsDir() {
        return "scripts/storage/secondary";
    }
}
