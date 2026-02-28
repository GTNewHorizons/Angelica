package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;
import com.gtnewhorizons.angelica.rendering.StateAwareTessellator;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.BlockRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexEncoder;
import com.gtnewhorizons.angelica.rendering.celeritas.light.LightDataCache;
import com.gtnewhorizons.angelica.rendering.celeritas.light.VanillaDiffuseProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import lombok.Getter;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.IBlockAccess;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.data.LightDataAccess;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.light.flat.FlatLightPipeline;
import org.embeddedt.embeddium.impl.model.light.smooth.SmoothLightPipeline;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.ChunkColorWriter;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildContext;
import org.embeddedt.embeddium.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.util.QuadUtil;

import java.util.Arrays;
import java.util.List;

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
    private IBlockAccess blockAccess;

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
        this.blockAccess = blockAccess;
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
        if (!AngelicaMod.options().performance.useRenderPassOptimization || isShaderPackOverride) {
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
    public void copyRawBuffer(int[] rawBuffer, int vertexCount, int[] vertexStates,
                              ChunkBuildBuffers buffers, Material material, boolean isShaderPackOverride,
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
        final boolean celeritasSmoothLighting = AngelicaMod.options().quality.useCeleritasSmoothLighting;
        final boolean shaderActive = IrisApi.getInstance().isShaderPackInUse();
        final boolean useAoCalculation = lightPipelineReady && (separateAo || celeritasSmoothLighting || shaderActive);
        final boolean shouldApplyDiffuse = !BlockRenderingSettings.INSTANCE.shouldDisableDirectionalShading();
        final ChunkColorWriter colorEncoder = separateAo ? ChunkColorWriter.SEPARATE_AO : ChunkColorWriter.EMBEDDIUM;

        int ptr = 0;
        final int numQuads = vertexCount / 4;

        final int blockX = blockRenderContext.localPosX;
        final int blockY = blockRenderContext.localPosY;
        final int blockZ = blockRenderContext.localPosZ;
        final int worldX = originX + blockX;
        final int worldY = originY + blockY;
        final int worldZ = originZ + blockZ;

        int stateIdx = 0;
        int facesAtBaseMaterial = 0;

        for (int quadIdx = 0; quadIdx < numQuads; quadIdx++) {
            float uSum = 0, vSum = 0;

            int quadState = -1;

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

                quadState &= vertexStates[stateIdx++];
            }

            final int trueNormal = QuadUtil.calculateNormal(vertices);
            final ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            final TextureAtlasSprite sprite = this.textureAtlas.celeritas$findFromUV(uSum * 0.25f, vSum * 0.25f);

            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSprites.add(sprite);
            }

            // If block used vanilla AO, then we can safely compute our own lighting data and ignore what vanilla
            // computed. We suppress vanilla emitting the brightness/color in MixinRenderBlocks.
            // Otherwise, we need to use the block's lightmap values as they are as the ISBRH may have
            // specified its own lightmaps for visual purposes (e.g. fullbright)
            if (useAoCalculation && (quadState & StateAwareTessellator.RENDERED_WITH_VANILLA_AO) != 0) {
                quadView.setup(trueNormal, blockX, blockY, blockZ);
                final ModelQuadFacing lightFace = quadView.getLightFace();
                final LightPipeline pipeline = blockAllowsSmoothLighting ? smoothLightPipeline : flatLightPipeline;
                final ModelQuadFacing cullFace = quadView.getCullFace();
                pipeline.calculate(quadView, worldX, worldY, worldZ, quadLightData, cullFace, lightFace, shouldApplyDiffuse, true);

                for (int vIdx = 0; vIdx < 4; vIdx++) {
                    final var vertex = vertices[vIdx];
                    vertex.trueNormal = trueNormal;
                    vertex.color = colorEncoder.writeColor(vertex.color, quadLightData.br[vIdx]);
                    vertex.light = quadLightData.lm[vIdx];
                }
            } else {
                if (!shouldApplyDiffuse) {
                    // Calculate the inverse of the expected diffuse constant and apply it to the color to fake
                    // diffuse not having been applied
                    final float inverseDiffuse = VanillaDiffuseProvider.INSTANCE.getInverseDiffuse(facing);
                    for (int vIdx = 0; vIdx < 4; vIdx++) {
                        vertices[vIdx].color = VanillaDiffuseProvider.multiplyColor(vertices[vIdx].color, inverseDiffuse);
                    }
                }
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

            final int faceBit = 1 << facing.ordinal();
            final Material correctMaterial;
            if ((facesAtBaseMaterial & faceBit) != 0) {
                // Face already has a non-demoted quad — skip selectMaterial entirely
                correctMaterial = material;
            } else {
                correctMaterial = selectMaterial(material, sprite, isShaderPackOverride);
                if (correctMaterial == material) {
                    facesAtBaseMaterial |= faceBit;
                }
            }
            final var builder = buffers.get(correctMaterial);

            if (correctMaterial != material && builder.getEncoder() instanceof IrisExtendedChunkVertexEncoder iris) {
                iris.setContext(blockRenderContext);
            }

            builder.getVertexBuffer(facing).push(vertices, correctMaterial);
        }
    }
}
