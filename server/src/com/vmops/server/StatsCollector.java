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

package com.vmops.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.vmops.agent.AgentManager;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.GetFileStatsCommand;
import com.vmops.agent.api.GetStorageStatsCommand;
import com.vmops.agent.api.VmStatsEntry;
import com.vmops.capacity.CapacityVO;
import com.vmops.capacity.dao.CapacityDao;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.exception.InternalErrorException;
import com.vmops.host.Host;
import com.vmops.host.HostStats;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.dao.HostDao;
import com.vmops.storage.StorageManager;
import com.vmops.storage.StoragePoolHostVO;
import com.vmops.storage.StoragePoolVO;
import com.vmops.storage.StorageStats;
import com.vmops.storage.VolumeStats;
import com.vmops.storage.VolumeVO;
import com.vmops.storage.dao.StoragePoolDao;
import com.vmops.storage.dao.VolumeDao;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.concurrency.NamedThreadFactory;
import com.vmops.utils.db.GlobalLock;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.vm.UserVmManager;
import com.vmops.vm.UserVmVO;
import com.vmops.vm.VmStats;
import com.vmops.vm.dao.UserVmDao;

/**
 * Provides real time stats for various agent resources up to x seconds
 * 
 * @author Will Chan
 *
 */
public class StatsCollector {
	public static final Logger s_logger = Logger.getLogger(StatsCollector.class.getName());

	private static StatsCollector s_instance = null;

	private ScheduledExecutorService _executor = null;
	private final AgentManager _agentMgr;
	private final UserVmManager _userVmMgr;
	private final HostDao _hostDao;
	private final UserVmDao _userVmDao;
	private final VolumeDao _volsDao;
	private final CapacityDao _capacityDao;
	private final StoragePoolDao _storagePoolDao;
	private final StorageManager _storageManager;

	private ConcurrentHashMap<Long, HostStats> _hostStats = new ConcurrentHashMap<Long, HostStats>();
	private ConcurrentHashMap<Long, VmStats> _vmStats = new ConcurrentHashMap<Long, VmStats>();
	private ConcurrentHashMap<Long, VolumeStats> _volumeStats = new ConcurrentHashMap<Long, VolumeStats>();
	private ConcurrentHashMap<Long, StorageStats> _storageStats = new ConcurrentHashMap<Long, StorageStats>();
	private ConcurrentHashMap<Long, StorageStats> _storagePoolStats = new ConcurrentHashMap<Long, StorageStats>();
	
	long hostStatsInterval = -1L;
	long vmStatsInterval = -1L;
	long storageStatsInterval = -1L;
	long volumeStatsInterval = -1L;

	private final GlobalLock m_capacityCheckLock = GlobalLock.getInternLock("capacity.check");

    public static StatsCollector getInstance() {
        return s_instance;
    }
	public static StatsCollector getInstance(Map<String, String> configs) {
	    if (s_instance == null) {
	        s_instance = new StatsCollector(configs);
	    }
        return s_instance;
    }

	private StatsCollector(Map<String, String> configs) {
		ComponentLocator locator = ComponentLocator.getLocator(ManagementServer.Name);
		_agentMgr = locator.getManager(AgentManager.class);
		_userVmMgr = locator.getManager(UserVmManager.class);
		_hostDao = locator.getDao(HostDao.class);
		_userVmDao = locator.getDao(UserVmDao.class);
		_volsDao = locator.getDao(VolumeDao.class);
		_capacityDao = locator.getDao(CapacityDao.class);
		_storagePoolDao = locator.getDao(StoragePoolDao.class);
		_storageManager = locator.getManager(StorageManager.class);

		_executor = Executors.newScheduledThreadPool(3, new NamedThreadFactory("StatsCollector"));

		 hostStatsInterval = NumbersUtil.parseLong(configs.get("host.stats.interval"), 60000L);
		 vmStatsInterval = NumbersUtil.parseLong(configs.get("vm.stats.interval"), 60000L);
		 storageStatsInterval = NumbersUtil.parseLong(configs.get("storage.stats.interval"), 60000L);
		 volumeStatsInterval = NumbersUtil.parseLong(configs.get("volume.stats.interval"), -1L);

		_executor.scheduleWithFixedDelay(new HostCollector(), 15000L, hostStatsInterval, TimeUnit.MILLISECONDS);
		// _executor.scheduleWithFixedDelay(new VmCollector(), 15000L, vmStatsInterval, TimeUnit.MILLISECONDS);
		_executor.scheduleWithFixedDelay(new StorageCollector(), 15000L, storageStatsInterval, TimeUnit.MILLISECONDS);
		
		// -1 means we don't even start this thread to pick up any data.
		if (volumeStatsInterval > 0) {
			_executor.scheduleWithFixedDelay(new VolumeCollector(), 15000L, volumeStatsInterval, TimeUnit.MILLISECONDS);
		} else {
			s_logger.info("Disabling volume stats collector");
		}
	}

	class HostCollector implements Runnable {
		public void run() {
			try {
				s_logger.debug("HostStatsCollector is running...");
				
				SearchCriteria sc = _hostDao.createSearchCriteria();
				sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.Storage.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ConsoleProxy.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorage.toString());
				ConcurrentHashMap<Long, HostStats> hostStats = new ConcurrentHashMap<Long, HostStats>();
				List<HostVO> hosts = _hostDao.search(sc, null);
				for (HostVO host : hosts) {
					if (host.getId() != null) {
					    HostStats stats = _agentMgr.getHostStatistics(host.getId());
					    if (stats != null) {
	                        hostStats.put(host.getId(), stats);
					    } else {
					        s_logger.warn("Received invalid host stats for host: " + host.getId());
					    }
					} else {
						s_logger.warn("Host: " + host.getId() + " does not exist, skipping host statistics");
					}
				}
				_hostStats = hostStats;
			} catch (Throwable t) {
				s_logger.error("Error trying to retrieve host stats", t);
			}
		}
	}
	
	class VmCollector implements Runnable {
		public void run() {
			try {	
				s_logger.debug("VmStatsCollector is running...");
				
				SearchCriteria sc = _hostDao.createSearchCriteria();
				sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.Storage.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.ConsoleProxy.toString());
				sc.addAnd("type", SearchCriteria.Op.NEQ, Host.Type.SecondaryStorage.toString());
				List<HostVO> hosts = _hostDao.search(sc, null);
				
				for (HostVO host : hosts) {
					List<UserVmVO> vms = _userVmDao.listRunningByHostId(host.getId());
					List<Long> vmIds = new ArrayList<Long>();
					
					for (UserVmVO vm : vms) {
						vmIds.add(vm.getId());
					}
					
					try {
						HashMap<Long, VmStatsEntry> vmStatsById = _userVmMgr.getVirtualMachineStatistics(host.getId(), vmIds);
						_vmStats.putAll(vmStatsById);
					} catch (InternalErrorException e) {
						s_logger.debug("Failed to get VM stats for host with ID: " + host.getId());
						continue;
					}
				}
				
			} catch (Throwable t) {
				s_logger.error("Error trying to retrieve VM stats", t);
			}
		}
	}

	public HostStats getHostStats(long id) {
		return _hostStats.get(id);
	}

	public VmStats getVMStats(long id){
		return _vmStats.get(id);
	}
	
	class StorageCollector implements Runnable {
		public void run() {
			try {
				SearchCriteria sc = _hostDao.createSearchCriteria();
				sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
				sc.addAnd("type", SearchCriteria.Op.EQ, Host.Type.Storage.toString());
				
				ConcurrentHashMap<Long, StorageStats> storageStats = new ConcurrentHashMap<Long, StorageStats>();
				List<HostVO> hosts = _hostDao.search(sc, null);
				for (HostVO host : hosts) {
					GetStorageStatsCommand command = new GetStorageStatsCommand(host.getGuid());
		            Answer answer = _agentMgr.easySend(host.getId(), command);
		            if (answer != null && answer.getResult()) {
		            	storageStats.put(host.getId(), (StorageStats)answer);
		            }
				}
				
                sc = _hostDao.createSearchCriteria();
                sc.addAnd("status", SearchCriteria.Op.EQ, Status.Up.toString());
                sc.addAnd("type", SearchCriteria.Op.EQ, Host.Type.SecondaryStorage.toString());
                
                hosts = _hostDao.search(sc, null);
                for (HostVO host : hosts) {
                    GetStorageStatsCommand command = new GetStorageStatsCommand(host.getGuid());
                    Answer answer = _agentMgr.easySend(host.getId(), command);
                    if (answer != null && answer.getResult()) {
                        storageStats.put(host.getId(), (StorageStats)answer);
                    }
                }
				_storageStats = storageStats;
				
				ConcurrentHashMap<Long, StorageStats> storagePoolStats = new ConcurrentHashMap<Long, StorageStats>();

				List<StoragePoolVO> storagePools = _storagePoolDao.listAllActive();
				for (StoragePoolVO pool: storagePools) {
					StoragePoolHostVO sphvo = _storageManager.chooseHostForStoragePool(pool, new ArrayList<Long>());
					if (sphvo != null) {
						GetStorageStatsCommand command = new GetStorageStatsCommand(pool.getUuid(), sphvo.getLocalPath()); 
						Answer answer = _agentMgr.easySend(sphvo.getHostId(), command);
						if (answer != null && answer.getResult()) {
							storagePoolStats.put(pool.getId(), (StorageStats)answer);
						}
					}

				}
				_storagePoolStats = storagePoolStats;

		        if (m_capacityCheckLock.lock(5)) { // 5 second timeout
		            if (s_logger.isTraceEnabled()) {
		                s_logger.trace("recalculating system storage capacity");
		            }
		            try {
		                // now update the capacity table with the new stats
		                // FIXME: the right way to do this is to register a listener (see RouterStatsListener)
		                //        for the host stats, send the Watch<something>Command at a regular interval
		                //        to collect the stats from an agent and update the database as needed.  The
		                //        listener model has connects/disconnects to keep things in sync much better
		                //        than this model right now
		                _capacityDao.clearStorageCapacities();

		                // create new entries
		                for (Long hostId : _storageStats.keySet()) {
		                    StorageStats stats = _storageStats.get(hostId);
		                    HostVO host = _hostDao.findById(hostId);

		                    if (Host.Type.SecondaryStorage.equals(host.getType())) {
	                            CapacityVO capacity = new CapacityVO(host.getId(), host.getDataCenterId(), host.getPodId(), stats.getByteUsed(), host.getTotalSize(), CapacityVO.CAPACITY_TYPE_SECONDARY_STORAGE);
	                            _capacityDao.persist(capacity);
		                    } else {
	                            CapacityVO capacity = new CapacityVO(host.getId(), host.getDataCenterId(), host.getPodId(), stats.getByteUsed(), host.getTotalSize(), CapacityVO.CAPACITY_TYPE_STORAGE);
	                            _capacityDao.persist(capacity);
		                    }
		                }

		                for (Long poolId : _storagePoolStats.keySet()) {
		                    StorageStats stats = _storagePoolStats.get(poolId);
		                    StoragePoolVO pool = _storagePoolDao.findById(poolId);

		                    CapacityVO capacity = new CapacityVO(poolId, pool.getDataCenterId(), pool.getPodId(), stats.getByteUsed(), stats.getCapacityBytes(), CapacityVO.CAPACITY_TYPE_STORAGE);
		                    _capacityDao.persist(capacity);
		                }
		            } finally {
		                m_capacityCheckLock.unlock();
		            }
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("done recalculating system storage capacity");
                    }
		        } else {
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace("not recalculating system storage capacity, unable to lock capacity table");
                    }
		        }
			} catch (Throwable t) {
				s_logger.error("Error trying to retrieve storage stats", t);
			}
		}
	}

	public StorageStats getStorageStats(long id) {
		return _storageStats.get(id);
	}
	
	public StorageStats getStoragePoolStats(long id) {
		return _storagePoolStats.get(id);
	}

	class VolumeCollector implements Runnable {
		public void run() {
			try {
				List<VolumeVO> volumes = _volsDao.listAllActive();
				Map<Long, List<VolumeCommand>> commandsByHost = new HashMap<Long, List<VolumeCommand>>();
				
				for (VolumeVO volume : volumes) {
					List<VolumeCommand> commands = commandsByHost.get(volume.getHostId());
					if (commands == null) {
						commands = new ArrayList<VolumeCommand>();
						commandsByHost.put(volume.getHostId(), commands);
					}
					VolumeCommand vCommand = new VolumeCommand();
					vCommand.volumeId = volume.getId();
					vCommand.command = new GetFileStatsCommand(volume);
					commands.add(vCommand);
				}
				ConcurrentHashMap<Long, VolumeStats> volumeStats = new ConcurrentHashMap<Long, VolumeStats>();
				for (Iterator<Long> iter = commandsByHost.keySet().iterator(); iter.hasNext();) {
					Long hostId = iter.next();
					List<VolumeCommand> commandsList = commandsByHost.get(hostId);
					
					long[] volumeIdArray = new long[commandsList.size()];
					Command[] commandsArray = new Command[commandsList.size()];
					for (int i = 0; i < commandsList.size(); i++) {
						VolumeCommand vCommand = commandsList.get(i);
						volumeIdArray[i] = vCommand.volumeId;
						commandsArray[i] = vCommand.command;
					}
					Answer[] answers = _agentMgr.send(hostId, commandsArray, false);
					if (answers != null) {
					    long totalBytes = 0L;
						for (int i = 0; i < answers.length; i++) {
							if (answers[i].getResult()) {
							    VolumeStats vStats = (VolumeStats)answers[i];
								volumeStats.put(volumeIdArray[i], vStats);
								totalBytes += vStats.getBytesUsed();
							}
						}
					}
				}

				// We replace the existing volumeStats so that it does not grow with no bounds
				_volumeStats = volumeStats;
			} catch (AgentUnavailableException e) {
			    s_logger.debug(e.getMessage());
			} catch (Throwable t) {
				s_logger.error("Error trying to retrieve volume stats", t);
			}
		}
	}

	private class VolumeCommand {
		public long volumeId;
		public GetFileStatsCommand command;
	}
	
	public VolumeStats[] getVolumeStats(long[] ids) {
		VolumeStats[] stats = new VolumeStats[ids.length];
		if (volumeStatsInterval > 0) {
			for (int i = 0; i < ids.length; i++) {
				stats[i] = _volumeStats.get(ids[i]);
			}
		}
		return stats;
	}
}
