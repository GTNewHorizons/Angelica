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

import com.seibel.distanthorizons.core.util.threading.DhThreadFactory;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;

/**
 * Handles thread pool creation.
 * 
 * @see ThreadPoolUtil
 * @see TimerUtil
 */
public class ThreadUtil
{
	private static final Logger LOGGER = LogManager.getLogger();
	
	public static final String THREAD_NAME_PREFIX = ModInfo.THREAD_NAME_PREFIX;
	
	// TODO move all "Runtime.getRuntime().availableProcessors()" calls here
	
	
	
	//===============//
	// standard pool // 
	//===============//
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name, int priority, boolean isDaemon)
	{
		// this is what was being internally used by Executors.newFixedThreadPool
		// I'm just calling it explicitly here so we can reference the more feature-rich
		// ThreadPoolExecutor vs the more generic ExecutorService
		return new ThreadPoolExecutor(/*corePoolSize*/ poolSize, /*maxPoolSize*/ poolSize,
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>(),
				new DhThreadFactory(name, priority, isDaemon));
	}
	
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz, int priority) { return makeThreadPool(poolSize, clazz.getSimpleName(), priority, false); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, String name) { return makeThreadPool(poolSize, name, Thread.NORM_PRIORITY, false); }
	public static ThreadPoolExecutor makeThreadPool(int poolSize, Class<?> clazz) { return makeThreadPool(poolSize, clazz.getSimpleName(), Thread.NORM_PRIORITY, false); }
	
	
	
	//====================//
	// single thread pool //
	//====================//
	
	public static ThreadPoolExecutor makeSingleThreadPool(String name, int priority) { return makeThreadPool(1, name, priority, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz, int priority) { return makeThreadPool(1, clazz.getSimpleName(), priority, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(String name) { return makeThreadPool(1, name, Thread.NORM_PRIORITY, false); }
	public static ThreadPoolExecutor makeSingleThreadPool(Class<?> clazz) { return makeThreadPool(1, clazz.getSimpleName(), Thread.NORM_PRIORITY, false); }
	
	public static ThreadPoolExecutor makeSingleDaemonThreadPool(String name) { return makeThreadPool(1, name, Thread.NORM_PRIORITY, true); }
	
}
