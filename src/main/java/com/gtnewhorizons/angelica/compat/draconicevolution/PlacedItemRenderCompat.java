package com.gtnewhorizons.angelica.compat.draconicevolution;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.rendering.items.BlockRenderListManager;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

public final class PlacedItemRenderCompat {
    private PlacedItemRenderCompat() {}

    public static boolean requiresLiveRender(ItemStack stack) {
        if (stack == null) return false;
        if (MinecraftForgeClient.getItemRenderer(stack, ItemRenderType.ENTITY) != null) return true;
        return stack.getItem() instanceof ItemBlock
            && BlockRenderListManager.isISBRH(Block.getBlockFromItem(stack.getItem()).getRenderType());
    }

    public static void render(double x, double y, double z, Runnable draw) {
        final boolean inFrame = RenderItem.renderInFrame;
        GLStateManager.glPushMatrix();
        GLStateManager.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GLStateManager.glTranslated(x, y, z);
            draw.run();
        } finally {
            RenderItem.renderInFrame = inFrame;
            GLStateManager.glPopAttrib();
            GLStateManager.glPopMatrix();
        }
    }

    public static byte renderWithBatchState(double x, double y, double z, byte state, Runnable draw) {
        BatchEligibility.beginIsolated(state, GLStateManager.drawCalls);
        try {
            render(x, y, z, draw);
        } finally {
            state = BatchEligibility.endIsolated(state, GLStateManager.drawCalls);
        }
        return state;
    }
}
