package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.compat.mojang.BlockRenderView;
import com.gtnewhorizons.angelica.mixins.interfaces.IHasClientChunkProvider;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldClient.class)
public abstract class MixinWorldClient implements BlockRenderView, IHasClientChunkProvider {
    @Shadow
    private ChunkProviderClient clientChunkProvider;

    @Override
    public ChunkProviderClient getClientChunkProvider() {
        return clientChunkProvider;
    }
}
