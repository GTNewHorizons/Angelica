package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.DisplayListManager.RecordMode;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.ffp.VertexKey;
import com.gtnewhorizons.angelica.glsm.stacks.BooleanStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.IntegerStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightModelStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.LightStateStack;
import com.gtnewhorizons.angelica.glsm.stacks.MaterialStateStack;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.LOGGER;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.ctx;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.isCachingEnabled;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.mod;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.rejectExitCapture;

public final class GLSMLighting {

    private GLSMLighting() {}

    public static void glGetMaterial(int face, int pname, FloatBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final MaterialStateStack state;
        if (face == GL11.GL_FRONT) {
            state = glCtx.frontMaterial;
        } else if (face == GL11.GL_BACK) {
            state = glCtx.backMaterial;
        } else {
            throw new RuntimeException("Invalid face parameter specified to glGetMaterial: " + face);
        }

        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(params.position(), params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(params.position(), params);
            case GL11.GL_SPECULAR -> state.specular.get(params.position(), params);
            case GL11.GL_EMISSION -> state.emission.get(params.position(), params);
            case GL11.GL_SHININESS -> params.put(params.position(), state.shininess);
            case GL11.GL_COLOR_INDEXES -> state.colorIndexes.get(params.position(), params);
            default -> {
            }
        }
    }

    private static void putLightingInts(FloatBuffer values, int pname, boolean material, IntBuffer params) {
        final int count = switch (pname) {
            case GL11.GL_AMBIENT, GL11.GL_DIFFUSE, GL11.GL_SPECULAR -> 4;
            case GL11.GL_EMISSION -> material ? 4 : 0;
            case GL11.GL_COLOR_INDEXES -> material ? 3 : 0;
            case GL11.GL_SHININESS -> material ? 1 : 0;
            case GL11.GL_POSITION -> material ? 0 : 4;
            case GL11.GL_SPOT_DIRECTION -> material ? 0 : 3;
            case GL11.GL_SPOT_EXPONENT, GL11.GL_SPOT_CUTOFF, GL11.GL_CONSTANT_ATTENUATION, GL11.GL_LINEAR_ATTENUATION,
                 GL11.GL_QUADRATIC_ATTENUATION -> material ? 0 : 1;
            default -> 0;
        };
        final boolean color = count == 4 && pname != GL11.GL_POSITION;
        final int pos = params.position();
        for (int i = 0; i < count; i++) {
            final float value = values.get(i);
            final int converted;
            if (color) {
                converted = LegacyQueries.floatToInt(value);
            } else if (material) {
                converted = LegacyQueries.lround(value);
            } else {
                converted = (int) value;
            }
            params.put(pos + i, converted);
        }
    }

    public static void glGetMaterial(int face, int pname, IntBuffer params) {
        final FloatBuffer scratch = ctx().queryScratch;
        scratch.clear();
        glGetMaterial(face, pname, scratch);
        putLightingInts(scratch, pname, true, params);
    }

    public static void glGetLight(int light, int pname, IntBuffer params) {
        final FloatBuffer scratch = ctx().queryScratch;
        scratch.clear();
        glGetLight(light, pname, scratch);
        putLightingInts(scratch, pname, false, params);
    }

    public static void glGetLight(int light, int pname, FloatBuffer params) {
        rejectExitCapture();
        final LightStateStack state = ctx().lightDataStates[light - GL11.GL_LIGHT0];
        switch (pname) {
            case GL11.GL_AMBIENT -> state.ambient.get(params.position(), params);
            case GL11.GL_DIFFUSE -> state.diffuse.get(params.position(), params);
            case GL11.GL_SPECULAR -> state.specular.get(params.position(), params);
            case GL11.GL_POSITION -> state.position.get(params.position(), params);
            case GL11.GL_SPOT_DIRECTION -> state.spotDirection.get(params.position(), params);
            case GL11.GL_SPOT_EXPONENT -> params.put(params.position(), state.spotExponent);
            case GL11.GL_SPOT_CUTOFF -> params.put(params.position(), state.spotCutoff);
            case GL11.GL_CONSTANT_ATTENUATION -> params.put(params.position(), state.constantAttenuation);
            case GL11.GL_LINEAR_ATTENUATION -> params.put(params.position(), state.linearAttenuation);
            case GL11.GL_QUADRATIC_ATTENUATION -> params.put(params.position(), state.quadraticAttenuation);
            default -> {
            }
        }
    }

    public static float i2f(int i) {return ((i - Integer.MIN_VALUE) & 0xFFFFFFFFL) / 4294967295.0F;}

    public static void enableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableLighting(ctx());
    }

    static void enableLighting(GLContextState glCtx) {
        glCtx.lightingState.enable();
    }

    public static void enableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableLight(ctx(), light);
    }

    static void enableLight(GLContextState glCtx, int light) {
        if (light >= VertexKey.FFP_LIGHT_COUNT && (warnedFfpLightMask & (1 << light)) == 0 && ShaderManager.isEnabled()) {
            warnedFfpLightMask |= (1 << light);
            LOGGER.warn("GL_LIGHT{} enabled, but FFP shader emulation implements only GL_LIGHT0..{}", light, VertexKey.FFP_LIGHT_COUNT - 1, new Throwable("call site"));
        }
        glCtx.lightStates[light].enable();
    }

    private static int warnedFfpLightMask = 0;

    public static void enableColorMaterial() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableColorMaterial(ctx());
    }

    static void enableColorMaterial(GLContextState glCtx) {
        glCtx.colorMaterial.enable();
        bakeCurrentColorIntoTrackedMaterial(glCtx);
    }

    public static void disableColorMaterial() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_COLOR_MATERIAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableColorMaterial(ctx());
    }

    static void disableColorMaterial(GLContextState glCtx) {
        bakeCurrentColorIntoTrackedMaterial(glCtx);
        glCtx.colorMaterial.disable();
    }

    private static void bakeCurrentColorIntoTrackedMaterial(GLContextState glCtx) {
        glCtx.exitCapture.requireMaterialBakeInputs();
        final float r = glCtx.color.getRed();
        final float g = glCtx.color.getGreen();
        final float b = glCtx.color.getBlue();
        final float a = glCtx.color.getAlpha();
        final int face = glCtx.colorMaterialFace.getValue();
        final int param = glCtx.colorMaterialParameter.getValue();
        if (face == GL11.GL_FRONT || face == GL11.GL_FRONT_AND_BACK) {
            bakeMaterialColor(glCtx, glCtx.frontMaterial, param, r, g, b, a);
        }
        if (face == GL11.GL_BACK || face == GL11.GL_FRONT_AND_BACK) {
            bakeMaterialColor(glCtx, glCtx.backMaterial, param, r, g, b, a);
        }
    }

    private static boolean materialVectorDiffers(Vector4f vec, float r, float g, float b, float a) {
        return vec.x != r || vec.y != g || vec.z != b || vec.w != a;
    }

    private static void bakeMaterialColor(GLContextState glCtx, MaterialStateStack material, int param, float r, float g, float b, float a) {
        glCtx.exitCapture.material(material.face, param);
        final boolean changed = switch (param) {
            case GL11.GL_AMBIENT_AND_DIFFUSE -> materialVectorDiffers(material.ambient, r, g, b, a) || materialVectorDiffers(material.diffuse, r, g, b, a);
            case GL11.GL_AMBIENT -> materialVectorDiffers(material.ambient, r, g, b, a);
            case GL11.GL_DIFFUSE -> materialVectorDiffers(material.diffuse, r, g, b, a);
            case GL11.GL_SPECULAR -> materialVectorDiffers(material.specular, r, g, b, a);
            case GL11.GL_EMISSION -> materialVectorDiffers(material.emission, r, g, b, a);
            default -> false;
        };
        if (!changed) return;
        mod(material);
        switch (param) {
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                material.ambient.set(r, g, b, a);
                material.diffuse.set(r, g, b, a);
            }
            case GL11.GL_AMBIENT -> material.ambient.set(r, g, b, a);
            case GL11.GL_DIFFUSE -> material.diffuse.set(r, g, b, a);
            case GL11.GL_SPECULAR -> material.specular.set(r, g, b, a);
            case GL11.GL_EMISSION -> material.emission.set(r, g, b, a);
            default -> {}
        }
    }

    public static void disableLighting() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHTING);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableLighting(ctx());
    }

    static void disableLighting(GLContextState glCtx) {
        glCtx.lightingState.disable();
    }

    public static void disableLight(int light) {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL11.GL_LIGHT0 + light);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableLight(ctx(), light);
    }

    static void disableLight(GLContextState glCtx, int light) {
        glCtx.lightStates[light].disable();
    }

    public static void enableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordEnable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        enableRescaleNormal(ctx());
    }

    static void enableRescaleNormal(GLContextState glCtx) {
        glCtx.rescaleNormalState.enable();
    }

    public static void disableRescaleNormal() {
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordDisable(GL12.GL_RESCALE_NORMAL);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        disableRescaleNormal(ctx());
    }

    static void disableRescaleNormal(GLContextState glCtx) {
        glCtx.rescaleNormalState.disable();
    }

    private static boolean glMaterialFront(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        mod(glCtx.frontMaterial);
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.frontMaterial.setAmbient(params);
                final boolean d = glCtx.frontMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.frontMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialBack(int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        mod(glCtx.backMaterial);
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.backMaterial.setAmbient(params);
                final boolean d = glCtx.backMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.backMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialFront(int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        mod(glCtx.frontMaterial);
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.frontMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.frontMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.frontMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.frontMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.frontMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.frontMaterial.setAmbient(params);
                final boolean d = glCtx.frontMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.frontMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    private static boolean glMaterialBack(int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        mod(glCtx.backMaterial);
        return switch (pname) {
            case GL11.GL_AMBIENT -> glCtx.backMaterial.setAmbient(params);
            case GL11.GL_DIFFUSE -> glCtx.backMaterial.setDiffuse(params);
            case GL11.GL_SPECULAR -> glCtx.backMaterial.setSpecular(params);
            case GL11.GL_EMISSION -> glCtx.backMaterial.setEmission(params);
            case GL11.GL_SHININESS -> glCtx.backMaterial.setShininess(params);
            case GL11.GL_AMBIENT_AND_DIFFUSE -> {
                final boolean a = glCtx.backMaterial.setAmbient(params);
                final boolean d = glCtx.backMaterial.setDiffuse(params);
                yield a || d;
            }
            case GL11.GL_COLOR_INDEXES -> glCtx.backMaterial.setColorIndexes(params);
            default -> false;
        };
    }

    public static void glMaterial(int face, int pname, FloatBuffer params) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.material(face, pname);
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordMaterial(face, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            changed = glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            changed = glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            final boolean f = glMaterialFront(pname, params);
            final boolean b = glMaterialBack(pname, params);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glMaterial(int face, int pname, IntBuffer params) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.material(face, pname);
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordMaterial(face, pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            changed = glMaterialFront(pname, params);
        } else if (face == GL11.GL_BACK) {
            changed = glMaterialBack(pname, params);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            final boolean f = glMaterialFront(pname, params);
            final boolean b = glMaterialBack(pname, params);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glMaterialf(int face, int pname, float val) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordMaterialf(face, pname, val);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        if (pname != GL11.GL_SHININESS) {
            // it is only valid to call glMaterialf for the GL_SHININESS parameter
            return;
        }

        boolean changed = false;
        if (face == GL11.GL_FRONT) {
            mod(glCtx.frontMaterial);
            changed = glCtx.frontMaterial.setShininess(val);
        } else if (face == GL11.GL_BACK) {
            mod(glCtx.backMaterial);
            changed = glCtx.backMaterial.setShininess(val);
        } else if (face == GL11.GL_FRONT_AND_BACK) {
            mod(glCtx.frontMaterial);
            mod(glCtx.backMaterial);
            final boolean f = glCtx.frontMaterial.setShininess(val);
            final boolean b = glCtx.backMaterial.setShininess(val);
            changed = f || b;
        } else {
            throw new RuntimeException("Unsupported face value for glMaterial: " + face);
        }
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glMateriali(int face, int pname, int val) {
        // This will end up no-opping if pname != GL_SHININESS, it is invalid to call this with another pname
        // Command recording happens in glMaterialf
        glMaterialf(face, pname, (float) val);
    }

    public static void glLight(int light, int pname, FloatBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLight(light, pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        mod(lightState);
        final boolean changed = switch (pname) {
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
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLight(int light, int pname, IntBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordLight(light, pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        mod(lightState);
        final boolean changed = switch (pname) {
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
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightf(int light, int pname, float param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightf(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        mod(lightState);
        final boolean changed = switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLighti(int light, int pname, int param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLighti(light, pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        final LightStateStack lightState = glCtx.lightDataStates[light - GL11.GL_LIGHT0];
        mod(lightState);
        final boolean changed = switch (pname) {
            case GL11.GL_SPOT_EXPONENT -> lightState.setSpotExponent(param);
            case GL11.GL_SPOT_CUTOFF -> lightState.setSpotCutoff(param);
            case GL11.GL_CONSTANT_ATTENUATION -> lightState.setConstantAttenuation(param);
            case GL11.GL_LINEAR_ATTENUATION -> lightState.setLinearAttenuation(param);
            case GL11.GL_QUADRATIC_ATTENUATION -> lightState.setQuadraticAttenuation(param);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModel(int pname, FloatBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModel(pname, params);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        mod(glCtx.lightModel);
        final boolean changed = switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.setAmbient(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModel(int pname, IntBuffer params) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        // For IntBuffer version, we need to convert to float for recording
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            // Convert int params to float for recording
            final float[] floatParams = new float[params.remaining()];
            params.mark();
            for (int i = 0; i < floatParams.length; i++) {
                floatParams[i] = i2f(params.get());
            }
            params.reset();
            DisplayListManager.recordLightModel(pname, FloatBuffer.wrap(floatParams));
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }
        mod(glCtx.lightModel);
        final boolean changed = switch (pname) {
            case GL11.GL_LIGHT_MODEL_AMBIENT -> glCtx.lightModel.setAmbient(params);
            case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.setColorControl(params);
            case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(params);
            case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(params);
            default -> false;
        };
        if (changed) glCtx.lightingGeneration++;
    }

    public static void glLightModelf(int pname, float param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModelf(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            mod(glCtx.lightModel);
            final boolean changed = switch (pname) {
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(param);
                default -> false;
            };
            if (changed) glCtx.lightingGeneration++;
        }
    }

    public static void glLightModeli(int pname, int param) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        final RecordMode mode = DisplayListManager.getRecordMode();
        if (mode != RecordMode.NONE) {
            DisplayListManager.recordLightModeli(pname, param);
            if (mode == RecordMode.COMPILE) {
                return;
            }
        }

        if (isCachingEnabled()) {
            mod(glCtx.lightModel);
            final boolean changed = switch (pname) {
                case GL12.GL_LIGHT_MODEL_COLOR_CONTROL -> glCtx.lightModel.setColorControl(param);
                case GL11.GL_LIGHT_MODEL_LOCAL_VIEWER -> glCtx.lightModel.setLocalViewer(param);
                case GL11.GL_LIGHT_MODEL_TWO_SIDE -> glCtx.lightModel.setTwoSide(param);
                default -> false;
            };
            if (changed) glCtx.lightingGeneration++;
        }
    }

    public static void glColorMaterial(int face, int mode) {
        final GLContextState glCtx = ctx();
        glCtx.exitCapture.write(ExitStateCapture.COLOR_MATERIAL_SETTINGS);
        final RecordMode recordMode = DisplayListManager.getRecordMode();
        if (recordMode != RecordMode.NONE) {
            DisplayListManager.recordColorMaterial(face, mode);
            if (recordMode == RecordMode.COMPILE) {
                return;
            }
        }
        final boolean caching = isCachingEnabled();
        if (!caching || glCtx.colorMaterialFace.getValue() != face || glCtx.colorMaterialParameter.getValue() != mode) {
            mod(glCtx.colorMaterialFace).setValue(face);
            mod(glCtx.colorMaterialParameter).setValue(mode);
            glCtx.lightingGeneration++;
        }
    }

    public static int getLightingGeneration() {
        return ctx().lightingGeneration;
    }

    public static BooleanStateStack getLightingState() {
        return ctx().lightingState;
    }

    public static BooleanStateStack getRescaleNormalState() {
        return ctx().rescaleNormalState;
    }

    public static BooleanStateStack getNormalizeState() {
        return ctx().normalizeState;
    }

    public static BooleanStateStack[] getLightStates() {
        return ctx().lightStates;
    }

    public static LightStateStack[] getLightDataStates() {
        return ctx().lightDataStates;
    }

    public static BooleanStateStack getColorMaterial() {
        return ctx().colorMaterial;
    }

    public static IntegerStateStack getColorMaterialFace() {
        return ctx().colorMaterialFace;
    }

    public static IntegerStateStack getColorMaterialParameter() {
        return ctx().colorMaterialParameter;
    }

    public static LightModelStateStack getLightModel() {
        return ctx().lightModel;
    }

    public static MaterialStateStack getFrontMaterial() {
        return ctx().frontMaterial;
    }

    public static MaterialStateStack getBackMaterial() {
        return ctx().backMaterial;
    }

    public static void captureLighting(LightingSnapshot out) {
        final GLContextState glCtx = ctx();
        for (int i = 0; i < out.lights.length; i++) {
            out.lightEnabled[i] = glCtx.lightStates[i].isEnabled();
            out.lights[i].set(glCtx.lightDataStates[i]);
        }
        out.lightModel.set(glCtx.lightModel);
        out.frontMaterial.set(glCtx.frontMaterial);
        out.backMaterial.set(glCtx.backMaterial);
        out.colorMaterial = glCtx.colorMaterial.isEnabled();
        out.colorMaterialFace = glCtx.colorMaterialFace.getValue();
        out.colorMaterialParameter = glCtx.colorMaterialParameter.getValue();
        out.normalize = glCtx.normalizeState.isEnabled();
        out.rescaleNormal = glCtx.rescaleNormalState.isEnabled();
    }

    public static void applyLighting(LightingSnapshot in) {
        rejectExitCapture();
        final GLContextState glCtx = ctx();
        boolean changed = false;
        for (int i = 0; i < in.lights.length; i++) {
            glCtx.lightStates[i].setEnabled(in.lightEnabled[i]);
            if (!glCtx.lightDataStates[i].sameAs(in.lights[i])) {
                mod(glCtx.lightDataStates[i]).set(in.lights[i]);
                changed = true;
            }
        }
        if (!glCtx.lightModel.sameAs(in.lightModel)) {
            mod(glCtx.lightModel).set(in.lightModel);
            changed = true;
        }
        if (!glCtx.frontMaterial.sameAs(in.frontMaterial)) {
            mod(glCtx.frontMaterial).set(in.frontMaterial);
            changed = true;
        }
        if (!glCtx.backMaterial.sameAs(in.backMaterial)) {
            mod(glCtx.backMaterial).set(in.backMaterial);
            changed = true;
        }
        if (glCtx.colorMaterialFace.getValue() != in.colorMaterialFace || glCtx.colorMaterialParameter.getValue() != in.colorMaterialParameter) {
            mod(glCtx.colorMaterialFace).setValue(in.colorMaterialFace);
            mod(glCtx.colorMaterialParameter).setValue(in.colorMaterialParameter);
            changed = true;
        }
        // Direct set: glEnable(GL_COLOR_MATERIAL) would bake the current color over the captured material.
        glCtx.colorMaterial.setEnabled(in.colorMaterial);
        glCtx.normalizeState.setEnabled(in.normalize);
        glCtx.rescaleNormalState.setEnabled(in.rescaleNormal);
        if (changed) glCtx.lightingGeneration++;
    }
}
