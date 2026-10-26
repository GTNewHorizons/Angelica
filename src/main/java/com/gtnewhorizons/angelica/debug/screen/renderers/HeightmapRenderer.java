package com.gtnewhorizons.angelica.debug.screen.renderers;

final class HeightmapRenderer implements DebugWorldRenderer {

    private static final int CHUNK_DIST = 2;
    private static final double BOX_HEIGHT = 0.09375;
    private static final int COLOR = 0xFF00B200;

    @Override
    public Granularity viewerGranularity() {
        return Granularity.CHUNK;
    }

    @Override
    public int minIntervalMillis() {
        return 500;
    }

    @Override
    public int fallbackIntervalMillis() {
        return -1;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        final int chunkX = scan.viewerX() >> 4;
        final int chunkZ = scan.viewerZ() >> 4;
        int minHeight = Integer.MAX_VALUE;
        for (int dx = -CHUNK_DIST; dx <= CHUNK_DIST; dx++) {
            for (int dz = -CHUNK_DIST; dz <= CHUNK_DIST; dz++) {
                final int baseX = (chunkX + dx) << 4;
                final int baseZ = (chunkZ + dz) << 4;
                if (!scan.isLoaded(baseX, baseZ)) continue;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final double wx = baseX + x;
                        final double wz = baseZ + z;
                        final int height = scan.getHeight(baseX + x, baseZ + z);
                        if (height < minHeight) minHeight = height;
                        final double y = height + BOX_HEIGHT;
                        mesh.fillBox(wx + 0.25, y, wz + 0.25, wx + 0.75, y + BOX_HEIGHT, wz + 0.75, COLOR);
                    }
                }
            }
        }
        final int watchMinY = minHeight == Integer.MAX_VALUE ? 0 : minHeight - 1;
        scan.watch((chunkX - CHUNK_DIST) << 4, watchMinY, (chunkZ - CHUNK_DIST) << 4, ((chunkX + CHUNK_DIST) << 4) + 15, 255, ((chunkZ + CHUNK_DIST) << 4) + 15);
    }
}
