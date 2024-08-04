package com.gtnewhorizons.angelica.mixins.interfaces;

import org.spongepowered.asm.mixin.gen.Accessor;

public interface ExtendedBlockStorageAccessor {
    int getYBase();
    int getBlockRefCount();
    void setBlockRefCount(int blockRefCount);
}
