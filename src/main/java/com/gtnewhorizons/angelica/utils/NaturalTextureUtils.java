package com.gtnewhorizons.angelica.utils;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import java.util.HashSet;
import java.util.Set;

public class NaturalTextureUtils {

    private static Set<Block> cachedWhitelistedBlocks = null;

    // Get the top face orientation of the block with #math
    public static int getTopRotation(int x, int y, int z) {
        long hash = (x * 3129871L) ^ (z * 116586811L) ^ y;
        hash = hash * hash * 42317861L + hash * 11L;
        return (int) ((hash >> 16) & 3);
    }

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
        Set<Block> set = new HashSet<>();
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
