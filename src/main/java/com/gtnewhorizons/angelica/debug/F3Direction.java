package com.gtnewhorizons.angelica.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

public class F3Direction {
    public static void renderWorldDirectionsEvent(Minecraft mc, RenderGameOverlayEvent.Pre event) {
        if (mc.gameSettings.showDebugInfo && mc.gameSettings.thirdPersonView == 0) {
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int width = scaledresolution.getScaledWidth();
            int height = scaledresolution.getScaledHeight();

            GL11.glPushMatrix();

            GL11.glTranslatef((float) (width / 2), (float) (height / 2), -90);

            Entity entity = mc.renderViewEntity;
            GL11.glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * event.partialTicks, -1.0F, 0.0F, 0.0F);
            GL11.glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * event.partialTicks, 0.0F, 1.0F, 0.0F);

            GL11.glScalef(-1.0F, -1.0F, -1.0F);

            renderWorldDirections();

            GL11.glPopMatrix();
        }
    }
    public static void renderWorldDirections() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDepthMask(false);
        Tessellator tessellator = Tessellator.instance;

        GL11.glLineWidth(2);
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


        GL11.glLineWidth(2);
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

        GL11.glLineWidth(1.0F);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
