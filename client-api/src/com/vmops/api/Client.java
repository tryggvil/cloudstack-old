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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import com.vmops.utils.encoding.Base64;
import com.vmops.utils.exception.VmopsRuntimeException;

public class Client {
	private static final Logger s_logger = Logger.getLogger(Client.class.getName());
	
	private String apiServerUrl;
	private String apiKey;
	private String secretKey;
	private String jsessionId;
	private boolean webSession;
	
	public Client(boolean webSession, String serviceUrl) {
		this.webSession = webSession;
		apiServerUrl = serviceUrl;
	}
	
	public Client(boolean webSession, String host, int port) {
		this(webSession, host, port, false);
	}
	
	public Client(boolean webSession, String host, int port, boolean useSsl) {
		this(webSession, host, port, useSsl, null);
	}
	
	public Client(boolean webSession, String host, int port, boolean useSsl, String urlRoot) {
		this.webSession = webSession;
		
		apiServerUrl = (useSsl ? "https://" : "http://") + host;
		if(!(useSsl && port == 443 || !useSsl && port == 80))
			apiServerUrl += ":" + port;
		apiServerUrl +=	(urlRoot != null ? urlRoot : "/client/api");

		if(s_logger.isDebugEnabled())
			s_logger.debug("Init client object with apiServerUrl: " + apiServerUrl);
	}
	
	//
	// API methods
	//
    public boolean login(long domainId, String username, String password) throws HttpException, IOException {
    	
    	Map<String, Object> params = new HashMap<String, Object>();
    	params.put("domainid", 1L);
    	params.put("username", URLEncoder.encode(username, "UTF-8"));
    	params.put("password", URLEncoder.encode(password, "UTF-8"));
    	
		s_logger.info("login: " + username);

		if(webSession) {
			HttpClient client = new HttpClient();
			HttpMethod method = prepare("login", params);
			int responseCode = client.executeMethod(method);
			if (responseCode == 200) {
				InputStream is = method.getResponseBodyAsStream();
				Header cookieHeader = method.getResponseHeader("Set-Cookie");
				if(cookieHeader != null) {
					String[] cookieContent = cookieHeader.getValue().split(";");
					jsessionId = cookieContent[0].split("=")[1];
					s_logger.info("Jession id : " + jsessionId);
				}
				
				Map<String, String> requestKeyValues = getSingleValueFromXML(is,
					new String[] { "description" });
				String description = requestKeyValues.get("description");
				if(description != null && description.equalsIgnoreCase("success")) {
					if(s_logger.isDebugEnabled())
						s_logger.debug("login succeeded");
					return true;
				}
			} else  {
				s_logger.error("registration failed with error code: " + responseCode);
			} 
			s_logger.debug("login failed");
			return false;
		} else {
			HttpClient client = new HttpClient();
			HttpMethod method = prepare("register", params);
			int responseCode = client.executeMethod(method);
			if (responseCode == 200) {
				InputStream is = method.getResponseBodyAsStream();
				Map<String, String> requestKeyValues = getSingleValueFromXML(is,
					new String[] { "apikey", "secretkey" });
	
				apiKey = requestKeyValues.get("apikey");
				secretKey = requestKeyValues.get("secretkey");
				
				if(apiKey != null) {
					if(s_logger.isDebugEnabled())
						s_logger.debug("registration OK, apiKey: " + apiKey + ", secretKey: " + secretKey);
					return true;
				}
			} else  {
				s_logger.error("registration failed with error code: " + responseCode);
			} 
			s_logger.debug("login failed");
			return false;
		}
    }
    
    public Map<String, String> createUser(long domainId, String accountName, String userName, String password,
		String firstName, String lastName, String email) throws HttpException, IOException {
    	
    	Map<String, Object> params = new HashMap<String, Object>();
    	params.put("domainid", domainId);
    	params.put("username", URLEncoder.encode(userName, "UTF-8"));
		String encryptedPassword = createMD5Password(password);
		String encodedPassword = URLEncoder.encode(encryptedPassword, "UTF-8");
    	params.put("password", encodedPassword);
    	params.put("firstname", firstName);
    	params.put("lastname", lastName);
    	params.put("email", lastName);
    	if(accountName != null)
    		params.put("account", accountName);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("createUser", params);
		
		int responseCode = client.executeMethod(method);
		if (responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getSingleValueFromXML(is,
				new String[] { "id", "account" });
			return response;
		} else  {
			s_logger.error("createUser failed with error code: " + responseCode);
		} 

    	return null;
    }
    
    public Map<String, String> deployVM(long zoneId, long serviceOfferingId, long diskOfferingId, long templateId) 
		throws HttpException, IOException {
    	
    	Map<String, Object> params = new HashMap<String, Object>();
    	params.put("zoneid", zoneId);
    	params.put("serviceofferingid", serviceOfferingId);
    	params.put("diskofferingid", diskOfferingId);
    	params.put("templateid", templateId);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("deployVirtualMachine", params);
		int responseCode = client.executeMethod(method);
		
		if (responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			return getResultMap(is);
		} else  {
			s_logger.error("deployVM failed with error code: " + responseCode);
		} 
    	return null;
    }
    
    public long deployVMAsync(long zoneId, long serviceOfferingId, long rootDiskOfferingId, long dataDiskOfferingId, long templateId) 
		throws HttpException, IOException {
	
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("zoneid", zoneId);
		params.put("serviceofferingid", serviceOfferingId);
		params.put("rootdiskofferingid", rootDiskOfferingId);
		params.put("datadiskofferingid", dataDiskOfferingId);
		params.put("templateid", templateId);
		
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("deployVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }
    
    public Map<String, String> startVM(long vmId) throws HttpException, IOException { 
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("startVirtualMachine", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			return getResultMap(is);
		}
		return null;
    }
    
    public long startVMAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("startVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }
    
    public boolean stopVM(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("stopVirtualMachine", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getSingleValueFromXML(is,
				new String[] { "success" });
			
			String result = response.get("success");
			if(result != null && result.equalsIgnoreCase("true"))
				return true;
		}
		return false;
    }
    
    public long stopVMAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("stopVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public boolean destroyVM(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("destroyVirtualMachine", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getSingleValueFromXML(is,
				new String[] { "success" });
			
			String result = response.get("success");
			if(result != null && result.equalsIgnoreCase("true"))
				return true;
		}
		return false;
    }
    
    public long destroyVMAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("destroyVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public boolean rebootVM(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("rebootVirtualMachine", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getSingleValueFromXML(is,
				new String[] { "success" });
			
			String result = response.get("success");
			if(result != null && result.equalsIgnoreCase("true"))
				return true;
		}
		return false;
    }
    
    public long rebootVMAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("rebootVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }
    
    public long resetVMPasswordAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("resetPasswordForVirtualMachineAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }
    
    public long createSnapshotAsync(long vmId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("createSnapshotAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public long destroySnapshotAsync(long snapshotId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", snapshotId);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("destroySnapshotAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public long rollbackSnapshotAsync(long snapshotId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", snapshotId);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("rollbackToSnapshotAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public long scheduleRecurringSnapshotsAsync(long vmId, 
    		Integer interval, Integer hourlyMax, Integer dailyMax, Integer weeklyMax, Integer monthlyMax) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
    	params.put("interval", interval);
    	if(hourlyMax != null)
    		params.put("hourlymax", hourlyMax);
    	if(dailyMax != null)
    		params.put("dailymax", dailyMax);
    	if(weeklyMax != null)
    		params.put("weeklymax", weeklyMax);
    	if(monthlyMax != null)
    		params.put("monthlymax", monthlyMax);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("scheduleRecurringSnapshotsAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }

    public long createTemplateAsync(long vmId, String name, String description) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("id", vmId);
    	params.put("name", name);
    	params.put("displaytext", description);
    	
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("createTemplateAsync", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			Map<String, String> response = getResultMap(is);
			String result = response.get("jobid");
			if(result != null)
				return Long.parseLong(result);
		}
		return 0;
    }
    
    public Map<String, String> queryAsyncJobStatus(long jobId) throws HttpException, IOException {
    	Map<String, Object> params = new HashMap<String, Object>();
    	
    	params.put("jobid", jobId);
		HttpClient client = new HttpClient();
		HttpMethod method = prepare("queryAsyncJobResult", params);
		
		int responseCode = client.executeMethod(method);
		if(responseCode == 200) {
			InputStream is = method.getResponseBodyAsStream();
			return getResultMap(is);
		}
		return null;
    }
    
    
    //
    // Helper methods
    //
    private HttpMethod prepare(String cmdName, Map<String, Object> params) {
    	String url = composeCommandUrl(cmdName, params);
		HttpMethod method = new GetMethod(url);
		if(jsessionId != null)
			method.addRequestHeader("Cookie", "JSESSIONID=" + jsessionId);
		return method;
    }
    
    private String composeCommandUrl(String cmdName, Map<String, Object> params) {
    	StringBuffer sb = new StringBuffer("command=").append(cmdName);
    	for(Map.Entry<String, Object> entry: params.entrySet()) {
    		sb.append("&").append(entry.getKey()).append("=");
    		String value = entry.getValue() != null ? entry.getValue().toString() : "";
    		try {
				sb.append(URLEncoder.encode(value, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
			}
    	}
    	if(apiKey != null) {
    		try {
	    		sb.append("&apikey=").append(URLEncoder.encode(apiKey, "UTF-8"));
	    		String signature = signRequest(sb.toString(), secretKey);
				signature = URLEncoder.encode(signature, "UTF-8");
	    		sb.append("&signature=").append(signature);
			} catch (UnsupportedEncodingException e) {
			}
    	}
    	
    	String url = apiServerUrl + "?" + sb.toString();
    	if(s_logger.isDebugEnabled())
    		s_logger.debug("Composed url: " + url);
    	return url;
    }
    
	public static String signRequest(String request, String key) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "HmacSHA1");
			mac.init(keySpec);
			mac.update(request.toLowerCase().getBytes());
			byte[] encryptedBytes = mac.doFinal();
			return Base64.encodeBytes(encryptedBytes);
		} catch (Exception ex) {
			s_logger.error("unable to sign request", ex);
		}
		return null;
	}
	
	public static Map<String, String> getResultMap(InputStream is) {
		Map<String, String> returnValues = new HashMap<String, String>();
		try {
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = docBuilder.parse(is);
			if(s_logger.isDebugEnabled())
				s_logger.debug("Response: " + dumpXml(doc));
			Element rootElement = doc.getDocumentElement();

			NodeList nodes = rootElement.getChildNodes();
			if(s_logger.isDebugEnabled())
				s_logger.debug("Parsed response:");
			for(int i = 0; i < nodes.getLength(); i++) {
				Node node = nodes.item(i);
				returnValues.put(node.getNodeName(), node.getTextContent());
				
				if(s_logger.isDebugEnabled())
					s_logger.debug(node.getNodeName() + " : " + node.getTextContent());
			}
		} catch (Exception ex) {
			s_logger.error("error processing XML", ex);
		}
		return returnValues;
	}
    
	public static Map<String, String> getSingleValueFromXML(InputStream is,
			String[] tagNames) {
		Map<String, String> returnValues = new HashMap<String, String>();
		try {
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = docBuilder.parse(is);
			if(s_logger.isDebugEnabled())
				s_logger.debug("Response: " + dumpXml(doc));
			Element rootElement = doc.getDocumentElement();

			for (int i = 0; i < tagNames.length; i++) {
				NodeList targetNodes = rootElement.getElementsByTagName(tagNames[i]);
				if (targetNodes.getLength() <= 0) {
					s_logger.error("no " + tagNames[i] + " tag in XML response...returning null");
				} else {
					returnValues.put(tagNames[i], targetNodes.item(0).getTextContent());
				}
			}
		} catch (Exception ex) {
			s_logger.error("error processing XML", ex);
		}
		return returnValues;
	}
	
	public static Map<String, List<String>> getMultipleValuesFromXML(
			InputStream is, String[] tagNames) {
		Map<String, List<String>> returnValues = new HashMap<String, List<String>>();
		try {
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = docBuilder.parse(is);
			if(s_logger.isDebugEnabled())
				s_logger.debug("Response: " + dumpXml(doc));
			Element rootElement = doc.getDocumentElement();
			for (int i = 0; i < tagNames.length; i++) {
				NodeList targetNodes = rootElement
						.getElementsByTagName(tagNames[i]);
				if (targetNodes.getLength() <= 0) {
					s_logger.error("no " + tagNames[i]
							+ " tag in XML response...returning null");
				} else {
					List<String> valueList = new ArrayList<String>();
					for (int j = 0; j < targetNodes.getLength(); j++) {
						Node node = targetNodes.item(j);
						valueList.add(node.getTextContent());
					}
					returnValues.put(tagNames[i], valueList);
				}
			}
		} catch (Exception ex) {
			s_logger.error(ex);
		}
		return returnValues;
	}
	
	public static String createMD5Password(String password) {
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			throw new VmopsRuntimeException("Error", e);
		}

		md5.reset();
		BigInteger pwInt = new BigInteger(1, md5.digest(password.getBytes()));

		// make sure our MD5 hash value is 32 digits long...
		StringBuffer sb = new StringBuffer();
		String pwStr = pwInt.toString(16);
		int padding = 32 - pwStr.length();
		for (int i = 0; i < padding; i++) {
			sb.append('0');
		}
		sb.append(pwStr);
		return sb.toString();
	}
	
	private static String dumpXml(Document doc) {
		OutputFormat format = new OutputFormat(doc);
        format.setLineWidth(65);
        format.setIndenting(true);
        format.setIndent(2);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        XMLSerializer serializer = new XMLSerializer(out, format);
        try {
			serializer.serialize(doc);
		} catch (IOException e) {
		}
		return new String(out.toByteArray());
	}
}
