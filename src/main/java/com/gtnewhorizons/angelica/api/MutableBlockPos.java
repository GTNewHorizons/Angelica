package com.gtnewhorizons.angelica.api;

public interface MutableBlockPos extends BlockPos {

    MutableBlockPos set(int x, int y, int z);

    MutableBlockPos set(long packedPos);
}
