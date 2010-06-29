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

package com.cloud.api.commands;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.cloud.api.BaseCmd;
import com.cloud.api.ServerApiException;
import com.cloud.async.AsyncJobResult;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.serializer.SerializerHelper;
import com.cloud.utils.Pair;

public class QueryAsyncJobResultCmd extends BaseCmd {
    public static final Logger s_logger = Logger.getLogger(QueryAsyncJobResultCmd.class.getName());

    private static final String s_name = "queryasyncjobresultresponse";
    private static final List<Pair<Enum, Boolean>> s_properties = new ArrayList<Pair<Enum, Boolean>>();

    static {
        s_properties.add(new Pair<Enum, Boolean>(BaseCmd.Properties.JOB_ID, Boolean.TRUE));
    }

    public String getName() {
        return s_name;
    }
    
    public List<Pair<Enum, Boolean>> getProperties() {
        return s_properties;
    }

    @Override
    public List<Pair<String, Object>> execute(Map<String, Object> params) {
        Long jobId = (Long)params.get(BaseCmd.Properties.JOB_ID.getName());
        AsyncJobResult result;
        
		try {
			result = getManagementServer().queryAsyncJobResult(jobId);
		} catch (PermissionDeniedException e) {
			throw new ServerApiException(BaseCmd.ACCOUNT_ERROR, "Permission denied");
		}
        
        List<Pair<String, Object>> returnValues = new ArrayList<Pair<String, Object>>();
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_ID.getName(), jobId));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_STATUS.getName(), Integer.valueOf(result.getJobStatus())));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_PROCESS_STATUS.getName(), Integer.valueOf(result.getProcessStatus())));
        returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_RESULT_CODE.getName(), Integer.valueOf(result.getResultCode())));
        
        Object resultObject = result.getResultObject();
        if(resultObject != null) {

            Class<?> clz = resultObject.getClass();
            if(clz.isPrimitive() || clz.getSuperclass() == Number.class || clz == String.class || clz == Date.class) {
                returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_RESULT_TYPE.getName(), "text"));
                SerializerHelper.appendPairList(returnValues, resultObject, BaseCmd.Properties.JOB_RESULT.getName());
            } else {
                returnValues.add(new Pair<String, Object>(BaseCmd.Properties.JOB_RESULT_TYPE.getName(), "object"));

    	        if(result.getCmdOriginator() != null && !result.getCmdOriginator().isEmpty()) {
    	        	List<Pair<String, Object>> resultValues = new ArrayList<Pair<String, Object>>();
    	            SerializerHelper.appendPairList(resultValues, resultObject, BaseCmd.Properties.JOB_RESULT.getName());
    	            returnValues.add(new Pair<String, Object>(result.getCmdOriginator(), new Object[] { resultValues } ));
    	        }
            }
        } 
        return returnValues;
    }
}
