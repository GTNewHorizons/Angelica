package net.coderbot.iris.celeritas;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.EntityRendererAccessor;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.samplers.IrisSamplers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import net.coderbot.iris.gl.program.ProgramImages;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.uniforms.custom.CustomUniforms;
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

public class IrisCeleritasChunkShaderInterface implements ChunkShaderInterface {
    @Nullable
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

    // Iris program state
    private final ProgramUniforms irisProgramUniforms;
    private final ProgramSamplers irisProgramSamplers;
    private final ProgramImages irisProgramImages;
    private final CustomUniforms customUniforms;

    // Rendering state
    private final BlendModeOverride blendModeOverride;

    // Stored matrices for inverse and normal matrix computation
    private final Matrix4f projectionMatrixInverse = new Matrix4f();
    private final Matrix4f modelViewMatrixInverse = new Matrix4f();
    private final Matrix3f normalMatrix = new Matrix3f();

    public IrisCeleritasChunkShaderInterface(int handle, ShaderBindingContext context, CeleritasTerrainPipeline pipeline, boolean isShadowPass, BlendModeOverride blendModeOverride, CustomUniforms customUniforms) {
        this.uniformModelViewMatrix = context.bindUniformIfPresent("iris_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformModelViewMatrixInverse = context.bindUniformIfPresent("iris_ModelViewMatrixInverse", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniformIfPresent("iris_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrixInverse = context.bindUniformIfPresent("iris_ProjectionMatrixInverse", GlUniformMatrix4f::new);
        this.uniformNormalMatrix = context.bindUniformIfPresent("iris_NormalMatrix", GlUniformMatrix3f::new);
        this.uniformRegionOffset = context.bindUniformIfPresent("u_RegionOffset", GlUniformFloat3v::new);

        this.blendModeOverride = blendModeOverride;
        this.customUniforms = customUniforms;

        final ProgramUniforms.Builder builder = pipeline.initUniforms(handle);
        customUniforms.mapholderToPass(builder, this);
        this.irisProgramUniforms = builder.buildUniforms();
        this.irisProgramSamplers = isShadowPass ? pipeline.initShadowSamplers(handle) : pipeline.initTerrainSamplers(handle);
        this.irisProgramImages = isShadowPass ? pipeline.initShadowImages(handle) : pipeline.initTerrainImages(handle);
    }

    @Override
    public void setupState(TerrainRenderPass pass) {
        bindFramebuffer(pass);

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            GLStateManager.disableCull();
        }

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + IrisSamplers.ALBEDO_TEXTURE_UNIT);
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, Minecraft.getMinecraft().getTextureMapBlocks().getGlTextureId());

        GLStateManager.glActiveTexture(GL13.GL_TEXTURE0 + IrisSamplers.LIGHTMAP_TEXTURE_UNIT);
        final DynamicTexture lightmapTexture = ((EntityRendererAccessor) Minecraft.getMinecraft().entityRenderer).getLightmapTexture();
        GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, lightmapTexture.getGlTextureId());

        if (blendModeOverride != null) {
            blendModeOverride.apply();
        }

        if (irisProgramUniforms != null) {
            irisProgramUniforms.update();
        }
        if (irisProgramSamplers != null) {
            irisProgramSamplers.update();
        }
        if (irisProgramImages != null) {
            irisProgramImages.update();
        }

        customUniforms.push(this);
    }

    @Override
    public void restoreState() {
        if (blendModeOverride != null) {
            BlendModeOverride.restore();
        }

        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
    }

    @Override
    public GlPrimitiveType getPrimitiveType() {
        return GlPrimitiveType.QUADS;
    }

    @Override
    public void setProjectionMatrix(Matrix4fc matrix) {
        if (uniformProjectionMatrix != null) {
            uniformProjectionMatrix.set(matrix);
        }

        if (uniformProjectionMatrixInverse != null) {
            projectionMatrixInverse.set(matrix);
            projectionMatrixInverse.invert();
            uniformProjectionMatrixInverse.set(projectionMatrixInverse);
        }
    }

    @Override
    public void setModelViewMatrix(Matrix4fc modelView) {
        if (uniformModelViewMatrix != null) {
            uniformModelViewMatrix.set(modelView);
        }

        if (uniformModelViewMatrixInverse != null) {
            modelViewMatrixInverse.set(modelView);
            modelViewMatrixInverse.invert();
            uniformModelViewMatrixInverse.set(modelViewMatrixInverse);
        }

        if (uniformNormalMatrix != null) {
            normalMatrix.set(modelView);
            normalMatrix.invert();
            normalMatrix.transpose();
            uniformNormalMatrix.set(normalMatrix);
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
            irisPass = pass.supportsFragmentDiscard() ? IrisTerrainPass.SHADOW_CUTOUT : IrisTerrainPass.SHADOW;
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

    @org.jetbrains.annotations.Nullable
    private CeleritasTerrainPipeline getCeleritasTerrainPipeline() {
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline != null ? pipeline.getCeleritasTerrainPipeline() : null;
    }
}
