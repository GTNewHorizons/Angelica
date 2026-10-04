package net.coderbot.iris.celeritas;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.shader.ProgramBinaryCache;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.gl.blending.BufferBlendOverride;
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
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.impl.gl.shader.ShaderBindingContext;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.KHRDebug;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

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
    private static ProgramBinaryCache.Key cacheKey(CeleritasTerrainPipeline.PassInfo passInfo, ChunkVertexType vertexType) {
        if (!ProgramBinaryCache.isEnabled()) return null;
        final String vertex = passInfo.sources().get(PatchShaderType.VERTEX).orElse(null);
        final String geometry = passInfo.sources().get(PatchShaderType.GEOMETRY).orElse(null);
        final String fragment = passInfo.sources().get(PatchShaderType.FRAGMENT).orElse(null);
        if (vertex == null || fragment == null) return null;

        final ProgramBinaryCache.Key key = ProgramBinaryCache.key();
        int attrIndex = 0;
        for (var attr : vertexType.getVertexFormat().getAttributes()) {
            key.attribute(attr.getName(), attrIndex++);
        }
        key.stage(ShaderType.VERTEX.id, vertex);
        if (geometry != null) key.stage(ShaderType.GEOM.id, geometry);
        key.stage(ShaderType.FRAGMENT.id, fragment);
        return key;
    }

    private static final class SavedProgram extends GlProgram<IrisCeleritasChunkShaderInterface> {
        SavedProgram(int program, Function<ShaderBindingContext, IrisCeleritasChunkShaderInterface> factory) {
            super(program, factory);
        }
    }

    @Nullable
    private GlProgram<IrisCeleritasChunkShaderInterface> createShader(IrisTerrainPass pass, CeleritasTerrainPipeline pipeline, ChunkVertexType vertexType) {
        final CeleritasTerrainPipeline.PassInfo passInfo = pipeline.getPassInfo(pass);
        final String name = "iris:celeritas-chunk-" + pass.getName();
        final BlendModeOverride blendOverride = passInfo.blendModeOverride();
        final List<BufferBlendOverride> bufferOverrides = passInfo.bufferBlendOverrides();
        final Function<ShaderBindingContext, IrisCeleritasChunkShaderInterface> factory = context -> new IrisCeleritasChunkShaderInterface(
            ((GlObject) context).handle(), context, pipeline, pass.isShadow(), blendOverride, bufferOverrides, pipeline.getCustomUniforms());

        final ProgramBinaryCache.Key cacheKey = cacheKey(passInfo, vertexType);
        if (cacheKey != null) {
            final int saved = ProgramBinaryCache.load(cacheKey);
            if (saved != 0) {
                GLDebug.nameObject(KHRDebug.GL_PROGRAM, saved, name);
                return new SavedProgram(saved, factory);
            }
        }

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
            final GlProgram.Builder builder = GlProgram.builder(name);

            builder.attachShader(vertShader);
            if (geomShader != null) {
                builder.attachShader(geomShader);
            }
            builder.attachShader(fragShader);

            // Bind all attributes from the vertex format (includes base + Iris extended attributes)
            int attrIndex = 0;
            for (var attr : vertexType.getVertexFormat().getAttributes()) {
                builder.bindAttribute(attr.getName(), attrIndex++);
            }

            final GlProgram<IrisCeleritasChunkShaderInterface> program = builder.link(factory);
            if (cacheKey != null) {
                ProgramBinaryCache.save(cacheKey, program.handle());
            }
            return program;
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
        createShaders(pipeline, pass -> pass.toTerrainPass(configuration).vertexType());
    }

    public void createShaders(CeleritasTerrainPipeline pipeline, ChunkVertexType vertexType) {
        createShaders(pipeline, pass -> vertexType);
    }

    private void createShaders(CeleritasTerrainPipeline pipeline, Function<IrisTerrainPass, ChunkVertexType> vertexTypeOf) {
        if (pipeline != null) {
            for (IrisTerrainPass pass : IrisTerrainPass.VALUES) {
                if (pass.isShadow() && !pipeline.hasShadowPass()) {
                    this.programs.put(pass, null);
                    continue;
                }
                this.programs.put(pass, createShader(pass, pipeline, vertexTypeOf.apply(pass)));
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
            if (pass.isReverseOrder()) {
                return programs.get(IrisTerrainPass.SHADOW_TRANSLUCENT);
            }
            return programs.get(pass.supportsFragmentDiscard() ? IrisTerrainPass.SHADOW_CUTOUT : IrisTerrainPass.SHADOW);
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
