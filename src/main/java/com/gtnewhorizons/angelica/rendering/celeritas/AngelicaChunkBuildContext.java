package com.gtnewhorizons.angelica.rendering.celeritas;

import com.gtnewhorizons.angelica.dynamiclights.DynamicLights;
import com.gtnewhorizons.angelica.dynamiclights.IDynamicLightSource;

import java.util.List;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.BlockRenderContext;
import com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexEncoder;
import com.gtnewhorizons.angelica.rendering.celeritas.world.WorldSlice;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.tileentity.TileEntity;
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

    private final TextureMap textureMap;
    private final ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
    @Getter
    private final WorldSlice worldSlice;
    private final BlockRenderContext blockRenderContext = new BlockRenderContext();

    /** Pre-filtered light sources for the current chunk being built. Null if dynamic lights disabled. */
    private List<IDynamicLightSource> chunkLightSources;
    private DynamicLights dynamicLightsInstance;

    public AngelicaChunkBuildContext(RenderPassConfiguration<?> renderPassConfiguration, WorldClient world) {
        super(renderPassConfiguration);
        this.textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        this.worldSlice = new WorldSlice(world);
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

    public BlockRenderContext getBlockRenderContext() {
        return this.blockRenderContext;
    }

    private Material selectMaterial(Material material, TextureAtlasSprite sprite) {
        if (sprite != null && sprite.getClass() == TextureAtlasSprite.class && !sprite.hasAnimationMetadata()) {
            final var transparencyLevel = ((SpriteExtension)sprite).celeritas$getTransparencyLevel();
            if (transparencyLevel == SpriteTransparencyLevel.OPAQUE && material == AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL) {
                // Downgrade to solid
                return AngelicaRenderPassConfiguration.SOLID_MATERIAL;
            } else if (material == AngelicaRenderPassConfiguration.TRANSLUCENT_MATERIAL && transparencyLevel != SpriteTransparencyLevel.TRANSLUCENT) {
                // Downgrade to cutout
                return AngelicaRenderPassConfiguration.CUTOUT_MIPPED_MATERIAL;
            }
        }
        return material;
    }

    @SuppressWarnings("unchecked")
    public void copyRawBuffer(int[] rawBuffer, int vertexCount, ChunkBuildBuffers buffers, Material material,
                              int originX, int originY, int originZ) {
        if (vertexCount == 0) {
            return;
        }

        final var animatedSprites = ((MinecraftBuiltRenderSectionData<TextureAtlasSprite, TileEntity>)buffers.getSectionContextBundle()).animatedSprites;

        if ((vertexCount & 0x3) != 0) {
            throw new IllegalStateException("Only quads are supported, got: " + vertexCount);
        }

        final boolean hasDynamicLights = chunkLightSources != null && !chunkLightSources.isEmpty();

        final var encoder = buffers.get(material).getEncoder();
        if (encoder instanceof IrisExtendedChunkVertexEncoder iris) {
            iris.setContext(blockRenderContext);
        }

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

                if (hasDynamicLights) {
                    final double dynamicLevel = dynamicLightsInstance.getDynamicLightLevelFromSources(
                        originX + vertex.x, originY + vertex.y, originZ + vertex.z, chunkLightSources);
                    if (dynamicLevel > 0) {
                        vertex.light = dynamicLightsInstance.getLightmapWithDynamicLight(dynamicLevel, vertex.light);
                    }
                }
            }

            final int trueNormal = QuadUtil.calculateNormal(vertices);
            for (int vIdx = 0; vIdx < 4; vIdx++) {
                vertices[vIdx].trueNormal = trueNormal;
            }

            final ModelQuadFacing facing = QuadUtil.findNormalFace(trueNormal);
            final TextureAtlasSprite sprite = ((TextureMapExtension) textureMap).celeritas$findFromUV(uSum * 0.25f, vSum * 0.25f);

            if (sprite != null && sprite.hasAnimationMetadata()) {
                animatedSprites.add(sprite);
            }

            final Material correctMaterial = selectMaterial(material, sprite);
            final var builder = buffers.get(correctMaterial);

            if (correctMaterial != material && builder.getEncoder() instanceof IrisExtendedChunkVertexEncoder iris) {
                iris.setContext(blockRenderContext);
            }

            builder.getVertexBuffer(facing).push(vertices, correctMaterial);
        }
    }
}
