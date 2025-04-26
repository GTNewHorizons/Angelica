package com.seibel.distanthorizons.common.wrappers.block;

import com.gtnewhorizon.gtnhlib.hash.Fnv1a32;
import net.minecraft.block.Block;

import java.util.Objects;

public class FakeBlockState {

    public final Block block;
    public final int meta;
    private final int hashCode;

    public FakeBlockState(Block block, int meta) {
        this.block = block;
        this.meta = meta;
        int hash = Fnv1a32.initialState();
        // I'd use BlockID, but I believe it needs a hash lookup via the registry to obtain that.. which seems to defeat the purpose!
        hash = Fnv1a32.hashStep(hash, block.hashCode());
        hash = Fnv1a32.hashStep(hash, meta);
        this.hashCode = hash;
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
