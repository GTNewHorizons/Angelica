package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal_EntityBatch {
    private static final Tracy.ZoneId Z_ENTITY_MODEL_PARTS = Tracy.zoneId("entityModelParts", Tracy.COLOR_CLIENT);

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=entities"))
    private void angelica$beginEntityBatch(CallbackInfo ci) {
        ModelPartBatcher.INSTANCE.begin(ModelPartBatcher.Mode.ENTITIES);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void angelica$flushEntityBatch(CallbackInfo ci) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_ENTITY_MODEL_PARTS);
        try {
            ModelPartBatcher.INSTANCE.flush();
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }
}
