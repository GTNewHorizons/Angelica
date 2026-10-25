package com.gtnewhorizons.angelica.debug.screen;

import com.gtnewhorizons.angelica.api.debug.DebugEntryCategory;
import com.gtnewhorizons.angelica.api.debug.DebugEntryHandle;
import com.gtnewhorizons.angelica.api.debug.DebugEntrySpec;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntry;
import com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.debug.ChunkDebugMinimap;
import com.gtnewhorizons.angelica.debug.screen.entries.AngelicaEntries;
import com.gtnewhorizons.angelica.debug.screen.entries.AngelicaRendererEntry;
import com.gtnewhorizons.angelica.debug.screen.entries.AngelicaShadersEntry;
import com.gtnewhorizons.angelica.debug.screen.entries.ForgeBrandingsEntry;
import com.gtnewhorizons.angelica.debug.screen.entries.HeaderEntries;
import com.gtnewhorizons.angelica.debug.screen.entries.LookingAtBlockEntry;
import com.gtnewhorizons.angelica.debug.screen.entries.MemoryEntry;
import com.gtnewhorizons.angelica.debug.screen.entries.PositionEntries;
import com.gtnewhorizons.angelica.debug.screen.entries.RenderingEntries;
import com.gtnewhorizons.angelica.debug.screen.entries.SystemSpecsEntry;
import net.minecraft.client.renderer.entity.RenderManager;

import static com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus.ALWAYS_ON;
import static com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus.IN_OVERLAY;
import static com.gtnewhorizons.angelica.api.debug.DebugScreenEntryStatus.NEVER;

public final class DebugScreenEntries {

    public static final int KIND_GAME_VERSION = 1;
    public static final int KIND_FPS = 2;
    public static final int KIND_TPS = 3;
    public static final int KIND_PLAYER_POSITION = 4;
    public static final int KIND_PLAYER_SECTION_POSITION = 5;
    public static final int KIND_PLAYER_SPEED = 6;
    public static final int KIND_BIOME = 7;
    public static final int KIND_LIGHT_LEVELS = 8;
    public static final int KIND_HEIGHTMAP = 9;
    public static final int KIND_CHUNK_RENDER_STATS = 10;
    public static final int KIND_ENTITY_RENDER_STATS = 11;
    public static final int KIND_PARTICLE_RENDER_STATS = 12;
    public static final int KIND_CHUNK_SOURCE_STATS = 13;
    public static final int KIND_MEMORY = 14;
    public static final int KIND_SYSTEM_SPECS = 15;
    public static final int KIND_LOOKING_AT_BLOCK = 16;
    public static final int KIND_FORGE_BRANDINGS = 17;
    public static final int KIND_ANGELICA_RENDERER = 18;
    public static final int KIND_ANGELICA_SHADERS = 20;
    public static final int KIND_ANGELICA_DYNAMIC_LIGHTS = 21;
    public static final int KIND_ANGELICA_FOG = 22;
    public static final int KIND_ANGELICA_ANIMATIONS = 23;
    public static final int KIND_HELP = 26;
    public static final int KIND_ENTITY_HITBOXES = 28;
    public static final int KIND_ANGELICA_CHUNK_MINIMAP = 38;
    public static final int KIND_POST_EFFECT = 39;

    private static final String NAME_KEY_PREFIX = "angelica.debug.entry.";

    public static final DebugEntryDef GAME_VERSION = add(text("minecraft:game_version", IN_OVERLAY, NEVER), KIND_GAME_VERSION, false);
    public static final DebugEntryDef FPS = add(text("minecraft:fps", IN_OVERLAY, ALWAYS_ON), KIND_FPS, false);
    public static final DebugEntryDef TPS = add(text("minecraft:tps", IN_OVERLAY, IN_OVERLAY), KIND_TPS, true);
    public static final DebugEntryDef PLAYER_POSITION = add(text("minecraft:player_position", IN_OVERLAY, NEVER), KIND_PLAYER_POSITION, false);
    public static final DebugEntryDef PLAYER_SECTION_POSITION = add(text("minecraft:player_section_position", IN_OVERLAY, NEVER), KIND_PLAYER_SECTION_POSITION, false);
    public static final DebugEntryDef PLAYER_SPEED = add(text("minecraft:player_speed", IN_OVERLAY, NEVER), KIND_PLAYER_SPEED, false);
    public static final DebugEntryDef BIOME = add(text("minecraft:biome", IN_OVERLAY, NEVER), KIND_BIOME, false);
    public static final DebugEntryDef LIGHT_LEVELS = add(text("minecraft:light_levels", IN_OVERLAY, NEVER), KIND_LIGHT_LEVELS, false);
    public static final DebugEntryDef HEIGHTMAP = add(text("minecraft:heightmap", IN_OVERLAY, NEVER), KIND_HEIGHTMAP, false);
    public static final DebugEntryDef CHUNK_RENDER_STATS = add(text("minecraft:chunk_render_stats", IN_OVERLAY, NEVER), KIND_CHUNK_RENDER_STATS, false);
    public static final DebugEntryDef ENTITY_RENDER_STATS = add(text("minecraft:entity_render_stats", IN_OVERLAY, NEVER), KIND_ENTITY_RENDER_STATS, false);
    public static final DebugEntryDef PARTICLE_RENDER_STATS = add(text("minecraft:particle_render_stats", IN_OVERLAY, NEVER), KIND_PARTICLE_RENDER_STATS, false);
    public static final DebugEntryDef POST_EFFECT = add(text("minecraft:post_effect", IN_OVERLAY, NEVER), KIND_POST_EFFECT, false);
    public static final DebugEntryDef CHUNK_SOURCE_STATS = add(text("minecraft:chunk_source_stats", IN_OVERLAY, NEVER), KIND_CHUNK_SOURCE_STATS, false);
    public static final DebugEntryDef MEMORY = add(text("minecraft:memory", IN_OVERLAY, IN_OVERLAY), KIND_MEMORY, false);
    public static final DebugEntryDef SYSTEM_SPECS = add(text("minecraft:system_specs", IN_OVERLAY, NEVER), KIND_SYSTEM_SPECS, false);
    public static final DebugEntryDef LOOKING_AT_BLOCK = add(text("minecraft:looking_at_block", IN_OVERLAY, NEVER), KIND_LOOKING_AT_BLOCK, true);
    public static final DebugEntryDef FORGE_BRANDINGS = add(text("forge:brandings", IN_OVERLAY, NEVER), KIND_FORGE_BRANDINGS, false);
    public static final DebugEntryDef ANGELICA_RENDERER = add(text("angelica:renderer", IN_OVERLAY, IN_OVERLAY), KIND_ANGELICA_RENDERER, true);
    public static final DebugEntryDef ANGELICA_RENDERER_VERBOSE = add(gate("angelica:renderer_verbose", NEVER, NEVER).supportsAlways(false), 0, false);

    static {
        if (AngelicaConfig.enableIris) {
            add(text("angelica:shaders", IN_OVERLAY, NEVER), KIND_ANGELICA_SHADERS, true);
        }
    }

    public static final DebugEntryDef ANGELICA_DYNAMIC_LIGHTS = add(text("angelica:dynamic_lights", IN_OVERLAY, NEVER), KIND_ANGELICA_DYNAMIC_LIGHTS, true);
    public static final DebugEntryDef ANGELICA_FOG = add(text("angelica:fog", NEVER, NEVER), KIND_ANGELICA_FOG, true);
    public static final DebugEntryDef ANGELICA_ANIMATIONS = add(text("angelica:animations", IN_OVERLAY, NEVER), KIND_ANGELICA_ANIMATIONS, true);
    public static final DebugEntryDef OTHER_LEFT = add(gate("angelica:other_left", IN_OVERLAY, NEVER).supportsAlways(false), 0, false);
    public static final DebugEntryDef OTHER_RIGHT = add(gate("angelica:other_right", IN_OVERLAY, NEVER).supportsAlways(false), 0, false);
    public static final DebugEntryDef HELP = add(text("angelica:help", IN_OVERLAY, NEVER).supportsAlways(false), KIND_HELP, false);
    public static final DebugEntryDef THREE_DIMENSIONAL_CROSSHAIR = add(renderer("minecraft:3d_crosshair", IN_OVERLAY, NEVER), 0, false);
    public static final DebugEntryDef ENTITY_HITBOXES = add(renderer("minecraft:entity_hitboxes", NEVER, NEVER).toggle(DebugScreenEntries::onToggle), KIND_ENTITY_HITBOXES, false);
    public static final DebugEntryDef ANGELICA_CHUNK_MINIMAP = add(renderer("angelica:chunk_minimap", NEVER, NEVER).toggle(DebugScreenEntries::onToggle), KIND_ANGELICA_CHUNK_MINIMAP, false);

    private DebugScreenEntries() {}

    static void bootstrap() {}

    private static DebugScreenEntry create(DebugEntryHandle entry) {
        final DebugEntryDef def = (DebugEntryDef) entry;
        return switch (def.kind) {
            case KIND_GAME_VERSION -> HeaderEntries::gameVersion;
            case KIND_FPS -> HeaderEntries::fps;
            case KIND_TPS -> RenderingEntries::tps;
            case KIND_PLAYER_POSITION -> new PositionEntries.PlayerPosition();
            case KIND_PLAYER_SECTION_POSITION -> PositionEntries::sectionPosition;
            case KIND_PLAYER_SPEED -> PositionEntries::speed;
            case KIND_BIOME -> new PositionEntries.Biome();
            case KIND_LIGHT_LEVELS -> PositionEntries::lightLevels;
            case KIND_HEIGHTMAP -> PositionEntries::heightmap;
            case KIND_CHUNK_RENDER_STATS, KIND_ENTITY_RENDER_STATS, KIND_PARTICLE_RENDER_STATS, KIND_CHUNK_SOURCE_STATS -> new RenderingEntries.RenderStats(def.kind);
            case KIND_POST_EFFECT -> new RenderingEntries.PostEffect();
            case KIND_MEMORY -> new MemoryEntry();
            case KIND_SYSTEM_SPECS -> new SystemSpecsEntry();
            case KIND_LOOKING_AT_BLOCK -> new LookingAtBlockEntry();
            case KIND_FORGE_BRANDINGS -> new ForgeBrandingsEntry();
            case KIND_ANGELICA_RENDERER -> new AngelicaRendererEntry();
            case KIND_ANGELICA_SHADERS -> new AngelicaShadersEntry();
            case KIND_ANGELICA_DYNAMIC_LIGHTS -> AngelicaEntries::dynamicLights;
            case KIND_ANGELICA_FOG -> AngelicaEntries::fog;
            case KIND_ANGELICA_ANIMATIONS -> AngelicaEntries::animations;
            case KIND_HELP -> new HeaderEntries.Help();
            default -> throw new IllegalArgumentException(def.id);
        };
    }

    private static void onToggle(DebugEntryHandle entry, boolean enabled) {
        switch (((DebugEntryDef) entry).kind) {
            case KIND_ENTITY_HITBOXES -> RenderManager.debugBoundingBox = enabled;
            case KIND_ANGELICA_CHUNK_MINIMAP -> {
                if (enabled) {
                    ChunkDebugMinimap.enable();
                } else {
                    ChunkDebugMinimap.disable();
                }
            }
            default -> {}
        }
    }

    private static DebugEntrySpec text(String id, DebugScreenEntryStatus defaultStatus, DebugScreenEntryStatus performanceStatus) {
        return new DebugEntrySpec(id, DebugEntryCategory.SCREEN_TEXT).defaults(defaultStatus, performanceStatus).text(DebugScreenEntries::create);
    }

    private static DebugEntrySpec gate(String id, DebugScreenEntryStatus defaultStatus, DebugScreenEntryStatus performanceStatus) {
        return new DebugEntrySpec(id, DebugEntryCategory.SCREEN_TEXT).defaults(defaultStatus, performanceStatus);
    }

    private static DebugEntrySpec renderer(String id, DebugScreenEntryStatus defaultStatus, DebugScreenEntryStatus performanceStatus) {
        return new DebugEntrySpec(id, DebugEntryCategory.RENDERER).defaults(defaultStatus, performanceStatus);
    }

    private static DebugEntryDef add(DebugEntrySpec spec, int kind, boolean flat) {
        return DebugRegistry.MAIN.registerBuiltin(spec.name(NAME_KEY_PREFIX + spec.getId().replace(':', '.')), kind, flat);
    }
}
