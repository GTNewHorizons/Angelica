package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.mixins.interfaces.VisibleChunkCollectorPool;
import com.gtnewhorizons.angelica.rendering.celeritas.RenderListPool;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.impl.render.chunk.lists.RenderListManager;
import org.embeddedt.embeddium.impl.render.chunk.lists.VisibleChunkCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RenderListManager.class, remap = false)
public abstract class MixinRenderListManager {
    @Unique private final RenderListPool angelica$pool = new RenderListPool();

    @ModifyExpressionValue(method = "submitSearch", at = @At(value = "NEW", target = "org/embeddedt/embeddium/impl/render/chunk/lists/VisibleChunkCollector"))
    private VisibleChunkCollector angelica$attachPool(VisibleChunkCollector collector, @Local(argsOnly = true, ordinal = 1) int regionIdsLength) {
        ((VisibleChunkCollectorPool) collector).angelica$setRecycled(this.angelica$pool.beginSearch(regionIdsLength));
        return collector;
    }
}
