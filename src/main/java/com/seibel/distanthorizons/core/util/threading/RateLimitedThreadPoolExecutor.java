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

package com.seibel.distanthorizons.core.util.threading;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Can be used to more finely control CPU usage and
 * reduce CPU usage if only 1 thread is already assigned.
 */
public class RateLimitedThreadPoolExecutor extends ThreadPoolExecutor
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public final ConfigEntry<Double> runTimeRatioConfig = Config.Common.MultiThreading.threadRunTimeRatio;
	
	/** When this thread started running its last task */
	private final ThreadLocal<Long> runStartTime = ThreadLocal.withInitial(() -> -1L);
	
	
	
	//==============//
	// constructors //
	//==============//
	
	public RateLimitedThreadPoolExecutor(int poolSize, ThreadFactory threadFactory, BlockingQueue<Runnable> workQueue)
	{
		super(poolSize, poolSize,
				0L, TimeUnit.MILLISECONDS,
				workQueue,
				threadFactory);
	}
	
	
	
	//===========//
	// overrides //
	//===========//
	
	@Override
	protected void beforeExecute(Thread thread, Runnable runnable)
	{
		this.runStartTime.set(System.nanoTime());
		
		super.beforeExecute(thread, runnable);
	}
	
	@Override
	protected void afterExecute(Runnable runnable, Throwable throwable)
	{
		super.afterExecute(runnable, throwable);
		
		try
		{
			long runTime = System.nanoTime() - this.runStartTime.get();
			Thread.sleep(TimeUnit.NANOSECONDS.toMillis((long) (runTime / this.runTimeRatioConfig.get() - runTime)));
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	@Override
	protected void terminated() 
	{
		super.terminated();
	}
	
	/** 
	 * Deprecated since most of the time this doesn't do what we want or need.
	 * In James testing any tasks started with {@link CompletableFuture#runAsync(Runnable, Executor)}
	 * or {@link CompletableFuture#supplyAsync(Supplier, Executor)} converted the {@link Runnable}
	 * and {@link CompletableFuture} into objects that didn't support being cancled and removed
	 * from the queue. The canceled tasks were correctly never run, but couldn't be purged.
	 */
	@Deprecated
	@Override
	public void purge()
	{
		super.purge();
	}
	
}