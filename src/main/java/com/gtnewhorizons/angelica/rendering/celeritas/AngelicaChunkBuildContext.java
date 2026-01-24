package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;

import java.util.Arrays;
import java.util.List;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.BlockRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexEncoder;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.flat.FlatLightPipeline;
import org.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import com.gtnewhorizons.angelica.rendering.celeritas.light.LightDataCache;
import com.gtnewhorizons.angelica.rendering.celeritas.light.QuadLightingHelper;
import com.gtnewhorizons.angelica.rendering.celeritas.light.VanillaDiffuseProvider;
import org.embeddedt.embeddium.impl.model.light.smooth.SmoothLightPipeline;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import lombok.Getter;
import me.jellysquid.mods.sodium.client.SodiumClientMod;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.IBlockAccess;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;

public class AngelicaChunkBuildContext extends ChunkBuildContext {
    public static final int NUM_PASSES = 2;

    private final TextureMapExtension textureAtlas;
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    @Getter
    private final WorldSlice worldSlice;
    @Getter
    private final BlockRenderContext blockRenderContext = new BlockRenderContext();

    private List<IDynamicLightSource> chunkLightSources;
    private DynamicLights dynamicLightsInstance;

    private final LightDataCache lightDataCache = new LightDataCache();
    private final SmoothLightPipeline smoothLightPipeline;
    private final FlatLightPipeline flatLightPipeline;
    private final QuadLightData quadLightData = new QuadLightData();
    private final VertexArrayQuadView quadView;
    private boolean lightPipelineReady = false;
    private int originX, originY, originZ;

    public AngelicaChunkBuildContext(RenderPassConfiguration<?> renderPassConfiguration, WorldClient world) {
        super(renderPassConfiguration);
        this.textureAtlas = (TextureMapExtension) Minecraft.getMinecraft().getTextureMapBlocks();
        this.worldSlice = new WorldSlice(world);
        this.smoothLightPipeline = new SmoothLightPipeline(lightDataCache, VanillaDiffuseProvider.INSTANCE, false);
        this.flatLightPipeline = new FlatLightPipeline(lightDataCache, VanillaDiffuseProvider.INSTANCE, false);
        this.quadView = new VertexArrayQuadView(vertices);
    }

    public void setupLightPipeline(int minBlockX, int minBlockY, int minBlockZ) {
        setupLightPipeline(worldSlice, minBlockX, minBlockY, minBlockZ);
    }

    public void setupLightPipeline(IBlockAccess blockAccess, int minBlockX, int minBlockY, int minBlockZ) {
        this.originX = minBlockX;
        this.originY = minBlockY;
        this.originZ = minBlockZ;
        lightDataCache.setWorld(blockAccess);
        lightDataCache.reset(minBlockX, minBlockY, minBlockZ);
        smoothLightPipeline.reset();
        flatLightPipeline.reset();
        lightPipelineReady = true;
    }

    public void setupDynamicLights(int chunkOriginX, int chunkOriginY, int chunkOriginZ) {
        if (DynamicLights.isEnabled()) {
            this.dynamicLightsInstance = DynamicLights.get();
            this.chunkLightSources = dynamicLightsInstance.getSourcesForChunk(chunkOriginX, chunkOriginY, chunkOriginZ);
        } else {
            this.dynamicLightsInstance = null;
            this.chunkLightSources = null;
        }
    }

    private Material selectMaterial(Material material, TextureAtlasSprite sprite, boolean isShaderPackOverride) {
        // Don't apply transparency-based optimization when shader pack explicitly overrides the material
        if (isShaderPackOverride) {
            return material;
        }
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata()) {
            final var transparencyLevel = ((SpriteExtension)sprite).celeritas$getTransparencyLevel();
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE && material == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL) {
                return AngelicaRenderPassConfiguration.SOLID_MATERIAL;
            } else if (material == AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                return AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL;
            }
        }
        return material;
    }

    @SuppressWarnings("unchecked")
    public void copyRawBuffer(int[] rawBuffer, int vertexCount, ChunkBuildBuffers buffers, Material material, boolean isShaderPackOverride,
                              boolean blockAllowsSmoothLighting) {
        if (vertexCount == 0) {
            return;
        }

        final var animatedSprites = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>)buffers.getSectionContextBundle()).animatedSprites;

        if ((vertexCount & 0x3) != 0) {
            throw new IllegalStateException("Only quads are supported, got: " + vertexCount);
        }

        final boolean hasDynamicLights = chunkLightSources != null && !chunkLightSources.isEmpty();
        final boolean separateAo = BlockRenderingSettings.INSTANCE.shouldUseSeparateAo();
        final boolean celeritasSmoothLighting = SodiumClientMod.options().quality.useCeleritasSmoothLighting;
        final boolean shaderActive = IrisApi.getInstance().isShaderPackInUse();
        final boolean useAoCalculation = lightPipelineReady && (separateAo || celeritasSmoothLighting || shaderActive);
        final boolean stripDiffuse = shaderActive && BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
        final boolean shade = false; // Vanilla bakes it, or we remove it - so no need to shade
        final ChunkColorWriter colorEncoder = separateAo ? ChunkColorWriter.SEPARATE_AO : ChunkColorWriter.EMBEDDIUM;

        int ptr = 0;
        final int numQuads = vertexCount / 4;

        for (int quadIdx = 0; quadIdx < numQuads; quadIdx++) {
            float uSum = 0, vSum = 0;

            for (int vIdx = 0; vIdx < 4; vIdx++) {
                final var vertex = vertices[vIdx];
                vertex.x = Float.intBitsToFloat(rawBuffer[ptr++]);
                vertex.y = Float.intBitsToFloat(rawBuffer[ptr++]);
                vertex.z = Float.intBitsToFloat(rawBuffer[ptr++]);

                final float u = Float.intBitsToFloat(rawBuffer[ptr++]);
                final float v = Float.intBitsToFloat(rawBuffer[ptr++]);
                vertex.u = u;
                uSum += u;
                vertex.v = v;
                vSum += v;

                vertex.color = rawBuffer[ptr++];
                vertex.vanillaNormal = rawBuffer[ptr++];
                vertex.light = rawBuffer[ptr++];
            }

            final int trueNormal = QuadUtil.calculateNormal(vertices);
            final ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            final TextureAtlasSprite sprite = this.textureAtlas.celeritas$findFromUV(uSum * 0.25f, vSum * 0.25f);

            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSprites.add(sprite);
            }

            // Strip vanilla's baked diffuse when shaders want oldLighting=false - multiply by an inversion factor
            // instead of attempting to mixin/asm all the places that bake it in
            if (stripDiffuse) {
                final float inverseDiffuse = VanillaDiffuseProvider.INSTANCE.getInverseDiffuse(facing);
                for (int vIdx = 0; vIdx < 4; vIdx++) {
                    vertices[vIdx].color = VanillaDiffuseProvider.multiplyColor(vertices[vIdx].color, inverseDiffuse);
                }
            }

            if (useAoCalculation) {
                final int blockX = blockRenderContext.localPosX;
                final int blockY = blockRenderContext.localPosY;
                final int blockZ = blockRenderContext.localPosZ;

                final boolean isEmissive = LightDataAccess.unpackEM(lightDataCache.get(originX + blockX, originY + blockY, originZ + blockZ));
                final boolean quadIsFullBright = QuadLightingHelper.isQuadFullBright(vertices);

                if (isEmissive || quadIsFullBright) {
                    Arrays.fill(quadLightData.br, 1.0f);
                    if (isEmissive) {
                        Arrays.fill(quadLightData.lm, LightDataAccess.FULL_BRIGHT);
                    } else {
                        for (int i = 0; i < 4; i++) {
                            quadLightData.lm[i] = vertices[i].light;
                        }
                    }
                } else {
                    quadView.setup(trueNormal, blockX, blockY, blockZ);
                    final ModelQuadFacing lightFace = quadView.getLightFace();
                    final LightPipeline pipeline = blockAllowsSmoothLighting ? smoothLightPipeline : flatLightPipeline;
                    final ModelQuadFacing cullFace = quadView.getCullFace();
                    pipeline.calculate(quadView, originX + blockX, originY + blockY, originZ + blockZ, quadLightData, cullFace, lightFace, shade, true);
                }

                for (int vIdx = 0; vIdx < 4; vIdx++) {
                    final var vertex = vertices[vIdx];
                    vertex.trueNormal = trueNormal;
                    vertex.color = colorEncoder.writeColor(vertex.color, quadLightData.br[vIdx]);
                    vertex.light = quadLightData.lm[vIdx];
                }
            } else {
                for (int vIdx = 0; vIdx < 4; vIdx++) {
                    vertices[vIdx].trueNormal = trueNormal;
                }
            }

            // Apply dynamic lights after AO calculation so they're not overwritten
            if (hasDynamicLights) {
                for (int vIdx = 0; vIdx < 4; vIdx++) {
                    final var vertex = vertices[vIdx];
                    final double dynamicLevel = dynamicLightsInstance.getDynamicLightLevelFromSources(originX + vertex.x, originY + vertex.y, originZ + vertex.z, chunkLightSources);
                    if (dynamicLevel > 0) {
                        vertex.light = dynamicLightsInstance.getLightmapWithDynamicLight(dynamicLevel, vertex.light);
                    }
                }
            }

            final Material correctMaterial = selectMaterial(material, sprite, isShaderPackOverride);
            final var builder = buffers.get(correctMaterial);

            if (correctMaterial != material && builder.getEncoder() instanceof IrisExtendedChunkVertexEncoder iris) {
                iris.setContext(blockRenderContext);
            }

            builder.getVertexBuffer(facing).push(vertices, correctMaterial);
        }
    }
}
