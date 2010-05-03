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
package com.vmops.resource.xen;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ejb.Local;
import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import com.vmops.alert.AlertManager;
import com.vmops.configuration.Config;
import com.vmops.configuration.dao.ConfigurationDao;
import com.vmops.host.HostInfo;
import com.vmops.host.HostVO;
import com.vmops.host.Host.HypervisorType;
import com.vmops.host.dao.HostDao;
import com.vmops.maint.Version;
import com.vmops.resource.Discoverer;
import com.vmops.resource.ServerResource;
import com.vmops.utils.NumbersUtil;
import com.vmops.utils.Pair;
import com.vmops.utils.Ternary;
import com.vmops.utils.component.ComponentLocator;
import com.vmops.utils.component.Inject;
import com.vmops.utils.net.UrlUtil;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.HostPatch;
import com.xensource.xenapi.Pool;
import com.xensource.xenapi.PoolPatch;
import com.xensource.xenapi.Types.SessionAuthenticationFailed;
import com.xensource.xenapi.Types.XenAPIException;

@Local(value=Discoverer.class)
public class XenServerDiscoverer implements Discoverer {
    private static final Logger s_logger = Logger.getLogger(XenServerDiscoverer.class);
    private String _name;
    private String _minProductVersion;
    private String _minXapiVersion;
    private String _minXenVersion;
    private String _maxProductVersion;
    private String _maxXapiVersion;
    private String _maxXenVersion;
    private AlertManager _alertMgr;
    private List<Pair<String, String>> _requiredPatches;
    private String _cidr;
    private String _publicNic;
    private String _privateNic;
    private int _wait;
    private boolean _formPoolsInPod;
    private int _maxPoolsInPod;
    private String _creationStrategy;
    private XenServerConnectionPool _connPool;
    private boolean _forceJoinPool;
    
    
    @Inject
    private HostDao _hostDao;
    
    protected XenServerDiscoverer() {
    }
    
    @Override
    public Map<? extends ServerResource, Map<String, String>> find(long dcId, Long podId, String urlString, String username, String password) {
        Map<XenServerResource, Map<String, String>> resources = new HashMap<XenServerResource, Map<String, String>>();
        Connection conn = null;
        if (!urlString.startsWith("http://")) {
            s_logger.debug("urlString is not http so we're not taking care of the discovery for this: " + urlString);
            return null;
        }
        try {
            URL url = new URL(urlString);
            
            String hostname = url.getHost();
            InetAddress ia = InetAddress.getByName(hostname);
            String addr = ia.getHostAddress();
            
            conn = _connPool.connect(addr, username, password, _wait);
            if (conn == null) {
                s_logger.debug("Unable to get a connection to " + urlString);
                return null;
            }
            
            String pod;
            HostVO first = null;
            if (podId == null) {
                Map<Pool, Pool.Record> pools = Pool.getAllRecords(conn);
                assert pools.size() == 1 : "Pools are not one....where on earth have i been? " + pools.size();
                
                pod = pools.values().iterator().next().uuid;
            } else {
                pod = Long.toString(podId);
            }
            Map<Host, Host.Record> hosts = Host.getAllRecords(conn);
            for (Map.Entry<Host, Host.Record> entry : hosts.entrySet()) {
                Host.Record record = entry.getValue();
                Host host = entry.getKey();
                
                String prodVersion = record.softwareVersion.get("product_version");
                String xenVersion = record.softwareVersion.get("xen");
                String hostOS = record.softwareVersion.get("product_brand");
                String hostOSVer = prodVersion;
                String hostKernelVer = record.softwareVersion.get("linux");

                if (_hostDao.findByGuid(record.uuid) != null) {
                    s_logger.debug("Skipping " + record.address + " because it is already in the database.");
                    continue;
                }
                
                if (!checkXenServer(conn, dcId, podId, host, record)) {
                    continue;
                }

                s_logger.info("Found host " + record.hostname + " ip=" + record.address);
                XenServerResource resource = new XenServerResource();
                Map<String, String> details = new HashMap<String, String>();
                Map<String, Object> params = new HashMap<String, Object>();
                details.put("url", addr);
                params.put("url", addr);
                details.put("username", username);
                params.put("username", username);
                details.put("password", password);
                params.put("password", password);
                params.put("zone", Long.toString(dcId));
                params.put("guid", record.uuid);
                params.put("pod", pod);
                params.put("management.network.cidr", _cidr);
                details.put(HostInfo.HOST_OS, hostOS);
                details.put(HostInfo.HOST_OS_VERSION, hostOSVer);
                details.put(HostInfo.HOST_OS_KERNEL_VERSION, hostKernelVer);
                details.put(HostInfo.HYPERVISOR_VERSION, xenVersion);

                if (!params.containsKey("public.network.device")) {
                    params.put("public.network.device", _publicNic);
                    details.put("public.network.device", _publicNic);
                }
                
                params.put(Config.Wait.toString().toLowerCase(), Integer.toString(_wait));
                try {
                    resource.configure("Xen Server", params);
                } catch (ConfigurationException e) {
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Error is " + e.getMessage());
                    s_logger.warn("Unable to instantiate " + record.address, e);
                    continue;
                }

                resource.start();
                resources.put(resource, details);
            }
            
            if (!addHostsToPool(url, conn, dcId, podId, resources)) {
                return null;
            }
        } catch (MalformedURLException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("Malformed URL Exception: " + e.getMessage());
                s_logger.trace("Malformed URL Exception: ", e);
            }
            return null;
        } catch (SessionAuthenticationFailed e) {
            s_logger.warn("Authentication error", e);
            return null;
        } catch (XenAPIException e) {
            s_logger.warn("XenAPI exception", e);
            return null;
        } catch (XmlRpcException e) {
            s_logger.warn("Xml Rpc Exception", e);
            return null;
        } catch (UnknownHostException e) {
            s_logger.warn("Unable to resolve the host name", e);
            return null;
        } finally {
        	if (conn != null) {
              conn.dispose();
        	}
        }
        
        return resources;
    }
    
    String getPoolUuid(Connection conn) throws XenAPIException, XmlRpcException {
        Map<Pool, Pool.Record> pools = Pool.getAllRecords(conn);
        assert pools.size() == 1 : "Pools size is " + pools.size();
        return pools.values().iterator().next().uuid;
    }
    
    protected boolean checkXenServer(Connection conn, long dcId, Long podId, Host host, Host.Record record) {
        String prodVersion = record.softwareVersion.get("product_version");
        String xapiVersion = record.softwareVersion.get("xapi");
        String xenVersion = record.softwareVersion.get("xen");
        String hostOS = record.softwareVersion.get("product_brand");
        String hostOSVer = prodVersion;
        String hostKernelVer = record.softwareVersion.get("linux");
        
        if (Version.compare(_minProductVersion, prodVersion) < 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the product version " + prodVersion + " is lower than the minimum " + _minProductVersion);
            s_logger.debug("Unable to add host because the product version " + prodVersion + " is lower than the minimum " + _minProductVersion);
            return false;
        }
        
        if (_maxProductVersion != null && Version.compare(prodVersion, _maxProductVersion) > 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the product version " + prodVersion + " is higher than the maximum " + _maxProductVersion);
            s_logger.debug("Unable to add host because the product version " + prodVersion + " is higher than the maximum " + _maxProductVersion);
            return false;
        }
        
        if (Version.compare(_minXenVersion, xenVersion) < 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the xen version " + xenVersion + " is lower than the minimum " + _minXenVersion);
            s_logger.debug("Unable to add host because the xen version " + xenVersion + " is lower than the minimum " + _minXenVersion);
            return false;
        }
        
        if (_maxXenVersion != null && Version.compare(xenVersion, _maxXenVersion) > 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the xen version " + xenVersion + " is higher than the maximum " + _maxXenVersion);
            s_logger.debug("Unable to add host because the xen version " + xenVersion + " is higher than the maximum " + _maxXenVersion);
            return false;
        }
        
        if (Version.compare(_minXapiVersion, xapiVersion) < 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the xapi version " + xapiVersion + " is lower than the minimum " + _minXapiVersion);
            s_logger.debug("Unable to add host because the xapi version " + xapiVersion + " is lower than the minimum " + _minXapiVersion);
            return false;
        }
        
        if (_maxXapiVersion != null && Version.compare(xapiVersion, _maxXapiVersion) > 0) {
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to add host because the xapi version " + xapiVersion + " is higher than the maximum " + _maxXapiVersion);
            s_logger.debug("Unable to add host because the xapi version " + xapiVersion + " is higher than the maximum " + _maxXapiVersion);
            return false;
        }
        
        Set<Ternary<String, String, Boolean>> required = new HashSet<Ternary<String, String, Boolean>>(_requiredPatches.size());
        for (Pair<String, String> req : _requiredPatches) {
            required.add(new Ternary<String, String, Boolean>(req.first(), req.second(), false));
        }
        
        try {
            Set<HostPatch> patches = host.getPatches(conn);
            if (patches != null) {
                for (HostPatch patch : patches) {
                    HostPatch.Record hpr = patch.getRecord(conn);
                    PoolPatch.Record ppr = hpr.poolPatch.getRecord(conn);
                    
                    for (Ternary<String, String, Boolean> req: required) {
                        if (hpr.nameLabel != null && hpr.nameLabel.contains(req.first()) &&
                            hpr.version != null && hpr.version.contains(req.second())) {
                            req.third(true);
                            break;
                        }
                        
                        if (ppr.nameLabel != null && ppr.nameLabel.contains(req.first()) &&
                            ppr.version != null && ppr.version.contains(req.second())) {
                            req.third(true);
                            break;
                        }
                    }
                }
            }
            for (Ternary<String, String, Boolean> req : required) {
                if (!req.third()) {
                    s_logger.warn("Unable to find the following patch: " + req.first() + " version " + req.second());
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Unable to find the following patch: " + req.first() + " version " + req.second());
                    return false;
                }
            }
        } catch (XenAPIException e) {
            s_logger.warn("Unable to add " + record.address, e);
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Error is " + e.getMessage());
            return false;
        } catch (XmlRpcException e) {
            s_logger.warn("Unable to add " + record.address, e);
            _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + record.address, "Error is " + e.getMessage());
            return false;
        }
        
        return true;
    }

    protected void addSamePool(Connection conn, Map<XenServerResource, Map<String, String>> resources) throws XenAPIException, XmlRpcException {
        Map<Pool, Pool.Record> hps = Pool.getAllRecords(conn);
        assert (hps.size() == 1) : "How can it be more than one but it's actually " + hps.size();
        
        // This is the pool.
        String poolUuid = hps.values().iterator().next().uuid;
        
        for (Map<String, String> details : resources.values()) {
            details.put("pool", poolUuid);
        }
    }
    
    protected boolean addHostsToPool(URL url, Connection conn, long dcId, Long podId, Map<XenServerResource, Map<String, String>> resources) throws XenAPIException, XmlRpcException {
        Map<String, String> params = UrlUtil.parseQueryParameters(url);
        boolean formPoolsInPod = _formPoolsInPod;
        String creationStrategy = _creationStrategy;
        int maxPoolsInPod = _maxPoolsInPod;
        boolean forceJoinPool = _forceJoinPool;
        
        String value = params.get(Config.CreatePoolsInPod);
        if (value != null) {
            formPoolsInPod = Boolean.parseBoolean(value);
        }
        
        if (podId == null || (!_formPoolsInPod && !Boolean.parseBoolean(params.get("formPool")))) {
            s_logger.debug("Not suppose to form pools so returning with true.");
            addSamePool(conn, resources);
            return true;
        }
        
        List<HostVO> hosts = _hostDao.listByHostPod(podId);
        Map<String, Pair<HostVO, Integer>> pools = new HashMap<String, Pair<HostVO, Integer>>();
        for (HostVO h : hosts) {
            if (h.getHypervisorType() == HypervisorType.XenServer) {
                _hostDao.loadDetails(h);
                String poolName = h.getDetail("pool");
                Pair<HostVO, Integer> c = pools.get(poolName);
                if (c == null) {
                    c = new Pair<HostVO, Integer>(h, 0);
                }
                c.second(c.second() + 1);
                pools.put(poolName, c);
            }
        }
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("There are " + pools.size() + " in pod " + podId);
        }
        
        String poolUuid = getPoolUuid(conn);
        if (pools.get(poolUuid) != null) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug("We're discovering more hosts in the same pool " + poolUuid);
            }
            addSamePool(conn, resources);
            return true;
        }
        if (resources.size() > 1) { // The hosts are already in a pool.  Let's see what we're asked to do with this.
            boolean force = Boolean.parseBoolean(params.get("force"));
            if (pools.size() < _maxPoolsInPod) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Resources are added as a pool and we have enough pools so just add it as one pool: " + poolUuid);
                }
                addSamePool(conn, resources);
                return true;
            }
            
            if (pools.size() >= _maxPoolsInPod && !force) {
                s_logger.warn("The hosts retrieved are already in a pool and the pool limit per pod has been exceeded");
                return false;
            }
        }
            
        if (pools.size() == 0) {
            s_logger.debug("There are no pools in the pod right now.");
            addSamePool(conn, resources);
            return true;
        }
        
        Iterator<Map.Entry<XenServerResource, Map<String, String>>> it = resources.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<XenServerResource, Map<String, String>> entry = it.next();
            XenServerResource resource = entry.getKey();
            Map<String, String> details = entry.getValue();
                
            Pool pool = null;
            HostVO poolHost = null;
            Map.Entry<String, Pair<HostVO, Integer>> ps = null;
            if (_creationStrategy.equalsIgnoreCase("Greedy")) {
                for (Map.Entry<String, Pair<HostVO, Integer>> poolStat : pools.entrySet()) {
                    if (poolStat.getValue().second() < 16) {
                        ps = poolStat;
                        break;
                    }
                }
            } else {
                Iterator<Map.Entry<String, Pair<HostVO, Integer>>> it2 = pools.entrySet().iterator();
                Map.Entry<String, Pair<HostVO, Integer>> smallest = it2.next();
                while (it2.hasNext()) {
                    Map.Entry<String, Pair<HostVO, Integer>> e = it2.next();
                    if (smallest.getValue().second() > e.getValue().second()) {
                        smallest = e;
                    }
                }
                if ((smallest.getValue().second() < 5 && pools.size() < _maxPoolsInPod) || (pools.size() == _maxPoolsInPod && smallest.getValue().second() < 16)) {
                    ps = smallest;
                }
            }
            if (ps == null) { // Didn't find a pool that's not full.
                if (pools.size() >= _maxPoolsInPod) {
                    s_logger.info("Unable to add host because maximum number of xen server pools in a pod has been reached");
                    it.remove();
                    resource.disconnected();
                    resource.stop();
                    continue;
                } else {
                    Map<Pool, Pool.Record> pools2 = Pool.getAllRecords(conn);
                    
                    String uuid = pools2.values().iterator().next().uuid;
                    
                    pools.put(uuid, new Pair<HostVO, Integer>(null, 1));
                    details.put("pool", uuid);
                }
            } else {
                details.put("pool", ps.getKey());
                HostVO h = ps.getValue().first();
                if (!resource.joinPool(h.getPrivateIpAddress(), h.getDetail("username"), h.getDetail("password"))) {
                    _alertMgr.sendAlert(AlertManager.ALERT_TYPE_HOST, dcId, podId, "Unable to add " + url, "Unable to add ");
                    //s_logger.debug("Unable to add host because the xapi version " + xapiVersion + " is higher than the maximum " + _maxXapiVersion);
                    it.remove();
                    continue;
                }
                ps.getValue().second(ps.getValue().second() + 1);
            }
        }
        return true;
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;
        
        _minXenVersion = (String)params.get("min.xen.version");
        if (_minXenVersion == null) {
            _minXenVersion = "3.3.1";
        }
        
        _minProductVersion = (String)params.get("min.product.version");
        if (_minProductVersion == null) {
            _minProductVersion = "5.5.0";
        }
        
        _minXapiVersion = (String)params.get("min.xapi.version");
        if (_minXapiVersion == null) {
            _minXapiVersion = "1.3";
        }
        
        _maxXenVersion = (String)params.get("min.xen.version");
        _maxProductVersion = (String)params.get("min.product.version");
        _maxXapiVersion = (String)params.get("min.xapi.version");
        
        _requiredPatches = new ArrayList<Pair<String, String>>();
        String value = (String)params.get("required.patches");
        if (value != null) {
            String[] patches = value.split(",");
            for (String patch : patches) {
                String[] tokens = patch.split("/");
                String ver = tokens.length == 2 ? tokens[1] : null;
                _requiredPatches.add(new Pair<String, String>(tokens[0], ver));
            }
        }
        
        if (_requiredPatches.size() == 0) {
            _requiredPatches.add(new Pair<String, String>("Update 2 for XenServer 5.5.0", "1.0"));
        }
        
        ComponentLocator locator = ComponentLocator.getCurrentLocator();
        _alertMgr = locator.getManager(AlertManager.class);
        if (_alertMgr == null) {
            throw new ConfigurationException("Unable to find the alert manager");
        }
        
        ConfigurationDao configDao = locator.getDao(ConfigurationDao.class);
        if (configDao == null) {
            throw new ConfigurationException("Unable to find the configuration dao");
        }
        
        Map<String, String> dbParams = configDao.getConfiguration("AgentManager", params);
        
        _publicNic = dbParams.get("xen.public.network.device");
        _privateNic = dbParams.get("xen.private.network.device");
        
        _cidr = dbParams.get("management.network.cidr");
        
        value = dbParams.get(Config.Wait.toString());
        _wait = NumbersUtil.parseInt(value, Integer.parseInt(Config.Wait.getDefaultValue()));
        
        value = dbParams.get(Config.CreatePoolsInPod);
        _formPoolsInPod = value == null ? true : Boolean.parseBoolean(value);
        
        value = dbParams.get(Config.MaxPoolsInPod);
        _maxPoolsInPod = NumbersUtil.parseInt(value, 1);
        
        _creationStrategy = dbParams.get(Config.PoolCreationStrategy);
        if (_creationStrategy == null) {
            _creationStrategy = "Greedy";
        }
        
        if (!_creationStrategy.equalsIgnoreCase("Greedy") && !_creationStrategy.equalsIgnoreCase("Even")) {
            throw new ConfigurationException("The value given for " + Config.PoolCreationStrategy.getName() + " can only be " + Config.PoolCreationStrategy.getRange() + " but it is " + _creationStrategy);
        }
        
        _forceJoinPool = Boolean.parseBoolean(dbParams.get(Config.ForceJoinPool));
        
        s_logger.info("XenServerDiscoverer started.  " + Config.PoolCreationStrategy + "=" + _creationStrategy + "; " + Config.MaxPoolsInPod + "=" + _maxPoolsInPod + "; " + Config.CreatePoolsInPod + "=" + _formPoolsInPod + "; " + Config.ForceJoinPool + "=" + _forceJoinPool);
        
        _connPool = XenServerConnectionPool.getInstance();
        
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
}
