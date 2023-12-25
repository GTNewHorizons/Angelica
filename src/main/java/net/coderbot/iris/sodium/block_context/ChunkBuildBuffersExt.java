package net.coderbot.iris.sodium.block_context;

import net.minecraft.block.Block;

public interface ChunkBuildBuffersExt {
	void iris$setLocalPos(int localPosX, int localPosY, int localPosZ);

	void iris$setMaterialId(Block block, short renderType);

	void iris$resetBlockContext();
}
