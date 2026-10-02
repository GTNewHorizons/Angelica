package com.gtnewhorizons.angelica.mixins.early.angelica.tracy;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.profiling.RenderClassTimings;
import com.gtnewhorizons.angelica.rendering.tesr.TesrAttribution;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public class MixinRenderGlobal_Tracy {
    private static final Tracy.ZoneId Z_ENTITY_DISPATCH = Tracy.zoneId("entityDispatch", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_ENTITY_RENDER = Tracy.zoneId("entityRender", Tracy.COLOR_CLIENT);

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=entities", shift = At.Shift.AFTER))
    private void angelica$beginEntityDispatchZone(CallbackInfo ci) {
        Tracy.beginZone(Z_ENTITY_DISPATCH);
    }

    @Inject(method = "renderEntities", at = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities"))
    private void angelica$endEntityDispatchZone(CallbackInfo ci) {
        Tracy.endZone();
    }

    @Surround(method = "renderEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"))
    private void angelica$timeEntityRender(RenderManager instance, Entity entity) {
        @Surround.Carry
        long start = System.nanoTime();
        @Surround.Carry
        Class<?> prevRenderable = TesrAttribution.currentRenderable;
        if (TesrAttribution.currentRenderable == null && entity != null) {
            TesrAttribution.currentRenderable = entity.getClass();
        }
        if (Tracy.FINE_ZONES) {
            Tracy.setGpuZonesEnabled(false);
            Tracy.beginZone(Z_ENTITY_RENDER);
        }
    }

    @Surround.Finally
    private void angelica$endEntityRender(RenderManager instance, Entity entity, @Surround.Carry long start, @Surround.Carry Class<?> prevRenderable) {
        if (Tracy.FINE_ZONES) {
            Tracy.endZone();
            Tracy.setGpuZonesEnabled(true);
        }
        TesrAttribution.currentRenderable = prevRenderable;
        RenderClassTimings.ENTITY.add(entity.getClass(), System.nanoTime() - start);
    }
}
