package com.seibel.distanthorizons.core.world;

import com.seibel.distanthorizons.core.file.structure.LocalSaveStructure;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.level.IDhLevel;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerState;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractDhServerWorld<TDhServerLevel extends AbstractDhServerLevel> extends AbstractDhWorld implements IDhServerWorld
{
	/** 
	 * Concurrent since levels can be added/remove while other processing is happening.
	 * (Otherwise we may need to just put the logic in a lock.
	 */
	protected final ConcurrentHashMap<ILevelWrapper, TDhServerLevel> dhLevelByLevelWrapper = new ConcurrentHashMap<>();
	public final LocalSaveStructure saveStructure = new LocalSaveStructure();
	
	private final ServerPlayerStateManager serverPlayerStateManager;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractDhServerWorld(EWorldEnvironment worldEnvironment)
	{
		super(worldEnvironment);
		this.serverPlayerStateManager = new ServerPlayerStateManager();
	}
	
	
	//=================//
	// player handling //
	//=================//
	
	@Override
	public ServerPlayerStateManager getServerPlayerStateManager()
	{
		return this.serverPlayerStateManager;
	}
	
	@Override
	public void addPlayer(IServerPlayerWrapper serverPlayer)
	{
		ServerPlayerState playerState = this.serverPlayerStateManager.registerJoinedPlayer(serverPlayer);
		this.getLevel(serverPlayer.getLevel()).addPlayer(serverPlayer);
		
		Iterator<TDhServerLevel> it = this.dhLevelByLevelWrapper.values().stream().distinct().iterator();
		while (it.hasNext())
		{
			TDhServerLevel level = it.next();
			level.registerNetworkHandlers(playerState);
		}
		
		this.serverPlayerStateManager.handlePluginMessagesFromQueue(playerState);
	}
	
	@Override
	public void removePlayer(IServerPlayerWrapper serverPlayer)
	{
		this.getLevel(serverPlayer.getLevel()).removePlayer(serverPlayer);
		this.serverPlayerStateManager.unregisterLeftPlayer(serverPlayer);
		
		// If player's left, session is already closed
	}
	
	@Override
	public void changePlayerLevel(IServerPlayerWrapper player, IServerLevelWrapper originLevel, IServerLevelWrapper destinationLevel)
	{
		this.getLevel(destinationLevel).addPlayer(player);
		this.getLevel(originLevel).removePlayer(player);
	}
	
	
	
	//================//
	// level handling //
	//================//
	
	@Override
	public TDhServerLevel getLevel(@NotNull ILevelWrapper wrapper) { return this.dhLevelByLevelWrapper.get(wrapper); }
	@Override
	public Iterable<? extends IDhLevel> getAllLoadedLevels() 
	{
		// hash set wrapper is used to filter out duplicate levels,
		// which can happen when on a singleplayer world and both a server/client level wrapper
		// are active for the same dimension
		return new HashSet<>(this.dhLevelByLevelWrapper.values()); 
	}
	@Override
	public int getLoadedLevelCount() { return this.dhLevelByLevelWrapper.size(); }
	
	
	
	//==============//
	// tick methods //
	//==============//
	
	@Override
	public void serverTick() { this.dhLevelByLevelWrapper.values().forEach(TDhServerLevel::serverTick); }
	
	@Override
	public void worldGenTick() { this.dhLevelByLevelWrapper.values().forEach(TDhServerLevel::worldGenTick); }
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public void close()
	{
		for (TDhServerLevel level : this.dhLevelByLevelWrapper.values())
		{
			LOGGER.info("Unloading level [" + level.getLevelWrapper().getDhIdentifier() + "].");
			
			// level wrapper shouldn't be null, but just in case
			IServerLevelWrapper serverLevelWrapper = level.getServerLevelWrapper();
			if (serverLevelWrapper != null)
			{
				serverLevelWrapper.onUnload();
			}
			
			level.close();
		}
		
		this.dhLevelByLevelWrapper.clear();
		LOGGER.info("Closed DhWorld of type [" + this.environment + "].");
	}
	
}
