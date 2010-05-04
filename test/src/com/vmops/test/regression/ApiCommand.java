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
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.vmops.test.utils.UtilsForTest;

public class ApiCommand{
	private TreeMap<String, String> urlParam;
	private HashMap<String, String> verifyParam;
	private HashMap<String, String> setParam;
	private int responseCode;
	private Element responseBody;
	private String name;
	private String url;
	private String host;
	private boolean list;
	private Element listName;
	private Element listId;
	private Boolean required = false;
	private Boolean error;
	private Boolean empty;
	//private Boolean async;
	private Boolean userCommand;
	private String testCaseId;
	
	public static final Logger s_logger = Logger.getLogger(ApiCommand.class.getName());
	//public static HashMap<String, String> param = new HashMap<String, String> ();

	
	public ApiCommand(Element fstElmnt, HashMap<String, String> param){
		this.setName(fstElmnt);
		this.setTestCaseId(fstElmnt);
		this.setError(fstElmnt);
		this.setEmpty(fstElmnt);
		this.setUserCommand(fstElmnt);
		this.verifyParam = new HashMap<String, String>();
		this.setParam = new HashMap<String, String>();
		this.setUrlParam(fstElmnt, param);
		this.setVerifyParam(fstElmnt, param);
		this.setHost("http://" + param.get("hostip"));
		this.setUrl(param);
	}
	
	//================FOLLOWING METHODS USE INPUT XML FILE=======================//
	//Set/Get command name
	public void setName(Element fstElmnt) {
	    NodeList commandName = fstElmnt.getElementsByTagName("name");
	    Element commandElmnt = (Element) commandName.item(0);
	    NodeList commandNm = commandElmnt.getChildNodes();
	    this.name = (((Node) commandNm.item(0)).getNodeValue());
	}
	
	public void setTestCaseId(Element fstElmnt) {
		NodeList commandName = fstElmnt.getElementsByTagName("testcase");
		if (commandName.getLength() != 0) {
			Element commandElmnt = (Element) commandName.item(0);
		    NodeList commandNm = commandElmnt.getChildNodes();
		    this.testCaseId = ((Node) commandNm.item(0)).getNodeValue();
		} 
		else {
			this.testCaseId = null;
		}
	}
	
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setError(Element fstElmnt) {
		NodeList commandName = fstElmnt.getElementsByTagName("error");
		if (commandName.getLength() != 0) {
			Element commandElmnt = (Element) commandName.item(0);
		    NodeList commandNm = commandElmnt.getChildNodes();
		    if (((Node) commandNm.item(0)).getNodeValue().equals("true")) {
		    	this.error = true;
		    }
		    else {
		    	this.error = false;
		    }
		} 
		else {
			this.error = false;
		}
	}
	
	
	public void setEmpty(Element fstElmnt) {
		NodeList commandName = fstElmnt.getElementsByTagName("empty");
		if (commandName.getLength() != 0) {
			Element commandElmnt = (Element) commandName.item(0);
		    NodeList commandNm = commandElmnt.getChildNodes();
		    if (((Node) commandNm.item(0)).getNodeValue().equals("true")) {
		    	this.empty = true;
		    }
		    else {
		    	this.empty = false;
		    }
		} 
		else {
			this.empty = false;
		}
	}
	
	public void setUserCommand(Element fstElmnt) {
		NodeList commandName = fstElmnt.getElementsByTagName("usercommand");
		if (commandName.getLength() != 0) {
			Element commandElmnt = (Element) commandName.item(0);
		    NodeList commandNm = commandElmnt.getChildNodes();
		    if (((Node) commandNm.item(0)).getNodeValue().equals("true")) {
		    	this.userCommand = true;
		    }
		    else {
		    	this.userCommand = false;
		    }
		} 
		else {
			this.userCommand = false;
		}
	}
	
	public Boolean getError() {
		return error;
	}
	
	public Boolean getEmpty() {
		return empty;
	}
	
//	public Boolean getAsync() {
//		return async;
//	}

	public String getName() {
		return name;
	}
	
	public String getTestCaseId() {
		return testCaseId;
	}
	
	public Boolean getRequired() {
		return required;
	}

	public void setUrlParam(Element fstElmnt, HashMap<String, String> param) {
		this.urlParam = new TreeMap<String, String>();
		NodeList parameterLst= fstElmnt.getElementsByTagName("parameters");
	      if (parameterLst != null ) {
	    	  for (int j=0; j<parameterLst.getLength(); j++) {
			    	 Element parameterElement = (Element) parameterLst.item(j);
			    	 NodeList itemLst = parameterElement.getElementsByTagName("item");
			    	 for (int k=0; k<itemLst.getLength(); k++) {
			    		 Node item=itemLst.item(k);
			    		 if (item.getNodeType() == Node.ELEMENT_NODE) {
				    		  Element itemElement= (Element) item;
				    		  NodeList itemName = itemElement.getElementsByTagName("name");
				    		  Element itemNameElement = (Element) itemName.item(0);
				    		  
				    		  //get value
				    		  Element itemValueElement = null;
				    		  if ((itemElement.getElementsByTagName("value") != null) && (itemElement.getElementsByTagName("value").getLength() != 0)) {
				    			  NodeList itemValue = itemElement.getElementsByTagName("value");
				    			  itemValueElement = (Element) itemValue.item(0);
				    		  }
				    		  
				    		  Element itemParamElement = null;
				    		  //getparam
				    		  if ((itemElement.getElementsByTagName("param") != null) && (itemElement.getElementsByTagName("param").getLength() != 0)) {
				    			  NodeList itemParam = itemElement.getElementsByTagName("param");
				    			  itemParamElement = (Element) itemParam.item(0);
				    		  }
				    		  
				    		  if ((itemElement.getAttribute("getparam").equals("true")) && (itemParamElement != null)) {
				    			  this.urlParam.put(itemNameElement.getTextContent(), param.get(itemParamElement.getTextContent()));
				    		  }
				    		  else if (itemValueElement != null){
				    			  this.urlParam.put(itemNameElement.getTextContent(), itemValueElement.getTextContent());
				    		  } else if (itemElement.getAttribute("random").equals("true")) {
				    			  Random ran = new Random();
				    			  String randomString = Math.abs(ran.nextInt())+ "-randomName";
				    			  this.urlParam.put(itemNameElement.getTextContent(), randomString);
				    			  if ((itemElement.getAttribute("setparam").equals("true")) && (itemParamElement != null)) {
						    		  param.put(itemParamElement.getTextContent(), randomString);
				    			  }
				    		  } else if (itemElement.getAttribute("randomnumber").equals("true")) {
				    			  Random ran = new Random();
				    			  Integer randomNumber = (Integer)Math.abs(ran.nextInt(65535));
				    			  this.urlParam.put(itemNameElement.getTextContent(), randomNumber.toString());
				    			  if ((itemElement.getAttribute("setparam").equals("true")) && (itemParamElement != null)) {
						    		  param.put(itemParamElement.getTextContent(), randomNumber.toString());
				    			  }
				    		  }
				    		  
				    	  }
			    	 }
	    	  	}
	      }
	}
	
	
	//Set command URL
	public void setUrl(HashMap<String, String> param) { 
		if ((param.get("apikey") == null) || (param.get("secretkey")==null) || (this.userCommand == false)) {
			String temp = this.host + ":8096/?command="+ this.name;
			Set c = this.urlParam.entrySet();
			Iterator it = c.iterator();
			while (it.hasNext()) {
				Map.Entry me = (Map.Entry)it.next();
				String key = (String) me.getKey();
				String value = (String) me.getValue();
				try {
					temp = temp + "&" + key + "=" + URLEncoder.encode(value, "UTF-8");
				} catch (Exception ex) {
				s_logger.error("Unable to set parameter " + key + " for the command " + this.getName());
			}
			}
			this.url = temp;
		}
		else if (userCommand == true) {
			String apiKey = param.get("apikey");
			String secretKey = param.get("secretkey"); 
			
			String temp = "";
			this.urlParam.put("apikey", apiKey);
			this.urlParam.put("command", this.name);
			
			//sort url hash map by key
			Set c = this.urlParam.entrySet();
			Iterator it = c.iterator();
			while (it.hasNext()) {
				Map.Entry me = (Map.Entry)it.next();
				String key = (String) me.getKey();
				String value = (String) me.getValue();
				try {
					temp = temp + key + "=" + URLEncoder.encode(value, "UTF-8") + "&";
				} catch (Exception ex) {
					s_logger.error("Unable to set parameter " + value + " for the command " + this.getName());
				}
				
			}
			temp = temp.substring(0, temp.length()-1 );
			String requestToSign = temp.toLowerCase();
			
			String signature = UtilsForTest.signRequest(requestToSign, secretKey);
			String encodedSignature = "";
			try {
				encodedSignature = URLEncoder.encode(signature, "UTF-8");
			} catch (Exception ex) {
				s_logger.error(ex);
			}
			this.url = this.host + ":8080/client/api/?" + temp + "&signature=" + encodedSignature;
		}
	}

	public void setVerifyParam(Element fstElmnt, HashMap<String, String> param) {
		NodeList returnLst= fstElmnt.getElementsByTagName("returnvalue");
      if (returnLst != null ) {
    	  for (int m=0; m<returnLst.getLength(); m++) {
    		  Element returnElement = (Element) returnLst.item(m);
    		  if (returnElement.getAttribute("list").equals("true")) {
    			  this.list = true;
    			  NodeList elementLst = returnElement.getElementsByTagName("element");
    			  this.listId = (Element) elementLst.item(0);		  
    			  NodeList elementName = returnElement.getElementsByTagName("name");
    			  this.listName = (Element) elementName.item(0);
    		  }
    		  else {
    			  this.list = false;
    		  }

    		  NodeList itemLst1 = returnElement.getElementsByTagName("item");
    		  if (itemLst1 != null) {
    			  for (int n=0; n<itemLst1.getLength(); n++) {
			    		 Node item=itemLst1.item(n);
			    		 if (item.getNodeType() == Node.ELEMENT_NODE) {
				    		  Element itemElement= (Element) item;
				    		  //get parameter name
				    		  NodeList itemName = itemElement.getElementsByTagName("name");
				    		  Element itemNameElement = (Element) itemName.item(0);
				    		 
				    		  //Get parameters for future use
				    		  if (itemElement.getAttribute("setparam").equals("true")) {
					    		  NodeList itemVariable = itemElement.getElementsByTagName("param");
					    		  Element itemVariableElement = (Element) itemVariable.item(0);
					    		  setParam.put(itemVariableElement.getTextContent(), itemNameElement.getTextContent());
					    		  this.required = true;
				    		  } else if (itemElement.getAttribute("getparam").equals("true")){
			    				  NodeList itemVariable = itemElement.getElementsByTagName("param");
			    				  Element itemVariableElement = (Element) itemVariable.item(0);
			    				  this.verifyParam.put(itemNameElement.getTextContent(), param.get(itemVariableElement.getTextContent())); 
			    			  } else if ((itemElement.getElementsByTagName("value") != null) && (itemElement.getElementsByTagName("value").getLength() != 0)) {
			    				  NodeList itemVariable = itemElement.getElementsByTagName("value");
				    			  Element itemVariableElement = (Element) itemVariable.item(0);
				    			  this.verifyParam.put(itemNameElement.getTextContent(), itemVariableElement.getTextContent());
				    		  } else {
				    			  this.verifyParam.put(itemNameElement.getTextContent(), "no value"); 
				    		  }
				    	  }
			    	 }
    		  }
    	  }
      }
	}
	
	
	public int getResponseCode() {
		return responseCode;
	}
	
	//Send api command to the server
	public void sendCommand(HttpClient client){
//		s_logger.info("url is " + this.url);
		HttpMethod method = new GetMethod(this.url);
		try {
			this.responseCode = client.executeMethod(method);
			if (this.responseCode == 200) {
				InputStream is = method.getResponseBodyAsStream();
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document doc = builder.parse(is);
				doc.getDocumentElement().normalize();
				String async = TestCaseEngine.apiCommands.get(this.getName());
				
				if (async.equals("no")) {
					this.responseBody = doc.getDocumentElement();
				}
				else if (async.equals("yes")){
					//get async job result
					Element jobTag = (Element) doc.getDocumentElement().getElementsByTagName("jobid").item(0);
					String jobId = jobTag.getTextContent();
					Element responseBodyAsyncEl = queryAsyncJobResult(jobId);
					if (responseBodyAsyncEl == null) {
						s_logger.error("Can't get a async result");
					}
					else {
						this.responseBody = responseBodyAsyncEl;
						//get status of the job
						Element jobStatusTag = (Element)responseBodyAsyncEl.getElementsByTagName("jobstatus").item(0);
						String jobStatus = jobStatusTag.getTextContent();
						if (!jobStatus.equals("1")) { // Need to modify with different error codes for jobAsync results
							//set fake response code by now
							this.responseCode = 400;
						}			
					}
				}
				method.releaseConnection();
			}
		} catch (Exception ex) {
			s_logger.error("Command " + url + " failed");
		}
		
	}

	//verify if response is empty (contains only root element)
	public boolean isEmpty(){
		boolean result = false;
		if (!this.responseBody.hasChildNodes())
			result = true;
		return result;
	}
	
	
	//================FOLLOWING METHODS USE RETURN XML FILE=======================//
	
	public boolean setParam (HashMap<String, String> param) {
		if (this.responseBody == null) {
			s_logger.error("Response body is empty");
			return false;
		}
		Boolean result = true;
		if (this.list == false) {
			Set set = this.setParam.entrySet();
			Iterator it = set.iterator();
			
			while (it.hasNext()) {
				Map.Entry me = (Map.Entry)it.next();
				String key = (String) me.getKey();
				String value = (String) me.getValue();
							//set parameters needed for the future use
							NodeList itemName = this.responseBody.getElementsByTagName(value);
							if ((itemName != null) && (itemName.getLength() != 0)) {
				    			 Element itemNameElement = (Element) itemName.item(0);
					    		 param.put(key, itemNameElement.getTextContent());
					    		 //s_logger.info("putting parameter " + key + " with value " + itemNameElement.getTextContent());
				    		 }	
							else {
								s_logger.error("Following return parameter is missing: " + value);
								result = false;
							}		
			}
		} 
		else {
			Set set = this.setParam.entrySet();
			Iterator it = set.iterator();
			NodeList returnLst = this.responseBody.getElementsByTagName(this.listName.getTextContent());
			Node requiredNode = returnLst.item(Integer.parseInt(this.listId.getTextContent()));

			if (requiredNode.getNodeType() == Node.ELEMENT_NODE) {
				  Element fstElmnt = (Element) requiredNode;
				  
				  while (it.hasNext()) {
						Map.Entry me = (Map.Entry)it.next();
						String key = (String) me.getKey();
						String value = (String) me.getValue();
						NodeList itemName = fstElmnt.getElementsByTagName(value);
						if ((itemName != null) && (itemName.getLength() != 0)) {
			    			 Element itemNameElement = (Element) itemName.item(0);
				    		 param.put(key, itemNameElement.getTextContent());
			    		}	
						else {
							s_logger.error("Following return parameter is missing: " + value);
							result = false;
						}
					}
			}
		}
		return result;
	}
	
	
	public String getUrl() {
		return url;
	}

	public boolean verifyParam() {
		boolean result = true;
		if (this.list == false) {
			Set set = verifyParam.entrySet();
			Iterator it = set.iterator();
			
			while (it.hasNext()) {
				Map.Entry me = (Map.Entry)it.next();
				String key = (String) me.getKey();
				String value = (String) me.getValue();
				if (value == null) {
					s_logger.error("Parameter " + key + " is missing in the list of global parameters");
					return false;
				}
							NodeList itemName = this.responseBody.getElementsByTagName(key);
							if ((itemName.getLength() != 0) && (itemName != null)) {
				    			 Element itemNameElement = (Element) itemName.item(0);
				    			 if ( !(verifyParam.get(key).equals("no value")) && !(itemNameElement.getTextContent().equals(verifyParam.get(key)))){
				    				 s_logger.error("Incorrect value for the following tag: " + key + ". Expected value is " + verifyParam.get(key) + " while actual value is " + itemNameElement.getTextContent());
				    				 result = false;
				    			 } 
				    		 }	
							else {
								s_logger.error("Following xml element is missing in the response: " + key);
								result=false;
							}
			}
		}
		//for multiple elements
		else {
			Set set = verifyParam.entrySet();
			Iterator it = set.iterator();
			//get list element specified by id
			NodeList returnLst = this.responseBody.getElementsByTagName(this.listName.getTextContent());
			Node requiredNode = returnLst.item(Integer.parseInt(this.listId.getTextContent()));

			if (requiredNode.getNodeType() == Node.ELEMENT_NODE) {
				  Element fstElmnt = (Element) requiredNode;
				  
				  while (it.hasNext()) {
						Map.Entry me = (Map.Entry)it.next();
						String key = (String) me.getKey();
						String value = (String) me.getValue();
						if (value == null) {
							s_logger.error("Parameter " + key + " is missing in the list of global parameters");
							return false;
						}
						NodeList itemName = fstElmnt.getElementsByTagName(key);
						if ((itemName.getLength() != 0) && (itemName != null)) {
			    			 Element itemNameElement = (Element) itemName.item(0);
			    			 if ( !(verifyParam.get(key).equals("no value")) && !(itemNameElement.getTextContent().equals(verifyParam.get(key)))){
			    				 s_logger.error("Incorrect value for the following tag: " + key + ". Expected value is " + verifyParam.get(key) + " while actual value is " + itemNameElement.getTextContent());
			    				 result = false;
			    			 } 
			    		 }	
						else {
							s_logger.error("Following xml element is missing in the response: " + key);
							result = false;
						}
					}
			}
		}
		return result;
	}
	
	public static boolean verifyEvents (String fileName, String level, String host, String account) {
		boolean result=false;
		HashMap<String, Integer> expectedEvents = new HashMap<String, Integer> ();
		HashMap<String, Integer> actualEvents = new HashMap<String, Integer> ();
		String key = "";
		
		File file = new File(fileName);
		if (file.exists()) {
			Properties pro = new Properties();
			try {
				//get expected events
				FileInputStream in = new FileInputStream(file);
		        pro.load(in);
		        Enumeration en = pro.propertyNames();
		        while (en.hasMoreElements()) {
		        	key = (String) en.nextElement();
		        	expectedEvents.put(key, Integer.parseInt(pro.getProperty(key)));
		        }	
		        
		        //get actual events	
	        	String url = host + "/?command=listEvents&account=" + account + "&level=" + level;
	        	HttpClient client = new HttpClient();
				HttpMethod method = new GetMethod(url);
				int responseCode = client.executeMethod(method);
				if (responseCode == 200 ) {
					InputStream is = method.getResponseBodyAsStream();
					ArrayList<HashMap<String, String>> eventValues = UtilsForTest.parseMulXML(
							is, new String[] { "event" });
					
					for (int i=0; i< eventValues.size(); i++) {
						HashMap<String, String> element = eventValues.get(i);
						if (element.get("level").equals(level)) {
							if (actualEvents.containsKey(element.get("type")) == true){
								actualEvents.put(element.get("type"), actualEvents.get(element.get("type"))+1);
							}
							else {
								actualEvents.put(element.get("type"), 1);
							}
						}		
					}
				}	
				method.releaseConnection();
				
				//compare actual events with expected events
				
				//compare expected result and actual result
				Iterator iterator = expectedEvents.keySet().iterator();
				Integer expected;
				Integer actual;
				int fail=0;
				while (iterator.hasNext()) {
					expected=null;
					actual=null;
					String type = iterator.next().toString();
					expected = expectedEvents.get(type);
					actual = actualEvents.get(type);
					if (actual == null ) {
						s_logger.error("Event of type " + type + " and level " + level + " is missing in the listEvents response. Expected number of these events is " + expected);
						fail++;
					}
					else if (expected.compareTo(actual) != 0){
							fail++;
							s_logger.info("Amount of events of  " + type + " type and level " + level + " is incorrect. Expected number of these events is " + expected + ", actual number is " + actual);
					}
				}
						
				if (fail == 0) {
					result = true;
				}
		        
			} catch (Exception ex) {
				s_logger.error(ex);
			}	
		}else {
			s_logger.info("File " + fileName + " not found");
		}
		return result;
	}
	
	
	public static boolean verifyEvents (HashMap <String, Integer> expectedEvents, String level, String host, String parameters) {
		boolean result=false;
		HashMap<String, Integer> actualEvents = new HashMap<String, Integer> ();
		String key = "";
		
		        try {
		        	//get actual events	
		        	String url = host + "/?command=listEvents&" + parameters;
		        	HttpClient client = new HttpClient();
					HttpMethod method = new GetMethod(url);
					int responseCode = client.executeMethod(method);
					if (responseCode == 200 ) {
						InputStream is = method.getResponseBodyAsStream();
						ArrayList<HashMap<String, String>> eventValues = UtilsForTest.parseMulXML(
								is, new String[] { "event" });
						
						for (int i=0; i< eventValues.size(); i++) {
							HashMap<String, String> element = eventValues.get(i);
							if (element.get("level").equals(level)) {
								if (actualEvents.containsKey(element.get("type")) == true){
									actualEvents.put(element.get("type"), actualEvents.get(element.get("type"))+1);
								}
								else {
									actualEvents.put(element.get("type"), 1);
								}
							}		
						}
					}
					method.releaseConnection();
		        }catch (Exception ex) {
		        	s_logger.error(ex);
		        }
		        	
				//compare actual events with expected events
				Iterator iterator = expectedEvents.keySet().iterator();
				Integer expected;
				Integer actual;
				int fail=0;
				while (iterator.hasNext()) {
					expected=null;
					actual=null;
					String type = iterator.next().toString();
					expected = expectedEvents.get(type);
					actual = actualEvents.get(type);
					if (actual == null ) {
						s_logger.error("Event of type " + type + " and level " + level + " is missing in the listEvents response. Expected number of these events is " + expected);
						fail++;
					}
					else if (expected.compareTo(actual) != 0){
							fail++;
							s_logger.info("Amount of events of  " + type + " type and level " + level + " is incorrect. Expected number of these events is " + expected + ", actual number is " + actual);
					}
				}
						
				if (fail == 0) {
					result = true;
				}
		       
		
		return result;
	}
	
	
	public Element queryAsyncJobResult (String jobId) {
		Element returnBody = null;
		int code = 400;
		String resultUrl = this.host + ":8096/?command=queryAsyncJobResult&jobid=" + jobId;
		//s_logger.info("Sending request with url " + resultUrl);
		HttpClient client = new HttpClient();
		HttpMethod method = new GetMethod(resultUrl);
		
		while (true) {
			try {
				    code = client.executeMethod(method);
				    if (code == 200) {
				    	InputStream is = method.getResponseBodyAsStream();
						DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
						DocumentBuilder builder = factory.newDocumentBuilder();
						Document doc = builder.parse(is);
						doc.getDocumentElement().normalize();
						returnBody = doc.getDocumentElement();
						Element jobStatusTag = (Element) returnBody.getElementsByTagName("jobstatus").item(0);
						String jobStatus = jobStatusTag.getTextContent();
						if(jobStatus.equals("0")) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
							}
						} else {
							break;
						}
						method.releaseConnection();
				    }
				    else {
				    	s_logger.error("Error during queryJobAsync. Error code is " + code);
				    	this.responseCode = code;
				    	return null;
				    }
					
					
			} catch (Exception ex) {
				s_logger.error(ex);
			}
		}
		return returnBody;
	} 
	
	
}
