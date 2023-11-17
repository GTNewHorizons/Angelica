package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.mixins.interfaces.IHasClientChunkProvider;
import com.gtnewhorizons.angelica.mixins.interfaces.IWorldClientExt;
import me.jellysquid.mods.sodium.client.world.IChunkProviderClientExt;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldClient.class)
public abstract class MixinWorldClient implements BlockRenderView, IHasClientChunkProvider, IWorldClientExt {
    @Shadow
    private ChunkProviderClient clientChunkProvider;

    @Override
    public ChunkProviderClient getClientChunkProvider() {
        return clientChunkProvider;
    }

    @Override
    public void doPostChunk(int x, int z) {
        ((IChunkProviderClientExt)this.clientChunkProvider).doPostChunk(x, z);
    }
}
