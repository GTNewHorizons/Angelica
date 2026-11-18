package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;

final class ChunkBordersRenderer implements DebugWorldRenderer {

    private static final int YELLOW = 0xFFFFFF00;
    private static final int CELL_BORDER = 0xFF009B9B;
    private static final int MAJOR = 0xFF3F3FFF;
    private static final int NEIGHBOR = 0x7FFF0000;
    private static final double MIN_Y = 0.0;
    private static final double MAX_Y = 256.0;

    @Override
    public Granularity viewerGranularity() {
        return Granularity.SECTION;
    }

    @Override
    public int minIntervalMillis() {
        return 0;
    }

    @Override
    public int fallbackIntervalMillis() {
        return -1;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        final double x0 = DebugRenderSlots.anchor(scan.viewerX());
        final double z0 = DebugRenderSlots.anchor(scan.viewerZ());
        final double sectionY = DebugRenderSlots.anchor(scan.viewerY());
        final double x1 = x0 + 16.0;
        final double z1 = z0 + 16.0;

        for (int x = -16; x <= 32; x += 16) {
            for (int z = -16; z <= 32; z += 16) {
                mesh.line(LineWidth.THICK, x0 + x, MIN_Y, z0 + z, x0 + x, MAX_Y, z0 + z, NEIGHBOR);
            }
        }
        for (int i = 2; i < 16; i += 2) {
            final int color = i % 4 == 0 ? CELL_BORDER : YELLOW;
            mesh.line(LineWidth.THIN, x0 + i, MIN_Y, z0, x0 + i, MAX_Y, z0, color);
            mesh.line(LineWidth.THIN, x0 + i, MIN_Y, z1, x0 + i, MAX_Y, z1, color);
            mesh.line(LineWidth.THIN, x0, MIN_Y, z0 + i, x0, MAX_Y, z0 + i, color);
            mesh.line(LineWidth.THIN, x1, MIN_Y, z0 + i, x1, MAX_Y, z0 + i, color);
        }
        for (int y = 0; y <= 256; y += 2) {
            mesh.ring(LineWidth.THIN, x0, y, z0, x1, z1, y % 8 == 0 ? CELL_BORDER : YELLOW);
        }
        for (int x = 0; x <= 16; x += 16) {
            for (int z = 0; z <= 16; z += 16) {
                mesh.line(LineWidth.THICK, x0 + x, MIN_Y, z0 + z, x0 + x, MAX_Y, z0 + z, MAJOR);
            }
        }
        mesh.alwaysOnTop(true);
        mesh.lineBox(LineWidth.THIN, x0, sectionY, z0, x1, sectionY + 16.0, z1, MAJOR);
        mesh.alwaysOnTop(false);
        for (int y = 0; y <= 256; y += 16) {
            mesh.ring(LineWidth.THICK, x0, y, z0, x1, z1, MAJOR);
        }
    }
}
