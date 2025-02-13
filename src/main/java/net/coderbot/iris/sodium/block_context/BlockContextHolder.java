package net.coderbot.iris.sodium.block_context;

import net.coderbot.iris.block_rendering.MaterialIdLookup;

import net.minecraft.block.Block;

public class BlockContextHolder {
	private final MaterialIdLookup lookup;

	public int localPosX;
	public int localPosY;
	public int localPosZ;

	public short materialId;
	public short renderType;

	public BlockContextHolder() {
		this.lookup = (a, b) -> (short) -1;
		this.materialId = -1;
		this.renderType = -1;
	}

	public BlockContextHolder(MaterialIdLookup lookup) {
		this.lookup = lookup;
		this.materialId = -1;
		this.renderType = -1;
	}

	public void setLocalPos(int localPosX, int localPosY, int localPosZ) {
		this.localPosX = localPosX;
		this.localPosY = localPosY;
		this.localPosZ = localPosZ;
	}

	public void set(Block block, int meta, short renderType) {
		this.materialId = this.lookup.get(block, meta);
		this.renderType = renderType;
	}

	public void reset() {
		this.materialId = -1;
		this.renderType = -1;
		this.localPosX = 0;
		this.localPosY = 0;
		this.localPosZ = 0;
	}
}
