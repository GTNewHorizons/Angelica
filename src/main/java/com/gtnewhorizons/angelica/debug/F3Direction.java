package com.gtnewhorizons.angelica.debug;

import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDepthMask;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glDisable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glEnable;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPopMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glPushMatrix;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glRotatef;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glScalef;
import static com.gtnewhorizons.angelica.glsm.GLStateManager.glTranslatef;
import static org.lwjgl.opengl.GL11C.glLineWidth;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

@Lwjgl3Aware
public class F3Direction {
    public static void renderWorldDirectionsEvent(Minecraft mc, RenderGameOverlayEvent.Pre event) {
        if (mc.gameSettings.showDebugInfo && mc.gameSettings.thirdPersonView == 0) {
            ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int width = scaledresolution.getScaledWidth();
            int height = scaledresolution.getScaledHeight();

            glPushMatrix();

            glTranslatef((float) (width / 2), (float) (height / 2), -90);

            Entity entity = mc.renderViewEntity;
            glRotatef(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * event.partialTicks, -1.0F, 0.0F, 0.0F);
            glRotatef(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * event.partialTicks, 0.0F, 1.0F, 0.0F);

            glScalef(-1.0F, -1.0F, -1.0F);

            renderWorldDirections();

            glPopMatrix();
        }
    }
    public static void renderWorldDirections() {
        glDisable(GL11.GL_TEXTURE_2D);
        glDepthMask(false);
        Tessellator tessellator = Tessellator.instance;

        glLineWidth(2.0F);
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

        glLineWidth(1.0F);
        glDepthMask(true);
        glEnable(GL11.GL_TEXTURE_2D);
    }
}
