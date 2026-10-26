package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.PostProcessingHelper;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMCurrentAttribs;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMLighting;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer {

    @Shadow
    public int framebufferTexture;


    /**
     * @author Sisyphus
     * @reason Replace FFP with statically allocated Buffers.
     */
    @Overwrite
    public void framebufferRender(int width, int height) {
        GLSMBlend.glColorMask(true, true, true, false);
        GLSMDepthStencilAlpha.disableDepthTest();
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMMatrices.glMatrixMode(GL11.GL_PROJECTION);
        GLSMMatrices.glLoadIdentity();
        GLSMMatrices.glMatrixMode(GL11.GL_MODELVIEW);
        GLSMMatrices.glLoadIdentity();
        GLSMRasterizer.glViewport(0, 0, width, height);
        GLSMTextures.enableTexture();
        GLSMLighting.disableLighting();
        GLSMDepthStencilAlpha.disableAlphaTest();
        GLSMBlend.disableBlend();
        GLSMRasterizer.disableCull();
        GLSMCurrentAttribs.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, framebufferTexture);
        PostProcessingHelper.bindFullscreenVAO();
        PostProcessingHelper.drawFullscreenQuad();
        PostProcessingHelper.unbindVAO();
        GLSMTextures.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GLSMDepthStencilAlpha.glDepthMask(true);
        GLSMBlend.glColorMask(true, true, true, true);
    }
}
