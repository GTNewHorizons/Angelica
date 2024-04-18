package com.prupe.mcpatcher.cc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.material.MapColor;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionHelper;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;

import jss.notfine.util.MapColorExpansion;
import jss.notfine.util.PotionExpansion;

public class ColorizeItem {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_COLORS);

    private static final Map<Integer, String> entityNamesByID = new HashMap<>();
    private static final Map<Integer, Integer> spawnerEggShellColors = new HashMap<>(); // egg.shell.*
    private static final Map<Integer, Integer> spawnerEggSpotColors = new HashMap<>(); // egg.spots.*

    private static int waterBottleColor; // potion.water
    private static final List<Potion> potions = new ArrayList<>(); // potion.*

    private static boolean potionsInitialized;

    private static final String[] MAP_MATERIALS = new String[] { "air", "grass", "sand", "cloth", "tnt", "ice", "iron",
        "foliage", "snow", "clay", "dirt", "stone", "water", "wood", "quartz", "adobe", "magenta", "lightBlue",
        "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black", "gold",
        "diamond", "lapis", "emerald", "obsidian", "netherrack", };

    static {
        try {
            reset();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    static void reset() {
        spawnerEggShellColors.clear();
        spawnerEggSpotColors.clear();

        // 1.5+btw: Calling PotionHelper on startup runs the static initializer which crashes because Potion class
        // hasn't finished initializing yet.
        if (potionsInitialized) {
            if (PotionHelper.field_77925_n != null) {
                PotionHelper.field_77925_n.clear();
            }
        }
        potionsInitialized = true;

        waterBottleColor = 0x385dc6;
        for (Potion potion : potions) {
            potion.liquidColor = ((PotionExpansion) potion).getOrigColor();
        }

        for (MapColor mapColor : MapColor.mapColorArray) {
            if (mapColor != null) {
                mapColor.colorValue = ((MapColorExpansion) mapColor).getOriginalColorValue();
            }
        }
    }

    static void reloadPotionColors(PropertiesFile properties) {
        for (Potion potion : potions) {
            Colorizer.loadIntColor(potion.getName(), potion);
        }
        int[] temp = new int[] { waterBottleColor };
        Colorizer.loadIntColor("potion.water", temp, 0);
        waterBottleColor = temp[0];
    }

    static void reloadMapColors(PropertiesFile properties) {
        for (int i = 0; i < MapColor.mapColorArray.length; i++) {
            if (MapColor.mapColorArray[i] != null) {
                int[] rgb = new int[] { ((MapColorExpansion) MapColor.mapColorArray[i]).getOriginalColorValue() };
                Colorizer.loadIntColor("map." + Colorizer.getStringKey(MAP_MATERIALS, i), rgb, 0);
                MapColor.mapColorArray[i].colorValue = rgb[0];
            }
        }
    }

    public static void setupSpawnerEgg(String entityName, int entityID, int defaultShellColor, int defaultSpotColor) {
        logger.config("egg.shell.%s=%06x", entityName, defaultShellColor);
        logger.config("egg.spots.%s=%06x", entityName, defaultSpotColor);
        entityNamesByID.put(entityID, entityName);
    }

    public static void setupPotion(Potion potion) {
        ((PotionExpansion) potion).setOrigColor(potion.getLiquidColor());
        potions.add(potion);
    }

    public static int colorizeSpawnerEgg(int defaultColor, int entityID, int spots) {
        if (!Colorizer.useEggColors) {
            return defaultColor;
        }
        Integer value = null;
        Map<Integer, Integer> eggMap = (spots == 0 ? spawnerEggShellColors : spawnerEggSpotColors);
        if (eggMap.containsKey(entityID)) {
            value = eggMap.get(entityID);
        } else if (entityNamesByID.containsKey(entityID)) {
            String name = entityNamesByID.get(entityID);
            if (name != null) {
                int[] tmp = new int[] { defaultColor };
                Colorizer.loadIntColor((spots == 0 ? "egg.shell." : "egg.spots.") + name, tmp, 0);
                eggMap.put(entityID, tmp[0]);
                value = tmp[0];
            }
        }
        return value == null ? defaultColor : value;
    }

    public static int getWaterBottleColor() {
        return waterBottleColor;
    }
}
