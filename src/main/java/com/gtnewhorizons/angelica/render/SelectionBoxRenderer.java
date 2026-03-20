package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat2v;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class SelectionBoxRenderer {

    private static GlProgram<SelectionBoxUniforms> program;
    private static int emptyVao;
    private static final Matrix4f mvpMatrix = new Matrix4f();
    private static final float[] colorBuf = new float[4];

    private SelectionBoxRenderer() {}

    public static void init() {
        final boolean needsGS = GLStateManager.wideLineEmulationEnabled;

        final GlShader vs = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:selection_box.vert.glsl", ShaderConstants.EMPTY);
        final GlShader fs = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:selection_box.frag.glsl", ShaderConstants.EMPTY);
        final GlProgram.Builder builder = GlProgram.builder("SelectionBox").attachShader(vs).attachShader(fs);

        GlShader gs = null;
        if (needsGS) {
            gs = ShaderLoader.loadShader(ShaderType.GEOM, "angelica:selection_box.geom.glsl", ShaderConstants.EMPTY);
            builder.attachShader(gs);
        }

        program = builder.link(ctx -> new SelectionBoxUniforms(
                ctx.bindUniform("u_MVP", GlUniformMatrix4f::new),
                ctx.bindUniform("u_Min", GlUniformFloat3v::new),
                ctx.bindUniform("u_Max", GlUniformFloat3v::new),
                ctx.bindUniform("u_Color", GlUniformFloat4v::new),
                needsGS ? ctx.bindUniform("u_ViewportSize", GlUniformFloat2v::new) : null,
                needsGS ? ctx.bindUniform("u_LineWidth", GlUniformFloat::new) : null));

        vs.destroy();
        fs.destroy();
        if (gs != null) gs.destroy();

        emptyVao = GL30.glGenVertexArrays();
    }

    public static void draw(AxisAlignedBB aabb, int color) {
        if (program == null) return;

        final SelectionBoxUniforms uniforms = program.getInterface();

        program.bind();

        GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), mvpMatrix);
        uniforms.mvp.set(mvpMatrix);
        uniforms.min.set((float) aabb.minX, (float) aabb.minY, (float) aabb.minZ);
        uniforms.max.set((float) aabb.maxX, (float) aabb.maxY, (float) aabb.maxZ);

        if (color != -1) {
            colorBuf[0] = ((color >> 16) & 0xFF) / 255.0f;
            colorBuf[1] = ((color >> 8) & 0xFF) / 255.0f;
            colorBuf[2] = (color & 0xFF) / 255.0f;
            colorBuf[3] = 1.0f;
        } else {
            final Color4 c = GLStateManager.getColor();
            colorBuf[0] = c.getRed();
            colorBuf[1] = c.getGreen();
            colorBuf[2] = c.getBlue();
            colorBuf[3] = c.getAlpha();
        }
        uniforms.color.set(colorBuf);

        if (uniforms.viewportSize != null) {
            uniforms.viewportSize.set(GLStateManager.getViewportState().width, GLStateManager.getViewportState().height);
            uniforms.lineWidth.setFloat(GLStateManager.getLineState().getWidth());
        }

        GLStateManager.glBindVertexArray(emptyVao);
        GL11.glDrawArrays(GL11.GL_LINES, 0, 24);
        GLStateManager.glBindVertexArray(0);

        program.unbind();
    }

    public static void destroy() {
        if (program != null) {
            program.destroy();
            program = null;
        }
        if (emptyVao != 0) {
            GL30.glDeleteVertexArrays(emptyVao);
            emptyVao = 0;
        }
    }

    private record SelectionBoxUniforms(GlUniformMatrix4f mvp, GlUniformFloat3v min, GlUniformFloat3v max, GlUniformFloat4v color, GlUniformFloat2v viewportSize, GlUniformFloat lineWidth) {

    }
}
