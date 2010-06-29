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

package com.cloud.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.BackupSnapshotAnswer;
import com.cloud.agent.api.BackupSnapshotCommand;
import com.cloud.agent.api.CheckHealthAnswer;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.CheckStateAnswer;
import com.cloud.agent.api.CheckStateCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.CreateVolumeFromSnapshotAnswer;
import com.cloud.agent.api.CreateVolumeFromSnapshotCommand;
import com.cloud.agent.api.DeleteSnapshotBackupAnswer;
import com.cloud.agent.api.DeleteSnapshotBackupCommand;
import com.cloud.agent.api.GetFileStatsAnswer;
import com.cloud.agent.api.GetFileStatsCommand;
import com.cloud.agent.api.GetStorageStatsAnswer;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVncPortAnswer;
import com.cloud.agent.api.GetVncPortCommand;
import com.cloud.agent.api.ManageSnapshotAnswer;
import com.cloud.agent.api.ManageSnapshotCommand;
import com.cloud.agent.api.ModifyStoragePoolAnswer;
import com.cloud.agent.api.ModifyStoragePoolCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.PingStorageCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.agent.api.StartupStorageCommand;
import com.cloud.agent.api.StoragePoolInfo;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.api.storage.CreatePrivateTemplateAnswer;
import com.cloud.agent.api.storage.CreatePrivateTemplateCommand;
import com.cloud.agent.api.storage.DeleteTemplateCommand;
import com.cloud.agent.api.storage.DestroyCommand;
import com.cloud.agent.api.storage.DownloadAnswer;
import com.cloud.agent.api.storage.DownloadCommand;
import com.cloud.agent.api.storage.DownloadProgressCommand;
import com.cloud.agent.api.storage.ManageVolumeAnswer;
import com.cloud.agent.api.storage.ManageVolumeCommand;
import com.cloud.agent.api.storage.PrimaryStorageDownloadCommand;
import com.cloud.agent.api.storage.ShareAnswer;
import com.cloud.agent.api.storage.ShareCommand;
import com.cloud.agent.api.storage.UpgradeDiskAnswer;
import com.cloud.agent.api.storage.UpgradeDiskCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.storage.StoragePool;
import com.cloud.storage.StoragePoolVO;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.StoragePool.StoragePoolType;
import com.cloud.storage.Volume.StorageResourceType;
import com.cloud.storage.template.TemplateInfo;
import com.cloud.vm.State;

public class AgentStorageResource extends AgentResourceBase {
    private static final Logger s_logger = Logger.getLogger(AgentStorageResource.class);
    
    protected String _parent;

	public AgentStorageResource(AgentContainer agent) {
		super(agent);
	}
	
	@Override
	public Answer executeRequest(Command cmd) {
        if (cmd instanceof CreateCommand) {
            return execute((CreateCommand)cmd);
        } else if (cmd instanceof DestroyCommand) {
            return execute((DestroyCommand)cmd);
        } else if (cmd instanceof GetFileStatsCommand) {
            return execute((GetFileStatsCommand)cmd);
        } else if (cmd instanceof GetStorageStatsCommand) {
            return execute((GetStorageStatsCommand)cmd);
        } else if (cmd instanceof CheckStateCommand) {
            return execute((CheckStateCommand) cmd);
        } else if (cmd instanceof CheckHealthCommand) {
            return execute((CheckHealthCommand)cmd);
        } else if (cmd instanceof UpgradeDiskCommand) {
            return execute((UpgradeDiskCommand) cmd);
        } else if (cmd instanceof ShareCommand) {
            return execute((ShareCommand)cmd);
        } else if (cmd instanceof CreatePrivateTemplateCommand) {
            return execute((CreatePrivateTemplateCommand)cmd);
        } else if (cmd instanceof ManageSnapshotCommand) {
        	return execute((ManageSnapshotCommand)cmd);
        } else if (cmd instanceof BackupSnapshotCommand) {
            return execute((BackupSnapshotCommand)cmd);
        } else if (cmd instanceof DeleteSnapshotBackupCommand) {
            return execute((DeleteSnapshotBackupCommand)cmd);
        } else if (cmd instanceof CreateVolumeFromSnapshotCommand) {
            return execute((CreateVolumeFromSnapshotCommand)cmd);
        } else if (cmd instanceof ModifyStoragePoolCommand ){
        	return execute ((ModifyStoragePoolCommand) cmd);
        } else if (cmd instanceof DownloadProgressCommand) {
            return execute((DownloadProgressCommand)cmd);
        } else if (cmd instanceof DownloadCommand) {
            return execute((DownloadCommand)cmd);
        } else if (cmd instanceof ManageVolumeCommand) {
        	return execute((ManageVolumeCommand)cmd);
        } else if (cmd instanceof DeleteTemplateCommand) {
        	return execute((DeleteTemplateCommand)cmd);
        } else if (cmd instanceof ReadyCommand) {
        	return execute((ReadyCommand)cmd);
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
	public StartupCommand [] initialize() {
    	StorageMgr storageMgr = getAgent().getStorageMgr();
		
        Map<String, TemplateInfo> tInfo = new HashMap<String, TemplateInfo>();
        populateTemplateStartupInfo(tInfo);
        
        StoragePoolInfo poolInfo = new StoragePoolInfo("sp."+getAgent().getHostPrivateIp(),
        	java.util.UUID.randomUUID().toString(),
        	getAgent().getHostPrivateIp(), "/host/path", "/local/path",
        	StoragePool.StoragePoolType.NetworkFilesystem,
        	storageMgr.getTotalSize(),
        	storageMgr.getTotalSize() - storageMgr.getUsedSize());
        
        StartupStorageCommand cmd = new StartupStorageCommand(_parent, StoragePoolType.NetworkFilesystem,
        	getAgent().getStorageMgr().getTotalSize(), tInfo);
        
        cmd.setPoolInfo(poolInfo);
        cmd.setResourceType(StorageResourceType.SECONDARY_STORAGE);
        cmd.getHostDetails().put("orig.url", "nfs://nfs/" + getAgent().getId());
        cmd.getHostDetails().put("mount.parent", "/mnt");
        cmd.getHostDetails().put("mount.path", "/mant/" + getAgent().getId());
        
        return new StartupCommand [] {cmd};
	}

	protected Answer execute(CreateCommand cmd) {
        StorageMgr storageMgr = getAgent().getStorageMgr();
        
        String imagePath = cmd.getRootdiskFolder();
        
        int index = imagePath.lastIndexOf(":/");
        if (index != -1) {
            imagePath = imagePath.substring(index + 2);
        }
        
        index = imagePath.lastIndexOf('/');
        if (index == -1) {
            return new CreateAnswer(cmd, "Incorrect path passed: " + imagePath);
        }
        
        String userfs = getUserPath(imagePath);
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("locking " + userfs);
        }
        synchronized(userfs) {
        	if (cmd.getDataDiskSizeGB() == 0) {
        		storageMgr.create(cmd.getTemplatePath(), imagePath, userfs, cmd.getDataDiskTemplatePath());
        	} else {
        		storageMgr.create(cmd.getTemplatePath(), imagePath, userfs, cmd.getDataDiskSizeGB());
        	}
        }
        
        try {
            List<VolumeVO> vols = getVolumes(imagePath);
            return new CreateAnswer(cmd, vols);
        } catch(Throwable th) {
        	storageMgr.delete(imagePath);
            return new Answer(cmd, false, th.getMessage());
        }
    }

    protected Answer execute(DestroyCommand cmd) {
    	StorageMgr storageMgr = getAgent().getStorageMgr();
    	
        String image = cmd.getVolumeName();
        List<VolumeVO> volumes = cmd.getVolumes();
        
        String[] paths = null;
        if (volumes.size() > 0) {
	        paths = new String[volumes.size()];
	        int i = 0;
	        for(Volume vol : volumes) {
	            paths[i++] = vol.getPath();
	        }
        }
        
        String result = null;
        
        if (!isMounted(paths)) {
            if (paths != null) {
                String userfs = getUserPath(image);
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Locking on " + userfs);
                }
                synchronized(userfs) {
                    result = storageMgr.delete(image);
                }
            } else {
            	assert(false);
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("VM has no volume attached? image : " + image);
                }
            }
        } else {
            result = "STILL MOUNTED";
        }
        
        return new Answer(cmd, result == null, result);
    }
    
    protected Answer execute(GetFileStatsCommand cmd) {
        String image = cmd.getPaths();
        return new GetFileStatsAnswer(cmd, getAgent().getStorageMgr().getSize(image));
    }

    protected Answer execute(PrimaryStorageDownloadCommand cmd) {
    	// use random UUID
    	String uuid = UUID.randomUUID().toString();
        DownloadAnswer answer = new DownloadAnswer(null, 100, cmd, com.cloud.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, uuid, uuid);
        
        // use hard-coded template size, we are just simulating after all ...
        answer.setTemplateSize(1000);
        return answer;
    }
    
    protected Answer execute(DownloadCommand cmd) {
    	
    	DownloadAnswer answer;
    	if(cmd instanceof DownloadProgressCommand) {
        	if(s_logger.isInfoEnabled())
        		s_logger.info("Executing download-progress command, url: " + cmd.getUrl() + ", name: " + cmd.getName()
        			+ ", description: " + cmd.getDescription() + ", job: " + ((DownloadProgressCommand)cmd).getJobId());
    		
	        String jobId = ((DownloadProgressCommand)cmd).getJobId();
	        if(jobId == null)
	        	jobId = String.valueOf(System.currentTimeMillis());
	        	
	        switch (((DownloadProgressCommand)cmd).getRequest()) {
	        case GET_STATUS:
	            break;
	        case ABORT:
	            break;
	        case RESTART:
	            break;
	        case PURGE:
	        	answer = new DownloadAnswer(jobId, 100, cmd,
	            	com.cloud.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, "/tmp/" + jobId, "/template/" + jobId);
	        	answer.setTemplateSize(1000);
	        	return answer;
	        	
	        default:
	            break;
	        }
	        
	        answer = new DownloadAnswer(jobId, 100, cmd,
	        	com.cloud.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, "/tmp/" + jobId, "/template/" + jobId);
	        answer.setTemplateSize(1000);
	        return answer;
    	}
    	
    	long jobId = System.currentTimeMillis();
    	
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Executing download command, url: " + cmd.getUrl() + ", name: " + cmd.getName()
    			+ ", description: " + cmd.getDescription());
    	
    	answer = new DownloadAnswer(String.valueOf(jobId), 100, cmd,
    		com.cloud.storage.VMTemplateStorageResourceAssoc.Status.DOWNLOADED, "/tmp/" + jobId, "/template/" + jobId);
    	answer.setTemplateSize(1000);
    	return answer;
	}
    
    protected GetVncPortAnswer execute(GetVncPortCommand cmd) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Executing getVncPort command, vm: " + cmd.getId() + ", name: " + cmd.getName());
    	
    	VmMgr vmMgr = getAgent().getVmMgr();
    	int port = vmMgr.getVncPort(cmd.getName());
    	
    	// make it more real by plus 5900 base port number
    	if(port >= 0)
    		port += 5900;
    	
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Return vnc port: " + port + " for " + cmd.getName());
    	
    	return new GetVncPortAnswer(cmd, port);
    }
    
	protected ManageVolumeAnswer execute(ManageVolumeCommand cmd) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Executing ManageVolumeCommand, folder: " + cmd.getFolder() + ", path: " + cmd.getPath()
    			+ ", size(GB): " + cmd.getSize() + ", add: " + cmd.getAdd());
    	
    	StorageMgr mgr = getAgent().getStorageMgr();
    	if(cmd.getAdd()) {
    		mgr.create("", cmd.getPath(), cmd.getFolder(), (int)cmd.getSize()*1024);
    	} else {
    		mgr.delete(cmd.getPath());
    	}
    	
    	return new ManageVolumeAnswer(cmd, true, null, cmd.getSize());
	}
    
    protected GetStorageStatsAnswer execute(GetStorageStatsCommand cmd) {
        long size = getAgent().getStorageMgr().getUsedSize();
        return size != -1 ? new GetStorageStatsAnswer(cmd, 0, size) : new GetStorageStatsAnswer(cmd, "Unable to get storage stats");
    }
	
    protected CheckStateAnswer execute(CheckStateCommand cmd) {
        return new CheckStateAnswer(cmd, State.Unknown, "Not Implemented");
    }
    
    protected CheckHealthAnswer execute(CheckHealthCommand cmd) {
        return new CheckHealthAnswer(cmd, true);
    }
    
    protected UpgradeDiskAnswer execute(UpgradeDiskCommand cmd) {
        return new UpgradeDiskAnswer(cmd, true, null);
    }
    
    protected ShareAnswer execute(ShareCommand cmd) {
        return new ShareAnswer(cmd, new HashMap<String, Integer>());
    }
    
    protected ManageSnapshotAnswer execute(final ManageSnapshotCommand cmd) {
    	if(s_logger.isInfoEnabled())
    		s_logger.info("Manage snapshot command {" + cmd.getCommandSwitch() + ", "
    			+ cmd.getVolumePath() + ", " + cmd.getSnapshotId() + "}");

        return new ManageSnapshotAnswer(cmd, true, null);
    }
    
    protected CreatePrivateTemplateAnswer execute(final CreatePrivateTemplateCommand cmd) {
        CreatePrivateTemplateAnswer answer = new CreatePrivateTemplateAnswer(cmd, true, null, null, 0, null, null);
        answer.setPath("");
        return answer;
    }
    
    protected BackupSnapshotAnswer execute(final BackupSnapshotCommand cmd) {
        return new BackupSnapshotAnswer(cmd, true, null, UUID.randomUUID().toString());
    }
    
    protected DeleteSnapshotBackupAnswer execute(final DeleteSnapshotBackupCommand cmd) {
        return new DeleteSnapshotBackupAnswer(cmd, true, "1");
    }
    
    protected CreateVolumeFromSnapshotAnswer execute(final CreateVolumeFromSnapshotCommand cmd) {
        return new CreateVolumeFromSnapshotAnswer(cmd, true, null, UUID.randomUUID().toString());
    }
    
    protected Answer execute(final DeleteTemplateCommand cmd) {
    	return new Answer(cmd, true, null);
    }
    
    protected Answer execute(final ReadyCommand cmd) {
    	return new ReadyAnswer(cmd);
    }
    
	protected Answer execute(ModifyStoragePoolCommand cmd) {
	    StoragePoolVO pool = cmd.getPool();
		if (cmd.getAdd()) {
					
			long capacity = getAgent().getStorageMgr().getTotalSize();
			long used = getAgent().getStorageMgr().getUsedSize();
			long available = capacity - used;
			Map<String, TemplateInfo> tInfo = new HashMap<String, TemplateInfo>();
			populateTemplateStartupInfo(tInfo);
			
			ModifyStoragePoolAnswer answer = new ModifyStoragePoolAnswer(cmd, capacity, available, tInfo);
			
			if(s_logger.isInfoEnabled())
				s_logger.info("Sending ModifyNetfsStoragePoolCommand answer with capacity: " + capacity + ", used: " +
					used + ", available: " + available);
			return answer;
		} else {
			if(s_logger.isInfoEnabled())
				s_logger.info("ModifyNetfsStoragePoolCmd is not add command, cmd: " +cmd.toString());
		    return new Answer(cmd);
		}
	}
    
    protected String getUserPath(String image) {
        return image.substring(0, image.indexOf("/", _parent.length() + 2)).intern();
    }
    
    protected List<VolumeVO> getVolumes(String imagePath) {
    	StorageMgr storageMgr = getAgent().getStorageMgr();
        ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>();

        String path = storageMgr.getVolumeName(imagePath, null);
        long totalSize = storageMgr.getVolumeSize(path);
        
        VolumeVO vol = new VolumeVO(null, null, -1, -1, -1, -1, new Long(-1), null, path, totalSize, Volume.VolumeType.ROOT);
        vol.setStorageResourceType(Volume.StorageResourceType.STORAGE_HOST);
        if(vol.getPath() == null)
        	vol.setPath("");
        vol.setFolder("");
        vols.add(vol);

        path = storageMgr.getVolumeName(imagePath, (long)1);
        if (path != null) {
            totalSize = storageMgr.getVolumeSize(path);
            
            vol = new VolumeVO(null, null, -1, -1, -1, -1, new Long(-1), path, null, totalSize, Volume.VolumeType.DATADISK);
            vol.setStorageResourceType(Volume.StorageResourceType.STORAGE_HOST);
            if(vol.getPath() == null)
            	vol.setPath("");
            vol.setFolder("");
            vols.add(vol);
        }
        return vols;
    }
    
    protected boolean isMounted(String[] paths) {
        return false;
    }
    
    public static void populateTemplateStartupInfo(Map<String, TemplateInfo> info) {
    	info.put("routing", new TemplateInfo("routing",
    			"tank/volumes/demo/template/private/u000000/os/routing", false));
    	info.put("consoleproxy", new TemplateInfo("consoleproxy",
        		"tank/volumes/demo/template/private/u000000/os/consoleproxy", false));
    	
    	info.put("centos53-x86_64", new TemplateInfo("centos53-x86_64",
        		"tank/volumes/demo/template/public/os/centos53-x86_64", true));
    	info.put("win2003sp2", new TemplateInfo("win2003sp2",
        		"tank/volumes/demo/template/public/os/win2003sp2", true));
    	info.put("winxpsp3", new TemplateInfo("winxpsp3",
        		"tank/volumes/demo/template/public/os/winxpsp3", true));
    	info.put("fedora10-x86_64", new TemplateInfo("fedora10-x86_64",
        		"tank/volumes/demo/template/public/os/fedora10-x86_64", true));
    	info.put("fedora9-x86_64", new TemplateInfo("fedora9-x86_64",
        		"tank/volumes/demo/template/public/os/fedora9-x86_64", true));
    	info.put("centos52-x86_64", new TemplateInfo("centos52-x86_64",
        		"tank/volumes/demo/template/public/os/centos52-x86_64", true));
    }
    
    private void populateTemplateStorage() {
    	MockStorageMgr storageMgr = (MockStorageMgr)getAgent().getStorageMgr();
    	
    	String volume = "tank/volumes/demo/template/private/u000000/os/routing";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));
    	
    	volume = "tank/volumes/demo/template/private/u000000/os/consoleproxy";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));
    	
    	volume = "tank/volumes/demo/template/public/os/centos53-x86_64";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));
    	
    	volume = "tank/volumes/demo/template/public/os/win2003sp2";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));
    	
    	volume = "tank/volumes/demo/template/public/os/winxpsp3";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));

    	volume = "tank/volumes/demo/template/public/os/fedora10-x86_64";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));

    	volume = "tank/volumes/demo/template/public/os/fedora9-x86_64";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));

    	volume = "tank/volumes/demo/template/public/os/centos52-x86_64";
    	storageMgr.addVolume(volume, new MockVolume(volume, 1024));
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            s_logger.warn("Base class was unable to configure");
            return false;
        }
        
        String value = (String)params.get("instance");
        if (value == null) {
            value = (String)params.get("parent");
            if (value == null) {
                value = "vmops";
            }
        }
        if (!value.endsWith("/")) {
        	value = value + "/";
        }
        _parent = value + "vm";
        
        s_logger.info("Storage parent path : " + _parent);
        
        populateTemplateStorage();
        return true;
    }
}
