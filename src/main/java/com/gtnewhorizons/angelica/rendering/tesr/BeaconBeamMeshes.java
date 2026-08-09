package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizon.gtnhlib.client.renderer.vertex.DefaultVertexFormat;
import com.gtnewhorizons.angelica.api.tesr.TesrMaterial;
import com.gtnewhorizons.angelica.api.tesr.TesrMeshBuilder;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class BeaconBeamMeshes {

    private static final ResourceLocation BEACON_TEXTURE = new ResourceLocation("textures/entity/beacon_beam.png");

    private static final TesrMaterial INNER = TesrMaterial.builder()
        .color(1f, 1f, 1f, 1f).lightmap(240f, 240f).unlit().noCull().cutout(0.1f).stream()
        .special(TesrMaterial.SpecialRender.BEACON_BEAM).build();
    private static final TesrMaterial OUTER = TesrMaterial.builder()
        .color(1f, 1f, 1f, 32f / 255f).lightmap(240f, 240f).unlit().noCull().cutout(0.1f)
        .translucent().noDepthWrite().stream()
        .special(TesrMaterial.SpecialRender.BEACON_BEAM).build();

    private static final Object INNER_KEY = new Object();
    private static final Object OUTER_KEY = new Object();

    private static final double INNER_RADIUS = 0.2;
    private static final double BEAM_HEIGHT = 256.0;
    private static final double INNER_V_TOP = BEAM_HEIGHT * (0.5 / INNER_RADIUS);

    private static final TesrMeshBuilder INNER_BUILDER = sink -> {
        final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE, BEACON_TEXTURE, INNER);
        final double[] cx = new double[4];
        final double[] cz = new double[4];

        final double[] angles = {3 * Math.PI / 4, Math.PI / 4, 5 * Math.PI / 4, 7 * Math.PI / 4};
        for (int i = 0; i < 4; i++) {
            cx[i] = Math.cos(angles[i]) * INNER_RADIUS;
            cz[i] = Math.sin(angles[i]) * INNER_RADIUS;
        }
        sideQuad(t, cx[0], cz[0], cx[1], cz[1], INNER_V_TOP);
        sideQuad(t, cx[3], cz[3], cx[2], cz[2], INNER_V_TOP);
        sideQuad(t, cx[1], cz[1], cx[3], cz[3], INNER_V_TOP);
        sideQuad(t, cx[2], cz[2], cx[0], cz[0], INNER_V_TOP);
    };

    private static final TesrMeshBuilder OUTER_BUILDER = sink -> {
        final Tessellator t = sink.angelica$bucket(DefaultVertexFormat.POSITION_TEXTURE, BEACON_TEXTURE, OUTER);
        final double r = 0.3;
        sideQuad(t, -r, -r, r, -r, BEAM_HEIGHT);
        sideQuad(t, r, r, -r, r, BEAM_HEIGHT);
        sideQuad(t, r, -r, r, r, BEAM_HEIGHT);
        sideQuad(t, -r, r, -r, -r, BEAM_HEIGHT);
    };

    private static void sideQuad(Tessellator t, double x0, double z0, double x1, double z1, double vTop) {
        t.addVertexWithUV(x0, BEAM_HEIGHT, z0, 1.0, vTop);
        t.addVertexWithUV(x0, 0.0, z0, 1.0, 0.0);
        t.addVertexWithUV(x1, 0.0, z1, 0.0, 0.0);
        t.addVertexWithUV(x1, BEAM_HEIGHT, z1, 0.0, vTop);
    }

    private BeaconBeamMeshes() {}

    public static boolean render(TileEntityBeacon beacon, double x, double y, double z, float partialTicks) {
        final float f1 = beacon.func_146002_i();
        GLStateManager.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        if (f1 <= 0.0f) {
            return true;
        }
        if (f1 < 1.0f) {
            return false;
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(BEACON_TEXTURE);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GLStateManager.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);

        final float f2 = (float) beacon.getWorldObj().getTotalWorldTime() + partialTicks;
        final float f3 = -f2 * 0.2f - MathHelper.floor_float(-f2 * 0.1f);
        final double d3 = f2 * 0.025 * -1.5;

        GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
        GLStateManager.glPushMatrix();
        GLStateManager.glLoadIdentity();
        GLStateManager.glTranslatef(0f, f3 - 1f, 0f);
        GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        try {
            GLStateManager.glPushMatrix();
            GLStateManager.glTranslated(x + 0.5, y, z + 0.5);
            GLStateManager.glRotatef((float) -Math.toDegrees(d3), 0f, 1f, 0f);
            AngelicaTesrMeshCache.INSTANCE.renderCached(INNER_KEY, INNER_BUILDER);
            GLStateManager.glPopMatrix();

            GLStateManager.glPushMatrix();
            GLStateManager.glTranslated(x + 0.5, y, z + 0.5);
            AngelicaTesrMeshCache.INSTANCE.renderCached(OUTER_KEY, OUTER_BUILDER);
            GLStateManager.glPopMatrix();
        } finally {
            GLStateManager.glMatrixMode(GL11.GL_TEXTURE);
            GLStateManager.glPopMatrix();
            GLStateManager.glMatrixMode(GL11.GL_MODELVIEW);
        }
        return true;
    }
}
