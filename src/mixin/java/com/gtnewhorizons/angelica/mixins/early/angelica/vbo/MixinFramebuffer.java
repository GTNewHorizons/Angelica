package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizon.gtnhlib.client.renderer.postprocessing.PostProcessingHelper;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer {

    @Shadow
    public abstract void bindFramebufferTexture();

    @Shadow
    public abstract void unbindFramebufferTexture();

    /**
     * @author Sisyphus
     * @reason Replace FFP with statically allocated Buffers.
     */
    @Overwrite
    public void framebufferRender(int width, int height) {
        GLStateManager.glColorMask(true, true, true, false);
        GLStateManager.disableDepthTest();
        GLStateManager.glDepthMask(false);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glViewport(0, 0, width, height);
        GLStateManager.enableTexture();
        GLStateManager.disableLighting();
        GLStateManager.disableAlphaTest();
        GLStateManager.disableBlend();
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.bindFramebufferTexture();
        PostProcessingHelper.bindFullscreenVAO();
        PostProcessingHelper.drawFullscreenQuad();
        PostProcessingHelper.unbindVAO();
        this.unbindFramebufferTexture();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
    }
}
