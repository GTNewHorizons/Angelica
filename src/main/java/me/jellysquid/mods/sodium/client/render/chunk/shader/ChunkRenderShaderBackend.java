package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.gtnewhorizons.angelica.compat.toremove.MatrixStack;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.compat.FogHelper;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.gl.shader.GlProgram;
import me.jellysquid.mods.sodium.client.gl.shader.GlShader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderConstants;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderLoader;
import me.jellysquid.mods.sodium.client.gl.shader.ShaderType;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shaderpack.transform.StringTransformations;
import net.coderbot.iris.shaderpack.transform.Transformations;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.coderbot.iris.sodium.shader_overrides.IrisChunkProgramOverrides;
import net.coderbot.iris.sodium.vertex_format.IrisModelVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;

public abstract class ChunkRenderShaderBackend<T extends ChunkGraphicsState> implements ChunkRenderBackend<T> {

    private final EnumMap<ChunkFogMode, ChunkProgram> programs = new EnumMap<>(ChunkFogMode.class);

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat<ChunkMeshAttribute> vertexFormat;

    protected ChunkProgram activeProgram;

    // Iris
    private IrisChunkProgramOverrides irisChunkProgramOverrides;

    private RenderDevice device;

    private ChunkProgram override;

    public ChunkRenderShaderBackend(ChunkVertexType vertexType) {
        if (AngelicaConfig.enableIris) {
            irisChunkProgramOverrides = new IrisChunkProgramOverrides();
        }

        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getCustomVertexFormat();
    }

    public GlShader loadShaderRedirect(RenderDevice device, ShaderType type, ResourceLocation name, List<String> constants) {
        if (AngelicaConfig.enableIris && this.vertexType == IrisModelVertexFormats.MODEL_VERTEX_XHFP) {
            String shader = getShaderSource(ShaderLoader.getShaderPath(name, type));
            shader = shader.replace("v_LightCoord = a_LightCoord", "v_LightCoord = (iris_LightmapTextureMatrix * vec4(a_LightCoord, 0, 1)).xy");

            StringTransformations transformations = new StringTransformations(shader);

            transformations.injectLine(
                Transformations.InjectionPoint.BEFORE_CODE,
                "mat4 iris_LightmapTextureMatrix = mat4(vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0), vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0));");

            return new GlShader(device, type, name, transformations.toString(), ShaderConstants.fromStringList(constants));
        } else {
            return ShaderLoader.loadShader(device, type, name, ShaderConstants.fromStringList(constants));
        }
    }

    private ChunkProgram createShader(RenderDevice device, ChunkFogMode fogMode, GlVertexFormat<ChunkMeshAttribute> vertexFormat) {
        if(AngelicaConfig.enableIris) {
            this.device = device;
            WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipelineNullable();
            SodiumTerrainPipeline sodiumTerrainPipeline = null;

            if (worldRenderingPipeline != null) {
                sodiumTerrainPipeline = worldRenderingPipeline.getSodiumTerrainPipeline();
            }

            irisChunkProgramOverrides.createShaders(sodiumTerrainPipeline, device);
        }

        GlShader vertShader = loadShaderRedirect(device, ShaderType.VERTEX, new ResourceLocation("sodium", "chunk_gl20"), fogMode.getDefines());

        GlShader fragShader = ShaderLoader.loadShader(device, ShaderType.FRAGMENT, new ResourceLocation("sodium", "chunk_gl20"), ShaderConstants.fromStringList(fogMode.getDefines()));

        try {
            return GlProgram.builder(new ResourceLocation("sodium", "chunk_shader"))
                .attachShader(vertShader)
                .attachShader(fragShader)
                .bindAttribute("a_Pos", ChunkShaderBindingPoints.POSITION)
                .bindAttribute("a_Color", ChunkShaderBindingPoints.COLOR)
                .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.TEX_COORD)
                .bindAttribute("a_LightCoord", ChunkShaderBindingPoints.LIGHT_COORD)
                .bindAttribute("d_ModelOffset", ChunkShaderBindingPoints.MODEL_OFFSET)
                .build((program, name) -> new ChunkProgram(device, program, name, fogMode.getFactory()));
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }



    private static String getShaderSource(String path) {
        try {
            InputStream in = ShaderLoader.class.getResourceAsStream(path);
            Throwable throwable = null;

            String res;
            try {
                if (in == null) {
                    throw new RuntimeException("Shader not found: " + path);
                }

                res = IOUtils.toString(in, StandardCharsets.UTF_8);
            } catch (Throwable tr) {
                throwable = tr;
                throw tr;
            } finally {
                if (in != null) {
                    if (throwable != null) {
                        try {
                            in.close();
                        } catch (Throwable var12) {
                            throwable.addSuppressed(var12);
                        }
                    } else {
                        in.close();
                    }
                }

            }

            return res;
        } catch (IOException e) {
            throw new RuntimeException("Could not read shader sources", e);
        }
    }


    @Override
    public final void createShaders(RenderDevice device) {
        this.programs.put(ChunkFogMode.NONE, this.createShader(device, ChunkFogMode.NONE, this.vertexFormat));
        this.programs.put(ChunkFogMode.LINEAR, this.createShader(device, ChunkFogMode.LINEAR, this.vertexFormat));
        this.programs.put(ChunkFogMode.EXP2, this.createShader(device, ChunkFogMode.EXP2, this.vertexFormat));
    }

    @Override
    public void begin(MatrixStack matrixStack) {
        this.activeProgram = this.programs.get(FogHelper.getFogMode());
        if (AngelicaConfig.enableIris && override != null) {
            this.activeProgram = override;
        }
        this.activeProgram.bind();
        this.activeProgram.setup(matrixStack, this.vertexType.getModelScale(), this.vertexType.getTextureScale());
    }

    @Override
    public void end(MatrixStack matrixStack) {
        this.activeProgram.unbind();
        this.activeProgram = null;
        if(AngelicaConfig.enableIris) {
            ProgramUniforms.clearActiveUniforms();
            ProgramSamplers.clearActiveSamplers();
            Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::endSodiumTerrainRendering);
        }
    }

    @Override
    public void delete() {
        if(AngelicaConfig.enableIris) {
            irisChunkProgramOverrides.deleteShaders();
        }
        for (ChunkProgram shader : this.programs.values()) {
            shader.delete();
        }
    }

    @Override
    public ChunkVertexType getVertexType() {
        return this.vertexType;
    }

    // Iris
    public void iris$begin(MatrixStack matrixStack, BlockRenderPass pass) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            // No back face culling during the shadow pass
            // TODO: Hopefully this won't be necessary in the future...
            GLStateManager.disableCullFace();
        }

        this.override = irisChunkProgramOverrides.getProgramOverride(device, pass);

        Iris.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::beginSodiumTerrainRendering);
        begin(matrixStack);
    }

}
