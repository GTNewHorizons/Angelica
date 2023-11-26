package net.coderbot.iris.sodium.block_context;

import com.gtnewhorizons.angelica.compat.mojang.BlockState;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;

public class BlockContextHolder {
	private final Object2IntMap<BlockState> blockStateIds;

	public int localPosX;
	public int localPosY;
	public int localPosZ;

	public short blockId;
	public short renderType;

	public BlockContextHolder() {
		this.blockStateIds = Object2IntMaps.emptyMap();
		this.blockId = -1;
		this.renderType = -1;
	}

	public BlockContextHolder(Object2IntMap<BlockState> idMap) {
		this.blockStateIds = idMap;
		this.blockId = -1;
		this.renderType = -1;
	}

	public void setLocalPos(int localPosX, int localPosY, int localPosZ) {
		this.localPosX = localPosX;
		this.localPosY = localPosY;
		this.localPosZ = localPosZ;
	}

	public void set(BlockState state, short renderType) {
		this.blockId = (short) this.blockStateIds.getOrDefault(state, -1);
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
