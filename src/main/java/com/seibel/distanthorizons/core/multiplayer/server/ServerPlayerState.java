package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.config.listeners.ConfigChangeListener;
import com.seibel.distanthorizons.core.level.AbstractDhServerLevel;
import com.seibel.distanthorizons.core.multiplayer.config.SessionConfig;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadSender;
import com.seibel.distanthorizons.core.network.event.internal.IncompatibleMessageInternalEvent;
import com.seibel.distanthorizons.core.network.messages.base.CloseReasonMessage;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import com.seibel.distanthorizons.core.network.messages.base.SessionConfigMessage;
import com.seibel.distanthorizons.core.network.event.internal.CloseInternalEvent;
import com.seibel.distanthorizons.core.network.exceptions.RateLimitedException;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceRequestMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.util.ratelimiting.SupplierBasedRateAndConcurrencyLimiter;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPlayerState implements Closeable
{
	private final ConfigChangeListener<String> levelKeyPrefixChangeListener
			= new ConfigChangeListener<>(Config.Server.levelKeyPrefix, this::onLevelKeyPrefixConfigChanged);
	private final SessionConfig.AnyChangeListener configAnyChangeListener = new SessionConfig.AnyChangeListener(this::onSessionConfigChanged);
	
	
	private String lastLevelKey = "";
	
	
	public final NetworkSession networkSession;
	public IServerPlayerWrapper getServerPlayer() { return this.networkSession.serverPlayer; }
	
	@NotNull
	public final SessionConfig sessionConfig = new SessionConfig();
	public boolean isReady() { return this.sessionConfig.constrainingConfig != null; }
	
	public final FullDataPayloadSender fullDataPayloadSender;
	
	private final ConcurrentHashMap<AbstractDhServerLevel, RateLimiterSet> rateLimiterSets = new ConcurrentHashMap<>();
	public RateLimiterSet getRateLimiterSet(AbstractDhServerLevel level) { return this.rateLimiterSets.computeIfAbsent(level, ignored -> new RateLimiterSet()); }
	public void clearRateLimiterSets() { this.rateLimiterSets.clear(); }
	
	
	//==============//
	// constructors //
	//==============//
	
	public ServerPlayerState(IServerPlayerWrapper serverPlayer)
	{
		this.networkSession = new NetworkSession(serverPlayer);
		this.fullDataPayloadSender = new FullDataPayloadSender(this.networkSession, this.sessionConfig::getMaxDataTransferSpeed);
		
		this.networkSession.registerHandler(SessionConfigMessage.class, (sessionConfigMessage) ->
		{
			this.sessionConfig.constrainingConfig = sessionConfigMessage.config;
			this.sendLevelKey();
			this.networkSession.sendMessage(new SessionConfigMessage(this.sessionConfig));
		});
		
		this.networkSession.registerHandler(CloseInternalEvent.class, event -> {
			// No-op. prevents "Unhandled message" log entries
		});
		
		this.networkSession.registerHandler(IncompatibleMessageInternalEvent.class, event ->
		{
			// Client won't understand this message, but it's still enough to display incompatible protocol error
			this.networkSession.sendMessage(new CloseReasonMessage("Incompatible protocol version"));
			this.close();
		});
	}
	
	
	
	//=================//
	// client updating //
	//=================//
	
	private void onLevelKeyPrefixConfigChanged(String newLevelKey) { this.sendLevelKey(); }
	private void sendLevelKey()
	{
		if (Config.Server.sendLevelKeys.get())
		{
			// let the client's know about the change
			String levelKey = this.getServerPlayer().getLevel().getKeyedLevelDimensionName();
			if (!levelKey.equals(this.lastLevelKey))
			{
				this.lastLevelKey = levelKey;
				this.networkSession.sendMessage(new LevelInitMessage(levelKey));
			}
		}
	}
	
	private void onSessionConfigChanged() { this.networkSession.sendMessage(new SessionConfigMessage(this.sessionConfig)); }
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override
	public void close()
	{
		this.fullDataPayloadSender.close();
		this.levelKeyPrefixChangeListener.close();
		this.configAnyChangeListener.close();
		this.networkSession.close();
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	public class RateLimiterSet
	{
		public final SupplierBasedRateAndConcurrencyLimiter<FullDataSourceRequestMessage> generationRequestRateLimiter = new SupplierBasedRateAndConcurrencyLimiter<>(
				() -> Config.Server.generationRequestRateLimit.get(),
				msg -> {
					msg.sendResponse(new RateLimitedException("Full data request rate limit: " + ServerPlayerState.this.sessionConfig.getGenerationRequestRateLimit()));
				}
		);
		
		public final SupplierBasedRateAndConcurrencyLimiter<FullDataSourceRequestMessage> syncOnLoginRateLimiter = new SupplierBasedRateAndConcurrencyLimiter<>(
				() -> Config.Server.syncOnLoadRateLimit.get(),
				msg -> {
					msg.sendResponse(new RateLimitedException("Sync on login rate limit: " + ServerPlayerState.this.sessionConfig.getSyncOnLoginRateLimit()));
				}
		);
		
	}
	
}