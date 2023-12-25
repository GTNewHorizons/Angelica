package com.prupe.mcpatcher.mob;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.mal.resource.TexturePackAPI;

public class MobOverlay {

    private static final ResourceLocation MOOSHROOM_OVERLAY = TexturePackAPI
        .newMCPatcherResourceLocation("mob/cow/mooshroom_overlay.png");
    private static final ResourceLocation SNOWMAN_OVERLAY = TexturePackAPI
        .newMCPatcherResourceLocation("mob/snowman_overlay.png");

    private static final double MOO_X0 = -0.45;
    private static final double MOO_X1 = 0.45;
    private static final double MOO_Y0 = -0.5;
    private static final double MOO_Y1 = 0.5;
    private static final double MOO_Z0 = -0.45;
    private static final double MOO_Z1 = 0.45;

    private static final double SNOW_X0 = -0.5;
    private static final double SNOW_X1 = 0.5;
    private static final double SNOW_Y0 = -0.5;
    private static final double SNOW_Y1 = 0.5;
    private static final double SNOW_Z0 = -0.5;
    private static final double SNOW_Z1 = 0.5;

    private static boolean overlayActive;
    private static int overlayCounter;
    private static boolean haveMooshroom;
    private static boolean haveSnowman;

    static void reset() {
        haveMooshroom = TexturePackAPI.hasResource(MOOSHROOM_OVERLAY);
        haveSnowman = TexturePackAPI.hasResource(SNOWMAN_OVERLAY);
    }

    public static ResourceLocation setupMooshroom(EntityLivingBase entity, ResourceLocation defaultTexture) {
        overlayCounter = 0;
        if (haveMooshroom) {
            overlayActive = true;
            return MobRandomizer.randomTexture(entity, MOOSHROOM_OVERLAY);
        } else {
            overlayActive = false;
            return defaultTexture;
        }
    }

    public static boolean renderMooshroomOverlay(double offset) {
        if (overlayActive && overlayCounter < 3) {
            float tileX0 = overlayCounter / 3.0f;
            float tileX1 = ++overlayCounter / 3.0f;

            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y1 + offset, MOO_Z0 - offset, tileX0, 0.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y0 + offset, MOO_Z0 - offset, tileX0, 1.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y0 + offset, MOO_Z1 - offset, tileX1, 1.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y1 + offset, MOO_Z1 - offset, tileX1, 0.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y1 + offset, MOO_Z1 - offset, tileX0, 0.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y0 + offset, MOO_Z1 - offset, tileX0, 1.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y0 + offset, MOO_Z0 - offset, tileX1, 1.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y1 + offset, MOO_Z0 - offset, tileX1, 0.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y1 + offset, MOO_Z1 - offset, tileX0, 0.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y0 + offset, MOO_Z1 - offset, tileX0, 1.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y0 + offset, MOO_Z0 - offset, tileX1, 1.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y1 + offset, MOO_Z0 - offset, tileX1, 0.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y1 + offset, MOO_Z0 - offset, tileX0, 0.0);
            tessellator.addVertexWithUV(MOO_X1 + offset, MOO_Y0 + offset, MOO_Z0 - offset, tileX0, 1.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y0 + offset, MOO_Z1 - offset, tileX1, 1.0);
            tessellator.addVertexWithUV(MOO_X0 + offset, MOO_Y1 + offset, MOO_Z1 - offset, tileX1, 0.0);
            tessellator.draw();
        }
        return overlayActive;
    }

    public static void finishMooshroom() {
        overlayCounter = 0;
        overlayActive = false;
    }

    public static boolean renderSnowmanOverlay(EntityLivingBase entity) {
        if (!haveSnowman) {
            return false;
        }
        TexturePackAPI.bindTexture(MobRandomizer.randomTexture(entity, SNOWMAN_OVERLAY));
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();

        double[] c = new double[4];

        // bottom = y-
        getTileCoordinates(0, c);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z0, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z1, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z1, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z0, c[0], c[3]);

        // top = y+
        getTileCoordinates(1, c);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z0, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z1, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z1, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z0, c[0], c[3]);

        // back = x-
        getTileCoordinates(2, c);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z1, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z0, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z0, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z1, c[0], c[3]);

        // right = z-
        getTileCoordinates(3, c);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z0, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z0, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z0, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z0, c[0], c[3]);

        // front = x+
        getTileCoordinates(4, c);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z0, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z1, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z1, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z0, c[0], c[3]);

        // left = z+
        getTileCoordinates(5, c);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y1, SNOW_Z1, c[0], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y1, SNOW_Z1, c[1], c[2]);
        tessellator.addVertexWithUV(SNOW_X0, SNOW_Y0, SNOW_Z1, c[1], c[3]);
        tessellator.addVertexWithUV(SNOW_X1, SNOW_Y0, SNOW_Z1, c[0], c[3]);

        tessellator.draw();
        return true;
    }

    private static void getTileCoordinates(int tileNum, double[] c) {
        c[0] = (tileNum % 3) / 3.0;
        c[1] = c[0] + 1.0 / 3.0;
        c[2] = (tileNum / 3) / 2.0;
        c[3] = c[2] + 0.5;
    }
}
