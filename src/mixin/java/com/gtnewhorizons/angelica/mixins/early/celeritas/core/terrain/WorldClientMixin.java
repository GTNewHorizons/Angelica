package com.gtnewhorizons.angelica.mixins.early.celeritas.core.terrain;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.taumc.celeritas.impl.extensions.WorldClientExtension;
import org.taumc.celeritas.impl.world.biome.SmoothBiomeColorCache;

@Mixin(WorldClient.class)
public class WorldClientMixin implements ChunkTrackerHolder, WorldClientExtension {
    private final ChunkTracker celeritas$tracker = new ChunkTracker();
    private final SmoothBiomeColorCache celeritas$smoothBiomeColorCache = new SmoothBiomeColorCache((IBlockAccess)this);

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }

    @Override
    public SmoothBiomeColorCache celeritas$getSmoothBiomeColorCache() {
        return celeritas$smoothBiomeColorCache;
    }
}
