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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.storage.CreateAnswer;
import com.vmops.agent.api.storage.CreateCommand;
import com.vmops.agent.api.storage.DestroyCommand;
import com.vmops.agent.api.storage.UpgradeDiskAnswer;
import com.vmops.agent.api.storage.UpgradeDiskCommand;
import com.vmops.resource.ServerResource;
import com.vmops.storage.StorageResource;
import com.vmops.storage.Volume;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.template.TemplateInfo;
import com.vmops.utils.Pair;
import com.vmops.utils.exception.VmopsRuntimeException;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;
import com.vmops.utils.script.OutputInterpreter.OneLineParser;

/**
 * Manage storage volumes on OpenSolaris Storage Server
 * @author chiradeep
 * 
 * @config
 * {@table
 *    || Param Name | Description | Values | Default ||
 *    || iscsi.tgt.name | name of the iscsitgt | String | iscsitgt ||
 *  }
 */
@Local(value={ServerResource.class})
public class ZfsIscsiStorageResource extends StorageResource {
    private static final Logger s_logger = Logger.getLogger(ZfsIscsiStorageResource.class);

    protected String _upgradevmdiskPath;
    protected String _viewandluremovePath;
	protected String _zfsdestroyPath;
	protected String _zfsmountrecoveryPath;
	protected String _pool;

	protected String _sharePath;
	protected String _infoPath;
	
	public class IscsiTargetParser extends OutputInterpreter {
		private IscsiTarget tgt = null;

		@Override
		public String interpret(BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
	           String [] toks = line.trim().split(",");
	           if (toks.length == 2) {
	        	   tgt = new IscsiTarget(toks[0], toks[1]);
	           }
            }
            
            return null;
		}

		public IscsiTarget getTgt() {
			return tgt;
		}

	}
	
	@Override
	protected String getDefaultScriptsDir() {
	    return "scripts/storage/zfs/iscsi";
	}

	public static class IscsiTarget {
		private final String zfsLocalPath;
		private final String iscsiName;
		public IscsiTarget(String zfsLocalPath, String iscsiName) {
			super();
			this.zfsLocalPath = zfsLocalPath;
			this.iscsiName = iscsiName;
		}
		public String getZfsLocalPath() {
			return zfsLocalPath;
		}
		public String getIscsiName() {
			return iscsiName;
		}

	}
	
	protected static class IscsiConnectionParser extends OutputInterpreter {
        boolean connected = false;
        private static final String conn = "Connections: ";
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            connected = false;
            String line = null;
            while ((line = reader.readLine()) != null) {
                final int index = line.indexOf(conn);
                if (index > -1) {
                    connected = Integer.parseInt(line.substring(index + conn.length())) > 0;
                    break;
                }
            }

            return null;
        }

        public boolean isConnected() {
            return connected;
        }
    }

    protected static class ShowMountParser extends OutputInterpreter {
        List<Pair<String, String>> _paths;
        String _parent;

        public ShowMountParser(final String parent, final List<Pair<String, String>> paths) {
            _parent = parent;
            _paths = paths;
        }

        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains(_parent)) {
                    final String[] tokens = line.split(":/");
                    _paths.add(new Pair<String, String>(tokens[0], tokens[1]));
                }
            }

            return null;
        }
    }
	
	

	public ZfsIscsiStorageResource() {
	}

	@Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }
        
        _checkchildrenPath = findScript("checkchildren.sh");
        if (_checkchildrenPath == null) {
            throw new ConfigurationException("Unable to find the checkchildren.sh");
        }
        s_logger.info("checkchildren.sh found in " + _checkchildrenPath);
        
        _upgradevmdiskPath = findScript("upgradevmdisk.sh");
        if (_upgradevmdiskPath == null) {
            throw new ConfigurationException("Unable to find the upgradevmdisk.sh");
        }
        s_logger.info("upgradevmdisk.sh found in " + _upgradevmdiskPath);
        
        _sharePath = findScript("lu_share.sh");
        if (_sharePath == null) {
            throw new ConfigurationException("Unable to find lu_share.sh");
        }
        s_logger.info("lu_share.sh found in " + _sharePath);

        _infoPath = findScript("lu_info.sh");
        if (_infoPath == null) {
            throw new ConfigurationException("Unable to find lu_info.sh");
        }
        s_logger.info("lu_info.sh found in " + _infoPath);
        
        _viewandluremovePath = findScript("view_and_lu_remove.sh");
        if (_viewandluremovePath == null) {
        	throw new ConfigurationException("Unable to find view_and_lu_remove.sh");
        }
        s_logger.info("view_and_lu_remove.sh found in " + _viewandluremovePath);
        
        _zfsdestroyPath = findScript("zfs_destroy.sh");
        if (_zfsdestroyPath == null) {
        	throw new ConfigurationException("Unable to find zfs_destroy.sh");
        }
        s_logger.info("zfs_destroy.sh found in " + _zfsdestroyPath);
        
        _zfsmountrecoveryPath = findScript("zfs_mount_recovery.sh");
        if (_zfsmountrecoveryPath == null) {
        	throw new ConfigurationException("Unable to find zfs_mount_recovery.sh");
        }
        s_logger.info("zfs_mount_recovery.sh found in " + _zfsmountrecoveryPath);
        
        String zfsmountrecoveryResult;
        zfsmountrecoveryResult = checkAndRecoverZFS();
        if (zfsmountrecoveryResult != null) {
        	throw new ConfigurationException("Unable to recover ZFS file system hierarchy");
        }
        
        return true;
    }


	protected IscsiTarget getVolume(String imagePath, Volume.VolumeType d, Long diskNum) {
        Script command = new Script(_listvmdiskPath, _timeout, s_logger);
        command.add("-i", imagePath);
        
        switch (d) {
        case ROOT:
            command.add("-r");
        	break;
        case DATADISK:
            command.add("-d", diskNum.toString());
        	break;
        case SWAP:
            command.add("-w");
        	break;
        default:
        	break;
        }
        
        
        IscsiTargetParser parser = new IscsiTargetParser();
        String result = command.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException("Can't get volume name due to " + result);
        }
        
        return parser.getTgt();
	}

	@Override
	protected List<VolumeVO> getVolumes(String imagePath) {
		ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>();

        IscsiTarget tgt = getVolume(imagePath, Volume.VolumeType.ROOT, null);
        long totalSize = getVolumeSize(tgt.getZfsLocalPath());
        
        VolumeVO vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(1), Volume.VolumeType.ROOT, null, tgt.getZfsLocalPath(), tgt.getIscsiName(), totalSize);
        vols.add(vol);
        
        tgt = getVolume(imagePath, Volume.VolumeType.SWAP, null);
        if (tgt != null) {
            totalSize = getVolumeSize(tgt.getZfsLocalPath());
            vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), Volume.VolumeType.SWAP, null, tgt.getZfsLocalPath(), tgt.getIscsiName(), totalSize);
            vols.add(vol);
        }
        
        tgt = getVolume(imagePath, Volume.VolumeType.DATADISK, (long)1);
        if (tgt != null) {
            totalSize = getVolumeSize(tgt.getZfsLocalPath());
            vol = new VolumeVO(null, null, -1, -1, -1, -1, -1, new Long(-1), Volume.VolumeType.DATADISK, null, tgt.getZfsLocalPath(), tgt.getIscsiName(), totalSize);
            vols.add(vol);
        }
        
        return vols;
	}
	
    protected boolean hasIScsiClients(VolumeVO volume) {
        final Script script = new Script(_infoPath, _timeout, s_logger);
        script.add(volume.getPath());

        final IscsiConnectionParser parser = new IscsiConnectionParser();
        final String result = script.execute(parser);
        if (result != null) {
            throw new VmopsRuntimeException("Unable to execute iscsitadm script: " + result);
        }
        return parser.isConnected();
    }
    
	protected List<Pair<String, String>> gatherMounts() {
	    final Script script = new Script("showmount", _timeout, s_logger);
	    script.add("-a");

	    final List<Pair<String, String>> paths = new ArrayList<Pair<String, String>>();
	    script.execute(new ShowMountParser(_parent, paths));
	    return paths;
	}
	
    protected boolean isMounted(List<VolumeVO> volumes) {
        for (final VolumeVO child : volumes) {
            if (hasIScsiClients(child)) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Still connected: " + child);
                }
                return true;
            }
        }

        return false;
    }
    
    @Override
    protected Answer execute(final DestroyCommand cmd) {
        String image = cmd.getVolumeName();
        String result = null;
        List<VolumeVO> volumes = cmd.getVolumes();

        if (!isMounted(volumes)) {
            final String userfs = getUserPath(image);
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Locking on " + userfs);
            }
            String iscsiNames = null;
            if (volumes != null && volumes.size() != 0) {
            	StringBuilder builder = new StringBuilder();
            	for (VolumeVO vol : volumes) {
            		builder.append(vol.getIscsiName());
            		builder.append(",");
            	}
            	builder.delete(builder.length() - 1, builder.length());
            	iscsiNames = builder.toString();
            }
            synchronized(userfs) {
            	result = delete(image, iscsiNames);
            }
        } else {
            result = "STILL MOUNTED";
        }

        return new Answer(cmd, result == null, result);
    }
    
    @Override
    protected String delete(String path, String iscsiName) {
    	
        final Script cmd = new Script(_delvmPath, _timeout, s_logger);
        cmd.add("-i", path);
        if (iscsiName != null) {
        	cmd.add("-l", iscsiName);
        }

        final String result = cmd.execute();
        if (result != null) {
            return result;
        }

        cleanUpEmptyParents(path);
        return null;
    }
    
    protected boolean checkShared(final String path) {
        final Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("list");
        cmd.add("-Ho", "shareiscsi");
        cmd.add(path);

        final OneLineParser parser = new OutputInterpreter.OneLineParser();
        final String result = cmd.execute(parser);
        if (result != null) {
            s_logger.warn("Unable to check share: " + result);
            return false;
        }

        return parser.getLine().equalsIgnoreCase("on");
    }
    
	Set<String> findChildren(final String path) {
	    final Script cmd = new Script("zfs");
	    cmd.add("list", "-Hr");
	    cmd.add("-o", "name");
	    cmd.add(path);

	    final ZfsPathParser parser = new ZfsPathParser(_parent);
	    final String result = cmd.execute(parser);
	    if (result != null) {
	        throw new VmopsRuntimeException("Problem in listing children: " + result);
	    }

	    return parser.getPaths();
	}
	
	@Override
	protected CreateAnswer execute(final CreateCommand cmd) {
		String imagePath = cmd.getRootdiskFolder();

		int index = imagePath.lastIndexOf(":/");
		if (index != -1) {
			imagePath = imagePath.substring(index + 2);
		}

		index = imagePath.lastIndexOf('/');
		if (index == -1) {
			return new CreateAnswer(cmd, "Incorrect path passed: " + imagePath);
		}

		String result = null;

		final String userfs = getUserPath(imagePath);
		if (s_logger.isDebugEnabled()) {
			s_logger.debug("locking " + userfs);
		}
		synchronized(userfs) {
			if (cmd.getDataDiskSizeGB() == 0) {
				result = create(cmd.getTemplatePath(), imagePath, userfs, cmd.getDataDiskTemplatePath(), cmd.getLocalPath());
			} else {
				result = create(cmd.getTemplatePath(), imagePath, userfs, null, null, cmd.getDataDiskSizeGB(), cmd.getLocalPath());
			}
			if (result != null) {
				if (result.contains("dataset already exists") && existPath(imagePath)) {
					s_logger.debug("directory is already there so let's move it to the trashcan");
					final Set<String> children = findChildren(imagePath);
					List<VolumeVO> vols = new ArrayList<VolumeVO>(children.size());
					for (final String child : children) {
						// vols.add(new VolumeVO(-1l, null, -1, -1, -1, -1, -1, -1, child, new Long(-1), Volume.VolumeType.ROOT));
					}
					if (isMounted(vols)) {
						return new CreateAnswer(cmd, "vm already exists and is still mounted");
					}

					result = destroy(imagePath);
					if (result != null) {
						return new CreateAnswer(cmd, "Unable to clean an existing vm: " + imagePath);
					}

					if (s_logger.isDebugEnabled()) {
						s_logger.debug("Creating for a second time " + imagePath);
					}
					if (cmd.getDataDiskSizeGB() == 0) {
						result = create(cmd.getTemplatePath(), imagePath, userfs, cmd.getDataDiskTemplatePath(), cmd.getLocalPath());
					} else {
						// result = create(cmd.getTemplatePath(), imagePath, userfs, cmd.getDataDiskSizeGB(), cmd.isPublicTemplate(), cmd.getSnapshotName());
					}
				}

				if (result != null) {
					s_logger.warn("Unable to create." + result);

					delete(imagePath, null);
					return new CreateAnswer(cmd, result);
				}
			}
		}

		try {
			final List<VolumeVO> vols = getVolumes(imagePath);
            for (VolumeVO vol: vols) {
            	vol.setStorageResourceType(getStorageResourceType());
            }
			return new CreateAnswer(cmd, vols);
		} catch(final Throwable th) {
			delete(imagePath, null);
			return new CreateAnswer(cmd, th.getMessage());
		}
	}
	
    @Override
    protected UpgradeDiskAnswer execute(final UpgradeDiskCommand cmd) {
        final Script command = new Script(_upgradevmdiskPath, _timeout, s_logger);
        command.add("-v", cmd.getImagePath());
        command.add("-d", cmd.getNewSize());

        final String result = command.execute();
        return new UpgradeDiskAnswer(cmd, (result == null), result);
    }

    @Override
    protected void cleanUpEmptyParents(String imagePath) {
        imagePath = imagePath.substring(0, imagePath.lastIndexOf(File.separator));
        String destroyPath = null;
        while (imagePath.length() > _parent.length() && !hasChildren(imagePath)) {
        	destroyPath = imagePath;
            imagePath = imagePath.substring(0, imagePath.lastIndexOf(File.separator));
        }
        
        if (destroyPath != null) {
            final Script cmd = new Script("zfs", _timeout, s_logger);
            cmd.add("destroy", "-r", destroyPath);
            cmd.execute();
        }
    }
    
    @Override
    protected  long getUsedSize(){
        final Script cmd = new Script("zpool", _timeout, s_logger);
        cmd.add("list");
        cmd.add("-Ho", "used", _pool);

        final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        if (cmd.execute(parser) != null) {
            return -1;
        }

        return convertFilesystemSize(parser.getLine());
    }
    
    @Override
    protected String destroy(final String imagePath) {
        final StringBuilder trashPath = new StringBuilder();
        String result = createTrashDir(imagePath, trashPath);
        if (result != null) {
            return result;
        }

        final Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("rename");
        cmd.add(imagePath);
        cmd.add(trashPath.toString());
        result = cmd.execute();
        if (result != null) {
            return result;
        }

        s_logger.warn("Path " + imagePath + " has been stored to " + trashPath.toString());

        cleanUpEmptyParents(imagePath);
        return null;
    }
    
    @Override
    protected String createTrashDir(final String imagePath, final StringBuilder path) {
        final int index = imagePath.lastIndexOf(File.separator) + File.separator.length();
        path.append(_trashcanDir);
        path.append(imagePath.substring(_parent.length(), index));
        path.append(Long.toHexString(System.currentTimeMillis()));

        final Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("create", "-p", path.toString());

        final String result = cmd.execute();
        if (result != null) {
            return result;
        }

        path.append(File.separator).append(imagePath.substring(index));
        return null;
    }
    
    @Override
    public boolean existPath(final String path) {
        final Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("list", "-H");
        cmd.add("-o", "name");
        cmd.add(path);

        final String result = cmd.execute();
        if (result == null) {
            return true;
        }

        if (result == Script.ERR_TIMEOUT) {
            throw new VmopsRuntimeException("Script timed out");
        }

        return !result.contains("does not exist");
    }
    
    @Override
    public String createPath(final String createPath) {
        final Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("create", "-p", createPath);

        return cmd.execute();
    }


    protected String sharePath(final String path) {
        Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("set", "sharenfs=rw,anon=0");
        cmd.add(path);

        final String result = cmd.execute();
        if (result != null) {
            return result;
        }

        cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("set", "shareiscsi=on");
        cmd.add(path);

        return cmd.execute();
    }
    
    protected String checkAndRecoverZFS() {
    	final Script command = new Script(_zfsmountrecoveryPath, _timeout, s_logger);
    	return command.execute();
    }

    protected boolean checkIscsiTarget(final String tgt) {
        final Script command = new Script("svcs", _timeout, s_logger);
        command.add("-H", tgt);
        final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();

        String result = command.execute(parser);
        if (result != null) {
            return false;
        }

        result = parser.getLine();

        return result != null && result.contains("online");
    }

    protected boolean startIscsiTarget(final String tgt) {
        final Script command = new Script("svcadm", _timeout, s_logger);
        command.add("enable", tgt);
        String result = command.execute();
        if (result != null) {
        	return false;
        }
        
        try {
        	Thread.sleep(3000);
        } catch(InterruptedException e) {
        	
        }
        
        return checkIscsiTarget(tgt);
    }
    
    @Override
    protected long getTotalSize() {
        final Script cmd = new Script("zpool", _timeout, s_logger);
        cmd.add("list");
        cmd.add("-Ho", "size", _pool);

        final OutputInterpreter.OneLineParser parser = new OutputInterpreter.OneLineParser();
        if (cmd.execute(parser) != null) {
            return -1;
        }
        return convertFilesystemSize(parser.getLine());
    }
    
    public static class ZfsPathParser extends OutputInterpreter {
        String _parent;
        SortedSet<String> paths;
        public ZfsPathParser(final String parent) {
            _parent = parent;
        }

        @Override
        public boolean drain() {
            return true;
        }

        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            final int parentLength = _parent.length() + 1;

            paths = new TreeSet<String>();
            line = reader.readLine();
            if (line == null) {
                return null;
            }
            while ((line = reader.readLine()) != null) {

                // We can't include the parent directories because zfs
                // list actually returns all of the parent directories.
                // Sending those up causes a problem because it's not on
                // the management server so a delete will come down.
                // And there's a significant number of them so it's
                // actually quite useless and takes up bandwidth.
                final String subdir = line.substring(parentLength);
                int index = subdir.indexOf(File.separatorChar);
                if (index == -1) {
                    continue;
                }
                index = subdir.indexOf(File.separatorChar, index + 1);
                if (index == -1) {
                    continue;
                }
                paths.add(subdir);
            }

            return null;
        }

        public SortedSet<String> getPaths() {
            return paths;
        }
    }

    public static class ZfsCheckChildrenParser extends OutputInterpreter {
        boolean hasChildren = false;

        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;

            line = reader.readLine();
            if (line == null) {
                return null;
            }
            line = reader.readLine();
            hasChildren = line != null;

            return null;
        }

        public boolean hasChildren() {
            return hasChildren;
        }
    }

	@Override
	protected StorageResourceType getStorageResourceType() {
		return StorageResourceType.STORAGE_HOST;
	}

	@Override
	protected void configureFolders(final String name, final Map<String, Object> params) throws ConfigurationException {
		String pool = (String)params.get("pool");
        _pool = pool == null ? "tank" : pool;

        _pool = _pool.substring(_pool.startsWith(File.separator) ? 1 : 0, _pool.length() - (_pool.endsWith(File.separator) ? 1 : 0)); // Get rid of the file separator

        if (_pool.startsWith(File.separator)) {
            _pool = _pool.substring(1, _pool.length());
        }
      
        // _rootDir will be something like tank/vmops/[guid]
        _rootDir = _pool + File.separator + _rootFolder + (_guid != null ? (File.separator + _guid) : "");
        
        //  Make sure that _parent actually exists
        if (!existPath(_rootDir)) {
        	throw new ConfigurationException("The ZFS path tank/vmops/[guid] does not exist. The guid in agent.properties is out of sync with the ZFS template path.");
        }

        String vmFolder = (String)params.get("vmFolder");
        _vmFolder = vmFolder == null ? "vm" : vmFolder;
        
        String trashcanFolder = (String)params.get("trashcanFolder");
        _trashcanFolder = trashcanFolder == null ? "trashcan" : trashcanFolder;

        String instanceFolder = (String)params.get("instance");
        
        if (instanceFolder == null)
        	_parent = _rootDir + File.separator + _vmFolder;
        else
        	_parent = _rootDir + File.separator + instanceFolder + File.separator + _vmFolder;

        if (instanceFolder == null)
        	_trashcanDir = _rootDir + File.separator + _trashcanFolder;
        else
        	_trashcanDir = _rootDir + File.separator + instanceFolder + File.separator + _trashcanFolder;

        s_logger.info("vms = " + _parent + "; Trashcan = " + _trashcanDir);

        String result;
        
        if (!existPath(_parent)) {
            s_logger.info("Creating vm path: " + _parent);
            result = createPath(_parent);
            if (result != null) {
                throw new ConfigurationException("Cannot create " + _parent + " due to " + result);
            }
        }

        if (!existPath(_trashcanDir)) {
            s_logger.info("Creating trashcan path: " + _trashcanDir);
            result = createPath(_trashcanDir);
            if (result != null) {
                throw new ConfigurationException("Cannot create " + _trashcanDir + " due to " + result);
            }
        }

        if (!checkShared(_parent)) {
            s_logger.info("Sharing vm path: " + _parent);
            result = sharePath(_parent);
            if (result != null) {
                throw new ConfigurationException("Cannot share " + _parent + " due to " + result);
            }
        }

        String value = (String)params.get("iscsi.tgt.name");
        if (value == null) {
            s_logger.info("No 'iscsi.tgt.name' property found: defaulting to 'iscsitgt'");
            value = "iscsitgt";
        }
        
        if (!checkIscsiTarget(value)) {
            s_logger.info("Starting the iscsi target: " + value);
            if (!startIscsiTarget(value)) {
                throw new ConfigurationException("Iscsi Target is not running");
            }
        }
	}

	@Override
	public StartupCommand[] initialize() {
		StartupCommand[] cmds = super.initialize();
        final Map<String, TemplateInfo> tInfo = _downloadManager.gatherTemplateInfo();
        StartupStorageCommand ssCmd = (StartupStorageCommand)cmds[0];
        ssCmd.setTemplateInfo(tInfo);
		return cmds;
	}

	@Override
	protected String create(String templateFolder, String rootdiskFolder,
			String userPath, String datadiskFolder, String datadiskName,
			int datadiskSize, String localPath) {
		final Script command = new Script(_createvmPath, _timeout, s_logger);

        // for private templates, the script needs the snapshot name being used to create the VM
        command.add("-t", templateFolder);
        command.add("-i", rootdiskFolder);
        command.add("-u", userPath);
        if (datadiskSize != 0) {
            command.add("-s", Integer.toString(datadiskSize));
        }
        return command.execute();
	}
}
