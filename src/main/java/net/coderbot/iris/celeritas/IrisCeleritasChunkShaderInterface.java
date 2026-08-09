package net.coderbot.iris.celeritas;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat3v;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderTextureSlot;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.util.List;

public class IrisCeleritasChunkShaderInterface implements ChunkShaderInterface {
    @Nullable
    private static final Tracy.ZoneId Z_IRIS_SETUP_FBO = Tracy.zoneId("iris_setup_fbo", Tracy.COLOR_IRIS);
    private static final Tracy.ZoneId Z_IRIS_SETUP_UNIFORMS = Tracy.zoneId("iris_setup_uniforms", Tracy.COLOR_IRIS);
    private static final Tracy.ZoneId Z_IRIS_SETUP_SAMPLERS = Tracy.zoneId("iris_setup_samplers", Tracy.COLOR_IRIS);
    private static final Tracy.ZoneId Z_IRIS_SETUP_IMAGES = Tracy.zoneId("iris_setup_images", Tracy.COLOR_IRIS);
    private static final Tracy.ZoneId Z_IRIS_SETUP_CUSTOM = Tracy.zoneId("iris_setup_custom", Tracy.COLOR_IRIS);

    private final GlUniformMatrix4f uniformModelViewMatrix;
    @Nullable
    private final GlUniformMatrix4f uniformModelViewMatrixInverse;
    @Nullable
    private final GlUniformMatrix4f uniformProjectionMatrix;
    @Nullable
    private final GlUniformMatrix4f uniformProjectionMatrixInverse;
    @Nullable
    private final GlUniformMatrix3f uniformNormalMatrix;
    @Nullable
    private final GlUniformFloat3v uniformRegionOffset;
    @Nullable
    private final GlUniformMatrix4f uniformLightmapTextureMatrix;

    // Iris program state
    private final ProgramUniforms irisProgramUniforms;
    private final ProgramSamplers irisProgramSamplers;
    private final ProgramImages irisProgramImages;
    private final CustomUniforms customUniforms;

    // Rendering state
    private final BlendModeOverride blendModeOverride;
    private final List<BufferBlendOverride> bufferBlendOverrides;
    private final boolean hasOverrides;

    private final Matrix4f lastProjection = new Matrix4f();
    private final Matrix4f lastModelView = new Matrix4f();
    private boolean hasLastProjection;
    private boolean hasLastModelView;

    private static final Matrix4f sharedProjKey = new Matrix4f();
    private static final Matrix4f sharedProjInverse = new Matrix4f();
    private static boolean sharedProjValid;
    private static final Matrix4f sharedMvKey = new Matrix4f();
    private static final Matrix4f sharedMvInverse = new Matrix4f();
    private static final Matrix3f sharedNormal = new Matrix3f();
    private static boolean sharedMvValid;

    public IrisCeleritasChunkShaderInterface(int handle, ShaderBindingContext context, CeleritasTerrainPipeline pipeline, boolean isShadowPass, BlendModeOverride blendModeOverride, List<BufferBlendOverride> bufferBlendOverrides, CustomUniforms customUniforms) {
        this.uniformModelViewMatrix = context.bindUniformIfPresent("iris_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformModelViewMatrixInverse = context.bindUniformIfPresent("iris_ModelViewMatrixInverse", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniformIfPresent("iris_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrixInverse = context.bindUniformIfPresent("iris_ProjectionMatrixInverse", GlUniformMatrix4f::new);
        this.uniformNormalMatrix = context.bindUniformIfPresent("iris_NormalMatrix", GlUniformMatrix3f::new);
        this.uniformRegionOffset = context.bindUniformIfPresent("u_RegionOffset", GlUniformFloat3v::new);
        this.uniformLightmapTextureMatrix = context.bindUniformIfPresent("iris_LightmapTextureMatrix", GlUniformMatrix4f::new);

        this.blendModeOverride = blendModeOverride;
        this.bufferBlendOverrides = bufferBlendOverrides;
        this.hasOverrides = bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty();
        this.customUniforms = customUniforms;

        final ProgramUniforms.Builder builder = pipeline.initUniforms(handle);
        customUniforms.mapholderToPass(builder, this);
        this.irisProgramUniforms = builder.buildUniforms();
        this.irisProgramSamplers = isShadowPass ? pipeline.initShadowSamplers(handle) : pipeline.initTerrainSamplers(handle);
        this.irisProgramImages = isShadowPass ? pipeline.initShadowImages(handle) : pipeline.initTerrainImages(handle);
    }

    @Override
    public void setupState(TerrainRenderPass pass) {
        if (Tracy.ENABLED) Tracy.beginZone(Z_IRIS_SETUP_FBO);
        bindFramebuffer(pass);

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            GLStateManager.disableCull();
        }

        // Explicitly set iris_LightmapTextureMatrix rather than relying on CompatUniformManager.
        // At high uniform locations in large UBOs, the compat manager's writes may not persist
        // through the per-program dirtyUniforms swap in non-GL backends.
        if (uniformLightmapTextureMatrix != null) {
            uniformLightmapTextureMatrix.set(GLStateManager.getTextures().getTextureUnitMatrix(1));
        }

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + IrisSamplers.ALBEDO_TEXTURE_UNIT);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, Minecraft.getMinecraft().getTextureMapBlocks().getGlTextureId());

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + IrisSamplers.LIGHTMAP_TEXTURE_UNIT);
        final DynamicTexture lightmapTexture = ((EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer).getLightmapTexture();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture.getGlTextureId());

        if (blendModeOverride != null) {
            blendModeOverride.apply();
        }

        if (hasOverrides) {
            bufferBlendOverrides.forEach(BufferBlendOverride::apply);
        }
        if (Tracy.ENABLED) Tracy.endZone();

        if (irisProgramUniforms != null) {
            if (Tracy.ENABLED) Tracy.beginZone(Z_IRIS_SETUP_UNIFORMS);
            irisProgramUniforms.update();
            if (Tracy.ENABLED) Tracy.endZone();
        }
        if (irisProgramSamplers != null) {
            if (Tracy.ENABLED) Tracy.beginZone(Z_IRIS_SETUP_SAMPLERS);
            irisProgramSamplers.update();
            if (Tracy.ENABLED) Tracy.endZone();
        }
        if (irisProgramImages != null) {
            if (Tracy.ENABLED) Tracy.beginZone(Z_IRIS_SETUP_IMAGES);
            irisProgramImages.update();
            if (Tracy.ENABLED) Tracy.endZone();
        }

        if (Tracy.ENABLED) Tracy.beginZone(Z_IRIS_SETUP_CUSTOM);
        customUniforms.push(this);
        if (Tracy.ENABLED) Tracy.endZone();
    }

    @Override
    public void restoreState() {
        if (blendModeOverride != null || hasOverrides) {
            BlendModeOverride.restore();
        }

        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
    }

    @Override
    public GlPrimitiveType getPrimitiveType() {
        return GlPrimitiveType.TRIANGLES;
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        if (hasLastProjection && lastProjection.equals(matrix, 0.0f)) {
            return;
        }
        hasLastProjection = true;
        lastProjection.set(matrix);

        if (uniformProjectionMatrix != null) {
            uniformProjectionMatrix.set(matrix);
        }

        if (uniformProjectionMatrixInverse != null) {
            if (!sharedProjValid || !sharedProjKey.equals(matrix, 0.0f)) {
                sharedProjValid = true;
                sharedProjKey.set(matrix);
                sharedProjInverse.set(matrix);
                sharedProjInverse.invert();
            }
            uniformProjectionMatrixInverse.set(sharedProjInverse);
        }
    }

    @Override
    public void setModelViewMatrix(Matrix4fc modelView) {
        if (hasLastModelView && lastModelView.equals(modelView, 0.0f)) {
            return;
        }
        hasLastModelView = true;
        lastModelView.set(modelView);

        if (uniformModelViewMatrix != null) {
            uniformModelViewMatrix.set(modelView);
        }

        if (uniformModelViewMatrixInverse != null || uniformNormalMatrix != null) {
            if (!sharedMvValid || !sharedMvKey.equals(modelView, 0.0f)) {
                sharedMvValid = true;
                sharedMvKey.set(modelView);
                sharedMvInverse.set(modelView);
                sharedMvInverse.invert();
                sharedNormal.set(modelView);
                sharedNormal.invert();
                sharedNormal.transpose();
            }
            if (uniformModelViewMatrixInverse != null) {
                uniformModelViewMatrixInverse.set(sharedMvInverse);
            }
            if (uniformNormalMatrix != null) {
                uniformNormalMatrix.set(sharedNormal);
            }
        }
    }

    @Override
    public void setRegionOffset(float x, float y, float z) {
        if (uniformRegionOffset != null) {
            uniformRegionOffset.set(x, y, z);
        }
    }

    @Override
    public void setTextureSlot(ChunkShaderTextureSlot slot, int val) {
        // No-op - Iris manages texture state internally
    }

    private void bindFramebuffer(TerrainRenderPass pass) {
        final CeleritasTerrainPipeline pipeline = getCeleritasTerrainPipeline();
        if (pipeline == null) {
            return;
        }

        final boolean isShadow = ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        final IrisTerrainPass irisPass;
        if (isShadow) {
            if (pass.isReverseOrder()) {
                irisPass = IrisTerrainPass.SHADOW_TRANSLUCENT;
            } else {
                irisPass = pass.supportsFragmentDiscard() ? IrisTerrainPass.SHADOW_CUTOUT : IrisTerrainPass.SHADOW;
            }
        } else if (pass.isReverseOrder()) {
            irisPass = IrisTerrainPass.GBUFFER_TRANSLUCENT;
        } else if (pass.supportsFragmentDiscard()) {
            irisPass = IrisTerrainPass.GBUFFER_CUTOUT;
        } else {
            irisPass = IrisTerrainPass.GBUFFER_SOLID;
        }

        final GlFramebuffer framebuffer = pipeline.getPassInfo(irisPass).framebuffer();
        if (framebuffer != null) {
            framebuffer.bind();
        }
    }

    @Nullable
    private CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline != null ? pipeline.getCeleritasTerrainPipeline() : null;
    }
}
