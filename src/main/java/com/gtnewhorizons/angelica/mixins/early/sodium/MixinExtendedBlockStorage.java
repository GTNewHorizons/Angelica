package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.IExtendedBlockStorageExt;
import lombok.Getter;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtendedBlockStorage.class)
public abstract class MixinExtendedBlockStorage implements IExtendedBlockStorageExt {
    @Shadow public abstract NibbleArray getSkylightArray();
    @Shadow public abstract NibbleArray getBlocklightArray();
    @Shadow public abstract NibbleArray getMetadataArray();
    @Shadow public abstract byte[] getBlockLSBArray();

    @Getter @Shadow private int yBase;

    @Override
    public boolean hasSky() {
        return getSkylightArray() != null;
    }
}
