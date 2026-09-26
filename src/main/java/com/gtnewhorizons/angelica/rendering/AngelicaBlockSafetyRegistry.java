package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.block.Block;

import java.util.concurrent.locks.StampedLock;

public class AngelicaBlockSafetyRegistry {
    private static final Int2IntOpenHashMap ISBRH_SAFETY_MAP = new Int2IntOpenHashMap();
    private static final StampedLock LOCK = new StampedLock();

    static {
        ISBRH_SAFETY_MAP.defaultReturnValue(-1);
    }

    public static boolean canBlockRenderOffThread(Block block) {
        final int renderType = block.getRenderType();

        long stamp = LOCK.tryOptimisticRead();
        int value;
        try {
            value = ISBRH_SAFETY_MAP.get(renderType);
        } catch (RuntimeException ignored) {
            value = -1;
        }
        if (LOCK.validate(stamp) && value != -1) {
            return value == 1;
        }

        stamp = LOCK.readLock();
        try {
            value = ISBRH_SAFETY_MAP.get(renderType);
        } finally {
            LOCK.unlockRead(stamp);
        }
        if (value != -1) {
            return value == 1;
        }

        return populateCanRenderOffThread(renderType);
    }

    @SuppressWarnings("deprecation")
    private static boolean populateCanRenderOffThread(int renderType) {
        final ISimpleBlockRenderingHandler isbrh = ((IRenderingRegistryExt) RenderingRegistry.instance())
            .getISBRH(renderType);
        final boolean canBeOffThread = isbrh != null
            && (isbrh.getClass().isAnnotationPresent(ThreadSafeISBRH.class) || isbrh instanceof ThreadSafeISBRHFactory);

        final long stamp = LOCK.writeLock();
        try {
            ISBRH_SAFETY_MAP.put(renderType, canBeOffThread ? 1 : 0);
        } finally {
            LOCK.unlock(stamp);
        }

        return canBeOffThread;
    }

}
