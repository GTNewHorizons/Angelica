package com.gtnewhorizons.angelica.debug.screen.renderers;

import net.minecraft.world.EnumSkyBlock;

final class LightLevelRenderer implements DebugWorldRenderer, ScanState.CellVisitor {

    private static final int LEVELS = 16;
    private static final int RADIUS = 10;
    private static final int MAX_CELLS = 4096;

    private final EnumSkyBlock type;
    private final int unlit;
    private final int[] colors;
    private final double labelY;

    LightLevelRenderer(boolean sky) {
        this.type = sky ? EnumSkyBlock.Sky : EnumSkyBlock.Block;
        this.unlit = sky ? 15 : 0;
        this.colors = sky ? ramp(0x0000FF, 0x00FFFF) : ramp(0xAA0000, 0xFFFF00);
        this.labelY = sky ? 0.25 : 0.5;
    }

    private static int lerp(float t, int from, int to) {
        final int r = channel(t, from >> 16, to >> 16);
        final int g = channel(t, from >> 8, to >> 8);
        final int b = channel(t, from, to);
        return (r << 16) | (g << 8) | b;
    }

    private static int channel(float t, int from, int to) {
        final int a = from & 0xFF;
        final int b = to & 0xFF;
        return a + Math.round((b - a) * t);
    }

    private static int[] ramp(int from, int to) {
        final int[] out = new int[LEVELS];
        for (int i = 0; i < LEVELS; i++) {
            out[i] = lerp(i / 15.0F, from, to);
        }
        return out;
    }

    @Override
    public int fallbackIntervalMillis() {
        return -1;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        if (type == EnumSkyBlock.Sky && scan.world().provider.hasNoSky) return;
        scan.scanCube(RADIUS, MAX_CELLS, this, mesh);
    }

    @Override
    public boolean visitCell(ScanState scan, DebugMeshData mesh, int x, int y, int z) {
        final int level = scan.getSavedLight(type, x, y, z);
        if (level == unlit) return false;
        if (scan.getBlock(x, y, z).isOpaqueCube()) return false;
        mesh.label(x + 0.5, y + labelY, z + 0.5, level, 0xFF000000 | colors[level]);
        return true;
    }
}
