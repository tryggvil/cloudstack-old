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

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.cloud.agent.Listener;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.CheckHealthCommand;
import com.cloud.agent.api.CheckVirtualMachineCommand;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MigrateCommand;
import com.cloud.agent.api.PingTestCommand;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartCommand;
import com.cloud.agent.api.StartRouterCommand;
import com.cloud.agent.api.StopCommand;
import com.cloud.agent.api.storage.CreateCommand;
import com.cloud.agent.transport.Request;
import com.cloud.agent.transport.Response;
import com.cloud.exception.AgentUnavailableException;
import com.cloud.exception.OperationTimedoutException;
import com.cloud.host.Status;
import com.cloud.utils.concurrency.NamedThreadFactory;

/**
 *  AgentAttache provides basic commands to be implemented.
 */
public abstract class AgentAttache {
    private static final Logger s_logger = Logger.getLogger(AgentAttache.class);
    
    private static final ScheduledExecutorService s_executor = Executors.newScheduledThreadPool(10, new NamedThreadFactory("ListenerTimer"));
    
    protected static final Comparator<Request> s_reqComparator =
        new Comparator<Request>() {
            @Override
            public int compare(Request o1, Request o2) {
                long seq1 = o1.getSequence();
                long seq2 = o2.getSequence();
                if (seq1 < seq2) {
                    return -1;
                } else if (seq1 > seq2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        };
        
    protected static final Comparator<Object> s_seqComparator =
        new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                long seq1 = ((Request) o1).getSequence();
                long seq2 = (Long) o2;
                if (seq1 < seq2) {
                    return -1;
                } else if (seq1 > seq2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        };

    protected final long _id;
    protected final ConcurrentHashMap<Long, Listener> _waitForList;
    protected final LinkedList<Request> _requests;
    protected Long _currentSequence;
    protected Status _status = Status.Connecting;
    protected boolean _maintenance;
    
    public final static String[] s_commandsAllowedInMaintenanceMode =
        new String[] { MigrateCommand.class.toString(), StopCommand.class.toString(), CheckVirtualMachineCommand.class.toString(), PingTestCommand.class.toString(), CheckHealthCommand.class.toString(), ReadyCommand.class.toString() };
    protected final static String[] s_commandsNotAllowedInConnectingMode =
        new String[] { StartCommand.class.toString(), StartRouterCommand.class.toString(), CreateCommand.class.toString() };
    static {
        Arrays.sort(s_commandsAllowedInMaintenanceMode);
        Arrays.sort(s_commandsNotAllowedInConnectingMode);
    }
    

    protected AgentAttache(final long id, boolean maintenance) {
        _id = id;
        _waitForList = new ConcurrentHashMap<Long, Listener>();
        _currentSequence = null;
        _maintenance = maintenance;
        _requests = new LinkedList<Request>();
    }

    public synchronized void setMaintenanceMode(final boolean value) {
        _maintenance = value;
    }
    
    public void ready() {
        _status = Status.Up;
    }
    
    public boolean isConnecting() {
        return _status == Status.Connecting;
    }
    
	public boolean forForward() {
		return false;
	}

    protected void checkAvailability(final Command[] cmds) throws AgentUnavailableException {
        if (!_maintenance && _status != Status.Connecting) {
            return;
        }

        if (_maintenance) {
            for (final Command cmd : cmds) {
                if (Arrays.binarySearch(s_commandsAllowedInMaintenanceMode, cmd.getClass().toString()) < 0) {
                    throw new AgentUnavailableException("Unable to send " + cmd.getClass().toString() + " because agent is in maintenance mode", _id);
                }
            }
        }
        
        if (_status == Status.Connecting) {
            for (final Command cmd : cmds) {
                if (Arrays.binarySearch(s_commandsNotAllowedInConnectingMode, cmd.getClass().toString()) >= 0) {
                    throw new AgentUnavailableException("Unable to send " + cmd.getClass().toString() + " because agent is in connecting mode", _id);
                }
            }
        }
    }
    
    protected synchronized void addRequest(Request req) {
        int index = findRequest(req);
        assert (index < 0) : "How can we get index again? " + index + ":" + req.toString();
        _requests.add(-index - 1, req);
    }

    
    protected void cancel(Request req) {
        long seq = req.getSequence();
        cancel(seq);
    }

    protected synchronized void cancel(final long seq) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(log(seq, "Cancelling."));
        }
        final Listener listener = _waitForList.remove(seq);
        if (listener != null) {
            listener.processDisconnect(_id, Status.Disconnected);
        }
        int index = findRequest(seq);
        if (index >= 0) {
            _requests.remove(index);
        }
    }
    
    protected synchronized int findRequest(Request req) {
        return Collections.binarySearch(_requests, req, s_reqComparator);
    }

    protected synchronized int findRequest(long seq) {
        return Collections.binarySearch(_requests, seq, s_seqComparator);
    }


    protected String log(final long seq, final String msg) {
        return "Seq " + _id + "-" + seq + ": " + msg;
    }
    
    protected void registerListener(final long seq, final Listener listener) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace(log(seq, "Registering listener"));
        }
        if (listener.getTimeout() != -1) {
        	s_executor.schedule(new Alarm(seq), listener.getTimeout(), TimeUnit.SECONDS);
        }
        _waitForList.put(seq, listener);
    }

    protected Listener unregisterListener(final long sequence) {
        if (s_logger.isTraceEnabled()) {
            s_logger.trace(log(sequence, "Unregistering listener"));
        }
        return _waitForList.remove(sequence);
    }
    
    protected Listener getListener(final long sequence) {
        return _waitForList.get(sequence);
    }

    public long getId() {
        return _id;
    }
    
    public int getQueueSize() {
        return _requests.size();
    }

    public boolean processAnswers(final long seq, final Response resp) {
        if (s_logger.isDebugEnabled()) {
            if (!resp.executeInSequence()) {
                s_logger.debug(log(seq, "Processing: " + resp.toString()));
            } else {
                s_logger.trace(log(seq, "Processing: " + resp.toString()));
            }
        }

        final Answer[] answers = resp.getAnswers();

        boolean processed = false;

        Listener monitor = getListener(seq);

        if (monitor == null) {
            if ( answers[0] != null && answers[0].getResult() ) {
                processed = true;
            }
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(log(seq, "Unable to find listener."));
            }
        } else {
            processed = monitor.processAnswer(_id, seq, answers);
            if (s_logger.isTraceEnabled()) {
                s_logger.trace(log(seq, (processed ? "" : " did not ") + " processed "));
            }

            if (!monitor.isRecurring()) {
                unregisterListener(seq);
            }
        }

        if (resp.executeInSequence()) {
            sendNext(seq);
        }
        return processed;
    }

    protected void cancelAllCommands(final Status state, final boolean cancelActive) {
        final Set<Map.Entry<Long, Listener>> entries = _waitForList.entrySet();
        final Iterator<Map.Entry<Long, Listener>> it = entries.iterator();
        while (it.hasNext()) {
            final Map.Entry<Long, Listener> entry = it.next();
            it.remove();
            final Listener monitor = entry.getValue();
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(log(entry.getKey(), "Sending disconnect to " + monitor.getClass()));
            }
            monitor.processDisconnect(_id, state);
        }
    }

    public void cleanup(final Status state) {
        cancelAllCommands(state, true);
        _requests.clear();
    }

    @Override
    public boolean equals(Object obj) {
        try {
            AgentAttache that = (AgentAttache) obj;
            return this._id == that._id;
        } catch (ClassCastException e) {
            assert false : "Who's sending an " + obj.getClass().getSimpleName() + " to AgentAttache.equals()? ";
            return false;
        }
    }
    
    public void send(Request req, final Listener listener) throws AgentUnavailableException {
        checkAvailability(req.getCommands());
        
        long seq = req.getSequence();
        
        if (listener != null) {
            registerListener(seq, listener);
        } else if (s_logger.isDebugEnabled()) {
            s_logger.debug(log(seq, "Routing: "));
        }

        synchronized(this) {
            try {
                if (isClosed()) {
                    throw new AgentUnavailableException("The link to the agent has been closed", _id);
                }
        
                if (req.executeInSequence() && _currentSequence != null) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(log(seq, "Waiting for Seq " + _currentSequence + " Scheduling: " + req.toString()));
                    }
                    addRequest(req);
                    return;
                }
        
                // If we got to here either we're not suppose to set
                // the _currentSequence or it is null already.
                
                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(log(seq, "Sending " + req.toString()));
                }
                send(req);
        
                if (req.executeInSequence() && _currentSequence == null) {
                    _currentSequence = seq;
                    if (s_logger.isTraceEnabled()) {
                        s_logger.trace(log(seq, " is current sequence"));
                    }
                }
            } catch (AgentUnavailableException e) {
                s_logger.info(log(seq, "Unable to send due to " + e.getMessage()));
                cancel(seq);
                throw e;
            } catch (Exception e) {
                s_logger.warn(log(seq, "Unable to send due to "), e);
                cancel(seq);
                throw new AgentUnavailableException("Problem due to other exception " + e.getMessage(), _id);
            }
        }
    }

    public Answer[] send(Request req, final int wait) throws AgentUnavailableException, OperationTimedoutException {
        final SynchronousListener sl = new SynchronousListener(null);
        final long seq = req.getSequence();

        send(req, sl);

        try {
            for (int i = 0; i < 2; i++) {
                Answer[] answers = null;
                try {
                    answers = sl.waitFor(wait);
                } catch (final InterruptedException e) {
                    s_logger.debug(log(seq, "Interrupted"));
                }
                if (answers != null) {
                    if (s_logger.isDebugEnabled()) {
                        if (req.executeInSequence()) {
                            s_logger.debug(log(seq, "Received: " + new Response(req, answers).toString()));
                        } else {
                            s_logger.debug(log(seq, "Received: "));
                            if (s_logger.isTraceEnabled()) {
                                s_logger.trace(log(seq, "Received: " + new Response(req, answers).toString()));
                            }
                        }
                    }
                    return answers;
                }

                answers = sl.getAnswers(); // Try it again.
                if (answers != null) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(log(seq, "Received after timeout: " + new Response(req, answers).toString()));
                    }
                    return answers;
                }

                final Long current = _currentSequence;
                if (current != null && seq != current) {
                    if (s_logger.isDebugEnabled()) {
                        s_logger.debug(log(seq, "Waited too long."));
                    }

                    throw new OperationTimedoutException(req.getCommands(), _id, seq, wait, false);
                }

                if (s_logger.isDebugEnabled()) {
                    s_logger.debug(log(seq, "Waiting some more time because this is the current command"));
                }
            }

            throw new OperationTimedoutException(req.getCommands(), _id, seq, wait * 2, true);
        } catch (OperationTimedoutException e) {
            s_logger.warn(log(seq, "Timed out on " + req.toString()));
            cancel(seq);
            throw e;
        } catch (Exception e) {
            s_logger.warn(log(seq, "Exception while waiting for answer"), e);
            cancel(seq);
            throw new OperationTimedoutException(req.getCommands(), _id, seq, wait, false);
        } finally {
            unregisterListener(seq);
        }
    }
    
    protected synchronized void sendNext(final long seq) {
        _currentSequence = null;
        if (_requests.isEmpty()) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(log(seq, "No more commands found"));
            }
            return;
        }

        Request req = _requests.pop();
        if (s_logger.isDebugEnabled()) {
            s_logger.debug(log(req.getSequence(), "Sending now.  is current sequence."));
        }
        try {
            send(req);
        } catch (AgentUnavailableException e) {
            if (s_logger.isDebugEnabled()) {
                s_logger.debug(log(req.getSequence(), "Unable to send the next sequence"));
            }
            cancel(seq);
        }
        _currentSequence = req.getSequence();
    }
    
	public void process(Answer[] answers) {
        //do nothing
	}

    /**
     * sends the request asynchronously.
     * 
     * @param req
     * @throws AgentUnavailableException
     */
    public abstract void send(Request req) throws AgentUnavailableException;
    
    /**
     * Process disconnect.
     * @param state state of the agent.
     */
    public abstract void disconnect(final Status state);
    
    /**
     * Is the agent closed for more commands?
     * @return true if unable to reach agent or false if reachable.
     */
    protected abstract boolean isClosed();
    
    protected class Alarm implements Runnable {
    	long _seq;
    	public Alarm(long seq) {
    		_seq = seq;
    	}
    	
    	@Override
    	public void run() {
    	    try {
        		Listener listener = unregisterListener(_seq);
        		if (listener != null) {
    	    		cancel(_seq);
    	    		listener.processTimeout(_id, _seq);
        		}
    	    } catch (Exception e) {
    	        s_logger.warn("Exception ", e);
    	    }
    	}
    }
}