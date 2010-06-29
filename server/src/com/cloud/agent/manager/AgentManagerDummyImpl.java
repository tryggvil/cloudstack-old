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
package com.cloud.agent.manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.ejb.Local;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.GetFileStatsAnswer;
import com.cloud.agent.api.GetFileStatsCommand;
import com.cloud.agent.api.GetStorageStatsCommand;
import com.cloud.agent.api.GetVmStatsAnswer;
import com.cloud.agent.api.GetVmStatsCommand;
import com.cloud.agent.api.StartAnswer;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartRouterCommand;
import com.cloud.agent.api.StopAnswer;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.VmStatsEntry;
import com.cloud.agent.api.storage.CreateAnswer;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.host.HostStats;
import com.cloud.storage.StorageStats;
import com.cloud.storage.Volume;
import com.cloud.storage.VolumeVO;

@Local(value={AgentManager.class})
public class AgentManagerDummyImpl extends AgentManagerImpl implements AgentManager {
	private final Random ran = new Random();
	
    protected AgentManagerDummyImpl() {
    }
    /*
    protected Answer internalSend(long hostId, Command cmd) {
        if (cmd instanceof CreateCommand) {
            CreateCommand create = (CreateCommand)cmd;
            ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>(2);
            
            vols.add(new VolumeVO(null, create.getVmPath(), -1, -1, -1, -1, -1, "/path/root", 1024000, Volume.VolumeType.ROOT));
            vols.add(new VolumeVO(null, create.getVmPath(), -1, -1, -1, -1, -1, "/path/data", 10240000, Volume.VolumeType.DATADISK));
            return new CreateAnswer(create, vols);
        } else if (cmd instanceof StartCommand) {
        	return new StartAnswer((StartCommand)cmd, 10);
        } else if (cmd instanceof StartRouterCommand) {
        	return new Answer(cmd);
        } else if (cmd instanceof StopCommand) {
        	return new StopAnswer((StopCommand)cmd, "", 0, 111111L, 1123123L);
        } else if (cmd instanceof GetVmStatsCommand) {
        	return new GetVmStatsAnswer((GetVmStatsCommand)cmd, new HashMap<String, long[]>(), new HashMap<String, long[]>());
        }
        return new Answer(cmd);
    }*/
    
    @Override
    public Answer [] send(Long agentId, Command [] cmds, boolean stopOnError) {
    	Answer[] answers = new Answer[cmds.length];
    	Command cmd = cmds[0];
    	answers[0] = new Answer(cmd);
    	if (cmd instanceof CreateCommand) {
            CreateCommand create = (CreateCommand)cmd;
            ArrayList<VolumeVO> vols = new ArrayList<VolumeVO>(2);
            
            vols.add(new VolumeVO(null, create.getRootdiskFolder(), -1, -1, -1, -1, new Long(-1), "/path", "/path/root", 1024000, Volume.VolumeType.ROOT));
            vols.add(new VolumeVO(null, create.getRootdiskFolder(), -1, -1, -1, -1, new Long(-1), "/path", "/path/data", 10240000, Volume.VolumeType.DATADISK));
            answers[0] = new CreateAnswer(create, vols);
        } else if (cmd instanceof StartCommand) {
        	answers[0] = new StartAnswer((StartCommand)cmd);
        } else if (cmd instanceof StartRouterCommand) {
        	answers[0] = new Answer(cmd);
        } else if (cmd instanceof StopCommand) {
        	answers[0] = new StopAnswer((StopCommand)cmd, "", 0, 111111L, 1123123L);
        } else if (cmd instanceof GetVmStatsCommand) {
        	answers[0] = new GetVmStatsAnswer((GetVmStatsCommand) cmd, null);
        } else if (cmds[0] instanceof GetFileStatsCommand) {
        	for (int i = 0; i < cmds.length; i++) {
        		answers[i] = new GetFileStatsAnswer((GetFileStatsCommand)cmds[i], ran.nextInt(500) * 1024L * 1024L);
        	}
        }
    	return answers;
    }
    
    @Override
    public Answer easySend(Long agentId, Command cmd) {
    	if (cmd instanceof GetStorageStatsCommand) {
    		DummyStorageStats stats = new DummyStorageStats(cmd);
        	return stats;
        } else {
        	return super.easySend(agentId, cmd);
        }
    }
    
    class DummyStorageStats extends Answer implements StorageStats {
    	public DummyStorageStats(Command command) {
    		super(command);
    	}
    	long l = ran.nextInt(100) * 1024L * 1024L;
    	public long getByteUsed() {
			return l;
		}
    	long c = l + 1024L * 1024L;
        public long getCapacityBytes() {
            return c;
        }
    	
    }
    
    public boolean updateFirewallRule(long id) {
    	return true;
    }
    
    public boolean associateIP(Long userId, String ipAddress, boolean add) {
    	return true;
    }
    
    @Override
    public HostStats getHostStatistics(long hostId) {
    	return (new HostStats() {
    		double d = ran.nextDouble();
			public double getCpuUtilization() {
				return d;
			}

			long l = ran.nextInt(100) * 1024L * 1024L;
			public long getFreeMemory() {
				return l;
			}
			
			public long getUsedMemory() {
				return 0;
			}
			
			public long getTotalMemory() {
				return 0;
			}
			
			public double getPublicNetworkReadKBs() {
				return 0;
			}
			
			public double getPublicNetworkWriteKBs() {
				return 0;
			}
    	});
    }
}
