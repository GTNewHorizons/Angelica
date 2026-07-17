package com.gtnewhorizons.angelica.mixins.interfaces;

import com.gtnewhorizons.angelica.utils.ConcurrentTileEntityMap;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;

public interface IChunkTileEntityMapHolder {
    ConcurrentTileEntityMap angelica$getConcurrentTEMap();
}
