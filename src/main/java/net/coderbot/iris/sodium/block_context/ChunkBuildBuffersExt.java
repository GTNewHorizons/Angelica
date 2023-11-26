package net.coderbot.iris.sodium.block_context;

import com.gtnewhorizons.angelica.compat.mojang.BlockState;

public interface ChunkBuildBuffersExt {
	void iris$setLocalPos(int localPosX, int localPosY, int localPosZ);

	void iris$setMaterialId(BlockState state, short renderType);

	void iris$resetBlockContext();
}
