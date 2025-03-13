package com.seibel.distanthorizons.core.multiplayer.client;

import com.seibel.distanthorizons.core.config.Config;
import com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector;
import com.seibel.distanthorizons.core.logging.ConfigBasedLogger;
import com.seibel.distanthorizons.core.multiplayer.config.SessionConfig;
import com.seibel.distanthorizons.core.multiplayer.fullData.FullDataPayloadReceiver;
import com.seibel.distanthorizons.core.network.event.ScopedNetworkEventSource;
import com.seibel.distanthorizons.core.network.event.internal.CloseInternalEvent;
import com.seibel.distanthorizons.core.network.event.internal.IncompatibleMessageInternalEvent;
import com.seibel.distanthorizons.core.network.messages.base.LevelInitMessage;
import com.seibel.distanthorizons.core.network.messages.base.SessionConfigMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSourceResponseMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataSplitMessage;
import com.seibel.distanthorizons.core.network.messages.fullData.FullDataPartialUpdateMessage;
import com.seibel.distanthorizons.core.network.session.NetworkSession;
import com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftClientWrapper;
import com.seibel.distanthorizons.coreapi.ModInfo;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.List;

public class ClientNetworkState implements Closeable
{
	protected static final ConfigBasedLogger LOGGER = new ConfigBasedLogger(LogManager.getLogger(),
			() -> Config.Common.Logging.logNetworkEvent.get());
	
	private static final IMinecraftClientWrapper MC_CLIENT = SingletonInjector.INSTANCE.get(IMinecraftClientWrapper.class);
	
	
	public final FullDataPayloadReceiver fullDataPayloadReceiver = new FullDataPayloadReceiver();
	
	private final SessionConfig.AnyChangeListener configAnyChangeListener = new SessionConfig.AnyChangeListener(this::sendConfigMessage);
	
	
	private final NetworkSession networkSession = new NetworkSession(null);
	/**
	 * Returns the client used by this instance. <p>
	 * If you need to subscribe to any packet events, create an instance of {@link ScopedNetworkEventSource} using the returned instance.
	 */
	public NetworkSession getSession() { return this.networkSession; }
	
	public SessionConfig sessionConfig = new SessionConfig();
	
	private volatile boolean configReceived = false;
	public boolean isReady() { return this.configReceived; }
	
	private EServerSupportStatus serverSupportStatus = EServerSupportStatus.NONE;
	
	/** Protocol version closest to supported by this mod version */
	@Nullable
	private Integer closestProtocolVersion;
	
	private long serverTimeOffset = 0;
	public long getServerTimeOffset() { return this.serverTimeOffset; }
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public ClientNetworkState()
	{
		this.networkSession.registerHandler(IncompatibleMessageInternalEvent.class, event ->
		{
			if (this.closestProtocolVersion == null 
				|| Math.abs(event.protocolVersion - ModInfo.PROTOCOL_VERSION) < this.closestProtocolVersion)
			{
				this.closestProtocolVersion = event.protocolVersion;
			}
		});
		
		this.networkSession.registerHandler(LevelInitMessage.class, message ->
		{
			// we will also receive this message when we have full support
			if (this.serverSupportStatus == EServerSupportStatus.NONE)
			{
				this.serverSupportStatus = EServerSupportStatus.LEVELS_ONLY;
			}
			
			this.serverTimeOffset = message.serverTime - System.currentTimeMillis();
			LOGGER.info("Server time offset: ["+this.serverTimeOffset+"] ms");
		});
		
		this.networkSession.registerHandler(CloseInternalEvent.class, message ->
		{
			this.configReceived = false;
		});
		
		this.networkSession.registerHandler(FullDataPartialUpdateMessage.class, msg ->
		{
			// Dummy handler to prevent unhandled message warnings
		});
		
		if (MC_CLIENT.connectedToReplay())
		{
			// Prevent handling specific messages because replay server is not interactive.
			// Level keys are still good because they don't affect anything other than level loading.
			
			this.networkSession.registerHandler(SessionConfigMessage.class, message -> { });
			this.networkSession.registerHandler(FullDataSourceResponseMessage.class, message -> { });
			this.networkSession.registerHandler(FullDataSplitMessage.class, message -> { });
		}
		else
		{
			this.networkSession.registerHandler(SessionConfigMessage.class, message ->
			{
				this.serverSupportStatus = EServerSupportStatus.FULL;
				
				LOGGER.info("Connection config has been changed: [" + message.config + "].");
				this.sessionConfig = message.config;
				this.configReceived = true;
			});
			
			this.networkSession.registerHandler(FullDataSplitMessage.class, this.fullDataPayloadReceiver::receiveChunk);
		}
	}
	
	
	
	//==============//
	// send message //
	//==============//
	
	
	
	public void sendConfigMessage()
	{
		this.configReceived = false;
		this.getSession().sendMessage(new SessionConfigMessage(new SessionConfig()));
	}
	
	
	
	//===========//
	// debugging //
	//===========//
	
	public void addDebugMenuStringsToList(List<String> messageList)
	{
		if (this.networkSession.isClosed())
		{
			messageList.add("NetworkSession closed: " + this.networkSession.getCloseReason().getMessage());
			return;
		}
		
		if (this.serverSupportStatus == EServerSupportStatus.NONE && this.closestProtocolVersion != null)
		{
			messageList.add("Incompatible protocol version: [" + this.closestProtocolVersion + "], required: [" + ModInfo.PROTOCOL_VERSION+ "]");
			return;
		}
		
		messageList.add(this.serverSupportStatus.message);
	}
	
	
	
	//==========//
	// shutdown //
	//==========//
	
	@Override
	public void close()
	{
		this.fullDataPayloadReceiver.close();
		this.configAnyChangeListener.close();
		this.networkSession.close();
	}
	
	
	
	//================//
	// helper classes //
	//================//
	
	/**
	 * NONE,        <br>
	 * LEVELS_ONLY, <br>
	 * FULL,        <br>
	 */
	private enum EServerSupportStatus
	{
		NONE("Server does not support DH"),
		LEVELS_ONLY("Server supports shared level keys"),
		FULL("Server has full DH support");
		
		public final String message;
		
		EServerSupportStatus(String message) { this.message = message; }
		
	}
}