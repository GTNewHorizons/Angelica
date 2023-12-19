package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.mob;

import net.minecraft.block.Block;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderMooshroom;
import net.minecraft.entity.passive.EntityMooshroom;
import net.minecraft.util.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.prupe.mcpatcher.mob.MobOverlay;

@Mixin(RenderMooshroom.class)
public abstract class MixinRenderMooshroom extends RenderLiving {

    public MixinRenderMooshroom(ModelBase modelBase, float shadowSize) {
        super(modelBase, shadowSize);
    }

    @Inject(method = "renderEquippedItems(Lnet/minecraft/entity/passive/EntityMooshroom;F)V", at = @At("RETURN"))
    private void modifyRenderEquippedItems1(EntityMooshroom entity, float p_77029_2_, CallbackInfo ci) {
        MobOverlay.finishMooshroom();
    }

    @Redirect(
        method = "renderEquippedItems(Lnet/minecraft/entity/passive/EntityMooshroom;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderMooshroom;bindTexture(Lnet/minecraft/util/ResourceLocation;)V"))
    private void modifyRenderEquippedItems2(RenderMooshroom renderLiving, ResourceLocation resourceLocation,
        EntityMooshroom entity, float p_77029_2_) {
        this.bindTexture(MobOverlay.setupMooshroom(entity, resourceLocation));
    }

    @WrapWithCondition(
        method = "renderEquippedItems(Lnet/minecraft/entity/passive/EntityMooshroom;F)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"))
    private boolean modifyRenderEquippedItems3(RenderBlocks renderBlock, Block block, int i, float f) {
        return !MobOverlay.renderMooshroomOverlay(0.0);
    }
}
