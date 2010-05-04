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

package com.vmops.async.executor;

import com.vmops.agent.api.Answer;
import com.vmops.async.BaseAsyncJobExecutor;

public abstract class VMOperationExecutor extends BaseAsyncJobExecutor {
	public abstract void processAnswer(VMOperationListener listener, long agentId, long seq, Answer answer);
	public abstract void processDisconnect(VMOperationListener listener, long agentId);
	public abstract void processTimeout(VMOperationListener listener, long agentId, long seq);
}
