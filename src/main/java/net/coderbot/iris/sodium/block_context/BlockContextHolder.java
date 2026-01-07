package net.coderbot.iris.sodium.block_context;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import net.minecraft.block.Block;

/**
 * Holds block context for shader material ID lookups.
 * Based on Iris's approach but adapted for 1.7.10 metadata system.
 */
public class BlockContextHolder {
	private final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches;

	public int localPosX;
	public int localPosY;
	public int localPosZ;

	public short blockId;
	public short renderType;

	public BlockContextHolder() {
		this.blockMetaMatches = Reference2ObjectMaps.emptyMap();
		this.blockId = -1;
		this.renderType = -1;
	}

	public BlockContextHolder(Reference2ObjectMap<Block, Int2IntMap> idMap) {
		this.blockMetaMatches = idMap;
		this.blockId = -1;
		this.renderType = -1;
	}

	public void setLocalPos(int localPosX, int localPosY, int localPosZ) {
		this.localPosX = localPosX;
		this.localPosY = localPosY;
		this.localPosZ = localPosZ;
	}

	public void set(Block block, int meta, short renderType) {
		Int2IntMap metaMap = this.blockMetaMatches.get(block);
		int id = metaMap != null ? metaMap.get(meta) : -1;

		this.blockId = (short) id;
		this.renderType = renderType;
	}

	public void reset() {
		this.blockId = -1;
		this.renderType = -1;
		this.localPosX = 0;
		this.localPosY = 0;
		this.localPosZ = 0;
	}
}
