package com.seibel.distanthorizons.common.wrappers.block;

import net.minecraft.block.Block;

import java.util.Objects;

public class FakeBlockState {

    public final Block block;
    public final int meta;

    public FakeBlockState(Block block, int meta)
    {
        this.block = block;
        this.meta = meta;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FakeBlockState that)) return false;
        return meta == that.meta && Objects.equals(block, that.block);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, meta);
    }
}
