package com.gtnewhorizons.angelica.debug.screen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DebugScreenEntryList {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final int FORMAT_VERSION = 2;
    static final int MAX_REBUILD_PASSES = 8;
    private static final String KEY_VERSION = "version";
    private static final String KEY_PROFILE = "profile";
    private static final String KEY_CUSTOM = "custom";

    private final DebugRegistry registry;
    private final int count;
    private final DebugScreenEntryStatus[] statuses;
    private final DebugScreenEntryStatus[] overrides;
    private final DebugEntryDef[] enabledOrder;
    private final DebugEntryDef[] flipped;
    private final LinkedHashMap<String, JsonElement> unknownEntries = new LinkedHashMap<>();
    private final LinkedHashMap<String, JsonElement> unknownMembers = new LinkedHashMap<>();
    private int enabledCount;
    private int flipCount;
    private int overrideCount;
    private boolean anyTextEnabled;
    private boolean anyTextAlwaysOn;
    private boolean overlayVisible;
    private boolean rebuilding;
    private boolean rebuildAgain;
    private DebugScreenProfile profile;
    private File file;
    private Runnable listener;
    private long version;

    public DebugScreenEntryList(DebugRegistry registry) {
        if (!registry.isFrozen()) throw new IllegalStateException("The debug screen registry must be frozen first");
        this.registry = registry;
        this.count = registry.entryCount();
        this.statuses = new DebugScreenEntryStatus[count];
        this.overrides = new DebugScreenEntryStatus[count];
        this.enabledOrder = new DebugEntryDef[count];
        this.flipped = new DebugEntryDef[count];
        resetToProfile(DebugScreenProfile.DEFAULT);
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setListener(Runnable listener) {
        this.listener = listener;
    }

    private static DebugScreenEntryStatus clamp(DebugEntryDef def, DebugScreenEntryStatus status) {
        return status == DebugScreenEntryStatus.ALWAYS_ON && !def.supportsAlways ? DebugScreenEntryStatus.IN_OVERLAY : status;
    }

    private void resetToDefault() {
        unknownMembers.clear();
        resetToProfile(DebugScreenProfile.DEFAULT);
        rebuildCurrentList();
    }

    public void load() {
        unknownEntries.clear();
        unknownMembers.clear();
        if (file == null) {
            resetToDefault();
            return;
        }
        if (!file.exists()) {
            resetToDefault();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            final JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) throw new IllegalStateException("Expected a JSON object");
            applyJson(root.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Failed to load debug screen profile from {}, resetting to default", file, e);
            resetToDefault();
            quarantine();
            save();
            return;
        }
        rebuildCurrentList();
    }

    private void quarantine() {
        final Path source = file.toPath();
        try {
            Files.move(source, source.resolveSibling(file.getName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to set aside the unreadable debug screen profile {}", file, e);
        }
    }

    private static DebugScreenEntryStatus statusOf(JsonElement value) {
        return DebugScreenEntryStatus.bySerializedName(stringOf(value));
    }

    private static String stringOf(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private void applyJson(JsonObject json) {
        final JsonElement customElement = json.get(KEY_CUSTOM);
        if (customElement != null && !customElement.isJsonObject()) throw new IllegalStateException("Expected custom to be an object");
        final JsonObject custom = customElement == null ? null : customElement.getAsJsonObject();
        for (final Map.Entry<String, JsonElement> member : json.entrySet()) {
            final String key = member.getKey();
            if (KEY_VERSION.equals(key) || KEY_PROFILE.equals(key) || KEY_CUSTOM.equals(key)) continue;
            unknownMembers.put(key, member.getValue());
        }
        final JsonElement profileElement = json.get(KEY_PROFILE);
        if (profileElement != null || custom == null) {
            final DebugScreenProfile selected = DebugScreenProfile.bySerializedName(stringOf(profileElement));
            resetToProfile(selected == null ? DebugScreenProfile.DEFAULT : selected);
            return;
        }
        for (final Map.Entry<String, JsonElement> member : custom.entrySet()) {
            if (registry.entryById(member.getKey()) == null) unknownEntries.put(member.getKey(), member.getValue());
        }
        for (int i = 0; i < count; i++) {
            final DebugEntryDef def = registry.entryAt(i);
            final DebugScreenEntryStatus status = statusOf(custom.get(def.id));
            statuses[i] = clamp(def, status == null ? def.defaultStatus : status);
        }
        reattachProfile();
    }

    public void save() {
        if (file == null) return;
        final JsonObject json = new JsonObject();
        json.addProperty(KEY_VERSION, FORMAT_VERSION);
        if (profile != null) {
            json.addProperty(KEY_PROFILE, profile.serializedName);
        } else {
            final JsonObject custom = new JsonObject();
            for (int i = 0; i < count; i++) {
                final DebugEntryDef def = registry.entryAt(i);
                if (statuses[i] != clamp(def, def.defaultStatus)) custom.addProperty(def.id, statuses[i].serializedName);
            }
            for (final Map.Entry<String, JsonElement> member : unknownEntries.entrySet()) {
                custom.add(member.getKey(), member.getValue());
            }
            json.add(KEY_CUSTOM, custom);
        }
        for (final Map.Entry<String, JsonElement> member : unknownMembers.entrySet()) {
            json.add(member.getKey(), member.getValue());
        }
        final Path target = file.toPath();
        final Path temp = target.resolveSibling(file.getName() + ".tmp");
        try {
            final File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.write(temp, GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save debug screen profile to {}", file, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
            }
        }
    }

    private void resetToProfile(DebugScreenProfile profile) {
        this.profile = profile;
        unknownEntries.clear();
        dropOverrides();
        for (int i = 0; i < count; i++) {
            final DebugEntryDef def = registry.entryAt(i);
            statuses[i] = clamp(def, def.profileStatus(profile));
        }
    }

    public void loadProfile(DebugScreenProfile profile) {
        resetToProfile(profile);
        rebuildCurrentList();
    }

    public boolean isUsingProfile(DebugScreenProfile profile) {
        return this.profile == profile;
    }

    public DebugScreenEntryStatus getStatus(DebugEntryDef def) {
        return statuses[def.index];
    }

    public DebugScreenEntryStatus effectiveStatus(DebugEntryDef def) {
        final DebugScreenEntryStatus override = overrides[def.index];
        return override == null ? statuses[def.index] : override;
    }

    public void setStatus(DebugEntryDef def, DebugScreenEntryStatus status) {
        final int index = def.index;
        statuses[index] = clamp(def, status);
        if (overrides[index] != null) {
            overrides[index] = null;
            overrideCount--;
        }
        reattachProfile();
        rebuildCurrentList();
        save();
    }

    private void reattachProfile() {
        profile = matchingProfile();
        if (profile != null) unknownEntries.clear();
    }

    private DebugScreenProfile matchingProfile() {
        for (final DebugScreenProfile candidate : DebugScreenProfile.VALUES) {
            if (matches(candidate)) return candidate;
        }
        return null;
    }

    private boolean matches(DebugScreenProfile candidate) {
        for (int i = 0; i < count; i++) {
            final DebugEntryDef def = registry.entryAt(i);
            if (statuses[i] != clamp(def, def.profileStatus(candidate))) return false;
        }
        return true;
    }

    public boolean toggleStatus(DebugEntryDef def) {
        if (def.category == DebugEntryCategory.RENDERER && def.supportsAlways) {
            overrideForSession(def, def.isEnabled() ? DebugScreenEntryStatus.NEVER : DebugScreenEntryStatus.ALWAYS_ON);
            return effectiveStatus(def) != DebugScreenEntryStatus.NEVER;
        }
        final DebugScreenEntryStatus next = switch (effectiveStatus(def)) {
            case ALWAYS_ON -> DebugScreenEntryStatus.NEVER;
            case IN_OVERLAY -> overlayVisible || !def.supportsAlways ? DebugScreenEntryStatus.NEVER : DebugScreenEntryStatus.ALWAYS_ON;
            default -> overlayVisible ? DebugScreenEntryStatus.IN_OVERLAY : DebugScreenEntryStatus.ALWAYS_ON;
        };
        overrideForSession(def, next);
        return effectiveStatus(def) != DebugScreenEntryStatus.NEVER;
    }

    public void overrideForSession(DebugEntryDef def, DebugScreenEntryStatus status) {
        final DebugScreenEntryStatus previous = overrides[def.index];
        final DebugScreenEntryStatus next = status == null ? null : clamp(def, status);
        if (previous == next) return;
        if (previous == null) overrideCount++;
        if (next == null) overrideCount--;
        overrides[def.index] = next;
        rebuildCurrentList();
    }

    public void clearSessionOverrides() {
        if (overrideCount == 0) return;
        dropOverrides();
        rebuildCurrentList();
    }

    private void dropOverrides() {
        if (overrideCount == 0) return;
        Arrays.fill(overrides, 0, count, null);
        overrideCount = 0;
    }

    public int enabledCount() {
        return enabledCount;
    }

    public DebugEntryDef enabledAt(int index) {
        return enabledOrder[index];
    }

    public boolean anyTextEnabled() {
        return anyTextEnabled;
    }

    public boolean anyTextAlwaysOn() {
        return anyTextAlwaysOn;
    }

    public boolean isOverlayVisible() {
        return overlayVisible;
    }

    public void setOverlayVisible(boolean visible) {
        if (overlayVisible != visible) {
            overlayVisible = visible;
            rebuildCurrentList();
        }
    }

    public long version() {
        return version;
    }

    public void refresh() {
        if (registry.consumeFailures()) rebuildCurrentList();
    }

    public void rebuildCurrentList() {
        if (rebuilding) {
            rebuildAgain = true;
            return;
        }
        rebuilding = true;
        try {
            int passes = 0;
            do {
                rebuildAgain = false;
                rebuildPass();
            } while (rebuildAgain && ++passes < MAX_REBUILD_PASSES);
            if (rebuildAgain) failUnsettledListeners();
        } finally {
            rebuilding = false;
        }
    }

    private void failUnsettledListeners() {
        rebuildAgain = false;
        LOGGER.error("Debug screen toggle listeners kept changing entry statuses after {} passes; failing the {} entries that flipped last", MAX_REBUILD_PASSES, flipCount);
        final IllegalStateException cause = new IllegalStateException("Toggle listener did not settle within " + MAX_REBUILD_PASSES + " rebuild passes");
        for (int i = 0; i < flipCount; i++) {
            registry.fail(flipped[i], cause);
        }
    }

    private void rebuildPass() {
        Arrays.fill(flipped, 0, flipCount, null);
        enabledCount = 0;
        anyTextEnabled = false;
        anyTextAlwaysOn = false;
        int flips = 0;
        for (int i = 0; i < count; i++) {
            final DebugEntryDef def = registry.entryAt(i);
            final DebugScreenEntryStatus status = effectiveStatus(def);
            final boolean always = !def.failed && status == DebugScreenEntryStatus.ALWAYS_ON;
            final boolean on = always || !def.failed && overlayVisible && status == DebugScreenEntryStatus.IN_OVERLAY;
            def.alwaysOn = always;
            if (def.enabled != on) {
                def.enabled = on;
                if (def.toggleListener != null) flipped[flips++] = def;
            }
            if (on) {
                enabledOrder[enabledCount++] = def;
                if (def.isScreenText()) {
                    anyTextEnabled = true;
                    anyTextAlwaysOn |= always;
                }
            }
        }
        version++;
        flipCount = flips;
        for (int i = 0; i < flips; i++) {
            final DebugEntryDef def = flipped[i];
            try {
                def.toggleListener.onEnabledChanged(def, def.enabled);
            } catch (RuntimeException | LinkageError e) {
                registry.fail(def, e);
            }
        }
        if (listener != null) listener.run();
    }
}
