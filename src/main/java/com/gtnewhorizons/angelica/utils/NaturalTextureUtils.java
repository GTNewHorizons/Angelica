package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.Set;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class NaturalTextureUtils {

    // Yes this is supposed to be volatile
    private volatile static Set<Block> cachedWhitelistedBlocks = null;

    public static int getTopRotation(int x, int y, int z) {
        long hash = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL + y * 0x165667B19E3779F9L;
        // splitmix64 finalizer
        hash ^= hash >>> 30;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 27;
        hash *= 0x94D049BB133111EBL;
        hash ^= hash >>> 31;
        return (int) (hash >>> 62);
    }

    // This method gets called from worker threads
    public static boolean isNaturalBlock(Block block) {
        if (cachedWhitelistedBlocks == null) {
            // If list is empty add back all the stuff from config
            initCache();
        }
        return cachedWhitelistedBlocks.contains(block);
    }

    // On reload clear the list
    public static void reloadCache() {
        cachedWhitelistedBlocks = null;
    }

    private static void initCache() {
        Set<Block> set = new ReferenceOpenHashSet<>();
        if (AngelicaConfig.naturalTextureBlocks != null) {
            for (String name : AngelicaConfig.naturalTextureBlocks) {
                Block b = (Block) Block.blockRegistry.getObject(name);
                if (b != null && b != Blocks.air) {
                    set.add(b);
                }
            }
        }
        cachedWhitelistedBlocks = set;
    }
}
