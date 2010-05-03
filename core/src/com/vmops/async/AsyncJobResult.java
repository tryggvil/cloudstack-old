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

package com.vmops.async;

import com.google.gson.Gson;
import com.vmops.serializer.GsonHelper;

public class AsyncJobResult {
	public static final int STATUS_IN_PROGRESS = 0;
	public static final int STATUS_SUCCEEDED = 1;
	public static final int STATUS_FAILED = 2;
	
	private String cmdOriginator;
	private long jobId;
	private int jobStatus;
	private int processStatus;
	private int resultCode;
	private String result;

	public AsyncJobResult(long jobId) {
		this.jobId = jobId;
		jobStatus = STATUS_IN_PROGRESS;
		processStatus = 0;
		resultCode = 0;
		result = "";
	}
	
	public String getCmdOriginator() {
		return cmdOriginator;
	}
	
	public void setCmdOriginator(String cmdOriginator) {
		this.cmdOriginator = cmdOriginator;
	}
	
	public long getJobId() {
		return jobId;
	}
	
	public void setJobId(long jobId) {
		this.jobId = jobId;
	}
	
	public int getJobStatus() {
		return jobStatus;
	}
	
	public void setJobStatus(int jobStatus) {
		this.jobStatus = jobStatus;
	}
	
	public int getProcessStatus() {
		return processStatus;
	}
	
	public void setProcessStatus(int processStatus) {
		this.processStatus = processStatus;
	}
	
	public int getResultCode() {
		return resultCode;
	}
	
	public void setResultCode(int resultCode) {
		this.resultCode = resultCode;
	}
	
	public String getResult() {
		return result;
	}
	
	public void setResult(String result) {
		this.result = result;
	}
	
	public Object getResultObject() {
		return AsyncJobResult.fromResultString(result);
	}
	
	public void setResultObject(Object result) {
		Class<?> clz = result.getClass();
    	Gson gson = GsonHelper.getBuilder().create();
    	
		this.result = clz.getName() + "/" + gson.toJson(result);
	}
	
	public static String toResultString(Object result) {
		Class<?> clz = result.getClass();
    	Gson gson = GsonHelper.getBuilder().create();
    	
		return clz.getName() + "/" + gson.toJson(result);
	}
	
	public static Object fromResultString(String result) {
		if(result != null && !result.isEmpty()) {
			int seperatorPos = result.indexOf('/');
			if(seperatorPos < 0)
				return null;
			
			String clzName = result.substring(0, seperatorPos);
			String content = result.substring(seperatorPos + 1);
			Class<?> clz;
			try {
				clz = Class.forName(clzName);
			} catch (ClassNotFoundException e) {
				return null;
			}
			
	    	Gson gson = GsonHelper.getBuilder().create();
	    	return gson.fromJson(content, clz);
		}
		return null;
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("AsyncJobResult {jobId:").append(getJobId());
		sb.append(", jobStatus: ").append(getJobStatus());
		sb.append(", processStatus: ").append(getProcessStatus());
		sb.append(", resultCode: ").append(getResultCode());
		sb.append(", result: ").append(result);
		sb.append("}");
		return sb.toString();
	}
}
