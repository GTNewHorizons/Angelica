package com.gtnewhorizons.angelica.debug.render;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMBlend;
import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import com.gtnewhorizons.angelica.glsm.GLSMDepthStencilAlpha;
import com.gtnewhorizons.angelica.glsm.GLSMPrograms;
import com.gtnewhorizons.angelica.glsm.GLSMRasterizer;
import com.gtnewhorizons.angelica.glsm.GLSMTextures;
import com.gtnewhorizons.angelica.glsm.GLSMVertexInput;
import com.gtnewhorizons.angelica.glsm.StateSet;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

public final class DebugDrawScope {

    private static final StateSet SET = StateSet.forMask(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_ENABLE_BIT | GL11.GL_POLYGON_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_LINE_BIT);

    private int depth;
    private int program;
    private int vao;
    private int vbo;

    public void beginHud() {
        capture();
        GLSMDepthStencilAlpha.disableDepthTest();
        common();
    }

    public void beginWorld() {
        capture();
        GLSMDepthStencilAlpha.enableDepthTest();
        GLSMDepthStencilAlpha.glDepthFunc(GL11.GL_LEQUAL);
        GLSMRasterizer.glCullFace(GL11.GL_BACK);
        GLSMRasterizer.glFrontFace(GL11.GL_CCW);
        common();
    }

    private void capture() {
        depth = GLSMAttribStack.pushState(SET);
        program = GLSMPrograms.getActiveProgram();
        vao = GLSMVertexInput.getBoundVAO();
        vbo = GLSMBuffers.getBoundVBO();
    }

    public void end() {
        GLSMPrograms.glUseProgram(program);
        GLSMVertexInput.glBindVertexArray(vao);
        GLSMBuffers.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLSMAttribStack.popStateTo(depth);
    }

    private static void common() {
        GLSMDepthStencilAlpha.glDepthMask(false);
        GLSMBlend.enableBlend();
        GLSMBlend.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLSMRasterizer.disableCull();
        GLSMRasterizer.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GLSMRasterizer.disableScissorTest();
        GLSMBlend.glColorMask(true, true, true, true);
        GLSMTextures.glActiveTexture(GL13.GL_TEXTURE0);
        GLSMRasterizer.glLineWidth(1.0F);
    }
}
