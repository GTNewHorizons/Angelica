package com.gtnewhorizons.angelica.compat;

import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedNibbleArray;
import com.gtnewhorizons.angelica.mixins.interfaces.IExtendedBlockStorageExt;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class ExtendedBlockStorageExt extends ExtendedBlockStorage {

    public ExtendedBlockStorageExt(int yBase, boolean hasSky) {
        super(yBase, hasSky);
    }

    public ExtendedBlockStorageExt(IExtendedBlockStorageExt storage) {
        super(storage.getYBase(), storage.hasSky());

        final byte[] blockLSBArray = storage.getBlockLSBArray();

        System.arraycopy(storage.getBlockLSBArray(), 0, blockLSBArray, 0, blockLSBArray.length);
        if(storage.getBlockMSBArray() != null) {
            this.setBlockMSBArray(new NibbleArray(blockLSBArray.length, 4));
            copyNibbleArray((ExtendedNibbleArray) storage.getBlockMSBArray(), (ExtendedNibbleArray) this.getBlockMSBArray());
        }
        copyNibbleArray((ExtendedNibbleArray) storage.getMetadataArray(), (ExtendedNibbleArray)this.getMetadataArray());
        copyNibbleArray((ExtendedNibbleArray) storage.getBlocklightArray(), (ExtendedNibbleArray)this.getBlocklightArray());
        if(storage.getSkylightArray() != null) {
            if(this.getSkylightArray() == null) {
                this.setSkylightArray(new NibbleArray(blockLSBArray.length, 4));
            }
            copyNibbleArray((ExtendedNibbleArray) storage.getSkylightArray(), (ExtendedNibbleArray) this.getSkylightArray());
        }
    }


    private static void copyNibbleArray(ExtendedNibbleArray srcArray, ExtendedNibbleArray dstArray) {
        if(srcArray == null || dstArray == null) {
            throw new RuntimeException("NibbleArray is null src: " + (srcArray==null) + " dst: " + (dstArray==null));
        }
        final byte[] data = srcArray.getData();
        System.arraycopy(data, 0, dstArray.getData(), 0, data.length);
    }
}
