package com.gtnewhorizons.angelica.debug.screen.renderers;

interface DebugWorldRenderer {

    enum Granularity {

        BLOCK,
        SECTION,
        CHUNK
    }

    void rebuild(ScanState scan, DebugMeshData mesh);

    default Granularity viewerGranularity() {
        return Granularity.BLOCK;
    }

    default int minIntervalMillis() {
        return 250;
    }

    default int fallbackIntervalMillis() {
        return 2000;
    }
}
