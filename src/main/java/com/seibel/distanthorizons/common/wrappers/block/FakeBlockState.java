package com.seibel.distanthorizons.common.wrappers.block;

import net.minecraft.block.Block;

import java.util.Objects;

public class FakeBlockState {

    public final Block block;
    public final int meta;
    private final int hashCode;

    public FakeBlockState(Block block, int meta) {
        this(block, meta, Block.getIdFromBlock(block));
    }

    public FakeBlockState(Block block, int meta, int blockID) {
        this.block = block;
        this.meta = meta;
        this.hashCode = calculateHashCode(blockID, meta);
    }

    public static int calculateHashCode(int blockId, int meta) {
        return (blockId << 16) + meta;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FakeBlockState that)) return false;
        return meta == that.meta && Objects.equals(block, that.block);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
