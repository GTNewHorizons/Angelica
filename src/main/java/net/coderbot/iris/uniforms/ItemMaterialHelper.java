package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.coderbot.iris.block_rendering.BlockMaterialMapping;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * Helper class for resolving material IDs for items.
 * Checks both item.properties and block.properties.
 */
public class ItemMaterialHelper {
    private static final Reference2ObjectMap<Item, Int2IntMap> MATERIAL_CACHE = new Reference2ObjectOpenHashMap<>();
    private static final int CACHE_MISS_SENTINEL = Integer.MIN_VALUE;
    private static final Reference2ObjectMap<Item, NamespacedId> ITEM_NAME_CACHE = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectMap<Block, Int2IntMap> BLOCK_MATERIAL_CACHE = new Reference2ObjectOpenHashMap<>();

    /**
     * Get the material ID for an ItemStack.
     * For ItemBlock: block.properties only, 0 if unmapped.
     * For other items: item.properties only, -1 if unmapped.
     *
     * @param itemStack The item stack to get material ID for
     */
    public static int getMaterialId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return -1;
        }

        // Check item NBT conditions first
        final NbtConditionalIdMap<NamespacedId> itemNbtMap = BlockRenderingSettings.INSTANCE.getItemNbtMap();
        if (itemNbtMap != null && !itemNbtMap.isEmpty() && itemStack.hasTagCompound()) {
            NamespacedId namespacedId = getCachedItemName(itemStack.getItem());
            if (namespacedId != null && itemNbtMap.hasConditions(namespacedId)) {
                int nbtId = itemNbtMap.resolve(namespacedId, itemStack.getTagCompound());
                if (nbtId != -1) {
                    return nbtId;
                }
            }
        }

        return getMaterialId(itemStack.getItem(), itemStack.getItemDamage());
    }


    public static int getMaterialId(Item item, int metadata) {
        if (item == null) {
            return -1;
        }

        // Check cache first
        Int2IntMap metadataCache = MATERIAL_CACHE.get(item);
        if (metadataCache != null) {
            int cached = metadataCache.getOrDefault(metadata, CACHE_MISS_SENTINEL);
            if (cached != CACHE_MISS_SENTINEL) {
                return cached;
            }
        }

        // Cache miss
        int materialId = lookupMaterialId(item, metadata);

        if (metadataCache == null) {
            metadataCache = new Int2IntOpenHashMap();
            metadataCache.defaultReturnValue(CACHE_MISS_SENTINEL);
            MATERIAL_CACHE.put(item, metadataCache);
        }
        metadataCache.put(metadata, materialId);

        return materialId;
    }

    /**
     * Get the material ID for a Block rendered outside of terrain.
     *
     * @return The material ID, or 0 if not found
     */
    public static int getMaterialId(Block block, int metadata) {
        if (block == null) {
            return 0;
        }

        Int2IntMap metadataCache = BLOCK_MATERIAL_CACHE.get(block);
        if (metadataCache != null) {
            int cached = metadataCache.getOrDefault(metadata, CACHE_MISS_SENTINEL);
            if (cached != CACHE_MISS_SENTINEL) {
                return cached;
            }
        }

        int materialId = lookupBlockMaterialId(block, metadata);

        if (metadataCache == null) {
            metadataCache = new Int2IntOpenHashMap();
            metadataCache.defaultReturnValue(CACHE_MISS_SENTINEL);
            BLOCK_MATERIAL_CACHE.put(block, metadataCache);
        }
        metadataCache.put(metadata, materialId);

        return materialId;
    }

    private static int lookupBlockMaterialId(Block block, int metadata) {
        return lookupBlockPropertiesId(block, metadata);
    }

    /**
     * Perform the actual material ID lookup without caching.
     */
    private static int lookupMaterialId(Item item, int metadata) {
        if (item instanceof ItemBlock itemBlock) {
            final Block block = itemBlock.field_150939_a; // The block this item places
            return block == null ? 0 : lookupBlockPropertiesId(block, itemBlock.getMetadata(metadata));
        }

        return lookupItemPropertiesId(item);
    }

    private static int lookupBlockPropertiesId(Block block, int metadata) {
        final Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
        if (blockMetaMatches == null) {
            return 0;
        }

        final Int2IntMap metaMap = blockMetaMatches.get(block);
        if (metaMap == null) {
            return 0;
        }

        final int id = BlockMaterialMapping.resolveId(metaMap, metadata);
        return id == -1 ? 0 : id;
    }

    private static int lookupItemPropertiesId(Item item) {
        final Object2IntFunction<NamespacedId> itemIds = BlockRenderingSettings.INSTANCE.getItemIds();
        if (itemIds == null) {
            return -1;
        }

        final NamespacedId name = getCachedItemName(item);
        return name == null ? -1 : itemIds.applyAsInt(name);
    }

    /**
     * Clear the material ID cache.
     * Should be called when shader packs are reloaded
     */
    public static void clearCache() {
        MATERIAL_CACHE.clear();
        ITEM_NAME_CACHE.clear();
        BLOCK_MATERIAL_CACHE.clear();
    }

    private static NamespacedId getCachedItemName(Item item) {
        NamespacedId cached = ITEM_NAME_CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        String itemIdString = Item.itemRegistry.getNameForObject(item);
        if (itemIdString == null) {
            return null;
        }

        ResourceLocation itemId = new ResourceLocation(itemIdString);
        cached = new NamespacedId(itemId.getResourceDomain(), itemId.getResourcePath());
        ITEM_NAME_CACHE.put(item, cached);
        return cached;
    }
}
