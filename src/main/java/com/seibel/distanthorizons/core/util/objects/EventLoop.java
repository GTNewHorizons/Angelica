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

package com.seibel.distanthorizons.core.util.objects;

import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

public class EventLoop implements AutoCloseable
{
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	private final boolean PAUSE_ON_ERROR = ModInfo.IS_DEV_BUILD;
	private final ExecutorService executorService;
	
	private final Runnable runnable;
	/** the future related to the given runnable */
	private CompletableFuture<Void> runnableFuture;
	
	private boolean isRunning = true;
	
	
	
	public EventLoop(ExecutorService executorService, Runnable runnable)
	{
		this.executorService = executorService;
		this.runnable = runnable;
	}
	
	
	
	public void tick()
	{
		if (runnableFuture != null && runnableFuture.isDone())
		{
			try
			{
				runnableFuture.join();
			}
			catch (CompletionException ce)
			{
				LOGGER.error("Uncaught exception in event loop", ce.getCause());
				if (PAUSE_ON_ERROR)
				{
					isRunning = false;
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Exception in event loop", e);
				if (PAUSE_ON_ERROR)
				{
					isRunning = false;
				}
			}
			finally
			{
				runnableFuture = null;
			}
		}
		
		if (runnableFuture == null && isRunning)
		{
			runnableFuture = CompletableFuture.runAsync(runnable, executorService);
		}
	}
	
	public void close()
	{
		if (runnableFuture != null)
		{
			runnableFuture.cancel(true);
		}
		
		runnableFuture = null;
		executorService.shutdown();
	}
	
	public boolean isRunning() { return runnableFuture != null && !runnableFuture.isDone(); }
	
}
