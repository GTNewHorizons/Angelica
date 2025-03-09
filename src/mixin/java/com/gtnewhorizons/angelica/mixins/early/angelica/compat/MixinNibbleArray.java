package com.gtnewhorizons.angelica.mixins.early.angelica.compat;

import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedNibbleArray;
import net.minecraft.world.chunk.NibbleArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NibbleArray.class)
public abstract class MixinNibbleArray implements ExtendedNibbleArray  {
    @Shadow public byte[] data;
    @Shadow private int depthBits;
    @Shadow private int depthBitsPlusFour;

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public int getDepthBits() {
        return depthBits;
    }

    @Override
    public int getDepthBitsPlusFour() {
        return depthBitsPlusFour;
    }
}
