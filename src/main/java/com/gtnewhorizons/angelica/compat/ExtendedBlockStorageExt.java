package com.gtnewhorizons.angelica.compat;

import com.falsepattern.chunk.api.DataRegistry;
import com.gtnewhorizons.neid.mixins.interfaces.IExtendedBlockStorageMixin;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

public class ExtendedBlockStorageExt extends ExtendedBlockStorage {
    public boolean hasSky;

    public ExtendedBlockStorageExt(int yBase, boolean hasSky) {
        super(yBase, hasSky);
        this.hasSky = hasSky;
    }

    public ExtendedBlockStorageExt(Chunk chunk, ExtendedBlockStorage storage) {
        super(storage.yBase, storage.getSkylightArray() != null);
        copyFrom(chunk, storage);
    }

    public void copyFrom(Chunk chunk, ExtendedBlockStorage storage) {
        this.yBase = storage.yBase;
        final boolean srcHasSky = storage.getSkylightArray() != null;
        this.hasSky = srcHasSky;

        if (ModStatus.isChunkAPILoaded) {
            // ChunkAPI BlocklightManager.cloneSubChunk repoints the source at our array
            final NibbleArray liveBlocklight = storage.getBlocklightArray();
            DataRegistry.cloneSubChunk(chunk, storage, this);
            storage.setBlocklightArray(liveBlocklight);
        } else {
            int arrayLen;
            if (ModStatus.isNEIDLoaded){
                final short[] block16BArray = ((IExtendedBlockStorageMixin)(Object)this).getBlock16BArray();
                System.arraycopy(((IExtendedBlockStorageMixin)(Object)storage).getBlock16BArray(), 0, block16BArray, 0, block16BArray.length);
                copyMSB(storage, block16BArray.length);
                arrayLen = block16BArray.length;
                if (ModStatus.isNEIDMetadataExtended) {
                    final short[] block16BMetaArray = ((IExtendedBlockStorageMixin)(Object)this).getBlock16BMetaArray();
                    System.arraycopy(((IExtendedBlockStorageMixin)(Object)storage).getBlock16BMetaArray(), 0, block16BMetaArray, 0, block16BMetaArray.length);
                }
            }
            else {
                final byte[] blockLSBArray = this.getBlockLSBArray();
                System.arraycopy(storage.getBlockLSBArray(), 0, blockLSBArray, 0, blockLSBArray.length);
                copyMSB(storage, blockLSBArray.length);
                arrayLen = blockLSBArray.length;
            }


            if (!ModStatus.isNEIDMetadataExtended) copyNibbleArray(storage.getMetadataArray(), this.getMetadataArray());
            copyNibbleArray(storage.getBlocklightArray(), this.getBlocklightArray());
            if (srcHasSky) {
                if(this.getSkylightArray() == null) {
                    this.setSkylightArray(new NibbleArray(arrayLen, 4));
                }
                copyNibbleArray(storage.getSkylightArray(), this.getSkylightArray());
            } else {
                this.setSkylightArray(null);
            }
        }
        this.blockRefCount = storage.blockRefCount;
    }

    private void copyMSB(ExtendedBlockStorage storage, int len) {
        final NibbleArray src = storage.getBlockMSBArray();
        if (src == null) {
            this.setBlockMSBArray(null);
            return;
        }
        if (this.getBlockMSBArray() == null) {
            this.setBlockMSBArray(new NibbleArray(len, 4));
        }
        copyNibbleArray(src, this.getBlockMSBArray());
    }


    private static void copyNibbleArray(NibbleArray srcArray, NibbleArray dstArray) {
        if (srcArray == null || dstArray == null) {
            throw new RuntimeException("NibbleArray is null src: " + (srcArray == null) + " dst: " + (dstArray == null));
        }
        final byte[] data = srcArray.data;
        System.arraycopy(data, 0, dstArray.data, 0, data.length);
    }
}
