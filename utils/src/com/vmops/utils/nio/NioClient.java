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
package com.vmops.utils.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.apache.log4j.Logger;

public class NioClient extends NioConnection {
    private static final Logger s_logger = Logger.getLogger(NioClient.class);
    
    protected String _host;
    
    public NioClient(String name, String host, int port, int workers, HandlerFactory factory) {
        super(name, port, workers, factory);
        _host = host;
    }
    
    @Override
    protected void init() throws IOException {
        _selector = Selector.open();
        
        SocketChannel sch = SocketChannel.open();
        sch.configureBlocking(false);
        s_logger.info("Connecting to " + _host + ":" + _port);
        
        InetSocketAddress addr = new InetSocketAddress(_host, _port);
        sch.connect(addr);
        
        Link link = new Link(addr, this);
        SelectionKey key = sch.register(_selector, SelectionKey.OP_CONNECT);
        link.setKey(key);
        key.attach(link);
    }
    
    @Override
    protected void registerLink(InetSocketAddress saddr, Link link) {
        // don't do anything.
    }
    
    @Override
    protected void unregisterLink(InetSocketAddress saddr) {
        // don't do anything.
    }
}
