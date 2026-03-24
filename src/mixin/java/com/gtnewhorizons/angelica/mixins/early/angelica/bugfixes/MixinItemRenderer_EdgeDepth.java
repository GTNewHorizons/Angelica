package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix enchantment glint z-fighting on held item edges using stencil masking.
 *
 * Vanilla uses GL_EQUAL to restrict the glint to item pixels, but GPU depth
 * non-determinism between draw calls causes GL_EQUAL to fail on edge quads.
 *
 * This mixin uses a stencil-based check instead of a GL_EQUAL check.
 * 1. During item render: write stencil value 1 wherever the item draws.
 * 2. During each glint renderItemIn2D call:
 *    a. Pre-pass: no color/depth writes, GL_LEQUAL depth test, increment the stencil 1→2
 *       where depth passes. This determines pixel ownership without corrupting the depth buffer (damn potions...)
 *    b. Color pass: draw only where stencil == 2, GL_ALWAYS depth test, decrement the stencil
 *       2→1 to reset for the next glint pass.
 */
@Mixin(value = ItemRenderer.class, priority = 1100)
public class MixinItemRenderer_EdgeDepth {

    @Unique private static boolean angelica$glintMode = false;
    @Unique private static boolean angelica$inPrepass = false;

    // Item render: write stencil value 1 at all visible item pixels

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true),
        remap = false
    )
    private void angelica$stencilWriteStart(CallbackInfo ci) {
        GLStateManager.glEnable(GL11.GL_STENCIL_TEST);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glClearStencil(0);
        GLStateManager.glClear(GL11.GL_STENCIL_BUFFER_BIT);
        GLStateManager.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);
    }

    @Inject(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemIn2D(Lnet/minecraft/client/renderer/Tessellator;FFFFIIF)V", ordinal = 0, remap = true, shift = At.Shift.AFTER),
        remap = false
    )
    private void angelica$stencilWriteEnd(CallbackInfo ci) {
        GLStateManager.glStencilMask(0x00);
    }

    // Glint section: replace GL_EQUAL with stencil-based masking

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 0, remap = false),
        remap = false
    )
    private void angelica$glintStart(int func) {
        angelica$glintMode = true;
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
    }

    @Redirect(
        method = "renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;ILnet/minecraftforge/client/IItemRenderer$ItemRenderType;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDepthFunc(I)V", ordinal = 1, remap = false),
        remap = false
    )
    private void angelica$glintEnd(int func) {
        angelica$glintMode = false;
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glDepthMask(true);
        GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
        GLStateManager.glStencilMask(0xFF);
    }

    // Stencil pre-pass inside renderItemIn2D when in glint mode

    @WrapMethod(method = "renderItemIn2D")
    private static void angelica$glintPrepass(Tessellator tess, float minU, float minV, float maxU, float maxV,
                                               int w, int h, float thickness, Operation<Void> original) {
        if (!angelica$glintMode || angelica$inPrepass) {
            original.call(tess, minU, minV, maxU, maxV, w, h, thickness);
            return;
        }

        angelica$inPrepass = true;

        // Pre-pass: determine pixel ownership via GL_LEQUAL depth test.
        GLStateManager.glColorMask(false, false, false, false);
        GLStateManager.glDepthMask(false);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glStencilMask(0xFF);
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_INCR);
        original.call(tess, minU, minV, maxU, maxV, w, h, thickness);

        // Color pass: draw only at pre-pass pixels.
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 2, 0xFF);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_DECR);
        GLStateManager.glDepthFunc(GL11.GL_ALWAYS);
        original.call(tess, minU, minV, maxU, maxV, w, h, thickness);

        // Restore state for the next glint renderItemIn2D call
        GLStateManager.glStencilMask(0x00);
        GLStateManager.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
        GLStateManager.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);

        angelica$inPrepass = false;
    }
}
