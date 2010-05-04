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
package com.vmops.storage.secondary;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.host.HostVO;
import com.vmops.host.Status.Event;
import com.vmops.host.dao.HostDao;
import com.vmops.resource.Discoverer;
import com.vmops.resource.ServerResource;
import com.vmops.storage.VMTemplateHostVO;
import com.vmops.storage.VMTemplateStorageResourceAssoc;
import com.vmops.storage.VMTemplateVO;
import com.vmops.storage.dao.VMTemplateDao;
import com.vmops.storage.dao.VMTemplateHostDao;
import com.vmops.storage.resource.DummySecondaryStorageResource;
import com.vmops.storage.resource.NfsSecondaryStorageResource;
import com.vmops.storage.template.TemplateConstants;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.component.Inject;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.net.NfsUtils;
import com.vmops.utils.script.Script;

/**
 * SecondaryStorageDiscoverer is used to discover secondary
 * storage servers and make sure everything it can do is
 * correct.
 */
@Local(value=Discoverer.class)
public class SecondaryStorageDiscoverer implements Discoverer {
    private static final Logger s_logger = Logger.getLogger(SecondaryStorageDiscoverer.class);
    
    String _name;
    long _timeout = 2 * 60 * 1000; // 2 minutes
    String _mountParent;
    boolean _useServiceVM = false;
    Random _random = new Random(System.currentTimeMillis());
    ConfigurationDao _configDao;
    @Inject
    protected HostDao _hostDao = null;
    @Inject
    protected VMTemplateDao _tmpltDao = null;
    @Inject
    protected VMTemplateHostDao _vmTemplateHostDao = null;
    
    protected SecondaryStorageDiscoverer() {
    }
    
    @Override
    public Map<? extends ServerResource, Map<String, String>> find(long dcId, Long podId, String urlString, String username, String password) {
        URI uri;
        try {
            uri = new URI(urlString);
            if (!uri.getScheme().equalsIgnoreCase("nfs") && !uri.getScheme().equalsIgnoreCase("file")
                    && !uri.getScheme().equalsIgnoreCase("iso") && !uri.getScheme().equalsIgnoreCase("dummy")) {
                s_logger.debug("It's not NFS or file or ISO, so not a secondary storage server: " + urlString);
                return null;
            }
        } catch (URISyntaxException e) {
            s_logger.debug("Is not a valid url" + urlString);
            return null;
        }

        if (uri.getScheme().equalsIgnoreCase("nfs") || uri.getScheme().equalsIgnoreCase("iso")) {
            return createNfsSecondaryStorageResource(dcId, podId, urlString);
        } else if (uri.getScheme().equalsIgnoreCase("file")) {
            return createLocalSecondaryStorageResource(dcId, podId, uri);
        } else if (uri.getScheme().equalsIgnoreCase("dummy")) {
            return createDummySecondaryStorageResource(dcId, podId, uri);
        } else {
            return null;
        }
    }
    
    protected Map<? extends ServerResource, Map<String, String>> createNfsSecondaryStorageResource(long dcId, Long podId, String urlString) {
        
    	if (_useServiceVM) {
    		try {
				return createDummySecondaryStorageResource(dcId, podId, new URI(urlString));
			} catch (URISyntaxException e) {
				s_logger.warn("Invalid uri  " + urlString);
			}
    	}
        String mountStr;
        try {
            mountStr = NfsUtils.url2Mount(urlString);
        } catch (URISyntaxException e1) {
            return null;
        }
        
        Script script = new Script(true, "mount", _timeout, s_logger);
        String mntPoint = null;
        File file = null;
        do {
            mntPoint = _mountParent + File.separator + Integer.toHexString(_random.nextInt(Integer.MAX_VALUE));
            file = new File(mntPoint);
        } while (file.exists());
                
        if (!file.mkdirs()) {
            s_logger.warn("Unable to make directory: " + mntPoint);
            return null;
        }
        
        script.add(mountStr, mntPoint);
        String result = script.execute();
        if (result != null && !result.contains("already mounted")) {
            s_logger.warn("Unable to mount " + urlString + " due to " + result);
            file.delete();
            return null;
        }
        
        script = new Script(true, "umount", 0, s_logger);
        script.add(mntPoint);
        script.execute();
        
        file.delete();
        
        Map<NfsSecondaryStorageResource, Map<String, String>> srs = new HashMap<NfsSecondaryStorageResource, Map<String, String>>();
        
        NfsSecondaryStorageResource storage = new NfsSecondaryStorageResource();
        Map<String, String> details = new HashMap<String, String>();
        details.put("mount.path", mountStr);
        details.put("orig.url", urlString);
        details.put("mount.parent", _mountParent);
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.putAll(details);
        params.put("zone", Long.toString(dcId));
        if (podId != null) {
            params.put("pod", podId.toString());
        }
        params.put("guid", urlString);
        params.put("secondary.storage.vm", "false");
        
        try {
            storage.configure("Storage", params);
        } catch (ConfigurationException e) {
            s_logger.warn("Unable to configure the storage ", e);
            return null;
        }
        srs.put(storage, details);
        
        return srs;
    }
    
    protected Map<? extends ServerResource, Map<String, String>> createLocalSecondaryStorageResource(long dcId, Long podId, URI uri) {
        Map<LocalSecondaryStorageResource, Map<String, String>> srs = new HashMap<LocalSecondaryStorageResource, Map<String, String>>();
        
        LocalSecondaryStorageResource storage = new LocalSecondaryStorageResource();
        Map<String, String> details = new HashMap<String, String>();
        
        File file = new File(uri);
        details.put("mount.path", file.getAbsolutePath());
        details.put("orig.url", uri.toString());
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.putAll(details);
        params.put("zone", Long.toString(dcId));
        if (podId != null) {
            params.put("pod", podId.toString());
        }
        params.put("guid", uri.toString());
        
        try {
            storage.configure("Storage", params);
        } catch (ConfigurationException e) {
            s_logger.warn("Unable to configure the storage ", e);
            return null;
        }
        srs.put(storage, details);
        
        return srs;
    }
    
    protected Map<ServerResource, Map<String, String>> createDummySecondaryStorageResource(long dcId, Long podId, URI uri) {
        Map<ServerResource, Map<String, String>> srs = new HashMap<ServerResource, Map<String, String>>();
        
        DummySecondaryStorageResource storage = new DummySecondaryStorageResource(_useServiceVM);
        Map<String, String> details = new HashMap<String, String>();
        
        details.put("mount.path", uri.toString());
        details.put("orig.url", uri.toString());
        
        Map<String, Object> params = new HashMap<String, Object>();
        params.putAll(details);
        params.put("zone", Long.toString(dcId));
        if (podId != null) {
            params.put("pod", podId.toString());
        }
        params.put("guid", uri.toString());
        
        try {
            storage.configure("Storage", params);
        } catch (ConfigurationException e) {
            s_logger.warn("Unable to configure the storage ", e);
            return null;
        }
        srs.put(storage, details);
        
        return srs;
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;
        
        final ComponentLocator locator = ComponentLocator.getCurrentLocator();
        
        _configDao = locator.getDao(ConfigurationDao.class);
        if (_configDao == null) {
            throw new ConfigurationException("Unable to get the configuration dao.");
        }
        
        _mountParent = _configDao.getValue("mount.parent");
        if (_mountParent == null) {
            _mountParent = "/mnt";
        }
        
        String useServiceVM = _configDao.getValue("secondary.storage.vm");
        if ("true".equalsIgnoreCase(useServiceVM)){
        	_useServiceVM = true;
        }
        return true;
    }

    @Override
    public String getName() {
        return _name;
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
	public void postDiscovery(List<HostVO> hosts, long msId) {
		if (_useServiceVM) {
			for (HostVO h: hosts) {
				_hostDao.disconnect(h, Event.AgentDisconnected, msId);
				associateSystemVmTemplate(h.getId());
			}
		}
		
	}
	
    protected void associateSystemVmTemplate(long hostId) {
    	VMTemplateVO tmplt = _tmpltDao.findById(TemplateConstants.DEFAULT_SYSTEM_VM_DB_ID);
    	if (tmplt == null) {
    		throw new VmopsRuntimeException("Cannot find routing template in vm_template table. Check your configuration");
    	} 
    	VMTemplateHostVO tmpltHost = _vmTemplateHostDao.findByHostTemplate(hostId, TemplateConstants.DEFAULT_SYSTEM_VM_DB_ID);
    	if (tmpltHost == null) {
    		VMTemplateHostVO vmTemplateHost = new VMTemplateHostVO(hostId, TemplateConstants.DEFAULT_SYSTEM_VM_DB_ID, new Date(), 100, VMTemplateStorageResourceAssoc.Status.DOWNLOADED, null, null, null, TemplateConstants.DEFAULT_SYSTEM_VM_TEMPLATE_PATH);
    		_vmTemplateHostDao.persist(vmTemplateHost);
    	}      
    }
}
