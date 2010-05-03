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
package com.vmops.host.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Local;
import javax.naming.ConfigurationException;
import javax.persistence.TableGenerator;

import com.vmops.dc.HostVlanMapVO;
import com.vmops.dc.dao.HostVlanMapDaoImpl;
import com.vmops.host.Host;
import com.vmops.host.HostVO;
import com.vmops.host.Status;
import com.vmops.host.Host.Type;
import com.vmops.host.Status.Event;
import com.vmops.info.RunningHostCountInfo;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.db.Attribute;
import com.vmops.utils.db.Filter;
import com.vmops.utils.db.GenericDaoBase;
import com.vmops.utils.db.SearchBuilder;
import com.vmops.utils.db.SearchCriteria;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.db.UpdateBuilder;

@Local(value = { HostDao.class })
@TableGenerator(name="host_req_sq", table="host", pkColumnName="id", valueColumnName="sequence", allocationSize=1)
public class HostDaoImpl extends GenericDaoBase<HostVO, Long> implements HostDao {
    protected final VmHostDaoImpl        _vmHostDao;

    protected final SearchBuilder<HostVO> TypePodDcStatusSearch;

    protected final SearchBuilder<HostVO> IdStatusSearch;
    protected final SearchBuilder<HostVO> TypeDcSearch;
    protected final SearchBuilder<HostVO> TypeDcStatusSearch;
    protected final SearchBuilder<HostVO> LastPingedSearch;
    protected final SearchBuilder<HostVO> LastPingedSearch2;
    protected final SearchBuilder<HostVO> MsStatusSearch;
    protected final SearchBuilder<HostVO> DcPrivateIpAddressSearch;
    protected final SearchBuilder<HostVO> DcStorageIpAddressSearch;

    protected final SearchBuilder<HostVO> GuidSearch;
    protected final SearchBuilder<HostVO> DcSearch;
    protected final SearchBuilder<HostVO> PodSearch;
    protected final SearchBuilder<HostVO> TypeSearch;
    protected final SearchBuilder<HostVO> StatusSearch;
    protected final SearchBuilder<HostVO> NameLikeSearch;
    protected final SearchBuilder<HostVO> SequenceSearch;
    protected final SearchBuilder<HostVO> DirectlyConnectedSearch;
    
    protected final Attribute _statusAttr;
    protected final Attribute _msIdAttr;
    protected final Attribute _pingTimeAttr;
    protected final Attribute _sequenceAttr;
    
    protected static final HostVlanMapDaoImpl _hostVlanMapDao = ComponentLocator.inject(HostVlanMapDaoImpl.class);
    protected final DetailsDaoImpl _detailsDao = ComponentLocator.inject(DetailsDaoImpl.class);

    public HostDaoImpl() {
        _vmHostDao = ComponentLocator.inject(VmHostDaoImpl.class);
        
        TypePodDcStatusSearch = createSearchBuilder();
        HostVO entity = TypePodDcStatusSearch.entity();
        TypePodDcStatusSearch.addAnd("type", entity.getType(), SearchCriteria.Op.EQ);
        TypePodDcStatusSearch.addAnd("pod", entity.getPodId(), SearchCriteria.Op.EQ);
        TypePodDcStatusSearch.addAnd("dc", entity.getDataCenterId(), SearchCriteria.Op.EQ);
        TypePodDcStatusSearch.addAnd("status", entity.getStatus(), SearchCriteria.Op.EQ);
        TypePodDcStatusSearch.done();

        LastPingedSearch = createSearchBuilder();
        LastPingedSearch.addAnd("ping", LastPingedSearch.entity().getLastPinged(), SearchCriteria.Op.LT);
        LastPingedSearch.addAnd("state", LastPingedSearch.entity().getStatus(), SearchCriteria.Op.IN);
        LastPingedSearch.done();
        
        LastPingedSearch2 = createSearchBuilder();
        LastPingedSearch2.addAnd("ping", LastPingedSearch2.entity().getLastPinged(), SearchCriteria.Op.LT);
        LastPingedSearch2.addAnd("type", LastPingedSearch2.entity().getType(), SearchCriteria.Op.EQ);
        LastPingedSearch2.done();
        
        MsStatusSearch = createSearchBuilder();
        MsStatusSearch.addAnd("ms", MsStatusSearch.entity().getManagementServerId(), SearchCriteria.Op.EQ);
        MsStatusSearch.addAnd("statuses", MsStatusSearch.entity().getStatus(), SearchCriteria.Op.IN);
        MsStatusSearch.done();
        
        TypeDcSearch = createSearchBuilder();
        TypeDcSearch.addAnd("type", TypeDcSearch.entity().getType(), SearchCriteria.Op.EQ);
        TypeDcSearch.addAnd("dc", TypeDcSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        TypeDcSearch.done();
        
        TypeDcStatusSearch = createSearchBuilder();
        TypeDcStatusSearch.addAnd("type", TypeDcStatusSearch.entity().getType(), SearchCriteria.Op.EQ);
        TypeDcStatusSearch.addAnd("dc", TypeDcStatusSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        TypeDcStatusSearch.addAnd("status", TypeDcStatusSearch.entity().getStatus(), SearchCriteria.Op.EQ);
        TypeDcStatusSearch.done();
        
        IdStatusSearch = createSearchBuilder();
        IdStatusSearch.addAnd("id", IdStatusSearch.entity().getId(), SearchCriteria.Op.EQ);
        IdStatusSearch.addAnd("states", IdStatusSearch.entity().getStatus(), SearchCriteria.Op.IN);
        IdStatusSearch.done();
        
        DcPrivateIpAddressSearch = createSearchBuilder();
        DcPrivateIpAddressSearch.addAnd("privateIpAddress", DcPrivateIpAddressSearch.entity().getPrivateIpAddress(), SearchCriteria.Op.EQ);
        DcPrivateIpAddressSearch.addAnd("dc", DcPrivateIpAddressSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DcPrivateIpAddressSearch.done();
        
        DcStorageIpAddressSearch = createSearchBuilder();
        DcStorageIpAddressSearch.addAnd("storageIpAddress", DcStorageIpAddressSearch.entity().getStorageIpAddress(), SearchCriteria.Op.EQ);
        DcStorageIpAddressSearch.addAnd("dc", DcStorageIpAddressSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DcStorageIpAddressSearch.done();

        GuidSearch = createSearchBuilder();
        GuidSearch.addAnd("guid", GuidSearch.entity().getGuid(), SearchCriteria.Op.EQ);
        GuidSearch.done();
        
        DcSearch = createSearchBuilder();
        DcSearch.addAnd("dc", DcSearch.entity().getDataCenterId(), SearchCriteria.Op.EQ);
        DcSearch.done();
        
        PodSearch = createSearchBuilder();
        PodSearch.addAnd("pod", PodSearch.entity().getPodId(), SearchCriteria.Op.EQ);
        PodSearch.done();
        
        TypeSearch = createSearchBuilder();
        TypeSearch.addAnd("type", TypeSearch.entity().getType(), SearchCriteria.Op.EQ);
        TypeSearch.done();
        
        StatusSearch =createSearchBuilder();
        StatusSearch.addAnd("status", StatusSearch.entity().getStatus(), SearchCriteria.Op.IN);
        StatusSearch.done();
        
        NameLikeSearch = createSearchBuilder();
        NameLikeSearch.addAnd("name", NameLikeSearch.entity().getName(), SearchCriteria.Op.LIKE);
        NameLikeSearch.done();
        
        SequenceSearch = createSearchBuilder();
        SequenceSearch.addAnd("id", SequenceSearch.entity().getId(), SearchCriteria.Op.EQ);
//        SequenceSearch.addRetrieve("sequence", SequenceSearch.entity().getSequence());
        SequenceSearch.done();
        
        DirectlyConnectedSearch = createSearchBuilder();
        DirectlyConnectedSearch.addAnd("resource", DirectlyConnectedSearch.entity().getResource(), SearchCriteria.Op.NNULL);
        DirectlyConnectedSearch.done();
        
        _statusAttr = _allAttributes.get("status");
        _msIdAttr = _allAttributes.get("managementServerId");
        _pingTimeAttr = _allAttributes.get("lastPinged");
        _sequenceAttr = _allAttributes.get("sequence");
        
        assert (_statusAttr != null && _msIdAttr != null && _pingTimeAttr != null && _sequenceAttr != null) : "Couldn't find one of these attributes";
    }
    
    @Override
    public HostVO findSecondaryStorageHost(long dcId) {
    	SearchCriteria sc = TypeDcSearch.create();
    	sc.setParameters("type", Host.Type.SecondaryStorage);
    	sc.setParameters("dc", dcId);
    	List<HostVO> storageHosts = listBy(sc);
    	
    	if (storageHosts == null || storageHosts.size() != 1) {
    		return null;
    	} else {
    		return storageHosts.get(0);
    	}
    }
    
    @Override
    public List<HostVO> listSecondaryStorageHosts() {
    	SearchCriteria sc = TypeSearch.create();
    	sc.setParameters("type", Host.Type.SecondaryStorage);
    	List<HostVO> secondaryStorageHosts = listBy(sc);
    	
    	return secondaryStorageHosts;
    }
    
    @Override
    public List<HostVO> findDirectlyConnectedHosts() {
        SearchCriteria sc = DirectlyConnectedSearch.create();
        return search(sc, null);
    }
    
    @Override
    public List<HostVO> findDirectAgentToLoad(Long msid, boolean msidInclusive, Long lastPingSecondsAfter, Long limit) {
        SearchBuilder<HostVO> sb = createSearchBuilder();
		SearchBuilder<HostVO> sb2 = createSearchBuilder();
        sb.addAnd("resource", sb.entity().getResource(), SearchCriteria.Op.NNULL);
        if(msid != null) {
        	if(msidInclusive) {
        		sb.addAnd("managementServerId", sb.entity().getManagementServerId(), SearchCriteria.Op.EQ);
        	} else {
        		sb2.addAnd("managementServerId", sb2.entity().getManagementServerId(), SearchCriteria.Op.NEQ);
        		sb2.addOr("server", sb2.entity().getManagementServerId(), SearchCriteria.Op.NULL);
        		
        		sb.addAnd("abc", sb.entity().getManagementServerId(), SearchCriteria.Op.SC);
        	}
        }
        if(lastPingSecondsAfter != null)
        	sb.addAnd("lastPinged", sb.entity().getLastPinged(), SearchCriteria.Op.LTEQ);
        sb.done();
        
    	SearchCriteria sc = sb.create();
    	if(msid != null) {
        	if(msidInclusive) {
        		sc.setParameters("managementServerId", msid);
        	} else {
		    	SearchCriteria sc2 = sb2.create();
		    	
        		sc2.setParameters("managementServerId", msid);
		    	sc.setParameters("abc", sc2);
        	}
    	}
    	
    	if(lastPingSecondsAfter != null)
    		sc.setParameters("lastPinged", lastPingSecondsAfter);
    	
        return search(sc, new Filter(HostVO.class, "id", true, 0L, limit));
    }
    
    @Override
    public void markHostsAsDisconnected(long msId, Status... states) {
        SearchCriteria sc = MsStatusSearch.create();
        sc.setParameters("ms", msId);
        sc.setParameters("statuses", (Object[])Status.toStrings(states));
        
        HostVO host = createForUpdate();
        host.setManagementServerId(null);
        host.setDisconnectedOn(new Date());
        
        UpdateBuilder ub = getUpdateBuilder(host);
        ub.set(host, "status", Status.Disconnected);
        
        update(ub, sc, null);
    }

    @Override
    public List<HostVO> listBy(Host.Type type, long podId, long dcId) {
        SearchCriteria sc = TypePodDcStatusSearch.create();
        sc.setParameters("type", type.toString());
        sc.setParameters("pod", podId);
        sc.setParameters("dc", dcId);
        sc.setParameters("status", Status.Up.toString());

        return listActiveBy(sc);
    }
    
    @Override
    public List<HostVO> listBy(Host.Type type, long dcId) {
        SearchCriteria sc = TypeDcStatusSearch.create();
        sc.setParameters("type", type.toString());
        sc.setParameters("dc", dcId);
        sc.setParameters("status", Status.Up.toString());

        return listActiveBy(sc);
    }
    
    @Override
    public HostVO findByPrivateIpAddressInDataCenter(long dcId, String privateIpAddress) {
        SearchCriteria sc = DcPrivateIpAddressSearch.create();
        sc.setParameters("dc", dcId);
        sc.setParameters("privateIpAddress", privateIpAddress);
        
        return findOneActiveBy(sc);
    }
    
    @Override
    public HostVO findByStorageIpAddressInDataCenter(long dcId, String privateIpAddress) {
        SearchCriteria sc = DcStorageIpAddressSearch.create();
        sc.setParameters("dc", dcId);
        sc.setParameters("storageIpAddress", privateIpAddress);
        
        return findOneActiveBy(sc);
    }
    
    @Override
    public void loadDetails(HostVO host) {
        Map<String, String> details =_detailsDao.findDetails(host.getId());
        host.setDetails(details);
    }
    
    @Override
    public boolean updateStatus(HostVO host, Event event, long msId) {
        Status oldStatus = host.getStatus();
        long oldPingTime = host.getLastPinged();
        Status newStatus = oldStatus.getNextStatus(event);
        
        if (newStatus == null) {
            return false;
        }
        
        SearchCriteria sc = null;
        
        sc = IdStatusSearch.create();
        
        sc.setParameters("states", oldStatus);
        sc.setParameters("id", host.getId());
        if (!event.isUserRequest() && newStatus.checkManagementServer()) {
        	sc.addAnd(_msIdAttr, SearchCriteria.Op.EQ, msId);
        	sc.addAnd(_pingTimeAttr, SearchCriteria.Op.EQ, oldPingTime);
        }
        
        UpdateBuilder ub = getUpdateBuilder(host);
        ub.set(host, _statusAttr, newStatus);
        if (!event.isUserRequest() && newStatus.updateManagementServer()) {
	        ub.set(host, _msIdAttr, msId);
	        ub.set(host, _pingTimeAttr, System.currentTimeMillis() >> 10);
        }
        
        int result = update(ub, sc, null);
        assert result <= 1 : "How can this update " + result + " rows? ";
        
        if (s_logger.isDebugEnabled() && result == 0) {
        	HostVO vo = findById(host.getId());
        	assert vo != null : "How how how? : " + host.getId();
	        	
        	StringBuilder str = new StringBuilder("Unable to update host for event:").append(event.toString());
        	str.append(". New=[status=").append(newStatus.toString()).append(":msid=").append(msId).append(":lastpinged=").append(host.getLastPinged()).append("]");
        	str.append("; Old=[status=").append(oldStatus.toString()).append(":msid=").append(msId).append(":lastpinged=").append(oldPingTime).append("]");
        	str.append("; DB=[status=").append(vo.getStatus().toString()).append(":msid=").append(vo.getManagementServerId()).append(":lastpinged=").append(vo.getLastPinged()).append("]");
        	s_logger.debug(str.toString());
        }
        return result > 0;
    }
    
    @Override
    public boolean disconnect(HostVO host, Event event, long msId) {
        host.setDisconnectedOn(new Date());
        
        return updateStatus(host, event, msId);
    }

    @Override
    public boolean connect(HostVO host, long msId) {
        Transaction txn = Transaction.currentTxn();
        long id = host.getId();
        txn.start();
        
        if (!updateStatus(host, Event.AgentConnected, msId)) {
            return false;
        }
        
        txn.commit();
        return true;
    }

    @Override
    public HostVO findByGuid(String guid) {
        SearchCriteria sc = GuidSearch.create("guid", guid);
        return findOneBy(sc);
    }

    @Override
    public List<HostVO> findLostHosts(long timeout) {
        SearchCriteria sc = LastPingedSearch.create();
        sc.setParameters("ping", timeout);
        sc.setParameters("state", Status.Up.toString(), Status.Updating.toString(), Status.Disconnected.toString());
        return listActiveBy(sc);
    }
    
    public List<HostVO> findHostsLike(String hostName) {
    	SearchCriteria sc = NameLikeSearch.create();
        sc.setParameters("name", "%" + hostName + "%");
        return listActiveBy(sc);
    }

    @Override
    public List<HostVO> findLostHosts2(long timeout, Type type) {
        SearchCriteria sc = LastPingedSearch2.create();
        sc.setParameters("ping", timeout);
        sc.setParameters("type", type.toString());
        return listActiveBy(sc);
    }

    @Override
    public List<HostVO> listByDataCenter(long dcId) {
        SearchCriteria sc = DcSearch.create("dc", dcId);
        return listActiveBy(sc);
    }

    public List<HostVO> listByHostPod(long podId) {
        SearchCriteria sc = PodSearch.create("pod", podId);
        return listActiveBy(sc);
    }
    
    @Override
    public List<HostVO> listByStatus(Status... status) {
    	SearchCriteria sc = StatusSearch.create();
    	sc.setParameters("status", (Object[])status);
        return listActiveBy(sc);
    }

    @Override
    public List<HostVO> listByTypeDataCenter(Type type, long dcId) {
        SearchCriteria sc = TypeDcSearch.create();
        sc.setParameters("type", type.toString());
        sc.setParameters("dc", dcId);

        return listActiveBy(sc);
    }

    @Override
    public List<HostVO> listByType(Type type, boolean routingCapable) {
        SearchCriteria sc = TypeSearch.create();
        sc.setParameters("type", type.toString());

        if (routingCapable) {
            sc.addAnd("routing_capbable", SearchCriteria.Op.EQ, Integer.valueOf(1));
        }

        return listBy(sc);
    }

    protected void saveDetails(HostVO host) {
        Map<String, String> details = host.getDetails();
        if (details == null) {
            return;
        }
        _detailsDao.persist(host.getId(), details);
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        if (!super.configure(name, params)) {
            return false;
        }

        Transaction txn = Transaction.currentTxn();
		try {
			txn.start();
			PreparedStatement alter = txn.prepareAutoCloseStatement("ALTER TABLE host ADD COLUMN sequence bigint unsigned NOT NULL DEFAULT 1");
			try {
				int result = alter.executeUpdate();
				s_logger.debug("Result for alter host table is " + result);
				txn.commit();
			} catch (SQLException e) {
				if (!e.getMessage().contains("Duplicate column name")) {
					s_logger.debug("Got this while updating", e);
					throw new ConfigurationException("Unable to update the host table ");
				}
				s_logger.info("host table is already up to date");
				txn.commit();
			}
		} catch (SQLException e) {
			s_logger.error("Unable to upgrade the db due to " + e);
			throw new ConfigurationException("Unable to upgrade the db due to " + e);
		}

        if (!_vmHostDao.configure("VM Host Operations Table", new HashMap<String, Object>())) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public Long persist(HostVO host) {
        Transaction txn = Transaction.currentTxn();
        txn.start();
        
        Long id = super.persist(host);
        saveDetails(host);
        
        txn.commit();
     
        return id;
    }
    
    @Override
    public boolean update(Long hostId, HostVO host) {
        Transaction txn = Transaction.currentTxn();
        txn.start();
        
        boolean persisted = super.update(hostId, host);
        if (!persisted) {
            return persisted;
        }
        
        saveDetails(host);
        
        txn.commit();
     
        return persisted;
    }

    @Override
    public List<RunningHostCountInfo> getRunningHostCounts(Date cutTime) {
    	String sql = "select * from (select h.data_center_id, h.type, count(*) as count from host as h INNER JOIN mshost as m ON h.mgmt_server_id=m.msid " +
    		  "where h.status='Up' and h.type='Computing' and m.last_update > ? " +
    		  "group by h.data_center_id, h.type " +
    		  "UNION ALL " +
    		  "select h.data_center_id, h.type, count(*) as count from host as h INNER JOIN mshost as m ON h.mgmt_server_id=m.msid " +
    		  "where h.status='Up' and h.type='SecondaryStorage' and m.last_update > ? " +
    		  "group by h.data_center_id, h.type " +
    		  "UNION ALL " +
			  "select h.data_center_id, h.type, count(*) as count from host as h INNER JOIN mshost as m ON h.mgmt_server_id=m.msid " +
			  "where h.status='Up' and h.type='Routing' and m.last_update > ? " +
			  "group by h.data_center_id, h.type) as t " +
			  "ORDER by t.data_center_id, t.type";

    	ArrayList<RunningHostCountInfo> l = new ArrayList<RunningHostCountInfo>();
    	
        Transaction txn = Transaction.currentTxn();;
        PreparedStatement pstmt = null;
        try {
            pstmt = txn.prepareAutoCloseStatement(sql);
            java.sql.Timestamp ts = new java.sql.Timestamp(cutTime.getTime());
            pstmt.setTimestamp(1, ts);
            pstmt.setTimestamp(2, ts);
            pstmt.setTimestamp(3, ts);
            
            ResultSet rs = pstmt.executeQuery();
            while(rs.next()) {
            	RunningHostCountInfo info = new RunningHostCountInfo();
            	info.setDcId(rs.getLong(1));
            	info.setHostType(rs.getString(2));
            	info.setCount(rs.getInt(3));
            	
            	l.add(info);
            }
        } catch (SQLException e) {
        } catch (Throwable e) {
        }
        return l;
    }

    @Override
    public long getNextSequence(long hostId) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace("getNextSequence(), hostId: " + hostId);
        }
        
        TableGenerator tg = _tgs.get("host_req_sq");
        assert tg != null : "how can this be wrong!";
        
        return s_seqFetcher.getNextSequence(Long.class, tg, hostId);
    }
    
    @Override
    public List<String> getVlanIds(long hostId) {
    	List<String> vlanIds = new ArrayList<String>();
    	List<HostVlanMapVO> hostVlanMaps = _hostVlanMapDao.getHostVlanMaps(hostId);
    	
    	for (HostVlanMapVO map : hostVlanMaps) {
    		vlanIds.add(map.getVlanId());
    	}
    	
    	return vlanIds;
    }
    
    @Override
    public void addVlan(long hostId, String vlanId) {
    	HostVlanMapVO map = _hostVlanMapDao.findHostVlanMap(hostId, vlanId);
    	if (map == null) {
    		HostVlanMapVO newMap = new HostVlanMapVO(hostId, vlanId);
    		_hostVlanMapDao.persist(newMap);
    	}
    }
    
    @Override
    public void removeVlan(long hostId, String vlanId) {
    	HostVlanMapVO map = _hostVlanMapDao.findHostVlanMap(hostId, vlanId);
    	Long mapId = map.getId();
    	_hostVlanMapDao.delete(mapId);
    }
}



