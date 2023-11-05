package me.jellysquid.mods.sodium.common.util;

import com.gtnewhorizons.angelica.compat.mojang.BlockPos;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraftforge.common.util.ForgeDirection;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Contains a number of cached arrays to avoid allocations since calling Enum#values() requires the backing array to
 * be cloned every time.
 */
public class DirectionUtil {
    public static final ForgeDirection[] ALL_DIRECTIONS = ForgeDirection.values();
    public static final int DIRECTION_COUNT = ALL_DIRECTIONS.length;

    // Provides the same order as enumerating ForgeDirection and checking the axis of each value
    public static final ForgeDirection[] HORIZONTAL_DIRECTIONS = new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST };

    private static final Long2ObjectMap<ForgeDirection> VECTOR_TO_DIRECTION = (Long2ObjectMap) Arrays.stream(ALL_DIRECTIONS).collect(Collectors.toMap((arg) -> {
        return (new BlockPos(arg.offsetX, arg.offsetY, arg.offsetZ)).asLong();
    }, (arg) -> {
        return arg;
    }, (arg, arg2) -> {
        throw new IllegalArgumentException("Duplicate keys");
    }, Long2ObjectOpenHashMap::new));

    @Nullable
    public static ForgeDirection fromVector(int x, int y, int z) {
        return (ForgeDirection)VECTOR_TO_DIRECTION.get(BlockPos.asLong(x, y, z));
    }

}
