package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.api.enums.worldGeneration.EDhApiDistantGeneratorMode;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.file.fullDatafile.GeneratedFullDataSourceProvider;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayload;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.sql.dto.BeaconBeamDTO;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import org.apache.logging.log4j.LogManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class FullDataSourceRequestHandler
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	
	private final AbstractDhServerLevel serverLevel;
	private String getLevelIdentifier() { return this.serverLevel.getLevelWrapper().getDhIdentifier(); }
	private GeneratedFullDataSourceProvider fullDataSourceProvider() { return this.serverLevel.serverside.fullDataFileHandler; }
	private List<BeaconBeamDTO> getAllBeamsForPos(long pos) { return this.serverLevel.beaconBeamRepo.getAllBeamsForPos(pos); }
	
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByPos = new ConcurrentHashMap<>();
	private final ConcurrentMap<Long, DataSourceRequestGroup> requestGroupsByFutureId = new ConcurrentHashMap<>();
	
	
	public FullDataSourceRequestHandler(AbstractDhServerLevel serverLevel)
	{
		this.serverLevel = serverLevel;
	}
	
	
	//==================//
	// network handling //
	//==================//
	
	public void queueLodSyncForRequestMessage(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
	{
		if (!serverPlayerState.sessionConfig.getSynchronizeOnLoad())
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		
		if (!rateLimiterSet.syncOnLoginRateLimiter.tryAcquire(message))
		{
			return;
		}
		
		
		// the client timestamp will be null if we want to retrieve the LOD regardless of when it was last updated
		long clientTimestamp = (message.clientTimestamp != null) ? message.clientTimestamp : -1;
		// the server timestamp will be null if no LOD data exists for this position
		Long serverTimestamp = this.fullDataSourceProvider().getTimestampForPos(message.sectionPos);
		if (serverTimestamp == null
			|| serverTimestamp <= clientTimestamp)
		{
			// either no data exists to sync, or the client is already up to date
			rateLimiterSet.syncOnLoginRateLimiter.release();
			message.sendResponse(new FullDataSourceResponseMessage(null));
			return;
		}
		
		
		AbstractExecutorService executor = ThreadPoolUtil.getNetworkCompressionExecutor();
		if (executor == null)
		{
			// shouldn't normally happen, but just in case
			LOGGER.warn("Unable to send FullDataSourceResponseMessage - getNetworkCompressionExecutor() is null");
			return;
		}
		
		this.fullDataSourceProvider().getAsync(message.sectionPos).thenAcceptAsync(fullDataSource ->
		{
			try (FullDataPayload payload = new FullDataPayload(fullDataSource, this.getAllBeamsForPos(message.sectionPos)))
			{
				fullDataSource.close();
				
				serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () ->
				{
					message.sendResponse(new FullDataSourceResponseMessage(payload));
					rateLimiterSet.syncOnLoginRateLimiter.release();
				});
			}
			catch (Exception e)
			{
				LOGGER.error("Unexpected issue getting request for pos ["+DhSectionPos.toString(message.sectionPos)+"], error: ["+e.getMessage()+"].", e);
			}
		}, executor);
	}
	
	public void queueWorldGenForRequestMessage(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
	{
		if (!serverPlayerState.sessionConfig.isDistantGenerationEnabled())
		{
			message.sendResponse(new RequestRejectedException("Operation is disabled in config."));
			return;
		}
		
		if (!rateLimiterSet.generationRequestRateLimiter.tryAcquire(message))
		{
			return;
		}
		
		this.doQueueWorldGenForRequestMessage(new DataSourceRequestGroup.RequestData(serverPlayerState, message, rateLimiterSet));
	}
	
	private void doQueueWorldGenForRequestMessage(DataSourceRequestGroup.RequestData requestData)
	{
		while (true)
		{
			AtomicBoolean createdNewGroup = new AtomicBoolean(false);
			DataSourceRequestGroup requestGroup = this.requestGroupsByPos.computeIfAbsent(requestData.sectionPos(), pos ->
			{
				DataSourceRequestGroup newGroup = new DataSourceRequestGroup(pos);
				newGroup.tryAddRequest(requestData);
				createdNewGroup.set(true);
				
				this.tryFulfillDataSourceRequestGroup(newGroup, pos);
				
				LOGGER.debug("[" + this.getLevelIdentifier() + "] Created request group for pos [" + DhSectionPos.toString(pos) + "].");
				return newGroup;
			});
			
			// If this fails, loop until either a permit is acquired or the group is removed to create another one
			if (!createdNewGroup.get() && !requestGroup.tryAddRequest(requestData))
			{
				Thread.yield();
				continue;
			}
			
			this.requestGroupsByFutureId.put(requestData.futureId(), requestGroup);
			break;
		}
	}
	
	public void cancelRequest(long requestId)
	{
		DataSourceRequestGroup requestGroup = this.requestGroupsByFutureId.remove(requestId);
		if (requestGroup == null)
		{
			return;
		}
		
		DataSourceRequestGroup.RequestData removedRequest = requestGroup.tryRemoveRequest(requestId, requestsToTransfer ->
		{
			LOGGER.debug("[" + this.getLevelIdentifier() + "] Cancelled request group [" + DhSectionPos.toString(requestGroup.pos) + "].");
			this.requestGroupsByPos.remove(requestGroup.pos);
			
			if (!requestsToTransfer.isEmpty())
			{
				for (DataSourceRequestGroup.RequestData requestToTransfer : requestsToTransfer)
				{
					this.doQueueWorldGenForRequestMessage(requestToTransfer);
				}
			}
			else
			{
				this.fullDataSourceProvider().removeRetrievalRequestIf(pos -> pos == requestGroup.pos);
			}
		});
		
		if (removedRequest != null)
		{
			removedRequest.rateLimiterSet.generationRequestRateLimiter.release();
		}
	}
	
	
	public void tick()
	{
		// Send finished data source requests
		for (Map.Entry<Long, DataSourceRequestGroup> entry : this.requestGroupsByPos.entrySet())
		{
			DataSourceRequestGroup requestGroup = entry.getValue();
			
			if (requestGroup.fullDataSource == null)
			{
				continue;
			}
			
			LOGGER.debug("[" + this.getLevelIdentifier() + "] Fulfilled request group [" + DhSectionPos.toString(entry.getKey()) + "]");
			
			// Make this group unavailable for adding into
			this.requestGroupsByPos.remove(entry.getKey());
			if (!requestGroup.tryClose())
			{
				continue;
			}
			
			AbstractExecutorService executor = ThreadPoolUtil.getNetworkCompressionExecutor();
			if (executor == null)
			{
				LOGGER.warn("Unable to send FullDataSourceResponseMessage - getNetworkCompressionExecutor() is null");
				continue;
			}
			CompletableFuture.runAsync(() ->
			{
				try (FullDataPayload payload = new FullDataPayload(requestGroup.fullDataSource, this.getAllBeamsForPos(entry.getKey())))
				{
					requestGroup.fullDataSource.close();
					
					for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
					{
						this.requestGroupsByFutureId.remove(requestData.futureId());
						
						requestData.serverPlayerState.fullDataPayloadSender.sendInChunks(payload, () -> {
							requestData.message.sendResponse(new FullDataSourceResponseMessage(payload));
							requestData.rateLimiterSet.generationRequestRateLimiter.release();
						});
					}
				}
			}, executor);
		}
	}
	
	private void tryFulfillDataSourceRequestGroup(DataSourceRequestGroup requestGroup, long pos)
	{
		this.fullDataSourceProvider().getAsync(pos).thenAccept(fullDataSource ->
		{
			if (this.fullDataSourceProvider().isFullyGenerated(fullDataSource.columnGenerationSteps))
			{
				requestGroup.fullDataSource = fullDataSource;
				return;
			}
			
			fullDataSource.close();
			
			if (DhSectionPos.getDetailLevel(pos) > (Config.Common.WorldGenerator.distantGeneratorMode.get() == EDhApiDistantGeneratorMode.INTERNAL_SERVER
					? DhSectionPos.SECTION_MINIMUM_DETAIL_LEVEL
					: this.serverLevel.serverside.fullDataFileHandler.lowestDataDetailLevel()))
			{
				// Make this group unavailable for adding into
				this.requestGroupsByPos.remove(pos);
				if (!requestGroup.tryClose())
				{
					return;
				}
				
				for (DataSourceRequestGroup.RequestData requestData : requestGroup.requestMessages.values())
				{
					this.requestGroupsByFutureId.remove(requestData.futureId());
					requestData.rateLimiterSet.generationRequestRateLimiter.release();
					requestData.message.sendResponse(new SectionRequiresSplittingException());
				}
			}
			else if (requestGroup.isWorldGenTaskComplete())
			{
				//LOGGER.info("sending - retry [" + DhSectionPos.toString(pos) + "]");
				this.tryFulfillDataSourceRequestGroup(requestGroup, pos);
			}
			else
			{
				//LOGGER.info("sending - queueing [" + DhSectionPos.toString(pos) + "]");
				this.fullDataSourceProvider().queuePositionForRetrieval(pos);
			}
		});
	}
	
	public void onWorldGenTaskComplete(long pos)
	{
		DataSourceRequestGroup requestGroup = this.requestGroupsByPos.get(pos);
		if (requestGroup != null)
		{
			requestGroup.markWorldGenTaskComplete();
			this.tryFulfillDataSourceRequestGroup(requestGroup, pos);
		}
	}
	
}
