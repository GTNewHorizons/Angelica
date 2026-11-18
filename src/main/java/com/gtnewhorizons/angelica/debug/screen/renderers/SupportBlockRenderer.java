package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import java.util.List;

final class SupportBlockRenderer implements DebugWorldRenderer {

    private static final double RADIUS_SQ = 16.0 * 16.0;
    private static final double GROW = 0.02;
    private static final int MAX_BLOCKS = 256;
    private static final int PLAYER = 0x66FF0000;
    private static final int OTHER = 0x6600FF00;

    @Override
    public int minIntervalMillis() {
        return 100;
    }

    @Override
    public int fallbackIntervalMillis() {
        return 100;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        final EntityLivingBase viewer = scan.viewer();
        final Entity player = Minecraft.getMinecraft().thePlayer;
        final List<Entity> entities = scan.world().loadedEntityList;
        final int n = entities.size();
        int blocks = 0;
        for (int i = 0; i < n && blocks < MAX_BLOCKS; i++) {
            final Entity e = entities.get(i);
            if (!e.onGround) continue;
            final double dx = e.posX - viewer.posX;
            final double dy = e.posY - viewer.posY;
            final double dz = e.posZ - viewer.posZ;
            if (dx * dx + dy * dy + dz * dz > RADIUS_SQ) continue;
            final int bx = MathHelper.floor_double(e.posX);
            int by = MathHelper.floor_double(e.boundingBox.minY - 0.2);
            final int bz = MathHelper.floor_double(e.posZ);
            final int belowType = scan.getBlock(bx, by - 1, bz).getRenderType();
            if (belowType == 11 || belowType == 32 || belowType == 21) by--;
            final double x = bx;
            final double y = by;
            final double z = bz;
            mesh.lineBox(LineWidth.MEDIUM, x - GROW, y - GROW, z - GROW, x + 1.0 + GROW, y + 1.0 + GROW, z + 1.0 + GROW, e == player ? PLAYER : OTHER);
            blocks++;
        }
    }
}
