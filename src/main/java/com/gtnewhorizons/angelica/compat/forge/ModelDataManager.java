package com.gtnewhorizons.angelica.compat.forge;

import com.google.common.base.Preconditions;
import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import com.gtnewhorizons.angelica.compat.mojang.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ModelDataManager {
    private static final Map<ChunkPos, Map<BlockPos, IModelData>> modelDataCache = new ConcurrentHashMap<>();

    public static @Nullable IModelData getModelData(World world, BlockPos pos)
    {
        return getModelData(world, new ChunkPos(pos)).get(pos);
    }

    public static Map<BlockPos, IModelData> getModelData(World world, ChunkPos pos)
    {
        Preconditions.checkArgument(!world.isRemote, "Cannot request model data for server world");
//        refreshModelData(world, pos);
        return modelDataCache.getOrDefault(pos, Collections.emptyMap());
    }
}
