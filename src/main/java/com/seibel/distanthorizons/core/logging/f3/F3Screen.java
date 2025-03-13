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

package com.seibel.distanthorizons.core.logging.f3;

import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.jar.ModJarInfo;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.pooling.PhantomArrayListPool;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.RenderBufferHandler;
import com.seibel.distanthorizons.core.render.renderer.generic.GenericObjectRenderer;
import com.seibel.distanthorizons.core.util.threading.PriorityTaskPicker;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.AbstractDhWorld;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import com.seibel.distanthorizons.coreapi.util.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

public class F3Screen
{
	private static final Logger LOGGER = LogManager.getLogger();
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	public static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance();
	
	
	
	//=================//
	// injection point //
	//=================//
	
	/**
	 * F3 menu example: <br>
	 <code>
	 Distant Horizons v: 2.1.1-a-dev <br> 
	 Build: 7e163ce6 (main) <br><br>
	 
	 Queued chunk updates: 0 / 1000 <br>
	 World Gen Tasks: 40/5304, (in progress: 7) <br><br>
	 
	 File thread pool tasks: 0 (complete: 759) <br>
	 Update thread pool tasks: 10 (complete: 24) <br>
	 Level Unsaved #: 0 <br>
	 File Handler Unsaved #: 0 <br>
	 Parent Update #: 12 <br><br>
	 
	 Client_Server World with 3 levels <br>
	 [minecraft:overworld] rendering: Active <br>
	 [minecraft:the_end] rendering: Inactive <br>
	 [minecraft:the_nether] rendering: Inactive <br><br>
	 
	 VBO Render Count: 199/374 <br>
	 </code>
	 */
	public static void addStringToDisplay(List<String> messageList)
	{
		// multi thread pools
		PriorityTaskPicker.Executor worldGenPool = ThreadPoolUtil.getWorldGenExecutor();
		PriorityTaskPicker.Executor fileHandlerPool = ThreadPoolUtil.getFileHandlerExecutor();
		PriorityTaskPicker.Executor updatePool = ThreadPoolUtil.getUpdatePropagatorExecutor();
		PriorityTaskPicker.Executor lodBuilderPool = ThreadPoolUtil.getChunkToLodBuilderExecutor();
		PriorityTaskPicker.Executor networkPool = ThreadPoolUtil.getNetworkCompressionExecutor();
		
		// single thread pools
		ThreadPoolExecutor cleanupPool = ThreadPoolUtil.getCleanupExecutor();
		ThreadPoolExecutor beaconCullingPool = ThreadPoolUtil.getBeaconCullingExecutor();
		ThreadPoolExecutor migrationPool = ThreadPoolUtil.getFullDataMigrationExecutor();
		
		AbstractDhWorld world = SharedApi.getAbstractDhWorld();
		Iterable<? extends IDhLevel> levelIterator = world.getAllLoadedLevels();
		
		
		// DH version
		messageList.add("");
		messageList.add(ModInfo.READABLE_NAME+": "+ModInfo.VERSION);
		if (ModInfo.IS_DEV_BUILD)
		{
			messageList.add("Build: " + StringUtil.shortenString(ModJarInfo.Git_Commit, 8) + " (" + ModJarInfo.Git_Branch + ")");
		}
		
		// player pos
		if (Config.Client.Advanced.Debugging.F3Screen.showPlayerPos.get())
		{
			if (MC_CLIENT != null)
			{
				byte requestedDetailLevel = Config.Client.Advanced.Debugging.F3Screen.playerPosSectionDetailLevel.get().byteValue();
				long sectionPos = DhSectionPos.encodeContaining(requestedDetailLevel, MC_CLIENT.getPlayerChunkPos());
				
				int detailLevel = DhSectionPos.getDetailLevel(sectionPos);
				int posX = DhSectionPos.getX(sectionPos);
				int posZ = DhSectionPos.getZ(sectionPos);
				messageList.add("LOD Pos: " + detailLevel + "*"+posX+","+posZ);
			}
			messageList.add("");
		}
		
		// thread pools
		if (Config.Client.Advanced.Debugging.F3Screen.showThreadPools.get())
		{
			// multi thread pools
			messageList.add(getThreadPoolStatString("World Gen/Import", worldGenPool));
			messageList.add(getThreadPoolStatString("File Handler", fileHandlerPool));
			messageList.add(getThreadPoolStatString("Update Propagator", updatePool));
			messageList.add(getThreadPoolStatString("LOD Builder", lodBuilderPool));
			messageList.add(getThreadPoolStatString("Networking", networkPool));
			//// single thread pools
			//messageList.add(getThreadPoolStatString("Cleanup", cleanupPool));
			//messageList.add(getThreadPoolStatString("Beacon Culling", beaconCullingPool));
			//messageList.add(getThreadPoolStatString("Migration", migrationPool));
			messageList.add("");
		}
		
		// combined object pools
		if (Config.Client.Advanced.Debugging.F3Screen.showCombinedObjectPools.get())
		{
			PhantomArrayListPool.addDebugMenuStringsToListForCombinedPools(messageList);
			messageList.add("");
		}
		// separated object pools
		if (Config.Client.Advanced.Debugging.F3Screen.showSeparatedObjectPools.get())
		{
			PhantomArrayListPool.addDebugMenuStringsToListForSeparatePools(messageList);
			messageList.add("");
		}
		
		// chunk updates
		if (Config.Client.Advanced.Debugging.F3Screen.showQueuedChunkUpdateCount.get())
		{
			messageList.add(SharedApi.INSTANCE.getDebugMenuString());
			messageList.add("");
		}
		
		// world / levels
		if (Config.Client.Advanced.Debugging.F3Screen.showLevelStatus.get())
		{
			world.addDebugMenuStringsToList(messageList);
			messageList.add("");
			for (IDhLevel level : levelIterator)
			{
				level.addDebugMenuStringsToList(messageList);
				// LOD rendering
				RenderBufferHandler renderBufferHandler = level.getRenderBufferHandler();
				if (renderBufferHandler != null)
				{
					messageList.add(renderBufferHandler.getVboRenderDebugMenuString());
					String showPassString = renderBufferHandler.getShadowPassRenderDebugMenuString();
					if (showPassString != null)
					{
						messageList.add(showPassString);
					}
				}
				// Generic rendering
				GenericObjectRenderer genericRenderer = level.getGenericRenderer();
				if (genericRenderer != null)
				{
					messageList.add(genericRenderer.getVboRenderDebugMenuString());
				}
				messageList.add("");
			}
		}
	}
	
	
	
	//================//
	// helper methods //
	//================//
	
	private static String getThreadPoolStatString(String name, PriorityTaskPicker.Executor pool)
	{
		String queueSize = (pool != null) ? NUMBER_FORMAT.format(pool.getQueueSize()) : "-";
		String completedCount = (pool != null) ? NUMBER_FORMAT.format(pool.getCompletedTaskCount()) : "-";
		
		String message = name+", Tasks: "+queueSize+", Done: "+completedCount;
		
		if (pool != null)
		{
			// active threads
			int activeThreadCount = pool.getRunningTaskCount();
			int threadCount = pool.getPoolSize();
			message += ", Active: "+activeThreadCount+"/"+threadCount;
			
			// thread runtime
			String runTimeAvgStr;
			double runTimeAvgInMs = pool.getAverageRunTimeInMs();
			if (!Double.isNaN(runTimeAvgInMs))
			{
				runTimeAvgStr = NUMBER_FORMAT.format(runTimeAvgInMs);
			}
			else
			{
				runTimeAvgStr = "<0";
			}
			
			message += ", Avg: "+runTimeAvgStr+"ms";
		}
		
		
		return message;
	}
	
	
	
}
