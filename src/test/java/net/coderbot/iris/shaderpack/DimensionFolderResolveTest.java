package net.coderbot.iris.shaderpack;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static net.coderbot.iris.shaderpack.ShaderPack.resolveDimensionFolder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DimensionFolderResolveTest {

    private static final Map<String, String> LEGACY_VANILLA = Map.of("Overworld", "world0", "Nether", "world-1", "The End", "world1");

    @Test
    void legacyPackWithWorld0SendsUnmappedDimensionsToWorld0() {
        final Set<String> folders = Set.of("world0", "world-1", "world1");
        assertEquals("world0", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Personal World 180", 180));
        assertEquals("world0", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Moon", -28));
        assertEquals("world-1", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Nether", -1));
        assertEquals("world1", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "The End", 1));
    }

    @Test
    void legacyWorldIdFolderBeatsWorld0Default() {
        final Set<String> folders = Set.of("world0", "world-1", "world1", "world7", "world4");
        assertEquals("world7", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Twilight Forest", 7));
        assertEquals("world4", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Other", 4));
        assertEquals("world0", resolveDimensionFolder(LEGACY_VANILLA, folders, "world0", "Personal World 180", 180));
    }

    @Test
    void legacyPackWithoutWorld0FallsBackToBase() {
        final Map<String, String> map = Map.of("Nether", "world-1", "The End", "world1");
        final Set<String> folders = Set.of("world-1", "world1");
        assertNull(resolveDimensionFolder(map, folders, null, "Moon", -28));
        assertEquals("world-1", resolveDimensionFolder(map, folders, null, "Nether", -1));
    }

    @Test
    void dimensionPropertiesWildcard() {
        final Map<String, String> map = Map.of("*", "world0", "Nether", "world-1", "The End", "world1");
        final Set<String> folders = Set.of("world0", "world-1", "world1");
        assertEquals("world0", resolveDimensionFolder(map, folders, null, "Personal World 180", 180));
        assertEquals("world-1", resolveDimensionFolder(map, folders, null, "Nether", -1));
    }

    @Test
    void exactMappingToFolderWithoutFilesFallsBackToBase() {
        final Map<String, String> map = Map.of("*", "world0", "Moon", "moon");
        final Set<String> folders = Set.of("world0");
        assertNull(resolveDimensionFolder(map, folders, null, "Moon", -28));
    }
}
