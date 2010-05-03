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
package com.vmops.storage.listener;

import java.util.List;

import org.apache.log4j.Logger;

import com.vmops.agent.Listener;
import com.vmops.agent.api.AgentControlAnswer;
import com.vmops.agent.api.AgentControlCommand;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.api.StartupComputingCommand;
import com.vmops.agent.api.StartupStorageCommand;
import com.vmops.agent.api.StoragePoolInfo;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.Host.HypervisorType;
import com.vmops.host.dao.HostDao;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.Volume.StorageResourceType;
import com.vmops.storage.dao.StoragePoolDao;

public class StoragePoolMonitor implements Listener {
    private static final Logger s_logger = Logger.getLogger(StoragePoolMonitor.class);
	private final HostDao _hostDao;
	private final StorageManager _storageManager;
	private final StoragePoolDao _poolDao;
	
    public StoragePoolMonitor(StorageManager mgr, HostDao hostDao, StoragePoolDao poolDao) {
    	this._storageManager = mgr;
    	this._hostDao = hostDao;
    	this._poolDao = poolDao;
    }
    
    
    @Override
    public boolean isRecurring() {
        return false;
    }
    
    @Override
    public synchronized boolean processAnswer(long agentId, long seq, Answer[] resp) {
        return true;
    }
    
    @Override
    public synchronized boolean processDisconnect(long agentId, Status state) {
    
        return true;
    }
    
    @Override
    public boolean processConnect(long agentId, StartupCommand cmd) {
    	if (cmd instanceof StartupStorageCommand) {
    		HostVO host = _hostDao.findById(agentId);
    		if (!host.getType().toString().equalsIgnoreCase(Host.Type.SecondaryStorage.toString())) {
    			List<StoragePoolVO> pools = _poolDao.listByDataCenterPodId(host.getDataCenterId(),host.getPodId());
    			for (StoragePoolVO pool : pools) {
    				Long hostId = host.getId();
    				s_logger.debug("Host " + hostId + " connected, sending down storage pool information ...");
    				_storageManager.addPoolToHost(hostId, pool);
    				_storageManager.createCapacityEntry(pool);
    			}
    		}
        }         
        else if (cmd instanceof StartupComputingCommand) {
            StartupComputingCommand scCmd = (StartupComputingCommand)cmd;
            if (scCmd.getHypervisorType() == HypervisorType.XenServer ) {
                HostVO host = _hostDao.findById(agentId);
                List<StoragePoolVO> pools = _poolDao.listByDataCenterPodId(host.getDataCenterId(),host.getPodId());
                for (StoragePoolVO pool : pools) {
                    Long hostId = host.getId();
                    s_logger.debug("Host " + hostId + " connected, sending down storage pool information ...");
                    _storageManager.addPoolToHost(hostId, pool);
                    _storageManager.createCapacityEntry(pool);
                }

            }
        }

        return true;
    }
    


    @Override
    public boolean processCommand(long agentId, long seq, Command[] req) {
        return false;
    }
   
    @Override
    public AgentControlAnswer processControlCommand(long agentId, AgentControlCommand cmd) {
    	return null;
    }
    
    @Override
    public boolean processTimeout(long agentId, long seq) {
    	return true;
    }
    
    @Override
    public int getTimeout() {
    	return -1;
    }
}
