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
package com.vmops.agent.resource.storage;



import java.io.File;
import java.net.URI;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.GetStorageStatsAnswer;
import com.vmops.agent.api.GetStorageStatsCommand;

import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.CreatePrivateTemplateAnswer;
import com.vmops.agent.api.storage.CreatePrivateTemplateCommand;
import com.vmops.agent.api.storage.UpgradeDiskAnswer;
import com.vmops.agent.api.storage.UpgradeDiskCommand;
import com.vmops.agent.api.storage.PrimaryStorageDownloadCommand;
import com.vmops.agent.api.storage.DownloadAnswer;
import com.vmops.storage.FileSystemStorageResource;
import com.vmops.storage.JavaStorageLayer;
import com.vmops.storage.StorageLayer;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Storage.ImageFormat;
import com.vmops.storage.template.DownloadManagerImpl;
import com.vmops.storage.template.QCOW2Processor;
import com.vmops.storage.template.Processor;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.script.Script;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.naming.ConfigurationException;

/**
 * @author chiradeep
 *
 */
public class Qcow2StorageResource extends FileSystemStorageResource {

	protected static final Logger s_logger = Logger.getLogger(Qcow2StorageResource.class);
	protected String _secondaryStroageMountPoint;
    StorageLayer _storage;
    Processor _processor;
    
	@Override
	protected UpgradeDiskAnswer execute(UpgradeDiskCommand cmd) {
	    return new UpgradeDiskAnswer(cmd, false, "Disk Upgrade is not supported with Qcow2 disks");
	
	}
    
    protected Answer execute(final PrimaryStorageDownloadCommand cmd) {
    	String remoteHost, sharePath;
    	String result;
    	String url = cmd.getUrl();
    	int index = url.lastIndexOf("/template/");

    	String remotePath = url.substring(0, index);
    	URI ss = null;
    	try {
    		ss = new URI(remotePath);
    		remoteHost = ss.getHost();
    		sharePath = ss.getPath();
    	} catch (URISyntaxException e) {
    		s_logger.debug("Not a valid uri: " + remotePath);
    		return new DownloadAnswer(null, 100, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, null,
        			null);
    	}
    	if (ss.getScheme().equalsIgnoreCase("nfs")) {
    		if (!isNfsMounted(remoteHost, sharePath, _secondaryStroageMountPoint)) {
    			result = mountNfs(remoteHost, sharePath, _secondaryStroageMountPoint);
    			if (result != null) {
    				s_logger.debug("Failed to mount: " + remoteHost + ":" + sharePath + " due to " + result);
    				return new DownloadAnswer(null, 0, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, null,
    						null);
    			}
    		}
    	} else if (ss.getScheme().equalsIgnoreCase("file")){
    		_secondaryStroageMountPoint = sharePath;
    		if (!_secondaryStroageMountPoint.startsWith("/")) {
    			_secondaryStroageMountPoint = "/" + _secondaryStroageMountPoint;
    		}
    	}
    	String filePath = url.substring(index);
    	String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
    	filePath = filePath.substring(0, filePath.lastIndexOf("/") + 1);

    	String installPath = cmd.getLocalPath() + filePath;
    	String srcPath = _secondaryStroageMountPoint + filePath;
    	/*Manually copy template from secondary storage to primary storage*/
    	if (!existPath(installPath)) {
    		createPath(installPath);
    	}

    	if (!existPath(installPath + fileName)) {
    		final Script copy = new Script("cp", _timeout, s_logger);
    		copy.add(srcPath + fileName);
    		copy.add(installPath);
    		result = copy.execute();
    		if (result != null) {
    			s_logger.debug("Failed to copy " + srcPath + fileName + " to " + installPath + " due to " + result);
    			return new DownloadAnswer(null, 0, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, null,
            			null);
    		}
    	}

    	if (!existPath(installPath + "template.properties")) {
    		final Script copyMeta = new Script("cp", _timeout, s_logger);
    		copyMeta.add(srcPath + "template.properties");
    		copyMeta.add(installPath);
    		result = copyMeta.execute();
    		if (result != null) {
    			s_logger.debug("Failed to copy " + srcPath + "template.properties" + " to " + installPath + " due to " + result);
    			return new DownloadAnswer(null, 0, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR, null,
            			null);
    		}
    	}
    	
    	return new DownloadAnswer(null, 100, cmd, com.vmops.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, installPath + fileName,
    			installPath + fileName);
    }

    protected CreatePrivateTemplateAnswer execute(CreatePrivateTemplateCommand cmd) {
        final Script command = new Script(_createPrivateTemplatePath, _timeout, s_logger);

        String installDir = getSecondaryStorageMountPoint(cmd.getSecondaryStorageURL());
        String relativePath = "template" + File.separator +  cmd.getUniqueName();
        if (relativePath.startsWith("/")) {
        	relativePath = relativePath.substring(1);
        }

        String temlatePath = installDir + File.separator + relativePath;
        String templateName = cmd.getTemplateName() + ".qcow2";
        command.add("-p", cmd.getSnapshotPath());
        command.add("-n", templateName);
        command.add("-d", temlatePath);
        command.add("-s", cmd.getSnapshotName());
        final String result = command.execute();
        CreatePrivateTemplateAnswer answer = new CreatePrivateTemplateAnswer(cmd, (result == null), result, null);

        if (result == null) {
            answer.setPath(relativePath  + File.separator + templateName);
            _processor.process(temlatePath, null, cmd.getTemplateName());
        }

        return answer;
    }
    
    public String getSecondaryStorageMountPoint(String uri) {
    	URI u = null;
    	try {
    		 u = new URI(uri);
    	} catch (URISyntaxException e) {
    		
    	}
    	if (u.getScheme().equalsIgnoreCase("nfs")) {
    		if (!isNfsMounted(u.getHost(), u.getPath(), _secondaryStroageMountPoint)) {
    			String result = mountNfs(u.getHost(), u.getPath(), _secondaryStroageMountPoint);
    			if (result != null) {
    				s_logger.debug("Failed to mount: " + uri + " due to " + result);
    				return null;
    			}
    		}
    	} else if (u.getScheme().equalsIgnoreCase("file")) {
    		String path = u.getPath();
    		if (!path.startsWith("/")) {
    			path = "/" + path;
    		}
    		return path;
    	}
    	return _secondaryStroageMountPoint;
    }
    
    protected void configureFolders(String name, Map<String, Object> params) throws ConfigurationException {
    }
    
    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
    	final long size = getUsedSize(cmd.getLocalPath());
    	return size != -1 ? new GetStorageStatsAnswer(cmd, 0, size) : new GetStorageStatsAnswer(cmd, "Unable to get storage stats");
    }
    
    @Override
    protected String getDefaultScriptsDir() {
        return "scripts/storage/qcow2";
    }
	
    protected String create(String templateFolder, String rootdiskFolder, String dataPath, String localPath) {
		
		s_logger.debug("Creating volumes");
		final Script command = new Script(_createvmPath, _timeout, s_logger);
		String relativePath = templateFolder.replaceFirst(localPath + "/", "");
		String relRootPath = rootdiskFolder.replaceFirst(localPath,	"");
		if (relRootPath.startsWith("/")) {
			relRootPath = relRootPath.replaceFirst("^/*", "");
		}
		String paths[] = relRootPath.split(File.separator);
		String prefix = "./";
		for (String path : paths) {
			prefix = "../" + prefix;
		}
		relativePath = prefix + relativePath;
		command.add("-t", relativePath);
		command.add("-i", rootdiskFolder);
 

        return command.execute();
	}


	protected String create( String templatePath, final String rootdiskFolder, final String datadiskFolder, final String datadiskName, final int datadiskSize, String localPath) {
 
    	s_logger.debug("Creating volumes by cloning " + templatePath);
        final Script command = new Script(_createvmPath, _timeout, s_logger);
		String relativePath =  templatePath.replaceFirst(localPath + "/", "");
		String relRootPath = rootdiskFolder.replaceFirst(localPath,	"");
		if (relRootPath.startsWith("/")) {
			relRootPath = relRootPath.replaceFirst("^/*", "");
		}
		String paths[] = relRootPath.split(File.separator);
		String prefix = "./";
		for (String path : paths) {
			prefix = "../" + prefix;
		}
		relativePath = prefix + relativePath;
        command.add("-t", relativePath);
        command.add("-i", rootdiskFolder);
        if (datadiskSize != 0) {
        	command.add("-f", datadiskFolder);
            command.add("-s", Integer.toString(datadiskSize));
            command.add("-n", datadiskName);
        }

        return command.execute();
    }
    
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }
        
        _secondaryStroageMountPoint = (String)params.get("secondary.storage.path");
    	if (_secondaryStroageMountPoint == null) {
    		_secondaryStroageMountPoint = "/mnt/secondary-storage";
    	}
    	File f = new File(_secondaryStroageMountPoint);
    	if (!f.exists()) {
    		f.mkdir();
    	}
        
        //configureFolders(name, params);
        _localStoragePath = (String)params.get("storage.local.path");
        if (_localStoragePath == null) {
        	_localStoragePath = File.separator + "mnt/vmops_local_storage";
        }
        
        _storage = (StorageLayer)params.get(StorageLayer.InstanceConfigKey);
        if (_storage == null) {
            String value = (String)params.get(StorageLayer.ClassConfigKey);
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
        
        Map<String, Object> _params = new HashMap<String, Object>();
        _params.put(StorageLayer.InstanceConfigKey, _storage);
        _processor = new QCOW2Processor();
        _processor.configure("QCOW2 Processor", _params);
        
        return true;
    }
    
    protected CreateAnswer execute(final CreateCommand cmd) {

        String rootdiskFolder = cmd.getRootdiskFolder();
		String datadiskFolder = cmd.getDatadiskFolder();
		String datadiskName = cmd.getDatadiskName();

        int index = rootdiskFolder.lastIndexOf(":/");
        if (index != -1) {
            rootdiskFolder = rootdiskFolder.substring(index + 2);
        }

        index = rootdiskFolder.lastIndexOf('/');
        if (index == -1) {
            return new CreateAnswer(cmd, "Incorrect path passed: " + rootdiskFolder);
        }

        String result = null;
        

        String tmpltPath = cmd.getTemplatePath();

        if (cmd.getRootDiskSizeGB() != 0) {
        	result = create(rootdiskFolder, cmd.getRootDiskSizeGB());
        } else if (cmd.getDataDiskSizeGB() == 0) {
        	result = create(tmpltPath, rootdiskFolder, cmd.getDataDiskTemplatePath(), cmd.getLocalPath());
        } else {
        	result = create(tmpltPath, rootdiskFolder, datadiskFolder, datadiskName, cmd.getDataDiskSizeGB(), cmd.getLocalPath());
        }

        if (result != null) {
        	s_logger.warn("Unable to create." + result);

        	delete(rootdiskFolder, null);
        	return new CreateAnswer(cmd, result);

        }


        try {
            final List<VolumeVO> vols = getVolumes(rootdiskFolder, datadiskFolder, datadiskName);
            for (VolumeVO vol: vols) {
            	String postfix = null;
            	if (vol.getVolumeType() == Volume.VolumeType.ROOT) {
            		postfix = "-ROOT";            	
            	} else if (vol.getVolumeType() == Volume.VolumeType.DATADISK) {
            		postfix = "-DATA";
            	}
            	vol.setName(cmd.getVmName() + postfix);
            	vol.setNameLabel(cmd.getVmName() + postfix);
        		
            	vol.setStorageResourceType(getStorageResourceType());
            }
            return new CreateAnswer(cmd, vols);
        } catch(final Throwable th) {
            delete(rootdiskFolder, null);
            return new CreateAnswer(cmd, th.getMessage());
        }
    }
}
