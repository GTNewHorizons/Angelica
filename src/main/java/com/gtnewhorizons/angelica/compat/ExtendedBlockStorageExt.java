package com.gtnewhorizons.angelica.compat;

import com.falsepattern.chunk.api.DataRegistry;
import com.gtnewhorizons.angelica.mixins.early.sodium.MixinExtendedBlockStorage;
import com.gtnewhorizons.angelica.mixins.interfaces.ExtendedNibbleArray;
import com.gtnewhorizons.neid.mixins.interfaces.IExtendedBlockStorageMixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import ru.fewizz.idextender.Hooks;

public class ExtendedBlockStorageExt extends ExtendedBlockStorage {
    public boolean hasSky;

    public ExtendedBlockStorageExt(int yBase, boolean hasSky) {
        super(yBase, hasSky);
        this.hasSky = hasSky;
    }

    public ExtendedBlockStorageExt(Chunk chunk, ExtendedBlockStorage storage) {
        super(((MixinExtendedBlockStorage) storage).getYBase(), storage.getSkylightArray() != null);

        if (ModStatus.isChunkAPILoaded) {
            if (storage.getSkylightArray() != null) {
                hasSky = true;
            }
            DataRegistry.cloneSubChunk(chunk, storage, this);
        } else {
            int arrayLen;
            if (ModStatus.isNEIDLoaded){
                final short[] block16BArray = ((IExtendedBlockStorageMixin)(Object)this).getBlock16BArray();
                System.arraycopy(((IExtendedBlockStorageMixin)(Object)storage).getBlock16BArray(), 0, block16BArray, 0, block16BArray.length);
                if(storage.getBlockMSBArray() != null) {
                    this.setBlockMSBArray(new NibbleArray(block16BArray.length, 4));
                    copyNibbleArray((ExtendedNibbleArray) storage.getBlockMSBArray(), (ExtendedNibbleArray) this.getBlockMSBArray());
                }
                arrayLen = block16BArray.length;
                if (ModStatus.isNEIDMetadataExtended) {
                    final short[] block16BMetaArray = ((IExtendedBlockStorageMixin)(Object)this).getBlock16BMetaArray();
                    System.arraycopy(((IExtendedBlockStorageMixin)(Object)storage).getBlock16BMetaArray(), 0, block16BMetaArray, 0, block16BMetaArray.length);
                }
            }
            else if (ModStatus.isOldNEIDLoaded){
                final short[] blockLSBArray = Hooks.get(this);
                System.arraycopy(Hooks.get(storage), 0, blockLSBArray, 0, blockLSBArray.length);
                // getBlockMSBArray is nuked in asm version
                arrayLen = blockLSBArray.length;
            }
            else {
                final byte[] blockLSBArray = this.getBlockLSBArray();
                System.arraycopy(storage.getBlockLSBArray(), 0, blockLSBArray, 0, blockLSBArray.length);
                if(storage.getBlockMSBArray() != null) {
                    this.setBlockMSBArray(new NibbleArray(blockLSBArray.length, 4));
                    copyNibbleArray((ExtendedNibbleArray) storage.getBlockMSBArray(), (ExtendedNibbleArray) this.getBlockMSBArray());
                }
                arrayLen = blockLSBArray.length;
            }


            if (!ModStatus.isNEIDMetadataExtended) copyNibbleArray((ExtendedNibbleArray) storage.getMetadataArray(), (ExtendedNibbleArray)this.getMetadataArray());
            copyNibbleArray((ExtendedNibbleArray) storage.getBlocklightArray(), (ExtendedNibbleArray)this.getBlocklightArray());
            if(storage.getSkylightArray() != null) {
                hasSky = true;
                if(this.getSkylightArray() == null) {
                    this.setSkylightArray(new NibbleArray(arrayLen, 4));
                }
                copyNibbleArray((ExtendedNibbleArray) storage.getSkylightArray(), (ExtendedNibbleArray) this.getSkylightArray());
            }
        }
        ((MixinExtendedBlockStorage) this).setBlockRefCount(((MixinExtendedBlockStorage) storage).getBlockRefCount());
    }


    private static void copyNibbleArray(ExtendedNibbleArray srcArray, ExtendedNibbleArray dstArray) {
        if (srcArray == null || dstArray == null) {
            throw new RuntimeException("NibbleArray is null src: " + (srcArray == null) + " dst: " + (dstArray == null));
        }
        final byte[] data = srcArray.getData();
        System.arraycopy(data, 0, dstArray.getData(), 0, data.length);
    }
}
