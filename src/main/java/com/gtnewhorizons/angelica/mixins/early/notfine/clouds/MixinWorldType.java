package com.gtnewhorizons.angelica.mixins.early.notfine.clouds;

import jss.notfine.core.Settings;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = WorldType.class)
public abstract class MixinWorldType {

    /**
     * @author jss2a98aj
     * @reason Control cloud height.
     */
    @Overwrite(remap = false)
    public float getCloudHeight() {
        return Settings.CLOUD_HEIGHT.getValue();
    }

}
