package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.celeritas.SmoothBiomeColorCache;
import com.gtnewhorizons.angelica.rendering.celeritas.WorldClientExtension;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;


@Mixin(WorldClient.class)
public class MixinWorldClient implements ChunkTrackerHolder, WorldClientExtension {
    @Unique private final ChunkTracker celeritas$tracker = new ChunkTracker();
    @Unique private final SmoothBiomeColorCache celeritas$smoothBiomeColorCache = new SmoothBiomeColorCache((IBlockAccess) this);

    @Override
    public ChunkTracker sodium$getTracker() {
        return this.celeritas$tracker;
    }

    @Override
    public SmoothBiomeColorCache celeritas$getSmoothBiomeColorCache() {
        return this.celeritas$smoothBiomeColorCache;
    }
}
