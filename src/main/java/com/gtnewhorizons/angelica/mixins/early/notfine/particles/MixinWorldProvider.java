package com.gtnewhorizons.angelica.mixins.early.notfine.particles;

import jss.notfine.core.Settings;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = WorldProvider.class)
public class MixinWorldProvider {

    /**
     * @author jss2a98aj
     * @reason Toggle void particles.
     */
    @Overwrite
    public boolean getWorldHasVoidParticles() {
        return (boolean)Settings.PARTICLES_VOID.option.getStore() && terrainType.hasVoidParticles(hasNoSky);
    }

    @Shadow public WorldType terrainType;
    @Shadow public boolean hasNoSky;

}
