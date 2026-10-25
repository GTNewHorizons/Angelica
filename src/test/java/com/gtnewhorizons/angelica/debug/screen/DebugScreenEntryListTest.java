package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugScreenEntryListTest {

    private static final DebugScreenEntryStatus NEVER = DebugScreenEntryStatus.NEVER;
    private static final DebugScreenEntryStatus IN_OVERLAY = DebugScreenEntryStatus.IN_OVERLAY;
    private static final DebugScreenEntryStatus ALWAYS_ON = DebugScreenEntryStatus.ALWAYS_ON;

    private final DebugRegistry registry = new DebugRegistry();
    private final int[] toggles = new int[2];
    private DebugEntryDef gameVersion;
    private DebugEntryDef fps;
    private DebugEntryDef tps;
    private DebugEntryDef memory;
    private DebugEntryDef help;
    private DebugEntryDef hitboxes;
    private DebugScreenEntryList list;

    @TempDir
    File dir;

    @BeforeEach
    void setUp() {
        gameVersion = registry.registerEntry(TestRegistry.text("minecraft:game_version"));
        fps = registry.registerEntry(TestRegistry.text("minecraft:fps").text(entry -> null).defaults(IN_OVERLAY, ALWAYS_ON));
        tps = registry.registerEntry(TestRegistry.text("minecraft:tps").text(entry -> null).defaults(IN_OVERLAY, IN_OVERLAY));
        memory = registry.registerEntry(TestRegistry.text("minecraft:memory").defaults(ALWAYS_ON, NEVER));
        help = registry.registerEntry(TestRegistry.text("test:help").defaults(IN_OVERLAY, ALWAYS_ON).supportsAlways(false));
        hitboxes = registry.registerEntry(TestRegistry.renderer("minecraft:entity_hitboxes").toggle((handle, enabled) -> toggles[enabled ? 1 : 0]++));
        registry.freeze();
        list = new DebugScreenEntryList(registry);
    }

    private File file() {
        return new File(dir, "sub/debug-profile.json");
    }

    private File existingFile(String content) throws IOException {
        Files.createDirectories(file().getParentFile().toPath());
        Files.writeString(file().toPath(), content);
        return file();
    }

    private DebugScreenEntryList reload() {
        return reload(registry);
    }

    private DebugScreenEntryList reload(DebugRegistry source) {
        final DebugScreenEntryList other = new DebugScreenEntryList(source);
        other.setFile(file());
        other.load();
        return other;
    }

    private String read() throws IOException {
        return Files.readString(file().toPath()).replaceAll("\\s", "");
    }

    private String[] siblings() {
        final String[] names = file().getParentFile().list();
        Arrays.sort(names);
        return names;
    }

    @ParameterizedTest(name = "{0} {1} overlay={2}")
    @CsvSource({
        "fps, ALWAYS_ON, true, NEVER, false", "fps, ALWAYS_ON, false, NEVER, false", "fps, IN_OVERLAY, true, NEVER, false",
        "fps, IN_OVERLAY, false, ALWAYS_ON, true", "fps, NEVER, true, IN_OVERLAY, true", "fps, NEVER, false, ALWAYS_ON, true",
        "help, NEVER, false, IN_OVERLAY, true", "help, IN_OVERLAY, false, NEVER, false",
        "hitboxes, NEVER, true, ALWAYS_ON, true", "hitboxes, NEVER, false, ALWAYS_ON, true", "hitboxes, ALWAYS_ON, true, NEVER, false",
        "hitboxes, IN_OVERLAY, true, NEVER, false", "hitboxes, IN_OVERLAY, false, ALWAYS_ON, true" })
    void toggleStatus(String entry, DebugScreenEntryStatus from, boolean overlayVisible, DebugScreenEntryStatus to, boolean result) {
        final DebugEntryDef def = switch (entry) {
            case "fps" -> fps;
            case "help" -> help;
            default -> hitboxes;
        };
        list.setOverlayVisible(overlayVisible);
        list.setStatus(def, from);
        list.setFile(file());
        assertEquals(result, list.toggleStatus(def));
        assertEquals(to, list.effectiveStatus(def));
        assertEquals(from, list.getStatus(def));
        assertFalse(file().exists());
    }

    @Test
    void enabledSetFollowsOverlayVisibilityAndTextFlags() {
        list.setStatus(fps, IN_OVERLAY);
        list.setStatus(tps, ALWAYS_ON);
        list.setStatus(memory, ALWAYS_ON);
        list.setOverlayVisible(false);
        assertFalse(fps.isEnabled());
        assertTrue(tps.isAlwaysOn());
        assertTrue(memory.isAlwaysOn());
        assertTrue(list.anyTextAlwaysOn());
        list.setStatus(tps, IN_OVERLAY);
        assertFalse(list.anyTextAlwaysOn());
        assertFalse(list.anyTextEnabled());
        list.setOverlayVisible(true);
        assertTrue(fps.isEnabled());
        assertTrue(list.anyTextEnabled());
        final long version = list.version();
        list.setOverlayVisible(true);
        assertEquals(version, list.version());
    }

    @Test
    void toggleListenerFiresOncePerFlipAndMayChangeStatuses() {
        list.setStatus(hitboxes, IN_OVERLAY);
        assertEquals(0, toggles[1]);
        list.setOverlayVisible(true);
        list.setStatus(hitboxes, ALWAYS_ON);
        assertEquals(1, toggles[1]);
        list.setStatus(hitboxes, NEVER);
        assertEquals(1, toggles[0]);

        final DebugRegistry other = new DebugRegistry();
        final DebugScreenEntryList[] holder = new DebugScreenEntryList[1];
        final DebugEntryDef[] followers = new DebugEntryDef[2];
        final int[] flips = new int[1];
        final DebugEntryDef leader = other.registerEntry(TestRegistry.renderer("test:leader").toggle((handle, enabled) -> {
            if (!enabled) {
                holder[0].clearSessionOverrides();
                return;
            }
            for (final DebugEntryDef follower : followers) holder[0].overrideForSession(follower, ALWAYS_ON);
        }));
        final DebugEntryDef bystander = other.registerEntry(TestRegistry.renderer("test:bystander").toggle((handle, enabled) -> flips[0]++));
        followers[0] = other.registerEntry(TestRegistry.renderer("test:follower_a").toggle((handle, enabled) -> flips[0]++));
        followers[1] = other.registerEntry(TestRegistry.renderer("test:follower_b").toggle((handle, enabled) -> flips[0]++));
        other.freeze();
        holder[0] = new DebugScreenEntryList(other);
        holder[0].setStatus(leader, IN_OVERLAY);
        holder[0].setStatus(bystander, IN_OVERLAY);
        holder[0].setOverlayVisible(true);
        assertTrue(followers[0].isEnabled());
        assertTrue(followers[1].isEnabled());
        holder[0].setOverlayVisible(false);
        assertFalse(followers[0].isEnabled());
        assertFalse(followers[1].isEnabled());
        assertFalse(bystander.isEnabled());
        assertFalse(leader.isFailed());
        assertEquals(6, flips[0]);
    }

    @Test
    void listenerThatKeepsFlippingItsOwnStatusFailsInsteadOfHanging() {
        final DebugRegistry other = new DebugRegistry();
        final DebugScreenEntryList[] holder = new DebugScreenEntryList[1];
        final int[] flips = new int[1];
        final DebugEntryDef[] self = new DebugEntryDef[1];
        self[0] = other.registerEntry(TestRegistry.renderer("test:flipper").toggle((handle, enabled) -> holder[0].overrideForSession(self[0], enabled ? NEVER : ALWAYS_ON)));
        final DebugEntryDef bystander = other.registerEntry(TestRegistry.renderer("test:bystander").toggle((handle, enabled) -> flips[0]++));
        other.freeze();
        holder[0] = new DebugScreenEntryList(other);
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> holder[0].setStatus(self[0], ALWAYS_ON));
        assertTrue(self[0].isFailed());
        holder[0].refresh();
        holder[0].setStatus(bystander, ALWAYS_ON);
        assertFalse(self[0].isEnabled());
        assertFalse(bystander.isFailed());
        assertEquals(1, flips[0]);
    }

    @Test
    void loadAppliesSavedStatusesWithoutFlippingDefaultsFirst() throws IOException {
        final DebugRegistry other = new DebugRegistry();
        final int[] shown = new int[1];
        other.registerEntry(TestRegistry.renderer("test:shown_by_default").defaults(ALWAYS_ON, NEVER).toggle((handle, enabled) -> shown[0]++));
        other.freeze();
        existingFile("{\"version\":2,\"custom\":{\"test:shown_by_default\":\"never\"}}");
        reload(other);
        assertEquals(0, shown[0]);
    }

    @Test
    void failedEntryTellsItsListenerItIsHidden() {
        final DebugRegistry other = new DebugRegistry();
        final boolean[] shown = new boolean[1];
        final DebugEntryDef def = other.registerEntry(TestRegistry.text("test:failing").text(handle -> null).toggle((handle, enabled) -> shown[0] = enabled));
        other.freeze();
        final DebugScreenEntryList entries = new DebugScreenEntryList(other);
        entries.setStatus(def, ALWAYS_ON);
        assertTrue(shown[0]);
        other.fail(def, new IllegalStateException("boom"));
        entries.refresh();
        assertFalse(def.isEnabled());
        assertFalse(shown[0]);
    }

    @Test
    void sessionOverrideIsNeverSavedAndAlwaysIsClampedWhenUnsupported() throws IOException {
        list.setFile(file());
        list.save();
        final String saved = read();
        list.overrideForSession(gameVersion, ALWAYS_ON);
        list.overrideForSession(help, ALWAYS_ON);
        assertTrue(gameVersion.isEnabled());
        assertFalse(help.isAlwaysOn());
        assertTrue(list.isUsingProfile(DebugScreenProfile.DEFAULT));
        list.save();
        assertEquals(saved, read());
        list.setStatus(help, ALWAYS_ON);
        assertEquals(IN_OVERLAY, list.getStatus(help));
        assertEquals(saved, read());
        list.clearSessionOverrides();
        assertFalse(gameVersion.isEnabled());
        existingFile("{\"version\":2,\"custom\":{\"test:help\":\"alwaysOn\"}}");
        assertEquals(IN_OVERLAY, reload().getStatus(help));
        list.overrideForSession(gameVersion, ALWAYS_ON);
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        assertEquals(NEVER, list.effectiveStatus(gameVersion));
    }

    @Test
    void profileAndCustomStatusesRoundTrip() throws IOException {
        list.setFile(file());
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        assertFalse(file().exists());
        list.save();
        assertTrue(read().contains("\"profile\":\"performance\""));
        assertTrue(reload().isUsingProfile(DebugScreenProfile.PERFORMANCE));
        assertEquals(ALWAYS_ON, reload().getStatus(fps));

        list.setStatus(fps, NEVER);
        list.setStatus(tps, ALWAYS_ON);
        assertFalse(read().contains("profile"));
        final DebugScreenEntryList other = reload();
        assertFalse(other.isUsingProfile(DebugScreenProfile.PERFORMANCE));
        for (int i = 0; i < registry.entryCount(); i++) assertEquals(list.getStatus(registry.entryAt(i)), other.getStatus(registry.entryAt(i)));
        assertArrayEquals(new String[] { "debug-profile.json" }, siblings());
    }

    @Test
    void customSaveHoldsOnlyChangedEntriesAndUntouchedOnesFollowNewDefaults() throws IOException {
        list.setFile(file());
        list.setStatus(fps, NEVER);
        assertEquals("{\"version\":2,\"custom\":{\"minecraft:fps\":\"never\"}}", read());

        final DebugRegistry changed = new DebugRegistry();
        final DebugEntryDef changedFps = changed.registerEntry(TestRegistry.text("minecraft:fps").defaults(IN_OVERLAY, ALWAYS_ON));
        final DebugEntryDef changedMemory = changed.registerEntry(TestRegistry.text("minecraft:memory").defaults(NEVER, NEVER));
        changed.freeze();
        final DebugScreenEntryList loaded = reload(changed);
        assertEquals(NEVER, loaded.getStatus(changedFps));
        assertEquals(NEVER, loaded.getStatus(changedMemory));
    }

    @Test
    void settingAnEntryBackToItsProfileValueReattachesTheProfile() throws IOException {
        list.setFile(file());
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        list.setStatus(fps, NEVER);
        assertFalse(list.isUsingProfile(DebugScreenProfile.PERFORMANCE));
        list.setStatus(fps, ALWAYS_ON);
        assertTrue(list.isUsingProfile(DebugScreenProfile.PERFORMANCE));
        assertTrue(read().contains("\"profile\":\"performance\""));
    }

    @Test
    void missingFileIsWrittenWithTheDefaultProfile() throws IOException {
        list.setFile(file());
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        list.load();
        assertTrue(list.isUsingProfile(DebugScreenProfile.DEFAULT));
        assertTrue(read().contains("\"profile\":\"default\""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "this is { not json", "[1, 2]", "{\"version\":2,\"custom\":\"nope\",\"keep\":1}" })
    void corruptFileIsSetAsideAndRewritten(String content) throws IOException {
        existingFile(content);
        list.setFile(file());
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        list.load();
        assertTrue(list.isUsingProfile(DebugScreenProfile.DEFAULT));
        assertTrue(read().contains("\"default\""));
        assertFalse(read().contains("keep"));
        assertEquals(content, Files.readString(new File(file().getParentFile(), "debug-profile.json.corrupt").toPath()));
        assertArrayEquals(new String[] { "debug-profile.json", "debug-profile.json.corrupt" }, siblings());
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"version\":2,  \"profile\":\"bogus\"}", "{\"later\":true}", "{\"version\":2,\"custom\":{}}" })
    void unrecognizedButValidFileIsDefaultAndUntouched(String content) throws IOException {
        existingFile(content);
        list.setFile(file());
        list.loadProfile(DebugScreenProfile.PERFORMANCE);
        list.load();
        assertTrue(list.isUsingProfile(DebugScreenProfile.DEFAULT));
        assertEquals(content, Files.readString(file().toPath()));
        assertArrayEquals(new String[] { "debug-profile.json" }, siblings());
    }

    @Test
    void customEntriesMissingOrInvalidTakeTheirDefault() throws IOException {
        existingFile("{\"version\":2,\"custom\":{\"minecraft:fps\":\"alwaysOn\",\"minecraft:tps\":\"weird\",\"minecraft:game_version\":7}}");
        final DebugScreenEntryList loaded = reload();
        assertFalse(loaded.isUsingProfile(DebugScreenProfile.DEFAULT));
        assertEquals(ALWAYS_ON, loaded.getStatus(fps));
        assertEquals(IN_OVERLAY, loaded.getStatus(tps));
        assertEquals(IN_OVERLAY, loaded.getStatus(gameVersion));
        assertEquals(ALWAYS_ON, loaded.getStatus(memory));
        assertEquals(NEVER, loaded.getStatus(hitboxes));
    }

    @Test
    void unknownIdsAndMembersSurviveSaves() throws IOException {
        existingFile("{\"custom\":{\"gone:entry\":\"alwaysOn\",\"minecraft:fps\":\"never\",\"gone:odd\":{\"a\":[1,2]}},\"future\":{\"x\":1}}");
        final DebugScreenEntryList loaded = reload();
        assertEquals(NEVER, loaded.getStatus(fps));
        loaded.setStatus(tps, ALWAYS_ON);
        assertTrue(read().contains("\"gone:entry\":\"alwaysOn\""));
        assertTrue(read().contains("\"gone:odd\":{\"a\":[1,2]}"));
        assertTrue(read().contains("\"minecraft:tps\":\"alwaysOn\""));
        assertTrue(read().contains("\"future\":{\"x\":1}"));
        assertTrue(read().contains("\"version\":2"));
        loaded.loadProfile(DebugScreenProfile.PERFORMANCE);
        loaded.save();
        assertTrue(read().contains("\"future\":{\"x\":1}"));
        assertFalse(read().contains("gone:entry"));
        final DebugScreenEntryList again = reload();
        again.setStatus(fps, IN_OVERLAY);
        assertTrue(read().contains("\"future\":{\"x\":1}"));
    }
}
