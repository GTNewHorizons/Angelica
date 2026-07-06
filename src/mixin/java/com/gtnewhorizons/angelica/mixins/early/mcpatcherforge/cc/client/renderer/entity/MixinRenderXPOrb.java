package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.renderer.entity;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderXPOrb;
import net.minecraft.entity.item.EntityXPOrb;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cc.ColorizeEntity;

@Mixin(RenderXPOrb.class)
public abstract class MixinRenderXPOrb {

    @Redirect(
        method = "doRender(Lnet/minecraft/entity/item/EntityXPOrb;DDDFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Tessellator;setColorRGBA_I(II)V"))
    private void modifyDoRender(Tessellator instance, int color, int alpha, EntityXPOrb entity, double x, double y,
        double z, float p_76986_8_, float p_76986_9_) {
        instance
            .setColorRGBA_I(ColorizeEntity.colorizeXPOrb(color, ((float) entity.xpColor + p_76986_9_) / 2.0F), alpha);
    }
}
