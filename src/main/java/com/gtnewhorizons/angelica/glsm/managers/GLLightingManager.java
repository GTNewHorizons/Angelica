package com.gtnewhorizons.angelica.glsm.managers;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.stacks.AlphaStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BlendStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.Color4Stack;
import com.gtnewhorizons.angelica.glsm.stacks.ColorMaskStack;
import com.gtnewhorizons.angelica.glsm.stacks.DepthStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightModelStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MaterialStateStack;
import com.gtnewhorizons.angelica.glsm.states.Color4;
import com.gtnewhorizons.angelica.glsm.utils.GLHelper;
import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import net.coderbot.iris.gl.blending.BlendModeStorage;
import net.coderbot.iris.gl.blending.DepthColorStorage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Manages OpenGL lighting state including lights, materials, and color material properties.
 *
 * Note: OpenGL lighting functions are not available in OpenGL 3.3 Core Profile (GL33C),
 * so we need to use GL11 for these legacy functions.  They will additionally need emulation
 * for the core profile.
 */
@SuppressWarnings("unused")
public class GLLightingManager {

    public static final BooleanStateStack lightingState = new BooleanStateStack(GL11.GL_LIGHTING);
    public static final BooleanStateStack rescaleNormalState = new BooleanStateStack(GL12.GL_RESCALE_NORMAL);
    public static final BooleanStateStack[] lightStates = new BooleanStateStack[8];
    public static final LightStateStack[] lightDataStates = new LightStateStack[8];
    public static final BooleanStateStack colorMaterial = new BooleanStateStack(GL11.GL_COLOR_MATERIAL);
    public static final IntegerStateStack colorMaterialFace = new IntegerStateStack(GL11.GL_FRONT_AND_BACK);
    public static final IntegerStateStack colorMaterialParameter = new IntegerStateStack(GL11.GL_AMBIENT_AND_DIFFUSE);
    public static final LightModelStateStack lightModel = new LightModelStateStack();
    public static final MaterialStateStack frontMaterial = new MaterialStateStack(GL11.GL_FRONT);
    public static final MaterialStateStack backMaterial = new MaterialStateStack(GL11.GL_BACK);
    public static final Color4Stack color = new Color4Stack();
    public static final Color4Stack clearColor = new Color4Stack(new Color4(0.0F, 0.0F, 0.0F, 0.0F));
    public static final ColorMaskStack colorMask = new ColorMaskStack();
    public static final BooleanStateStack cullState = new BooleanStateStack(GL11.GL_CULL_FACE);
    public static final AlphaStateStack alphaState = new AlphaStateStack();
    public static final BooleanStateStack alphaTest = new BooleanStateStack(GL11.GL_ALPHA_TEST);
    public static final Color4 DirtyColor = new Color4(-1.0F, -1.0F, -1.0F, -1.0F);
    public static final BlendStateStack blendState = new BlendStateStack();
    public static final BooleanStateStack blendMode = new BooleanStateStack(GL11.GL_BLEND);
    public static final DepthStateStack depthState = new DepthStateStack();
    public static final BooleanStateStack depthTest = new BooleanStateStack(GL11.GL_DEPTH_TEST);
    public static final BooleanStateStack colorArrayState = new BooleanStateStack(GL11.GL_COLOR_ARRAY);
    public static final BooleanStateStack texCoordArrayState = new BooleanStateStack(GL11.GL_TEXTURE_COORD_ARRAY);

    // Iris Listeners
    public static Runnable blendFuncListener = null;


    static {
        for (int i = 0; i < lightStates.length; i ++) {
            lightStates[i] = new BooleanStateStack(GL11.GL_LIGHT0 + i);
            lightDataStates[i] = new LightStateStack(GL11.GL_LIGHT0 + i);
        }
    }


    public static void reset() {

    }

    public static void glGetMaterial(int face, int pname, FloatBuffer params) {
        glGetMaterialfv(face, pname, params);
    }

    public static void glGetMaterialfv(int face, int pname, FloatBuffer params) {
        if (GLStateManager.shouldBypassCache()) {
            GL11.glGetMaterialfv(face, pname, params);
            return;
        }

        final MaterialStateStack state;
        if (face == GL11.GL_FRONT) {
            state = frontMaterial;
        } else  if (face == GL11.GL_BACK) {
            state = backMaterial;
        } else {
            throw new RuntimeException("Invalid face parameter specified to glGetMaterial: " + face);
        }

        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_EMISSION -> state.emission.get(0, params);
            case GL11.GL_SHININESS -> params.put(state.shininess);
            case GL11.GL_COLOR_INDEXES -> state.colorIndexes.get(0, params);
            default -> GL11.glGetMaterialfv(face, pname, params);
        }
    }

    public static void glGetLight(int light, int pname, FloatBuffer params) {
        glGetLightfv(light, pname, params);
    }

    public static void glGetLightfv(int light, int pname, FloatBuffer params) {
        if (GLStateManager.shouldBypassCache()) {
            GL11.glGetLightfv(light, pname, params);
            return;
        }

        final LightStateStack state = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(0, params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(0, params);
            case GL11.GL_SPECULAR -> state.specular.get(0, params);
            case GL11.GL_POSITION -> state.position.get(0, params);
            case GL11.GL_SPOT_DIRECTION -> state.spotDirection.get(0, params);
            case GL11.GL_SPOT_EXPONENT -> params.put(state.spotExponent);
            case GL11.GL_SPOT_CUTOFF -> params.put(state.spotCutoff);
            case GL11.GL_CONSTANT_ATTENUATION -> params.put(state.constantAttenuation);
            case GL11.GL_LINEAR_ATTENUATION -> params.put(state.linearAttenuation);
            case GL11.GL_QUADRATIC_ATTENUATION -> params.put(state.quadraticAttenuation);
            default -> GL11.glGetLightfv(light, pname, params);
        }
    }

    public static void glBlendColor(float red, float green, float blue, float alpha) {
        GL14.glBlendColor(red, green, blue, alpha);
    }

    public static void glBlendFunc(int srcFactor, int dstFactor) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcFactor, dstFactor, srcFactor, dstFactor);
                return;
            }
        }
        if (HUDCaching.INSTANCE.renderingCacheOverride) {
            blendState.setSrcRgb(srcFactor);
            blendState.setDstRgb(dstFactor);
            blendState.setSrcAlpha(GL11.GL_ONE);
            blendState.setDstAlpha(GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendFuncSeparate(srcFactor, dstFactor, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            return;
        }
        if (GLStateManager.shouldBypassCache() || blendState.getSrcRgb() != srcFactor || blendState.getDstRgb() != dstFactor) {
            blendState.setSrcRgb(srcFactor);
            blendState.setDstRgb(dstFactor);
            GL11.glBlendFunc(srcFactor, dstFactor);
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glBlendEquation(int mode) {
        GL14.glBlendEquation(mode);
    }

    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        GL20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    public static void tryBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (AngelicaConfig.enableIris) {
            if (BlendModeStorage.isBlendLocked()) {
                BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
                return;
            }
        }
        if (HUDCaching.INSTANCE.renderingCacheOverride && dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            srcAlpha = GL11.GL_ONE;
            dstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
        }
        if (GLStateManager.shouldBypassCache() || blendState.getSrcRgb() != srcRgb || blendState.getDstRgb() != dstRgb || blendState.getSrcAlpha()
            != srcAlpha || blendState.getDstAlpha() != dstAlpha) {
            blendState.setSrcRgb(srcRgb);
            blendState.setDstRgb(dstRgb);
            blendState.setSrcAlpha(srcAlpha);
            blendState.setDstAlpha(dstAlpha);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }

        // Iris
        if (blendFuncListener != null) blendFuncListener.run();
    }

    public static void glDepthFunc(int func) {
        if (GLStateManager.shouldBypassCache() || func != depthState.getFunc() ) {
            depthState.setFunc(func);
            GL11.glDepthFunc(func);
        }
    }

    public static void glDepthMask(boolean mask) {
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferDepthEnable(mask);
                return;
            }
        }

        if (GLStateManager.shouldBypassCache() || mask != depthState.isEnabled()) {
            depthState.setEnabled(mask);
            GL11.glDepthMask(mask);
        }
    }

    public static void glColor4f(float red, float green, float blue, float alpha) {
        if (changeColor(red, green, blue, alpha)) {
            GL11.glColor4f(red, green, blue, alpha);
        }
    }

    public static void glColor4d(double red, double green, double blue, double alpha) {
        if (changeColor((float) red, (float) green, (float) blue, (float) alpha)) {
            GL11.glColor4d(red, green, blue, alpha);
        }
    }

    public static void glColor4b(byte red, byte green, byte blue, byte alpha) {
        if (changeColor(GLHelper.b2f(red), GLHelper.b2f(green), GLHelper.b2f(blue), GLHelper.b2f(alpha))) {
            GL11.glColor4b(red, green, blue, alpha);
        }
    }

    public static void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        if (changeColor(GLHelper.ub2f(red), GLHelper.ub2f(green), GLHelper.ub2f(blue), GLHelper.ub2f(alpha))) {
            GL11.glColor4ub(red, green, blue, alpha);
        }
    }

    public static void glColor3f(float red, float green, float blue) {
        if (changeColor(red, green, blue, 1.0F)) {
            GL11.glColor3f(red, green, blue);
        }
    }

    public static void glColor3d(double red, double green, double blue) {
        if (changeColor((float) red, (float) green, (float) blue, 1.0F)) {
            GL11.glColor3d(red, green, blue);
        }
    }

    public static void glColor3b(byte red, byte green, byte blue) {
        if (changeColor(GLHelper.b2f(red), GLHelper.b2f(green), GLHelper.b2f(blue), 1.0F)) {
            GL11.glColor3b(red, green, blue);
        }
    }

    public static void glColor3ub(byte red, byte green, byte blue) {
        if (changeColor(GLHelper.ub2f(red), GLHelper.ub2f(green), GLHelper.ub2f(blue), 1.0F)) {
            GL11.glColor3ub(red, green, blue);
        }
    }

    private static boolean changeColor(float red, float green, float blue, float alpha) {
        // Helper function for glColor*
        if (GLStateManager.shouldBypassCache() || red != color.getRed() || green != color.getGreen() || blue != color.getBlue() || alpha != color.getAlpha()) {
            color.setRed(red);
            color.setGreen(green);
            color.setBlue(blue);
            color.setAlpha(alpha);
            return true;
        }
        return false;
    }

    public static void clearCurrentColor() {
        // Marks the cache dirty, doesn't actually reset the color
        color.set(DirtyColor);
    }

    public static void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        if (AngelicaConfig.enableIris) {
            if (DepthColorStorage.isDepthColorLocked()) {
                DepthColorStorage.deferColorMask(red, green, blue, alpha);
                return;
            }
        }
        if (GLStateManager.shouldBypassCache() || red != colorMask.red || green != colorMask.green || blue != colorMask.blue || alpha != colorMask.alpha) {
            colorMask.red = red;
            colorMask.green = green;
            colorMask.blue = blue;
            colorMask.alpha = alpha;
            GL11.glColorMask(red, green, blue, alpha);
        }
    }

    // Clear Color
    public static void glClearColor(float red, float green, float blue, float alpha) {
        if (GLStateManager.shouldBypassCache() || red != clearColor.getRed() || green != clearColor.getGreen() || blue != clearColor.getBlue() || alpha != clearColor.getAlpha()) {
            clearColor.setRed(red);
            clearColor.setGreen(green);
            clearColor.setBlue(blue);
            clearColor.setAlpha(alpha);
            GL11.glClearColor(red, green, blue, alpha);
        }
    }

    public static void glClearDepth(double depth) {
        GL11.glClearDepth(depth);
    }

    public static void defaultBlendFunc() {
        tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
    }

    private static void glMaterialFront(int pname, FloatBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                frontMaterial.setAmbient(params);
                frontMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> frontMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialBack(int pname, FloatBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                backMaterial.setAmbient(params);
                backMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> backMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialFront(int pname, IntBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                frontMaterial.setAmbient(params);
                frontMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> frontMaterial.setColorIndexes(params);
        }
    }

    private static void glMaterialBack(int pname, IntBuffer params) {
        switch (pname) {
            case GL11.GL_AMBIENT -> backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                backMaterial.setAmbient(params);
                backMaterial.setDiffuse(params);
            }
            case GL11.GL_COLOR_INDEXES -> backMaterial.setColorIndexes(params);
        }
    }

    public static void glMaterial(int face, int pname, FloatBuffer params) {
        glMaterialfv(face, pname, params);
    }

    public static void glMaterialfv(int face, int pname, FloatBuffer params) {
        switch (face) {
            case GL11.GL_FRONT -> glMaterialFront(pname, params);
            case GL11.GL_BACK -> glMaterialBack(pname, params);
            case GL11.GL_FRONT_AND_BACK -> {
                glMaterialFront(pname, params);
                glMaterialBack(pname, params);
            }
            default -> throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
    }

    public static void glMaterial(int face, int pname, IntBuffer params) {
        glMaterialiv(face, pname, params);
    }

    public static void glMaterialiv(int face, int pname, IntBuffer params) {
        switch (face) {
            case GL11.GL_FRONT -> glMaterialFront(pname, params);
            case GL11.GL_BACK -> glMaterialBack(pname, params);
            case GL11.GL_FRONT_AND_BACK -> {
                glMaterialFront(pname, params);
                glMaterialBack(pname, params);
            }
            default -> throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
    }

    public static void glMaterialf(int face, int pname, float val) {
        if (pname != GL11.GL_SHININESS) {
            // it is only valid to call glMaterialf for the GL_SHININESS parameter
            return;
        }

        switch (face) {
            case GL11.GL_FRONT -> frontMaterial.setShininess(val);
            case GL11.GL_BACK -> backMaterial.setShininess(val);
            case GL11.GL_FRONT_AND_BACK -> {
                frontMaterial.setShininess(val);
                backMaterial.setShininess(val);
            }
            default -> throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
    }

    public static void glMateriali(int face, int pname, int val) {
        // This will end up no-opping if pname != GL_SHININESS, it is invalid to call this with another pname
        glMaterialf(face, pname, (float) val);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        glLightfv(light, pname, params);
    }

    public static void glLightfv(int light, int pname, FloatBuffer params) {
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> lightState.setAmbient(params);
            case GL11.GL_DIFFUSE -> lightState.setDiffuse(params);
            case GL11.GL_SPECULAR -> lightState.setSpecular(params);
            case GL11.GL_POSITION -> lightState.setPosition(params);
            case GL11.GL_SPOT_DIRECTION -> lightState.setSpotDirection(params);
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(params);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(params);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(params);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(params);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(params);
            default -> GL11.glLightfv(light, pname, params);
        }
    }

    public static void glLight(int light, int pname, IntBuffer params) {
        glLightiv(light, pname, params);
    }

    public static void glLightiv(int light, int pname, IntBuffer params) {
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> lightState.setAmbient(params);
            case GL11.GL_DIFFUSE -> lightState.setDiffuse(params);
            case GL11.GL_SPECULAR -> lightState.setSpecular(params);
            case GL11.GL_POSITION -> lightState.setPosition(params);
            case GL11.GL_SPOT_DIRECTION -> lightState.setSpotDirection(params);
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(params);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(params);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(params);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(params);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(params);
            default -> GL11.glLightiv(light, pname, params);
        }
    }

    public static void glLightf(int light, int pname, float param) {
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> GL11.glLightf(light, pname, param);
        }
    }

    public static void glLighti(int light, int pname, int param) {
        final LightStateStack lightState = lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> GL11.glLighti(light, pname, param);
        }
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        glLightModelfv(pname, params);
    }

    public static void glLightModelfv(int pname, FloatBuffer params) {
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> GL11.glLightModelfv(pname, params);
        }
    }

    public static void glLightModel(int pname, IntBuffer params) {
        glLightModeliv(pname, params);
    }

    public static void glLightModeliv(int pname, IntBuffer params) {
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> lightModel.setAmbient(params);
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(params);
            default -> GL11.glLightModeliv(pname, params);
        }
    }

    public static void glLightModelf(int pname, float param) {
        switch (pname) {
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
            default -> GL11.glLightModelf(pname, param);
        }
    }

    public static void glLightModeli(int pname, int param) {
        switch (pname) {
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> lightModel.setColorControl(param);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> lightModel.setLocalViewer(param);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> lightModel.setTwoSide(param);
            default -> GL11.glLightModeli(pname, param);
        }
    }

    public static void glColorMaterial(int face, int mode) {
        if (GLStateManager.shouldBypassCache() || (colorMaterialFace.getValue() != face || colorMaterialParameter.getValue() != mode)) {
            colorMaterialFace.setValue(face);
            colorMaterialParameter.setValue(mode);
            GL11.glColorMaterial(face, mode);
        }
    }

    public static void glColorPointer(int size, int type, int stride, long pointer_buffer_offset) {
        GL11.glColorPointer(size, type, stride, pointer_buffer_offset);
    }

    public static void glColorPointer(int size, int type, int stride, ByteBuffer pointer) {
        GL11.glColorPointer(size, type, stride, pointer);
    }

    public static void glColorPointer(int size, int type, int stride, FloatBuffer pointer) {
        GL11.glColorPointer(size, type, stride, pointer);
    }
}
