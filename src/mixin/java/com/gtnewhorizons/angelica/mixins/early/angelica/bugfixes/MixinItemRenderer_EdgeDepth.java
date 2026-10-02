package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.ffp.CombinedGlint;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.rendering.items.HeldItemGlint;
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

/** Match item depth and prevent coplanar extrusion faces from blending the same glint layer twice. */
@Mixin(value = ItemRenderer.class, priority = 1100)
public class MixinItemRenderer_EdgeDepth {

    @Unique private static final String RENDER_ITEM = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V";
    @Unique private static final Tracy.ZoneId angelica$Z_HELD_ITEM = Tracy.zoneId("heldItem", Tracy.COLOR_CLIENT);
    @Unique private static IIcon angelica$glintIcon;
    @Unique private static boolean angelica$combinedGlint;
    @Unique private static boolean angelica$stencilPrepared;
    @Unique private static boolean angelica$firstLayerMarked;
    @Unique private static boolean angelica$drawingGlint;

    @Surround(method = RENDER_ITEM, id = "heldItem", remap = false)
    private void angelica$enterRenderItem(EntityLivingBase entity, ItemStack stack, int pass, ItemRenderType type) {
        @Surround.Carry final IIcon glintIcon = angelica$glintIcon;
        @Surround.Carry("combined") final boolean combinedGlint = angelica$combinedGlint;
        @Surround.Carry("prepared") final boolean stencilPrepared = angelica$stencilPrepared;
        angelica$glintIcon = null;
        angelica$combinedGlint = false;
        angelica$stencilPrepared = false;
        angelica$firstLayerMarked = false;
        if (Tracy.FINE_ZONES) Tracy.beginZone(angelica$Z_HELD_ITEM);
    }

    @Surround.Finally("heldItem")
    private void angelica$exitRenderItem(@Surround.Carry IIcon glintIcon, @Surround.Carry("combined") boolean combinedGlint,
                                         @Surround.Carry("prepared") boolean stencilPrepared) {
        if (Tracy.FINE_ZONES) Tracy.endZone();
        angelica$glintIcon = glintIcon;
        angelica$combinedGlint = combinedGlint;
        angelica$stencilPrepared = stencilPrepared;
        angelica$firstLayerMarked = false;
    }

    @Surround(method = RENDER_ITEM,
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true),
        id = "stencil", remap = false)
    private void angelica$prepareGlintStencil(Tessellator tess,
                                             @Surround.Local(argsOnly = true) ItemStack stack, @Surround.Local(argsOnly = true) int pass) {
        @Surround.Carry
        final boolean preparing = !ShadowRenderer.ACTIVE && !GLStateManager.isRecordingDisplayList()
            && !TessellatorManager.isCurrentlyCapturing() && !TessellatorManager.shouldInterceptDraw(tess)
            && GLStateManager.getDepthTest().isEnabled() && GLStateManager.getDepthState().isEnabled()
            && HeldItemGlint.canResetStencil() && HeldItemGlint.eligible() && HeldItemGlint.needsImmediateBase(stack, pass);
        if (preparing) {
            BatchStateGuard.suspend();
            try {
                GLStateManager.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
                GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
                GLStateManager.glStencilMask(1);
                GLStateManager.glStencilFunc(GL11.GL_ALWAYS, 0, 1);
                GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
            } finally {
                BatchStateGuard.resume();
            }
        }
    }

    @Surround.Finally("stencil")
    private void angelica$finishGlintStencil(@Surround.Carry boolean preparing) {
        if (preparing) {
            BatchStateGuard.suspend();
            try {
                GLStateManager.glPopAttrib();
            } finally {
                BatchStateGuard.resume();
            }
        }
        angelica$stencilPrepared = preparing;
    }

    @Surround.Return("stencil")
    private void angelica$rememberGlintIcon(@Surround.Local IIcon icon) {
        angelica$glintIcon = icon;
    }

    // Surround the geometry method so mods redirecting the caller retain their glint color and blending.
    @Surround(method = "renderItemIn2D", id = "glintGeometry")
    private static void angelica$matchGlintGeometry(Tessellator tess, float minU, float minV, float maxU, float maxV,
                                                   int width, int height, float thickness) {
        @Surround.Skip
        final boolean drawn = !angelica$drawingGlint && angelica$drawGlintGeometry(tess, minU, minV, maxU, maxV, width, height, thickness);
    }

    @Unique
    private static boolean angelica$drawGlintGeometry(Tessellator tess, float minU, float minV, float maxU, float maxV,
                                                     int width, int height, float thickness) {
        final IIcon icon = angelica$glintIcon;
        if (icon == null || thickness != 0.0625F
            || !((width == 256 && height == 256) || (width == icon.getIconWidth() && height == icon.getIconHeight()))
            || minU != 0 || minV != 0 || maxU != 1 || maxV != 1) {
            return false;
        }
        final int iconWidth = icon.getIconWidth();
        final int iconHeight = icon.getIconHeight();
        if (ShadowRenderer.ACTIVE || GLStateManager.isRecordingDisplayList()
            || TessellatorManager.isCurrentlyCapturing() || TessellatorManager.shouldInterceptDraw(tess)) {
            if (width == iconWidth && height == iconHeight) return false;
            angelica$drawGlint(tess, iconWidth, iconHeight, thickness);
            return true;
        }
        if (angelica$combinedGlint) return true;
        final boolean secondLayer = angelica$firstLayerMarked;
        BatchStateGuard.suspend();
        try {
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
                angelica$drawGlint(tess, iconWidth, iconHeight, thickness);
            } finally {
                GLStateManager.glPopAttrib();
                if (combined) CombinedGlint.end();
            }
            angelica$combinedGlint = combined;
            angelica$firstLayerMarked = !combined && !secondLayer && HeldItemGlint.layersCoverSamePixels();
        } finally {
            BatchStateGuard.resume();
        }
        return true;
    }

    @Unique
    private static void angelica$drawGlint(Tessellator tess, int width, int height, float thickness) {
        angelica$drawingGlint = true;
        try {
            ItemRenderer.renderItemIn2D(tess, 0, 0, 1, 1, width, height, thickness);
        } finally {
            angelica$drawingGlint = false;
        }
    }
}
