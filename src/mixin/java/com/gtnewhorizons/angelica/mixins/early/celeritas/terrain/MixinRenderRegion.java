package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizons.angelica.rendering.culling.RenderPassIndex;
import com.gtnewhorizons.angelica.mixins.interfaces.SectionRenderDataStorageRegionAccessor;
import org.embeddedt.embeddium.impl.render.chunk.data.SectionRenderDataStorage;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RenderRegion.class, remap = false)
public abstract class MixinRenderRegion {

    @Inject(method = "createStorage", at = @At("RETURN"))
    private void angelica$tagStorageWithRegion(TerrainRenderPass pass, RenderPassConfiguration<?> renderPassConfiguration, CallbackInfoReturnable<SectionRenderDataStorage> cir) {
        final SectionRenderDataStorage storage = cir.getReturnValue();
        if (storage instanceof SectionRenderDataStorageRegionAccessor a) {
            a.angelica$setRegion((RenderRegion) (Object) this);
            a.angelica$setPassIndex(RenderPassIndex.indexOf(pass));
        }
    }
}
