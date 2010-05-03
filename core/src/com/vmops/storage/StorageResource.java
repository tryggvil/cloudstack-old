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
package com.vmops.storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.BackupSnapshotAnswer;
import com.vmops.agent.api.BackupSnapshotCommand;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetFileStatsAnswer;
import com.vmops.agent.api.GetFileStatsCommand;
import com.vmops.agent.api.GetStorageStatsAnswer;
import com.vmops.agent.api.GetStorageStatsCommand;
import com.vmops.agent.api.ManageSnapshotAnswer;
import com.vmops.agent.api.ManageSnapshotCommand;
import com.vmops.agent.api.ModifyStoragePoolCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.PingStorageCommand;
import com.vmops.agent.api.ScheduleVolumeSnapshotAnswer;
import com.vmops.agent.api.ScheduleVolumeSnapshotCommand;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.CreatePrivateTemplateAnswer;
import com.vmops.agent.api.storage.CreatePrivateTemplateCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.agent.api.storage.DownloadCommand;
import com.vmops.agent.api.storage.ManageVolumeAnswer;
import com.vmops.agent.api.storage.ManageVolumeCommand;
import com.vmops.agent.api.storage.PrimaryStorageDownloadCommand;
import com.vmops.agent.api.storage.ShareAnswer;
import com.vmops.agent.api.storage.ShareCommand;
import com.vmops.agent.api.storage.UpgradeDiskAnswer;
import com.vmops.agent.api.storage.UpgradeDiskCommand;
import com.vmops.agent.api.storage.VolumeSnapshotAnswer;
import com.vmops.agent.api.storage.VolumeSnapshotCommand;
import com.vmops.host.Host;
import com.vmops.resource.ServerResource;
import com.vmops.resource.ServerResourceBase;
import com.vmops.storage.StoragePool.StoragePoolType;
import com.vmops.storage.template.DownloadManager;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;

/**
 * StorageResource represents the storage server.  It executes commands
 * against the storage server.
 *
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || pool | name of the pool to use | String | tank ||
 *    || parent | parent path to all of the templates and the trashcan | Path | [pool]/vmops ||
 *    || scripts.dir | directory to the scripts | Path | ./scripts ||
 *    || scripts.timeout | timeout value to use  when executing scripts | seconds | 120s ||
 *    || public.templates.root.dir | directory where public templates reside | Path | [pool]/volumes/demo/template/public/os ||
 *    || private.templates.root.dir | directory where private templates reside | Path | [pool]/volumes/template/demo/private ||
 *    || templates.download.dir | directory where templates are downloaded prior to being installed | Path | [pool]/volumes/demo/template/download ||
 *    || install.timeout.pergig | timeout for the template creation script per downloaded gigabyte | seconds | 900s ||
 *    || install.numthreads | number of concurrent install threads | number | 3 ||
 *  }
 */
public abstract class StorageResource extends ServerResourceBase implements ServerResource {
    protected static final Logger s_logger = Logger.getLogger(StorageResource.class);
    protected String _createvmPath;
    protected String _listvmdiskPath;
    protected String _listvmdisksizePath;
    protected String _delvmPath;
    protected String _manageSnapshotPath;
    protected String _manageVolumePath;
    protected String _createPrivateTemplatePath;
    protected String _guid;
    protected String _rootDir;
    protected String _rootFolder = "vmops";
    protected String _parent;
    protected String _trashcanDir;
    protected String _vmFolder;
    protected String _trashcanFolder;
    protected String _datadisksFolder;
    protected String _datadisksDir;
	protected String _sharePath;
	protected String _infoPath;
	protected int _timeout;
	
	protected String _iqnPath;

	protected String _checkchildrenPath;
	protected String _userPrivateTemplateRootDir;
	
	protected String _zfsScriptsDir;

	protected DownloadManager _downloadManager;

	protected Map<Long, VolumeSnapshotRequest> _volumeHourlySnapshotRequests = new HashMap<Long, VolumeSnapshotRequest>();
    protected Map<Long, VolumeSnapshotRequest> _volumeDailySnapshotRequests = new HashMap<Long, VolumeSnapshotRequest>();
	protected String _instance;

    @Override
    public Answer executeRequest(final Command cmd) {
        if (cmd instanceof CreateCommand) {
            return execute((CreateCommand)cmd);
        } else if (cmd instanceof DestroyCommand) {
            return execute((DestroyCommand)cmd);
        } else if (cmd instanceof GetFileStatsCommand) {
            return execute((GetFileStatsCommand)cmd);
        } else if (cmd instanceof PrimaryStorageDownloadCommand) {
        	return execute((PrimaryStorageDownloadCommand)cmd);
        } else if (cmd instanceof DownloadCommand) {
            return execute((DownloadCommand)cmd);
        } else if (cmd instanceof GetStorageStatsCommand) {
            return execute((GetStorageStatsCommand)cmd);
        } else if (cmd instanceof UpgradeDiskCommand) {
            return execute((UpgradeDiskCommand) cmd);
        } else if (cmd instanceof ShareCommand) {
            return execute((ShareCommand)cmd);
        } else if (cmd instanceof ManageSnapshotCommand) {
            return execute((ManageSnapshotCommand)cmd);
        } else if (cmd instanceof BackupSnapshotCommand) {
            return execute((BackupSnapshotCommand)cmd);
        } else if (cmd instanceof CreatePrivateTemplateCommand) {
            return execute((CreatePrivateTemplateCommand)cmd);
        } else if (cmd instanceof VolumeSnapshotCommand) {
            return execute((VolumeSnapshotCommand)cmd);
        } else if (cmd instanceof ScheduleVolumeSnapshotCommand) {
            return execute((ScheduleVolumeSnapshotCommand)cmd);
        } else if (cmd instanceof ModifyStoragePoolCommand ){
        	return execute ((ModifyStoragePoolCommand) cmd);
        } else if (cmd instanceof ManageVolumeCommand) {
        	return execute ((ManageVolumeCommand) cmd);
        } else {
        	s_logger.warn("StorageResource: Unsupported command");
            return Answer.createUnsupportedCommandAnswer(cmd);
        }
    }

	protected Answer execute(ModifyStoragePoolCommand cmd) {
		s_logger.warn("Unsupported: network file system mount attempted");
		return Answer.createUnsupportedCommandAnswer(cmd);
	}
	
	protected Answer execute(ManageVolumeCommand cmd) {
		final boolean add = cmd.getAdd();
		final String folder = cmd.getFolder();
		final String path = cmd.getPath();
		final String size = String.valueOf(cmd.getSize());
		
        final Script command = new Script(_manageVolumePath, _timeout, s_logger);
        command.add("-f", folder);
        command.add("-p", path);
        command.add("-s", size);
        if (!add)
        	command.add("-d", "true");
        
        if (add)
        	s_logger.debug("Creating new volume at path: " + path + " with size: " + size + " GB");
        else
        	s_logger.debug("Destroying volume at path: " + path);

        final String result = command.execute();
        
        if (result != null) {
        	s_logger.debug("ManageVolumeCommand did not succeed.");
        	return new ManageVolumeAnswer(cmd, false, result, null);
        } else {
        	Long createdSize = null;
        	if (add)
        		createdSize = getVolumeSize(path);
        	return new ManageVolumeAnswer(cmd, true, result, createdSize);
        }
	}

	protected ShareAnswer execute(final ShareCommand cmd) {
        return new ShareAnswer(cmd, new HashMap<String, Integer>());
    }

    protected Answer execute(final PrimaryStorageDownloadCommand cmd) {
    	return Answer.createUnsupportedCommandAnswer(cmd);
    }
    
    protected Answer execute(final DownloadCommand cmd) {
    	return _downloadManager.handleDownloadCommand(cmd);
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

        final String userfs = getUserPath(rootdiskFolder);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("locking " + userfs);
        }
        
        synchronized(userfs) {
        	String tmpltPath = cmd.getTemplatePath();

        	if (cmd.getRootDiskSizeGB() != 0) {
        		result = create(rootdiskFolder, cmd.getRootDiskSizeGB());
        	} else if (cmd.getDataDiskSizeGB() == 0) {
        		result = create(tmpltPath, rootdiskFolder, userfs, cmd.getDataDiskTemplatePath(), cmd.getLocalPath());
        	} else {
        		result = create(tmpltPath, rootdiskFolder, userfs, datadiskFolder, datadiskName, cmd.getDataDiskSizeGB(), cmd.getLocalPath());
        	}
        	
        	if (result != null) {
        		s_logger.warn("Unable to create." + result);

        		delete(rootdiskFolder, null);
        		return new CreateAnswer(cmd, result);

        	}
        }

        try {
            final List<VolumeVO> vols = getVolumes(rootdiskFolder, datadiskFolder, datadiskName);
            for (VolumeVO vol: vols) {
            	vol.setStorageResourceType(getStorageResourceType());
            }
            return new CreateAnswer(cmd, vols);
        } catch(final Throwable th) {
            delete(rootdiskFolder, null);
            return new CreateAnswer(cmd, th.getMessage());
        }
    }

	public String getSecondaryStorageMountPoint(String uri) {
		return null;
	}

	protected String getUserPath(final String image) {
        return image.substring(0, image.indexOf(File.separator, _parent.length() + 2)).intern();
    }


    protected Answer execute(final GetFileStatsCommand cmd) {
        final String image = cmd.getPaths();
        final Script command = new Script(_listvmdisksizePath, _timeout, s_logger);
        command.add("-d", image);
        command.add("-a");

        final SizeParser parser = new SizeParser();
        final String result = command.execute(parser);
        if (result != null) {
            return new Answer(cmd, false, result);
        }

        return new GetFileStatsAnswer(cmd, parser.size);
    }
    
    protected List<VolumeVO> getVolumes(final String rootdiskFolder, final String datadiskFolder, final String datadiskName) {
        final ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>();

        // Get the rootdisk volume
        String path = rootdiskFolder + File.separator + "rootdisk";
        long totalSize = getVolumeSize(path);

        VolumeVO vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), rootdiskFolder, path, totalSize, Volume.VolumeType.ROOT);
        vols.add(vol);

        // Get the datadisk volume
        if (datadiskFolder != null && datadiskName != null) {
        	path = datadiskFolder + File.separator + datadiskName;
            totalSize = getVolumeSize(path);

            vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), datadiskFolder, path, totalSize, Volume.VolumeType.DATADISK);
            vols.add(vol);
        }

        return vols;
    }

    protected List<VolumeVO> getVolumes(final String imagePath) {
        final ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>();

        String path = getVolumeName(imagePath, null);
        long totalSize = getVolumeSize(path);

        VolumeVO vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), null, path, totalSize, Volume.VolumeType.ROOT);

        vols.add(vol);

        path = getVolumeName(imagePath, (long)1);
        if (path != null) {
            totalSize = getVolumeSize(path);


            vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), null, path, totalSize, Volume.VolumeType.DATADISK);
            vols.add(vol);
        }

        return vols;
    }

    protected long getVolumeSize(final String volume) {
        final Script command = new Script(_listvmdisksizePath, _timeout, s_logger);

        command.add("-d", volume);
        command.add("-t");

        final SizeParser parser = new SizeParser();
        final String result =  command.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException(result);
        }
        return parser.size;
    }

    protected String getVolumeName(final String imagePath, final Long diskNum) {

        final Script command = new Script(_listvmdiskPath, _timeout, s_logger);
        command.add("-i", imagePath);
        if (diskNum == null) {
            command.add("-r");
        } else {
            command.add("-d", diskNum.toString());
        }

        final PathParser parser = new PathParser();
        final String result = command.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException("Can't get volume name due to " + result);
        }

        return parser.path;
    }


    protected long convertFilesystemSize(final String size) {
        if (size == null) {
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

    protected abstract void cleanUpEmptyParents(String imagePath);
    protected abstract long getUsedSize() ;
    protected abstract long getTotalSize();
    protected abstract String destroy(final String imagePath) ;
    protected abstract String createTrashDir(final String imagePath, final StringBuilder path) ;
    public abstract boolean existPath(final String path);
    public abstract String createPath(final String createPath) ;
    protected abstract Answer execute(DestroyCommand cmd) ;
    protected abstract UpgradeDiskAnswer execute(final UpgradeDiskCommand cmd);
	protected abstract String  delete(String imagePath, String extra);
    protected abstract Volume.StorageResourceType getStorageResourceType();
    protected abstract void configureFolders(String name,  Map<String, Object> params) throws ConfigurationException ;



    protected GetStorageStatsAnswer execute(final GetStorageStatsCommand cmd) {
        final long size = getUsedSize();
        return size != -1 ? new GetStorageStatsAnswer(cmd, 0, size) : new GetStorageStatsAnswer(cmd, "Unable to get storage stats");
    }



    protected ManageSnapshotAnswer execute(final ManageSnapshotCommand cmd) {
        final Script command = new Script(_manageSnapshotPath, _timeout, s_logger);
        String path = null;
        if (cmd.getCommandSwitch().equalsIgnoreCase(ManageSnapshotCommand.DESTROY_SNAPSHOT)) {
        	path = cmd.getSnapshotPath();
        } else if (cmd.getCommandSwitch().equalsIgnoreCase(ManageSnapshotCommand.CREATE_SNAPSHOT)) {
        	path = cmd.getVolumePath();
        }
        command.add(cmd.getCommandSwitch(), path);
        command.add("-n", cmd.getSnapshotName());

        final String result = command.execute();
        return new ManageSnapshotAnswer(cmd, cmd.getSnapshotId(),cmd.getVolumePath(), (result == null), result);
    }

    protected BackupSnapshotAnswer execute(final BackupSnapshotCommand cmd) {
        // This is implemented only for XenServerResource
        Answer answer = Answer.createUnsupportedCommandAnswer(cmd);
        return new BackupSnapshotAnswer(cmd, false, answer.getDetails(), null);
    }

    protected CreatePrivateTemplateAnswer execute(final CreatePrivateTemplateCommand cmd) {
        final Script command = new Script(_createPrivateTemplatePath, _timeout, s_logger);

        String installDir = _userPrivateTemplateRootDir;
        if (installDir.startsWith("/")) {
            installDir = installDir.substring(1);
        }

        command.add("-p", cmd.getSnapshotPath());
        command.add("-s", cmd.getTemplateName());
        command.add("-d", installDir);
        command.add("-u", cmd.getUserFolder());
        String templateName = cmd.getTemplateName().replaceAll(" ", "_"); //hard to pass spaces to shell scripts
        if (templateName.length() > 32) {
            templateName = templateName.substring(0,31); //truncate
        }
        command.add("-n", templateName);

        final String result = command.execute();
        CreatePrivateTemplateAnswer answer = new CreatePrivateTemplateAnswer(cmd, (result == null), result, null);

        if (result == null) {
            answer.setPath("/" + installDir + "/" + cmd.getUserFolder() + "/" + templateName);
        }

        return answer;
    }

    protected ScheduleVolumeSnapshotAnswer execute(final ScheduleVolumeSnapshotCommand cmd) {
    	// Snapshot.TYPE_NONE is used to indicate the the current snapshot schedule should be removed.  
        if (cmd.getSnapshotType() == Snapshot.TYPE_NONE) {
            synchronized(_volumeDailySnapshotRequests) {
                _volumeDailySnapshotRequests.remove(Long.valueOf(cmd.getVolumeId()));
            }
            synchronized(_volumeHourlySnapshotRequests) {
                _volumeHourlySnapshotRequests.remove(Long.valueOf(cmd.getVolumeId()));
            }
        } else {
            VolumeSnapshotRequest newSnapshotRequest = new VolumeSnapshotRequest(cmd.getVolumeId(), cmd.getPath());
            Map<Long, VolumeSnapshotRequest> snapshotRequests = ((cmd.getSnapshotType() == Snapshot.TYPE_DAILY) ? _volumeDailySnapshotRequests : _volumeHourlySnapshotRequests);
            synchronized(snapshotRequests) {
                snapshotRequests.put(Long.valueOf(cmd.getVolumeId()), newSnapshotRequest);
            }
        }
        return new ScheduleVolumeSnapshotAnswer(cmd);
    }

    protected VolumeSnapshotAnswer execute(final VolumeSnapshotCommand cmd) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());

        int month = cal.get(Calendar.MONTH) + 1;
        int dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        int year = cal.get(Calendar.YEAR);
        int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
        int minutes = cal.get(Calendar.MINUTE);

        String localSnapshotName = ("" + year);
        localSnapshotName += "_";
        localSnapshotName += ((month < 10) ? "0" : "") + month;
        localSnapshotName += "_";
        localSnapshotName += ((dayOfMonth < 10) ? "0" : "") + dayOfMonth;
        localSnapshotName += "_";
        localSnapshotName += ((hourOfDay < 10) ? "0" : "") + hourOfDay;
        localSnapshotName += "_";
        localSnapshotName += ((minutes < 10) ? "0" : "") + minutes;

        boolean success = true;

        StringBuilder sb = new StringBuilder();
        sb.append("snapshot results -- ");
        List<Long> volumeIds = new ArrayList<Long>();

        Map<Long, VolumeSnapshotRequest> snapshotRequests = ((cmd.getSnapshotType() == Snapshot.TYPE_DAILY) ? _volumeDailySnapshotRequests : _volumeHourlySnapshotRequests);
        if ((snapshotRequests != null) && !snapshotRequests.isEmpty()) {
            for (VolumeSnapshotRequest snapshotRequest : snapshotRequests.values()) {
                Script command = new Script(_manageSnapshotPath, _timeout, s_logger);
                command.add("-c", snapshotRequest.getSnapshotPath());
                command.add("-n", localSnapshotName);

                final String result = command.execute();
                success = (success && (result == null));
                if (result != null) {
                    sb.append("path: ");
                    sb.append(snapshotRequest.getSnapshotPath());
                    sb.append(", name: ");
                    sb.append(localSnapshotName);
                    sb.append("; ");
                } else {
                    volumeIds.add(Long.valueOf(snapshotRequest.getVolumeId()));
                }
            }
        }

        return new VolumeSnapshotAnswer(cmd, cmd.getSnapshotType(), localSnapshotName, volumeIds, success, ((success == true) ? null : sb.toString()));
    }

    protected String create(final String rootdiskFolder, final int rootDiskSizeGB) {

        final Script command = new Script(_createvmPath, _timeout, s_logger);
        command.add("-i", rootdiskFolder);
        command.add("-S", Integer.toString(rootDiskSizeGB));

        return command.execute();
    }

    protected String create(final String templateFolder, final String rootdiskFolder, final String userPath, final String dataPath, String localPath) {

        final Script command = new Script(_createvmPath, _timeout, s_logger);
        command.add("-t", templateFolder);
        command.add("-i", rootdiskFolder);
        command.add("-u", userPath);
        if (dataPath != null) {
            command.add("-d", dataPath);
        }

        return command.execute();
    }

    protected String create(final String templateFolder, final String rootdiskFolder, final String userPath, final String datadiskFolder, final String datadiskName, final int datadiskSize, String localPath) {
    	
        final Script command = new Script(_createvmPath, _timeout, s_logger);

        // for private templates, the script needs the snapshot name being used to create the VM
        command.add("-t", templateFolder);
        command.add("-i", rootdiskFolder);
        command.add("-u", userPath);
        if (datadiskSize != 0) {
        	command.add("-f", datadiskFolder);
            command.add("-s", Integer.toString(datadiskSize));
            command.add("-n", datadiskName);
        }

        return command.execute();
    }
    
    @Override
    public PingCommand getCurrentStatus(final long id) {
        return new PingStorageCommand(Host.Type.Storage, id, new HashMap<String, Boolean>());
    }
    
    @Override
    public StartupCommand[] initialize() {
        final StartupStorageCommand cmd = new StartupStorageCommand(_parent, StoragePoolType.NetworkFilesystem, getTotalSize(), new HashMap<String, TemplateInfo>());
        cmd.setResourceType(getStorageResourceType());
        cmd.setIqn(getIQN());
        fillNetworkInformation(cmd);
        return new StartupCommand [] {cmd};
    }
    
    protected String getIQN() {
        final Script command = new Script(_iqnPath, 500, s_logger);
        final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        final String result = command.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException("Unable to get iqn: " + result);
        }

        return parser.getLine();
    }
    
    @Override
    protected String findScript(String script) {
        return Script.findScript(_zfsScriptsDir, script);
    }
    
    @Override
    protected abstract String getDefaultScriptsDir();

    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }
        
        _zfsScriptsDir = (String)params.get("zfs.scripts.dir");
        if (_zfsScriptsDir == null) {
            _zfsScriptsDir = getDefaultScriptsDir();
        }

        String value = (String)params.get("scripts.timeout");
        _timeout = NumbersUtil.parseInt(value, 1440) * 1000;

        _createvmPath = findScript("createvm.sh");
        if (_createvmPath == null) {
            throw new ConfigurationException("Unable to find the createvm.sh");
        }
        s_logger.info("createvm.sh found in " + _createvmPath);

        _delvmPath = findScript("delvm.sh");
        if (_delvmPath == null) {
            throw new ConfigurationException("Unable to find the delvm.sh");
        }
        s_logger.info("delvm.sh found in " + _delvmPath);

        _listvmdiskPath = findScript("listvmdisk.sh");
        if (_listvmdiskPath == null) {
            throw new ConfigurationException("Unable to find the listvmdisk.sh");
        }
        s_logger.info("listvmdisk.sh found in " + _listvmdiskPath);

        _listvmdisksizePath = findScript("listvmdisksize.sh");
        if (_listvmdisksizePath == null) {
            throw new ConfigurationException("Unable to find the listvmdisksize.sh");
        }
        s_logger.info("listvmdisksize.sh found in " + _listvmdisksizePath);

        _iqnPath = findScript("get_iqn.sh");
        if (_iqnPath == null) {
            throw new ConfigurationException("Unable to find get_iqn.sh");
        }
        s_logger.info("get_iqn.sh found in " + _iqnPath);

        _manageSnapshotPath = findScript("managesnapshot.sh");
        if (_manageSnapshotPath == null) {
            throw new ConfigurationException("Unable to find the managesnapshot.sh");
        }
        s_logger.info("managesnapshot.sh found in " + _manageSnapshotPath);
        
        _manageVolumePath = findScript("managevolume.sh");
        if (_manageVolumePath == null) {
            throw new ConfigurationException("Unable to find managevolume.sh");
        }
        s_logger.info("managevolume.sh found in " + _manageVolumePath);

        _createPrivateTemplatePath = findScript("create_private_template.sh");
        if (_createPrivateTemplatePath == null) {
            throw new ConfigurationException("Unable to find the create_private_template.sh");
        }
        s_logger.info("create_private_template.sh found in " + _createPrivateTemplatePath);

        _checkchildrenPath = findScript("checkchildren.sh");
        if (_checkchildrenPath == null) {
            throw new ConfigurationException("Unable to find the checkchildren.sh");
        }
        
        value = (String)params.get("developer");
        boolean isDeveloper = Boolean.parseBoolean(value);
        
        _instance = (String)params.get("instance");
       /* 
        String guid = (String)params.get("guid");
        if (!isDeveloper && guid == null) {
        	throw new ConfigurationException("Unable to find the guid");
        }
        _guid = guid;*/
      /*
        params.put("template.parent", _parent);
        _downloadManager = new DownloadManagerImpl();
        _downloadManager.configure("DownloadManager", params);*/

        return true;
    }

	@Override
    public Host.Type getType() {
        return Host.Type.Storage;
    }

	protected boolean hasChildren(final String path) {
	    final Script script = new Script(_checkchildrenPath, _timeout, s_logger);
	    script.add(path);

	    return script.execute() != null;  // not null means there's children.
	}


    public static class SizeParser extends OutputInterpreter {
        long size = 0;
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            final StringBuilder buff = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                buff.append(line);
            }

            size = Long.parseLong(buff.toString());

            return null;
        }
    }

    public static class PathParser extends OutputInterpreter {
        String path;
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            final StringBuilder buff = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                buff.append(line);
            }

            path = buff.toString();
            if (path != null && path.length() == 0) {
                path = null;
            }

            return null;
        }
    }

    protected class VolumeSnapshotRequest {
        private final long _volumeId;
        private final String _snapshotPath;

        public VolumeSnapshotRequest(long volumeId, String snapshotPath) {
            _volumeId = volumeId;
            _snapshotPath = snapshotPath;
        }

        public long getVolumeId() {
            return _volumeId;
        }

        public String getSnapshotPath() {
            return _snapshotPath;
        }
    }
}
