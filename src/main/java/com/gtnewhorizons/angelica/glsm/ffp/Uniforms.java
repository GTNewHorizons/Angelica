package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.states.ClipPlaneState;
import com.gtnewhorizons.angelica.glsm.states.FogState;
import com.gtnewhorizons.angelica.glsm.states.LightState;
import com.gtnewhorizons.angelica.glsm.states.MaterialState;
import com.gtnewhorizons.angelica.glsm.states.TexGenState;
import net.minecraft.client.renderer.OpenGlHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAllocFloat;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;

/**
 * Uploads GLSM cached state to the active FFP program's uniforms.
 *
 * Follows Mesa's prog_statevars.c patterns:
 * - Pre-computed derived state (light products, scene color) when material is static
 * - Raw light/material values when color material is active
 * - Optimized fog params (pre-divided for single MAD in shader)
 */
public class Uniforms {
    // Reusable temp buffers for uniform upload
    private final FloatBuffer mat4Buf = memAllocFloat(16);
    private final FloatBuffer mat3Buf = memAllocFloat(9);
    private final FloatBuffer vec4Buf = memAllocFloat(4);
    private final FloatBuffer vec3Buf = memAllocFloat(3);
    private final FloatBuffer clipPlaneBuf = memAllocFloat(32); // 8 planes * vec4

    // Derived matrices (computed on CPU)
    private final Matrix4f mvpMatrix = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();

    // Temp vectors for light product and normal scale computation
    private final Vector4f tempVec4 = new Vector4f();
    private final org.joml.Vector3f tempVec3 = new org.joml.Vector3f();

    // Pre-computed constants for fog params (Mesa STATE_FOG_PARAMS_OPTIMIZED)
    private static final double LN2 = Math.log(2.0);
    private static final double SQRT_LN2 = Math.sqrt(LN2);

    // Dirty tracking: last-uploaded generation per category + program ID.
    // Program change forces full re-upload since uniform locations differ.
    private int lastProgramId = -1;
    private int lastMvGen = -1;
    private int lastProjGen = -1;
    private int lastTexMatGen = -1;
    private int lastLightingGen = -1;
    private int lastFragmentGen = -1;
    private int lastColorGen = -1;
    private int lastNormalGen = -1;
    private int lastTexCoordGen = -1;
    private float lastLightmapX = Float.NaN;
    private float lastLightmapY = Float.NaN;
    private int lastTexGenGen = -1;
    private int lastClipPlaneGen = -1;

    /**
     * Upload all relevant uniforms to the given FFP program based on current GLSM state.
     * Uses generation counters to skip categories that haven't changed since last upload.
     */
    public void upload(Program program) {
        final boolean programChanged = program.getProgramId() != lastProgramId;
        lastProgramId = program.getProgramId();

        final int mvGen = GLStateManager.mvGeneration;
        final int projGen = GLStateManager.projGeneration;
        final int texMatGen = GLStateManager.texMatrixGeneration;
        final boolean mvChanged = programChanged || mvGen != lastMvGen;
        final boolean projChanged = programChanged || projGen != lastProjGen;
        final boolean texMatChanged = programChanged || texMatGen != lastTexMatGen;
        if (mvChanged || projChanged || texMatChanged) {
            uploadMatrices(program, mvChanged, projChanged, texMatChanged);
            lastMvGen = mvGen;
            lastProjGen = projGen;
            lastTexMatGen = texMatGen;
        }

        if (program.getVertexKey().lightingEnabled()) {
            final int litGen = GLStateManager.lightingGeneration;
            if (programChanged || litGen != lastLightingGen) {
                uploadLighting(program);
                lastLightingGen = litGen;
            }
        }

        // Current color/normal/texcoord — skip if generation unchanged
        if (!program.getVertexKey().hasVertexColor()) {
            final int colorGen = GLStateManager.colorGeneration;
            if (programChanged || colorGen != lastColorGen) {
                uploadCurrentColor(program);
                lastColorGen = colorGen;
            }
        }

        if (!program.getVertexKey().hasVertexNormal() && program.getVertexKey().lightingEnabled()) {
            final int normalGen = ShaderManager.getNormalGeneration();
            if (programChanged || normalGen != lastNormalGen) {
                uploadCurrentNormal(program);
                lastNormalGen = normalGen;
            }
        }

        if (!program.getVertexKey().hasVertexTexCoord() && program.getVertexKey().textureEnabled()) {
            final int texGen = ShaderManager.getTexCoordGeneration();
            if (programChanged || texGen != lastTexCoordGen) {
                uploadCurrentTexCoord(program);
                lastTexCoordGen = texGen;
            }
        }

        if (program.getVertexKey().lightmapEnabled() && !program.getVertexKey().hasVertexLightmap()) {
            if (programChanged || OpenGlHelper.lastBrightnessX != lastLightmapX || OpenGlHelper.lastBrightnessY != lastLightmapY) {
                uploadCurrentLightmapCoord(program);
                lastLightmapX = OpenGlHelper.lastBrightnessX;
                lastLightmapY = OpenGlHelper.lastBrightnessY;
            }
        }

        if (program.getVertexKey().texGenEnabled()) {
            final int tgGen = GLStateManager.texGenGeneration;
            if (programChanged || tgGen != lastTexGenGen) {
                uploadTexGen(program);
                lastTexGenGen = tgGen;
            }
        }

        if (program.getVertexKey().clipPlanesEnabled()) {
            final int cpGen = GLStateManager.clipPlaneGeneration;
            if (programChanged || cpGen != lastClipPlaneGen) {
                uploadClipPlanes(program);
                lastClipPlaneGen = cpGen;
            }
        }

        final int fragGen = GLStateManager.fragmentGeneration;
        if (programChanged || fragGen != lastFragmentGen) {
            uploadFragmentUniforms(program);
            lastFragmentGen = fragGen;
        }
    }

    private void uploadMatrices(Program program, boolean mvChanged, boolean projChanged, boolean texMatChanged) {
        final Matrix4f mv = GLStateManager.getModelViewMatrix();
        final Matrix4f proj = GLStateManager.getProjectionMatrix();

        // ModelView + derived (normal matrix, normal scale)
        if (mvChanged) {
            if (program.locModelViewMatrix != -1) {
                mv.get(mat4Buf);
                GL20.glUniformMatrix4(program.locModelViewMatrix, false, mat4Buf);
            }

            // Normal matrix = inverse transpose of upper-left 3x3 of ModelView
            if (program.locNormalMatrix != -1 || program.locNormalScale != -1) {
                mv.normal(normalMatrix);
            }
            if (program.locNormalMatrix != -1) {
                normalMatrix.get(mat3Buf);
                GL20.glUniformMatrix3(program.locNormalMatrix, false, mat3Buf);
            }

            // Normal scale (for GL_RESCALE_NORMAL without GL_NORMALIZE)
            if (program.locNormalScale != -1) {
                // Scale factor = 1/length of first column of normal matrix
                final float scale = 1.0f / normalMatrix.getColumn(0, tempVec3).length();
                GL20.glUniform1f(program.locNormalScale, scale);
            }
        }

        // Projection
        if (projChanged) {
            if (program.locProjectionMatrix != -1) {
                proj.get(mat4Buf);
                GL20.glUniformMatrix4(program.locProjectionMatrix, false, mat4Buf);
            }
        }

        // MVP = Projection * ModelView — needs recompute if either input changed
        if ((mvChanged || projChanged) && program.locMVPMatrix != -1) {
            proj.mul(mv, mvpMatrix);
            mvpMatrix.get(mat4Buf);
            GL20.glUniformMatrix4(program.locMVPMatrix, false, mat4Buf);
        }

        // Texture matrices
        if (texMatChanged) {
            // Texture matrix unit 0
            if (program.locTextureMatrix0 != -1) {
                final Matrix4f texMat = GLStateManager.getTextures().getTextureUnitMatrix(0);
                texMat.get(mat4Buf);
                GL20.glUniformMatrix4(program.locTextureMatrix0, false, mat4Buf);
            }

            // Texture matrix unit 1 (lightmap)
            if (program.locLightmapTextureMatrix != -1) {
                final Matrix4f lmTexMat = GLStateManager.getTextures().getTextureUnitMatrix(1);
                lmTexMat.get(mat4Buf);
                GL20.glUniformMatrix4(program.locLightmapTextureMatrix, false, mat4Buf);
            }
        }
    }

    private void uploadLighting(Program program) {
        final VertexKey vk = program.getVertexKey();

        if (vk.colorMaterialEnabled()) {
            uploadLightingColorMaterial(program, vk);
        } else {
            uploadLightingPreComputed(program, vk);
        }
    }

    /**
     * Color material active: upload raw light and material values.
     * The shader multiplies them with vertex color at runtime.
     */
    private void uploadLightingColorMaterial(Program program, VertexKey vk) {
        final MaterialState mat = GLStateManager.getFrontMaterial();

        uploadVec4(program.locLightModelAmbient, GLStateManager.getLightModel().ambient);
        uploadVec4(program.locMaterialEmission, mat.emission);
        uploadVec4(program.locMaterialAmbient, mat.ambient);
        uploadVec4(program.locMaterialDiffuse, mat.diffuse);
        uploadVec4(program.locMaterialSpecular, mat.specular);
        if (program.locMaterialShininess != -1) {
            GL20.glUniform1f(program.locMaterialShininess, mat.shininess);
        }

        if (vk.light0Enabled()) {
            final LightState light0 = GLStateManager.getLightDataStates()[0];
            uploadVec4(program.locLight0Ambient, light0.ambient);
            uploadVec4(program.locLight0Diffuse, light0.diffuse);
            uploadVec4(program.locLight0Specular, light0.specular);
            uploadVec4(program.locLight0Position, light0.position);
        }
        if (vk.light1Enabled()) {
            final LightState light1 = GLStateManager.getLightDataStates()[1];
            uploadVec4(program.locLight1Ambient, light1.ambient);
            uploadVec4(program.locLight1Diffuse, light1.diffuse);
            uploadVec4(program.locLight1Specular, light1.specular);
            uploadVec4(program.locLight1Position, light1.position);
        }
    }

    /**
     * No color material: upload pre-computed light products and scene color.
     * Fewer shader multiplies since material is static.
     */
    private void uploadLightingPreComputed(Program program, VertexKey vk) {
        final MaterialState mat = GLStateManager.getFrontMaterial();

        // Scene color = emission + ambient * lightModel.ambient
        // Alpha = material.diffuse.a
        if (program.locSceneColor != -1) {
            tempVec4.set(
                mat.emission.x + mat.ambient.x * GLStateManager.getLightModel().ambient.x,
                mat.emission.y + mat.ambient.y * GLStateManager.getLightModel().ambient.y,
                mat.emission.z + mat.ambient.z * GLStateManager.getLightModel().ambient.z,
                mat.diffuse.w  // alpha from diffuse
            );
            uploadVec4(program.locSceneColor, tempVec4);
        }

        if (program.locMaterialShininess != -1) {
            GL20.glUniform1f(program.locMaterialShininess, mat.shininess);
        }

        // Per-light products
        if (vk.light0Enabled()) {
            final LightState light0 = GLStateManager.getLightDataStates()[0];
            uploadVec4(program.locLight0Position, light0.position);
            uploadLightProduct(program.locLightProd0Ambient, light0.ambient, mat.ambient);
            uploadLightProduct(program.locLightProd0Diffuse, light0.diffuse, mat.diffuse);
            uploadLightProduct(program.locLightProd0Specular, light0.specular, mat.specular);
        }
        if (vk.light1Enabled()) {
            final LightState light1 = GLStateManager.getLightDataStates()[1];
            uploadVec4(program.locLight1Position, light1.position);
            uploadLightProduct(program.locLightProd1Ambient, light1.ambient, mat.ambient);
            uploadLightProduct(program.locLightProd1Diffuse, light1.diffuse, mat.diffuse);
            uploadLightProduct(program.locLightProd1Specular, light1.specular, mat.specular);
        }
    }

    /**
     * Upload pre-multiplied light product: light.X * material.X (RGB only).
     */
    private void uploadLightProduct(int loc, Vector4f lightVal, Vector4f materialVal) {
        if (loc == -1) return;
        vec3Buf.clear();
        vec3Buf.put(lightVal.x * materialVal.x);
        vec3Buf.put(lightVal.y * materialVal.y);
        vec3Buf.put(lightVal.z * materialVal.z);
        vec3Buf.flip();
        GL20.glUniform3(loc, vec3Buf);
    }

    private void uploadCurrentColor(Program program) {
        if (program.locCurrentColor == -1) return;
        // Upload the current color from GLSM
        final var color = GLStateManager.getColor();
        vec4Buf.clear();
        vec4Buf.put(color.getRed());
        vec4Buf.put(color.getGreen());
        vec4Buf.put(color.getBlue());
        vec4Buf.put(color.getAlpha());
        vec4Buf.flip();
        GL20.glUniform4(program.locCurrentColor, vec4Buf);
    }

    private void uploadCurrentTexCoord(Program program) {
        if (program.locCurrentTexCoord == -1) return;
        final var tc = ShaderManager.getCurrentTexCoord();
        uploadVec4(program.locCurrentTexCoord, tc);
    }

    private void uploadCurrentLightmapCoord(Program program) {
        if (program.locCurrentLightmapCoord == -1) return;
        GL20.glUniform2f(program.locCurrentLightmapCoord,
            OpenGlHelper.lastBrightnessX, OpenGlHelper.lastBrightnessY);
    }

    private void uploadCurrentNormal(Program program) {
        if (program.locCurrentNormal == -1) return;
        // Upload current normal from FFPShaderManager's tracked normal
        final var normal = ShaderManager.getCurrentNormal();
        vec3Buf.clear();
        vec3Buf.put(normal.x);
        vec3Buf.put(normal.y);
        vec3Buf.put(normal.z);
        vec3Buf.flip();
        GL20.glUniform3(program.locCurrentNormal, vec3Buf);
    }

    private void uploadTexGen(Program program) {
        final VertexKey vk = program.getVertexKey();
        final TexGenState tg = GLStateManager.getTextures().getTexGenState(0);

        if (vk.texGenModeS() == VertexKey.TG_OBJ_LINEAR) {
            uploadPlane(program.locTexGenObjPlaneS, tg.getObjectPlane(org.lwjgl.opengl.GL11.GL_S));
        } else if (vk.texGenModeS() == VertexKey.TG_EYE_LINEAR) {
            uploadPlane(program.locTexGenEyePlaneS, tg.getEyePlane(org.lwjgl.opengl.GL11.GL_S));
        }

        if (vk.texGenModeT() == VertexKey.TG_OBJ_LINEAR) {
            uploadPlane(program.locTexGenObjPlaneT, tg.getObjectPlane(org.lwjgl.opengl.GL11.GL_T));
        } else if (vk.texGenModeT() == VertexKey.TG_EYE_LINEAR) {
            uploadPlane(program.locTexGenEyePlaneT, tg.getEyePlane(org.lwjgl.opengl.GL11.GL_T));
        }

        if (vk.texGenModeR() == VertexKey.TG_OBJ_LINEAR) {
            uploadPlane(program.locTexGenObjPlaneR, tg.getObjectPlane(org.lwjgl.opengl.GL11.GL_R));
        } else if (vk.texGenModeR() == VertexKey.TG_EYE_LINEAR) {
            uploadPlane(program.locTexGenEyePlaneR, tg.getEyePlane(org.lwjgl.opengl.GL11.GL_R));
        }

        if (vk.texGenModeQ() == VertexKey.TG_OBJ_LINEAR) {
            uploadPlane(program.locTexGenObjPlaneQ, tg.getObjectPlane(org.lwjgl.opengl.GL11.GL_Q));
        } else if (vk.texGenModeQ() == VertexKey.TG_EYE_LINEAR) {
            uploadPlane(program.locTexGenEyePlaneQ, tg.getEyePlane(org.lwjgl.opengl.GL11.GL_Q));
        }
    }

    private void uploadPlane(int loc, float[] plane) {
        if (loc == -1) return;
        vec4Buf.clear();
        vec4Buf.put(plane[0]);
        vec4Buf.put(plane[1]);
        vec4Buf.put(plane[2]);
        vec4Buf.put(plane[3]);
        vec4Buf.flip();
        GL20.glUniform4(loc, vec4Buf);
    }

    private void uploadClipPlanes(Program program) {
        if (program.locClipPlanes == -1) return;
        final ClipPlaneState cps = GLStateManager.getClipPlaneState();
        clipPlaneBuf.clear();
        for (int i = 0; i < GLStateManager.MAX_CLIP_PLANES; i++) {
            cps.putEyePlane(i, clipPlaneBuf);
        }
        clipPlaneBuf.flip();
        GL20.glUniform4(program.locClipPlanes, clipPlaneBuf);
    }

    private void uploadFragmentUniforms(Program program) {
        final FragmentKey fk = program.getFragmentKey();

        // Alpha test reference
        if (fk.alphaTestEnabled() && program.locAlphaRef != -1) {
            GL20.glUniform1f(program.locAlphaRef, GLStateManager.getAlphaState().getReference());
        }

        // Tex env color (only used by GL_BLEND mode)
        if (program.locTexEnvColor != -1) {
            final var envColor = GLStateManager.getTexEnvColor();
            vec4Buf.clear();
            vec4Buf.put(envColor.getRed());
            vec4Buf.put(envColor.getGreen());
            vec4Buf.put(envColor.getBlue());
            vec4Buf.put(envColor.getAlpha());
            vec4Buf.flip();
            GL20.glUniform4(program.locTexEnvColor, vec4Buf);
        }

        // Fog
        if (fk.fogMode() != FragmentKey.FOG_NONE) {
            uploadFog(program);
        }
    }

    /**
     * Upload optimized fog params (following Mesa's STATE_FOG_PARAMS_OPTIMIZED):
     */
    private void uploadFog(Program program) {
        final FogState fog = GLStateManager.getFogState();

        if (program.locFogParams != -1) {
            final float start = fog.getStart();
            final float end = fog.getEnd();
            final float density = fog.getDensity();
            final float range = end - start;

            vec4Buf.clear();
            vec4Buf.put(range != 0.0f ? -1.0f / range : 0.0f);     // [0]: -1/(end-start)
            vec4Buf.put(range != 0.0f ? end / range : 1.0f);         // [1]: end/(end-start)
            vec4Buf.put((float)(density / LN2));        // [2]: density/ln(2)
            vec4Buf.put((float)(density / SQRT_LN2));  // [3]: density/sqrt(ln(2))
            vec4Buf.flip();
            GL20.glUniform4(program.locFogParams, vec4Buf);
        }

        if (program.locFogColor != -1) {
            vec4Buf.clear();
            vec4Buf.put((float) fog.getFogColor().x);
            vec4Buf.put((float) fog.getFogColor().y);
            vec4Buf.put((float) fog.getFogColor().z);
            vec4Buf.put(fog.getFogAlpha());
            vec4Buf.flip();
            GL20.glUniform4(program.locFogColor, vec4Buf);
        }
    }

    private void uploadVec4(int loc, Vector4f v) {
        if (loc == -1) return;
        vec4Buf.clear();
        vec4Buf.put(v.x);
        vec4Buf.put(v.y);
        vec4Buf.put(v.z);
        vec4Buf.put(v.w);
        vec4Buf.flip();
        GL20.glUniform4(loc, vec4Buf);
    }

    public void destroy() {
        memFree(mat4Buf);
        memFree(mat3Buf);
        memFree(vec4Buf);
        memFree(vec3Buf);
        memFree(clipPlaneBuf);
    }
}
