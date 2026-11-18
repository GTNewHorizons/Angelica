package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

import java.util.List;

final class CollisionBoxRenderer implements DebugWorldRenderer {

    private static final int MAX_BOXES = 2048;
    private static final double RANGE = 6.0;
    private static final int COLOR = 0xFFFFFFFF;

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
        final AxisAlignedBB query = viewer.boundingBox.expand(RANGE, RANGE, RANGE);
        scan.watch(
            MathHelper.floor_double(query.minX) - 1, MathHelper.floor_double(query.minY) - 1, MathHelper.floor_double(query.minZ) - 1,
            MathHelper.floor_double(query.maxX) + 1, MathHelper.floor_double(query.maxY) + 1, MathHelper.floor_double(query.maxZ) + 1);
        final List<AxisAlignedBB> boxes = scan.world().getCollidingBoundingBoxes(viewer, query);
        final int n = Math.min(boxes.size(), MAX_BOXES);
        for (int i = 0; i < n; i++) {
            final AxisAlignedBB box = boxes.get(i);
            mesh.lineBox(LineWidth.MEDIUM, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, COLOR);
        }
    }
}
