package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.Tags;
import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugFactLines;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.debug.screen.DebugScreen;
import com.gtnewhorizons.angelica.glsm.ffp.ShaderManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import com.gtnewhorizons.angelica.profiling.BailClassCounts;
import com.gtnewhorizons.angelica.profiling.RenderClassTimings;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import com.gtnewhorizons.angelica.rendering.tesr.ModelPartBatcher;
import com.gtnewhorizons.angelica.rendering.tesr.TesrBatchRenderer;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

public final class AngelicaRendererEntry implements DebugScreenEntry {

    private static final String BUFFERS_PREFIX = "G: ";
    private static final String BUFFERS_SPLIT = ", I: ";

    private final DebugFactLines facts = new DebugFactLines();

    @Override
    public void display(DebugScreenDisplayer displayer, DebugContext context) {
        final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
        if (renderer == null) return;
        if (context.isSampleDue()) rebuild(renderer);
        facts.emit(displayer, DebugGroup.ANGELICA);
    }

    private void rebuild(CeleritasWorldRenderer renderer) {
        facts.begin();
        facts.fact("Version", Tags.VERSION);
        facts.fact("Renderer", "Celeritas");
        facts.fact("Backend", RENDER_BACKEND.getName());
        for (final String line : renderer.getDebugStrings()) {
            addRendererLine(facts, line);
        }
        if (DebugScreen.isRendererVerbose()) rebuildVerbose();
        facts.end();
    }

    private void rebuildVerbose() {
        final ShaderManager sm = ShaderManager.getInstance();
        if (sm != null) {
            addIfPresent(sm.getDebugInfo());
        }
        addIfPresent(TessellatorStreamingDrawer.getDebugInfo());
        addIfPresent(TesrBatchRenderer.INSTANCE.getDebugSummaryLine());
        addIfPresent(RENDER_BACKEND.getTransferDebugInfo());
        for (final String line : TesrBatchRenderer.INSTANCE.getDebugDetailStrings()) {
            addRendererLine(facts, line);
        }
        addIfPresent(ModelPartBatcher.INSTANCE.getDebugString());
        if (Tracy.ENABLED) {
            addIfPresent(RenderClassTimings.TESR.label(), RenderClassTimings.TESR.debugValue());
            addIfPresent(RenderClassTimings.ENTITY.label(), RenderClassTimings.ENTITY.debugValue());
            addIfPresent(RenderClassTimings.SHADOW_ENTITY.label(), RenderClassTimings.SHADOW_ENTITY.debugValue());
            addIfPresent(BailClassCounts.MATERIAL.label(), BailClassCounts.MATERIAL.debugValue());
            addIfPresent(BailClassCounts.TEMPLATE.label(), BailClassCounts.TEMPLATE.debugValue());
            addIfPresent(BailClassCounts.PARTICLE_SPILL.label(), BailClassCounts.PARTICLE_SPILL.debugValue());
        }
    }

    private void addIfPresent(String line) {
        if (line != null && !line.isEmpty()) addRendererLine(facts, line);
    }

    private void addIfPresent(String label, String value) {
        if (value != null && !value.isEmpty()) facts.fact(label, value);
    }

    static void addRendererLine(DebugFactLines out, String line) {
        if (line.startsWith(BUFFERS_PREFIX)) {
            final int at = line.indexOf(BUFFERS_SPLIT);
            if (at > 0) {
                out.fact("Geometry", line.substring(BUFFERS_PREFIX.length(), at) + " MiB");
                out.fact("Index", line.substring(at + BUFFERS_SPLIT.length()));
                return;
            }
        }
        out.split(line);
    }
}
