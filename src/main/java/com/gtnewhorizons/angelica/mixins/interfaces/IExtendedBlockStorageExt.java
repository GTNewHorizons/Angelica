package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.world.chunk.NibbleArray;

public interface IExtendedBlockStorageExt {
    byte[] getBlockLSBArray();
    NibbleArray getBlockMSBArray();
    NibbleArray getMetadataArray();
    NibbleArray getBlocklightArray();
    NibbleArray getSkylightArray();
    int getYBase();

    boolean hasSky();

}
