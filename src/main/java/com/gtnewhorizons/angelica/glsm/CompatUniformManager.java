package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.stacks.FogStateStack;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import com.gtnewhorizons.angelica.glsm.states.LightModelState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static com.gtnewhorizons.angelica.loading.AngelicaTweaker.LOGGER;

/**
 * Per-program compat uniform location cache and upload manager.
 *
 * <p>Manages {@code angelica_*} uniforms injected by {@link CompatShaderTransformer} and {@code iris_*} matrix uniforms used by Iris/celeritas shader programs.
 */
public class CompatUniformManager {

    // Uniform location indices within the cached array (package-private for tests)
    static final int LOC_MODELVIEW = 0;
    static final int LOC_MODELVIEW_INVERSE = 1;
    static final int LOC_PROJECTION = 2;
    static final int LOC_PROJECTION_INVERSE = 3;
    static final int LOC_NORMAL = 4;
    static final int LOC_LIGHTMAP_TEXTURE_MATRIX = 5;
    static final int LOC_FOG_DENSITY = 6;
    static final int LOC_FOG_START = 7;
    static final int LOC_FOG_END = 8;
    static final int LOC_FOG_COLOR = 9;
    static final int LOC_IRIS_MODELVIEW = 10;
    static final int LOC_IRIS_PROJECTION = 11;
    static final int LOC_IRIS_NORMAL = 12;
    static final int LOC_IRIS_LIGHTMAP_TEXTURE_MATRIX = 13;
    static final int LOC_ALPHA_TEST_REF = 14;
    static final int LOC_SCENE_COLOR = 15;
    static final int LOC_CLIP_PLANES = 16;
    static final int LOC_CLIP_PLANES_ENABLED = 17;
    static final int LOC_COUNT = 18;

    private static final String[] UNIFORM_NAMES = {
        "angelica_ModelViewMatrix",
        "angelica_ModelViewMatrixInverse",
        "angelica_ProjectionMatrix",
        "angelica_ProjectionMatrixInverse",
        "angelica_NormalMatrix",
        "angelica_LightmapTextureMatrix",
        "angelica_FogDensity",
        "angelica_FogStart",
        "angelica_FogEnd",
        "angelica_FogColor",
        "iris_ModelViewMatrix",
        "iris_ProjectionMatrix",
        "iris_NormalMatrix",
        "iris_LightmapTextureMatrix",
        "angelica_currentAlphaTest",
        "angelica_SceneColor",
        "angelica_ClipPlane[0]",
        "angelica_ClipPlanesEnabled",
    };

    /** Per-program cached uniform locations. Maps program ID → int[LOC_COUNT]. */
    private static final Int2ObjectOpenHashMap<int[]> programLocations = new Int2ObjectOpenHashMap<>();

    // Reusable NIO buffers for upload
    private static final FloatBuffer mat4Buf = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer mat3Buf = BufferUtils.createFloatBuffer(9);
    private static final FloatBuffer vec4Buf = BufferUtils.createFloatBuffer(4);
    private static final FloatBuffer clipPlaneBuf = BufferUtils.createFloatBuffer(32); // 8 planes * vec4
    private static final Matrix3f normalMatrix = new Matrix3f();
    private static final Matrix4f scratchMatrix = new Matrix4f();

    private static final float LIGHTMAP_SCALE = 1.0f / 256.0f;
    private static final FloatBuffer lightmapMatrixBuf;
    static {
        lightmapMatrixBuf = BufferUtils.createFloatBuffer(16);
        new Matrix4f().scale(LIGHTMAP_SCALE).translate(8.0f, 8.0f, 8.0f).get(lightmapMatrixBuf);
    }

    // Dirty tracking: skip uploads when state hasn't changed and program is the same
    private static int lastProgram = -1;
    private static int lastMvGen = -1;
    private static int lastProjGen = -1;
    private static int lastTexMatGen = -1;
    private static int lastFragmentGen = -1;
    private static int lastLightingGen = -1;
    private static int lastClipPlaneGen = -1;

    private CompatUniformManager() {}

    public static void onLinkProgram(int program) {
        int[] locs = new int[LOC_COUNT];
        boolean hasAny = false;

        for (int i = 0; i < LOC_COUNT; i++) {
            locs[i] = GL20.glGetUniformLocation(program, UNIFORM_NAMES[i]);
            if (locs[i] != -1) hasAny = true;
        }

        if (hasAny) {
            programLocations.put(program, locs);
            LOGGER.debug("CompatUniformManager: program {} has compat uniforms", program);
        }
    }

    public static void onUseProgram(int program) {
        if (program == 0) return;

        int[] locs = programLocations.get(program);
        if (locs == null) return;

        final boolean programChanged = program != lastProgram;
        lastProgram = program;

        // Matrix uniforms — skip if generation unchanged and same program
        final int mvGen = GLStateManager.mvGeneration;
        final int projGen = GLStateManager.projGeneration;
        final int texMatGen = GLStateManager.texMatrixGeneration;
        final boolean mvChanged = programChanged || mvGen != lastMvGen;
        final boolean projChanged = programChanged || projGen != lastProjGen;
        final boolean texMatChanged = programChanged || texMatGen != lastTexMatGen;
        if (mvChanged || projChanged || texMatChanged) {
            uploadMatrices(locs, mvChanged, projChanged, texMatChanged);
            lastMvGen = mvGen;
            lastProjGen = projGen;
            lastTexMatGen = texMatGen;
        }

        // Fragment-category uniforms (fog, alpha) — skip if generation unchanged
        final int fragGen = GLStateManager.fragmentGeneration;
        if (programChanged || fragGen != lastFragmentGen) {
            lastFragmentGen = fragGen;
            uploadFragmentUniforms(locs);
        }

        // Lighting-derived uniforms (scene color) — skip if lighting state unchanged
        if (locs[LOC_SCENE_COLOR] != -1) {
            final int litGen = GLStateManager.lightingGeneration;
            if (programChanged || litGen != lastLightingGen) {
                lastLightingGen = litGen;
                uploadSceneColor(locs);
            }
        }

        // Clip plane equations + enabled bool — uploaded when enable state or equations change
        if (locs[LOC_CLIP_PLANES] != -1 || locs[LOC_CLIP_PLANES_ENABLED] != -1) {
            final int cpGen = GLStateManager.clipPlaneGeneration;
            if (programChanged || cpGen != lastClipPlaneGen) {
                lastClipPlaneGen = cpGen;
                uploadClipPlanes(locs);
            }
        }
    }

    private static void uploadMatrices(int[] locs, boolean mvChanged, boolean projChanged, boolean texMatChanged) {
        final Matrix4f mv = GLStateManager.getModelViewMatrix();
        final Matrix4f proj = GLStateManager.getProjectionMatrix();

        if (mvChanged) {
            // ModelView
            if (locs[LOC_MODELVIEW] != -1 || locs[LOC_IRIS_MODELVIEW] != -1) {
                mv.get(mat4Buf);
                if (locs[LOC_MODELVIEW] != -1) {
                    GL20.glUniformMatrix4(locs[LOC_MODELVIEW], false, mat4Buf);
                }
                if (locs[LOC_IRIS_MODELVIEW] != -1) {
                    GL20.glUniformMatrix4(locs[LOC_IRIS_MODELVIEW], false, mat4Buf);
                }
            }

            // ModelView Inverse
            if (locs[LOC_MODELVIEW_INVERSE] != -1) {
                mv.invert(scratchMatrix);
                scratchMatrix.get(mat4Buf);
                GL20.glUniformMatrix4(locs[LOC_MODELVIEW_INVERSE], false, mat4Buf);
            }

            // Normal Matrix (inverse-transpose of upper-left 3x3 of ModelView)
            if (locs[LOC_NORMAL] != -1 || locs[LOC_IRIS_NORMAL] != -1) {
                mv.normal(normalMatrix);
                normalMatrix.get(mat3Buf);
                if (locs[LOC_NORMAL] != -1) {
                    GL20.glUniformMatrix3(locs[LOC_NORMAL], false, mat3Buf);
                }
                if (locs[LOC_IRIS_NORMAL] != -1) {
                    GL20.glUniformMatrix3(locs[LOC_IRIS_NORMAL], false, mat3Buf);
                }
            }
        }

        if (projChanged) {
            // Projection
            if (locs[LOC_PROJECTION] != -1 || locs[LOC_IRIS_PROJECTION] != -1) {
                proj.get(mat4Buf);
                if (locs[LOC_PROJECTION] != -1) {
                    GL20.glUniformMatrix4(locs[LOC_PROJECTION], false, mat4Buf);
                }
                if (locs[LOC_IRIS_PROJECTION] != -1) {
                    GL20.glUniformMatrix4(locs[LOC_IRIS_PROJECTION], false, mat4Buf);
                }
            }

            // Projection Inverse
            if (locs[LOC_PROJECTION_INVERSE] != -1) {
                proj.invert(scratchMatrix);
                scratchMatrix.get(mat4Buf);
                GL20.glUniformMatrix4(locs[LOC_PROJECTION_INVERSE], false, mat4Buf);
            }
        }

        if (texMatChanged) {
            // Iris lightmap texture matrix (constant)
            if (locs[LOC_IRIS_LIGHTMAP_TEXTURE_MATRIX] != -1) {
                GL20.glUniformMatrix4(locs[LOC_IRIS_LIGHTMAP_TEXTURE_MATRIX], false, lightmapMatrixBuf);
            }

            // angelica_LightmapTextureMatrix — actual GLSM texture unit 1 matrix (set by enableLightmap())
            if (locs[LOC_LIGHTMAP_TEXTURE_MATRIX] != -1) {
                GLStateManager.getTextures().getTextureUnitMatrix(1).get(mat4Buf);
                GL20.glUniformMatrix4(locs[LOC_LIGHTMAP_TEXTURE_MATRIX], false, mat4Buf);
            }
        }
    }

    private static void uploadFragmentUniforms(int[] locs) {
        // Fog uniforms
        FogStateStack fog = GLStateManager.getFogState();
        if (locs[LOC_FOG_DENSITY] != -1) {
            GL20.glUniform1f(locs[LOC_FOG_DENSITY], Math.max(0.0f, fog.getDensity()));
        }
        if (locs[LOC_FOG_START] != -1) {
            GL20.glUniform1f(locs[LOC_FOG_START], fog.getStart());
        }
        if (locs[LOC_FOG_END] != -1) {
            GL20.glUniform1f(locs[LOC_FOG_END], fog.getEnd());
        }
        if (locs[LOC_FOG_COLOR] != -1) {
            vec4Buf.clear();
            vec4Buf.put((float) fog.getFogColor().x).put((float) fog.getFogColor().y)
                   .put((float) fog.getFogColor().z).put(fog.getFogAlpha());
            vec4Buf.flip();
            GL20.glUniform4(locs[LOC_FOG_COLOR], vec4Buf);
        }

        // Alpha test reference — core profile replacement for GL_ALPHA_TEST
        // Return -1.0f for ALWAYS or disabled so `alpha <= ref` never discards.
        if (locs[LOC_ALPHA_TEST_REF] != -1) {
            final float ref;
            if (!GLStateManager.getAlphaTest().isEnabled()
                || GLStateManager.getAlphaState().getFunction() == GL11.GL_ALWAYS) {
                ref = -1.0f;
            } else {
                ref = GLStateManager.getAlphaState().getReference();
            }
            GL20.glUniform1f(locs[LOC_ALPHA_TEST_REF], ref);
        }
    }

    /**
     * Scene color = emission + ambient * lightModel.ambient, alpha = diffuse.a
     * Same computation as {@code Uniforms.uploadLightingPreComputed()}.
     */
    private static void uploadSceneColor(int[] locs) {
        final MaterialState mat = GLStateManager.getFrontMaterial();
        final LightModelState lm = GLStateManager.getLightModel();
        vec4Buf.clear();
        vec4Buf.put(mat.emission.x + mat.ambient.x * lm.ambient.x)
               .put(mat.emission.y + mat.ambient.y * lm.ambient.y)
               .put(mat.emission.z + mat.ambient.z * lm.ambient.z)
               .put(mat.diffuse.w);
        vec4Buf.flip();
        GL20.glUniform4(locs[LOC_SCENE_COLOR], vec4Buf);
    }

    private static void uploadClipPlanes(int[] locs) {
        if (locs[LOC_CLIP_PLANES_ENABLED] != -1) {
            GL20.glUniform1i(locs[LOC_CLIP_PLANES_ENABLED], GLStateManager.anyClipPlaneEnabled() ? 1 : 0);
        }
        if (locs[LOC_CLIP_PLANES] != -1) {
            final ClipPlaneState cps = GLStateManager.getClipPlaneState();
            clipPlaneBuf.clear();
            for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
                cps.putEyePlane(i, clipPlaneBuf);
            }
            clipPlaneBuf.flip();
            GL20.glUniform4(locs[LOC_CLIP_PLANES], clipPlaneBuf);
        }
    }

    public static void onDeleteProgram(int program) {
        programLocations.remove(program);
    }

    public static boolean hasProgram(int program) {
        return programLocations.containsKey(program);
    }

    public static int[] getLocations(int program) {
        return programLocations.get(program);
    }
}
