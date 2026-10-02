package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.items.HeldItemGlint;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
    @Unique private static ItemStack angelica$deferredGlintStack;
    @Unique private static int angelica$deferredGlintPass;

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At("HEAD"),
        remap = false
    )
    private void angelica$rememberItem(EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type, CallbackInfo ci) {
        angelica$stack = stack;
        angelica$pass = pass;
        angelica$type = type;
        if (stack != angelica$deferredGlintStack || pass <= angelica$deferredGlintPass) angelica$deferredGlintStack = null;
    }

    @Surround(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
            ordinal = 0,
            remap = true
        ),
        id = "icon",
        remap = false
    )
    private void angelica$instanceHeldIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        @Surround.Carry
        final long part = angelica$heldIcon(maxU, minV, minU, maxV, width, height, thickness);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("icon")
    private void angelica$endHeldIcon(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    @Unique
    private static long angelica$heldIcon(float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        angelica$iconWidth = width;
        angelica$iconHeight = height;
        final ItemStack stack = angelica$stack;
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_ICON);
        try {
            // A later pass without glint covers the deferred glint of an earlier one, as vanilla draws it after that glint.
            final boolean afterGlint = angelica$deferredGlintStack != null && stack == angelica$deferredGlintStack && !stack.hasEffect(angelica$pass);
            return DroppedItemInstancer.icon(angelica$batchable(angelica$type) && !HeldItemGlint.needsImmediateBase(stack, angelica$pass) ? stack : null, afterGlint, maxU, minV, minU, maxV, width, height, thickness);
        } catch (Throwable t) {
            if (Tracy.FINE_ZONES) Tracy.endZone();
            throw t;
        }
    }

    @ModifyExpressionValue(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"),
        remap = false
    )
    private boolean angelica$queueHeldGlintBlock(boolean hasEffect) {
        if (!hasEffect || !HeldItemGlint.eligible() || !DroppedItemInstancer.queueSkippedGlint(angelica$iconWidth, angelica$iconHeight)) return hasEffect;
        angelica$deferredGlintStack = angelica$stack;
        angelica$deferredGlintPass = angelica$pass;
        SkippedGlintBlock.applyHeldItemExitState();
        return false;
    }

    @Surround(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = {
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
                ordinal = 1,
                remap = true
            ),
            @At(
                value = "INVOKE",
                target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
                ordinal = 2,
                remap = true
            )
        },
        id = "glint",
        remap = false
    )
    private void angelica$instanceHeldGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        @Surround.Carry
        final long part = angelica$heldGlint(maxU, minV, minU, maxV, thickness);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("glint")
    private void angelica$endHeldGlint(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
        if (Tracy.FINE_ZONES) Tracy.endZone();
    }

    @Unique
    private static long angelica$heldGlint(float maxU, float minV, float minU, float maxV, float thickness) {
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_GLINT);
        try {
            return DroppedItemInstancer.glint(maxU, minV, minU, maxV, angelica$iconWidth, angelica$iconHeight, thickness, EntityMaterials.ITEM_GLINT);
        } catch (Throwable t) {
            if (Tracy.FINE_ZONES) Tracy.endZone();
            throw t;
        }
    }

    @Surround(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V",
            remap = true
        ),
        id = "block",
        remap = false
    )
    private void angelica$instanceHeldBlock(RenderBlocks renderBlocks, Block block, int meta, float brightness) {
        @Surround.Carry
        final long part = DroppedItemInstancer.block(angelica$batchable(angelica$type) ? angelica$stack : null, renderBlocks, block, meta, brightness, false);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("block")
    private void angelica$endHeldBlock(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
    }

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J")
    )
    private long angelica$globalGlintTime() {
        return GlintClock.millis();
    }

    @Unique
    private static boolean angelica$batchable(ItemRenderType type) {
        return type == ItemRenderType.EQUIPPED || type == ItemRenderType.ENTITY;
    }
}
