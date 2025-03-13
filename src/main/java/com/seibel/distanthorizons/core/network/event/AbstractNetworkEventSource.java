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

package com.seibel.distanthorizons.core.network.event;

import com.google.common.cache.CacheBuilder;
import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.network.event.internal.AbstractInternalEvent;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import com.seibel.distanthorizons.core.network.messages.MessageRegistry;
import com.seibel.distanthorizons.core.network.session.SessionClosedException;
import com.seibel.distanthorizons.core.network.messages.requests.CancelMessage;
import com.seibel.distanthorizons.core.network.messages.requests.ExceptionMessage;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;

import java.io.InvalidClassException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

public abstract class AbstractNetworkEventSource
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	
	/**
	 * Contains all message handlers. <br>
	 * Grouped by: <br>
	 * - {@link AbstractNetworkMessage} type <br>
	 * - {@link AbstractNetworkEventSource} <br>
	 */
	private final ConcurrentHashMap<
			Class<? extends AbstractNetworkMessage>,
			ConcurrentMap<AbstractNetworkEventSource, Set<INetworkMessageConsumer>>
			> networkHandlerSetByMessageClass = new ConcurrentHashMap<>();
	
	
	private final ConcurrentHashMap<Long, FutureResponseData> pendingFutureById = new ConcurrentHashMap<>();
	/** automatically forgets about ID's after a set time span. */
	private final Set<Long> cancelledFutureIdSet = Collections.newSetFromMap(
			CacheBuilder.newBuilder()
			.expireAfterWrite(10, TimeUnit.SECONDS)
			.<Long, Boolean>build()
			.asMap());
	
	
	
	//=============//
	// constructor //
	//=============//
	
	protected void handleMessage(AbstractNetworkMessage message)
	{
		boolean handled = false;
		
		ConcurrentMap<AbstractNetworkEventSource, Set<INetworkMessageConsumer>> handlersByEventSource = this.networkHandlerSetByMessageClass.get(message.getClass());
		if (handlersByEventSource != null)
		{
			for (Set<INetworkMessageConsumer> handlerSet : handlersByEventSource.values())
			{
				for (INetworkMessageConsumer handler : handlerSet)
				{
					handled = true;
					handler.accept(message);
				}
			}
		}
		
		if (message instanceof AbstractTrackableMessage)
		{
			AbstractTrackableMessage trackableMessage = (AbstractTrackableMessage) message;
			
			FutureResponseData responseData = this.pendingFutureById.get(trackableMessage.futureId);
			if (responseData != null)
			{
				handled = true;
				
				if (message instanceof ExceptionMessage)
				{
					responseData.future.completeExceptionally(((ExceptionMessage) message).exception);
				}
				else if (message.getClass() != responseData.responseClass)
				{
					responseData.future.completeExceptionally(new InvalidClassException("Response with invalid type: expected " + responseData.responseClass.getSimpleName() + ", got:" + message));
				}
				else
				{
					responseData.future.complete(trackableMessage);
				}
			}
			else if (this.cancelledFutureIdSet.remove(trackableMessage.futureId))
			{
				handled = true;
			}
		}
		
		if (!handled && ModInfo.IS_DEV_BUILD)
		{
			LOGGER.warn("Unhandled message: [{}].", message);
		}
	}
	
	
	
	//==================//
	// abstract methods //
	//==================//
	
	public abstract <T extends AbstractNetworkMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation);
	
	
	
	//==================//
	// message handlers //
	//==================//
	
	protected final <T extends AbstractNetworkMessage> void registerHandler(AbstractNetworkEventSource eventSource, Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		if (!AbstractInternalEvent.class.isAssignableFrom(handlerClass))
		{
			MessageRegistry.INSTANCE.getMessageId(handlerClass);
		}
		
		//noinspection unchecked
		this.networkHandlerSetByMessageClass
				.computeIfAbsent(handlerClass, missingHandlerClass -> new ConcurrentHashMap<>())
				.computeIfAbsent(eventSource, missingEventSource -> ConcurrentHashMap.newKeySet())
				.add((m) -> handlerImplementation.accept((T) m));
	}
	
	protected void removeAllHandlers(AbstractNetworkEventSource eventSource)
	{
		for (ConcurrentMap<AbstractNetworkEventSource, Set<INetworkMessageConsumer>> handlerMap : this.networkHandlerSetByMessageClass.values())
		{
			handlerMap.remove(eventSource);
		}
	}
	
	
	
	//================//
	// create message //
	//================//
	
	protected <TResponse extends AbstractTrackableMessage> CompletableFuture<TResponse> createRequest(AbstractTrackableMessage msg, Class<TResponse> responseClass)
	{
		CompletableFuture<TResponse> responseFuture = new CompletableFuture<>();
		responseFuture.whenComplete((response, throwable) ->
		{
			if (throwable instanceof CancellationException)
			{
				this.cancelledFutureIdSet.add(msg.futureId);
				msg.sendResponse(new CancelMessage());
			}
			
			if (!(throwable instanceof SessionClosedException))
			{
				this.pendingFutureById.remove(msg.futureId);
			}
		});
		
		this.pendingFutureById.put(msg.futureId, new FutureResponseData(responseClass, responseFuture));
		
		return responseFuture;
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	public void close()
	{
		this.networkHandlerSetByMessageClass.clear();
		this.completeAllFuturesExceptionally(new SessionClosedException(this.getClass().getSimpleName() + " is closed."));
	}
	private void completeAllFuturesExceptionally(Throwable cause)
	{
		for (FutureResponseData responseData : this.pendingFutureById.values())
		{
			responseData.future.completeExceptionally(cause);
		}
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	private static class FutureResponseData
	{
		public final Class<? extends AbstractTrackableMessage> responseClass;
		public final CompletableFuture<AbstractTrackableMessage> future;
		
		private <T extends AbstractTrackableMessage> FutureResponseData(Class<T> responseClass, CompletableFuture<T> future)
		{
			this.responseClass = responseClass;
			//noinspection unchecked
			this.future = (CompletableFuture<AbstractTrackableMessage>) future;
		}
		
	}
	
	/** Simple wrapper just to make the code here a bit more readable */
	@FunctionalInterface
	private interface INetworkMessageConsumer 
	{
		void accept(AbstractNetworkMessage message);
	}
	
	
}