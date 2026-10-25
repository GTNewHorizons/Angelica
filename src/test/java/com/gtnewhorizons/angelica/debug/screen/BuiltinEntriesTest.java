package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinEntriesTest {

    private static final DebugRegistry REGISTRY = TestRegistry.main();

    @Test
    void idsAreStableBecauseProfilesPersistThem() {
        final List<String> expected = new ArrayList<>(List.of(
            "minecraft:game_version", "minecraft:fps", "minecraft:tps", "minecraft:player_position", "minecraft:player_section_position",
            "minecraft:player_speed", "minecraft:biome", "minecraft:light_levels", "minecraft:heightmap", "minecraft:chunk_render_stats",
            "minecraft:entity_render_stats", "minecraft:particle_render_stats", "minecraft:post_effect", "minecraft:chunk_source_stats", "minecraft:memory",
            "minecraft:system_specs", "minecraft:looking_at_block", "forge:brandings", "angelica:renderer", "angelica:renderer_verbose",
            "angelica:dynamic_lights", "angelica:fog", "angelica:animations", "angelica:other_left", "angelica:other_right", "angelica:help",
            "minecraft:3d_crosshair", "minecraft:entity_hitboxes", "angelica:chunk_minimap"));
        if (AngelicaConfig.enableIris) expected.add("angelica:shaders");
        final Set<String> actual = new HashSet<>();
        for (int i = 0; i < REGISTRY.entryCount(); i++) actual.add(REGISTRY.entryAt(i).id);
        assertTrue(actual.containsAll(expected));
    }

    @Test
    void everyEntryHasANameAndTooltipInTheEnglishLangFile() throws IOException {
        final Set<String> keys = new HashSet<>();
        try (InputStream in = BuiltinEntriesTest.class.getResourceAsStream("/assets/angelica/lang/en_US.lang")) {
            assertNotNull(in);
            for (final String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                final int equals = line.indexOf('=');
                if (equals > 0 && equals < line.length() - 1) keys.add(line.substring(0, equals));
            }
        }
        for (int i = 0; i < REGISTRY.entryCount(); i++) {
            final DebugEntryDef def = REGISTRY.entryAt(i);
            assertTrue(keys.contains(def.name), def.name);
            assertTrue(keys.contains(def.name + ".tooltip"), def.name + ".tooltip");
        }
    }

    @Test
    void everyKindIsUniqueAndHasAFactory() {
        final Set<Integer> kinds = new HashSet<>();
        for (int i = 0; i < REGISTRY.entryCount(); i++) {
            final DebugEntryDef def = REGISTRY.entryAt(i);
            if (def.kind <= 0) continue;
            assertTrue(kinds.add(def.kind), def.id);
            if (def.category == DebugEntryCategory.SCREEN_TEXT) {
                assertNotNull(def.entryOrCreate(), def.id);
            } else {
                assertNotNull(def.toggleListener, def.id);
            }
        }
    }
}
