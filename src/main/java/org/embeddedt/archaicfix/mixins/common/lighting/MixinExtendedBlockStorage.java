package org.embeddedt.archaicfix.mixins.common.lighting;

import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExtendedBlockStorage.class)
public class MixinExtendedBlockStorage {
    @Shadow
    private int blockRefCount;

    @Shadow private NibbleArray skylightArray;
    @Shadow private NibbleArray blocklightArray;
    private int lightRefCount = -1;

    /**
     * @author Angeline
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setExtSkylightValue(int x, int y, int z, int value) {
        this.skylightArray.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setExtBlocklightValue(int x, int y, int z, int value) {
        this.blocklightArray.set(x, y, z, value);
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setBlocklightArray(NibbleArray array) {
        this.blocklightArray = array;
        this.lightRefCount = -1;
    }

    /**
     * @author Angeline
     * @reason Reset lightRefCount on call
     */
    @Overwrite
    public void setSkylightArray(NibbleArray array) {
        this.skylightArray = array;
        this.lightRefCount = -1;
    }


    /**
     * @author Angeline
     * @reason Send light data to clients when lighting is non-trivial
     */
    @Overwrite
    public boolean isEmpty() {
        if (this.blockRefCount != 0) {
            return false;
        }

        // -1 indicates the lightRefCount needs to be re-calculated
        if (this.lightRefCount == -1) {
            if (this.checkLightArrayEqual(this.skylightArray, (byte) 0xFF)
                    && this.checkLightArrayEqual(this.blocklightArray, (byte) 0x00)) {
                this.lightRefCount = 0; // Lighting is trivial, don't send to clients
            } else {
                this.lightRefCount = 1; // Lighting is not trivial, send to clients
            }
        }

        return this.lightRefCount == 0;
    }

    private boolean checkLightArrayEqual(NibbleArray storage, byte val) {
        if (storage == null) {
            return true;
        }

        byte[] arr = storage.data;

        for (byte b : arr) {
            if (b != val) {
                return false;
            }
        }

        return true;
    }
}
