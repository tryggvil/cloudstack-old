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
package com.vmops.resource.xen;

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;

import com.vmops.utils.exception.VmopsRuntimeException;
import com.xensource.xenapi.APIVersion;
import com.xensource.xenapi.Connection;
import com.xensource.xenapi.Host;
import com.xensource.xenapi.Pool;
import com.xensource.xenapi.Session;
import com.xensource.xenapi.Types;
import com.xensource.xenapi.Types.XenAPIException;

public class XenServerConnectionPool {
    private static final Logger s_logger = Logger.getLogger(XenServerConnectionPool.class);
    
    protected HashMap<String /*hostUuid*/, XenServerConnection> _conns = new HashMap<String, XenServerConnection>();
    protected HashMap<String /*poolUuid*/, ConnectionInfo> _infos = new HashMap<String, ConnectionInfo>();
    
    protected int _retries;
    protected int _interval;
    
    protected XenServerConnectionPool() {
        _retries = 10;
        _interval = 3;
    }

    public synchronized void disconnect(String uuid, String poolUuid) {
        Connection conn = _conns.remove(uuid);
        if (conn == null) {
            return;
        }
        
        conn.dispose();
        
        ConnectionInfo info = _infos.get(poolUuid);
        if (info == null) {
            return;
        }
        
        info.refs.remove(uuid);
        if (info.refs.size() == 0) {
            _infos.remove(poolUuid);
            try {
                Session.logout(info.conn);
                info.conn.dispose();
            } catch (Exception e) {
                s_logger.debug("Logout has a problem " + e.getMessage());
            }
            info.conn = null;
        }
    }
    
    public Connection connect(String urlString, String username, String password, int wait) {
        return connect(null, urlString, username, password, wait);
    }
    
    protected ConnectionInfo getConnectionInfo(String poolUuid) {
        synchronized(_infos) {
            return _infos.get(poolUuid);
        }
    }
    
    protected XenServerConnection getConnection(String hostUuid) {
        synchronized(_conns) {
            return _conns.get(hostUuid);
        }
    }
    
    public synchronized Connection connect(String hostUuid, String ipAddress, String username, String password, int wait) {
        XenServerConnection conn = null;
        if (hostUuid != null) { // Let's see if it is an existing connection.
            conn = _conns.get(hostUuid);
            if (conn != null) {
                return conn;
            }
        }
        
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Creating connection to " + ipAddress);
        }
        
        // Well, it's not already in the existing connection list.
        // Let's login and see what this connection should be.
        // You might think this is slow.  Why not cache the pool uuid
        // you say?  Well, this doesn't happen very often.
        try {
            URL masterUrl = new URL("http://" + ipAddress);
            String poolUuid = null;

            Host host = null;
            Session session = null;
            try {
                conn = new XenServerConnection(masterUrl, username, password, _retries, _interval, wait);
                session = Session.loginWithPassword(conn, username, password, APIVersion.latest().toString());
                Pool.Record pr = getPoolRecord(conn);
                poolUuid = pr.uuid;
            } catch (Types.HostIsSlave e) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Host " + ipAddress + " is not a master so trying as slave");
                }
                
                session = Session.slaveLocalLoginWithPassword(conn, username, password);
                
                Pool.Record pr = getPoolRecord(conn);
                poolUuid = pr.uuid;
                Host master = pr.master;
                String ma = master.getAddress(conn);
                masterUrl = new URL("http://" + ma);
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("Master is found to be at " + ma);
                }
                
                Session.localLogout(conn);
                conn.dispose();
                
                conn = new XenServerConnection(masterUrl, username, password, _retries, _interval, wait);
                session = Session.loginWithPassword(conn, username, password, APIVersion.latest().toString());
            }
            
            ConnectionInfo info = null;
            info = _infos.get(poolUuid);
            if (info != null) {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("The pool already has a master connection.");
                }
                try {
                    Session.logout(conn);
                    conn.dispose();
                } catch (Exception e) {
                    s_logger.debug("Caught Exception while logging on but pushing on...." + e.getMessage());
                }
                conn = new XenServerConnection(info.conn);
            } else {
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug("This is the very first connection");
                }
                info = new ConnectionInfo();
                info.conn = conn;
                info.masterUrl = masterUrl;
                info.refs = new HashMap<String, String>();
                _infos.put(poolUuid, info);
            }
            
            if (hostUuid == null) {
                Map<Host, Host.Record> hosts = Host.getAllRecords(conn);
                for (Host.Record rec : hosts.values()) {
                    if (rec.address.equals(ipAddress)) {
                        hostUuid = rec.uuid;
                        break;
                    }
                }
                if (hostUuid == null) {
                    s_logger.warn("Unable to find host uuid for " + ipAddress);
                    return null;
                }
            }
            
            info.refs.put(hostUuid, ipAddress);
            _conns.put(hostUuid, conn);
            
            s_logger.info("Connection made to " + ipAddress + " for host " + hostUuid + ".  Pool Uuid is " + poolUuid);
            
            return conn;
        } catch (XenAPIException e) {
            s_logger.warn("Unable to make a connection to the server " + ipAddress, e);
            return null;
        } catch (XmlRpcException e) {
            s_logger.warn("Unable to make a connection to the server " + ipAddress, e);
            return null;
        } catch (MalformedURLException e) {
            throw new VmopsRuntimeException("How can we get a malformed exception for this " + ipAddress, e);
        }
    }
    
    protected Pool.Record getPoolRecord(Connection conn) throws XmlRpcException, XenAPIException {
        Map<Pool, Pool.Record> pools = Pool.getAllRecords(conn);
        assert pools.size() == 1 : "Pool size is not one....hmmm....wth? " + pools.size();
        
        return pools.values().iterator().next();
    }
    
    

    /*
    public synchronized Connection getConnection(String uuid, String masterUrl, String url, String username, String password, int wait) {
        XenServerConnection conn = _conns.get(uuid);
        if (conn != null) {
            return conn;
        }
        
        // We are either the very first time connecting to this server or the connection has been severed before.
        ConnectionInfo info = _infos.get(masterUrl);
        if (info != null && info.conn != null) {
            // This means it's been severed before so just create a new connection.
            info.refs.put(uuid, url);
            XenServerConnection connection = new XenServerConnection(info.conn);
            _conns.put(uuid, connection);
            return connection;
        }
        
        // We have no connection now.  Let's login and create one.
        try {
            URL murl = new URL(masterUrl);
            conn = new XenServerConnection(murl, username, password, _retries, _interval, wait);
            Session.loginWithPassword(conn, username, password, APIVersion.latest().toString());
            _conns.put(uuid, conn);
            if (info == null) {
                info = new ConnectionInfo();
                _infos.put(masterUrl, info);
            }
            info.conn = conn;
            info.masterUrl = murl;
            info.refs.put(uuid, url);
            return conn;
        } catch (BadServerResponse e) {
            s_logger.warn("Bad Server Response", e);
            throw new VmopsRuntimeException("Unable to get connection", e);
        } catch (SessionAuthenticationFailed e) {
            s_logger.warn("Authentication error", e);
            throw new VmopsRuntimeException("Unable to get connection", e);
        } catch (XenAPIException e) {
            s_logger.warn("XenAPI exception", e);
            throw new VmopsRuntimeException("Unable to get connection", e);
        } catch (XmlRpcException e) {
            s_logger.warn("Xml Rpc Exception", e);
            throw new VmopsRuntimeException("Unable to get connection", e);
        } catch (MalformedURLException e) {
            s_logger.warn("Unable to get connection", e);
            throw new VmopsRuntimeException("Unable to get connection", e);
        }
    }
    */
    
    private static final XenServerConnectionPool s_instance = new XenServerConnectionPool();
    public static XenServerConnectionPool getInstance() {
        return s_instance;
    }
    
    protected class ConnectionInfo {
        public URL masterUrl;
        public XenServerConnection conn;
        public HashMap<String, String> refs = new HashMap<String, String>();
    }
    
    public class XenServerConnection extends Connection {
        long _interval;
        int _retries;
        String _username;
        String _password;
        URL _url;
        
        public XenServerConnection(URL url, String username, String password, int retries, int interval, int wait) {
            super(url, wait);
            _url = url;
            _retries = retries;
            _username = username;
            _password = password;
            _interval = (long)interval * 1000;
        }

        public XenServerConnection(XenServerConnection conn) {
            super(conn._url, conn.getSessionReference(), conn._wait);
            _url = conn._url;
            _retries = conn._retries;
            _username = conn._username;
            _password = conn._password;
            _interval = conn._interval;
        }
        
        public int getWaitTimeout() {
            return _wait;
        }
        
        @Override
        protected Map dispatch(String method_call, Object[] method_params) throws XmlRpcException, XenAPIException {
            if (method_call.equals("session.login_with_password") || method_call.equals("session.slave_local_login_with_password") || method_call.equals("session.logout")) {
                Exception c = null;
                for (int retries = 0; retries < _retries; retries++) {
                    try {
                        return super.dispatch(method_call, method_params);
                    } catch (XmlRpcException e) {
                        Throwable cause = e.getCause();
                        if (cause == null || !(cause instanceof SocketException)) {
                            throw e;
                        }
                        c = e;
                        s_logger.debug("Unable to login...retrying " + retries);
                    }
                    try {
                        Thread.sleep(_interval);
                    } catch(InterruptedException e) {
                        s_logger.debug("Man....I was just getting comfortable there....who woke me up?");
                    }
                }
                throw new VmopsRuntimeException("After " + _retries + " retries, we cannot contact the host ", c);
            }
            
            Exception c = null;
            for (int retries = 0; retries < _retries; retries++) {
                try {
                    return super.dispatch(method_call, method_params);
                } catch (Types.SessionInvalid e) {
                    c = e;
                    s_logger.debug("Session is invalid.  Reconnecting");
                } catch (XmlRpcException e) {
                    c = e;
                    Throwable cause = e.getCause();
                    if (cause == null || !(cause instanceof SocketException)) {
                        throw e;
                    }
                    s_logger.debug("Connection couldn't be made. Reconnecting");
                }
                
                try {
                    Thread.sleep(_interval);
                } catch (InterruptedException e) {
                    s_logger.info("Who woke me from my slumber?");
                }

                Session session = Session.loginWithPassword(this, _username, _password, APIVersion.latest().toString());
                method_params[0] = getSessionReference();
            }
            throw new VmopsRuntimeException("After " + _retries + " retries, we cannot contact the host ", c);
        }
    }
}
