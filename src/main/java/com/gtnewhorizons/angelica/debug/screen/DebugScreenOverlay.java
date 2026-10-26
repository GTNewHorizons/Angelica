package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.AngelicaMod;
import com.gtnewhorizons.angelica.client.font.BatchingFontRenderer;
import com.gtnewhorizons.angelica.client.font.GlyphSettings;
import com.gtnewhorizons.angelica.client.font.TextMesh;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.F3Graph;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import com.gtnewhorizons.angelica.mixins.interfaces.FontRendererAccessor;
import com.gtnewhorizons.angelica.mixins.interfaces.IGameSettingsExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;

public final class DebugScreenOverlay {

    public static final DebugScreenOverlay INSTANCE = new DebugScreenOverlay();

    public enum Mode { OVERLAY, ALWAYS_ON, FLAT }

    private static final int COLUMN_BUDGET_INSET = 8;
    private static final int FORGE_ROW_HEIGHT = 10;
    private static final int CHROME = -1;
    private static final Tracy.ZoneId Z_ENTRIES = Tracy.zoneId("f3Entries", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_MEASURE = Tracy.zoneId("f3Measure", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_DRAW = Tracy.zoneId("f3Draw", Tracy.COLOR_CLIENT);
    private static final Tracy.ZoneId Z_CAPTURE = Tracy.zoneId("f3Capture", Tracy.COLOR_CLIENT);

    private final DebugRegistry registry = DebugRegistry.MAIN;
    private final TextMesh chrome = new TextMesh();
    private final ForgeLineFilter.Sink externalSink = this::appendExternal;
    private final StringBuilder dump = new StringBuilder();
    private GlyphSettings textSettings;
    private GlyphSettings frameSettings;

    private int groupCount = -1;
    private ForgeLineFilter filter;
    private GroupedDebugDisplayer displayer;
    private FlatDebugDisplayer flat;
    private DebugLayout layout;
    private TextMesh[] meshes;
    private boolean[] groupDirty;
    private boolean[] placed;
    private int[] placedX;
    private int[] placedY;
    private int placedCount;

    private DebugText.Batched text;
    private FontRenderer textFont;
    private boolean textUnicode;

    private long titlesTranslatedAt = -1L;
    private int lastTick = Integer.MIN_VALUE;
    private long lastVersion = -1L;
    private Mode lastMode;
    private int lastHeight = -1;
    private boolean chromeDirty = true;

    private boolean scaled;
    private int scale;
    private int gameScale;
    private int screenWidth;

    private boolean ran;
    private boolean skipped;
    private Mode lastDraw;

    private DebugScreenOverlay() {}

    public void afterDebugBlock(boolean ran, boolean skipped) {
        this.ran = ran;
        this.skipped = skipped;
    }

    public boolean debugBlockRan() {
        return ran;
    }

    private void ensureSized() {
        if (groupCount >= 0) return;
        final int n = registry.groupCount();
        filter = new ForgeLineFilter(registry);
        displayer = new GroupedDebugDisplayer(registry);
        flat = new FlatDebugDisplayer(registry);
        layout = new DebugLayout(registry);
        meshes = new TextMesh[n];
        for (int g = 0; g < n; g++) meshes[g] = new TextMesh();
        groupDirty = new boolean[n];
        placed = new boolean[n];
        placedX = new int[n];
        placedY = new int[n];
        groupCount = n;
    }

    public void addFlatLines(Minecraft mc, ArrayList<String> left, ArrayList<String> right) {
        final DebugScreenEntryList entries = DebugScreen.ENTRIES;
        if (entries == null) return;
        ensureSized();
        flat.rebuild(mc, DebugScreen.clientTick(), entries.version());
        flat.addTo(left, right);
        lastDraw = Mode.FLAT;
    }

    public boolean onTextEvent(RenderGameOverlayEvent.Text event, FontRenderer font, int width, int height, boolean canceled) {
        if (canceled) return true;
        final DebugScreenEntryList entries = DebugScreen.ENTRIES;
        if (entries == null || !DebugScreen.isGroupedActive()) return canceled;
        if (!(font instanceof FontRendererAccessor)) {
            AngelicaMod.LOGGER.error("Grouped debug screen needs the batching font renderer on the HUD font; using the classic layout for the rest of this session");
            DebugScreen.disableGrouped();
            return skipped;
        }
        final Mode mode = DebugScreen.isOverlayVisible() && ran ? Mode.OVERLAY : Mode.ALWAYS_ON;
        lastDraw = mode;
        ensureSized();
        if (mode == Mode.ALWAYS_ON && !entries.anyTextAlwaysOn()) {
            clearPlacement();
            return canceled;
        }
        try {
            prepare(mode, Minecraft.getMinecraft(), event, font, width, height, entries);
            draw();
        } catch (RuntimeException | LinkageError e) {
            AngelicaMod.LOGGER.error("Grouped debug screen failed; using the classic layout for the rest of this session", e);
            DebugScreen.disableGrouped();
            return skipped;
        }
        return mode == Mode.OVERLAY;
    }

    private void clearPlacement() {
        if (placedCount == 0) return;
        placedCount = 0;
        Arrays.fill(placed, false);
    }

    private void markAllDirty() {
        chromeDirty = true;
        Arrays.fill(groupDirty, true);
    }

    private void prepare(Mode mode, Minecraft mc, RenderGameOverlayEvent.Text event, FontRenderer font, int width, int height, DebugScreenEntryList entries) {
        final int gameScale = event.resolution.getScaleFactor();
        final boolean even = DebugGuiScale.evenForUnicode(mc);
        final int scale = DebugGuiScale.resolve(AngelicaConfig.debugGuiScale, gameScale, mc.displayWidth, mc.displayHeight, even);
        final boolean scaled = scale > 0 && scale < gameScale;
        final int screenWidth = scaled ? width * gameScale / scale : width;
        final int screenHeight = scaled ? height * gameScale / scale : height;
        final boolean sizeChanged = screenWidth != this.screenWidth || screenHeight != lastHeight || scale != this.scale || gameScale != this.gameScale;
        this.scaled = scaled;
        this.scale = scale;
        this.gameScale = gameScale;
        this.screenWidth = screenWidth;
        if (sizeChanged) {
            lastHeight = screenHeight;
            releaseSizeHolds();
            markAllDirty();
        }
        final boolean fontChanged = selectText(font);
        if (fontChanged) markAllDirty();
        final boolean titlesChanged = refreshTitles(fontChanged);
        final boolean refreshed = refreshContent(mc, mode, event, entries);
        if (refreshed || fontChanged || titlesChanged || sizeChanged) {
            if (Tracy.ENABLED) Tracy.beginZone(Z_MEASURE);
            final int flags;
            try {
                flags = measureAll(screenWidth / 2 - COLUMN_BUDGET_INSET, fontChanged);
            } finally {
                if (Tracy.ENABLED) Tracy.endZone();
            }
            if ((flags & DebugGroupContents.SIZE) != 0) chromeDirty = true;
        }

        fillHeights();
        if (mode == Mode.OVERLAY) {
            int leftMax = screenHeight;
            int rightMax = screenHeight;
            if (((IGameSettingsExt) mc.gameSettings).angelica$showFpsGraph()) {
                int strip = F3Graph.reservedHeight(font);
                if (scaled) strip = strip * gameScale / scale;
                leftMax -= strip;
                if (mc.isSingleplayer()) rightMax -= strip;
            }
            layout.assign(DebugLayout.MARGIN_TOP, leftMax, rightMax);
        } else {
            final int forgeRows = event.left.size();
            int forgeBlock = forgeRows > 0 ? forgeRows * FORGE_ROW_HEIGHT + DebugLayout.MARGIN_TOP : 0;
            if (scaled) forgeBlock = forgeBlock * gameScale / scale;
            layout.assignSingleColumn(DebugGroup.LEFT, DebugLayout.MARGIN_TOP + forgeBlock, screenHeight);
        }
        comparePlacement(screenWidth);
    }

    private void comparePlacement(int screenWidth) {
        int count = 0;
        for (int g = 0; g < groupCount; g++) {
            final boolean p = layout.side[g] >= 0 && layout.height[g] > 0;
            int x = 0;
            int y = 0;
            if (p) {
                x = panelLeft(displayer.contents(g), layout.side[g], screenWidth);
                y = layout.top[g];
                count++;
            }
            if (p != placed[g] || x != placedX[g] || y != placedY[g]) {
                placed[g] = p;
                placedX[g] = x;
                placedY[g] = y;
                chromeDirty = true;
                groupDirty[g] = true;
            }
        }
        placedCount = count;
    }

    private boolean selectText(FontRenderer font) {
        final boolean unicode = font.getUnicodeFlag();
        final GlyphSettings settings = GlyphSettings.current();
        frameSettings = settings;
        if (text != null && font == textFont && unicode == textUnicode && settings.sameMetricsAs(textSettings)) {
            return false;
        }
        if (text == null || font != textFont) {
            text = new DebugText.Batched(font, ((FontRendererAccessor) font).angelica$getBatcher());
            textFont = font;
        }
        textUnicode = unicode;
        textSettings = settings;
        return true;
    }

    private boolean refreshTitles(boolean fontChanged) {
        final long translationUpdate = StatCollector.getLastTranslationUpdateTimeInMilliseconds();
        if (!fontChanged && translationUpdate == titlesTranslatedAt) return false;
        titlesTranslatedAt = translationUpdate;
        for (int g = 0; g < groupCount; g++) {
            final DebugGroup group = registry.groupAt(g);
            final DebugGroupContents contents = displayer.contents(g);
            if (!group.hasTitle()) {
                contents.setTitle("", 0);
                continue;
            }
            final String title = StatCollector.translateToLocal(group.titleKey);
            contents.setTitle(title, text.width(title, 0, title.length()));
        }
        return true;
    }

    private boolean refreshContent(Minecraft mc, Mode mode, RenderGameOverlayEvent.Text event, DebugScreenEntryList entries) {
        final int tick = DebugScreen.clientTick();
        final long version = entries.version();
        if (tick == lastTick && version == lastVersion && mode == lastMode) return false;
        if (mode != lastMode) {
            releaseSizeHolds();
        }
        lastTick = tick;
        lastVersion = version;
        lastMode = mode;
        if (Tracy.ENABLED) Tracy.beginZone(Z_ENTRIES);
        try {
            displayer.reset();
            DebugScreen.runEntries(displayer, mc, version, null, mode == Mode.ALWAYS_ON);
            if (mode == Mode.OVERLAY) filter.sample(event.left, event.right, externalSink);
        } finally {
            if (Tracy.ENABLED) Tracy.endZone();
        }
        return true;
    }

    private void releaseSizeHolds() {
        for (int g = 0; g < groupCount; g++) displayer.contents(g).releaseSizeHold();
    }

    private void appendExternal(int group, String source, int start, String name) {
        final DebugGroupContents contents = displayer.contents(group);
        final DebugGroupContents.Slot slot = name != null ? contents.addFact(name) : contents.addLine();
        if (source != null) slot.text.append(source, start, source.length());
    }

    private int measureAll(int lineBudget, boolean force) {
        final int tick = DebugScreen.clientTick();
        int all = 0;
        for (int g = 0; g < groupCount; g++) {
            final int flags = displayer.contents(g).measure(text, lineBudget, force, tick);
            if ((flags & DebugGroupContents.TEXT) != 0) groupDirty[g] = true;
            all |= flags;
        }
        return all;
    }

    private void fillHeights() {
        for (int g = 0; g < groupCount; g++) {
            final DebugGroupContents contents = displayer.contents(g);
            layout.height[g] = contents.isEmpty() ? 0 : contents.fullHeight();
        }
    }

    private static int panelLeft(DebugGroupContents contents, int side, int screenWidth) {
        return side == DebugGroup.LEFT
            ? DebugGroupContents.MARGIN
            : screenWidth - DebugGroupContents.MARGIN - contents.fullWidth();
    }

    private void draw() {
        if (placedCount == 0) return;
        if (Tracy.ENABLED) Tracy.beginZone(Z_DRAW);
        if (scaled) {
            GLStateManager.glPushMatrix();
            final float factor = scale / (float) gameScale;
            GLStateManager.glScalef(factor, factor, 1.0f);
        }
        try {
            GLStateManager.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            text.batcher.beginBatch();
            try {
                drawChrome();
                drawGroups();
            } finally {
                text.batcher.endBatch();
            }
        } finally {
            if (scaled) GLStateManager.glPopMatrix();
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private void drawChrome() {
        text.beginRects();
        try {
            if (!chromeDirty && text.batcher.replay(chrome, frameSettings)) return;
            capture(chrome, CHROME);
            chromeDirty = false;
        } finally {
            text.endRects();
        }
    }

    private void drawGroups() {
        final BatchingFontRenderer batcher = text.batcher;
        for (int g = 0; g < groupCount; g++) {
            if (!placed[g]) continue;
            final TextMesh mesh = meshes[g];
            if (!groupDirty[g] && batcher.replay(mesh, frameSettings)) continue;
            capture(mesh, g);
            groupDirty[g] = false;
        }
    }

    private void capture(TextMesh mesh, int group) {
        final BatchingFontRenderer batcher = text.batcher;
        if (Tracy.ENABLED) Tracy.beginZone(Z_CAPTURE);
        batcher.beginCapture(mesh);
        boolean ok = false;
        try {
            if (group == CHROME) {
                emitChrome();
            } else {
                displayer.contents(group).drawText(text, placedX[group], placedY[group]);
            }
            ok = true;
        } finally {
            if (ok) {
                batcher.endCapture();
            } else {
                batcher.abortCapture();
            }
            if (Tracy.ENABLED) Tracy.endZone();
        }
    }

    private void emitChrome() {
        for (int g = 0; g < groupCount; g++) {
            if (!placed[g]) continue;
            displayer.contents(g).emitRects(text, placedX[g], placedY[g], layout.side[g], screenWidth);
        }
    }

    public void describe(Logger log, int shot) {
        if (DebugScreen.ENTRIES == null) return;
        ensureSized();
        final Mode mode = lastDraw != null ? lastDraw : DebugScreen.isGroupedActive() ? Mode.ALWAYS_ON : Mode.FLAT;
        final int groups = mode != Mode.FLAT ? placedCount
            : (flat.lineCount(DebugGroup.LEFT) > 0 ? 1 : 0) + (flat.lineCount(DebugGroup.RIGHT) > 0 ? 1 : 0);
        final StringBuilder out = begin("overlay", shot, null).append(" mode=").append(mode.name()).append(" groups=").append(groups).append(" renderers=");
        boolean first = true;
        for (int i = 0, n = registry.rendererCount(); i < n; i++) {
            final DebugEntryDef def = registry.rendererAt(i);
            if (!def.isEnabled()) continue;
            if (!first) out.append(',');
            out.append(def.id);
            first = false;
        }
        log.info(out.toString());
        if (mode == Mode.FLAT) {
            describeFlat(log, shot, DebugGroup.LEFT, "flat_left");
            describeFlat(log, shot, DebugGroup.RIGHT, "flat_right");
            return;
        }
        for (int g = 0; g < groupCount; g++) {
            if (!placed[g]) continue;
            final DebugGroupContents contents = displayer.contents(g);
            final String id = contents.group.id;
            log.info(begin("group", shot, id)
                .append(" side=").append(layout.side[g] == DebugGroup.LEFT ? 'L' : 'R')
                .append(" x=").append(placedX[g]).append(" y=").append(placedY[g])
                .append(" w=").append(contents.fullWidth()).append(" h=").append(contents.fullHeight())
                .append(" facts=").append(contents.factCount()).append(" lines=").append(contents.lineCount()).toString());
            for (int i = 0, n = contents.factCount(); i < n; i++) {
                final DebugGroupContents.Slot fact = contents.fact(i);
                DebugText.appendStripped(begin("line", shot, id).append(" text="), fact.name);
                dump.append(": ");
                DebugText.appendStripped(dump, fact.text);
                log.info(dump.toString());
            }
            for (int i = 0, n = contents.lineCount(); i < n; i++) {
                DebugText.appendStripped(begin("line", shot, id).append(" text="), contents.line(i).text);
                log.info(dump.toString());
            }
        }
    }

    private void describeFlat(Logger log, int shot, int side, String id) {
        final int n = flat.lineCount(side);
        if (n == 0) return;
        log.info(begin("group", shot, id).append(" side=").append(side == DebugGroup.LEFT ? 'L' : 'R').append(" x=0 y=0 w=0 h=0 facts=0 lines=").append(n).toString());
        for (int i = 0; i < n; i++) {
            final String line = flat.lineAt(side, i);
            DebugText.appendStripped(begin("line", shot, id).append(" text="), line == null ? "" : line);
            log.info(dump.toString());
        }
    }

    private StringBuilder begin(String kind, int shot, String id) {
        dump.setLength(0);
        dump.append("Flyby debug ").append(kind).append(" shot=").append(shot);
        return id == null ? dump : dump.append(" id=").append(id);
    }
}
