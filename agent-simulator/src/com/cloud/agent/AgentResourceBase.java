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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.resource.ServerResource;

public abstract class AgentResourceBase implements ServerResource {
    private static final Logger s_logger = Logger.getLogger(AgentResourceBase.class);
    
    protected String _name;
    private ArrayList<String> _warnings = new ArrayList<String>();
    private ArrayList<String> _errors = new ArrayList<String>();
    
    protected AgentContainer _agent;
    private IAgentControl _agentControl;
    
    public AgentResourceBase(AgentContainer agent) {
    	_agent = agent;
    }
    
    public AgentContainer getAgent() {
    	return _agent;
    }

    @Override
    public String getName() {
        return _name;
    }
    
    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {
        _name = name;
        return true;
    }
    
    @Override
    public void disconnected() {
    }
    
    protected void recordWarning(String msg, Throwable th) {
        String str = getLogStr(msg, th);
        synchronized(_warnings) {
            _warnings.add(str);
        }
    }
    
    protected void recordWarning(String msg) {
        recordWarning(msg, null);       
    }
    
    protected List<String> getWarnings() {
        synchronized(this) {
            ArrayList<String> results = _warnings;
            _warnings = new ArrayList<String>();
            return results;
        }
    }
    
    protected List<String> getErrors() {
        synchronized(this) {
            ArrayList<String> result = _errors;
            _errors = new ArrayList<String>();
            return result;
        }
    }
    
    protected void recordError(String msg, Throwable th) {
        String str = getLogStr(msg, th);
        synchronized(_errors) {
            _errors.add(str);
        }
    }
    
    protected void recordError(String msg) {
        recordError(msg, null);
    }
    
    protected Answer createErrorAnswer(Command cmd, String msg, Throwable th) {
        StringWriter writer = new StringWriter();
        if (msg != null) {
            writer.append(msg);
        }
        writer.append("===>Stack<===");
        th.printStackTrace(new PrintWriter(writer));
        return new Answer(cmd, false, writer.toString());
    }
    
    protected String createErrorDetail(String msg, Throwable th) {
        StringWriter writer = new StringWriter();
        if (msg != null) {
            writer.append(msg);
        }
        writer.append("===>Stack<===");
        th.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
    
    protected String getLogStr(String msg, Throwable th) {
        StringWriter writer = new StringWriter();
        writer.append(new Date().toString()).append(": ").append(msg);
        if (th != null) {
            writer.append("\n  Exception: ");
            th.printStackTrace(new PrintWriter(writer));
        }
        return writer.toString();
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }
    
    @Override
    public IAgentControl getAgentControl() {
    	return _agentControl; 
    }
    
    @Override
    public void setAgentControl(IAgentControl agentControl) {
    	_agentControl = agentControl;
    }
    
    protected String findScript(String script) {
        s_logger.debug("Looking for " + script + " in the classpath");
        URL url = ClassLoader.getSystemResource(script);
        File file = null;
        if (url == null) {
            file = new File("./" + script);
            s_logger.debug("Looking for " + script + " in " + file.getAbsolutePath());
            if (!file.exists()) {
                return null;
            }
        } else {
            file = new File(url.getFile());
        }        
        return file.getAbsolutePath();
    }
}
