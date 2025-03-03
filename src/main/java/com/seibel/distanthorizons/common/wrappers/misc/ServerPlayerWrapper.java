package com.seibel.distanthorizons.common.wrappers.misc;

import com.google.common.base.Objects;
import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentMap;

/**
 * This wrapper transparently ensures that the underlying {@link ServerPlayer} is always valid,
 * unless the player has disconnected.
 */
public class ServerPlayerWrapper implements IServerPlayerWrapper
{
	private static final ConcurrentMap<ServerGamePacketListenerImpl, ServerPlayerWrapper> serverPlayerWrapperMap = new MapMaker().weakKeys().weakValues().makeMap();
	
	private final ServerGamePacketListenerImpl connection;
	
	
	
	//=============//
	// constructor //
	//=============//
	
	public static ServerPlayerWrapper getWrapper(ServerPlayer serverPlayer) 
	{ return serverPlayerWrapperMap.computeIfAbsent(serverPlayer.connection, ignored -> new ServerPlayerWrapper(serverPlayer.connection)); }
	
	private ServerPlayerWrapper(ServerGamePacketListenerImpl connection) { this.connection = connection; }
	
	
	
	//=========//
	// getters //
	//=========//
	
	private ServerPlayer getServerPlayer() { return this.connection.player; }
	
	@Override
	public String getName() { return this.getServerPlayer().getName().getString(); }
	
	@Override
	public IServerLevelWrapper getLevel()
	{
		ServerLevel level = ((IMixinServerPlayer) this.getServerPlayer()).distantHorizons$getDimensionChangeDestination();
		if (level == null)
		{
			#if MC_VER < MC_1_20_1
			level = this.getServerPlayer().getLevel();
			#else
			level = this.getServerPlayer().serverLevel();
			#endif
		}
		
		return ServerLevelWrapper.getWrapper(level);
	}
	
	@Override
	public Vec3d getPosition()
	{
		Vec3 position = this.getServerPlayer().position();
		return new Vec3d(position.x, position.y, position.z);
	}
	
	@Override
	public int getViewDistance() { return this.getServerPlayer().server.getPlayerList().getViewDistance(); }
	
	@Override
	public SocketAddress getRemoteAddress()
	{
		#if MC_VER >= MC_1_19_4
		return this.getServerPlayer().connection.getRemoteAddress();
		#else // < 1.19.4
		return this.getServerPlayer().connection.connection.getRemoteAddress();
		#endif
	}
	
	
	
	//================//
	// base overrides //
	//================//
	
	@Override
	public Object getWrappedMcObject() { return this.getServerPlayer(); }
	
	@Override
	public String toString() { return "Wrapped{" + this.getServerPlayer() + "}"; }
	
	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		if (!(obj instanceof ServerPlayerWrapper))
		{
			return false;
		}
		ServerPlayerWrapper that = (ServerPlayerWrapper) obj;
		return Objects.equal(this.connection, that.connection);
	}
	
	@Override
	public int hashCode() { return Objects.hashCode(this.connection); }
	
}
