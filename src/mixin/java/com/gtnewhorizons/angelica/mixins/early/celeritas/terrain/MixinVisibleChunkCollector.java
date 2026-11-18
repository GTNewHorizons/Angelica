package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.mixins.interfaces.VisibleChunkCollectorPool;
import com.gtnewhorizons.angelica.rendering.celeritas.RenderListPool;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.VisibleChunkCollector;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = VisibleChunkCollector.class, remap = false)
public abstract class MixinVisibleChunkCollector implements VisibleChunkCollectorPool {
    @Unique private ChunkRenderList[] angelica$recycled;

    @Override
    public void angelica$setRecycled(ChunkRenderList[] slot) {
        this.angelica$recycled = slot;
    }

    @Redirect(method = "createRenderList", at = @At(value = "NEW", target = "org/embeddedt/embeddium/impl/render/chunk/lists/ChunkRenderList"))
    private ChunkRenderList angelica$reuseRenderList(RenderRegion region) {
        return RenderListPool.acquire(this.angelica$recycled, region);
    }
}
