package com.gtnewhorizons.angelica.render;

import com.gtnewhorizon.gtnhlib.client.renderer.vao.IVertexArrayObject;
import com.gtnewhorizon.gtnhlib.client.renderer.vao.VertexBufferType;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.util.AxisAlignedBB;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat4v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

public final class SelectionBoxRenderer {

    private static GlProgram<SelectionBoxUniforms> program;
    private static IVertexArrayObject vao;
    private static ByteBuffer edges;
    private static final Matrix4f mvpMatrix = new Matrix4f();
    private static final float[] colorBuf = new float[4];

    private static final int VERT_COUNT = 24;
    private static final float[] UNIT_EDGES = {
        0,0,0, 1,0,0,  1,0,0, 1,0,1,
        1,0,1, 0,0,1,  0,0,1, 0,0,0,
        0,1,0, 1,1,0,  1,1,0, 1,1,1,
        1,1,1, 0,1,1,  0,1,1, 0,1,0,
        0,0,0, 0,1,0,  1,0,0, 1,1,0,
        1,0,1, 1,1,1,  0,0,1, 0,1,1
    };

    private SelectionBoxRenderer() {}

    public static void init() {
        final GlShader vs = ShaderLoader.loadShader(ShaderType.VERTEX, "angelica:selection_box.vert.glsl", ShaderConstants.EMPTY);
        final GlShader fs = ShaderLoader.loadShader(ShaderType.FRAGMENT, "angelica:selection_box.frag.glsl", ShaderConstants.EMPTY);
        program = GlProgram.builder("SelectionBox").attachShader(vs).attachShader(fs).link(ctx -> new SelectionBoxUniforms(
                ctx.bindUniform("u_MVP", GlUniformMatrix4f::new),
                ctx.bindUniform("u_Color", GlUniformFloat4v::new)));
        vs.destroy();
        fs.destroy();

        edges = BufferUtils.createByteBuffer(UNIT_EDGES.length * Float.BYTES);
        edges.asFloatBuffer().put(UNIT_EDGES);
        vao = VertexBufferType.IMMUTABLE.allocate(DefaultVertexFormat.POSITION, GL11.GL_LINES, edges, VERT_COUNT);
    }

    public static void draw(AxisAlignedBB aabb, int color) {
        if (program == null || vao == null) return;

        final float minX = (float) aabb.minX, minY = (float) aabb.minY, minZ = (float) aabb.minZ;
        final float extX = (float) (aabb.maxX - aabb.minX);
        final float extY = (float) (aabb.maxY - aabb.minY);
        final float extZ = (float) (aabb.maxZ - aabb.minZ);

        if (GLStateManager.wideLineEmulationEnabled || (AngelicaConfig.enableIris && IrisApi.getInstance().isShaderPackInUse())) {
            drawAsLines(minX, minY, minZ, extX, extY, extZ, color);
        } else {
            drawFast(minX, minY, minZ, extX, extY, extZ, color);
        }
    }

    private static void drawFast(float minX, float minY, float minZ, float extX, float extY, float extZ, int color) {
        final SelectionBoxUniforms uniforms = program.getInterface();

        program.bind();

        GLStateManager.getProjectionMatrix().mul(GLStateManager.getModelViewMatrix(), mvpMatrix);
        mvpMatrix.translate(minX, minY, minZ).scale(extX, extY, extZ);
        uniforms.mvp.set(mvpMatrix);

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

        vao.render();

        program.unbind();
    }

    // The streaming drawer's line path widens the edges in the FFP vertex shader or sends them to a pack's gbuffers_line
    private static void drawAsLines(float minX, float minY, float minZ, float extX, float extY, float extZ, int color) {
        GLStateManager.glPushMatrix();
        GLStateManager.glTranslatef(minX, minY, minZ);
        GLStateManager.glScalef(extX, extY, extZ);

        final Color4 prev = GLStateManager.getColor();
        final float prevRed = prev.getRed(), prevGreen = prev.getGreen(), prevBlue = prev.getBlue(), prevAlpha = prev.getAlpha();
        final boolean overrideColor = color != -1;
        if (overrideColor) {
            GLStateManager.glColor4f(((color >> 16) & 0xFF) / 255.0f, ((color >> 8) & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, 1.0f);
        }

        edges.clear();
        TessellatorStreamingDrawer.drawPacked(edges, GL11.GL_LINES, DefaultVertexFormat.POSITION.getVertexFlags(), VERT_COUNT);

        if (overrideColor) {
            GLStateManager.glColor4f(prevRed, prevGreen, prevBlue, prevAlpha);
        }
        GLStateManager.glPopMatrix();
    }

    public static void destroy() {
        if (program != null) {
            program.destroy();
            program = null;
        }
        if (vao != null) {
            vao.delete();
            vao = null;
        }
    }

    private record SelectionBoxUniforms(GlUniformMatrix4f mvp, GlUniformFloat4v color) {

    }
}
