package com.gtnewhorizons.angelica.mixins.interfaces;

public interface ExtendedBlockStorageAccessor {
    int getYBase();
    int getBlockRefCount();
    void setBlockRefCount(int blockRefCount);
}
