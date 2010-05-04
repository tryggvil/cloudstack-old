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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.vmops.agent.api.storage.ShareAnswer;
import com.vmops.agent.api.storage.ShareCommand;
import com.vmops.resource.ServerResource;
import com.vmops.storage.VolumeVO;
import com.vmops.utils.script.OutputInterpreter;
import com.vmops.utils.script.Script;
import com.vmops.utils.script.OutputInterpreter.OneLineParser;

@Local(value={ServerResource.class})
public class ComstarStorageResource extends ZfsIscsiStorageResource {
    private static final Logger s_logger = Logger.getLogger(ComstarStorageResource.class);
    
    public ComstarStorageResource() {
    }
    
    @Override
    protected String getDefaultScriptsDir() {
        return "scripts/storage/zfs/iscsi/comstar/filebacked";
    }

    @Override
    protected boolean isMounted(List<VolumeVO> volumes) {
    	return false;
    }
    
    @Override
    protected String destroy(final String imagePath) {
        final StringBuilder trashPath = new StringBuilder();
        String result = createTrashDir(imagePath, trashPath);
        if (result != null) {
            return result;
        }

        final Script cmd1 = new Script(_viewandluremovePath, _timeout, s_logger);
        cmd1.add(imagePath);
        result = cmd1.execute();
        if (result != null) {
        	s_logger.warn("Failed to remove views and logical units in path: " + imagePath);
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
    protected void cleanUpEmptyParents(String imagePath) {
        imagePath = imagePath.substring(0, imagePath.lastIndexOf(File.separator));
        while (imagePath.length() > _parent.length() && !hasChildren(imagePath)) {
            final Script cmd = new Script(_zfsdestroyPath, _timeout, s_logger);
            cmd.add(imagePath);
            cmd.execute();
            imagePath = imagePath.substring(0, imagePath.lastIndexOf(File.separator));
        }
    }

    @Override
    protected ShareAnswer execute(final ShareCommand cmd) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("execute(ShareCommand cmd): cmd = " + cmd);
        }
        
        final List<VolumeVO> volumes = cmd.getVolumes();
        Script script;
        boolean failed = false;
        String errors = new String("");

        HashMap<String, Integer> luns = null;
        LunParser parser = null;
        if (cmd.isShare()) {
            luns = new HashMap<String, Integer>();
            parser = new LunParser();
        }
        
        int i = 0;
        for (final VolumeVO vol : volumes) {
            script = new Script(_sharePath, _timeout, s_logger);
            final String init_iqn = cmd.getInitiatorIqn();
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("execute(ShareCommand cmd): cmd.getInitiatorIqn = " + init_iqn);
            }
            if (cmd.isShare()) {
                script.add("-i", init_iqn);
                if (cmd.removePreviousShare()) {
                	script.add("-m");
                }
            } else {
                script.add("-u");
                if (init_iqn == null)
                    script.add("-i", "unshare_all");
                else
                    script.add("-i", init_iqn);
            }
            script.add("-t", vol.getIscsiName());

            if (cmd.isShare()) {
                errors = script.execute(parser);
            } else {
                errors = script.execute();
            }
            if (errors != null) {
                failed = true;
            }

            if (cmd.isShare()) {
                luns.put(vol.getIscsiName(), parser.getLun());
            }
        }
        
        if (failed) {
            return new ShareAnswer(cmd, "lu_share.sh failed\n" + errors);
        }
        
        return new ShareAnswer(cmd, luns);
    }
    
    @Override
	protected boolean checkShared(final String path) {
        Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("list");
        cmd.add("-Ho", "sharenfs");
        cmd.add(path);

        OneLineParser parser = new OutputInterpreter.OneLineParser();
        String result = cmd.execute(parser);
        if (result != null) {
            s_logger.warn("Unable to check share: " + result);
            return false;
        }
        
        // Make sure that NFS share is off
        if (parser.getLine().equalsIgnoreCase("rw,anon=0")) {
            return false;
        }
            
        cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("list");
        cmd.add("-Ho", "shareiscsi");
        cmd.add(path);

        parser = new OutputInterpreter.OneLineParser();
        result = cmd.execute(parser);
        if (result != null) {
            s_logger.warn("Unable to check iscsi share: " + result);
            return false;
        }

        return parser.getLine().equalsIgnoreCase("off");
    }
    
    @Override
    protected String sharePath(final String path) {
        Script cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("set", "sharenfs=rw,anon=0");
        cmd.add(path);

        final String result = cmd.execute();
        if (result != null) {
            return result;
        }

        cmd = new Script("zfs", _timeout, s_logger);
        cmd.add("set", "shareiscsi=off");
        cmd.add(path);

        return cmd.execute();
    }
    
    @Override
    public boolean configure(final String name, final Map<String, Object> params) throws ConfigurationException{
        params.put("iscsi.tgt.name", "iscsi/target");   // comstar iscsi
        
    	super.configure(name, params);
    	
    	final Script list_cmd = new Script("itadm", _timeout, s_logger);
    	list_cmd.add("list-target");
    	
    	final ItadmListTargetParser parser = new ItadmListTargetParser();
    	String result = list_cmd.execute(parser);
    	if (result == null) {
    		s_logger.info("Target IQN = " + parser.getTargetIqn());
    		return true;
    	}
    	
    	final Script create_cmd = new Script("itadm", _timeout, s_logger);
    	create_cmd.add("create-target");
    	
    	final ItadmCreateTargetParser parser2 = new ItadmCreateTargetParser();
    	result = create_cmd.execute(parser2);
    	if (result == null) {
    		s_logger.info("Target IQN = " + parser2.getTargetIqn());
    		return true;
    	} else {
    		s_logger.error("Failed to create target IQN: " + result);
    		return false;
    	}
    }
    
    protected class ItadmListTargetParser extends OutputInterpreter {
    	/* Typical output (or nothing, if target has not been created yet):
    	 * -bash-3.2# itadm list-target
		 * TARGET NAME                                                  STATE    SESSIONS
		 * iqn.1986-03.com.sun:02:29f7d69d-7ea1-64c6-a92a-b475887f69dd  online   0
    	 */
        String target_iqn;
        
        public ItadmListTargetParser() {
        	this.target_iqn = null;
        }
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
                final int index = line.indexOf("iqn.");
                if (index == -1) {
                    continue;
                }
                
                final int end = line.indexOf(" ", index + 4);
                if (end == -1) {
                    continue;
                }
                
                target_iqn = line.substring(index, end);
            }
            if (target_iqn != null)
            	return null;
            else
            	return "no target exists";
        }
        
        public String getTargetIqn() {
            return target_iqn;
        }
    }

    protected class ItadmCreateTargetParser extends OutputInterpreter {
    	/* Typical output:
    	 * -bash-3.2# itadm create-target
    	 * Target iqn.1986-03.com.sun:02:29f7d69d-7ea1-64c6-a92a-b475887f69dd successfully created
    	 * 
    	 * Failure output (after trying to create an existing iqn):
    	 * -bash-3.2# itadm create-target -n iqn.1986-03.com.sun:02:5085eb88-66d2-e361-bbe1-b120ff86916f
		 * iSCSI target iqn.1986-03.com.sun:02:5085eb88-66d2-e361-bbe1-b120ff86916f already configured
		 * itadm create-target failed with error 17
    	 */
        String target_iqn;
        
        public ItadmCreateTargetParser() {
        	this.target_iqn = null;
        }
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
        	String line = null;
        	while ((line = reader.readLine()) != null) {
                final int index = line.indexOf("iqn.");
                if (index == -1) {
                    continue;
                }
                
                final int end = line.indexOf(" ", index + 4);
                if (end == -1) {
                    continue;
                }
                
                target_iqn = line.substring(index, end);
            }
            if (target_iqn != null) {
            	return null;
        	} else
            	return line;
        }
        
        public String getTargetIqn() {
            return target_iqn;
        }
    }
    
    protected class LunParser extends OutputInterpreter {
        int lun = -1;
        
        @Override
        public String interpret(final BufferedReader reader) throws IOException {
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("lun ")) {
                    String[] tokens = line.split(" ");
                    if (tokens.length < 2) {
                        continue;
                    }
                    
                    try {
                        lun = Integer.parseInt(tokens[1]);
                        return null;
                    } catch (NumberFormatException e) {
                    }
                }
            }
            
            return "Unable to get the lun";
        }
        
        public int getLun() {
            return lun;
        }
    }

}
