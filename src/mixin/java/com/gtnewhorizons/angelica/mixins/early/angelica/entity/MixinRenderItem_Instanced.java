package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.GlintClock;
import com.gtnewhorizons.angelica.rendering.items.DroppedItemInstancer;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import com.gtnewhorizons.angelica.rendering.tesr.EntityMaterials;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public abstract class MixinRenderItem_Instanced {

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
    private void angelica$instanceDroppedIcon(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness, @Surround.Local ItemStack stack) {
        @Surround.Carry
        final long part = DroppedItemInstancer.icon(stack, false, maxU, minV, minU, maxV, width, height, thickness);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
    }

    @Surround.Finally("icon")
    private void angelica$endDroppedIcon(@Surround.Carry long part) {
        DroppedItemInstancer.endPart(part);
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
    private void angelica$instanceDroppedGlint(Tessellator tessellator, float maxU, float minV, float minU, float maxV, int width, int height, float thickness) {
        @Surround.Carry
        final long part = DroppedItemInstancer.glint(maxU, minV, minU, maxV, width, height, thickness, EntityMaterials.GLINT);
        @Surround.Skip
        final boolean queued = part == DroppedItemInstancer.SKIP;
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
        DroppedItemInstancer.endPart(part);
    }
}
