package com.gtnewhorizons.angelica.rendering;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import net.minecraft.block.Block;

import java.util.concurrent.locks.StampedLock;

public class AngelicaBlockSafetyRegistry {
    private static final Reference2BooleanMap<Block> BLOCK_SAFETY_MAP = new Reference2BooleanOpenHashMap<>();
    private static final Reference2BooleanMap<Block> ISBRH_SAFETY_MAP = new Reference2BooleanOpenHashMap<>();
    private static final StampedLock LOCK = new StampedLock();

    /**
     * This method is threadsafe to read, and threadsafe to write, but NOT if both could happen at the same time.
     */
    public static boolean canBlockRenderOffThread(Block block, boolean checkISBRH, boolean shouldPopulate) {

        final Reference2BooleanMap<Block> map = checkISBRH ? ISBRH_SAFETY_MAP : BLOCK_SAFETY_MAP;

        if (shouldPopulate)
            return populateCanRenderOffThread(block, map);
        return map.getBoolean(block);
    }

    private static boolean populateCanRenderOffThread(Block block, Reference2BooleanMap<Block> map) {
        final boolean canBeOffThread;
        if(map == ISBRH_SAFETY_MAP) {
            @SuppressWarnings("deprecation")
            final ISimpleBlockRenderingHandler isbrh = ((IRenderingRegistryExt)RenderingRegistry.instance()).getISBRH(block.getRenderType());
            canBeOffThread = isbrh != null
                && (isbrh.getClass().isAnnotationPresent(ThreadSafeISBRH.class)
                || isbrh instanceof ThreadSafeISBRHFactory);
         } else {
            canBeOffThread = !(block.getClass().getName().startsWith("gregtech.")) && !(block.getClass().getName().startsWith("com.github.bartimaeusnek."));
        }

        final long stamp = LOCK.writeLock();

        try {
            map.put(block, canBeOffThread);
        } finally {
            LOCK.unlock(stamp);
        }

        return canBeOffThread;
    }

}
