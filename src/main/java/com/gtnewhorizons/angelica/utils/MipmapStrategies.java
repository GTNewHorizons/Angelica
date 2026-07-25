package com.gtnewhorizons.angelica.utils;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlower;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockMushroom;
import net.minecraft.block.BlockRedstoneWire;

import java.util.LinkedList;
import java.util.Map;

/**
 * Fallback {@link MipmapStrategy} assignments for sprites that carry no metadata.
 */
public final class MipmapStrategies {

    private static final Map<Class<?>, MipmapStrategy> CLASS_RULES = new Object2ObjectOpenHashMap<>();
    private static final Map<String, MipmapStrategy> SPRITE_RULES = new Object2ObjectOpenHashMap<>();
    private static final LinkedList<MipmapStrategy> STRATEGY_STACK = new LinkedList<>();

    static {
        CLASS_RULES.put(BlockLeaves.class, MipmapStrategy.DARK_CUTOUT);
        CLASS_RULES.put(BlockFlower.class, MipmapStrategy.STRICT_CUTOUT);
        CLASS_RULES.put(BlockMushroom.class, MipmapStrategy.STRICT_CUTOUT);
        CLASS_RULES.put(BlockGlass.class, MipmapStrategy.MEAN);
        CLASS_RULES.put(BlockRedstoneWire.class, MipmapStrategy.MEAN);
    }

    private MipmapStrategies() {
    }

    public static void reset() {
        SPRITE_RULES.clear();
        STRATEGY_STACK.clear();
    }

    public static void beginBlock(Block block) {
        STRATEGY_STACK.push(resolve(block.getClass()));
    }

    public static void endBlock() {
        if (!STRATEGY_STACK.isEmpty()) {
            STRATEGY_STACK.pop();
        }
    }

    public static void recordSprite(int textureType, String iconName) {
        if (iconName == null) {
            return;
        }

        MipmapStrategy strategy = STRATEGY_STACK.peek();
        if (strategy == null && textureType == 0 && iconName.contains("leaves")) {
            strategy = MipmapStrategy.DARK_CUTOUT;
        }
        if (strategy == null) {
            return;
        }

        final MipmapStrategy existing = SPRITE_RULES.get(iconName);
        if (existing == null || strategy.conflictPrecedence() > existing.conflictPrecedence()) {
            SPRITE_RULES.put(iconName, strategy);
        }
    }

    public static MipmapStrategy inheritedFor(int textureType, String iconName) {
        if (iconName == null || textureType != 0) {
            return null;
        }
        return SPRITE_RULES.get(iconName);
    }

    private static MipmapStrategy resolve(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            final MipmapStrategy rule = CLASS_RULES.get(c);
            if (rule != null) {
                return rule;
            }
        }
        return null;
    }
}
