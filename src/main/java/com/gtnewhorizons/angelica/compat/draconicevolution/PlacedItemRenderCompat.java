package com.gtnewhorizons.angelica.compat.draconicevolution;

import com.gtnewhorizons.angelica.glsm.GLSMAttribStack;
import com.gtnewhorizons.angelica.glsm.GLSMDraws;
import com.gtnewhorizons.angelica.glsm.GLSMMatrices;
import com.gtnewhorizons.angelica.rendering.tesr.BatchEligibility;
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
        //TODO: Properly optimize this later, right now it's breaking everything.
        return stack.getItem() instanceof ItemBlock;
    }

    public static void render(double x, double y, double z, Runnable draw) {
        final boolean inFrame = RenderItem.renderInFrame;
        GLSMMatrices.glPushMatrix();
        GLSMAttribStack.glPushAttrib(GL11.GL_ENABLE_BIT);
        try {
            GLSMMatrices.glTranslated(x, y, z);
            draw.run();
        } finally {
            RenderItem.renderInFrame = inFrame;
            GLSMAttribStack.glPopAttrib();
            GLSMMatrices.glPopMatrix();
        }
    }

    public static byte renderWithBatchState(double x, double y, double z, byte state, Runnable draw) {
        BatchEligibility.beginIsolated(state, GLSMDraws.drawCalls);
        try {
            render(x, y, z, draw);
        } finally {
            state = BatchEligibility.endIsolated(state, GLSMDraws.drawCalls);
        }
        return state;
    }
}
