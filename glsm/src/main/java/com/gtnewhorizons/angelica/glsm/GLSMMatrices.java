package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.stacks.MatrixModeStack;
import lombok.SneakyThrows;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.beforeUncapturedStateChange;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.initConfig;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;

public final class GLSMMatrices {

    private GLSMMatrices() {}

    private static void beforeMatrixChange(GLContextState c) {
        beforeMatrixChange(c, false);
    }

    private static void beforeMatrixChange(GLContextState c, boolean absolute) {
        c.exitCapture.matrixChange(absolute);
        if (isUncapturedMatrix(c)) beforeUncapturedStateChange();
    }

    private static boolean isUncapturedMatrix(GLContextState c) {
        final int mode = c.matrixMode.getMode();
        return mode == GL11.GL_PROJECTION || (mode == GL11.GL_TEXTURE && c.activeTextureUnit.getValue() != 0);
    }

    private static final MethodHandle MAT4_STACK_CURR_DEPTH;

    static {
        try {
            final Field curr = Matrix4fStack.class.getDeclaredField("curr");
            curr.setAccessible(true);
            MAT4_STACK_CURR_DEPTH = MethodHandles.lookup().unreflectGetter(curr);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    public static int getMatrixStackDepth(Matrix4fStack stack) {
        // JOML's curr is 0-based (0 = no pushes), but OpenGL starts at 1 (base matrix counts)
        return (int) MAT4_STACK_CURR_DEPTH.invokeExact(stack) + 1;
    }

    public static void glMatrixMode(int mode) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.MATRIX_MODE);
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordMatrixMode(mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        mod(glCtx.matrixMode).setMode(mode);
    }

    public static void glLoadMatrix(FloatBuffer m) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx, true);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            final Matrix4f matrix = new Matrix4f().set(m);
            m.rewind();
            DisplayListManager.recordLoadMatrix(matrix);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).set(m);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glLoadMatrix(DoubleBuffer m) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx, true);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert double buffer to float buffer for recording
            glCtx.conversionMatrix4d.set(m);
            m.rewind();
            final Matrix4f floatMatrix = new Matrix4f();
            floatMatrix.set(glCtx.conversionMatrix4d);
            DisplayListManager.recordLoadMatrix(floatMatrix);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            glCtx.conversionMatrix4d.set(m);
            matrixStack(glCtx).set(glCtx.conversionMatrix4d);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void setModelViewMatrix(Matrix4fc m) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.matrixChange(GL11.GL_MODELVIEW, 0, true);
        if (isCachingEnabled()) {
            glCtx.modelViewMatrix.set(m);
            glCtx.mvGeneration++;
            glCtx.mvLinearGeneration++;
        }
    }

    public static void setTextureMatrix(int unit, Matrix4fc m) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.matrixChange(GL11.GL_TEXTURE, unit, true);
        if (unit != 0) beforeUncapturedStateChange();
        if (isCachingEnabled()) {
            glCtx.textures.getTextureUnitMatrix(unit).set(m);
            glCtx.texMatrixGeneration++;
        }
    }

    public static void setProjectionMatrix(Matrix4fc m) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        if (isCachingEnabled()) {
            glCtx.projectionMatrix.set(m);
            glCtx.projGeneration++;
        }
    }

    public static Matrix4fStack getMatrixStack() {
        return matrixStack(ctx());
    }

    private static Matrix4fStack matrixStack(GLContextState glCtx) {
        switch (glCtx.matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                return glCtx.modelViewMatrix;
            }
            case GL11.GL_PROJECTION -> {
                return glCtx.projectionMatrix;
            }
            case GL11.GL_TEXTURE -> {
                return glCtx.textures.getTextureUnitMatrix(glCtx.activeTextureUnit.getValue());
            }
            default -> throw new IllegalStateException("Unknown matrix mode: " + glCtx.matrixMode.getMode());
        }
    }

    /** Bump the generation counter for the currently active matrix mode. */
    private static void bumpMatrixGeneration(GLContextState glCtx) {
        bumpMatrixGeneration(glCtx, true);
    }

    private static void bumpMatrixGeneration(GLContextState glCtx, boolean linearChanged) {
        switch (glCtx.matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                glCtx.mvGeneration++;
                if (linearChanged) glCtx.mvLinearGeneration++;
            }
            case GL11.GL_PROJECTION -> glCtx.projGeneration++;
            case GL11.GL_TEXTURE -> glCtx.texMatrixGeneration++;
        }
    }

    public static void glLoadIdentity() {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx, true);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLoadIdentity();
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).identity();
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glTranslatef(float x, float y, float z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixTranslation(x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).translate(x, y, z);
            bumpMatrixGeneration(glCtx, false);
        }
    }

    public static void glTranslated(double x, double y, double z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixTranslation((float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).translate((float) x, (float) y, (float) z);
            bumpMatrixGeneration(glCtx, false);
        }
    }

    public static void glScalef(float x, float y, float z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixScale(x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).scale(x, y, z);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glScaled(double x, double y, double z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixScale((float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).scale((float) x, (float) y, (float) z);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glMultMatrix(FloatBuffer floatBuffer) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        glCtx.multMatrix.set(floatBuffer);
        final int currentMode = glCtx.matrixMode.getMode();

        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransform(glCtx.multMatrix);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).mul(glCtx.multMatrix);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void applyMultMatrix(Matrix4f multMatrix) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (isCachingEnabled()) {
            matrixStack(glCtx).mul(multMatrix);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glMultMatrix(DoubleBuffer matrix) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        glCtx.conversionMatrix4d.set(matrix);
        glCtx.conversionMatrix4f.set(glCtx.conversionMatrix4d);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransform(glCtx.conversionMatrix4f);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).mul(glCtx.conversionMatrix4f);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        final float lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0f) return;

        // Fast way to normalize rotation
        final float scalar = 1.0f / (float) Math.sqrt(lenSq);
        x *= scalar;
        y *= scalar;
        z *= scalar;
        // Convert deg to rad
        angle = (float) Math.toRadians(angle);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixRotation(angle, x, y, z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).rotate(angle, x, y, z);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glRotated(double angle, double x, double y, double z) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        final double lenSq = x * x + y * y + z * z;
        if (lenSq == 0.0) return;

        // Fast way to normalize rotation
        final double scalar = 1.0 / Math.sqrt(lenSq);
        x *= scalar;
        y *= scalar;
        z *= scalar;
        // Convert deg to rad
        angle = Math.toRadians(angle);

        if (DisplayListManager.isRecording()) {
            DisplayListManager.applyMatrixRotation((float) angle, (float) x, (float) y, (float) z);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).rotate((float) angle, (float) x, (float) y, (float) z);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransformOrtho(left, right, bottom, top, zNear, zFar);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).ortho((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        final GLContextState glCtx = ctx();
        beforeMatrixChange(glCtx);
        if (DisplayListManager.isRecording()) {
            DisplayListManager.updateRelativeTransformFrustum(left, right, bottom, top, zNear, zFar);
            return;
        }
        if (isCachingEnabled()) {
            matrixStack(glCtx).frustum((float) left, (float) right, (float) bottom, (float) top, (float) zNear, (float) zFar);
            bumpMatrixGeneration(glCtx);
        }
    }

    public static void glPushMatrix() {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.pushMatrix();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPushMatrix();  // Handles flush + relativeTransform stack
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                matrixStack(glCtx).pushMatrix();
            } catch (IllegalStateException ignored) {
                glCtx.exitCapture.reject();
                if (initConfig != null && initConfig.isLwjglDebug()) LOGGER.warn("Matrix stack overflow ", new Throwable());
            }
        }
    }

    public static void glPopMatrix() {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.popMatrix();
        if (isUncapturedMatrix(glCtx)) beforeUncapturedStateChange();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordPopMatrix();  // Handles flush + relativeTransform stack + lastRecordedTransform sync
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        // Only track stack on main thread (splash thread has separate GL context)
        if (isCachingEnabled()) {
            try {
                matrixStack(glCtx).popMatrix();
                bumpMatrixGeneration(glCtx);
            } catch (IllegalStateException ignored) {
                glCtx.exitCapture.reject();
                if (initConfig != null && initConfig.isLwjglDebug()) LOGGER.warn("Matrix stack underflow ", new Throwable());
            }
        }
    }

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        final GLContextState glCtx = ctx();
        glCtx.gluMatrix.identity().perspective((float) Math.toRadians(fovy), aspect, zNear, zFar);
        glCtx.gluMatrix.get(0, glCtx.gluBuffer);
        glMultMatrix(glCtx.gluBuffer);
    }

    public static void gluLookAt(float eyex, float eyey, float eyez, float centerx, float centery, float centerz, float upx, float upy, float upz) {
        final GLContextState glCtx = ctx();
        glCtx.gluMatrix.identity().lookAt(eyex, eyey, eyez, centerx, centery, centerz, upx, upy, upz);
        glCtx.gluMatrix.get(0, glCtx.gluBuffer);
        glMultMatrix(glCtx.gluBuffer);
    }

    public static void gluOrtho2D(float left, float right, float bottom, float top) {
        glOrtho(left, right, bottom, top, -1.0, 1.0);
    }

    public static void gluPickMatrix(float x, float y, float deltaX, float deltaY, IntBuffer viewport) {
        rejectExitCapture();
        if (deltaX <= 0 || deltaY <= 0) return;
        glTranslatef(
                (viewport.get(viewport.position() + 2) - 2 * (x - viewport.get(viewport.position() + 0))) / deltaX,
                (viewport.get(viewport.position() + 3) - 2 * (y - viewport.get(viewport.position() + 1))) / deltaY,
                0);
        glScalef(viewport.get(viewport.position() + 2) / deltaX, viewport.get(viewport.position() + 3) / deltaY, 1.0f);
    }

    public static int gluBuild2DMipmaps(int target, int components, int width, int height, int format, int type, ByteBuffer data) {
        if (width < 1 || height < 1) return GLU.GLU_INVALID_VALUE;
        GLSMTextures.glTexImage2D(target, 0, components, width, height, 0, format, type, data);
        GLSMTextures.glGenerateMipmap(target);
        return 0;
    }

    public static String gluErrorString(int errorCode) {
        return switch (errorCode) {
            case GLU.GLU_INVALID_ENUM -> "Invalid enum (glu)";
            case GLU.GLU_INVALID_VALUE -> "Invalid value (glu)";
            case GLU.GLU_OUT_OF_MEMORY -> "Out of memory (glu)";
            case GL11.GL_NO_ERROR -> "No error";
            case GL11.GL_INVALID_ENUM -> "Invalid enum";
            case GL11.GL_INVALID_VALUE -> "Invalid value";
            case GL11.GL_INVALID_OPERATION -> "Invalid operation";
            case GL11.GL_OUT_OF_MEMORY -> "Out of memory";
            default -> "Unknown error: " + errorCode;
        };
    }

    public static boolean gluUnProject(float winx, float winy, float winz, FloatBuffer modelMatrix, FloatBuffer projMatrix, IntBuffer viewport, FloatBuffer obj_pos) {
        final GLContextState glCtx = ctx();
        glCtx.gluMatrix.set(projMatrix).mul(glCtx.gluMatrix2.set(modelMatrix));
        if (Math.abs(glCtx.gluMatrix.determinant()) < 1.0e-10f) return false;
        glCtx.gluMatrix.invert(glCtx.gluMatrix);

        float ndcX = (winx - viewport.get(viewport.position())) / viewport.get(viewport.position() + 2) * 2.0f - 1.0f;
        float ndcY = (winy - viewport.get(viewport.position() + 1)) / viewport.get(viewport.position() + 3) * 2.0f - 1.0f;
        float ndcZ = winz * 2.0f - 1.0f;

        glCtx.gluVec4.set(ndcX, ndcY, ndcZ, 1.0f);
        glCtx.gluMatrix.transform(glCtx.gluVec4);
        if (glCtx.gluVec4.w == 0.0f) return false;

        float invW = 1.0f / glCtx.gluVec4.w;
        obj_pos.put(obj_pos.position(), glCtx.gluVec4.x * invW);
        obj_pos.put(obj_pos.position() + 1, glCtx.gluVec4.y * invW);
        obj_pos.put(obj_pos.position() + 2, glCtx.gluVec4.z * invW);
        return true;
    }

    public static int getMvGeneration() {
        return ctx().mvGeneration;
    }

    public static int getMvLinearGeneration() {
        return ctx().mvLinearGeneration;
    }

    public static int getProjGeneration() {
        return ctx().projGeneration;
    }

    public static int getTexMatrixGeneration() {
        return ctx().texMatrixGeneration;
    }

    public static MatrixModeStack getMatrixMode() {
        return ctx().matrixMode;
    }

    public static Matrix4fStack getModelViewMatrix() {
        return ctx().modelViewMatrix;
    }

    public static Matrix4fStack getProjectionMatrix() {
        return ctx().projectionMatrix;
    }
}
