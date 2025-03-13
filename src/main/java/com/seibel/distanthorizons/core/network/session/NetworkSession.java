package com.seibel.distanthorizons.core.network.session;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.network.event.AbstractNetworkEventSource;
import com.seibel.distanthorizons.core.network.event.internal.CloseInternalEvent;
import com.seibel.distanthorizons.core.network.event.internal.ProtocolErrorInternalEvent;
import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.network.messages.AbstractTrackableMessage;
import com.seibel.distanthorizons.core.network.messages.base.CloseReasonMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IPluginPacketSender;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class NetworkSession extends AbstractNetworkEventSource
{
	private static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	private static final IPluginPacketSender PACKET_SENDER = SingletonInjector.INSTANCE.get(IPluginPacketSender.class);
	
	private static final AtomicInteger lastId = new AtomicInteger();
	public final int id = lastId.getAndIncrement();
	
	/**
	 * When non-null, any received data will be ignored. <br>
	 * This does not include wrong versions, which are ignored without setting this flag,
	 * to allow multi-compat servers.
	 */
	private final AtomicReference<Throwable> closeReason = new AtomicReference<>();
	public Throwable getCloseReason() { return this.closeReason.get(); }
	public boolean isClosed() { return this.closeReason.get() != null; }
	
	@Nullable
	public final IServerPlayerWrapper serverPlayer;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public NetworkSession(@Nullable IServerPlayerWrapper serverPlayer)
	{
		this.serverPlayer = serverPlayer;
		
		this.registerHandler(CloseReasonMessage.class, msg ->
		{
			this.close(new SessionClosedException(msg.reason));
		});
		
		this.registerHandler(ProtocolErrorInternalEvent.class, event ->
		{
			if (event.replyWithCloseReason)
			{
				this.sendMessage(new CloseReasonMessage("Internal error on other side"));
			}
			
			this.close(event.reason);
		});
	}
	
	
	
	//==================//
	// message handling //
	//==================//
	
	public void tryHandleMessage(AbstractNetworkMessage message)
	{
		if (this.closeReason.get() != null)
		{
			return;
		}
		
		message.setSession(this);
		
		try
		{
			LOGGER.debug("Received message: ["+message+"].");
			this.handleMessage(message);
		}
		catch (Throwable e)
		{
			LOGGER.error("Failed to handle the message. New messages will be ignored.", e);
			LOGGER.error("Message: ["+message+"]");
			this.close();
		}
	}
	
	@Override
	public <T extends AbstractNetworkMessage> void registerHandler(Class<T> handlerClass, Consumer<T> handlerImplementation)
	{
		if (this.closeReason.get() != null)
		{
			return;
		}
		
		this.registerHandler(this, handlerClass, handlerImplementation);
	}
	
	
	
	//==============//
	// send message //
	//==============//
	
	public <TResponse extends AbstractTrackableMessage> CompletableFuture<TResponse> sendRequest(AbstractTrackableMessage msg, Class<TResponse> responseClass)
	{
		msg.setSession(this);
		CompletableFuture<TResponse> responseFuture = this.createRequest(msg, responseClass);
		this.sendMessage(msg);
		return responseFuture;
	}
	
	public void sendMessage(AbstractNetworkMessage message)
	{
		if (this.closeReason.get() != null)
		{
			return;
		}
		
		LOGGER.debug("Sending message: ["+message+"]");
		message.setSession(this);
		
		try
		{
			if (this.serverPlayer != null)
			{
				PACKET_SENDER.sendToClient(this.serverPlayer, message);
			}
			else
			{
				PACKET_SENDER.sendToServer(message);
			}
		}
		catch (Throwable throwable)
		{
			LOGGER.info("Failed to send a message", throwable);
			LOGGER.info("Message: ["+message+"]");
			this.close(throwable);
		}
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	public void close(Throwable closeReason)
	{
		if (!this.closeReason.compareAndSet(null, closeReason))
		{
			return;
		}
		
		try
		{
			this.handleMessage(new CloseInternalEvent());
		}
		catch (Throwable ignored) { }
		
		super.close();
	}
	
}