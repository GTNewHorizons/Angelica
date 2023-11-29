package com.gtnewhorizons.angelica.mixins.early.angelica.hudcaching;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.EXTBlendFuncSeparate;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(OpenGlHelper.class)
public class MixinOpenGlHelper_HUDCaching {

    @Shadow
    private static boolean openGL14;

    @Shadow
    public static boolean field_153211_u;

    // TODO don't override, do modify variable or inject via ASM with GlStateManager

    /**
     * @author ee
     * @reason eee
     */
    @Overwrite
    public static void glBlendFunc(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        if (HUDCaching.renderingCacheOverride && dstFactorAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            srcFactorAlpha = GL11.GL_ONE;
            dstFactorAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        if (openGL14) {
            if (field_153211_u) {
                EXTBlendFuncSeparate.glBlendFuncSeparateEXT(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
            } else {
                GL14.glBlendFuncSeparate(srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha);
            }
        } else {
            GL11.glBlendFunc(srcFactor, dstFactor);
        }
    }

}
