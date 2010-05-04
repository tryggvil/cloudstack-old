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

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.*;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class TestCaseEngine {
	
	public static final Logger s_logger = Logger.getLogger(TestCaseEngine.class.getName());
	public static final String fileName = "../metadata/adapter.xml";
	public static HashMap<String, String> globalParameters = new HashMap<String, String>();
	protected static HashMap<String, String> _componentMap = new HashMap<String, String>();
	protected static HashMap<String, String> _inputFile = new HashMap<String, String>();
	public static HashMap<String, String> apiCommands = new HashMap<String, String> ();
	
	public static void main (String args[]) {
		
		try {		
			//parse adapter.xml file to get list of tests to execute
			File file = new File (fileName);
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(file);
			doc.getDocumentElement().normalize();
			Element root = doc.getDocumentElement();
			
			
			//set global parameters
			setGlobalParams(root);
			
			//set async property for api commands
			File asyncCommands = new File(globalParameters.get("apicommands"));
			if (file.exists()) {
				Properties pro = new Properties();
				FileInputStream in = new FileInputStream(asyncCommands);
		        pro.load(in);
		        Enumeration en = pro.propertyNames();
		        while (en.hasMoreElements()) {
		        	String key = (String) en.nextElement();
		        	apiCommands.put(key, pro.getProperty(key));
		        }	
			}
			//populate _componentMap
			setComponent(root);
				
			//execute test
			executeTest();   
            	
		} catch (Exception exc) {
			
		}
	}
	
	public static void setGlobalParams (Element rootElement) {
		NodeList globalParam = rootElement.getElementsByTagName("globalparam");
		Element parameter = (Element)globalParam.item(0);
		NodeList paramLst = parameter.getElementsByTagName("param");
		
		for (int i=0; i<paramLst.getLength(); i++) {
			Element paramElement = (Element) paramLst.item(i);
			
			if (paramElement.getNodeType() == Node.ELEMENT_NODE) {
	    		  Element itemElement= (Element) paramElement;
	    		  NodeList itemName = itemElement.getElementsByTagName("name");
	    		  Element itemNameElement = (Element) itemName.item(0);
		    	  NodeList itemVariable = itemElement.getElementsByTagName("variable");
		    	  Element itemVariableElement = (Element) itemVariable.item(0);
		    	  globalParameters.put(itemVariableElement.getTextContent(), itemNameElement.getTextContent());
	    	}
		}
	}
	   
	  public static void setComponent (Element rootElement) {
			NodeList testLst = rootElement.getElementsByTagName("test");
			for (int j=0; j<testLst.getLength(); j++) {
				Element testElement = (Element) testLst.item(j);
				
				if (testElement.getAttribute("run").equals("true")) {
					if (testElement.getNodeType() == Node.ELEMENT_NODE) {
						Element itemElement= (Element) testElement;
  
						//get test case name
						String testCaseName = null;
						NodeList testCaseNameList = itemElement.getElementsByTagName("testname");
						if (testCaseNameList != null) {
							  testCaseName = ((Element)testCaseNameList.item(0)).getTextContent();
						}
  
						//set class name
						NodeList className = itemElement.getElementsByTagName("class");
						String name = ((Element)className.item(0)).getTextContent();
						_componentMap.put(testCaseName, name);
			    		  
						//set input file name
						NodeList inputFileNameLst = itemElement.getElementsByTagName("filename");
						if (inputFileNameLst != null) {
							String inputFileName = ((Element)inputFileNameLst.item(0)).getTextContent();
							_inputFile.put(testCaseName, inputFileName); 
						}
					}
				}
			}			
	  }
	   
	   public static void executeTest(){
		   Set set = _componentMap.entrySet();
		   Iterator it = set.iterator();
		   int error = 0;
		   
		   while (it.hasNext()){
			   Object result = null;
			   Map.Entry me = (Map.Entry)it.next();
			   String key = (String) me.getKey();
			   try {
				   Class c = Class.forName(_componentMap.get(key));
				   Object component = c.newInstance();
				   
	           	   Class[] param = new Class[] { HashMap.class };
	           	   Class[] inputFile = new Class[] { String.class };
	           	   Class[] testCaseName = new Class[] { String.class };
		           
		           s_logger.info("Starting \"" + key + "\" test...\n\n");
      
		           //set global parameters
		           Method setD = c.getMethod("setParam", param);
		           setD.invoke(component, new Object[] { globalParameters});
		           //s_logger.info("parameters are set");
		           
		           //set DB ip address
		           Method setC = c.getMethod("setConn", inputFile);
		           setC.invoke(component, new Object[] { globalParameters.get("dbPassword")});
		           //s_logger.info("db is set");
		           
		           if (_inputFile.get(key) != null ) {
		           	   Method setF = c.getMethod("setInputFile", inputFile);
			           setF.invoke(component, new Object[] { _inputFile.get(key) });
			           //s_logger.info("input file is set");
		           }
		           
		           if (key != null ) {
		           	   Method setF = c.getMethod("setTestCaseName", testCaseName);
			           setF.invoke(component, new Object[] { key });
			           //s_logger.info("test case name is set");
		           }
		              
		           Method exec = c.getMethod("executeTest");
		           result = exec.invoke(component);
		           if (result.toString().equals("false")) {
		        	   error++;
		        	   s_logger.error("FAILURE!!! Test \"" + key + "\" failed\n\n\n");
		           }
		           else {
		        	   s_logger.info("SUCCESS!!! Test \"" + key + "\" passed\n\n\n");
		           }
		           
			   } catch (NoSuchMethodException ex1){
				   s_logger.error(ex1);
				   
			   } catch (Exception ex) {   
				   s_logger.error("error during test execution " + ex);
			   }
		   }
		   
		   if (error != 0) {
			   System.exit(1);
		   }
	   }
}
