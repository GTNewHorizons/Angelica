package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(RenderBiped.class)
public abstract class MixinRenderBiped extends RenderLiving {

    public MixinRenderBiped(ModelBase modelBase, float shadowSize) {
        super(modelBase, shadowSize);
    }

    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/entity/EntityLiving;IF)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderBiped;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void modifyShouldRenderPass(RenderBiped instance, ResourceLocation resourceLocation,
        EntityLiving entityLiving, int slotId, float p_77032_3_) {
        this.bindTexture(
            CITUtils.getArmorTexture(resourceLocation, entityLiving, entityLiving.func_130225_q(3 - slotId)));
    }

    @Redirect(
        method = "func_82408_c(Lnet/minecraft/entity/EntityLiving;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderBiped;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))

    private void modifyFunc_82408_c(RenderBiped instance, ResourceLocation resourceLocation, EntityLiving entityLiving,
        int slotId, float p_82408_3_) {
        this.bindTexture(
            CITUtils.getArmorTexture(resourceLocation, entityLiving, entityLiving.func_130225_q(3 - slotId)));
    }
}
