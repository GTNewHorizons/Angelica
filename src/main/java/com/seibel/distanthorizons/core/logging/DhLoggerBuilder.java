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

package com.seibel.distanthorizons.core.logging;

import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Used to create loggers with specific names.
 *
 * @author James Seibel
 * @version 2022-4-24
 */
public class DhLoggerBuilder
{
	/**
	 * Creates a logger in the format <br>
	 * "ModInfo.Name-className" <br>
	 * For example: <br>
	 * "DistantHorizons-ReflectionHandler" <br><br>
	 *
	 * The suggested way to use this method is like this: <br><br>
	 * <code>
	 * private static final Logger LOGGER = DhLoggerBuilder.getLogger(MethodHandles.lookup().lookupClass().getSimpleName());
	 * </code> <br><br>
	 * By using MethodHandles you don't have to manually enter the class name,
	 * Java figures that out for you. Even in a static context.
	 *
	 * @param className name of the class this logger will be named after.
	 */
	public static Logger getLogger(String className)
	{
		return LogManager.getLogger(ModInfo.NAME + "-" + className);
	}
	
	/** Returns a logger for the given class. */
	public static Logger getLogger(Class<?> clazz)
	{
		return LogManager.getLogger(ModInfo.NAME + "-" + clazz.getSimpleName());
	}
	
	/** Attempts to return the logger for this containing class. */
	public static Logger getLogger()
	{
		return LogManager.getLogger(ModInfo.NAME + "-" + getCallingClassName());
	}
	
	/** @return "??" if no name could be found */
	public static String getCallingClassName()
	{
		StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
		String callerClassName = "??";
		for (int i = 1; i < stElements.length; i++)
		{
			StackTraceElement ste = stElements[i];
			if (!ste.getClassName().equals(DhLoggerBuilder.class.getName())
					&& ste.getClassName().indexOf("java.lang.Thread") != 0)
			{
				callerClassName = ste.getClassName();
				break;
			}
		}
		
		return callerClassName;
	}
	
	
}
