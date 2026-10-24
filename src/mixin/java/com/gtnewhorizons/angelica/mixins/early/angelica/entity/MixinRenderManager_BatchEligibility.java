package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IBatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityShadowReplay;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderManager.class)
public class MixinRenderManager_BatchEligibility {

    @Redirect(method = "func_147939_a", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void angelica$observeDoRender(Render render, Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        final IBatchEligibility holder = (IBatchEligibility) render;
        final byte state = holder.angelica$batchState();
        final boolean recording = AngelicaConfig.entityShadowReplayEnabled() && EntityShadowReplay.beginRenderer(render, entity, partialTicks, state);
        BatchEligibility.begin(state, GLStateManager.drawCalls);
        boolean completed = false;
        try {
            render.doRender(entity, x, y, z, yaw, partialTicks);
            completed = true;
        } finally {
            final byte next = BatchEligibility.end(state, GLStateManager.drawCalls);
            if (recording) EntityShadowReplay.endRenderer(completed, next);
            if (next != state) {
                holder.angelica$setBatchState(next);
                BatchEligibility.onStateChange(render, next);
            }
        }
    }
}
