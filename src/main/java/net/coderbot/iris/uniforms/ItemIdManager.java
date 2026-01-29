package net.coderbot.iris.uniforms;

import net.minecraft.item.ItemStack;

/**
 * Helper class to manage the currentRenderedItem ID uniform.
 */
public class ItemIdManager {
    /**
     * Set the item ID for an armor piece or held item.
     * If the ItemStack is null/empty, resets to 0.
     * Otherwise, looks up the material ID and sets it.
     *
     * @param itemStack The armor or item being rendered
     */
    public static void setItemId(ItemStack itemStack) {
        if (itemStack == null || itemStack.getItem() == null) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
            return;
        }

        int id = ItemMaterialHelper.getMaterialId(itemStack);
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Reset the item ID to 0.
     * Used when entering/exiting render sections or before glint rendering.
     */
    public static void resetItemId() {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
    }
}
