package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import com.gtnewhorizons.angelica.glsm.states.LineState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import com.gtnewhorizons.angelica.glsm.states.TexGenState;
import com.gtnewhorizons.angelica.glsm.states.ViewportState;
import com.gtnewhorizons.angelica.glsm.streaming.UniformRingBuffer;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig;
import org.joml.Math;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL31;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memPutInt;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Maintains the shared {@link FFPUniformBlock} image from GLSM cached state and streams it to the UBO ring.
 */
public class Uniforms {

    private static final int RING_CAPACITY = 16 * 1024 * 1024;

    private final ByteBuffer staging = memCalloc(FFPUniformBlock.SIZE);
    private final long stagingAddress = memAddress0(staging);
    private UniformRingBuffer ring;
    private boolean bound;
    private boolean contentChanged;

    private int mvGen = -1, mvLinearGen = -1, projGen = -1, texMatGen = -1;
    private int lightingGen = -1, fragmentGen = -1, colorGen = -1, normalGen = -1, texCoordGen = -1;
    private int texGenGen = -1, clipPlaneGen = -1;
    private float lightmapX = Float.NaN, lightmapY = Float.NaN, lineWidth = Float.NaN;
    private int viewportX = Integer.MIN_VALUE, viewportY = Integer.MIN_VALUE, viewportWidth = -1, viewportHeight = -1;
    private int lineStipple = -1;

    int blockWrites;
    int blockSkips;
    int stagedMatrices;
    int stagedLighting;
    int stagedFragment;
    int stagedColor;
    int stagedNormal;
    int stagedTexCoord;
    int stagedLightmap;
    int stagedTexGen;
    int stagedClipPlanes;
    int stagedMisc;
    int lastFrameBlockWrites;
    int lastFrameBlockSkips;
    int lastFrameStagedMatrices;
    int lastFrameStagedLighting;
    int lastFrameStagedFragment;
    int lastFrameStagedColor;
    int lastFrameStagedNormal;
    int lastFrameStagedTexCoord;
    int lastFrameStagedLightmap;
    int lastFrameStagedTexGen;
    int lastFrameStagedClipPlanes;
    int lastFrameStagedMisc;

    private final Matrix4f mvpMatrix = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();
    private final Vector3f tempVec3 = new Vector3f();
    private final FloatBuffer clipPlaneBuf = memAllocFloat(32); // 8 planes * vec4

    private static final double LN2 = java.lang.Math.log(2.0);
    private static final double SQRT_LN2 = java.lang.Math.sqrt(LN2);

    public void upload() {
        boolean dirty = false;
        contentChanged = false;

        final int mvG = GLStateManager.getMvGeneration();
        final int projG = GLStateManager.getProjGeneration();
        final boolean mvChanged = mvG != mvGen;
        final boolean projChanged = projG != projGen;
        if (mvChanged || projChanged) {
            stageMatrices(mvChanged, projChanged);
            mvGen = mvG;
            projGen = projG;
            stagedMatrices++;
            dirty = true;
        }
        final int mvLinG = GLStateManager.getMvLinearGeneration();
        if (mvLinG != mvLinearGen) {
            stageNormalMatrix();
            mvLinearGen = mvLinG;
            dirty = true;
        }
        final int texMatG = GLStateManager.getTexMatrixGeneration();
        if (texMatG != texMatGen) {
            stageTextureMatrices();
            texMatGen = texMatG;
            dirty = true;
        }
        final int litG = GLStateManager.getLightingGeneration();
        if (litG != lightingGen) {
            stageLighting();
            lightingGen = litG;
            stagedLighting++;
            dirty = true;
        }
        final int colG = GLStateManager.getColorGeneration();
        if (colG != colorGen) {
            stageCurrentColor();
            colorGen = colG;
            stagedColor++;
            dirty = true;
        }
        final int normG = ShaderManager.getNormalGeneration();
        if (normG != normalGen) {
            final Vector3f normal = ShaderManager.getCurrentNormal();
            putVec3(FFPUniformBlock.CURRENT_NORMAL, normal.x, normal.y, normal.z);
            normalGen = normG;
            stagedNormal++;
            dirty = true;
        }
        final int tcG = ShaderManager.getTexCoordGeneration();
        if (tcG != texCoordGen) {
            putVec4(FFPUniformBlock.CURRENT_TEX_COORD_0, ShaderManager.getCurrentTexCoord(0));
            putVec4(FFPUniformBlock.CURRENT_TEX_COORD_2, ShaderManager.getCurrentTexCoord(2));
            putVec4(FFPUniformBlock.CURRENT_TEX_COORD_3, ShaderManager.getCurrentTexCoord(3));
            texCoordGen = tcG;
            stagedTexCoord++;
            dirty = true;
        }
        final float brightX = GLSMConfig.lastBrightnessX;
        final float brightY = GLSMConfig.lastBrightnessY;
        if (brightX != lightmapX || brightY != lightmapY) {
            putFloat(FFPUniformBlock.CURRENT_LIGHTMAP_COORD, brightX);
            putFloat(FFPUniformBlock.CURRENT_LIGHTMAP_COORD + 4, brightY);
            lightmapX = brightX;
            lightmapY = brightY;
            stagedLightmap++;
            dirty = true;
        }
        final int tgG = GLStateManager.getTexGenGeneration();
        if (tgG != texGenGen) {
            stageTexGen();
            texGenGen = tgG;
            stagedTexGen++;
            dirty = true;
        }
        final int cpG = GLStateManager.getClipPlaneGeneration();
        if (cpG != clipPlaneGen) {
            stageClipPlanes();
            clipPlaneGen = cpG;
            stagedClipPlanes++;
            dirty = true;
        }
        final int fragG = GLStateManager.getFragmentGeneration();
        if (fragG != fragmentGen) {
            stageFragment();
            fragmentGen = fragG;
            stagedFragment++;
            dirty = true;
        }
        final float lw = GLStateManager.getLineState().getWidth();
        if (lw != lineWidth) {
            putFloat(FFPUniformBlock.LINE_WIDTH, lw);
            lineWidth = lw;
            stagedMisc++;
            dirty = true;
        }
        final ViewportState vp = GLStateManager.getViewportState();
        if (vp.x != viewportX || vp.y != viewportY || vp.width != viewportWidth || vp.height != viewportHeight) {
            putFloat(FFPUniformBlock.VIEWPORT_SIZE, vp.width);
            putFloat(FFPUniformBlock.VIEWPORT_SIZE + 4, vp.height);
            putVec4(FFPUniformBlock.VIEWPORT, vp.x, vp.y, vp.width, vp.height);
            viewportX = vp.x;
            viewportY = vp.y;
            viewportWidth = vp.width;
            viewportHeight = vp.height;
            stagedMisc++;
            dirty = true;
        }
        final LineState line = GLStateManager.getLineState();
        final int stippleFactor = line.getStippleFactor() < 1 ? 1 : line.getStippleFactor();
        final int stipplePacked = (line.getStipplePattern() & 0xFFFF) | (stippleFactor << 16);
        if (stipplePacked != lineStipple) {
            putInt(FFPUniformBlock.LINE_STIPPLE, stipplePacked);
            lineStipple = stipplePacked;
            stagedMisc++;
            dirty = true;
        }

        if (dirty || !bound) {
            if (!contentChanged && bound) {
                blockSkips++;
                return;
            }
            if (ring == null) {
                ring = new UniformRingBuffer(RING_CAPACITY, FFPUniformBlock.SIZE);
            }
            final int offset = ring.writeBlock(staging);
            RENDER_BACKEND.bindBufferRange(GL31.GL_UNIFORM_BUFFER, FFPUniformBlock.BINDING_POINT,
                ring.getBufferId(), offset, FFPUniformBlock.SIZE);
            bound = true;
            blockWrites++;
        }
    }

    private void stageMatrices(boolean mvChanged, boolean projChanged) {
        final Matrix4f mv = GLStateManager.getModelViewMatrix();
        final Matrix4f proj = GLStateManager.getProjectionMatrix();
        if (mvChanged) {
            putMat4(FFPUniformBlock.MODEL_VIEW_MATRIX, mv);
        }
        if (projChanged) {
            putMat4(FFPUniformBlock.PROJECTION_MATRIX, proj);
        }
        proj.mul(mv, mvpMatrix);
        putMat4(FFPUniformBlock.MVP_MATRIX, mvpMatrix);
    }

    private void stageNormalMatrix() {
        GLStateManager.getModelViewMatrix().normal(normalMatrix);
        putMat3(FFPUniformBlock.NORMAL_MATRIX, normalMatrix);
        putFloat(FFPUniformBlock.NORMAL_SCALE, rescaleFactor(normalMatrix, tempVec3));
    }

    static float rescaleFactor(Matrix3f normalMatrix, Vector3f scratch) {
        final float f = normalMatrix.getColumn(2, scratch).lengthSquared();
        return f > 1.0e-12f ? 1.0f / (float) Math.sqrt(f) : 1.0f;
    }

    private void stageTextureMatrices() {
        putMat4(FFPUniformBlock.TEXTURE_MATRIX_0, GLStateManager.getTextures().getTextureUnitMatrix(0));
        putMat4(FFPUniformBlock.TEXTURE_MATRIX_2, GLStateManager.getTextures().getTextureUnitMatrix(2));
        putMat4(FFPUniformBlock.TEXTURE_MATRIX_3, GLStateManager.getTextures().getTextureUnitMatrix(3));
        putMat4(FFPUniformBlock.LIGHTMAP_TEXTURE_MATRIX, GLStateManager.getTextures().getTextureUnitMatrix(1));
    }

    private void stageLighting() {
        final MaterialState mat = GLStateManager.getFrontMaterial();
        final Vector4f lmAmbient = GLStateManager.getLightModel().ambient;
        final LightState light0 = GLStateManager.getLightDataStates()[0];
        final LightState light1 = GLStateManager.getLightDataStates()[1];

        putVec4(FFPUniformBlock.LIGHT_MODEL_AMBIENT, lmAmbient);
        putVec4(FFPUniformBlock.MATERIAL_EMISSION, mat.emission);
        putVec4(FFPUniformBlock.MATERIAL_AMBIENT, mat.ambient);
        putVec4(FFPUniformBlock.MATERIAL_DIFFUSE, mat.diffuse);
        putVec4(FFPUniformBlock.MATERIAL_SPECULAR, mat.specular);
        putFloat(FFPUniformBlock.MATERIAL_SHININESS, mat.shininess);
        putVec4(FFPUniformBlock.LIGHT0_AMBIENT, light0.ambient);
        putVec4(FFPUniformBlock.LIGHT0_DIFFUSE, light0.diffuse);
        putVec4(FFPUniformBlock.LIGHT0_SPECULAR, light0.specular);
        putVec4(FFPUniformBlock.LIGHT0_POSITION, light0.position);
        putVec4(FFPUniformBlock.LIGHT1_AMBIENT, light1.ambient);
        putVec4(FFPUniformBlock.LIGHT1_DIFFUSE, light1.diffuse);
        putVec4(FFPUniformBlock.LIGHT1_SPECULAR, light1.specular);
        putVec4(FFPUniformBlock.LIGHT1_POSITION, light1.position);

        putVec4(FFPUniformBlock.SCENE_COLOR,
            mat.emission.x + mat.ambient.x * lmAmbient.x,
            mat.emission.y + mat.ambient.y * lmAmbient.y,
            mat.emission.z + mat.ambient.z * lmAmbient.z,
            mat.diffuse.w);
        putLightProduct(FFPUniformBlock.LIGHT_PROD0_AMBIENT, light0.ambient, mat.ambient);
        putLightProduct(FFPUniformBlock.LIGHT_PROD0_DIFFUSE, light0.diffuse, mat.diffuse);
        putLightProduct(FFPUniformBlock.LIGHT_PROD0_SPECULAR, light0.specular, mat.specular);
        putLightProduct(FFPUniformBlock.LIGHT_PROD1_AMBIENT, light1.ambient, mat.ambient);
        putLightProduct(FFPUniformBlock.LIGHT_PROD1_DIFFUSE, light1.diffuse, mat.diffuse);
        putLightProduct(FFPUniformBlock.LIGHT_PROD1_SPECULAR, light1.specular, mat.specular);
    }

    private void putLightProduct(int offset, Vector4f lightVal, Vector4f materialVal) {
        putVec3(offset, lightVal.x * materialVal.x, lightVal.y * materialVal.y, lightVal.z * materialVal.z);
    }

    private void stageCurrentColor() {
        final var color = GLStateManager.getColor();
        putVec4(FFPUniformBlock.CURRENT_COLOR,
            Math.clamp(0f, 1f, color.getRed()),
            Math.clamp(0f, 1f, color.getGreen()),
            Math.clamp(0f, 1f, color.getBlue()),
            Math.clamp(0f, 1f, color.getAlpha()));
    }

    private void stageTexGen() {
        final TexGenState tg = GLStateManager.getTextures().getTexGenState(0);
        putPlane(FFPUniformBlock.TEX_GEN_OBJ_PLANE_S, tg.getObjectPlane(GL11.GL_S));
        putPlane(FFPUniformBlock.TEX_GEN_OBJ_PLANE_T, tg.getObjectPlane(GL11.GL_T));
        putPlane(FFPUniformBlock.TEX_GEN_OBJ_PLANE_R, tg.getObjectPlane(GL11.GL_R));
        putPlane(FFPUniformBlock.TEX_GEN_OBJ_PLANE_Q, tg.getObjectPlane(GL11.GL_Q));
        putPlane(FFPUniformBlock.TEX_GEN_EYE_PLANE_S, tg.getEyePlane(GL11.GL_S));
        putPlane(FFPUniformBlock.TEX_GEN_EYE_PLANE_T, tg.getEyePlane(GL11.GL_T));
        putPlane(FFPUniformBlock.TEX_GEN_EYE_PLANE_R, tg.getEyePlane(GL11.GL_R));
        putPlane(FFPUniformBlock.TEX_GEN_EYE_PLANE_Q, tg.getEyePlane(GL11.GL_Q));
    }

    private void stageClipPlanes() {
        final ClipPlaneState cps = GLStateManager.getClipPlaneState();
        clipPlaneBuf.clear();
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            cps.putEyePlane(i, clipPlaneBuf);
        }
        final long src = memAddress0(clipPlaneBuf);
        for (int i = 0; i < 32 * 4; i += 4) {
            putInt(FFPUniformBlock.CLIP_PLANES + i, memGetInt(src + i));
        }
    }

    private void stageFragment() {
        putFloat(FFPUniformBlock.ALPHA_REF, GLStateManager.getAlphaState().getReference());

        for (int i = 0; i < 4; i++) {
            final var envState = GLStateManager.getTextures().getTexEnvState(i);
            putVec4(FFPUniformBlock.TEX_ENV_COLOR_0 + i * 16,
                envState.envColorR, envState.envColorG, envState.envColorB, envState.envColorA);
        }

        putVec4(FFPUniformBlock.OVERLAY_COLOR,
            GLStateManager.getOverlayR(), GLStateManager.getOverlayG(),
            GLStateManager.getOverlayB(), GLStateManager.getOverlayA());

        final var secondary = GLStateManager.getSecondaryColor();
        putVec3(FFPUniformBlock.SECONDARY_COLOR, secondary.getRed(), secondary.getGreen(), secondary.getBlue());

        // Mesa STATE_FOG_PARAMS_OPTIMIZED
        final FogState fog = GLStateManager.getFogState();
        final float start = fog.getStart();
        final float end = fog.getEnd();
        final float density = fog.getDensity();
        final float range = end - start;
        putVec4(FFPUniformBlock.FOG_PARAMS,
            range != 0.0f ? -1.0f / range : 0.0f,
            range != 0.0f ? end / range : 1.0f,
            (float) (density / LN2),
            (float) (density / SQRT_LN2));
        putVec4(FFPUniformBlock.FOG_COLOR,
            (float) fog.getFogColor().x, (float) fog.getFogColor().y, (float) fog.getFogColor().z,
            fog.getFogAlpha());
    }

    private void putFloat(int offset, float v) {
        final long a = stagingAddress + offset;
        final int bits = Float.floatToRawIntBits(v);
        if (memGetInt(a) != bits) contentChanged = true;
        memPutInt(a, bits);
    }

    private void putInt(int offset, int v) {
        final long a = stagingAddress + offset;
        if (memGetInt(a) != v) contentChanged = true;
        memPutInt(a, v);
    }

    private void putMat4(int offset, Matrix4f m) {
        putVec4(offset, m.m00(), m.m01(), m.m02(), m.m03());
        putVec4(offset + 16, m.m10(), m.m11(), m.m12(), m.m13());
        putVec4(offset + 32, m.m20(), m.m21(), m.m22(), m.m23());
        putVec4(offset + 48, m.m30(), m.m31(), m.m32(), m.m33());
    }

    /** std140 mat3: three vec4-aligned columns. */
    private void putMat3(int offset, Matrix3f m) {
        putVec3(offset, m.m00(), m.m01(), m.m02());
        putVec3(offset + 16, m.m10(), m.m11(), m.m12());
        putVec3(offset + 32, m.m20(), m.m21(), m.m22());
    }

    private void putVec4(int offset, Vector4f v) {
        putVec4(offset, v.x, v.y, v.z, v.w);
    }

    private void putVec4(int offset, float x, float y, float z, float w) {
        putFloat(offset, x);
        putFloat(offset + 4, y);
        putFloat(offset + 8, z);
        putFloat(offset + 12, w);
    }

    private void putVec3(int offset, float x, float y, float z) {
        putFloat(offset, x);
        putFloat(offset + 4, y);
        putFloat(offset + 8, z);
    }

    private void putPlane(int offset, float[] plane) {
        putVec4(offset, plane[0], plane[1], plane[2], plane[3]);
    }

    public void endFrame() {
        if (ring != null) {
            ring.endFrame();
        }
        lastFrameBlockWrites = blockWrites;
        lastFrameBlockSkips = blockSkips;
        lastFrameStagedMatrices = stagedMatrices;
        lastFrameStagedLighting = stagedLighting;
        lastFrameStagedFragment = stagedFragment;
        lastFrameStagedColor = stagedColor;
        lastFrameStagedNormal = stagedNormal;
        lastFrameStagedTexCoord = stagedTexCoord;
        lastFrameStagedLightmap = stagedLightmap;
        lastFrameStagedTexGen = stagedTexGen;
        lastFrameStagedClipPlanes = stagedClipPlanes;
        lastFrameStagedMisc = stagedMisc;
        blockWrites = 0;
        blockSkips = 0;
        stagedMatrices = 0;
        stagedLighting = 0;
        stagedFragment = 0;
        stagedColor = 0;
        stagedNormal = 0;
        stagedTexCoord = 0;
        stagedLightmap = 0;
        stagedTexGen = 0;
        stagedClipPlanes = 0;
        stagedMisc = 0;
    }

    UniformRingBuffer getRing() {
        return ring;
    }

    ByteBuffer getStaging() {
        return staging;
    }

    public void destroy() {
        memFree(staging);
        memFree(clipPlaneBuf);
        if (ring != null) {
            ring.destroy();
            ring = null;
        }
        bound = false;
    }
}
