package com.gtnewhorizons.angelica.mixins.early.archaic.common.core;

import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(EntityLiving.class)
public interface AccessorEntityLiving {
    @Invoker
    public boolean invokeCanDespawn();
    @Invoker
    public void invokeDespawnEntity();
}
