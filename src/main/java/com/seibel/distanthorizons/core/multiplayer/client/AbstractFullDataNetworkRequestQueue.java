package com.seibel.distanthorizons.core.multiplayer.client;

import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheBuilder;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.types.ConfigEntry;
import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.level.DhClientLevel;
import com.seibel.distanthorizons.core.logging.ConfigBasedSpamLogger;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.exceptions.RequestOutOfRangeException;
import com.seibel.distanthorizons.core.network.exceptions.RequestRejectedException;
import com.seibel.distanthorizons.core.network.exceptions.SectionRequiresSplittingException;
import com.seibel.distanthorizons.core.network.session.SessionClosedException;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.pos.blockPos.DhBlockPos2D;
import com.seibel.distanthorizons.core.pos.DhSectionPos;
import com.seibel.distanthorizons.core.render.renderer.DebugRenderer;
import com.seibel.distanthorizons.core.render.renderer.IDebugRenderable;
import com.seibel.distanthorizons.core.sql.dto.FullDataSourceV2DTO;
import com.seibel.distanthorizons.core.util.LodUtil;
import com.seibel.distanthorizons.core.util.ratelimiting.SupplierBasedRateLimiter;
import com.seibel.distanthorizons.core.util.threading.ThreadPoolUtil;
import com.seibel.distanthorizons.core.world.DhApiWorldProxy;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import org.apache.logging.log4j.LogManager;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractFullDataNetworkRequestQueue implements IDebugRenderable, AutoCloseable
{
	private static final ConfigBasedSpamLogger LOGGER = new ConfigBasedSpamLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get(), 3);
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	private static final int MAX_RETRY_ATTEMPTS = 3;
	
	protected static final long SHUTDOWN_TIMEOUT_SECONDS = 5;
	
	
	
	public final ClientNetworkState networkState;
	protected final DhClientLevel level;
	private final boolean changedOnly;
	
	private volatile CompletableFuture<Void> closingFuture = null;
	
	protected final ConcurrentMap<Long, RequestQueueEntry> waitingTasksBySectionPos = new ConcurrentHashMap<>();
	/**
	 * This semaphore prevents a given thread from accidentally locking on the same group
	 * multiple times, as the semaphore is tied to the given thread. <br>
	 * Reentrant Lock isn't used since it would allow the thread to lock on the same group. <br>
	 * the Short.MAX_VALUE is just a very large number that should be larger than the number of
	 * threads we'll have.
	 */
	private final Semaphore pendingTasksSemaphore = new Semaphore(Short.MAX_VALUE, true);
	
	private final AtomicInteger finishedRequests = new AtomicInteger();
	private final AtomicInteger failedRequests = new AtomicInteger();
	private final ConfigEntry<Boolean> showDebugWireframeConfig;
	
	private final SupplierBasedRateLimiter<Void> rateLimiter = new SupplierBasedRateLimiter<>(this::getRequestRateLimit);
	
	private final Set<Long> visitedPositions = Collections.newSetFromMap(CacheBuilder.newBuilder()
			.expireAfterWrite(20, TimeUnit.MINUTES)
			.<Long, Boolean>build()
			.asMap());
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public AbstractFullDataNetworkRequestQueue(
			ClientNetworkState networkState, DhClientLevel level,
			boolean changedOnly, ConfigEntry<Boolean> showDebugWireframeConfig)
	{
		this.networkState = networkState;
		this.level = level;
		this.changedOnly = changedOnly;
		this.showDebugWireframeConfig = showDebugWireframeConfig;
		DebugRenderer.register(this, this.showDebugWireframeConfig);
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	protected abstract int getRequestRateLimit();
	protected abstract boolean isSectionAllowedToGenerate(long sectionPos, DhBlockPos2D targetPos);
	
	protected abstract String getQueueName();
	
	
	
	//====================//
	// request submitting //
	//====================//
	
	public CompletableFuture<ERequestResult> submitRequest(long sectionPos, Consumer<FullDataSourceV2> dataSourceConsumer)
	{ return this.submitRequest(sectionPos, null, dataSourceConsumer); }
	public CompletableFuture<ERequestResult> submitRequest(long sectionPos, @Nullable Long clientTimestamp, Consumer<FullDataSourceV2> dataSourceConsumer)
	{
		if (this.visitedPositions.contains(sectionPos))
		{
			return CompletableFuture.completedFuture(ERequestResult.FAILED);
		}
		
		AtomicBoolean added = new AtomicBoolean(false);
		RequestQueueEntry entry = this.waitingTasksBySectionPos.compute(sectionPos, (pos, existingQueueEntry) ->
		{
			if (existingQueueEntry != null)
			{
				return existingQueueEntry;
			}
			
			RequestQueueEntry newEntry = new RequestQueueEntry(dataSourceConsumer, clientTimestamp);
			newEntry.future.whenComplete((requestResult, throwable) ->
			{
				this.waitingTasksBySectionPos.remove(sectionPos);
				
				switch (requestResult)
				{
					case SUCCEEDED:
						this.finishedRequests.incrementAndGet();
						this.visitedPositions.add(pos);
						return;
					case REQUIRES_SPLITTING:
						return;
					case FAILED:
						this.failedRequests.incrementAndGet();
						return;
					default:
						if (throwable != null && !(throwable instanceof CancellationException))
						{
							this.failedRequests.incrementAndGet();
						}
						break;
				}
			});
			
			added.set(true);
			return newEntry;
		});
		
		if (!added.get())
		{
			return CompletableFuture.completedFuture(ERequestResult.FAILED);
		}
		
		return entry.future;
	}
	
	public synchronized boolean tick(DhBlockPos2D targetPos)
	{
		if (DhApiWorldProxy.INSTANCE.worldLoaded() && DhApiWorldProxy.INSTANCE.getReadOnly())
		{
			return false;
		}
		
		if (this.closingFuture != null || !this.networkState.isReady())
		{
			return false;
		}
		
		// queue requests until the queue is full
		while (this.getInProgressTaskCount() < this.getWaitingTaskCount()
				&& this.getInProgressTaskCount() < this.getRequestRateLimit()
				&& this.pendingTasksSemaphore.tryAcquire())
		{
			if (!this.rateLimiter.tryAcquire())
			{
				this.pendingTasksSemaphore.release();
				break;
			}
			
			this.sendNextRequest(targetPos);
		}
		
		return true;
	}
	private void sendNextRequest(DhBlockPos2D targetPos)
	{
		Map.Entry<Long, RequestQueueEntry> mapEntry = this.waitingTasksBySectionPos.entrySet().stream()
				.filter(task -> task.getValue().networkDataSourceFuture == null)
				.min(Comparator.comparingInt(x -> DhSectionPos.getChebyshevSignedBlockDistance(x.getKey(), targetPos)))
				.orElse(null);
		
		if (mapEntry == null)
		{
			this.pendingTasksSemaphore.release();
			return;
		}
		
		long sectionPos = mapEntry.getKey();
		RequestQueueEntry entry = mapEntry.getValue();
		
		if (!this.isSectionAllowedToGenerate(sectionPos, targetPos))
		{
			entry.future.cancel(false);
			this.pendingTasksSemaphore.release();
			return;
		}
		
		Long offsetEntryTimestamp = entry.updateTimestamp != null
				? entry.updateTimestamp + this.networkState.getServerTimeOffset()
				: null;
		
		CompletableFuture<FullDataSourceResponseMessage> dataSourceFuture = this.networkState.getSession().sendRequest(
				new FullDataSourceRequestMessage(this.level.getLevelWrapper(), sectionPos, offsetEntryTimestamp),
				FullDataSourceResponseMessage.class
		);
		entry.networkDataSourceFuture = dataSourceFuture;
		dataSourceFuture.handle((response, throwable) ->
		{
			this.pendingTasksSemaphore.release();
			
			try
			{
				if (throwable != null)
				{
					throw throwable;
				}
				
				if (response.payload != null)
				{
					FullDataSourceV2DTO dataSourceDto = this.networkState.fullDataPayloadReceiver.decodeDataSourceAndReleaseBuffer(response.payload);
					
					// set application flags based on the received detail level,
					// this is needed so the data sources propagate correctly
					dataSourceDto.applyToChildren = DhSectionPos.getDetailLevel(dataSourceDto.pos) > DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL;
					dataSourceDto.applyToParent = DhSectionPos.getDetailLevel(dataSourceDto.pos) < DhSectionPos.SECTION_BLOCK_DETAIL_LEVEL + 12;
					
					AbstractExecutorService executor = ThreadPoolUtil.getNetworkCompressionExecutor();
					if (executor == null)
					{
						LOGGER.warn("Unable to handle FullDataPayload - getNetworkCompressionExecutor() is null");
						dataSourceDto.close();
						return null;
					}
					
					CompletableFuture.runAsync(() ->
					{
						try
						{
							this.level.updateBeaconBeamsForSectionPos(dataSourceDto.pos, response.payload.beaconBeams);
							
							FullDataSourceV2 fullDataSource = dataSourceDto.createDataSource(this.level.getLevelWrapper());
							entry.dataSourceConsumer.accept(fullDataSource);
						}
						catch (Exception e)
						{
							throw new RuntimeException(e);
						}
						finally
						{
							dataSourceDto.close();
						}
					}, executor);
				}
				else
				{
					LodUtil.assertTrue(this.changedOnly, "Received empty data source response for not changes-only request");
				}
			}
			catch (SectionRequiresSplittingException ignored)
			{
				return entry.future.complete(ERequestResult.REQUIRES_SPLITTING);
			}
			catch (SessionClosedException | CancellationException ignored)
			{
				return entry.future.cancel(false);
			}
			catch (RequestRejectedException e)
			{
				LOGGER.info("Request rejected by the server: " + e.getMessage());
				return entry.future.complete(ERequestResult.FAILED);
			}
			catch (RateLimitedException e)
			{
				LOGGER.info("Rate limited by server, re-queueing task [" + DhSectionPos.toString(sectionPos) + "]: " + e.getMessage());
				
				// Skip all requests for 1 second
				this.rateLimiter.acquireAll();
				
				entry.networkDataSourceFuture = null;
				return null;
			}
			catch (RequestOutOfRangeException e)
			{
				LOGGER.debug("Out of range, re-queueing task [" + DhSectionPos.toString(sectionPos) + "]: " + e.getMessage());
				
				entry.networkDataSourceFuture = null;
				return null;
			}
			catch (Throwable e)
			{
				entry.retryAttempts--;
				LOGGER.error("Error while fetching full data source, attempts left: {} / {}", entry.retryAttempts, MAX_RETRY_ATTEMPTS, e);
				
				// Retry logic
				if (entry.retryAttempts > 0)
				{
					entry.networkDataSourceFuture = null;
					return null;
				}
				else
				{
					return entry.future.complete(ERequestResult.FAILED);
				}
			}
			
			return entry.future.complete(ERequestResult.SUCCEEDED);
		});
	}
	
	
	
	
	//=========================================//
	// IFullDataSourceRetrievalQueue overrides //
	//=========================================//
	
	public void removeRetrievalRequestIf(DhSectionPos.ICancelablePrimitiveLongConsumer removeIf)
	{
		for (Map.Entry<Long, RequestQueueEntry> mapEntry : (Iterable<? extends Map.Entry<Long, RequestQueueEntry>>) this.waitingTasksBySectionPos.entrySet().stream()
				.sorted(Comparator.comparingInt((Map.Entry<Long, RequestQueueEntry> entry) -> DhSectionPos.getChebyshevSignedBlockDistance(entry.getKey(), Objects.requireNonNull(this.level.getTargetPosForGeneration()))).reversed())
				::iterator)
		{
			long pos = mapEntry.getKey();
			RequestQueueEntry entry = mapEntry.getValue();
			
			if (removeIf.accept(pos))
			{
				LOGGER.debug("Removing request  " + mapEntry.getKey() + "...");
				
				entry.future.cancel(false);
				if (entry.networkDataSourceFuture != null)
				{
					entry.networkDataSourceFuture.cancel(false);
				}
			}
		}
	}
	
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		messageList.add(this.getQueueName() + " [" + this.level.getClientLevelWrapper().getDhIdentifier() + "]");
		messageList.add("Requests: " + this.finishedRequests + " / " + (this.getWaitingTaskCount() + this.finishedRequests.get()) + " (failed: " + this.failedRequests + ", rate limit: " + this.getRequestRateLimit() + ")");
	}
	
	public int getWaitingTaskCount() { return this.waitingTasksBySectionPos.size(); }
	public int getInProgressTaskCount() { return Short.MAX_VALUE - this.pendingTasksSemaphore.availablePermits(); }
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	
	public CompletableFuture<Void> startClosingAsync(boolean alsoInterruptRunning)
	{
		return this.closingFuture = CompletableFuture.runAsync(() -> {
			Stopwatch stopwatch = Stopwatch.createStarted();
			
			do
			{
				for (RequestQueueEntry entry : this.waitingTasksBySectionPos.values())
				{
					entry.future.cancel(alsoInterruptRunning);
					if (entry.networkDataSourceFuture != null && entry.networkDataSourceFuture.cancel(alsoInterruptRunning))
					{
						this.pendingTasksSemaphore.release();
					}
				}
			}
			while (!this.pendingTasksSemaphore.tryAcquire(Short.MAX_VALUE) && stopwatch.elapsed(TimeUnit.SECONDS) < SHUTDOWN_TIMEOUT_SECONDS);
			
			if (stopwatch.elapsed(TimeUnit.SECONDS) >= SHUTDOWN_TIMEOUT_SECONDS)
			{
				LOGGER.warn("The request queue [" + this.getQueueName() + "] for level [" + this.level.getLevelWrapper() + "] did not shutdown in [" + SHUTDOWN_TIMEOUT_SECONDS + "] seconds. Some unfinished tasks might be left hanging.");
			}
		});
	}
	
	@Override
	public void close()
	{
		DebugRenderer.unregister(this, this.showDebugWireframeConfig);
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	@Override
	public void debugRender(DebugRenderer renderer)
	{
		if (MC_CLIENT.getWrappedClientLevel() != this.level.getClientLevelWrapper())
		{
			return;
		}
		
		for (Map.Entry<Long, RequestQueueEntry> mapEntry : this.waitingTasksBySectionPos.entrySet())
		{
			renderer.renderBox(new DebugRenderer.Box(mapEntry.getKey(), -32f, 64f, 0.05f,
					mapEntry.getValue().networkDataSourceFuture != null ? Color.red
							: this.isSectionAllowedToGenerate(mapEntry.getKey(), Objects.requireNonNull(this.level.getTargetPosForGeneration())) ? Color.gray
							: Color.darkGray
			));
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	protected static class RequestQueueEntry
	{
		/** encapsulates the entire request, including client side queuing and the actual server request */
		public final CompletableFuture<ERequestResult> future = new CompletableFuture<>();
		public final Consumer<FullDataSourceV2> dataSourceConsumer;
		/** will be null if we want to retrieve the LOD regardless of when it was last updated */
		@Nullable
		public final Long updateTimestamp;
		
		
		/** Will be null until the request has been sent to the server */
		@CheckForNull
		public CompletableFuture<FullDataSourceResponseMessage> networkDataSourceFuture;
		
		/** when this reaches zero then the request will be canceled. */
		public int retryAttempts = MAX_RETRY_ATTEMPTS;
		
		
		
		//=============//
		// constructor //
		//=============//
		
		public RequestQueueEntry(
				Consumer<FullDataSourceV2> dataSourceConsumer,
				@Nullable Long updateTimestamp)
		{
			this.dataSourceConsumer = dataSourceConsumer;
			this.updateTimestamp = updateTimestamp;
		}
		
	}
	
	public enum ERequestResult
	{
		SUCCEEDED,
		REQUIRES_SPLITTING,
		FAILED,
	}
	
	
	
}