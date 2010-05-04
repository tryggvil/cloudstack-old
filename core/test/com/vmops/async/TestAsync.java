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

package com.vmops.async;

import java.util.List;

import junit.framework.Assert;

import com.vmops.async.dao.AsyncJobDao;
import com.vmops.async.dao.AsyncJobDaoImpl;
import com.vmops.serializer.Param;
import com.vmops.serializer.SerializerHelper;
import com.vmops.utils.Pair;
import com.vmops.utils.db.Transaction;
import com.vmops.utils.testcase.Log4jEnabledTestCase;

public class TestAsync extends Log4jEnabledTestCase {
	public static class SampleAsyncResult {
		@Param(name="name", propName="name")
		private final String _name;
		
		@Param
		private final int count;
		
		public SampleAsyncResult(String name, int count) {
			_name = name;
			this.count = count;
		}
		
		public String getName() { return _name; }
		public int getCount() { return count; }
	}
	
	public void testDao() {
		AsyncJobDao dao = new AsyncJobDaoImpl();
		AsyncJobVO job = new AsyncJobVO(1, 1, "TestCmd", null);
		job.setInstanceType("user_vm");
		job.setInstanceId(1000L);
		
		char[] buf = new char[1024];
		for(int i = 0; i < 1024; i++)
			buf[i] = 'a';
			
		job.setResult(new String(buf));
		dao.persist(job);
		
		AsyncJobVO jobVerify = dao.findById(job.getId());
		
		Assert.assertTrue(jobVerify.getCmd().equals(job.getCmd()));
		Assert.assertTrue(jobVerify.getUserId() == 1);
		Assert.assertTrue(jobVerify.getAccountId() == 1);
		
		String result = jobVerify.getResult();
		for(int i = 0; i < 1024; i++)
			Assert.assertTrue(result.charAt(i) == 'a');
		
		jobVerify = dao.findInstancePendingAsyncJob("user_vm", 1000L);
		Assert.assertTrue(jobVerify != null);
		Assert.assertTrue(jobVerify.getCmd().equals(job.getCmd()));
		Assert.assertTrue(jobVerify.getUserId() == 1);
		Assert.assertTrue(jobVerify.getAccountId() == 1);
	}
	
	public void testSerialization() {
		List<Pair<String, Object>> l;
		int value = 1;
		l = SerializerHelper.toPairList(value, "result");
		Assert.assertTrue(l.size() == 1);
		Assert.assertTrue(l.get(0).first().equals("result"));
		Assert.assertTrue(l.get(0).second().equals("1"));
		l.clear();
		
		SampleAsyncResult result = new SampleAsyncResult("vmops", 1);
		l = SerializerHelper.toPairList(result, "result");
		
		Assert.assertTrue(l.size() == 2);
		Assert.assertTrue(l.get(0).first().equals("name"));
		Assert.assertTrue(l.get(0).second().equals("vmops"));
		Assert.assertTrue(l.get(1).first().equals("count"));
		Assert.assertTrue(l.get(1).second().equals("1"));
	}
	
	public void testAsyncResult() {
		AsyncJobResult result = new AsyncJobResult(1);
		
		result.setResultObject(100);
		Assert.assertTrue(result.getResult().equals("java.lang.Integer/100"));
		
		Object obj = result.getResultObject();
		Assert.assertTrue(obj instanceof Integer);
		Assert.assertTrue(((Integer)obj).intValue() == 100);
	}

	public void testTransaction() {
		Transaction txn = Transaction.open("testTransaction");
		try {
			txn.start();
			
			AsyncJobDao dao = new AsyncJobDaoImpl();
			AsyncJobVO job = new AsyncJobVO(1, 1, "TestCmd", null);
			job.setInstanceType("user_vm");
			job.setInstanceId(1000L);
			job.setResult("");
			dao.persist(job);
			txn.rollback();
		} finally {
			txn.close();
		}
	}
}
