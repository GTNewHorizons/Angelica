package com.seibel.distanthorizons.core.multiplayer.server;

import com.seibel.distanthorizons.core.network.messages.AbstractNetworkMessage;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerPlayerStateManager
{
	private final ConcurrentMap<IServerPlayerWrapper, ServerPlayerState> connectedPlayerStateByPlayerWrapper = new ConcurrentHashMap<>();
	private final ConcurrentMap<IServerPlayerWrapper, MessageQueueState> messageQueueByPlayerWrapper = new ConcurrentHashMap<>();
	
	
	
	//========================//
	// player joining/leaving //
	//========================//
	
	public ServerPlayerState registerJoinedPlayer(IServerPlayerWrapper serverPlayer)
	{
		ServerPlayerState playerState = new ServerPlayerState(serverPlayer);
		this.connectedPlayerStateByPlayerWrapper.put(serverPlayer, playerState);
		return playerState;
	}
	
	public void unregisterLeftPlayer(IServerPlayerWrapper serverPlayer)
	{
		ServerPlayerState playerState = this.connectedPlayerStateByPlayerWrapper.remove(serverPlayer);
		if (playerState != null)
		{
			playerState.close();
		}
	}
	
	
	
	//==========//
	// messages //
	//==========//
	
	public void handlePluginMessage(IServerPlayerWrapper player, AbstractNetworkMessage message)
	{
		MessageQueueState messageQueue = this.messageQueueByPlayerWrapper.computeIfAbsent(player, k -> new MessageQueueState());
		messageQueue.messageQueue.add(message);
		
		ServerPlayerState playerState = this.connectedPlayerStateByPlayerWrapper.get(player);
		if (playerState != null)
		{
			this.handlePluginMessagesFromQueue(playerState, messageQueue);
		}
	}
	
	public void handlePluginMessagesFromQueue(ServerPlayerState playerState)
	{
		MessageQueueState messageQueue = this.messageQueueByPlayerWrapper.computeIfAbsent(playerState.getServerPlayer(), k -> new MessageQueueState());
		this.handlePluginMessagesFromQueue(playerState, messageQueue);
	}
	
	private void handlePluginMessagesFromQueue(ServerPlayerState playerState, MessageQueueState messageQueueState)
	{
		while (!messageQueueState.messageQueue.isEmpty() && messageQueueState.isBeingDrained.compareAndSet(false, true))
		{
			AbstractNetworkMessage message = messageQueueState.messageQueue.poll();
			playerState.networkSession.tryHandleMessage(message);
			
			messageQueueState.isBeingDrained.set(false);
		}
	}
	
	
	
	//=========//
	// getters //
	//=========//
	
	@Nullable
	public ServerPlayerState getConnectedPlayer(IServerPlayerWrapper player) { return this.connectedPlayerStateByPlayerWrapper.get(player); }
	public Collection<ServerPlayerState> getConnectedPlayers() { return this.connectedPlayerStateByPlayerWrapper.values(); }
	public Iterable<ServerPlayerState> getReadyPlayers() { return this.getConnectedPlayers().stream().filter(ServerPlayerState::isReady)::iterator; }
	
	
	private static class MessageQueueState
	{
		public final Queue<AbstractNetworkMessage> messageQueue = new ConcurrentLinkedQueue<>();
		public final AtomicBoolean isBeingDrained = new AtomicBoolean();
		
	}
	
	
}