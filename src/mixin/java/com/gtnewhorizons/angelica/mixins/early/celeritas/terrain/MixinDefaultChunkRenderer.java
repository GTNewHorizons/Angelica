package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import org.embeddedt.embeddium.impl.render.chunk.DefaultChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {

    @Unique private static final SectionRenderDataStorage.BatchCacheParams angelica$CULLED = new SectionRenderDataStorage.BatchCacheParams(true);
    @Unique private static final SectionRenderDataStorage.BatchCacheParams angelica$UNCULLED = new SectionRenderDataStorage.BatchCacheParams(false);

    @Redirect(method = "render", at = @At(value = "NEW", target = "org/embeddedt/embeddium/impl/render/chunk/data/SectionRenderDataStorage$BatchCacheParams"))
    private SectionRenderDataStorage.BatchCacheParams angelica$sharedParams(boolean cull) {
        return cull ? angelica$CULLED : angelica$UNCULLED;
    }
}
