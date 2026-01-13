package me.flashyreese.mods.reeses_sodium_options.util;

import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    // Helper to create a mock option with name and tooltip
    private static Option<?> mockOption(String name, String tooltip) {
        return new MockOption(name, tooltip);
    }

    private static OptionPage mockPage(Option<?>... options) {
        return new MockOptionPage(ImmutableList.copyOf(options));
    }

    @Test
    void testExactMatch() {
        var page = mockPage(mockOption("Max Framerate", "Limits FPS"));
        var results = StringUtils.fuzzySearch(List.of(page), "max", 2);
        assertEquals(1, results.size());
    }

    @Test
    void testFuzzyMatchWithTypo() {
        var page = mockPage(mockOption("Max Framerate", "Limits FPS"));
        // "fraemrate" is a typo for "framerate" - should match
        var results = StringUtils.fuzzySearch(List.of(page), "fraemrate", 2);
        assertEquals(1, results.size(), "Should match 'framerate' with typo 'fraemrate'");
    }

    @Test
    void testFpsMatchesMaxFramerateViaTooltip() {
        var page = mockPage(mockOption("Max Framerate", "Limits the maximum FPS (frames per second)."));
        var results = StringUtils.fuzzySearch(List.of(page), "fps", 2);
        assertEquals(1, results.size(), "Should match 'Max Framerate' when searching 'fps' via tooltip");
    }

    @Test
    void testFpsDoesNotMatchFog() {
        var page = mockPage(mockOption("Fog", "Controls fog rendering"));
        var results = StringUtils.fuzzySearch(List.of(page), "fps", 2);
        assertEquals(0, results.size(), "Should NOT match 'Fog' when searching 'fps'");
    }

    @Test
    void testFpsDoesNotMatchFov() {
        var page = mockPage(mockOption("Dynamic FOV", "Controls field of view changes"));
        var results = StringUtils.fuzzySearch(List.of(page), "fps", 2);
        assertEquals(0, results.size(), "Should NOT match 'Dynamic FOV' when searching 'fps'");
    }

    @Test
    void testCloudDoesNotMatchMaxFramerate() {
        var page = mockPage(mockOption("Max Framerate", "Limits FPS. Reduces system load when multi-tasking."));
        var results = StringUtils.fuzzySearch(List.of(page), "cloud", 2);
        assertEquals(0, results.size(), "Should NOT match 'Max Framerate' when searching 'cloud'");
    }

    @Test
    void testCloudMatchesClouds() {
        var page = mockPage(mockOption("Clouds", "Controls cloud rendering"));
        var results = StringUtils.fuzzySearch(List.of(page), "cloud", 2);
        assertEquals(1, results.size(), "Should match 'Clouds' when searching 'cloud'");
    }

    @Test
    void testPrefixMatch() {
        var page = mockPage(mockOption("Brightness", "Controls gamma"));
        var results = StringUtils.fuzzySearch(List.of(page), "bri", 2);
        assertEquals(1, results.size(), "Should match 'Brightness' with prefix 'bri'");
    }

    @Test
    void testMultiWordSearch() {
        var page = mockPage(
            mockOption("Max Framerate", "Limits FPS"),
            mockOption("Render Distance", "How far to render")
        );
        var results = StringUtils.fuzzySearch(List.of(page), "max frame", 2);
        assertEquals(1, results.size(), "Should match only 'Max Framerate' with multi-word search");
    }

    // Simple mock implementations for testing
    private static class MockOption implements Option<Object> {
        private final String name;
        private final String tooltip;

        MockOption(String name, String tooltip) {
            this.name = name;
            this.tooltip = tooltip;
        }

        @Override public String getName() { return name; }
        @Override public String getTooltip() { return tooltip; }
        @Override public me.jellysquid.mods.sodium.client.gui.options.OptionImpact getImpact() { return null; }
        @Override public me.jellysquid.mods.sodium.client.gui.options.control.Control<Object> getControl() { return null; }
        @Override public Object getValue() { return null; }
        @Override public void setValue(Object value) {}
        @Override public void reset() {}
        @Override public me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage<?> getStorage() { return null; }
        @Override public boolean isAvailable() { return true; }
        @Override public boolean hasChanged() { return false; }
        @Override public void applyChanges() {}
        @Override public java.util.Collection<me.jellysquid.mods.sodium.client.gui.options.OptionFlag> getFlags() { return List.of(); }
    }

    private static class MockOptionPage extends OptionPage {
        private final ImmutableList<Option<?>> options;

        MockOptionPage(ImmutableList<Option<?>> options) {
            super("Test", ImmutableList.of());
            this.options = options;
        }

        @Override
        public ImmutableList<Option<?>> getOptions() {
            return options;
        }
    }
}
