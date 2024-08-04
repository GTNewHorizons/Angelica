package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedBlockStorageAccessor;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ExtendedBlockStorage.class)
public abstract class MixinExtendedBlockStorage implements ExtendedBlockStorageAccessor {

    @Accessor("yBase")
    public abstract int getYBase();
    @Accessor("blockRefCount")
    public abstract int getBlockRefCount();
    @Accessor("blockRefCount")
    public abstract void setBlockRefCount(int blockRefCount);
}
