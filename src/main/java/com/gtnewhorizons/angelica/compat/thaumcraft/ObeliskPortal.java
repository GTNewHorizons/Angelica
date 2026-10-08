package com.gtnewhorizons.angelica.compat.thaumcraft;

import com.gtnewhorizons.angelica.glsm.DisplayListManager;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.minecraft.client.renderer.Tessellator;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Make the obelisk portal work with shaders enabled.
 */
public final class ObeliskPortal {
    private static final int FACE_WIDTH = 256;
    private static final Face[] FACES = Face.values();
    private static final int TEXTURE_WIDTH = FACE_WIDTH * FACES.length;
    private static final double HALF_TEXEL_U = 0.5 / TEXTURE_WIDTH;
    private static final Matrix4fc IDENTITY = new Matrix4f();

    private static GlFramebuffer framebuffer;
    private static int texture;
    private static int textureHeight;

    private static DeferredWorldRenderingPipeline pipeline;
    private static final Matrix4f savedProjection = new Matrix4f();
    private static final Matrix4f savedModelView = new Matrix4f();
    private static final Matrix4f savedTextureMatrix = new Matrix4f();
    private static final Matrix4f faceProjection = new Matrix4f();
    private static final Matrix4f inverseModelView = new Matrix4f();
    private static int savedDrawFramebuffer, savedReadFramebuffer, savedMatrixMode;
    private static int stateDepth = -1;
    private static int capturedFaces;
    private static final double[] faceX = new double[FACES.length];
    private static final double[] faceY = new double[FACES.length];
    private static final double[] faceZ = new double[FACES.length];
    private static final int[] faceHeight = new int[FACES.length];

    private ObeliskPortal() {}

    public enum Face {
        NORTH(0, 0.01F, 1, 0), SOUTH(1, 0.99F, -1, 0),
        WEST(0.01F, 1, 0, -1), EAST(0.99F, 0, 0, 1);

        final double offsetX, offsetZ;
        final int stepX, stepZ;

        Face(double offsetX, double offsetZ, int stepX, int stepZ) {
            this.offsetX = offsetX;
            this.offsetZ = offsetZ;
            this.stepX = stepX;
            this.stepZ = stepZ;
        }

        Matrix4f projection(double x, double y, double z, int height, Matrix4fc modelView, Matrix4f dest) {
            return dest.zero()
                .m00(2 * stepX).m20(2 * stepZ).m30((float) (-1 - 2 * (stepX * x + stepZ * z)))
                .m11(2.0F / height).m31((float) (-1 - 2 * y / height))
                .m02(stepZ).m22(-stepX).m32((float) (-stepZ * x + stepX * z))
                .m33(1).mul(inverseModelView.set(modelView).invert());
        }
    }

    public static void beginFace(Face face, double x, double y, double z, int height) {
        if (pipeline == null) {
            final DeferredWorldRenderingPipeline active = capturingPipeline(height);
            if (active == null) return;
            open(active, height);
        }
        final int i = face.ordinal();
        faceX[i] = x + face.offsetX;
        faceY[i] = y;
        faceZ[i] = z + face.offsetZ;
        faceHeight[i] = height;
        capturedFaces |= 1 << i;
        GLStateManager.glViewport(i * FACE_WIDTH, 0, FACE_WIDTH, textureHeight);
        GLStateManager.setProjectionMatrix(
            face.projection(faceX[i], y, faceZ[i], height, GLStateManager.getModelViewMatrix(), faceProjection));
    }

    private static DeferredWorldRenderingPipeline capturingPipeline(int height) {
        if (!Iris.enabled || ShadowRenderer.ACTIVE || DisplayListManager.isRecording() || height <= 0) return null;
        return Iris.getPipelineManager().getPipelineNullable() instanceof DeferredWorldRenderingPipeline active
            && active.shouldOverrideShaders() ? active : null;
    }

    static void open(DeferredWorldRenderingPipeline active, int height) {
        BatchStateGuard.beforeChange();
        savedProjection.set(GLStateManager.getProjectionMatrix());
        savedModelView.set(GLStateManager.getModelViewMatrix());
        savedTextureMatrix.set(GLStateManager.getTextures().getTextureUnitMatrix(0));
        savedDrawFramebuffer = GLStateManager.getDrawFramebuffer();
        savedReadFramebuffer = GLStateManager.getReadFramebuffer();
        savedMatrixMode = GLStateManager.getMatrixMode().getMode();
        capturedFaces = 0;
        pipeline = active;
        try {
            active.setIsMainBound(false);
            stateDepth = GLStateManager.pushState(StateSet.forMask(GL11.GL_ALL_ATTRIB_BITS));
            GLStateManager.glUseProgram(0);
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            prepareTexture(height);
            framebuffer.bind();
            GLStateManager.disableDepthTest();
            GLStateManager.disableCull();
            GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glDisable(GL11.GL_STENCIL_TEST);
            GLStateManager.glColorMask(true, true, true, true);
            GLStateManager.glClearColor(0, 0, 0, 1);
            GLStateManager.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    public static void finish() {
        if (pipeline == null) return;
        close();
        final int depth = GLStateManager.pushState(StateSet.forMask(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT));
        try {
            GLStateManager.glActiveTexture(GL13.GL_TEXTURE0);
            GLStateManager.enableTexture();
            GLStateManager.disableLighting();
            GLStateManager.disableBlend();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.setTextureMatrix(0, IDENTITY);
            final Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.setColorOpaque_F(1, 1, 1);
            tess.setBrightness(240 | (240 << 16));
            for (Face face : FACES) {
                final int i = face.ordinal();
                if ((capturedFaces & (1 << i)) == 0) continue;
                final double x = faceX[i], y = faceY[i], z = faceZ[i];
                final int height = faceHeight[i];
                final double u0 = (double) i / FACES.length + HALF_TEXEL_U;
                final double u1 = (double) (i + 1) / FACES.length - HALF_TEXEL_U;
                tess.setNormal(face.stepZ, 0, -face.stepX);
                tess.addVertexWithUV(x, y, z, u0, 0);
                tess.addVertexWithUV(x, y + height, z, u0, 1);
                tess.addVertexWithUV(x + face.stepX, y + height, z + face.stepZ, u1, 1);
                tess.addVertexWithUV(x + face.stepX, y, z + face.stepZ, u1, 0);
            }
            tess.draw();
            BatchStateGuard.beforeChange();
        } finally {
            GLStateManager.setTextureMatrix(0, savedTextureMatrix);
            GLStateManager.popStateTo(depth);
        }
    }

    public static void close() {
        final DeferredWorldRenderingPipeline active = pipeline;
        if (active == null) return;
        pipeline = null;
        GLStateManager.setProjectionMatrix(savedProjection);
        GLStateManager.setModelViewMatrix(savedModelView);
        GLStateManager.setTextureMatrix(0, savedTextureMatrix);
        GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, savedDrawFramebuffer);
        GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedReadFramebuffer);
        if (stateDepth >= 0) GLStateManager.popStateTo(stateDepth);
        stateDepth = -1;
        GLStateManager.glMatrixMode(savedMatrixMode);
        active.setIsMainBound(true);
        active.rebindCurrentPass();
    }

    private static void prepareTexture(int height) {
        final int requiredHeight = Math.min(2048, FACE_WIDTH * height);
        if (framebuffer != null && textureHeight == requiredHeight) return;
        destroy();
        texture = GLStateManager.glGenTextures();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEXTURE_WIDTH, requiredHeight,
            0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        framebuffer = new GlFramebuffer();
        framebuffer.addColorAttachment(0, texture);
        framebuffer.drawBuffers(new int[] {0});
        framebuffer.readBuffer(0);
        if (!framebuffer.isComplete()) {
            destroy();
            throw new IllegalStateException("Incomplete Thaumcraft obelisk framebuffer");
        }
        textureHeight = requiredHeight;
    }

    static void destroy() {
        if (framebuffer != null) framebuffer.destroy();
        if (texture != 0) GLStateManager.glDeleteTextures(texture);
        framebuffer = null;
        texture = 0;
        textureHeight = 0;
    }
}
