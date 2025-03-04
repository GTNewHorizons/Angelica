package com.seibel.distanthorizons.forge;

import com.seibel.distanthorizons.common.AbstractModInitializer;
import com.seibel.distanthorizons.common.util.ProxyUtil;
import com.seibel.distanthorizons.common.wrappers.chunk.ChunkWrapper;
import com.seibel.distanthorizons.common.wrappers.misc.ServerPlayerWrapper;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.api.internal.ServerApi;
import com.seibel.distanthorizons.core.wrapperInterfaces.chunk.IChunkWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class ForgeServerProxy implements AbstractModInitializer.IEventProxy
{
	private static World GetEventLevel(WorldEvent e) { return e.world; }

	private final ServerApi serverApi = ServerApi.INSTANCE;
	private final boolean isDedicated;



	@Override
	public void registerEvents()
	{
		MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
		if (this.isDedicated)
		{
			ForgePluginPacketSender.setPacketHandler(ServerApi.INSTANCE::pluginMessageReceived);
		}
	}



	//=============//
	// constructor //
	//=============//

	public ForgeServerProxy(boolean isDedicated)
	{
		this.isDedicated = isDedicated;
	}



	//========//
	// events //
	//========//

	// ServerTickEvent (at end)
	@SubscribeEvent
	public void serverTickEvent(TickEvent.ServerTickEvent event)
	{
		if (event.phase == TickEvent.Phase.END)
		{
			this.serverApi.serverTickEvent();
		}
	}

	// ServerLevelLoadEvent
	@SubscribeEvent
	public void serverLevelLoadEvent(WorldEvent.Load event)
	{
		if (GetEventLevel(event) instanceof WorldServer)
		{
			this.serverApi.serverLevelLoadEvent(getServerLevelWrapper((WorldServer) GetEventLevel(event)));
		}
	}

	// ServerLevelUnloadEvent
	@SubscribeEvent
	public void serverLevelUnloadEvent(WorldEvent.Unload event)
	{
		if (GetEventLevel(event) instanceof WorldServer)
		{
			this.serverApi.serverLevelUnloadEvent(getServerLevelWrapper((WorldServer) GetEventLevel(event)));
		}
	}

	@SubscribeEvent
	public void serverChunkLoadEvent(ChunkEvent.Load event)
	{
		ILevelWrapper levelWrapper = ProxyUtil.getLevelWrapper(GetEventLevel(event));

		IChunkWrapper chunk = new ChunkWrapper(event.getChunk(), levelWrapper);
		this.serverApi.serverChunkLoadEvent(chunk, levelWrapper);
	}

	@SubscribeEvent
	public void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event)
	{ this.serverApi.serverPlayerJoinEvent(getServerPlayerWrapper(event)); }
	@SubscribeEvent
	public void playerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event)
	{ this.serverApi.serverPlayerDisconnectEvent(getServerPlayerWrapper(event)); }
	@SubscribeEvent
	public void playerChangedDimensionEvent(PlayerEvent.PlayerChangedDimensionEvent event)
	{
		this.serverApi.serverPlayerLevelChangeEvent(
				getServerPlayerWrapper(event),
				getServerLevelWrapper(event.fromDim, event),
				getServerLevelWrapper(event.toDim, event)
		);
	}



	//================//
	// helper methods //
	//================//

	private static IServerLevelWrapper getServerLevelWrapper(WorldServer level) { return ServerLevelWrapper.getWrapper(level); }


	private static IServerLevelWrapper getServerLevelWrapper(int dim, PlayerEvent event)
	{
        WorldServer world = (WorldServer) event.player.worldObj;
        WorldServer worldDim = world.func_73046_m().worldServerForDimension(dim);
		return getServerLevelWrapper(worldDim);
	}

	private static IServerPlayerWrapper getServerPlayerWrapper(PlayerEvent event) {
		return ServerPlayerWrapper.getWrapper(
				event.player
		);
	}

}
