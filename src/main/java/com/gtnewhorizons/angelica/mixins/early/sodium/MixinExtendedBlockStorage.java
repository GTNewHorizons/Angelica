package com.gtnewhorizons.angelica.mixins.early.sodium;

import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExtendedBlockStorage.class)
public interface MixinExtendedBlockStorage {

    @Accessor("yBase")
    int getYBase();
    @Accessor("blockRefCount")
    int getBlockRefCount();
    @Accessor("blockRefCount")
    void setBlockRefCount(int blockRefCount);
}
