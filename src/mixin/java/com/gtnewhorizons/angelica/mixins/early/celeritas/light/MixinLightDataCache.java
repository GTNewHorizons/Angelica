package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.ExtLightDataCache;
import com.gtnewhorizons.angelica.rendering.celeritas.light.LightDataCache;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = LightDataCache.class, remap = false)
public abstract class MixinLightDataCache implements ExtLightDataCache {

    @Shadow private IBlockAccess world;

    @Override
    public IBlockAccess angelica$getBlockAccess() {
        return this.world;
    }
}
