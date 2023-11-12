package com.gtnewhorizons.angelica.mixins.interfaces;

import net.minecraft.world.chunk.NibbleArray;

public interface ExtendedBlockStorageExt {
    byte[] getBlockLSBArray();
    NibbleArray getBlockMSBArray();
    NibbleArray getBlockMetadataArray();
    NibbleArray getBlocklightArray();
    NibbleArray getSkylightArray();

}
