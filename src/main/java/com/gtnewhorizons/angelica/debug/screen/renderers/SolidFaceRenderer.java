package com.gtnewhorizons.angelica.debug.screen.renderers;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

final class SolidFaceRenderer implements DebugWorldRenderer, ScanState.CellVisitor {

    private static final int RADIUS = 6;
    private static final int MAX_CELLS = 8192;
    private static final double OFFSET = 0.002;
    private static final int COLOR = 0x80FF0000;

    @Override
    public int fallbackIntervalMillis() {
        return -1;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        scan.scanCube(RADIUS, MAX_CELLS, this, mesh);
    }

    @Override
    public boolean visitCell(ScanState scan, DebugMeshData mesh, int x, int y, int z) {
        final World world = scan.world();
        final Block block = scan.getBlock(x, y, z);
        if (block.isAir(world, x, y, z)) return false;
        final ForgeDirection[] dirs = ForgeDirection.VALID_DIRECTIONS;
        boolean emitted = false;
        for (int d = 0; d < 6; d++) {
            final ForgeDirection dir = dirs[d];
            if (!block.isSideSolid(world, x, y, z, dir)) continue;
            if (scan.getBlock(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ).isOpaqueCube()) continue;
            mesh.face(d, x, y, z, x + 1.0, y + 1.0, z + 1.0, OFFSET, COLOR);
            emitted = true;
        }
        return emitted;
    }
}
