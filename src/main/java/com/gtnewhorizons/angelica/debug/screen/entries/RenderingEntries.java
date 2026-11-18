package com.gtnewhorizons.angelica.debug.screen.entries;

import com.gtnewhorizons.angelica.api.debug.DebugContext;
import com.gtnewhorizons.angelica.api.debug.DebugScreenDisplayer;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.debug.screen.DebugGroup;
import com.gtnewhorizons.angelica.proxy.ClientProxy;
import com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager;
import com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;

import static com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries.KIND_CHUNK_RENDER_STATS;
import static com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries.KIND_ENTITY_RENDER_STATS;
import static com.gtnewhorizons.angelica.debug.screen.DebugScreenEntries.KIND_PARTICLE_RENDER_STATS;

public final class RenderingEntries {

    private static final String SPRITES_PREFIX = "A: ";
    private static final String COLON = ": ";

    private RenderingEntries() {}

    public static void tps(DebugScreenDisplayer displayer, DebugContext context) {
        if (Minecraft.getMinecraft().getIntegratedServer() == null) return;
        displayer.fact(DebugGroup.RENDERING, "Server").value("Integrated").text(" @ ").value(ClientProxy.integratedTickTime(), 0).text(" ms ticks");
    }

    public static final class RenderStats implements DebugScreenEntry {

        private final Minecraft mc = Minecraft.getMinecraft();
        private final int kind;
        private String sampled;
        private int visibleChunks;
        private int totalSections;

        public RenderStats(int kind) {
            this.kind = kind;
        }

        @Override
        public void display(DebugScreenDisplayer displayer, DebugContext context) {
            if (mc.renderGlobal == null) return;
            switch (kind) {
                case KIND_CHUNK_RENDER_STATS -> displayChunks(displayer, context);
                case KIND_ENTITY_RENDER_STATS -> displayEntities(displayer, context);
                case KIND_PARTICLE_RENDER_STATS -> {
                    if (context.isSampleDue()) sampled = mc.effectRenderer.getStatistics();
                    displayer.fact(DebugGroup.RENDERING, "Particles").value(sampled);
                }
                default -> {
                    if (context.isSampleDue()) sampled = chunkCacheValue(mc.getWorldProviderName());
                    displayer.fact(DebugGroup.RENDERING, "Chunk Cache").value(sampled);
                }
            }
        }

        private void displayChunks(DebugScreenDisplayer displayer, DebugContext context) {
            final CeleritasWorldRenderer renderer = CeleritasWorldRenderer.getInstanceOrNull();
            if (renderer == null) return;
            if (context.isSampleDue()) {
                final AngelicaRenderSectionManager sections = renderer.getRenderSectionManager();
                visibleChunks = renderer.getVisibleChunkCount();
                totalSections = sections.getTotalSections();
                sampled = spritesValue(sections.getTickerDebugString());
            }
            displayer.fact(DebugGroup.RENDERING, "Chunks").value(visibleChunks).text('/').value(totalSections);
            displayer.fact(DebugGroup.RENDERING, "Render Distance").value(renderer.getEffectiveRenderDistance());
            if (sampled != null) displayer.fact(DebugGroup.RENDERING, "Animated Sprites").value(sampled);
        }

        private void displayEntities(DebugScreenDisplayer displayer, DebugContext context) {
            final RenderGlobal renderGlobal = mc.renderGlobal;
            final int total = context.world().loadedEntityList.size();
            final int rendered = renderGlobal.countEntitiesRendered;
            final int hidden = renderGlobal.countEntitiesHidden;
            displayer.fact(DebugGroup.RENDERING, "Entities").value(rendered).text('/').value(total).text(", ").value(hidden).text(" hidden, ").value(Math.max(0, total - hidden - rendered)).text(" culled");
            displayer.fact(DebugGroup.RENDERING, "Loaded Entities").value(total);
        }
    }

    public static final class PostEffect implements DebugScreenEntry {

        private final Minecraft mc = Minecraft.getMinecraft();
        private String name;

        @Override
        public void display(DebugScreenDisplayer displayer, DebugContext context) {
            if (context.isSampleDue()) {
                final EntityRenderer renderer = mc.entityRenderer;
                name = renderer != null && renderer.isShaderActive() ? renderer.getShaderGroup().getShaderGroupName() : null;
            }
            if (name != null) displayer.fact(DebugGroup.RENDERING, "Post Effect").value(name);
        }
    }

    static String spritesValue(String ticker) {
        if (ticker == null || !ticker.startsWith(SPRITES_PREFIX)) return null;
        return ticker.substring(SPRITES_PREFIX.length());
    }

    static String chunkCacheValue(String provider) {
        final int at = provider.indexOf(COLON);
        return at < 0 ? provider : provider.substring(at + COLON.length());
    }
}
