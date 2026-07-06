package com.gtnewhorizons.angelica.debug;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

public class F3Direction {
    public static void renderWorldDirectionsEvent(Minecraft mc, RenderGameOverlayEvent.Pre event) {
        if (mc.gameSettings.showDebugInfo && mc.gameSettings.thirdPersonView == 0) {
            final ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            final int width = scaledresolution.getScaledWidth();
            final int height = scaledresolution.getScaledHeight();

            GLStateManager.glPushMatrix();

            GLStateManager.glTranslatef((float) (width / 2), (float) (height / 2), -90);

            final Entity entity = mc.renderViewEntity;
            GLStateManager.glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * event.partialTicks, -1.0F, 0.0F, 0.0F);
            GLStateManager.glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * event.partialTicks, 0.0F, 1.0F, 0.0F);

            GLStateManager.glScalef(-1.0F, -1.0F, -1.0F);

            renderWorldDirections();

            GLStateManager.glPopMatrix();
        }
    }
    public static void renderWorldDirections() {
        GLStateManager.disableTexture();
        GLStateManager.glDepthMask(false);
        final Tessellator tessellator = Tessellator.instance;

        GLStateManager.glLineWidth(2.0F);
        tessellator.startDrawing(GL11.GL_LINES);

        //X
        tessellator.setColorRGBA_F(255, 0, 0, 1);
        tessellator.addVertex(0.0D, 0.0D, 0.0D);
        tessellator.addVertex(10, 0.0D, 0.0D);

        //Z
        tessellator.setColorRGBA_F(0, 0, 255, 1);
        tessellator.addVertex(0.0D, 0.0D, 0.0D);
        tessellator.addVertex(0.0D, 0.0D, 10);

        //Y
        tessellator.setColorRGBA_F(0, 255, 0, 1);
        tessellator.addVertex(0.0D, 0.0D, 0.0D);
        tessellator.addVertex(0.0D, 10, 0.0D);

        tessellator.draw();

        GLStateManager.glLineWidth(1.0F);
        GLStateManager.glDepthMask(true);
        GLStateManager.enableTexture();
    }
}
