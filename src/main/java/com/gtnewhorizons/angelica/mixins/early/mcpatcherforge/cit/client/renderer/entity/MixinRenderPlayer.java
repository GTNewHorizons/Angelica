package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cit.client.renderer.entity;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.prupe.mcpatcher.cit.CITUtils;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayer extends RendererLivingEntity {

    public MixinRenderPlayer(ModelBase modelBase, float shadowSize) {
        super(modelBase, shadowSize);
    }

    @Redirect(
        method = "shouldRenderPass(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)I",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderBiped;getArmorResource(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;ILjava/lang/String;)Lnet/minecraft/util/ResourceLocation;",
            remap = false))
    private ResourceLocation modifyShouldRenderPass(Entity entity, ItemStack stack, int slot, String type,
        AbstractClientPlayer player) {
        return CITUtils
            .getArmorTexture(RenderBiped.getArmorResource(player, stack, slot, type), (EntityLivingBase) entity, stack);
    }

    @Redirect(
        method = "func_82408_c(Lnet/minecraft/client/entity/AbstractClientPlayer;IF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderBiped;getArmorResource(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;ILjava/lang/String;)Lnet/minecraft/util/ResourceLocation;",
            remap = false))
    private ResourceLocation modifyFunc_82408_c(Entity entity, ItemStack stack, int slot, String type,
        AbstractClientPlayer player) {
        return CITUtils
            .getArmorTexture(RenderBiped.getArmorResource(player, stack, slot, type), (EntityLivingBase) entity, stack);
    }
}
