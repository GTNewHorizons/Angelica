package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.angelica.mixins.interfaces.AwaitingDescriptorTE;

@Mixin(TileEntity.class)
public abstract class MixinTileEntity_AwaitingDescriptor implements AwaitingDescriptorTE {

    @Unique private boolean angelica$awaitingDescriptor;

    @Unique private int angelica$descriptorWaitTicks;

    @Override
    public boolean angelica$isAwaitingDescriptor() {
        return this.angelica$awaitingDescriptor;
    }

    @Override
    public void angelica$setAwaitingDescriptor(boolean awaiting) {
        this.angelica$awaitingDescriptor = awaiting;
    }

    @Override
    public int angelica$getDescriptorWaitTicks() {
        return this.angelica$descriptorWaitTicks;
    }

    @Override
    public void angelica$setDescriptorWaitTicks(int ticks) {
        this.angelica$descriptorWaitTicks = ticks;
    }
}
