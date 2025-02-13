package net.coderbot.iris.api;

import net.minecraft.block.Block;

import org.jetbrains.annotations.ApiStatus;

import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;

/**
 * Usage:
 * <pre>
 *     public class YourISBRH implements ISimpleBlockRenderingHandler {
 *         ...
 *
 *         &#064;Override
 *         public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks renderer) {
 *             if (renderer instanceof IIrisRenderBlocks iris) {
 *                 switch (iris.getCurrentSubpass()) {
 *                     case 0 -> iris.setShaderBlockId(yourblock1, 5);
 *                     case 1 -> iris.setShaderBlockId(yourblock2, 123);
 *                 }
 *             }
 *
 *             ...
 *         }
 *
 *         ...
 *     }
 * </pre>
 */
public interface IIrisRenderBlocks {

    @ApiStatus.Internal
    void setCurrentSubpass(int subpass);

    @ApiStatus.Internal
    void setBuffers(ChunkBuildBuffers buffers);

    /** Gets the current subpass. */
    int getCurrentSubpass();

    /** Tells the shader to pretend the given block is the actual block (so that you can control the shader's behaviour). */
    void setShaderMaterialId(Block block, int meta);
}
