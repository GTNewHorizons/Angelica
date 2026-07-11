package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.profiling.RenderClassTimings;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_Tracy {

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=entities", shift = At.Shift.AFTER))
    private void angelica$beginEntityDispatchZone(CallbackInfo ci) {
        Tracy.beginZone("entityDispatch", Tracy.COLOR_CLIENT);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void angelica$endEntityDispatchZone(CallbackInfo ci) {
        Tracy.endZone();
    }

    @WrapOperation(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private boolean angelica$timeEntityRender(RenderManager instance, Entity entity, float partialTicks, Operation<Boolean> original) {
        final long start = System.nanoTime();
        if (TesrAttribution.currentRenderable == null && entity != null) {
            TesrAttribution.currentRenderable = entity.getClass();
        }
        try {
            return original.call(instance, entity, partialTicks);
        } finally {
            TesrAttribution.currentRenderable = null;
            RenderClassTimings.ENTITY.add(entity.getClass(), System.nanoTime() - start);
        }
    }
}
