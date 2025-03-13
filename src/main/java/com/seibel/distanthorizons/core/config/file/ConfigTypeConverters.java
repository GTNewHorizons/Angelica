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

package com.seibel.distanthorizons.core.config.file;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.json.JsonFormat;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows for custom varuable types to be saved in the config
 * (currently its only used for Map's)
 *
 * @author coolGi
 */
public class ConfigTypeConverters
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	// Once you've made a converter add it to here where the first value is the type you want to convert and the 2nd value is the converter
	public static final Map<Class<?>, ConverterBase> convertObjects = new HashMap<Class<?>, ConverterBase>()
	{{
		this.put(Short.class, new ShortConverter());
		this.put(Long.class, new LongConverter());
		this.put(Float.class, new FloatConverter());
		this.put(Double.class, new DoubleConverter());
		this.put(Byte.class, new ByteConverter());
		
		this.put(Map.class, new MapConverter());
	}};
	
	public static Class<?> isClassConvertable(Class<?> clazz)
	{
		for (int i = 0; i < convertObjects.size(); i++)
		{
			Class<?> selectedClass = (Class<?>) convertObjects.keySet().toArray()[i];
			if (selectedClass.isAssignableFrom(clazz))
				return selectedClass;
		}
		return null;
	}
	
	public static Object attemptToConvertToString(Object value)
	{
		return attemptToConvertToString(value.getClass(), value);
	}
	public static Object attemptToConvertToString(Class<?> clazz, Object value)
	{
		Class<?> convertablClass = isClassConvertable(clazz);
		if (convertablClass != null) {
			return convertToString(convertablClass, value);
		}
		return value;
	}
	
	public static Object attemptToConvertFromString(Object value)
	{
		return attemptToConvertFromString(value.getClass(), value);
	}
	public static Object attemptToConvertFromString(Class<?> outputClass, Object value)
	{
		boolean valueNeedsConverting = (value == null || value.getClass().equals(String.class));
		Class<?> convertablClass = isClassConvertable(outputClass);
		if (valueNeedsConverting && convertablClass != null) 
		{
			return convertFromString(convertablClass, (String) value);
		}
		return value;
	}
	
	public static String convertToString(Class<?> clazz, Object value)
	{
		try
		{
			return convertObjects.get(clazz).convertToString(value);
		}
		catch (Exception e)
		{
			System.out.println("Type [" + clazz.toString() + "] isn't a convertible value in the config file handler");
			return null;
		}
	}
	public static Object convertFromString(Class<?> clazz, String value)
	{
		try
		{
			return convertObjects.get(clazz).convertFromString(value);
		}
		catch (Exception e)
		{
			System.out.println("Type [" + clazz.toString() + "] isn't a convertible value in the config file handler");
			return null;
		}
	}
	
	
	/**
	 * The converter should extend this
	 */
	public static abstract class ConverterBase
	{
		public abstract String convertToString(Object value);
		public abstract Object convertFromString(String value);
		
	}
	
	
	
	// Some number types are a bit wack with the config parser
	// So we just store them as strings
	public static class ShortConverter extends ConverterBase
	{
		@Override public String convertToString(Object item) { return ((Short) item).toString(); }
		@Override public Short convertFromString(String s) { return Short.valueOf(s); }
		
	}
	
	public static class LongConverter extends ConverterBase
	{
		@Override public String convertToString(Object item) { return ((Long) item).toString(); }
		@Override public Long convertFromString(String s) { return Long.valueOf(s); }
		
	}
	
	public static class FloatConverter extends ConverterBase
	{
		@Override public String convertToString(Object item) { return ((Float) item).toString(); }
		@Override public Float convertFromString(String s) { return Float.valueOf(s); }
	}
	
	public static class DoubleConverter extends ConverterBase
	{
		@Override public String convertToString(Object item) { return ((Double) item).toString(); }
		@Override public Double convertFromString(String s) { return Double.valueOf(s); }
	}
	
	public static class ByteConverter extends ConverterBase
	{
		@Override public String convertToString(Object item) { return ((Byte) item).toString(); }
		@Override public Byte convertFromString(String s) { return Byte.valueOf(s); }
		
	}
	
	
	
	@SuppressWarnings("unchecked")
	public static class MapConverter extends ConverterBase
	{
		@Override
		public String convertToString(Object item)
		{
			Map<String, Object> mapObject = (Map<String, Object>) item;
			Config jsonObject = Config.inMemory();
			
			Object[] keyArray = mapObject.keySet().toArray();
			
			for (int i = 0; i < mapObject.size(); i++)
			{
				jsonObject.add(keyArray[i].toString(), mapObject.get(keyArray[i]));
			}
			
			return JsonFormat.minimalInstance().createWriter().writeToString(jsonObject);
		}
		
		@Override
		public Map<String, Object> convertFromString(String str)
		{
			Map<String, Object> map = new HashMap<>();
			
			Config jsonObject = Config.inMemory();
			try
			{
				JsonFormat.minimalInstance().createParser().parse(str, jsonObject, ParsingMode.REPLACE);
			}
			catch (Exception e)
			{
				LOGGER.error("Unable to convert config string value ["+str+"] to a Map, error: ["+e.getMessage()+"].", e);
			}
			
			return jsonObject.valueMap();
		}
		
	}
	
}
