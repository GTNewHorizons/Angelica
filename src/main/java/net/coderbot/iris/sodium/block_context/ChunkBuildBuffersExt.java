package net.coderbot.iris.sodium.block_context;

import net.minecraft.block.Block;

public interface ChunkBuildBuffersExt {
	void iris$setLocalPos(int localPosX, int localPosY, int localPosZ);

    /** Sets the material id while keeping the renderType */
    void iris$setMaterialId(Block block, int meta);

	void iris$setMaterialId(Block block, int meta, short renderType);

	void iris$resetBlockContext();
}
