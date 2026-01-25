package net.coderbot.iris.celeritas;

import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.pipeline.transform.PatchShaderType;
import net.coderbot.iris.shadows.ShadowRenderingState;
import org.embeddedt.embeddium.impl.gl.GlObject;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;

public class IrisCeleritasChunkProgramOverrides {
    private final EnumMap<IrisTerrainPass, GlProgram<IrisCeleritasChunkShaderInterface>> programs = new EnumMap<>(IrisTerrainPass.class);
    private boolean shadersCreated = false;
    private int versionCounterForShaderReload = -1;

    @Nullable
    private GlShader createVertexShader(IrisTerrainPass pass, CeleritasTerrainPipeline pipeline) {
        final CeleritasTerrainPipeline.PassInfo info = pipeline.getPassInfo(pass);
        final Optional<String> source = info.sources().get(PatchShaderType.VERTEX);

        return source.map(s -> new GlShader(ShaderType.VERTEX,
            "iris:celeritas-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".vsh", s))
            .orElse(null);
    }

    @Nullable
    private GlShader createGeometryShader(IrisTerrainPass pass, CeleritasTerrainPipeline pipeline) {
        final CeleritasTerrainPipeline.PassInfo info = pipeline.getPassInfo(pass);
        final Optional<String> source = info.sources().get(PatchShaderType.GEOMETRY);

        return source.map(s -> new GlShader(ShaderType.GEOM,
            "iris:celeritas-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".gsh", s))
            .orElse(null);
    }

    @Nullable
    private GlShader createFragmentShader(IrisTerrainPass pass, CeleritasTerrainPipeline pipeline) {
        final CeleritasTerrainPipeline.PassInfo info = pipeline.getPassInfo(pass);
        final Optional<String> source = info.sources().get(PatchShaderType.FRAGMENT);

        return source.map(s -> new GlShader(ShaderType.FRAGMENT,
            "iris:celeritas-terrain-" + pass.toString().toLowerCase(Locale.ROOT) + ".fsh", s))
            .orElse(null);
    }

    @Nullable
    private GlProgram<IrisCeleritasChunkShaderInterface> createShader(IrisTerrainPass pass, CeleritasTerrainPipeline pipeline, RenderPassConfiguration<?> configuration) {
        final GlShader vertShader = createVertexShader(pass, pipeline);
        final GlShader geomShader = createGeometryShader(pass, pipeline);
        final GlShader fragShader = createFragmentShader(pass, pipeline);

        if (vertShader == null || fragShader == null) {
            if (vertShader != null) vertShader.delete();
            if (geomShader != null) geomShader.delete();
            if (fragShader != null) fragShader.delete();
            return null;
        }

        try {
            final GlProgram.Builder builder = GlProgram.builder("iris:celeritas-chunk-" + pass.getName());

            builder.attachShader(vertShader);
            if (geomShader != null) {
                builder.attachShader(geomShader);
            }
            builder.attachShader(fragShader);

            // Bind all attributes from the vertex format (includes base + Iris extended attributes)
            final var vertexType =pass.toTerrainPass(configuration).vertexType();
            int attrIndex = 0;
            for (var attr : vertexType.getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), attrIndex++);
            }

            // Get blend from PassInfo
            final CeleritasTerrainPipeline.PassInfo passInfo = pipeline.getPassInfo(pass);
            final BlendModeOverride blendOverride = passInfo.blendModeOverride();

            return builder.link(context -> new IrisCeleritasChunkShaderInterface(((GlObject) context).handle(), context, pipeline, pass.isShadow(), blendOverride, pipeline.getCustomUniforms()));
        } finally {
            vertShader.delete();
            if (geomShader != null) geomShader.delete();
            fragShader.delete();
        }
    }

    /**
     * Create shaders for all Iris terrain passes.
     */
    public void createShaders(CeleritasTerrainPipeline pipeline, RenderPassConfiguration<?> configuration) {
        if (pipeline != null) {
            for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
                if (pass.isShadow() && !pipeline.hasShadowPass()) {
                    this.programs.put(pass, null);
                    continue;
                }
                this.programs.put(pass, createShader(pass, pipeline, configuration));
            }
        } else {
            deleteShaders();
        }
        shadersCreated = true;
    }

    @Nullable
    public GlProgram<? extends ChunkShaderInterface> getProgramOverride(TerrainRenderPass pass, RenderPassConfiguration<?> configuration) {
        // Check for shader pack reload
        if (versionCounterForShaderReload != Iris.getPipelineManager().getVersionCounterForSodiumShaderReload()) {
            versionCounterForShaderReload = Iris.getPipelineManager().getVersionCounterForSodiumShaderReload();
            deleteShaders();
        }

        final WorldRenderingPipeline worldPipeline = Iris.getPipelineManager().getPipelineNullable();
        CeleritasTerrainPipeline celeritasPipeline = null;
        if (worldPipeline != null) {
            celeritasPipeline = worldPipeline.getCeleritasTerrainPipeline();
        }

        if (!shadersCreated) {
            createShaders(celeritasPipeline, configuration);
        }

        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (celeritasPipeline != null && !celeritasPipeline.hasShadowPass()) {
                throw new IllegalStateException("Shadow program requested, but shader pack has no shadow pass");
            }
            return programs.get(pass.supportsFragmentDiscard() ?
                IrisTerrainPass.SHADOW_CUTOUT : IrisTerrainPass.SHADOW);
        } else {
            if (pass.supportsFragmentDiscard()) {
                return programs.get(IrisTerrainPass.GBUFFER_CUTOUT);
            } else if (pass.isReverseOrder()) {
                return programs.get(IrisTerrainPass.GBUFFER_TRANSLUCENT);
            } else {
                return programs.get(IrisTerrainPass.GBUFFER_SOLID);
            }
        }
    }

    public void deleteShaders() {
        for (GlProgram<?> program : programs.values()) {
            if (program != null) {
                program.delete();
            }
        }
        programs.clear();
        shadersCreated = false;
    }
}
