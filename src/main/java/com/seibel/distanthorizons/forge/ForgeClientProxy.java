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

package com.seibel.distanthorizons.forge;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.minecraft.MinecraftRenderWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ClientLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ClientApi;
import com.seibel.distanthorizons.core.api.internal.SharedApi;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;

import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IClientLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;

import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;


import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL32;

import java.util.concurrent.AbstractExecutorService;

/**
 * This handles all events sent to the client,
 * and is the starting point for most of the mod.
 *
 * @author James_Seibel
 * @version 2023-7-27
 */
public class ForgeClientProxy implements AbstractModInitializer.IEventProxy
{
	private static final IMinecraftClientWrapper MC = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	private static final Logger LOGGER = DhLoggerBuilder.getLogger();

	private static World GetEventLevel(WorldEvent e) { return e.world; }



	@Override
	public void registerEvents()
	{
		MinecraftForge.EVENT_BUS.register(this);
		ForgePluginPacketSender.setPacketHandler(ClientApi.INSTANCE::pluginMessageReceived);
	}



	//=============//
	// tick events //
	//=============//

	@SubscribeEvent
	public void clientTickEvent(TickEvent.ClientTickEvent event)
	{
		if (event.phase == TickEvent.Phase.START)
		{
			ClientApi.INSTANCE.clientTickEvent();
		}
	}



	//==============//
	// world events //
	//==============//

	@SubscribeEvent
	public void clientLevelLoadEvent(WorldEvent.Load event)
	{
		LOGGER.info("level load");

		World level = event.world;
		if (!(level instanceof WorldClient))
		{
			return;
		}

        WorldClient clientLevel = (WorldClient) level;
		IClientLevelWrapper clientLevelWrapper = ClientLevelWrapper.getWrapper(clientLevel, true);
		// TODO this causes a crash due to level being set to null somewhere
		ClientApi.INSTANCE.clientLevelLoadEvent(clientLevelWrapper);
	}
	@SubscribeEvent
	public void clientLevelUnloadEvent(WorldEvent.Unload event)
	{
		LOGGER.info("level unload");

        World level = event.world;
		if (!(level instanceof WorldClient))
		{
			return;
		}

        WorldClient clientLevel = (WorldClient) level;
		IClientLevelWrapper clientLevelWrapper = ClientLevelWrapper.getWrapper(clientLevel);
		ClientApi.INSTANCE.clientLevelUnloadEvent(clientLevelWrapper);
	}



	//==============//
	// chunk events //
	//==============//

	@SubscribeEvent
	public void clickBlockEvent(PlayerInteractEvent event)
	{
		if (MC.clientConnectedToDedicatedServer())
		{
			if (SharedApi.isChunkAtBlockPosAlreadyUpdating(event.x, event.z))
			{
				return;
			}

			//LOGGER.trace("interact or block place event at blockPos: " + event.getPos());

			World level = event.world;

			AbstractExecutorService executor = ThreadPoolUtil.getFileHandlerExecutor();
			if (executor != null)
			{
				executor.execute(() ->
				{
					Chunk chunk = level.getChunkFromBlockCoords(event.x, event.z);
					this.onBlockChangeEvent(level, chunk);
				});
			}
		}
	}

	private void onBlockChangeEvent(World level, Chunk chunk)
	{
		ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(level);
		SharedApi.INSTANCE.chunkBlockChangedEvent(new ChunkWrapper(chunk, wrappedLevel), wrappedLevel);
	}

	@SubscribeEvent
	public void clientChunkLoadEvent(ChunkEvent.Load event)
	{
		if (MC.clientConnectedToDedicatedServer())
		{
			ILevelWrapper wrappedLevel = ProxyUtil.getLevelWrapper(GetEventLevel(event));
			IChunkWrapper chunk = new ChunkWrapper(event.getChunk(), wrappedLevel);
			SharedApi.INSTANCE.chunkLoadEvent(chunk, wrappedLevel);
		}
	}



	//==============//
	// key bindings //
	//==============//

	@SubscribeEvent
	public void registerKeyBindings(InputEvent.KeyInputEvent event)
	{
		if (Minecraft.getMinecraft().thePlayer == null)
		{
			return;
		}
		/* TODO if (event.getAction() != GLFW.GLFW_PRESS)
		{
			return;
		}*/

		// TODO ClientApi.INSTANCE.keyPressedEvent(event.getKey());
	}


	//===========//
	// rendering //
	//===========//

	@SubscribeEvent
	public void afterLevelRenderEvent(TickEvent.RenderTickEvent event)
	{
		if (event.type.equals(TickEvent.RenderTickEvent.Type.RENDER))
		{
			try
			{
				// should generally only need to be set once per game session
				// allows DH to render directly to Optifine's level frame buffer,
				// allowing better shader support
				MinecraftRenderWrapper.INSTANCE.finalLevelFrameBufferId = GL32.glGetInteger(GL32.GL_FRAMEBUFFER_BINDING);
			}
			catch (Exception | Error e)
			{
				LOGGER.error("Unexpected error in afterLevelRenderEvent: "+e.getMessage(), e);
			}
		}
	}


}
