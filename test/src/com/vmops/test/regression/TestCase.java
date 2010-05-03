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

package com.vmops.test.regression;

import java.io.File;
import java.sql.DriverManager;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.httpclient.HttpClient;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;


import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;


public abstract class TestCase {
	
	public static Logger s_logger = Logger.getLogger(TestCase.class.getName());
	private Connection conn;
	private Document inputFile;
	private HttpClient client;
	private String testCaseName;
	private HashMap<String, String> param = new HashMap<String, String> ();
	
	public HashMap<String, String> getParam() {
		return param;
	}

	public void setParam(HashMap<String, String> param) {
		this.param = param;
	}

	public Connection getConn() {
		return conn;
	}

	public void setConn(String dbPassword) {
		this.conn = null;
		try {
			Class.forName("com.mysql.jdbc.Driver");
			this.conn=DriverManager.getConnection("jdbc:mysql://" + param.get("db") + "/vmops", "root", dbPassword);
			if (!this.conn.isValid(0)) {
				s_logger.error("Connection to DB failed to establish");
			}
			
		}catch (Exception ex) {
			s_logger.error(ex);
		}
	}

	public void setInputFile (String fileName) {
		File file = new File(fileName);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document doc = null;
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(file);
			doc.getDocumentElement().normalize();
		} catch (Exception ex) {
			s_logger.error("Unable to load " + fileName + " due to ", ex);
		}
		this.inputFile = doc;
	} 

	public Document getInputFile() {
		return inputFile;
	}
	
	public void setTestCaseName(String testCaseName) {
		this.testCaseName = testCaseName;
	}
	
	public String getTestCaseName(){
		return this.testCaseName;
	}

	public void setClient() {
		HttpClient client = new HttpClient();
		this.client = client;
	}
	
	public HttpClient getClient() {
		return this.client;
	}
	
	
	//abstract methods
	public abstract boolean executeTest();

}
