package com.gtnewhorizons.angelica.debug.screen.renderers;

import com.gtnewhorizons.angelica.debug.screen.renderers.DebugMeshData.LineWidth;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import org.embeddedt.embeddium.impl.render.chunk.LocalSectionIndex;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderList;
import org.embeddedt.embeddium.impl.render.chunk.lists.SortedRenderLists;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.GraphDirection;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.GraphDirectionSet;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.VisibilityEncoding;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

final class SectionVisibilityRenderer implements DebugWorldRenderer {

    private static final int RANGE = 3;
    private static final int MAX_LINES = 8192;
    private static final int LINE = 0xFFFF0000;
    private static final int FILL = 0x33E5E500;
    private static final double INSET = 0.5;

    @Override
    public int fallbackIntervalMillis() {
        return 250;
    }

    @Override
    public void rebuild(ScanState scan, DebugMeshData mesh) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null) return;
        final AngelicaRenderSectionManager manager = renderer.getRenderSectionManager();
        if (manager == null) return;
        final SortedRenderLists lists = manager.getRenderLists();
        final int camX = scan.viewerX() >> 4;
        final int camY = scan.viewerY() >> 4;
        final int camZ = scan.viewerZ() >> 4;
        final int regions = lists.getNumRegions();
        int lines = 0;
        for (int r = 0; r < regions; r++) {
            final ChunkRenderList list = lists.getRegion(r);
            final RenderRegion region = list.getRegion();
            final byte[] ids = list.getSectionsWithGeometry();
            final int count = list.getSectionsWithGeometryCount();
            for (int k = 0; k < count; k++) {
                final int id = ids[k] & 0xFF;
                final int sx = region.getChunkX() + LocalSectionIndex.unpackX(id);
                final int sy = region.getChunkY() + LocalSectionIndex.unpackY(id);
                final int sz = region.getChunkZ() + LocalSectionIndex.unpackZ(id);
                if (Math.abs(sx - camX) > RANGE || Math.abs(sy - camY) > RANGE || Math.abs(sz - camZ) > RANGE) continue;
                final RenderSection section = region.getSection(id);
                if (section == null) continue;
                final long data = section.getVisibilityData();
                final double x0 = sx << 4;
                final double y0 = sy << 4;
                final double z0 = sz << 4;
                final double ox = x0 + 8.0;
                final double oy = y0 + 8.0;
                final double oz = z0 + 8.0;
                boolean disconnected = false;
                for (int from = 0; from < GraphDirection.COUNT; from++) {
                    final int reachable = VisibilityEncoding.getConnections(data, GraphDirectionSet.of(from));
                    for (int to = from + 1; to < GraphDirection.COUNT; to++) {
                        if (GraphDirectionSet.contains(reachable, to)) continue;
                        if (lines >= MAX_LINES) return;
                        lines++;
                        disconnected = true;
                        mesh.line(LineWidth.THIN,
                            ox + GraphDirection.x(from) * 8.0, oy + GraphDirection.y(from) * 8.0, oz + GraphDirection.z(from) * 8.0,
                            ox + GraphDirection.x(to) * 8.0, oy + GraphDirection.y(to) * 8.0, oz + GraphDirection.z(to) * 8.0,
                            LINE);
                    }
                }
                if (disconnected) {
                    mesh.fillBox(x0 + INSET, y0 + INSET, z0 + INSET, x0 + 16.0 - INSET, y0 + 16.0 - INSET, z0 + 16.0 - INSET, FILL);
                }
            }
        }
    }
}
