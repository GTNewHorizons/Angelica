package com.gtnewhorizons.angelica.rendering.celeritas.iris;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;
import static com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType.STRIDE;
import static com.gtnewhorizons.angelica.rendering.celeritas.iris.IrisExtendedChunkVertexType.encodeMidTexture;

import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProvider;
import com.gtnewhorizons.angelica.rendering.celeritas.api.IrisShaderProviderHolder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import lombok.Setter;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.coderbot.iris.vertices.NormalHelper;
import net.coderbot.iris.vertices.NormI8;
import net.minecraft.block.Block;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;

/**
 * Writes Iris-extended vertices. Delegates base 28 bytes to VANILLA_LIKE encoder.
 */
public class IrisExtendedChunkVertexEncoder implements ContextAwareChunkVertexEncoder {
    // Offsets derived from format definition
    private static final int MID_TEX_OFFSET = IrisExtendedChunkVertexType.VERTEX_FORMAT.getAttribute("mc_midTexCoord").getPointer();
    private static final int TANGENT_OFFSET = IrisExtendedChunkVertexType.VERTEX_FORMAT.getAttribute("at_tangent").getPointer();
    private static final int NORMAL_OFFSET = IrisExtendedChunkVertexType.VERTEX_FORMAT.getAttribute("iris_Normal").getPointer();
    private static final int MC_ENTITY_OFFSET = IrisExtendedChunkVertexType.VERTEX_FORMAT.getAttribute("mc_Entity").getPointer();
    private static final int MID_BLOCK_OFFSET = IrisExtendedChunkVertexType.VERTEX_FORMAT.getAttribute("at_midBlock").getPointer();

    private final ChunkVertexEncoder baseEncoder = IrisExtendedChunkVertexType.BASE_TYPE.createEncoder();
    private final CeleritasQuadView quad = new CeleritasQuadView();
    private final Vector3f normal = new Vector3f();
    private final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;

    public IrisExtendedChunkVertexEncoder() {
        this.blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
    }

    // Per-quad accumulators
    private int vertexCount;
    private float uSum;
    private float vSum;

    @Setter
    private BlockRenderContext context;

    @Override
    public void prepareToRenderBlock(BlockRenderContext ctx, Block block, int metadata, short renderType, byte lightValue) {
        this.context = ctx;
        Int2IntMap metaMap = blockMetaMatches != null ? blockMetaMatches.get(block) : null;
        ctx.blockId = (short) (metaMap != null ? metaMap.get(metadata) : -1);
        ctx.renderType = renderType;
        ctx.lightValue = lightValue;
    }

    @Override
    public void prepareToRenderFluid(BlockRenderContext ctx, Block block, byte lightValue) {
        this.context = ctx;
        Int2IntMap metaMap = blockMetaMatches != null ? blockMetaMatches.get(block) : null;
        ctx.blockId = (short) (metaMap != null ? metaMap.get(0) : -1);
        ctx.renderType = ExtendedDataHelper.FLUID_RENDER_TYPE;
        ctx.lightValue = lightValue;
    }

    @Override
    public void finishRenderingBlock() {
        if (context != null) {
            context.reset();
        }
    }

    @Override
    public long write(long ptr, Material material, Vertex vertex, int sectionIndex) {
        uSum += vertex.u;
        vSum += vertex.v;
        vertexCount++;

        final BlockRenderContext ctx = context;

        baseEncoder.write(ptr, material, vertex, sectionIndex);

        // Per-vertex: renderType, midBlock, lightValue
        memPutShort(ptr + MC_ENTITY_OFFSET + 2, ctx.renderType);
        final int midBlock = ExtendedDataHelper.computeMidBlock(vertex.x, vertex.y, vertex.z, ctx.localPosX, ctx.localPosY, ctx.localPosZ);
        memPutInt(ptr + MID_BLOCK_OFFSET, midBlock);
        memPutByte(ptr + MID_BLOCK_OFFSET + 3, ctx.lightValue);

        // Per-quad: midTexCoord, blockId, normal, tangent
        if (vertexCount == 4) {
            vertexCount = 0;

            final float midU = uSum * 0.25f;
            final float midV = vSum * 0.25f;
            final int midUV = encodeMidTexture(midU, midV);

            memPutInt(ptr + MID_TEX_OFFSET, midUV);
            memPutInt(ptr + MID_TEX_OFFSET - STRIDE, midUV);
            memPutInt(ptr + MID_TEX_OFFSET - STRIDE * 2, midUV);
            memPutInt(ptr + MID_TEX_OFFSET - STRIDE * 3, midUV);

            memPutShort(ptr + MC_ENTITY_OFFSET, ctx.blockId);
            memPutShort(ptr + MC_ENTITY_OFFSET - STRIDE, ctx.blockId);
            memPutShort(ptr + MC_ENTITY_OFFSET - STRIDE * 2, ctx.blockId);
            memPutShort(ptr + MC_ENTITY_OFFSET - STRIDE * 3, ctx.blockId);

            quad.setup(ptr, STRIDE);
            NormalHelper.computeFaceNormal(normal, quad);
            final int packedNormal = NormI8.pack(normal);

            memPutInt(ptr + NORMAL_OFFSET, packedNormal);
            memPutInt(ptr + NORMAL_OFFSET - STRIDE, packedNormal);
            memPutInt(ptr + NORMAL_OFFSET - STRIDE * 2, packedNormal);
            memPutInt(ptr + NORMAL_OFFSET - STRIDE * 3, packedNormal);

            final int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);

            memPutInt(ptr + TANGENT_OFFSET, tangent);
            memPutInt(ptr + TANGENT_OFFSET - STRIDE, tangent);
            memPutInt(ptr + TANGENT_OFFSET - STRIDE * 2, tangent);
            memPutInt(ptr + TANGENT_OFFSET - STRIDE * 3, tangent);

            uSum = 0;
            vSum = 0;
        }

        return ptr + STRIDE;
    }
}
