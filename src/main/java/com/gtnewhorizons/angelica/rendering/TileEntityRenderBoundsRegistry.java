package com.gtnewhorizons.angelica.rendering;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tracking which TileEntity classes always return INFINITE_EXTENT_AABB or are known to change at runtime
 */
public final class TileEntityRenderBoundsRegistry {
    private static final byte UNKNOWN = 0;
    public static final byte STATIC = 1;
    public static final byte INFINITE = 2;
    public static final byte DYNAMIC = 3;

    private static final Reference2ByteOpenHashMap<Class<? extends TileEntity>> classRegistry = new Reference2ByteOpenHashMap<>();

    private static final Set<String> dynamicClassNames = ConcurrentHashMap.newKeySet();

    static {
        classRegistry.defaultReturnValue(UNKNOWN);
    }

    private TileEntityRenderBoundsRegistry() {}

    public static void registerDynamicClass(String className) {
        if (className != null && !className.isEmpty()) dynamicClassNames.add(className);
    }

    public static boolean isInfiniteExtentsBox(AxisAlignedBB box) {
        return box == null || Double.isInfinite(box.minX) || Double.isInfinite(box.minY) || Double.isInfinite(box.minZ) || Double.isInfinite(box.maxX) || Double.isInfinite(box.maxY) || Double.isInfinite(box.maxZ);
    }

    public static byte classify(TileEntity te) {
        final Class<? extends TileEntity> clazz = te.getClass();
        synchronized (classRegistry) {
            final byte cached = classRegistry.getByte(clazz);
            if (cached != UNKNOWN) return cached;
        }

        final byte result;
        if (dynamicClassNames.contains(clazz.getName())) {
            result = DYNAMIC;
        } else {
            boolean isInfinite;
            try {
                isInfinite = isInfiniteExtentsBox(te.getRenderBoundingBox());
            } catch (Throwable t) {
                isInfinite = true;
            }
            result = isInfinite ? INFINITE : STATIC;
        }

        synchronized (classRegistry) {
            classRegistry.put(clazz, result);
        }
        return result;
    }

    public static void clear() {
        synchronized (classRegistry) {
            classRegistry.clear();
        }
    }
}
