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
package com.vmops.agent.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.api.CronCommand;
import com.vmops.agent.api.PingCommand;
import com.vmops.agent.api.StartupAnswer;
import com.vmops.agent.api.StartupCommand;
import com.vmops.agent.transport.Request;
import com.vmops.agent.transport.Response;
import com.vmops.exception.AgentUnavailableException;
import com.vmops.host.Status;
import com.vmops.resource.ServerResource;
import com.vmops.utils.concurrency.NamedThreadFactory;

public class DirectAgentAttache extends AgentAttache {
    private final static Logger s_logger = Logger.getLogger(DirectAgentAttache.class);
    
    ServerResource _resource;
    static ScheduledExecutorService _executor = Executors.newScheduledThreadPool(10, new NamedThreadFactory("DirectAgent"));
    List<ScheduledFuture<?>> _futures = new ArrayList<ScheduledFuture<?>>();
    AgentManagerImpl _mgr;
    long _seq = 0;

	public DirectAgentAttache(long id, ServerResource resource, boolean maintenance, AgentManagerImpl mgr) {
		super(id, maintenance);
		_resource = resource;
		_mgr = mgr;
	}

	@Override
	public void disconnect(Status state) {
	    if (s_logger.isDebugEnabled()) {
	        s_logger.debug("Processing disconnect" + _id);
	    }
	    
	    for (ScheduledFuture<?> future : _futures) {
	        future.cancel(false);
	    }

	    synchronized(this) {
    	    _resource.disconnected();
    	    _executor.schedule(new ConnectTask(_resource), 5, TimeUnit.SECONDS);
    	    _resource = null;
	    }
	}

	@Override
	public boolean equals(Object obj) {
	    if (!(obj instanceof DirectAgentAttache)) {
	        return false;
	    }
	    return super.equals(obj) && _executor == ((DirectAgentAttache)obj)._executor;
	}

	@Override
	public synchronized boolean isClosed() {
	    return _resource == null;
	}
	
	@Override
	public void send(Request req) throws AgentUnavailableException {
	    if (s_logger.isDebugEnabled()) {
	        s_logger.debug(log(req.getSequence(), "Executing " + req.toString()));
	    }
	    if (req instanceof Response) {
	        Response resp = (Response)req;
	        Answer[] answers = resp.getAnswers();
	        if (answers != null && answers[0] instanceof StartupAnswer) {
	            StartupAnswer startup = (StartupAnswer)answers[0];
	            int interval = startup.getPingInterval();
	            _futures.add(_executor.scheduleAtFixedRate(new PingTask(), interval, interval, TimeUnit.SECONDS));
	        }
	    } else {
    	    Command[] cmds = req.getCommands();
    	    if (cmds.length > 0 && !(cmds[0] instanceof CronCommand)) {
    	        _executor.execute(new Task(req));
    	    } else {
    	        CronCommand cmd = (CronCommand)cmds[0];
    	        _futures.add(_executor.scheduleAtFixedRate(new Task(req), cmd.getInterval(), cmd.getInterval(), TimeUnit.SECONDS));
    	    }
	    }
	}
	
	@Override
    public void process(Answer[] answers) {
        if (answers != null && answers[0] instanceof StartupAnswer) {
            StartupAnswer startup = (StartupAnswer)answers[0];
            int interval = startup.getPingInterval();
            _futures.add(_executor.scheduleAtFixedRate(new PingTask(), interval, interval, TimeUnit.SECONDS));
        }
	}
	
	protected class PingTask implements Runnable {
	    @Override
	    public synchronized void run() {
	        try {
	            ServerResource resource = _resource;
	            
	            if (resource != null) {
        	        PingCommand cmd = resource.getCurrentStatus(_id);
        	        if (cmd == null) {
        	            s_logger.warn("Unable to get current status on " + _id);
        	            return;
        	        }
        	        long seq = _seq++;
        	        
        	        if (s_logger.isTraceEnabled()) {
        	            s_logger.trace("SeqA " + _id + "-" + seq + ": " + new Request(seq, _id, -1, cmd, false).toString());
        	        } else if (s_logger.isDebugEnabled()) {
        	            s_logger.debug("SeqA " + _id + "-" + seq + ": Ping");
        	        }
        	        _mgr.handleCommands(DirectAgentAttache.this, seq, new Command[]{cmd});
	            } else {
	                s_logger.debug("Unable to send ping because agent is disconnected " + _id);
	            }
	        } catch (Exception e) {
	            s_logger.warn("Unable to complete the ping task", e);
	        }
	    }
	}
	
	protected class ConnectTask implements Runnable {
	    ServerResource _resource;
	    public ConnectTask(ServerResource resource) {
	        _resource = resource;
	    }
	    public void run() {
	        try {
    	        s_logger.info("Reconnecting to agent manager");
    	        StartupCommand[] cmds = _resource.initialize();
    	        _mgr.handleDirectConnect(_resource, cmds, null, false);
	        } catch (Exception e) {
	            s_logger.warn("Reconnect has an exception", e);
	        }
	    }
	}
	
	protected class Task implements Runnable {
	    Request _req;
	    
	    public Task(Request req) {
	        _req = req;
	    }
	    
	    @Override
	    public void run() {
            long seq = _req.getSequence();
            try {
                ServerResource resource = _resource;
    	        Command[] cmds = _req.getCommands();
    	        boolean stopOnError = _req.stopOnError();
    
    	        if (s_logger.isDebugEnabled()) {
    	            s_logger.debug(log(seq, "Executing request"));
    	        }
    	        ArrayList<Answer> answers = new ArrayList<Answer>(cmds.length);
    	        for (int i = 0; i < cmds.length; i++) {
    	            Answer answer = null;
    	            try {
    	                if (resource != null) {
    	                    answer = resource.executeRequest(cmds[i]);
    	                } else {
    	                    answer = new Answer(cmds[i], false, "Agent is disconnected");
    	                }
    	            } catch (Exception e) {
    	                s_logger.warn("Exception Caught while executing command", e);
    	                answer = new Answer(cmds[i], false, e.toString());
    	            }
                    answers.add(answer);
                    if (!answer.getResult() && stopOnError) {
                        if (i < cmds.length - 1 && s_logger.isDebugEnabled()) {
                            s_logger.debug(log(seq, "Cancelling because one of the answers is false and it is stop on error."));
                        }
                        break;
                    }
    	        }
    	        
    	        Response resp = new Response(_req, answers.toArray(new Answer[answers.size()]));
    	        if (s_logger.isDebugEnabled()) {
    	            s_logger.debug(log(seq, "Response Received: "));
    	        }
    	        
    	        processAnswers(seq, resp);
    	    } catch (Exception e) {
    	        s_logger.warn(log(seq, "Exception caught "), e);
    	    }
	    }
	}
}
