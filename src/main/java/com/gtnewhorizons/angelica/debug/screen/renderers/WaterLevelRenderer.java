package com.gtnewhorizons.angelica.debug.screen.renderers;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;

final class WaterLevelRenderer implements DebugWorldRenderer, ScanState.CellVisitor {

    private static final int RADIUS = 10;
    private static final int MAX_CELLS = 8192;
    private static final int FILL = 0x2600FF00;
    private static final int LABEL = 0xFF000000;

    static int level(int meta) {
        return meta >= 8 ? 8 : 8 - meta;
    }

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
        final Block block = scan.getBlock(x, y, z);
        if (!(block instanceof BlockLiquid) || block.getMaterial() != Material.water) return false;
        final int meta = scan.getBlockMetadata(x, y, z);
        final double height = scan.getBlock(x, y + 1, z).getMaterial() == Material.water ? 1.0 : 1.0 - BlockLiquid.getLiquidHeightPercent(meta);
        final double top = y + height;
        mesh.fillBox(x + 0.01, y + 0.01, z + 0.01, x + 0.99, top, z + 0.99, FILL);
        mesh.label(x + 0.5, top, z + 0.5, level(meta), LABEL);
        return true;
    }
}
