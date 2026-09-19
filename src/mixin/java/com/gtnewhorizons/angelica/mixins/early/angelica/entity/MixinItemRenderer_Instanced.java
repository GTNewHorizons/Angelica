package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer_Instanced {

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
            ordinal = 0
        )
    )
    private void angelica$instanceHeldIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemRenderType type) {
        DroppedItemInstancer.icon(angelica$batchable(type) ? stack : null, tessellator, maxU, minV, minU, maxV, width, height, thickness, original);
    }

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
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
    private void angelica$instanceHeldGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        DroppedItemInstancer.glint(tessellator, maxU, minV, minU, maxV, width, height, thickness, original);
    }

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"
        )
    )
    private void angelica$instanceHeldBlock(RenderBlocks renderBlocks, Block block, int meta, float brightness, Operation<Void> original, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemRenderType type) {
        DroppedItemInstancer.block(angelica$batchable(type) ? stack : null, renderBlocks, block, meta, brightness, false, original);
    }

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J")
    )
    private long angelica$globalGlintTime() {
        return GlintClock.millis();
    }

    private static boolean angelica$batchable(ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.ENTITY;
    }
}
