package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedBlockStorageExt;
import lombok.Getter;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Getter
@Mixin(ExtendedBlockStorage.class)
public abstract class MixinExtendedBlockStorage implements ExtendedBlockStorageExt {
    @Shadow private byte[] blockLSBArray;
    @Shadow private NibbleArray blockMSBArray;
    @Shadow private NibbleArray blockMetadataArray;
    @Shadow private NibbleArray blocklightArray;
    @Shadow private NibbleArray skylightArray;
}
