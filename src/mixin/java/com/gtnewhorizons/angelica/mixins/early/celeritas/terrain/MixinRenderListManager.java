package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.mixins.interfaces.RenderListManagerAccessor;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager;
import org.embeddedt.embeddium.impl.render.chunk.lists.VisibleChunkCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.CompletableFuture;

@Mixin(value = RenderListManager.class, remap = false)
public class MixinRenderListManager implements RenderListManagerAccessor {

    @Shadow
    private CompletableFuture<VisibleChunkCollector> currentOcclusionFuture;

    @Override
    public boolean angelica$hasOcclusionFutureInFlight() {
        return currentOcclusionFuture != null;
    }
}
