package com.gtnewhorizons.angelica.mixins.late.client.openblocks;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import openblocks.client.renderer.tileentity.TileEntityTrophyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Trophies render a mob through {@link Render#doRender}, force entity phase here.
 */
@Mixin(value = TileEntityTrophyRenderer.class, remap = false)
public class MixinTileEntityTrophyRenderer {

    @Surround(
        method = "renderTrophy",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V", remap = true))
    private static void angelica$renderTrophyAsEntity(Render render, Entity entity) {
        CapturedRenderingState.INSTANCE.pushCurrentEntityAndItem();

        CapturedRenderingState.INSTANCE.setCurrentRenderedEntity(entity);

        @Surround.Carry boolean nestedInBlockEntity = GbufferPrograms.beginNestedEntityPhase();
    }

    @Surround.Finally
    private static void angelica$renderTrophyAsEntityEnd(@Surround.Carry boolean nestedInBlockEntity) {
        GbufferPrograms.endNestedEntityPhase(nestedInBlockEntity);
        CapturedRenderingState.INSTANCE.popCurrentEntityAndItem();
    }
}
