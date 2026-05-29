package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.IBlockAccess;

import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.angelica.compat.ModStatus;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaChunkTracker;
import com.gtnewhorizons.angelica.rendering.celeritas.CubeStatusTracker;
import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.WorldClientExtension;

@Mixin(WorldClient.class)
public class MixinWorldClient implements ChunkTrackerHolder, WorldClientExtension {
    @Unique private final ChunkTracker celeritas$tracker = ModStatus.isCubicChunksLoaded ? new CubeStatusTracker(false) : new AngelicaChunkTracker();
    @Unique private final SmoothBiomeColorCache celeritas$smoothBiomeColorCache = new SmoothBiomeColorCache((IBlockAccess) this);

    @Override
    public ChunkTracker sodium$getTracker() {
        return celeritas$tracker;
    }

    @Override
    public SmoothBiomeColorCache celeritas$getSmoothBiomeColorCache() {
        return this.celeritas$smoothBiomeColorCache;
    }
}
