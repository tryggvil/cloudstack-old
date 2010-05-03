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

package com.vmops.serializer;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.vmops.agent.api.Answer;
import com.vmops.agent.api.Command;
import com.vmops.agent.transport.ArrayTypeAdaptor;
import com.vmops.agent.transport.VolListTypeAdaptor;
import com.vmops.storage.VolumeVO;

public class GsonHelper {
	private static final GsonBuilder s_gBuilder;
	static {
        s_gBuilder = new GsonBuilder();
        s_gBuilder.setVersion(1.3);
        s_gBuilder.registerTypeAdapter(Command[].class, new ArrayTypeAdaptor<Command>());
        s_gBuilder.registerTypeAdapter(Answer[].class, new ArrayTypeAdaptor<Answer>());
        Type listType = new TypeToken<List<VolumeVO>>() {}.getType();
        s_gBuilder.registerTypeAdapter(listType, new VolListTypeAdaptor());
	}
	
	public static GsonBuilder getBuilder() {
		return s_gBuilder;
	}
}
