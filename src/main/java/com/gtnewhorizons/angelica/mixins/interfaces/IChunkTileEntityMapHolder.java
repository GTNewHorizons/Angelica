package com.gtnewhorizons.angelica.mixins.interfaces;

import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

public interface IChunkTileEntityMapHolder {
    Map<ChunkPosition, TileEntity> angelica$getChunkTileEntityMap();
}
