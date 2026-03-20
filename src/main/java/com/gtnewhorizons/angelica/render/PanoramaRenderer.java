package com.gtnewhorizons.angelica.render;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.client.rendering.GlUniformFloat2v;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.ShaderConstants;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.render.shader.ShaderLoader;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.function.Function;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Replaces the vanilla main menu panorama renderer with modern opengl.  Significant reduction in draw calls.
 */
public class PanoramaRenderer {

    private static final int FBO_SIZE = 256;
    private static final int PANORAMA_GRID = 8;
    private static final float BLUR_RADIUS = 3.0f;

    private static final int VERTEX_STRIDE = 24;
    private static final int VERTS_PER_FACE = PANORAMA_GRID * PANORAMA_GRID * 4;
    private static final int INDICES_PER_FACE = PANORAMA_GRID * PANORAMA_GRID * 6;

    private static PanoramaRenderer instance;

    private int fboA, fboB, texA, texB, cubeVao, cubeVbo, cubeIbo, emptyVao;
    private GlProgram<CubeUniforms> cubeProgram;
    private GlProgram<BlurUniforms> blurProgram;
    private GlProgram<BlitUniforms> blitProgram;
    private boolean initialized;

    private final Matrix4f mvpScratch = new Matrix4f();
    private final Matrix4f projScratch = new Matrix4f();
    private final Matrix4f baseMVScratch = new Matrix4f();

    // Pre-computed face rotation matrices
    private static final Matrix4f[] FACE_ROTATIONS = new Matrix4f[6];

    static {
        FACE_ROTATIONS[0] = new Matrix4f();
        FACE_ROTATIONS[1] = new Matrix4f().rotateY((float) Math.toRadians(90.0));
        FACE_ROTATIONS[2] = new Matrix4f().rotateY((float) Math.toRadians(180.0));
        FACE_ROTATIONS[3] = new Matrix4f().rotateY((float) Math.toRadians(-90.0));
        FACE_ROTATIONS[4] = new Matrix4f().rotateX((float) Math.toRadians(90.0));
        FACE_ROTATIONS[5] = new Matrix4f().rotateX((float) Math.toRadians(-90.0));
    }

    public static PanoramaRenderer getInstance() {
        if (instance == null) {
            instance = new PanoramaRenderer();
        }
        return instance;
    }

    private void init() {
        if (initialized) return;
        initialized = true;

        texA = createTexture();
        texB = createTexture();
        fboA = createFbo(texA);
        fboB = createFbo(texB);

        buildCubeGeometry();
        emptyVao = GL30.glGenVertexArrays();
        initShaders();
    }

    private void initShaders() {
        cubeProgram = loadProgram("angelica:panorama_cube", "angelica:panorama_cube", "angelica:panorama_cube", CubeUniforms::new);
        blitProgram = loadProgram("angelica:panorama_blit", "angelica:panorama_blur", "angelica:panorama_blit", BlitUniforms::new);
        blurProgram = loadProgram("angelica:panorama_blur", "angelica:panorama_blur", "angelica:panorama_blur", BlurUniforms::new);
        blurProgram.bind();
        blurProgram.getInterface().radius.setFloat(BLUR_RADIUS);
        blurProgram.unbind();
    }

    private static <T> GlProgram<T> loadProgram(String name, String vertBase, String fragBase, Function<ShaderBindingContext, T> factory) {
        final GlShader vert = ShaderLoader.loadShader(ShaderType.VERTEX, vertBase + ".vert", ShaderConstants.EMPTY);
        final GlShader frag = ShaderLoader.loadShader(ShaderType.FRAGMENT, fragBase + ".frag", ShaderConstants.EMPTY);
        try {
            final GlProgram<T> program = GlProgram.builder(name).attachShader(vert).attachShader(frag).link(factory);
            program.bind();
            GL20.glUniform1i(GL20.glGetUniformLocation(program.handle(), "u_Texture"), 0);
            program.unbind();
            return program;
        } finally {
            vert.delete();
            frag.delete();
        }
    }

    private void buildCubeGeometry() {
        final int grid = PANORAMA_GRID;
        final int numPasses = grid * grid;

        final FloatBuffer vbuf = ByteBuffer.allocateDirect(VERTS_PER_FACE * VERTEX_STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();

        for (int k = 0; k < numPasses; k++) {
            final float tx = ((float) (k % grid) / grid - 0.5f) / 64.0f;
            final float ty = ((float) (k / grid) / grid - 0.5f) / 64.0f;
            final float alpha = 1.0f / (k + 1);

            vbuf.put(-1.0f + tx).put(-1.0f + ty).put(1.0f).put(0.0f).put(0.0f).put(alpha);
            vbuf.put(1.0f + tx).put(-1.0f + ty).put(1.0f).put(1.0f).put(0.0f).put(alpha);
            vbuf.put(1.0f + tx).put(1.0f + ty).put(1.0f).put(1.0f).put(1.0f).put(alpha);
            vbuf.put(-1.0f + tx).put(1.0f + ty).put(1.0f).put(0.0f).put(1.0f).put(alpha);
        }
        vbuf.flip();

        final ShortBuffer ibuf = ByteBuffer.allocateDirect(INDICES_PER_FACE * 2).order(ByteOrder.nativeOrder()).asShortBuffer();

        for (int k = 0; k < numPasses; k++) {
            final int base = k * 4;
            ibuf.put((short) base).put((short) (base + 1)).put((short) (base + 2));
            ibuf.put((short) base).put((short) (base + 2)).put((short) (base + 3));
        }
        ibuf.flip();

        cubeVao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(cubeVao);

        cubeVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, cubeVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vbuf, GL15.GL_STATIC_DRAW);

        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, VERTEX_STRIDE, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, VERTEX_STRIDE, 12);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, VERTEX_STRIDE, 20);

        cubeIbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, cubeIbo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ibuf, GL15.GL_STATIC_DRAW);

        GL30.glBindVertexArray(0);
    }

    private static int createTexture() {
        final int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, FBO_SIZE, FBO_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private static int createFbo(int colorTex) {
        final int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorTex, 0);
        final int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Panorama FBO incomplete: 0x" + Integer.toHexString(status));
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    public void destroy() {
        if (!initialized) return;
        initialized = false;
        if (fboA != 0) {
            GL30.glDeleteFramebuffers(fboA);
            fboA = 0;
        }
        if (fboB != 0) {
            GL30.glDeleteFramebuffers(fboB);
            fboB = 0;
        }
        if (texA != 0) {
            GL11.glDeleteTextures(texA);
            texA = 0;
        }
        if (texB != 0) {
            GL11.glDeleteTextures(texB);
            texB = 0;
        }
        if (cubeVao != 0) {
            GL30.glDeleteVertexArrays(cubeVao);
            cubeVao = 0;
        }
        if (cubeVbo != 0) {
            GL15.glDeleteBuffers(cubeVbo);
            cubeVbo = 0;
        }
        if (cubeIbo != 0) {
            GL15.glDeleteBuffers(cubeIbo);
            cubeIbo = 0;
        }
        if (emptyVao != 0) {
            GL30.glDeleteVertexArrays(emptyVao);
            emptyVao = 0;
        }
        if (cubeProgram != null) {
            cubeProgram.delete();
            cubeProgram = null;
        }
        if (blurProgram != null) {
            blurProgram.delete();
            blurProgram = null;
        }
        if (blitProgram != null) {
            blitProgram.delete();
            blitProgram = null;
        }
    }

    public void renderSkybox(int panoramaTimer, float partialTicks, ResourceLocation[] panoramaPaths, Minecraft mc, int screenWidth, int screenHeight, float zLevel) {
        init();

        GLStateManager.glColorMask(true, true, true, true);
        GLStateManager.disableCull();
        GLStateManager.disableScissorTest();
        GLStateManager.disableDepthTest();

        mc.getFramebuffer().unbindFramebuffer();
        GL11.glViewport(0, 0, FBO_SIZE, FBO_SIZE);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        drawCubeFaces(panoramaTimer, partialTicks, panoramaPaths, mc);

        GLStateManager.disableBlend();
        blurProgram.bind();
        GL30.glBindVertexArray(emptyVao);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        final BlurUniforms bu = blurProgram.getInterface();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);
        bu.direction.set(1.0f / FBO_SIZE, 0.0f);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texB);
        bu.direction.set(0.0f, 1.0f / FBO_SIZE);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        mc.getFramebuffer().bindFramebuffer(true);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);

        blitProgram.bind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);

        final float f1 = screenWidth > screenHeight ? 120.0F / screenWidth : 120.0F / screenHeight;
        blitProgram.getInterface().scaleV.setFloat(screenHeight * f1 / 256.0F);
        blitProgram.getInterface().scaleU.setFloat(screenWidth * f1 / 256.0F);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

        GL30.glBindVertexArray(0);
        blitProgram.unbind();

        GLStateManager.enableDepthTest();
        GLStateManager.enableCull();
        GLStateManager.enableBlend();
    }

    private void drawCubeFaces(int panoramaTimer, float partialTicks, ResourceLocation[] panoramaPaths, Minecraft mc) {
        final float pitch = MathHelper.sin(((float) panoramaTimer + partialTicks) / 400.0F) * 25.0F + 20.0F;
        final float yaw = -((float) panoramaTimer + partialTicks) * 0.1F;

        final Matrix4f base = baseMVScratch.identity().rotateX((float) Math.toRadians(180.0)).rotateZ((float) Math.toRadians(90.0))
                .rotateX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw));

        final Matrix4f proj = projScratch.setPerspective((float) Math.toRadians(120.0), 1.0f, 0.05f, 10.0f);

        GLStateManager.enableBlend();
        GLStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glDepthMask(false);

        cubeProgram.bind();
        GL30.glBindVertexArray(cubeVao);
        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);

        final CubeUniforms cu = cubeProgram.getInterface();

        for (int face = 0; face < 6; face++) {
            proj.mul(base, mvpScratch).mul(FACE_ROTATIONS[face]);
            cu.mvp.set(mvpScratch);

            mc.getTextureManager().bindTexture(panoramaPaths[face]);
            GL11.glDrawElements(GL11.GL_TRIANGLES, INDICES_PER_FACE, GL11.GL_UNSIGNED_SHORT, 0);
        }

        GL30.glBindVertexArray(0);
        cubeProgram.unbind();
        GLStateManager.glDepthMask(true);
    }


    private static class CubeUniforms {
        final GlUniformMatrix4f mvp;

        CubeUniforms(ShaderBindingContext ctx) {
            mvp = ctx.bindUniform("u_MVP", GlUniformMatrix4f::new);
        }
    }

    private static class BlurUniforms {
        final GlUniformFloat2v direction;
        final GlUniformFloat radius;

        BlurUniforms(ShaderBindingContext ctx) {
            direction = ctx.bindUniform("u_Direction", GlUniformFloat2v::new);
            radius = ctx.bindUniform("u_Radius", GlUniformFloat::new);
        }
    }

    private static class BlitUniforms {
        final GlUniformFloat scaleV;
        final GlUniformFloat scaleU;

        BlitUniforms(ShaderBindingContext ctx) {
            scaleV = ctx.bindUniform("u_ScaleV", GlUniformFloat::new);
            scaleU = ctx.bindUniform("u_ScaleU", GlUniformFloat::new);
        }
    }
}
