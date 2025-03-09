package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import net.minecraft.util.LongHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LongHashMap.class)
public class MixinLongHashMap {
    /**
     * @author TheMasterCaver, embeddedt (mixin version)
     * @reason Use a better hash (from TMCW) that avoids collisions.
     */
    @Overwrite
    private static int getHashedKey(long par0) {
        return (int)par0 + (int)(par0 >>> 32) * 92821;
    }
}
