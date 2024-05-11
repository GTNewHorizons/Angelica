package com.gtnewhorizons.angelica.mixins.early.angelica;

import cpw.mods.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = FMLClientHandler.class, remap = false)
public class MixinFMLClientHandler {
    /**
     * @author mitchej123
     * @reason Remove more traces of Optifine
     */
    @Overwrite
    public void detectOptifine() {
        // Do nothing
    }

    /**
     * @author mitchej123
     * @reason Remove more traces of Optifine
     */
    @Overwrite
    public boolean hasOptifine() {
        return false;
    }

}
