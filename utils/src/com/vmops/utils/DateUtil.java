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

package com.vmops.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import com.vmops.utils.exception.VmopsRuntimeException;

public class DateUtil {
    public static final TimeZone GMT_TIMEZONE = TimeZone.getTimeZone("GMT");
    public static final String YYYYMMDD_FORMAT = "yyyyMMddHHmmss";
    
	public static Date currentGMTTime() {
		// Date object always stores miliseconds offset based on GMT internally
		return new Date();
	}
	
	public static Date parseDateString(TimeZone tz, String dateString) {
		return parseDateString(tz, dateString, "yyyy-MM-dd HH:mm:ss");
	}
	
	public static Date parseDateString(TimeZone tz, String dateString, String formatString) {
		DateFormat df = new SimpleDateFormat(formatString);
		df.setTimeZone(tz);
		
    	try {
    		return df.parse(dateString);
    	} catch (ParseException e) {
    		throw new VmopsRuntimeException("why why ", e);
    	}
	}
	
	public static String getDateDisplayString(TimeZone tz, Date time) {
		return getDateDisplayString(tz, time, "yyyy-MM-dd HH:mm:ss");
	}
	
	public static String getDateDisplayString(TimeZone tz, Date time, String formatString) {
		DateFormat df = new SimpleDateFormat(formatString);
		df.setTimeZone(tz);
		
		return df.format(time);
	}
	
    public enum IntervalType {
    	HOURLY,
    	DAILY,
    	WEEKLY,
    	MONTHLY;
    }

    public static IntervalType getIntervalType(String type){
    	if(type.equalsIgnoreCase(IntervalType.HOURLY.toString())){
    		return IntervalType.HOURLY;
    	} else if(type.equalsIgnoreCase(IntervalType.DAILY.toString())){
    		return IntervalType.DAILY;
    	} else if(type.equalsIgnoreCase(IntervalType.WEEKLY.toString())){
    		return IntervalType.WEEKLY;
    	}  else if(type.equalsIgnoreCase(IntervalType.MONTHLY.toString())){
    		return IntervalType.MONTHLY;
    	} 
    	return null;
    }

    public static IntervalType getIntervalType(short type){
    	if (type < 0 || type >= IntervalType.values().length) {
    	    return null;
    	}
    	return IntervalType.values()[type];
    }
    
	
	
	/**
	 * Return next run time
	 * @param intervalType  hourly/daily/weekly/monthly
	 * @param schedule MM[:HH][:DD] format. DD is day of week for weekly and day of month for monthly
	 * @param startDate if specified, returns next run time after the specified startDate  
	 * @return
	 */
    public static Date getNextRunTime(IntervalType type, String schedule, Date startDate) {

    	String[] scheduleParts = schedule.split(":"); //MM:HH:DAY 

    	final Calendar scheduleTime = Calendar.getInstance();

    	if(startDate == null){
    		startDate = new Date();
    	}
    	scheduleTime.setTime(startDate);
    	// Throw an ArrayIndexOutOfBoundsException if schedule is badly formatted.
    	scheduleTime.setLenient(false);
    	int minutes = 0;
    	int hour = 0;
    	int day = 0;
    	Date execDate = null;

    	switch(type){
    	case HOURLY:
    		if(scheduleParts.length < 1){
    			throw new VmopsRuntimeException("Incorrect schedule format: "+schedule+ " for interval type:"+type.toString());
    		}
    		minutes = Integer.parseInt(scheduleParts[0]);
    		scheduleTime.set(Calendar.MINUTE, minutes);
    		scheduleTime.set(Calendar.SECOND, 0);
    		scheduleTime.set(Calendar.MILLISECOND, 0);
    		execDate = scheduleTime.getTime();
    		// XXX: !execDate.after(startDate) is strictly for testing. 
    		// During testing we use a test clock which runs much faster than the real clock
    		// So startDate and execDate will always be ahead in the future
    		// and we will never increase the time here
    		if (execDate.before(new Date()) || !execDate.after(startDate)) {
    			scheduleTime.add(Calendar.HOUR_OF_DAY, 1);
    		}
    		break;
    	case DAILY:
    		if(scheduleParts.length < 2){
    			throw new VmopsRuntimeException("Incorrect schedule format: "+schedule+ " for interval type:"+type.toString());
    		}
    		minutes = Integer.parseInt(scheduleParts[0]);
    		hour = Integer.parseInt(scheduleParts[1]);
    		
    		scheduleTime.set(Calendar.HOUR_OF_DAY, hour);
    		scheduleTime.set(Calendar.MINUTE, minutes);
    		scheduleTime.set(Calendar.SECOND, 0);
    		scheduleTime.set(Calendar.MILLISECOND, 0);
    		execDate = scheduleTime.getTime();
    		// XXX: !execDate.after(startDate) is strictly for testing. 
            // During testing we use a test clock which runs much faster than the real clock
            // So startDate and execDate will always be ahead in the future
            // and we will never increase the time here
    		if (execDate.before(new Date()) || !execDate.after(startDate)) {
    			scheduleTime.add(Calendar.DAY_OF_YEAR, 1);
    		}
    		break;
    	case WEEKLY:
    		if(scheduleParts.length < 3){
    			throw new VmopsRuntimeException("Incorrect schedule format: "+schedule+ " for interval type:"+type.toString());
    		}
    		minutes = Integer.parseInt(scheduleParts[0]);
    		hour = Integer.parseInt(scheduleParts[1]);
    		day = Integer.parseInt(scheduleParts[2]);
    		scheduleTime.set(Calendar.DAY_OF_WEEK, day);
    		scheduleTime.set(Calendar.HOUR_OF_DAY, hour);
    		scheduleTime.set(Calendar.MINUTE, minutes);
    		scheduleTime.set(Calendar.SECOND, 0);
    		scheduleTime.set(Calendar.MILLISECOND, 0);
    		execDate = scheduleTime.getTime();
    		// XXX: !execDate.after(startDate) is strictly for testing. 
            // During testing we use a test clock which runs much faster than the real clock
            // So startDate and execDate will always be ahead in the future
            // and we will never increase the time here
    		if (execDate.before(new Date()) || !execDate.after(startDate)) {
    			scheduleTime.add(Calendar.DAY_OF_WEEK, 7);
    		};
    		break;
    	case MONTHLY:
    		if(scheduleParts.length < 3){
    			throw new VmopsRuntimeException("Incorrect schedule format: "+schedule+ " for interval type:"+type.toString());
    		}
    		minutes = Integer.parseInt(scheduleParts[0]);
    		hour = Integer.parseInt(scheduleParts[1]);
    		day = Integer.parseInt(scheduleParts[2]);
    		if(day > 28){
    			throw new VmopsRuntimeException("Day cannot be greater than 28 for monthly schedule");
    		}
    		scheduleTime.set(Calendar.DAY_OF_MONTH, day);
    		scheduleTime.set(Calendar.HOUR_OF_DAY, hour);
    		scheduleTime.set(Calendar.MINUTE, minutes);
    		scheduleTime.set(Calendar.SECOND, 0);
    		scheduleTime.set(Calendar.MILLISECOND, 0);
    		execDate = scheduleTime.getTime();
    		// XXX: !execDate.after(startDate) is strictly for testing. 
            // During testing we use a test clock which runs much faster than the real clock
            // So startDate and execDate will always be ahead in the future
            // and we will never increase the time here
    		if (execDate.before(new Date()) || !execDate.after(startDate)) {
    			scheduleTime.add(Calendar.MONTH, 1);
    		}
    		break;
    	default:
    		throw new VmopsRuntimeException("Incorrect interval: "+type.toString());
    	}


    	return scheduleTime.getTime();
    }

	
	// test only
	public static void main(String[] args) {
		TimeZone localTimezone = Calendar.getInstance().getTimeZone();
		TimeZone gmtTimezone = TimeZone.getTimeZone("GMT");
		TimeZone estTimezone = TimeZone.getTimeZone("EST");
		
		Date time = new Date();
		System.out.println("local time :" + getDateDisplayString(localTimezone, time));
		System.out.println("GMT time   :" + getDateDisplayString(gmtTimezone, time));
		System.out.println("EST time   :" + getDateDisplayString(estTimezone, time));
		//Test next run time. Expects interval and schedule as arguments
		if(args.length == 2) { 
			System.out.println("Next run time: "+getNextRunTime(getIntervalType(args[0]), args[1], time).toString());
		}
	}
}

