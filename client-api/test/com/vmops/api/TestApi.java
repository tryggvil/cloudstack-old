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

package com.vmops.api;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.httpclient.HttpException;
import org.apache.log4j.Logger;

import com.vmops.utils.testcase.Log4jEnabledTestCase;

public class TestApi extends Log4jEnabledTestCase {
	private static final Logger s_logger = Logger.getLogger(TestApi.class.getName());

	public void testLogin() {
		Client client = new Client(true, "localhost", 8080);
		
		try {
			client.login(1, "admin", "password");

			//long jobId = client.createTemplateAsync(6, "Test template", "Description of test template");
			//long jobId = client.startVMAsync(7);
			long jobId = client.deployVMAsync(3, 1, 1,1, 3);
			s_logger.info("job id: " + jobId);
			
			Map<String, String> result;
			while(true) {
				result = client.queryAsyncJobStatus(jobId);
				String jobStatus = result.get("jobstatus");
				if(Integer.parseInt(jobStatus) == 0) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
				} else {
					break;
				}
			}
			s_logger.info("result: " + result);
			
		} catch (HttpException e) {
			s_logger.error("Exception: ", e);
		} catch (IOException e) {
			s_logger.error("Exception: ", e);
		}
	}
}
