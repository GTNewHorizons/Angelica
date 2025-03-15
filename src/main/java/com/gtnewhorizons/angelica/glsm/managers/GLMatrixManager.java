package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.MatrixModeStack;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import cpw.mods.fml.relauncher.ReflectionHelper;
import lombok.SneakyThrows;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33C;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;

/*
 * This class is responsible for managing the OpenGL matrix stack.
 *   - Everything referenced here is from the compatability profile and needs to be emulated
 */
@SuppressWarnings("unused") // Entrypoint via ASM
public class GLMatrixManager {

    public static final MatrixModeStack matrixMode = new MatrixModeStack();
    public static final Matrix4d conversionMatrix4d = new Matrix4d();
    public static final Matrix4f conversionMatrix4f = new Matrix4f();
    private static final MethodHandle MAT4_STACK_CURR_DEPTH;

    public static final int MAX_MODELVIEW_STACK_DEPTH = GL33C.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH);
    public static final Matrix4fStack modelViewMatrix = new Matrix4fStack(MAX_MODELVIEW_STACK_DEPTH);
    public static final int MAX_PROJECTION_STACK_DEPTH = GL33C.glGetInteger(GL11.GL_MAX_PROJECTION_STACK_DEPTH);
    public static final Matrix4fStack projectionMatrix = new Matrix4fStack(MAX_PROJECTION_STACK_DEPTH);


    static {
        try {
            final Field curr = ReflectionHelper.findField(Matrix4fStack.class, "curr");
            curr.setAccessible(true);
            MAT4_STACK_CURR_DEPTH = MethodHandles.lookup().unreflectGetter(curr);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Matrix4f tempMatrix4f = new Matrix4f();
    private static final Vector3f rotation = new Vector3f();
    private static final Matrix4f perspectiveMatrix = new Matrix4f();
    private static final FloatBuffer perspectiveBuffer = BufferUtils.createFloatBuffer(16);

    public static void reset() {
        modelViewMatrix.clear();
        projectionMatrix.clear();
        matrixMode.clear();
    }

    @SneakyThrows
    public static int getMatrixStackDepth(Matrix4fStack stack) {
        return (int) MAT4_STACK_CURR_DEPTH.invokeExact(stack);
    }

    // Matrix Operations
    public static void glMatrixMode(int mode) {
        matrixMode.setMode(mode);
    }

    public static void glLoadMatrix(FloatBuffer m) {
        glLoadMatrixf(m);
    }

    public static void glLoadMatrixf(FloatBuffer m) {
        getMatrixStack().set(m);
        GL11.glLoadMatrixf(m);
    }

    public static void glLoadMatrix(DoubleBuffer m) {
        GLMatrixManager.glLoadMatrixd(m);
    }

    public static void glLoadMatrixd(DoubleBuffer m) {
        conversionMatrix4d.set(m);
        getMatrixStack().set(conversionMatrix4d);
        GL11.glLoadMatrixd(m);
    }

    public static Matrix4fStack getMatrixStack() {
        switch (matrixMode.getMode()) {
            case GL11.GL_MODELVIEW -> {
                return modelViewMatrix;
            }
            case GL11.GL_PROJECTION -> {
                return projectionMatrix;
            }
            case GL33C.GL_TEXTURE -> {
                return GLTextureManager.textures.getTextureUnitMatrix(GLStateManager.getActiveTextureUnit());
            }
            default -> throw new IllegalStateException("Unknown matrix mode: " + matrixMode.getMode());
        }
    }

    public static void glLoadIdentity() {
        GL11.glLoadIdentity();
        getMatrixStack().identity();
    }

    public static void glTranslatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
        getMatrixStack().translate(x, y, z);
    }

    public static void glTranslated(double x, double y, double z) {
        GL11.glTranslated(x, y, z);
        getMatrixStack().translate((float) x, (float) y, (float) z);
    }

    public static void glScalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
        getMatrixStack().scale(x, y, z);
    }

    public static void glScaled(double x, double y, double z) {
        GL11.glScaled(x, y, z);
        getMatrixStack().scale((float) x, (float) y, (float) z);
    }

    public static void glMultMatrix(FloatBuffer floatBuffer) {
        glMultMatrixf(floatBuffer);
    }

    public static void glMultMatrixf(FloatBuffer floatBuffer) {
        GL11.glMultMatrixf(floatBuffer);
        tempMatrix4f.set(floatBuffer);
        getMatrixStack().mul(tempMatrix4f);
    }

    public static void glMultMatrix(DoubleBuffer matrix) {
        GLMatrixManager.glMultMatrixd(matrix);
    }

    public static void glMultMatrixd(DoubleBuffer matrix) {
        GL11.glMultMatrixd(matrix);
        conversionMatrix4d.set(matrix);
        conversionMatrix4f.set(conversionMatrix4d);
        getMatrixStack().mul(conversionMatrix4f);
    }

    public static void glRotatef(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
        rotation.set(x, y, z).normalize();
        getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
    }

    public static void glRotated(double angle, double x, double y, double z) {
        GL11.glRotated(angle, x, y, z);
        rotation.set(x, y, z).normalize();
        getMatrixStack().rotate((float)Math.toRadians(angle), rotation);
    }

    public static void glOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glOrtho(left, right, bottom, top, zNear, zFar);
        getMatrixStack().ortho((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }

    public static void glFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        GL11.glFrustum(left, right, bottom, top, zNear, zFar);
        getMatrixStack().frustum((float)left, (float)right, (float)bottom, (float)top, (float)zNear, (float)zFar);
    }

    public static void glPushMatrix() {
        GL11.glPushMatrix();
        try {
            getMatrixStack().pushMatrix();
        } catch(IllegalStateException ignored) {
            // Ignore
            if(AngelicaMod.lwjglDebug)
                AngelicaTweaker.LOGGER.warn("Matrix stack overflow ", new Throwable());
        }
    }

    public static void glPopMatrix() {
        GL11.glPopMatrix();
        try {
            getMatrixStack().popMatrix();
        } catch(IllegalStateException ignored) {
            // Ignore
            if(AngelicaMod.lwjglDebug)
                AngelicaTweaker.LOGGER.warn("Matrix stack underflow ", new Throwable());
        }
    }

    public static void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        perspectiveMatrix.identity().perspective((float)Math.toRadians(fovy), aspect, zNear, zFar);

        perspectiveMatrix.get(0, perspectiveBuffer);
        GL11.glMultMatrixf(perspectiveBuffer);

        getMatrixStack().mul(perspectiveMatrix);
    }
}
