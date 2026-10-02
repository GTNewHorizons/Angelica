package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import net.coderbot.iris.gbuffer_overrides.matching.SpecialCondition;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.entity.Entity;

public final class EntityRenderScope {

    private EntityRenderScope() {}

    public static boolean begin(Entity entity, boolean lightning) {
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();
        CapturedRenderingState.INSTANCE.pushCurrentEntityColor();
        CapturedRenderingState.INSTANCE.setCurrentRenderedEntity(entity);
        TesrAttribution.currentRenderable = entity != null ? entity.getClass() : null;
        if (lightning) {
            GbufferPrograms.setupSpecialRenderCondition(SpecialCondition.LIGHTNING);
        }
        return GbufferPrograms.beginNestedEntityPhase();
    }

    public static void end(Class<?> prevRenderable, boolean lightning, boolean nested) {
        GbufferPrograms.endNestedEntityPhase(nested);
        if (lightning) {
            GbufferPrograms.teardownSpecialRenderCondition();
        }
        CapturedRenderingState.INSTANCE.popCurrentEntityColor();
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
        TesrAttribution.currentRenderable = prevRenderable;
    }
}
