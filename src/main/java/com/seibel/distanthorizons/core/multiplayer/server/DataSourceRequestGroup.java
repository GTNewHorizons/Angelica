package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.dataObjects.fullData.sources.FullDataSourceV2;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;

import javax.annotation.CheckForNull;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

class DataSourceRequestGroup
{
	public final long pos;
	
	/**
	 * If this variable is true, we definitely know that generation is complete and there's no need for checking column gen steps
	 */
	private boolean worldGenTaskComplete = false;
	
	void markWorldGenTaskComplete()
	{
		this.worldGenTaskComplete = true;
	}
	
	boolean isWorldGenTaskComplete()
	{
		return this.worldGenTaskComplete;
	}
	
	@CheckForNull
	public FullDataSourceV2 fullDataSource = null;
	
	public final ConcurrentMap<Long, RequestData> requestMessages = new ConcurrentHashMap<>();
	public final Semaphore pendingAdditionSemaphore = new Semaphore(Short.MAX_VALUE, true);
	public final AtomicBoolean isClosed = new AtomicBoolean();
	
	
	DataSourceRequestGroup(long pos)
	{
		this.pos = pos;
	}
	
	public boolean tryClose()
	{
		if (!this.isClosed.compareAndSet(false, true))
		{
			return false;
		}
		
		this.pendingAdditionSemaphore.acquireUninterruptibly(Short.MAX_VALUE);
		return true;
	}
	
	public boolean tryAddRequest(RequestData requestData)
	{
		if (!this.pendingAdditionSemaphore.tryAcquire())
		{
			return false;
		}
		
		this.requestMessages.put(requestData.futureId(), requestData);
		this.pendingAdditionSemaphore.release();
		
		return true;
	}
	
	
	public RequestData tryRemoveRequest(long requestId, IHangingRequestTransferConsumer hangingRequestTransferConsumer)
	{
		RequestData removed = this.requestMessages.remove(requestId);
		
		if (this.requestMessages.isEmpty() && this.tryClose())
		{
			hangingRequestTransferConsumer.accept(this.requestMessages.values());
		}
		
		return removed;
	}
	
	
	static class RequestData
	{
		public final ServerPlayerState serverPlayerState;
		public final ServerPlayerState.RateLimiterSet rateLimiterSet;
		
		public final FullDataSourceRequestMessage message;
		public long futureId() { return this.message.futureId; }
		public long sectionPos() { return this.message.sectionPos; }
		
		RequestData(ServerPlayerState serverPlayerState, FullDataSourceRequestMessage message, ServerPlayerState.RateLimiterSet rateLimiterSet)
		{
			this.serverPlayerState = serverPlayerState;
			this.rateLimiterSet = rateLimiterSet;
			this.message = message;
		}
		
	}
	
	/**
	 * While closing this group, some requests may slip through and end up lost. <br>
	 * This is a workaround that allows the caller to transfer these requests to a new group.
	 */
	@FunctionalInterface
	interface IHangingRequestTransferConsumer extends Consumer<Collection<RequestData>> { }
	
}
