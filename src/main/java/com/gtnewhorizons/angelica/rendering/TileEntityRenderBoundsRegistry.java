package com.gtnewhorizons.angelica.rendering;

import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;
import net.minecraft.launchwrapper.Launch;
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

    public static final byte PASS0_ONLY = 1;
    public static final byte PASS_OVERRIDES = 2;

    private static final byte NOT_OVERRIDDEN = 1;
    private static final byte OVERRIDDEN = 2;

    private static final Reference2ByteOpenHashMap<Class<? extends TileEntity>> classRegistry = new Reference2ByteOpenHashMap<>();
    private static final Reference2ByteOpenHashMap<Class<? extends TileEntity>> passClassRegistry = new Reference2ByteOpenHashMap<>();
    private static final Reference2ByteOpenHashMap<Class<? extends TileEntity>> distanceOverrideRegistry = new Reference2ByteOpenHashMap<>();

    private static final Set<String> dynamicClassNames = ConcurrentHashMap.newKeySet();

    private static final String[] SHOULD_RENDER_IN_PASS_NAMES = { "shouldRenderInPass" };
    private static String[] distanceFromNames;

    static {
        classRegistry.defaultReturnValue(UNKNOWN);
        passClassRegistry.defaultReturnValue(UNKNOWN);
        distanceOverrideRegistry.defaultReturnValue(UNKNOWN);
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

    public static boolean overridesMethod(Class<?> clazz, Class<?> base, String[] candidateNames, Class<?>... params) {
        for (String name : candidateNames) {
            try {
                return clazz.getMethod(name, params).getDeclaringClass() != base;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                return true;
            }
        }
        return true;
    }

    private static String[] distanceFromNames() {
        String[] names = distanceFromNames;
        if (names == null) {
            String resolved;
            try {
                resolved = Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment")) ? "getDistanceFrom" : "func_145835_a";
            } catch (Throwable t) {
                resolved = "getDistanceFrom";
            }
            distanceFromNames = names = new String[] { resolved };
        }
        return names;
    }

    public static byte classifyPass(TileEntity te) {
        final Class<? extends TileEntity> clazz = te.getClass();
        synchronized (passClassRegistry) {
            final byte cached = passClassRegistry.getByte(clazz);
            if (cached != UNKNOWN) return cached;
        }

        final byte result = overridesMethod(clazz, TileEntity.class, SHOULD_RENDER_IN_PASS_NAMES, int.class)
            ? PASS_OVERRIDES : PASS0_ONLY;

        synchronized (passClassRegistry) {
            passClassRegistry.put(clazz, result);
        }
        return result;
    }

    public static boolean overridesGetDistanceFrom(Class<? extends TileEntity> clazz) {
        synchronized (distanceOverrideRegistry) {
            final byte cached = distanceOverrideRegistry.getByte(clazz);
            if (cached != UNKNOWN) return cached == OVERRIDDEN;
        }

        final boolean overrides = overridesMethod(clazz, TileEntity.class, distanceFromNames(), double.class, double.class, double.class);

        synchronized (distanceOverrideRegistry) {
            distanceOverrideRegistry.put(clazz, overrides ? OVERRIDDEN : NOT_OVERRIDDEN);
        }
        return overrides;
    }

    public static void clear() {
        synchronized (classRegistry) {
            classRegistry.clear();
        }
        synchronized (passClassRegistry) {
            passClassRegistry.clear();
        }
        synchronized (distanceOverrideRegistry) {
            distanceOverrideRegistry.clear();
        }
    }
}
