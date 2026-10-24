package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.rendering.tesr.EntityShadowReplay;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.MinecraftForgeClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = RenderGlobal.class, priority = 900) // Lower priority due to Tracy
public class MixinRenderGlobal_EntityReplay {
    @Surround(method = "renderEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderManager;renderEntitySimple(Lnet/minecraft/entity/Entity;F)Z"),
        slice = @Slice(
            from = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=entities"),
            to = @At(value = "INVOKE_STRING", target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V", args = "ldc=blockentities")))
    private void angelica$replayEntity(RenderManager manager, Entity entity, float partialTicks) {
        @Surround.Skip final boolean replayed = EntityShadowReplay.replay(manager, entity, partialTicks, MinecraftForgeClient.getRenderPass());
    }

    @Surround.Skipped
    private boolean angelica$entityReplayed() {
        return true;
    }
}
