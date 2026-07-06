package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.cc.client.particle;

import net.minecraft.client.particle.EntityAuraFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;

import com.prupe.mcpatcher.cc.ColorizeEntity;
import com.prupe.mcpatcher.cc.Colorizer;

import jss.notfine.util.EntityAuraFXExpansion;

@Mixin(EntityAuraFX.class)
public abstract class MixinEntityAuraFX extends EntityFX implements EntityAuraFXExpansion {

    protected MixinEntityAuraFX(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public EntityAuraFX colorize() {
        if (ColorizeEntity.computeMyceliumParticleColor()) {
            this.particleRed = Colorizer.setColor[0];
            this.particleGreen = Colorizer.setColor[1];
            this.particleBlue = Colorizer.setColor[2];
        }
        return (EntityAuraFX) (Object) this;
    }
}
