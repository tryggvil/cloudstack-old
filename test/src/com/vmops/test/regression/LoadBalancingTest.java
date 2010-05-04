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

package com.vmops.test.regression;

import java.util.HashMap;

import org.apache.log4j.Logger;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class LoadBalancingTest extends TestCase{

	public static final Logger s_logger = Logger.getLogger(LoadBalancingTest.class.getName());
	
	public LoadBalancingTest(){
		this.setClient();
		this.setParam(new HashMap<String, String>());
	}
	
	public boolean executeTest(){
		
		int error=0;	
		Element rootElement = this.getInputFile().getDocumentElement();
		NodeList commandLst = rootElement.getElementsByTagName("command");
		
		//Analyze each command, send request and build the array list of api commands
		for (int i=0; i<commandLst.getLength(); i++) {
			
			Node fstNode = commandLst.item(i);
		    Element fstElmnt = (Element) fstNode;
		    
		    //new command
			ApiCommand api = new ApiCommand(fstElmnt, this.getParam());
			
			//send a command
			api.sendCommand(this.getClient());
			
			
			//verify the response of the command
			if ((api.getError() == true) && (api.getResponseCode() == 200)) {
				s_logger.error("Test case " + api.getTestCaseId() + " failed. Command that was supposed to fail, passed. The command was sent with the following url " + api.getUrl());
				error++;
			}
			else if (!(api.getError() == true) && (api.getResponseCode() == 200)) {
				//verify if response is suppposed to be empty
				if (api.getEmpty() == true) {
					if (api.isEmpty() == true) {
						s_logger.info("Test case " + api.getTestCaseId() + " passed");
					}
					else {
						s_logger.error("Test case " + api.getTestCaseId() + " failed. Empty response was expected. Command was sent with url " + api.getUrl());
					}
				} else {
					if (api.isEmpty() != false) 
						s_logger.error("Test case " + api.getTestCaseId() + " failed. Non-empty response was expected. Command was sent with url " + api.getUrl());
					else {
						//set parameters for the future use
						if (api.setParam(this.getParam()) == false) {
							s_logger.error("Exiting the test...Command " + api.getName() + " didn't return parameters needed for the future use. The command was sent with url " + api.getUrl());
							return false;
						}
						else if (api.getTestCaseId() != null){
							s_logger.info("Test case " + api.getTestCaseId() + " passed");
						}
					}	
				}
			}
			else if (!(api.getError() == true) && (api.getResponseCode() != 200)) {
				s_logger.error("Test case " + api.getTestCaseId() + " failed. Command was sent with url  " + api.getUrl());
				if (api.getRequired() == true) {
					s_logger.info("The command is required for the future use, so exiging");
					return false;
				}
				error++;
			}
			else if (api.getTestCaseId() != null){
					s_logger.info("Test case " + api.getTestCaseId() +  " passed");
			
			}		
		}
		
//		//Try to create portForwarding rule for all available private/public ports
//		ArrayList<String> port = new ArrayList<String>();
//		for (int i=1; i<65536; i++){
//			port.add(Integer.toString(i));
//		}
//		
//		//try all public ports	
//		for (String portValue : port) {
//	        try {
//	        	String url = this.getHost() + ":8096/?command=createOrUpdateLoadBalancerRule&account=" + this.getParam().get("accountname") + "&publicip=" + this.getParam().get("boundaryip") + 
//	        	"&privateip=" + this.getParam().get("vmipaddress") + "&privateport=22&protocol=tcp&publicport=" + portValue;
//	        	HttpClient client = new HttpClient();
//				HttpMethod method = new GetMethod(url);
//				int responseCode = client.executeMethod(method);
//				if (responseCode != 200 ) {
//					error++;
//					s_logger.error("Can't create LB rule for the public port " + portValue + ". Request was sent with url " + url);
//				}	
//	        }catch (Exception ex) {
//	        	s_logger.error(ex);
//	        }
//		}
//		
//		//try all private ports
//		for (String portValue : port) {
//	        try {
//	        	String url = this.getHost() + ":8096/?command=createOrUpdateLoadBalancerRule&account=" + this.getParam().get("accountname") + "&publicip=" + this.getParam().get("boundaryip") + 
//	        	"&privateip=" + this.getParam().get("vmipaddress") + "&publicport=22&protocol=tcp&privateport=" + portValue;
//	        	HttpClient client = new HttpClient();
//				HttpMethod method = new GetMethod(url);
//				int responseCode = client.executeMethod(method);
//				if (responseCode != 200 ) {
//					error++;
//					s_logger.error("Can't create LB rule for the private port " + portValue + ". Request was sent with url " + url);
//				}	
//	        }catch (Exception ex) {
//	        	s_logger.error(ex);
//	        }
//		}

		
		if (error != 0)
			return false;
		else
			return true;
	}

}
