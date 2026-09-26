package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CombinedGlint;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.items.HeldItemGlint;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Match item depth and prevent coplanar extrusion faces from blending the same glint layer twice. */
@Mixin(value = ItemRenderer.class, priority = 1100)
public class MixinItemRenderer_EdgeDepth {

    @Unique private static final Tracy.ZoneId angelica$Z_HELD_ITEM = Tracy.zoneId("heldItem", Tracy.COLOR_CLIENT);
    @Unique private static IIcon angelica$glintIcon;
    @Unique private static boolean angelica$combinedGlint;
    @Unique private static boolean angelica$stencilPrepared;
    @Unique private static boolean angelica$preparingStencil;
    @Unique private static boolean angelica$firstLayerMarked;
    @Unique private static int angelica$nesting;
    @Unique private static final IIcon[] angelica$savedIcons = new IIcon[8];
    @Unique private static final boolean[] angelica$savedCombined = new boolean[8];
    @Unique private static final boolean[] angelica$savedPrepared = new boolean[8];

    @Inject(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At("HEAD"), remap = false)
    private void angelica$enterRenderItem(CallbackInfo ci) {
        final int depth = angelica$nesting++;
        if (depth < angelica$savedIcons.length) {
            angelica$savedIcons[depth] = angelica$glintIcon;
            angelica$savedCombined[depth] = angelica$combinedGlint;
            angelica$savedPrepared[depth] = angelica$stencilPrepared;
        }
        angelica$glintIcon = null;
        angelica$combinedGlint = false;
        angelica$stencilPrepared = false;
        angelica$firstLayerMarked = false;
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_ITEM);
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At("RETURN"), remap = false)
    private void angelica$exitRenderItem(CallbackInfo ci) {
        if (Tracy.FINE_ZONES) Tracy.endZone();
        final int depth = --angelica$nesting;
        final boolean saved = depth >= 0 && depth < angelica$savedIcons.length;
        angelica$glintIcon = saved ? angelica$savedIcons[depth] : null;
        angelica$combinedGlint = saved && angelica$savedCombined[depth];
        angelica$stencilPrepared = saved && angelica$savedPrepared[depth];
        angelica$firstLayerMarked = false;
        if (saved) angelica$savedIcons[depth] = null;
        if (depth < 0) angelica$nesting = 0;
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true), remap = false)
    private void angelica$prepareGlintStencil(EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type, CallbackInfo ci,
                                             @Local Tessellator tess) {
        angelica$preparingStencil = !ShadowRenderer.ACTIVE && !GLStateManager.isRecordingDisplayList()
            && !TessellatorManager.isCurrentlyCapturing() && !TessellatorManager.shouldInterceptDraw(tess)
            && GLStateManager.getDepthTest().isEnabled() && GLStateManager.getDepthState().isEnabled()
            && HeldItemGlint.canResetStencil() && HeldItemGlint.eligible() && HeldItemGlint.needsImmediateBase(stack, pass);
        if (angelica$preparingStencil) {
            GLStateManager.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
            GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
            GLStateManager.glStencilMask(1);
            GLStateManager.glStencilFunc(GL11.GL_ALWAYS, 0, 1);
            GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
        }
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true, shift = At.Shift.AFTER),
        remap = false
    )
    private void angelica$finishGlintStencil(CallbackInfo ci) {
        if (angelica$preparingStencil) GLStateManager.glPopAttrib();
        angelica$stencilPrepared = angelica$preparingStencil;
        angelica$preparingStencil = false;
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true, shift = At.Shift.AFTER),
        remap = false
    )
    private void angelica$rememberGlintIcon(CallbackInfo ci, @Local IIcon icon) {
        angelica$glintIcon = icon;
    }

    // Wrap the geometry method so mods redirecting the caller retain their glint color and blending.
    @WrapMethod(method = "renderItemIn2D")
    private static void angelica$matchGlintGeometry(Tessellator tess, float minU, float minV, float maxU, float maxV,
                                                   int width, int height, float thickness, Operation<Void> original) {
        if (angelica$glintIcon != null && thickness == 0.0625F
            && ((width == 256 && height == 256)
                || (width == angelica$glintIcon.getIconWidth() && height == angelica$glintIcon.getIconHeight()))
            && minU == 0 && minV == 0 && maxU == 1 && maxV == 1) {
            width = angelica$glintIcon.getIconWidth();
            height = angelica$glintIcon.getIconHeight();
            if (!ShadowRenderer.ACTIVE && !GLStateManager.isRecordingDisplayList()
                && !TessellatorManager.isCurrentlyCapturing() && !TessellatorManager.shouldInterceptDraw(tess)) {
                if (angelica$combinedGlint) return;
                final boolean secondLayer = angelica$firstLayerMarked;
                final boolean combined = !secondLayer && HeldItemGlint.begin();
                // Depth selects the surface, stencil limits it to one blend.
                // The base draw's stencil reset or the first layer's marks avoid a masked full-screen clear.
                GLStateManager.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                try {
                    GLStateManager.glDepthMask(false);
                    GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
                    GLStateManager.glStencilMask(1);
                    if (secondLayer) {
                        // Same pixels as the first layer, so removing its marks leaves the stencil reset.
                        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 1, 1);
                        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
                    } else {
                        if (!angelica$stencilPrepared) {
                            GLStateManager.glClearStencil(0);
                            GLStateManager.glClear(GL11.GL_STENCIL_BUFFER_BIT);
                        }
                        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 0, 1);
                        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
                    }
                    original.call(tess, minU, minV, maxU, maxV, width, height, thickness);
                } finally {
                    GLStateManager.glPopAttrib();
                    if (combined) CombinedGlint.end();
                }
                angelica$combinedGlint = combined;
                angelica$firstLayerMarked = !combined && !secondLayer && HeldItemGlint.layersCoverSamePixels();
                return;
            }
        }
        original.call(tess, minU, minV, maxU, maxV, width, height, thickness);
    }
}
