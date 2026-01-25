package com.gtnewhorizons.angelica.rendering;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

/**
 * Registry for tracking which TileEntity classes always return INFINITE_EXTENT_AABB.
 */
public final class TileEntityRenderBoundsRegistry {
    private static final byte UNKNOWN = 0;
    private static final byte FINITE = 1;
    private static final byte INFINITE = 2;

    private static final Reference2ByteOpenHashMap<Class<? extends TileEntity>> classRegistry = new Reference2ByteOpenHashMap<>();

    static {
        classRegistry.defaultReturnValue(UNKNOWN);
    }

    private TileEntityRenderBoundsRegistry() {}

    public static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return box == null || Double.isInfinite(box.minX) || Double.isInfinite(box.minY) || Double.isInfinite(box.minZ) || Double.isInfinite(box.maxX) || Double.isInfinite(box.maxY) || Double.isInfinite(box.maxZ);
    }

    public static boolean isAlwaysInfiniteExtent(TileEntity te) {
        final Class<? extends TileEntity> clazz = te.getClass();
        byte result = classRegistry.getByte(clazz);

        if (result != UNKNOWN) {
            return result == INFINITE;
        }

        return probeAndCache(te, clazz);
    }

    private static boolean probeAndCache(TileEntity te, Class<? extends TileEntity> clazz) {
        boolean isInfinite = false;
        try {
            AxisAlignedBB aabb = te.getRenderBoundingBox();
            isInfinite = isInfiniteExtentsBox(aabb);
        } catch (Exception ignored) {}

        classRegistry.put(clazz, isInfinite ? INFINITE : FINITE);
        return isInfinite;
    }

    public static void clear() {
        classRegistry.clear();
    }
}
