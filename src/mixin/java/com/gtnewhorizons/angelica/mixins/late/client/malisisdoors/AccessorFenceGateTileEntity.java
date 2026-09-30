package com.gtnewhorizons.angelica.mixins.late.client.malisisdoors;

import net.malisis.core.util.BlockState;
import net.malisis.doors.door.tileentity.FenceGateTileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FenceGateTileEntity.class, remap = false)
public interface AccessorFenceGateTileEntity {
    @Accessor("camoState")
    BlockState angelica$getCamoState();
}
