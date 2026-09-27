package com.gtnewhorizons.angelica.mixins.hooks;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.StateSet;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

public class TextHighlightHooks {

    public static void draw(int x1, int y1, int x2, int y2) {
        final int depth = GLStateManager.pushState(StateSet.BLEND);

        GLStateManager.glDisable(GL11.GL_TEXTURE_2D);
        GLStateManager.glEnable(GL11.GL_BLEND);

        GLStateManager.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        quad(x1, y1, x2, y2);

        GLStateManager.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GLStateManager.glColor4f(0.0f, 0.0f, 1.0f, 1.0f);
        quad(x1, y1, x2, y2);

        GLStateManager.glEnable(GL11.GL_TEXTURE_2D);
        GLStateManager.popStateTo(depth);
    }

    private static void quad(int x1, int y1, int x2, int y2) {
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(x1, y2, 0.0D);
        tessellator.addVertex(x2, y2, 0.0D);
        tessellator.addVertex(x2, y1, 0.0D);
        tessellator.addVertex(x1, y1, 0.0D);
        tessellator.draw();
    }
}
