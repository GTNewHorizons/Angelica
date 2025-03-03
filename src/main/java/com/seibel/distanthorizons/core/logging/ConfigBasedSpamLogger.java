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

import com.seibel.distanthorizons.api.enums.config.EDhApiLoggerMode;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ConfigBasedSpamLogger
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final List<WeakReference<ConfigBasedSpamLogger>> loggers
			= Collections.synchronizedList(new LinkedList<WeakReference<ConfigBasedSpamLogger>>());
	
	public static synchronized void updateAll(boolean flush)
	{
		loggers.removeIf((logger) -> logger.get() == null);
		loggers.forEach((logger) -> {
			ConfigBasedSpamLogger l = logger.get();
			if (l != null)
				l.update();
			if (l != null && flush)
				l.reset();
		});
	}
	
	private EDhApiLoggerMode mode;
	private final Supplier<EDhApiLoggerMode> getter;
	private final int maxLogCount;
	private final AtomicInteger logTries = new AtomicInteger(0);
	private final Logger logger;
	
	public ConfigBasedSpamLogger(Logger logger, Supplier<EDhApiLoggerMode> configQuery, int maxLogPerSec)
	{
		getter = configQuery;
		mode = getter.get();
		maxLogCount = maxLogPerSec;
		this.logger = logger;
		loggers.add(new WeakReference<>(this));
	}
	
	public void reset()
	{
		logTries.set(0);
	}
	
	public boolean canMaybeLog()
	{
		return mode != EDhApiLoggerMode.DISABLED && logTries.get() < maxLogCount;
	}
	
	public void update()
	{
		mode = getter.get();
	}
	
	private String _throwableToDetailString(Throwable t)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(t.toString()).append('\n');
		StackTraceElement[] stacks = t.getStackTrace();
		
		if (stacks == null || stacks.length == 0)
			return sb.append("  at {Stack trace unavailable}").toString();
		
		for (StackTraceElement stack : stacks)
		{
			sb.append("  at ").append(stack.toString()).append("\n");
		}
		return sb.toString();
	}
	
	public void log(Level level, String str, Object... param)
	{
		if (logTries.get() >= maxLogCount)
			return;
		
		Message msg = logger.getMessageFactory().newMessage(str, param);
		String msgStr = msg.getFormattedMessage();
		if (level.isAtLeastAsSpecificAs(mode.levelForFile))
		{
			Level logLevel = Level.INFO.isAtLeastAsSpecificAs(level) ? Level.INFO : level;
			if (param.length > 0 && param[param.length - 1] instanceof Throwable)
				logger.log(logLevel, msgStr, (Throwable) param[param.length - 1]);
			else
				logger.log(logLevel, msgStr);
		}
		if (level.isAtLeastAsSpecificAs(mode.levelForChat))
		{
			if (param.length > 0 && param[param.length - 1] instanceof Throwable)
				MC.logToChat(level, msgStr + "\n" +
						_throwableToDetailString(((Throwable) param[param.length - 1])));
			else
				MC.logToChat(level, msgStr);
		}
	}
	
	public void error(String str, Object... param)
	{
		log(Level.ERROR, str, param);
	}
	
	public void warn(String str, Object... param)
	{
		log(Level.WARN, str, param);
	}
	
	public void info(String str, Object... param)
	{
		log(Level.INFO, str, param);
	}
	
	public void debug(String str, Object... param)
	{
		log(Level.DEBUG, str, param);
	}
	
	public void trace(String str, Object... param)
	{
		log(Level.TRACE, str, param);
	}
	
	public void incLogTries()
	{
		logTries.getAndIncrement();
	}
	
	public void logInc(Level level, String str, Object... param)
	{
		if (logTries.getAndIncrement() >= maxLogCount)
			return;
		
		Message msg = logger.getMessageFactory().newMessage(str, param);
		String msgStr = msg.getFormattedMessage();
		if (level.isAtLeastAsSpecificAs(mode.levelForFile))
		{
			Level logLevel = Level.INFO.isAtLeastAsSpecificAs(level) ? Level.INFO : level;
			if (param.length > 0 && param[param.length - 1] instanceof Throwable)
				logger.log(logLevel, msgStr, (Throwable) param[param.length - 1]);
			else
				logger.log(logLevel, msgStr);
		}
		if (level.isAtLeastAsSpecificAs(mode.levelForChat))
		{
			if (param.length > 0 && param[param.length - 1] instanceof Throwable)
				MC.logToChat(level, msgStr + "\n" +
						_throwableToDetailString(((Throwable) param[param.length - 1])));
			else
				MC.logToChat(level, msgStr);
		}
	}
	
	public void errorInc(String str, Object... param)
	{
		logInc(Level.ERROR, str, param);
	}
	
	public void warnInc(String str, Object... param)
	{
		logInc(Level.WARN, str, param);
	}
	
	public void infoInc(String str, Object... param)
	{
		logInc(Level.INFO, str, param);
	}
	
	public void debugInc(String str, Object... param)
	{
		logInc(Level.DEBUG, str, param);
	}
	
	public void traceInc(String str, Object... param)
	{
		logInc(Level.TRACE, str, param);
	}
	
}
