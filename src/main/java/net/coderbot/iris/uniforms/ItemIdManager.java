package net.coderbot.iris.uniforms;

import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.block.Block;
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
     * Set the item ID for a Block rendered outside of terrain.
     */
    public static void setBlockId(Block block, int metadata) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(Math.max(0, ItemMaterialHelper.getMaterialId(block, metadata)));
    }

    /**
     * Reset the item ID to 0.
     * Used when entering/exiting render sections or before glint rendering.
     */
    public static void resetItemId() {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
    }

    /**
     * The currently bound item ID.
     */
    public static int getItemId() {
        return CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
    }

    /**
     * Set an already-resolved item ID without performing a lookup.
     */
    public static void setItemIdRaw(int id) {
        CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
    }

    /**
     * Honestly I don't know why there's comments on a lot of these.
     * Guess I thought documentation is good but man if you can't figure this out
     * you're in trouble.
     */
    public static boolean isWorldRenderActive() {
        final WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
        return pipeline instanceof DeferredWorldRenderingPipeline drp && drp.isRenderingLevelGeometry();
    }
}
