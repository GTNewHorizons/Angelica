package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.items.HeldItemGlint;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer_Instanced {
    @Unique private static final Tracy.ZoneId angelica$Z_HELD_ICON = Tracy.zoneId("heldIcon", Tracy.COLOR_CLIENT);
    @Unique private static final Tracy.ZoneId angelica$Z_HELD_GLINT = Tracy.zoneId("heldGlint", Tracy.COLOR_CLIENT);
    @Unique private static ItemStack angelica$stack;
    @Unique private static int angelica$pass;
    @Unique private static ItemRenderType angelica$type;
    @Unique private static int angelica$iconWidth, angelica$iconHeight;

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void angelica$rememberItem(EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type, CallbackInfo ci) {
        angelica$stack = stack;
        angelica$pass = pass;
        angelica$type = type;
    }

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
            ordinal = 0
        )
    )
    private void angelica$instanceHeldIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, Operation<Void> original) {
        angelica$iconWidth = width;
        angelica$iconHeight = height;
        final ItemStack stack = angelica$stack;
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_ICON);
        try {
            DroppedItemInstancer.icon(angelica$batchable(angelica$type) && !HeldItemGlint.needsImmediateBase(stack, angelica$pass) ? stack : null, tessellator, maxU, minV, minU, maxV, width, height, thickness, original);
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
    }

    @ModifyExpressionValue(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"),
        remap = false
    )
    private boolean angelica$queueHeldGlintBlock(boolean hasEffect) {
        if (!hasEffect || !HeldItemGlint.eligible() || !DroppedItemInstancer.queueSkippedGlint(angelica$iconWidth, angelica$iconHeight)) return hasEffect;
        SkippedGlintBlock.applyHeldItemExitState();
        return false;
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
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_GLINT);
        try {
            DroppedItemInstancer.glint(tessellator, maxU, minV, minU, maxV, angelica$iconWidth, angelica$iconHeight, thickness, EntityMaterials.ITEM_GLINT, original);
        } finally {
            if (Tracy.FINE_ZONES) Tracy.endZone();
        }
    }

    @WrapOperation(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"
        )
    )
    private void angelica$instanceHeldBlock(RenderBlocks renderBlocks, Block block, int meta, float brightness, Operation<Void> original) {
        DroppedItemInstancer.block(angelica$batchable(angelica$type) ? angelica$stack : null, renderBlocks, block, meta, brightness, false, original);
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
