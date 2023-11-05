package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.embeddedt.archaicfix.config.ArchaicConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EntityLiving.class)
public abstract class MixinEntityLiving extends EntityLivingBase {
    private MixinEntityLiving(World p_i1594_1_) {
        super(p_i1594_1_);
    }

    @ModifyConstant(method = "despawnEntity", constant = @Constant(doubleValue = 16384.0D))
    private double lowerHardRange(double old) {
        if(!ArchaicConfig.fixMobSpawnsAtLowRenderDist)
            return old;
        if(worldObj.isRemote)
            return old;
        if(((WorldServer)worldObj).func_73046_m().getConfigurationManager().getViewDistance() < 10)
            return 96 * 96;
        return old;
    }
}
