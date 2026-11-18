package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugGroupHandle;
import com.gtnewhorizons.angelica.api.debug.DebugGroupSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public final class DebugRegistry {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final DebugGroup[] NO_GROUPS = new DebugGroup[0];
    private static final DebugEntryDef[] NO_ENTRIES = new DebugEntryDef[0];

    public static final DebugRegistry MAIN = new DebugRegistry();

    private final ArrayList<DebugGroup> openGroups = new ArrayList<>();
    private final ArrayList<DebugEntryDef> openEntries = new ArrayList<>();
    private final HashMap<String, DebugGroup> groupsById = new HashMap<>();
    private final HashMap<String, DebugEntryDef> entriesById = new HashMap<>();
    private DebugGroup[] groups = NO_GROUPS;
    private DebugEntryDef[] entries = NO_ENTRIES;
    private DebugEntryDef[] flat = NO_ENTRIES;
    private DebugEntryDef[] renderers = NO_ENTRIES;
    private int rendererSequence;
    private boolean frozen;
    private boolean failuresPending;

    static boolean isValidId(String id) {
        if (id == null) return false;
        final int colon = id.indexOf(':');
        final int length = id.length();
        if (colon <= 0 || colon == length - 1) return false;
        for (int i = 0; i < length; i++) {
            if (i == colon) continue;
            final char c = id.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-') continue;
            if (c == '/' && i > colon) continue;
            return false;
        }
        return true;
    }

    private void requireOpen() {
        if (frozen) throw new IllegalStateException("The debug screen registry is frozen; register before the first client tick");
    }

    public DebugGroup registerGroup(DebugGroupSpec spec) {
        requireOpen();
        final String id = spec.getId();
        if (!isValidId(id)) throw new IllegalArgumentException("Invalid debug group id: " + id);
        if (groupsById.containsKey(id)) throw new IllegalArgumentException("Duplicate debug group id: " + id);
        final int side = spec.getSide();
        if (side != DebugGroupHandle.LEFT && side != DebugGroupHandle.RIGHT) throw new IllegalArgumentException("Invalid side for debug group " + id + ": " + side);
        final DebugGroup group = new DebugGroup(id, spec.getTitleKey(), spec.getAccent(), side, spec.getOrder());
        openGroups.add(group);
        groupsById.put(id, group);
        return group;
    }

    public DebugEntryDef registerEntry(DebugEntrySpec spec) {
        return register(spec, 0, spec.getCategory() == DebugEntryCategory.SCREEN_TEXT);
    }

    DebugEntryDef registerBuiltin(DebugEntrySpec spec, int kind, boolean flat) {
        return register(spec, kind, flat);
    }

    private DebugEntryDef register(DebugEntrySpec spec, int kind, boolean isFlat) {
        requireOpen();
        final String id = spec.getId();
        if (!isValidId(id)) throw new IllegalArgumentException("Invalid debug entry id: " + id);
        if (entriesById.containsKey(id)) throw new IllegalArgumentException("Duplicate debug entry id: " + id);
        final DebugEntryCategory category = spec.getCategory();
        if (category == null || spec.getDefaultStatus() == null || spec.getPerformanceStatus() == null) {
            throw new IllegalArgumentException("Debug entry " + id + " needs a category and both profile statuses");
        }
        if (category == DebugEntryCategory.RENDERER && spec.getTextFactory() != null) {
            throw new IllegalArgumentException("Renderer debug entry " + id + " must not have a text factory");
        }
        final int rendererIndex = category == DebugEntryCategory.RENDERER ? rendererSequence++ : -1;
        final DebugEntryDef def = new DebugEntryDef(this, spec, openEntries.size(), kind, rendererIndex, isFlat);
        openEntries.add(def);
        entriesById.put(id, def);
        return def;
    }

    public void freeze() {
        if (frozen) return;
        frozen = true;
        final int groupCount = openGroups.size();
        final DebugGroup[] sorted = openGroups.toArray(new DebugGroup[groupCount]);
        Arrays.sort(sorted, Comparator.comparingInt((DebugGroup g) -> g.side).thenComparingInt(g -> g.order));
        for (int i = 0; i < groupCount; i++) {
            sorted[i].index = i;
        }
        groups = sorted;
        entries = openEntries.toArray(new DebugEntryDef[openEntries.size()]);
        int flatCount = 0;
        for (final DebugEntryDef def : entries) {
            if (isFlatText(def)) flatCount++;
        }
        flat = new DebugEntryDef[flatCount];
        renderers = new DebugEntryDef[rendererSequence];
        int f = 0;
        for (final DebugEntryDef def : entries) {
            if (isFlatText(def)) flat[f++] = def;
            if (def.rendererIndex >= 0) renderers[def.rendererIndex] = def;
        }
        openGroups.clear();
        openGroups.trimToSize();
        openEntries.clear();
        openEntries.trimToSize();
    }

    private static boolean isFlatText(DebugEntryDef def) {
        return def.flat && def.isScreenText();
    }

    public void failAll() {
        for (final DebugEntryDef def : entriesById.values()) def.failed = true;
    }

    public boolean hasNamespace(String namespace) {
        final String prefix = namespace + ':';
        for (final String id : groupsById.keySet()) {
            if (id.startsWith(prefix)) return true;
        }
        for (final String id : entriesById.keySet()) {
            if (id.startsWith(prefix)) return true;
        }
        return false;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public int entryCount() {
        return entries.length;
    }

    public DebugEntryDef entryAt(int index) {
        return entries[index];
    }

    public DebugEntryDef entryById(String id) {
        return entriesById.get(id);
    }

    public int groupCount() {
        return groups.length;
    }

    public DebugGroup groupAt(int index) {
        return groups[index];
    }

    public DebugGroup groupById(String id) {
        return groupsById.get(id);
    }

    public int flatCount() {
        return flat.length;
    }

    public DebugEntryDef flatAt(int index) {
        return flat[index];
    }

    public int rendererCount() {
        return renderers.length;
    }

    public DebugEntryDef rendererAt(int index) {
        return renderers[index];
    }

    public void fail(DebugEntryDef def, Throwable cause) {
        if (def.failed) return;
        def.failed = true;
        failuresPending = true;
        LOGGER.error("Debug screen entry {} failed and is disabled for the rest of this session", def.id, cause);
    }

    public boolean consumeFailures() {
        final boolean pending = failuresPending;
        failuresPending = false;
        return pending;
    }
}
