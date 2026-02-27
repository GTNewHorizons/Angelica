package com.gtnewhorizons.angelica.mixins.early.angelica.vbo;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizon.gtnhlib.client.renderer.TessellatorManager;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Framebuffer.class)
public abstract class MixinFramebuffer {

    @Shadow
    public abstract void bindFramebufferTexture();

    @Shadow
    public abstract void unbindFramebufferTexture();


    @Unique
    private static IVertexArrayObject angelica$vao;

    @Unique
    private static int angelica$vaoWidth = -1;

    @Unique
    private static int angelica$vaoHeight = -1;

    /**
     * @author Sisyphus
     * @reason Replace FFP with statically allocated Buffers.
     */
    @Overwrite
    public void framebufferRender(int width, int height) {
        if (angelica$vaoWidth != width || angelica$vaoHeight != height) {
            final DirectTessellator tessellator = DirectTessellator.startCapturing();
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(0, height, 0, 0, 0);
            tessellator.addVertexWithUV(width, height, 0, 1, 0);
            tessellator.addVertexWithUV(width, 0, 0, 1, 1);
            tessellator.addVertexWithUV(0, 0, 0.0D, 0.0D, 1);
            tessellator.draw();
            if (angelica$vao == null) {
                angelica$vao = tessellator.uploadToVBO(VertexBufferType.MUTABLE);
            } else {
                tessellator.updateToVBO(angelica$vao.getVBO());
            }
            DirectTessellator.stopCapturing();
            angelica$vaoWidth = width;
            angelica$vaoHeight = height;
        }
        GLStateManager.glColorMask(true, true, true, false);
        GLStateManager.disableDepthTest();
        GLStateManager.glDepthMask(false);
        GLStateManager.glMatrixMode(GL11.GL_PROJECTION);
        GLStateManager.glLoadIdentity();
        GLStateManager.glOrtho(0.0D, width, height, 0.0D, 1000.0D, 3000.0D);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(0.0F, 0.0F, -2000.0F);
        GLStateManager.glViewport(0, 0, width, height);
        GLStateManager.enableTexture();
        GLStateManager.disableLighting();
        GLStateManager.disableAlphaTest();
        GLStateManager.disableBlend();
        GLStateManager.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.bindFramebufferTexture();
        angelica$vao.render();
        this.unbindFramebufferTexture();
        GLStateManager.glDepthMask(true);
        GLStateManager.glColorMask(true, true, true, true);
    }
}
