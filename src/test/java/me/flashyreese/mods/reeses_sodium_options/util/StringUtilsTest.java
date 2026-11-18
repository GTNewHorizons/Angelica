package me.flashyreese.mods.reeses_sodium_options.util;

import java.util.Collection;
import me.jellysquid.mods.sodium.client.gui.options.OptionFlag;
import me.jellysquid.mods.sodium.client.gui.options.OptionImpact;
import me.jellysquid.mods.sodium.client.gui.options.control.Control;
import me.jellysquid.mods.sodium.client.gui.options.storage.OptionStorage;
import com.google.common.collect.ImmutableList;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.OptionPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest(name = "''{2}'' against ''{0}'' -> {3}")
    @CsvSource({
        "Max Framerate, Limits FPS, max, 1",
        "Max Framerate, Limits FPS, fraemrate, 1",
        "Max Framerate, Limits the maximum FPS (frames per second)., fps, 1",
        "Fog, Controls fog rendering, fps, 0",
        "Dynamic FOV, Controls field of view changes, fps, 0",
        "Max Framerate, Limits FPS. Reduces system load when multi-tasking., cloud, 0",
        "Clouds, Controls cloud rendering, cloud, 1",
        "Brightness, Controls gamma, bri, 1",
    })
    void singleOptionSearch(String name, String tooltip, String query, int expectedMatches) {
        var page = mockPage(mockOption(name, tooltip));
        assertEquals(expectedMatches, StringUtils.fuzzySearch(List.of(page), query, 2).size());
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
        @Override public OptionImpact getImpact() { return null; }
        @Override public Control<Object> getControl() { return null; }
        @Override public Object getValue() { return null; }
        @Override public void setValue(Object value) {}
        @Override public void reset() {}
        @Override public OptionStorage<?> getStorage() { return null; }
        @Override public boolean isAvailable() { return true; }
        @Override public boolean hasChanged() { return false; }
        @Override public void applyChanges() {}
        @Override public Collection<OptionFlag> getFlags() { return List.of(); }
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
