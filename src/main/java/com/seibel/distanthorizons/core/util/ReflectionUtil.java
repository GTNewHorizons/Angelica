/*
 *    This file is part of the Distant Horizons mod
 *    licensed under the GNU LGPL v3 License.
 *    
 *    Copyright (C) 2020 James Seibel
 *    
 *    This program is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU Lesser General Public License as published by
 *    the Free Software Foundation, version 3.
 *    
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU Lesser General Public License for more details.
 *    
 *    You should have received a copy of the GNU Lesser General Public License
 *    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.seibel.distanthorizons.core.util;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor;
import org.apache.logging.log4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public class ReflectionUtil
{
	
	public static String getAllFieldValuesAsString(Object obj)
	{
		StringBuilder stringBuilder = new StringBuilder();
		
		Field[] fields = obj.getClass().getDeclaredFields();
		for (Field field : fields)
		{
			String fieldName = field.getName();;
			String fieldStringValue;
			try
			{
				field.setAccessible(true);
				fieldStringValue = field.get(obj) + "";
			}
			catch (Exception e)
			{
				fieldStringValue = "ERROR:[" + e.getMessage() + "]";
			}
			
			stringBuilder.append(fieldName+" - "+fieldStringValue+"\n");
		}
		
		return stringBuilder.toString();
	}
	
}
