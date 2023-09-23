package org.embeddedt.archaicfix.mixins.common.core;

import net.minecraft.util.LongHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LongHashMap.class)
public abstract class MixinLongHashMap {
    /**
     * @author embeddedt
     * @reason Use a better hash (from TMCW) that avoids collisions.
     */
    @Overwrite
    private static int getHashedKey(long par0)
    {
        return (int)par0 + (int)(par0 >>> 32) * 92821;
    }
}
