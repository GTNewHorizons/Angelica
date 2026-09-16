package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem_Instanced {

    @WrapOperation(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
            ordinal = 0
        )
    )
    private void angelica$instanceDroppedIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original, @Local ItemStack stack) {
        DroppedItemInstancer.icon(stack, tessellator, maxU, minV, minU, maxV, width, height, thickness, original);
    }

    @WrapOperation(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = {
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
                ordinal = 1
            ),
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
                ordinal = 2
            )
        }
    )
    private void angelica$instanceDroppedGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        DroppedItemInstancer.glint(tessellator, maxU, minV, minU, maxV, width, height, thickness, original);
    }

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J")
    )
    private long angelica$globalGlintTime() {
        return GlintClock.millis();
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;renderEntityItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/item/ItemStack;FFLjava/util/Random;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/client/renderer/RenderBlocks;I)Z",
            remap = false
        )
    )
    private void angelica$beginCustomItemDraws(CallbackInfo ci) {
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
    }

    @Inject(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;renderEntityItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/item/ItemStack;FFLjava/util/Random;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/client/renderer/RenderBlocks;I)Z",
            shift = At.Shift.AFTER,
            remap = false
        )
    )
    private void angelica$endCustomItemDraws(CallbackInfo ci) {
        BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
    }

    @WrapOperation(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"
        )
    )
    private void angelica$instanceDroppedBlock(RenderBlocks renderBlocks, Block block, int meta, float brightness, Operation<Void> original, @Local ItemStack stack) {
        DroppedItemInstancer.block(stack, renderBlocks, block, meta, brightness, true, original);
    }
}
