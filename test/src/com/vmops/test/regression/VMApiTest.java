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


public class VMApiTest extends TestCase{
public static final Logger s_logger = Logger.getLogger(VMApiTest.class.getName());
	
	
	public VMApiTest(){
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
				//set parameters for the future use
				if (api.setParam(this.getParam()) == false) {
					s_logger.error("Exiting the test...Command " + api.getName() + " didn't return parameters needed for the future use. The command was sent with url " + api.getUrl());
					return false;
				}
				else if (api.getTestCaseId() != null){
					s_logger.info("Test case " + api.getTestCaseId() + " passed");
				}
			}
			else if (!(api.getError() == true) && (api.getResponseCode() != 200)) {
				s_logger.error("Test case  " + api.getTestCaseId() + " failed with an error code " + api.getResponseCode() + " . Command was sent with url  " + api.getUrl());
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
			if (error != 0)
				return false;
			else
				return true;
	}
}
