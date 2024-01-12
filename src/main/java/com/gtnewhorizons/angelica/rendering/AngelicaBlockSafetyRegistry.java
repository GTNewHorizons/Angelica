package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.interfaces.IThreadSafeISBRH;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.versioning.ComparableVersion;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.block.Block;

import java.util.concurrent.locks.StampedLock;

public class AngelicaBlockSafetyRegistry {
    private static final Reference2BooleanMap<Block> BLOCK_SAFETY_MAP = new Reference2BooleanOpenHashMap<>();
    private static final Reference2BooleanMap<Block> ISBRH_SAFETY_MAP = new Reference2BooleanOpenHashMap<>();
    private static final StampedLock LOCK = new StampedLock();

    public static boolean canBlockRenderOffThread(Block block, boolean checkISBRH) {
        final long stamp = LOCK.readLock();
        final Reference2BooleanMap<Block> map = checkISBRH ? ISBRH_SAFETY_MAP : BLOCK_SAFETY_MAP;
        boolean isOffThread, shouldPopulate;
        try {
            isOffThread = map.getBoolean(block);
            if (isOffThread) {
                return true; // no need to check if 'false' was due to not being populated
            }

            shouldPopulate = !map.containsKey(block);
        } finally {
            LOCK.unlock(stamp);
        }

        if(shouldPopulate) {
            isOffThread = populateCanRenderOffThread(block, map);
        }

        return isOffThread;
    }

    private static boolean populateCanRenderOffThread(Block block, Reference2BooleanMap<Block> map) {
        @SuppressWarnings("deprecation")
        final boolean canBeOffThread = map == ISBRH_SAFETY_MAP ? ((IRenderingRegistryExt)RenderingRegistry.instance()).getISBRH(block.getRenderType()) instanceof IThreadSafeISBRH : !(block.getClass().getName().startsWith("gregtech."));

        final long stamp = LOCK.writeLock();

        try {
            map.put(block, canBeOffThread);
        } finally {
            LOCK.unlock(stamp);
        }

        return canBeOffThread;
    }

}
