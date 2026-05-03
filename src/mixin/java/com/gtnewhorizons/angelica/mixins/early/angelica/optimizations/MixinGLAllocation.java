package com.gtnewhorizons.angelica.mixins.early.angelica.optimizations;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.renderer.GLAllocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

/**
 * Optimizes GLAllocation display list tracking by replacing HashMap with fastutil Int2IntOpenHashMap.
 */
@Mixin(GLAllocation.class)
public class MixinGLAllocation {

    @Shadow @Final @Mutable
    private static Map mapDisplayLists;

    static {
        mapDisplayLists = new Int2IntOpenHashMap();
    }

    /**
     * @author mitchej123
     * @reason Use primitive put() method to avoid boxing overhead
     */
    @Overwrite
    public static synchronized int generateDisplayLists(int count) {
        final int id = GLStateManager.glGenLists(count);
        // Cast to Int2IntMap to use primitive put(int, int) instead of put(Integer, Integer)
        ((Int2IntMap) mapDisplayLists).put(id, count);
        return id;
    }

    /**
     * @author mitchej123
     * @reason Use primitive remove() method and add null-safety
     */
    @Overwrite
    public static synchronized void deleteDisplayLists(int id) {
        // Cast to Int2IntMap to use primitive remove(int) which returns 0 if key not found
        // This makes it null-safe even if called with unallocated ID (no NPE from unboxing null)
        final int count = ((Int2IntMap) mapDisplayLists).remove(id);
        if (count > 0) {
            GLStateManager.glDeleteLists(id, count);
        }
    }

    /**
     * @author mitchej123
     * @reason Use primitive iteration to avoid boxing
     */
    @Overwrite
    public static synchronized void deleteTexturesAndDisplayLists() {
        // Cast to Int2IntMap to use primitive iteration
        for (Int2IntMap.Entry entry : ((Int2IntMap) mapDisplayLists).int2IntEntrySet()) {
            GLStateManager.glDeleteLists(entry.getIntKey(), entry.getIntValue());
        }
        mapDisplayLists.clear();
    }
}
