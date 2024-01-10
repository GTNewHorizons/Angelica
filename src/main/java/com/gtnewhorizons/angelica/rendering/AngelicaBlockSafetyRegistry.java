package com.gtnewhorizons.angelica.rendering;

import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.block.Block;

import java.util.concurrent.locks.StampedLock;

public class AngelicaBlockSafetyRegistry {
    private static final Reference2BooleanMap<Block> SAFETY_MAP = new Reference2BooleanOpenHashMap<>();
    private static final StampedLock LOCK = new StampedLock();

    public static boolean canBlockRenderOffThread(Block block) {
        final long stamp = LOCK.readLock();
        boolean isOffThread, shouldPopulate;
        try {
            isOffThread = SAFETY_MAP.getBoolean(block);
            if (isOffThread) {
                return true; // no need to check if 'false' was due to not being populated
            }

            shouldPopulate = !SAFETY_MAP.containsKey(block);
        } finally {
            LOCK.unlock(stamp);
        }

        if(shouldPopulate) {
            isOffThread = populateCanRenderOffThread(block);
        }

        return isOffThread;
    }

    private static boolean populateCanRenderOffThread(Block block) {
        final boolean canBeOffThread = !(block.getClass().getName().startsWith("gregtech."));

        final long stamp = LOCK.writeLock();

        try {
            SAFETY_MAP.put(block, canBeOffThread);
        } finally {
            LOCK.unlock(stamp);
        }

        return canBeOffThread;
    }

    public static boolean isSafeISBRH(Block block) {
        // TODO Ensure the Render Handler is an instanceof IThreadSafeISBRH; also optionally add a version check for compat
        return block.getClass().getName().startsWith("com.carpentersblocks");
    }
}
