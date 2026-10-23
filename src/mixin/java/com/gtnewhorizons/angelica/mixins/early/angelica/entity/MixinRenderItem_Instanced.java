package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.SkippedGlintBlock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.items.ItemGlint;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem_Instanced {

    @Unique private static final int NO_DEFERRED_GLINT = -1;
    @Unique private static final byte GLINT_PASSES_UNKNOWN = 0, ONE_GLINT_PASS = 1, SEVERAL_GLINT_PASSES = 2;

    @Unique private int angelica$deferredGlintPass;
    @Unique private byte angelica$glintPasses;

    @Surround(method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V", id = "glintState")
    private void angelica$beginGlintState() {
        @Surround.Carry("pass") final int previousPass = angelica$deferredGlintPass;
        @Surround.Carry("glintPasses") final byte previousGlintPasses = angelica$glintPasses;
        angelica$deferredGlintPass = NO_DEFERRED_GLINT;
        angelica$glintPasses = GLINT_PASSES_UNKNOWN;
    }

    @Surround.Finally("glintState")
    private void angelica$restoreGlintState(@Surround.Carry("pass") int previousPass, @Surround.Carry("glintPasses") byte previousGlintPasses) {
        angelica$deferredGlintPass = previousPass;
        angelica$glintPasses = previousGlintPasses;
    }

    @Surround(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V",
            ordinal = 0,
            remap = true
        ),
        id = "icon",
        remap = false
    )
    private void angelica$instanceDroppedIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness,
        @Surround.Local ItemStack stack, @Surround.Local(argsOnly = true, ordinal = 1) int pass) {
        @Surround.Carry
        final long part = DroppedItemInstancer.icon(stack, angelica$deferredGlintPass >= 0 && pass > angelica$deferredGlintPass,
            maxU, minV, minU, maxV, width, height, thickness);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("icon")
    private void angelica$endDroppedIcon(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
    }

    @Surround(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;hasEffect(I)Z"),
        id = "glintBlock",
        remap = false
    )
    private void angelica$beginDroppedGlintBlock() {}

    @Surround.Return("glintBlock")
    private boolean angelica$queueDroppedGlintBlock(boolean hasEffect, ItemStack stack, int pass, @Surround.Local(argsOnly = true) IIcon icon) {
        if (!hasEffect || !ItemGlint.eligible() || !DroppedItemInstancer.canQueueSkippedGlint() || !angelica$oneGlintPass(stack)
            || !DroppedItemInstancer.queueSkippedGlint(icon.getIconWidth(), icon.getIconHeight(), EntityMaterials.GLINT)) return hasEffect;
        angelica$deferredGlintPass = pass;
        SkippedGlintBlock.applyDroppedItemExitState();
        return false;
    }

    @Unique
    private boolean angelica$oneGlintPass(ItemStack stack) {
        // Depends only on the stack, so it holds for every pass and copy of this doRender.
        if (angelica$glintPasses == GLINT_PASSES_UNKNOWN) {
            angelica$glintPasses = angelica$countOneGlintPass(stack) ? ONE_GLINT_PASS : SEVERAL_GLINT_PASSES;
        }
        return angelica$glintPasses == ONE_GLINT_PASS;
    }

    @Unique
    private static boolean angelica$countOneGlintPass(ItemStack stack) {
        final Item item = stack.getItem();
        assert item != null;
        if (!item.requiresMultipleRenderPasses()) return true;
        int glintPasses = 0;
        for (int pass = 0, passes = item.getRenderPasses(stack.getItemDamage()); pass < passes; pass++) {
            if (stack.hasEffect(pass) && ++glintPasses > 1) return false;
        }
        return true;
    }

    @Surround(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
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
    private void angelica$instanceDroppedGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness,
        @Surround.Local ItemStack stack, @Surround.Local(argsOnly = true, ordinal = 1) int pass, @Surround.Local(argsOnly = true) IIcon icon) {
        @Surround.Carry
        final long part = DroppedItemInstancer.glint(maxU, minV, minU, maxV, icon.getIconWidth(), icon.getIconHeight(), thickness, EntityMaterials.GLINT);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
        if (queued && !ModelPartBatcher.INSTANCE.isShadowPass() && angelica$oneGlintPass(stack)) angelica$deferredGlintPass = pass;
    }

    @Surround.Finally("glint")
    private void angelica$endDroppedGlint(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
    }

    @Redirect(
        method = "renderDroppedItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/util/IIcon;IFFFFI)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSystemTime()J")
    )
    private long angelica$globalGlintTime() {
        return GlintClock.millis();
    }

    @Surround(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;renderEntityItem(Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/item/ItemStack;"
                + "FFLjava/util/Random;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/client/renderer/RenderBlocks;I)Z",
            remap = false
        ),
        id = "customDraws"
    )
    private void angelica$beginCustomItemDraws() {
        BatchEligibility.beginExpectedDraws(GLStateManager.drawCalls);
    }

    @Surround.Finally("customDraws")
    private void angelica$endCustomItemDraws() {
        BatchEligibility.endExpectedDraws(GLStateManager.drawCalls);
    }

    @Surround(
        method = "doRender(Lnet/minecraft/entity/item/EntityItem;DDDFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderBlocks;renderBlockAsItem(Lnet/minecraft/block/Block;IF)V"
        ),
        id = "block"
    )
    private void angelica$instanceDroppedBlock(RenderBlocks renderBlocks, Block block, int meta, float brightness, @Surround.Local ItemStack stack) {
        @Surround.Carry
        final long part = DroppedItemInstancer.block(stack, renderBlocks, block, meta, brightness, true);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("block")
    private void angelica$endDroppedBlock(@Surround.Carry long part) {
        DroppedItemInstancer.endBlock(part);
    }
}
