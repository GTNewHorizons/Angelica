package com.seibel.distanthorizons.core.level;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.file.fullDatafile.FullDataSourceProviderV2;
import com.seibel.distanthorizons.core.file.structure.ISaveStructure;
import com.seibel.distanthorizons.core.logging.DhLoggerBuilder;
import com.seibel.distanthorizons.core.logging.f3.F3Screen;
import com.seibel.distanthorizons.core.multiplayer.server.FullDataSourceRequestHandler;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerState;
import com.seibel.distanthorizons.core.multiplayer.server.ServerPlayerStateManager;
import com.seibel.distanthorizons.core.network.exceptions.RequestOutOfRangeException;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import com.seibel.distanthorizons.core.network.messages.ILevelRelatedMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataPartialUpdateMessage;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.requests.CancelMessage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.ILevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.*;

public abstract class AbstractDhServerLevel extends AbstractDhLevel implements IDhServerLevel
{
	protected static final Logger LOGGER = DhLoggerBuilder.getLogger();
	
	public final ServerLevelModule serverside;
	protected final IServerLevelWrapper serverLevelWrapper;
	
	protected final ServerPlayerStateManager serverPlayerStateManager;
	
	/**
	 * This queue is used for ensuring fair generation speed for each player. <br>
	 * Every tick the first player gets used for centering generation, and then is immediately moved into the back of the queue. <br>
	 * TODO only add players that actually have something to generate
	 */
	protected final ConcurrentLinkedQueue<IServerPlayerWrapper> worldGenPlayerCenteringQueue = new ConcurrentLinkedQueue<>();
	
	private final FullDataSourceRequestHandler requestHandler = new FullDataSourceRequestHandler(this);
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractDhServerLevel(ISaveStructure saveStructure, IServerLevelWrapper serverLevelWrapper, ServerPlayerStateManager serverPlayerStateManager)
	{
		this(saveStructure, serverLevelWrapper, serverPlayerStateManager, true);
	}
	public AbstractDhServerLevel(
			ISaveStructure saveStructure,
			IServerLevelWrapper serverLevelWrapper,
			ServerPlayerStateManager serverPlayerStateManager,
			boolean runRepoReliantSetup
		)
	{
		if (saveStructure.getSaveFolder(serverLevelWrapper).mkdirs())
		{
			LOGGER.warn("unable to create data folder.");
		}
		this.serverLevelWrapper = serverLevelWrapper;
		this.serverside = new ServerLevelModule(this, saveStructure);
		this.createAndSetSupportingRepos(this.serverside.fullDataFileHandler.repo.databaseFile);
		if (runRepoReliantSetup)
		{
			this.runRepoReliantSetup();
		}
		
		LOGGER.info("Started "+this.getClass().getSimpleName()+" for ["+serverLevelWrapper+"] at ["+saveStructure+"].");
		
		this.serverPlayerStateManager = serverPlayerStateManager;
	}
	
	
	
	//=======//
	// ticks //
	//=======//
	
	@Override
	public void serverTick()
	{
		this.requestHandler.tick();
	}
	
	@Override
	public boolean shouldDoWorldGen()
	{ return Config.Common.WorldGenerator.enableDistantGeneration.get() && !this.worldGenPlayerCenteringQueue.isEmpty(); }
	
	@Override
	@Nullable
	public DhBlockPos2D getTargetPosForGeneration()
	{
		IServerPlayerWrapper firstPlayer = this.worldGenPlayerCenteringQueue.peek();
		if (firstPlayer == null)
		{
			return null;
		}
		
		// Put first player in back before removing from front, so it can be removed by other thread without blocking
		// - if it gets removed, remove() below will remove the item we just put instead
		this.worldGenPlayerCenteringQueue.add(firstPlayer);
		this.worldGenPlayerCenteringQueue.remove(firstPlayer);
		
		Vec3d position = firstPlayer.getPosition();
		return new DhBlockPos2D((int) position.x, (int) position.z);
	}
	
	@Override 
	public void worldGenTick() { this.serverside.worldGenModule.worldGenTick(); }
	
	
	
	//==================//
	// network handling //
	//==================//
	
	public void registerNetworkHandlers(ServerPlayerState serverPlayerState)
	{
		serverPlayerState.networkSession.registerHandler(FullDataSourceRequestMessage.class, (message) ->
		{
			if (!this.validatePlayerInCurrentLevel(message))
			{
				return;
			}
			
			Vec3d playerPosition = serverPlayerState.getServerPlayer().getPosition();
			int distanceFromPlayer = DhSectionPos.getChebyshevSignedBlockDistance(message.sectionPos, new DhBlockPos2D((int) playerPosition.x, (int) playerPosition.z)) / 16;
			
			ServerPlayerState.RateLimiterSet rateLimiterSet = serverPlayerState.getRateLimiterSet(this);
			
			if (message.clientTimestamp == null)
			{
				if (distanceFromPlayer > Config.Server.maxGenerationRequestDistance.get())
				{
					message.sendResponse(new RequestOutOfRangeException("Distance too large: " + distanceFromPlayer + " > " + Config.Server.maxGenerationRequestDistance.get()));
					return;
				}
				
				if (Config.Server.generationBoundsRadius.get() > 0)
				{
					if (DhSectionPos.getChebyshevSignedBlockDistance(message.sectionPos, new DhBlockPos2D(
							Config.Server.generationBoundsX.get(), Config.Server.generationBoundsZ.get()
					)) > Config.Server.generationBoundsRadius.get())
					{
						message.sendResponse(new RequestOutOfRangeException("Section out of allowed bounds"));
						return;
					}
				}
				
				if (!Config.Server.Experimental.enableNSizedGeneration.get() && DhSectionPos.getDetailLevel(message.sectionPos) != DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL)
				{
					message.sendResponse(new SectionRequiresSplittingException("Only highest-detail sections are allowed"));
					return;
				}
				
				this.requestHandler.queueWorldGenForRequestMessage(serverPlayerState, message, rateLimiterSet);
			}
			else
			{
				if (distanceFromPlayer > Config.Server.maxSyncOnLoadRequestDistance.get())
				{
					message.sendResponse(new RequestOutOfRangeException("Distance too large: " + distanceFromPlayer + " > " + Config.Server.maxSyncOnLoadRequestDistance.get()));
					return;
				}
				this.requestHandler.queueLodSyncForRequestMessage(serverPlayerState, message, rateLimiterSet);
			}
		});
		
		
		serverPlayerState.networkSession.registerHandler(CancelMessage.class, msg ->
		{
			this.requestHandler.cancelRequest(msg.futureId);
		});
	}
	
	
	/** May send an error message in response if the message is a {@link AbstractTrackableMessage} */
	private <T extends AbstractNetworkMessage> boolean validatePlayerInCurrentLevel(T message)
	{
		if (!(message instanceof ILevelRelatedMessage))
		{
			LodUtil.assertNotReach("Received message ["+message+"] does not implement ["+ILevelRelatedMessage.class.getSimpleName()+"]");
		}
		
		// Only handle requests for this level
		if (!((ILevelRelatedMessage) message).isSameLevelAs(this.getServerLevelWrapper()))
		{
			return false;
		}
		
		LodUtil.assertTrue(message.getSession().serverPlayer != null);
		
		// Check if the player is in this dimension,
		// since handling multiple dimensions isn't allowed
		if (message.getSession().serverPlayer.getLevel() != this.getLevelWrapper())
		{
			// If the message can be replied to - reply with an error, otherwise just ignore
			if (message instanceof AbstractTrackableMessage)
			{
				((AbstractTrackableMessage) message).sendResponse(
						new RequestRejectedException(
								"Generation not allowed. " +
										"Requested dimension: ["+((ILevelRelatedMessage) message).getLevelName()+"], " +
										"player dimension: [" + message.getSession().serverPlayer.getLevel().getDhIdentifier() + "], " +
										"handler dimension: [" + this.getLevelWrapper().getDhIdentifier() + "]"
						)
				);
			}
			
			return false;
		}
		
		return true;
	}
	
	
	
	//===========//
	// world gen //
	//===========//
	
	@Override
	public void onWorldGenTaskComplete(long pos)
	{
		this.requestHandler.onWorldGenTaskComplete(pos);
	}
	
	
	
	//=================//
	// player handling //
	//=================//
	
	public void addPlayer(IServerPlayerWrapper serverPlayer) { this.worldGenPlayerCenteringQueue.add(serverPlayer); }
	public void removePlayer(IServerPlayerWrapper serverPlayer) { this.worldGenPlayerCenteringQueue.remove(serverPlayer); }
	
	@Override
	public CompletableFuture<Void> updateDataSourcesAsync(FullDataSourceV2 data)
	{
		return this.getFullDataProvider()
			.updateDataSourceAsync(data)
			.thenRun(() -> 
			{
				if (!Config.Server.enableRealTimeUpdates.get())
				{
					return;
				}
				
				LodUtil.assertTrue(this.beaconBeamRepo != null, "beaconBeamRepo should not be null");
				try (FullDataPayload payload = new FullDataPayload(data, this.beaconBeamRepo.getAllBeamsForPos(data.getPos())))
				{
					for (ServerPlayerState serverPlayerState : this.serverPlayerStateManager.getReadyPlayers())
					{
						if (serverPlayerState.getServerPlayer().getLevel() != this.serverLevelWrapper)
						{
							continue;
						}
						
						if (!serverPlayerState.sessionConfig.isRealTimeUpdatesEnabled())
						{
							continue;
						}
						
						Vec3d playerPosition = serverPlayerState.getServerPlayer().getPosition();
						int distanceFromPlayer = DhSectionPos.getChebyshevSignedBlockDistance(data.getPos(), new DhBlockPos2D((int) playerPosition.x, (int) playerPosition.z)) / 16;
						if (distanceFromPlayer <= serverPlayerState.sessionConfig.getMaxUpdateDistanceRadius())
						{
							serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () ->
							{
								serverPlayerState.networkSession.sendMessage(new FullDataPartialUpdateMessage(this.serverLevelWrapper, payload));
							});
						}
					}
				}
			});
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		// migration
		boolean migrationErrored = this.serverside.fullDataFileHandler.getMigrationStoppedWithError();
		if (!migrationErrored)
		{
			long legacyDeletionCount = this.serverside.fullDataFileHandler.getLegacyDeletionCount();
			if (legacyDeletionCount > 0)
			{
				messageList.add("  Migrating - Deleting #: " + F3Screen.NUMBER_FORMAT.format(legacyDeletionCount));
			}
			long migrationCount = this.serverside.fullDataFileHandler.getTotalMigrationCount();
			if (migrationCount > 0)
			{
				messageList.add("  Migrating - Conversion #: " + F3Screen.NUMBER_FORMAT.format(migrationCount));
			}
		}
		else
		{
			messageList.add("  Migration Failed");
		}
		
		// world gen
		this.serverside.worldGenModule.addDebugMenuStringsToList(messageList);
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Override
	public int getMinY() { return this.getLevelWrapper().getMinHeight(); }
	
	@Override
	public IServerLevelWrapper getServerLevelWrapper() { return this.serverLevelWrapper; }
	
	@Override
	public ILevelWrapper getLevelWrapper() { return this.getServerLevelWrapper(); }
	
	@Override
	public FullDataSourceProviderV2 getFullDataProvider() { return this.serverside.fullDataFileHandler; }
	
	@Override
	public ISaveStructure getSaveStructure() { return this.serverside.saveStructure; }
	
	@Override
	public boolean hasSkyLight() { return this.serverLevelWrapper.hasSkyLight(); }
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override
	public void close()
	{
		super.close();
		this.serverside.close();
		LOGGER.info("Closed DHLevel for [" + this.getLevelWrapper() + "].");
	}
	
}
