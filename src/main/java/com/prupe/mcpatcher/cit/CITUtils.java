package com.prupe.mcpatcher.cit;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.item.ItemAPI;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;
import com.prupe.mcpatcher.mal.resource.ResourceList;
import com.prupe.mcpatcher.mal.resource.TexturePackAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;
import com.prupe.mcpatcher.mal.tile.TileLoader;

import mist475.mcpatcherforge.config.MCPatcherForgeConfig;
import com.gtnewhorizons.angelica.mixins.interfaces.NBTTagListExpansion;

public class CITUtils {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_ITEM_TEXTURES, "CIT");

    static final String CIT_PROPERTIES = "cit.properties";
    private static final ResourceLocation CIT_PROPERTIES1 = TexturePackAPI.newMCPatcherResourceLocation(CIT_PROPERTIES);
    private static final ResourceLocation CIT_PROPERTIES2 = TexturePackAPI
        .newMCPatcherResourceLocation("cit/" + CIT_PROPERTIES);
    static final ResourceLocation FIXED_ARMOR_RESOURCE = new ResourceLocation("textures/models/armor/iron_layer_1.png");

    static final int MAX_ENCHANTMENTS = 256;

    private static Item itemEnchantedBook;
    static Item itemCompass;
    static Item itemClock;

    static final boolean enableItems = MCPatcherForgeConfig.instance().citItems;
    static final boolean enableEnchantments = MCPatcherForgeConfig.instance().citEnchantments;
    static final boolean enableArmor = MCPatcherForgeConfig.instance().citArmor;

    private static TileLoader tileLoader;
    private static final Map<Item, List<ItemOverride>> items = new IdentityHashMap<>();
    private static final Map<Item, List<Enchantment>> enchantments = new IdentityHashMap<>();
    private static final List<Enchantment> allItemEnchantments = new ArrayList<>();
    private static final Map<Item, List<ArmorOverride>> armors = new IdentityHashMap<>();

    static boolean useGlint;

    private static EnchantmentList armorMatches;
    private static int armorMatchIndex;

    private static ItemStack lastItemStack;
    private static int lastRenderPass;
    static IIcon lastOrigIcon;
    private static IIcon lastIcon;

    public static void init() {
        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.CUSTOM_ITEM_TEXTURES, 3) {

            @Override
            public void beforeChange() {
                itemEnchantedBook = ItemAPI.getFixedItem("minecraft:enchanted_book");
                itemCompass = ItemAPI.getFixedItem("minecraft:compass");
                itemClock = ItemAPI.getFixedItem("minecraft:clock");

                tileLoader = new TileLoader("textures/items", logger);
                items.clear();
                enchantments.clear();
                allItemEnchantments.clear();
                armors.clear();
                lastOrigIcon = null;
                lastIcon = null;

                BufferedImage image = TexturePackAPI.getImage(FIXED_ARMOR_RESOURCE);
                if (image == null) {
                    Enchantment.baseArmorWidth = 64.0f;
                    Enchantment.baseArmorHeight = 32.0f;
                } else {
                    Enchantment.baseArmorWidth = image.getWidth();
                    Enchantment.baseArmorHeight = image.getHeight();
                }

                PropertiesFile properties = PropertiesFile.get(logger, CIT_PROPERTIES1);
                if (properties == null) {
                    properties = PropertiesFile.getNonNull(logger, CIT_PROPERTIES2);
                }
                useGlint = properties.getBoolean("useGlint", true);
                EnchantmentList.setProperties(properties);

                if (enableItems || enableEnchantments || enableArmor) {
                    for (ResourceLocation resource : ResourceList.getInstance()
                        .listResources(TexturePackAPI.MCPATCHER_SUBDIR + "cit", ".properties", true)) {
                        registerOverride(OverrideBase.create(resource));
                    }
                    if (enableItems) {
                        PotionReplacer replacer = new PotionReplacer();
                        for (ItemOverride override : replacer.overrides) {
                            registerOverride(override);
                        }
                    }
                }
            }

            @Override
            public void afterChange() {
                for (List<ItemOverride> list : items.values()) {
                    for (ItemOverride override : list) {
                        override.registerIcon(tileLoader);
                    }
                    Collections.sort(list);
                }
                for (List<Enchantment> list : enchantments.values()) {
                    list.addAll(allItemEnchantments);
                    Collections.sort(list);
                }
                Collections.sort(allItemEnchantments);
                for (List<ArmorOverride> list : armors.values()) {
                    Collections.sort(list);
                }
            }

            @SuppressWarnings("unchecked")
            private void registerOverride(OverrideBase override) {
                if (override != null && override.properties.valid()) {
                    Map<?, ?> map;
                    if (override instanceof ItemOverride) {
                        ((ItemOverride) override).preload(tileLoader);
                        map = items;
                    } else if (override instanceof Enchantment) {
                        map = enchantments;
                    } else if (override instanceof ArmorOverride) {
                        map = armors;
                    } else {
                        logger.severe(
                            "unknown ItemOverride type %d",
                            override.getClass()
                                .getName());
                        return;
                    }
                    if (override.items == null) {
                        if (override instanceof Enchantment) {
                            logger.fine("registered %s to all items", override);
                            allItemEnchantments.add((Enchantment) override);
                        }
                    } else {
                        int i = 0;
                        for (Item item : override.items) {
                            registerOverride((Map<Item, List<OverrideBase>>) map, item, override);
                            if (i < 10) {
                                logger.fine("registered %s to item %s", override, ItemAPI.getItemName(item));
                            } else if (i == 10) {
                                logger.fine("... %d total", override.items.size());
                            }
                            i++;
                        }
                    }
                }
            }

            private void registerOverride(Map<Item, List<OverrideBase>> map, Item item, OverrideBase override) {
                List<OverrideBase> list = map.computeIfAbsent(item, k -> new ArrayList<>());
                list.add(override);
            }
        });
    }

    public static IIcon getIcon(IIcon icon, ItemStack itemStack, int renderPass) {
        if (icon == lastIcon && itemStack == lastItemStack && renderPass == lastRenderPass) {
            return icon;
        }
        lastOrigIcon = lastIcon = icon;
        lastItemStack = itemStack;
        lastRenderPass = renderPass;
        if (enableItems) {
            ItemOverride override = findItemOverride(itemStack);
            if (override != null) {
                IIcon newIcon = override.getReplacementIcon(icon);
                if (newIcon != null) {
                    lastIcon = newIcon;
                }
            }
        }
        return lastIcon;
    }

    public static IIcon getEntityIcon(IIcon icon, Entity entity) {
        if (entity instanceof EntityPotion potion) {
            return getIcon(icon, potion.potionDamage, 1);
        }
        return icon;
    }

    public static ResourceLocation getArmorTexture(ResourceLocation texture, EntityLivingBase entity,
        ItemStack itemStack) {
        if (enableArmor) {
            ArmorOverride override = findArmorOverride(itemStack);
            if (override != null) {
                ResourceLocation newTexture = override.getReplacementTexture(texture);
                if (newTexture != null) {
                    return newTexture;
                }
            }
        }
        return texture;
    }

    private static <T extends OverrideBase> T findMatch(Map<Item, List<T>> overrides, ItemStack itemStack) {
        Item item = itemStack.getItem();
        List<T> list = overrides.get(item);
        if (list != null) {
            int[] enchantmentLevels = getEnchantmentLevels(item, itemStack.getTagCompound());
            boolean hasEffect = itemStack.hasEffect();
            for (T override : list) {
                if (override.match(itemStack, enchantmentLevels, hasEffect)) {
                    return override;
                }
            }
        }
        return null;
    }

    static ItemOverride findItemOverride(ItemStack itemStack) {
        return findMatch(items, itemStack);
    }

    static ArmorOverride findArmorOverride(ItemStack itemStack) {
        return findMatch(armors, itemStack);
    }

    static EnchantmentList findEnchantments(ItemStack itemStack) {
        return new EnchantmentList(enchantments, allItemEnchantments, itemStack);
    }

    public static boolean renderEnchantmentHeld(ItemStack itemStack, int renderPass) {
        if (itemStack == null || renderPass != 0) {
            return true;
        }
        if (!enableEnchantments) {
            return false;
        }
        EnchantmentList matches = findEnchantments(itemStack);
        if (matches.isEmpty()) {
            return !useGlint;
        }
        int width;
        int height;
        if (lastIcon == null) {
            width = height = 256;
        } else {
            width = lastIcon.getIconWidth();
            height = lastIcon.getIconHeight();
        }
        Enchantment.beginOuter3D();
        for (int i = 0; i < matches.size(); i++) {
            matches.getEnchantment(i)
                .render3D(matches.getIntensity(i), width, height);
        }
        Enchantment.endOuter3D();
        return !useGlint;
    }

    public static boolean renderEnchantmentDropped(ItemStack itemStack) {
        return renderEnchantmentHeld(itemStack, lastRenderPass);
    }

    public static boolean renderEnchantmentGUI(ItemStack itemStack, int x, int y, float z) {
        if (!enableEnchantments || itemStack == null) {
            return false;
        }
        EnchantmentList matches = findEnchantments(itemStack);
        if (matches.isEmpty()) {
            return !useGlint;
        }
        Enchantment.beginOuter2D();
        for (int i = 0; i < matches.size(); i++) {
            matches.getEnchantment(i)
                .render2D(matches.getIntensity(i), x, y, x + 16, y + 16, z);
        }
        Enchantment.endOuter2D();
        return !useGlint;
    }

    public static boolean setupArmorEnchantments(EntityLivingBase entity, int pass) {
        return setupArmorEnchantments(entity.getEquipmentInSlot(4 - pass));
    }

    public static boolean setupArmorEnchantments(ItemStack itemStack) {
        armorMatches = null;
        armorMatchIndex = 0;
        if (enableEnchantments && itemStack != null) {
            EnchantmentList tmpList = findEnchantments(itemStack);
            if (!tmpList.isEmpty()) {
                armorMatches = tmpList;
            }
        }
        return isArmorEnchantmentActive() || !useGlint;
    }

    public static boolean preRenderArmorEnchantment() {
        if (isArmorEnchantmentActive()) {
            Enchantment enchantment = armorMatches.getEnchantment(armorMatchIndex);
            if (enchantment.bindTexture(lastOrigIcon)) {
                enchantment.beginArmor(armorMatches.getIntensity(armorMatchIndex));
                return true;
            } else {
                return false;
            }
        } else {
            armorMatches = null;
            armorMatchIndex = 0;
            return false;
        }
    }

    public static boolean isArmorEnchantmentActive() {
        return armorMatches != null && armorMatchIndex < armorMatches.size();
    }

    public static void postRenderArmorEnchantment() {
        armorMatches.getEnchantment(armorMatchIndex)
            .endArmor();
        armorMatchIndex++;
    }

    static int[] getEnchantmentLevels(Item item, NBTTagCompound nbt) {
        int[] levels = null;
        if (nbt != null) {
            NBTBase base;
            if (item == itemEnchantedBook) {
                base = nbt.getTag("StoredEnchantments");
            } else {
                base = nbt.getTag("ench");
            }
            if (base instanceof NBTTagList list) {
                for (int i = 0; i < list.tagCount(); i++) {
                    base = ((NBTTagListExpansion) list).tagAt(i);
                    if (base instanceof NBTTagCompound) {
                        short id = ((NBTTagCompound) base).getShort("id");
                        short level = ((NBTTagCompound) base).getShort("lvl");
                        if (id >= 0 && id < MAX_ENCHANTMENTS && level > 0) {
                            if (levels == null) {
                                levels = new int[MAX_ENCHANTMENTS];
                            }
                            levels[id] += level;
                        }
                    }
                }
            }
        }
        return levels;
    }
}
