package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
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

    /**
     * Get the material ID for an ItemStack.
     * Checks item.properties first, then block.properties if the item is an ItemBlock.
     *
     * @param itemStack The item stack to get material ID for
     * @return The material ID, or 0 if not found in either map
     */
    public static int getMaterialId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            return 0;
        }

        Item item = itemStack.getItem();
        String itemIdString = (String) Item.itemRegistry.getNameForObject(item);
        if (itemIdString == null) {
            return 0;
        }

        ResourceLocation itemId = new ResourceLocation(itemIdString);
        NamespacedId namespacedId = new NamespacedId(itemId.getResourceDomain(), itemId.getResourcePath());

        // First, check item.properties
        Object2IntFunction<NamespacedId> itemIds = BlockRenderingSettings.INSTANCE.getItemIds();
        if (itemIds != null) {
            int id = itemIds.applyAsInt(namespacedId);
            if (id != 0) {
                return id;
            }
        }

        // Second, check block.properties if this is an ItemBlock
        if (item instanceof ItemBlock) {
            ItemBlock itemBlock = (ItemBlock) item;
            Block block = itemBlock.field_150939_a; // The block this item places
            int metadata = itemStack.getItemDamage();

            Reference2ObjectMap<Block, Int2IntMap> blockMetaMatches = BlockRenderingSettings.INSTANCE.getBlockMetaMatches();
            if (blockMetaMatches != null) {
                Int2IntMap metaMap = blockMetaMatches.get(block);
                if (metaMap != null) {
                    int id = metaMap.get(metadata);
                    if (id != 0) {
                        return id;
                    }
                }
            }
        }

        // Not found in either map
        return 0;
    }
}
