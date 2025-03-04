package com.seibel.distanthorizons.common.wrappers.misc;

import com.google.common.base.Objects;
import com.google.common.collect.MapMaker;
import com.seibel.distanthorizons.common.wrappers.world.ServerLevelWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.misc.IServerPlayerWrapper;
import com.seibel.distanthorizons.core.wrapperInterfaces.world.IServerLevelWrapper;
import com.seibel.distanthorizons.core.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/**
 * This wrapper transparently ensures that the underlying ServerPlayer is always valid,
 * unless the player has disconnected.
 */
public class ServerPlayerWrapper implements IServerPlayerWrapper
{
    private static final ConcurrentMap<UUID, ServerPlayerWrapper> serverPlayerWrapperMap = new MapMaker().weakKeys().weakValues().makeMap();

    private final EntityPlayerMP serverPlayer;



    //=============//
    // constructor //
    //=============//

    public static ServerPlayerWrapper getWrapper(EntityPlayerMP serverPlayer)
    { return serverPlayerWrapperMap.computeIfAbsent(serverPlayer.getUniqueID(), ignored -> new ServerPlayerWrapper(serverPlayer)); }

    private ServerPlayerWrapper(EntityPlayerMP serverPlayer) { this.serverPlayer = serverPlayer; }



    //=========//
    // getters //
    //=========//

    private EntityPlayerMP getServerPlayer() { return this.serverPlayer; }

    @Override
    public String getName() { return this.getServerPlayer().getDisplayName(); }

    @Override
    public IServerLevelWrapper getLevel()
    {
        WorldServer level = null; // TODO((IMixinServerPlayer) this.getServerPlayer()).distantHorizons$getDimensionChangeDestination();
        if (level == null)
        {
			level = (WorldServer)this.getServerPlayer().worldObj;
        }

        return ServerLevelWrapper.getWrapper(level);
    }

    @Override
    public Vec3d getPosition()
    {
        Entity position = this.getServerPlayer();
        return new Vec3d(position.posX, position.posY, position.posZ);
    }

    @Override
    public int getViewDistance() { return this.getServerPlayer().mcServer.getConfigurationManager().getViewDistance(); }

    @Override
    public SocketAddress getRemoteAddress()
    {
        return this.getServerPlayer().playerNetServerHandler.netManager.getSocketAddress(); // TODO?
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
        return Objects.equal(this.serverPlayer.getUniqueID(), that.serverPlayer.getUniqueID());
    }

    @Override
    public int hashCode() { return Objects.hashCode(this.serverPlayer.getUniqueID()); }

}
