package com.gtnewhorizons.angelica.debug.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
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
        GLStateManager.disableDepthTest();
        common();
    }

    public void beginWorld() {
        capture();
        GLStateManager.enableDepthTest();
        GLStateManager.glDepthFunc(GL11.GL_LEQUAL);
        GLStateManager.glCullFace(GL11.GL_BACK);
        GLStateManager.glFrontFace(GL11.GL_CCW);
        common();
    }

    private void capture() {
        depth = GLStateManager.pushState(SET);
        program = GLStateManager.getActiveProgram();
        vao = GLStateManager.getBoundVAO();
        vbo = GLStateManager.getBoundVBO();
    }

    public void end() {
        GLStateManager.glUseProgram(program);
        GLStateManager.glBindVertexArray(vao);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GLStateManager.popStateTo(depth);
    }

    private static void common() {
        GLStateManager.glDepthMask(false);
        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.disableCull();
        GLStateManager.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GLStateManager.disableScissorTest();
        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
        GLStateManager.glLineWidth(1.0F);
    }
}
