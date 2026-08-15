package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.gtnewhorizons.angelica.mixins.interfaces.IAwaitingDescriptorTE;

@Mixin(TileEntity.class)
public abstract class MixinTileEntity_AwaitingDescriptor implements IAwaitingDescriptorTE {

    @Unique private boolean angelica$awaitingDescriptor;

    @Override
    public boolean angelica$isAwaitingDescriptor() {
        return this.angelica$awaitingDescriptor;
    }

    @Override
    public void angelica$setAwaitingDescriptor(boolean awaiting) {
        this.angelica$awaitingDescriptor = awaiting;
    }
}
