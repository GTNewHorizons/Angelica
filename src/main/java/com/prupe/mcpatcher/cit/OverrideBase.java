package com.prupe.mcpatcher.cit;

import java.io.File;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.item.ItemAPI;
import com.prupe.mcpatcher.mal.nbt.NBTRule;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.tile.TileLoader;

abstract class OverrideBase implements Comparable<OverrideBase> {

    static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_ITEM_TEXTURES, "CIT");

    private static final int MAX_DAMAGE = 65535;
    private static final int MAX_STACK_SIZE = 65535;

    final PropertiesFile properties;
    final ResourceLocation textureName;
    final Map<String, ResourceLocation> alternateTextures;
    final int weight;
    final Set<Item> items;
    final BitSet damagePercent;
    final BitSet damage;
    final int damageMask;
    final BitSet stackSize;
    final BitSet enchantmentIDs;
    final BitSet enchantmentLevels;
    private final List<NBTRule> nbtRules = new ArrayList<>();

    int lastEnchantmentLevel;

    static OverrideBase create(ResourceLocation filename) {
        if (new File(filename.getResourcePath()).getName()
            .equals("cit.properties")) {
            return null;
        }
        PropertiesFile properties = PropertiesFile.get(logger, filename);
        if (properties == null) {
            return null;
        }
        String type = properties.getString("type", "item")
            .toLowerCase();
        OverrideBase override;
        switch (type) {
            case "item" -> {
                if (!CITUtils.enableItems) {
                    return null;
                }
                override = new ItemOverride(properties);
            }
            case "enchantment", "overlay" -> {
                if (!CITUtils.enableEnchantments) {
                    return null;
                }
                override = new Enchantment(properties);
            }
            case "armor" -> {
                if (!CITUtils.enableArmor) {
                    return null;
                }
                override = new ArmorOverride(properties);
            }
            default -> {
                logger.error("%s: unknown type '%s'", filename, type);
                return null;
            }
        }
        return override.properties.valid() ? override : null;
    }

    OverrideBase(PropertiesFile properties) {
        this.properties = properties;

        alternateTextures = getAlternateTextures();

        String value = properties.getString("source", "");
        ResourceLocation resource = null;
        if (value.isEmpty()) {
            value = properties.getString("texture", "");
        }
        if (value.isEmpty()) {
            value = properties.getString("tile", "");
        }
        if (value.isEmpty()) {
            if (MCPatcherUtils.isNullOrEmpty(alternateTextures)) {
                resource = TileLoader.getDefaultAddress(properties.getResource());
                if (!TexturePackAPI.hasResource(resource)) {
                    resource = null;
                }
            }
        } else {
            resource = TileLoader.parseTileAddress(properties.getResource(), value);
            if (!TexturePackAPI.hasResource(resource)) {
                properties.error("source texture %s not found", value);
                resource = null;
            }
        }
        textureName = resource;

        weight = properties.getInt("weight", 0);

        value = properties.getString("items", "");
        if (value.isEmpty()) {
            value = properties.getString("matchItems", "");
        }
        if (value.isEmpty()) {
            items = null;
        } else {
            items = new HashSet<>();
            for (String s : value.split("\\s+")) {
                Item item = ItemAPI.parseItemName(s);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        value = properties.getString("damage", "");
        if (value.isEmpty()) {
            damage = null;
            damagePercent = null;
        } else if (value.contains("%")) {
            damage = null;
            damagePercent = parseBitSet(value.replace("%", ""), 0, 100);
        } else {
            damage = parseBitSet(value, 0, MAX_DAMAGE);
            damagePercent = null;
        }
        damageMask = properties.getInt("damageMask", MAX_DAMAGE);
        stackSize = parseBitSet(properties, "stackSize", 0, MAX_STACK_SIZE);
        enchantmentIDs = parseBitSet(properties, "enchantmentIDs", 0, CITUtils.MAX_ENCHANTMENTS - 1);
        enchantmentLevels = parseBitSet(properties, "enchantmentLevels", 0, CITUtils.MAX_ENCHANTMENTS - 1);

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(NBTRule.NBT_RULE_PREFIX)) {
                value = entry.getValue();
                NBTRule rule = NBTRule.create(name, value);
                if (rule == null) {
                    properties.error("invalid nbt rule: %s", value);
                } else {
                    nbtRules.add(rule);
                }
            }
        }
    }

    public int compareTo(OverrideBase o) {
        int result = o.weight - weight;
        if (result != 0) {
            return result;
        }
        return properties.getResource()
            .toString()
            .compareTo(
                o.properties.getResource()
                    .toString());
    }

    boolean match(ItemStack itemStack, int[] itemEnchantmentLevels, boolean hasEffect) {
        return matchDamage(itemStack) && matchDamagePercent(itemStack)
            && matchStackSize(itemStack)
            && matchEnchantment(itemEnchantmentLevels, hasEffect)
            && matchNBT(itemStack);
    }

    String preprocessAltTextureKey(String name) {
        return name;
    }

    private Map<String, ResourceLocation> getAlternateTextures() {
        Map<String, ResourceLocation> tmpMap = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            String name;
            if (key.startsWith("source.")) {
                name = key.substring(7);
            } else if (key.startsWith("texture.")) {
                name = key.substring(8);
            } else if (key.startsWith("tile.")) {
                name = key.substring(5);
            } else {
                continue;
            }
            name = preprocessAltTextureKey(name);
            if (MCPatcherUtils.isNullOrEmpty(name)) {
                continue;
            }
            ResourceLocation resource = TileLoader.parseTileAddress(properties.getResource(), value);
            if (resource != null) {
                tmpMap.put(name, resource);
            }
        }
        return tmpMap.isEmpty() ? null : tmpMap;
    }

    private boolean matchDamage(ItemStack itemStack) {
        return damage == null || damage.get(itemStack.getItemDamage() & damageMask);
    }

    private boolean matchDamagePercent(ItemStack itemStack) {
        if (damagePercent == null) {
            return true;
        }
        int maxDamage = itemStack.getMaxDamage();
        if (maxDamage == 0) {
            return false;
        }
        int percent = (100 * itemStack.getItemDamage()) / maxDamage;
        if (percent < 0) {
            percent = 0;
        } else if (percent > 100) {
            percent = 100;
        }
        return damagePercent.get(percent);
    }

    private boolean matchStackSize(ItemStack itemStack) {
        return stackSize == null || stackSize.get(itemStack.stackSize);
    }

    private boolean matchEnchantment(int[] itemEnchantmentLevels, boolean hasEffect) {
        if (enchantmentLevels == null && enchantmentIDs == null) {
            return true;
        } else if (itemEnchantmentLevels == null) {
            return (lastEnchantmentLevel = getEnchantmentLevelMatch(hasEffect)) >= 0;
        } else {
            return (lastEnchantmentLevel = getEnchantmentLevelMatch(itemEnchantmentLevels)) >= 0;
        }
    }

    private int getEnchantmentLevelMatch(boolean hasEffect) {
        if (hasEffect && enchantmentIDs == null && enchantmentLevels.get(1)) {
            return 1;
        } else {
            return -1;
        }
    }

    private int getEnchantmentLevelMatch(int[] itemEnchantmentLevels) {
        int matchLevel = -1;
        if (enchantmentIDs == null) {
            int sum = 0;
            for (int level : itemEnchantmentLevels) {
                sum += level;
            }
            if (enchantmentLevels.get(sum)) {
                return sum;
            }
        } else if (enchantmentLevels == null) {
            for (int id = enchantmentIDs.nextSetBit(0); id >= 0; id = enchantmentIDs.nextSetBit(id + 1)) {
                if (itemEnchantmentLevels[id] > 0) {
                    matchLevel = Math.max(matchLevel, itemEnchantmentLevels[id]);
                }
            }
        } else {
            for (int id = enchantmentIDs.nextSetBit(0); id >= 0; id = enchantmentIDs.nextSetBit(id + 1)) {
                if (enchantmentLevels.get(itemEnchantmentLevels[id])) {
                    matchLevel = Math.max(matchLevel, itemEnchantmentLevels[id]);
                }
            }
        }
        return matchLevel;
    }

    private boolean matchNBT(ItemStack itemStack) {
        for (NBTRule rule : nbtRules) {
            if (!rule.match(itemStack.getTagCompound())) {
                return false;
            }
        }
        return true;
    }

    abstract String getType();

    @Override
    public String toString() {
        return String.format("ItemOverride{%s, %s, %s}", getType(), properties, textureName);
    }

    private static BitSet parseBitSet(PropertiesFile properties, String tag, int min, int max) {
        String value = properties.getString(tag, "");
        return parseBitSet(value, min, max);
    }

    private static BitSet parseBitSet(String value, int min, int max) {
        if (value.isEmpty()) {
            return null;
        }
        BitSet bits = new BitSet();
        for (int i : MCPatcherUtils.parseIntegerList(value, min, max)) {
            bits.set(i);
        }
        return bits;
    }
}
