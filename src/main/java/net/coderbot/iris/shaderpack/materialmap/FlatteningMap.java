package net.coderbot.iris.shaderpack.materialmap;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps modern (1.13+ post-flattening) block names to their 1.7.10 equivalents.
 *
 * In 1.13, Minecraft's "flattening" replaced numeric block IDs + metadata with separate
 * named blocks. For example, oak_planks/spruce_planks/birch_planks became distinct blocks,
 * where before they were all "planks" with different metadata values.
 *
 * Modern shader packs reference blocks by their post-flattening names in block.properties.
 * This map allows us to resolve those names to the correct 1.7.10 block + metadata.
 *
 * Only entries where the modern name differs from the 1.7.10 name are included.
 * Blocks with identical names across versions (e.g. diamond_ore, glass, obsidian)
 * resolve directly via the registry and don't need entries here.
 */
public class FlatteningMap {

	/** Name-only mappings: modern block name -> list of legacy block entries. */
	private static final Map<String, List<BlockEntry>> MODERN_TO_LEGACY = new HashMap<>();

	/** State property mappings: "blockname|property=value" -> list of legacy block entries. */
	private static final Map<String, List<BlockEntry>> STATE_MAPPINGS = new HashMap<>();

	private static final String[] COLORS = {
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	private static final String[] WOOD_TYPES = {
		"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"
	};

	static {
		// ==============
		// Simple renames
		// ==============

		// === Grass block (ID 2) ===
		rename("grass_block", "grass");

		// === Note block (ID 25) ===
		rename("note_block", "noteblock");

		// === Powered rail (ID 27) ===
		rename("powered_rail", "golden_rail");

		// === Cobweb (ID 30) ===
		rename("cobweb", "web");

		// === Dead bush (ID 32) ===
		rename("dead_bush", "deadbush");

		// === Moving piston (ID 36) ===
		rename("moving_piston", "piston_extension");

		// === Dandelion (ID 37) ===
		rename("dandelion", "yellow_flower");

		// === Bricks (ID 45) ===
		rename("bricks", "brick_block");

		// === Spawner (ID 52) ===
		rename("spawner", "mob_spawner");

		// === Oak door (ID 64) ===
		rename("oak_door", "wooden_door");

		// === Cobblestone stairs (ID 67) ===
		rename("cobblestone_stairs", "stone_stairs");

		// === Oak pressure plate (ID 72) ===
		rename("oak_pressure_plate", "wooden_pressure_plate");

		// === Snow block (ID 80) ===
		rename("snow_block", "snow");

		// === Sugar cane (ID 83) ===
		rename("sugar_cane", "reeds");

		// === Oak fence (ID 85) ===
		rename("oak_fence", "fence");

		// === Carved pumpkin (ID 86) ===
		rename("carved_pumpkin", "pumpkin");

		// === Nether portal (ID 90) ===
		rename("nether_portal", "portal");

		// === Jack-o-lantern (ID 91) ===
		rename("jack_o_lantern", "lit_pumpkin");

		// === Oak trapdoor (ID 96) ===
		rename("oak_trapdoor", "trapdoor");

		// === Melon (ID 103) ===
		rename("melon", "melon_block");

		// === Attached pumpkin stem (ID 104) ===
		rename("attached_pumpkin_stem", "pumpkin_stem");

		// === Attached melon stem (ID 105) ===
		rename("attached_melon_stem", "melon_stem");

		// === Oak fence gate (ID 107) ===
		rename("oak_fence_gate", "fence_gate");

		// === Lily pad (ID 111) ===
		rename("lily_pad", "waterlily");

		// === Nether bricks (ID 112) ===
		rename("nether_bricks", "nether_brick");

		// === Oak button (ID 143) ===
		rename("oak_button", "wooden_button");

		// === Nether quartz ore (ID 153) ===
		rename("nether_quartz_ore", "quartz_ore");

		// === Terracotta (ID 172) ===
		rename("terracotta", "hardened_clay");

		// === Torch variants (ID 50) ===
		// In 1.7.10, torch handles both floor and wall via metadata
		// In 1.13+, wall_torch is a separate block. Metas 1-4 = wall, 5 = floor.
		metas("wall_torch", "torch", 1, 2, 3, 4);

		// === Snow layers (ID 78) ===
		// Modern "snow" = layer block, but 1.7.10 "snow" = full snow block (ID 80).
		rename("snow", "snow_layer");

		// === Redstone wall torch (IDs 75, 76) ===
		// Wall metas only (1-4). Defaults to lit; lit=false state mapping handles unlit.
		metas("redstone_wall_torch", "redstone_torch", 1, 2, 3, 4);

		// === Signs (IDs 63, 68) ===
		// In 1.7.10 only one sign type exists. In 1.14+ signs got wood variants.
		rename("oak_sign",              "standing_sign");
		rename("oak_wall_sign",         "wall_sign");
		rename("spruce_sign",           "standing_sign");
		rename("spruce_wall_sign",      "wall_sign");
		rename("birch_sign",            "standing_sign");
		rename("birch_wall_sign",       "wall_sign");
		rename("jungle_sign",           "standing_sign");
		rename("jungle_wall_sign",      "wall_sign");
		rename("acacia_sign",           "standing_sign");
		rename("acacia_wall_sign",      "wall_sign");
		rename("dark_oak_sign",         "standing_sign");
		rename("dark_oak_wall_sign",    "wall_sign");

		// === Skull / Head blocks (ID 144) ===
		// In 1.7.10, skull type is stored in tile entity, not metadata.
		// All modern skull blocks map to the same 1.7.10 block.
		rename("skeleton_skull",                "skull");
		rename("skeleton_wall_skull",           "skull");
		rename("wither_skeleton_skull",         "skull");
		rename("wither_skeleton_wall_skull",    "skull");
		rename("zombie_head",                   "skull");
		rename("zombie_wall_head",              "skull");
		rename("player_head",                   "skull");
		rename("player_wall_head",              "skull");
		rename("creeper_head",                  "skull");
		rename("creeper_wall_head",             "skull");

		// ==========================================
		// Metadata variants (block split by flattening)
		// ==========================================

		// === Stone variants (ID 1) ===
		meta("granite",             "stone", 1);
		meta("polished_granite",    "stone", 2);
		meta("diorite",             "stone", 3);
		meta("polished_diorite",    "stone", 4);
		meta("andesite",            "stone", 5);
		meta("polished_andesite",   "stone", 6);

		// === Dirt variants (ID 3) ===
		meta("coarse_dirt", "dirt", 1);
		meta("podzol",      "dirt", 2);

		// === Planks (ID 5) ===
		meta("oak_planks",      "planks", 0);
		meta("spruce_planks",   "planks", 1);
		meta("birch_planks",    "planks", 2);
		meta("jungle_planks",   "planks", 3);
		meta("acacia_planks",   "planks", 4);
		meta("dark_oak_planks", "planks", 5);

		// === Saplings (ID 6) ===
		// Bit 3 = growth stage, include both stage 0 and stage 1 for each type
		metas("oak_sapling",        "sapling", 0, 8);
		metas("spruce_sapling",     "sapling", 1, 9);
		metas("birch_sapling",      "sapling", 2, 10);
		metas("jungle_sapling",     "sapling", 3, 11);
		metas("acacia_sapling",     "sapling", 4, 12);
		metas("dark_oak_sapling",   "sapling", 5, 13);

		// === Sand (ID 12) ===
		meta("red_sand", "sand", 1);

		// === Logs (ID 17) ===
		// Metadata: bits 0-1 = wood type, bits 2-3 = axis (0=Y, 1=X, 2=Z, 3=bark)
		logVariants("oak_log",      "oak_wood",     0);
		logVariants("spruce_log",   "spruce_wood",  1);
		logVariants("birch_log",    "birch_wood",   2);
		logVariants("jungle_log",   "jungle_wood",  3);

		// === Stripped logs (1.13+) ===
		// Don't exist in 1.7.10, map to regular for compatibility
		logVariants("stripped_oak_log",     "stripped_oak_wood",    0);
		logVariants("stripped_spruce_log",  "stripped_spruce_wood", 1);
		logVariants("stripped_birch_log",   "stripped_birch_wood",  2);
		logVariants("stripped_jungle_log",  "stripped_jungle_wood", 3);

		// === Logs2 (ID 162) ===
		// log2 only has 2 real types (acacia=0, dark_oak=1), but types 2 and 3 exist
		// in the world and render as acacia/dark_oak respectively. Include them all.
		log2Variants("acacia_log",              "acacia_wood",              0);
		log2Variants("dark_oak_log",            "dark_oak_wood",            1);
		log2Variants("stripped_acacia_log",     "stripped_acacia_wood",     0);
		log2Variants("stripped_dark_oak_log",   "stripped_dark_oak_wood",   1);

		// === Leaves (ID 18) ===
		// Metadata: bits 0-1 = type, bit 2 = no_decay, bit 3 = check_decay
		leafVariants("oak_leaves",      "leaves", 0);
		leafVariants("spruce_leaves",   "leaves", 1);
		leafVariants("birch_leaves",    "leaves", 2);
		leafVariants("jungle_leaves",   "leaves", 3);

		// === Leaves2 (ID 161) ===
		leafVariants("acacia_leaves",   "leaves2", 0);
		leafVariants("dark_oak_leaves", "leaves2", 1);

		// === Sponge (ID 19) ===
		meta("wet_sponge", "sponge", 1);

		// === Sandstone variants (ID 24) ===
		meta("chiseled_sandstone",  "sandstone", 1);
		meta("cut_sandstone",       "sandstone", 2);
		meta("smooth_sandstone",    "sandstone", 2);

		// === Beds (ID 26) ===
		// In 1.7.10, bed is one block. Metadata encodes facing/occupied/part, NOT color.
		// Color is not metadata-based (always red pre-1.12, tile entity in 1.12).
		for (String color : COLORS) {
			rename(color + "_bed", "bed");
		}

		// === Tall grass / fern (ID 31) ===
		// tallgrass: 0 = shrub, 1 = grass, 2 = fern
		meta("short_grass", "tallgrass", 1);   // 1.20.3+ name
		meta("fern",        "tallgrass", 2);

		// === Wool (ID 35) ===
		colorVariants("wool", "wool");

		// === Flowers (ID 38) ===
		meta("poppy",           "red_flower", 0);
		meta("blue_orchid",     "red_flower", 1);
		meta("allium",          "red_flower", 2);
		meta("azure_bluet",     "red_flower", 3);
		meta("red_tulip",       "red_flower", 4);
		meta("orange_tulip",    "red_flower", 5);
		meta("white_tulip",     "red_flower", 6);
		meta("pink_tulip",      "red_flower", 7);
		meta("oxeye_daisy",     "red_flower", 8);

		// === Stone slabs (ID 44) ===
		// Bit 3 = top half, bits 0-2 = slab type
		slabVariants("stone_slab",          "stone_slab", 0);
		slabVariants("sandstone_slab",      "stone_slab", 1);
		slabVariants("petrified_oak_slab",  "stone_slab", 2);  // Legacy wooden slab in the stone slab block
		slabVariants("cobblestone_slab",    "stone_slab", 3);
		slabVariants("brick_slab",          "stone_slab", 4);
		slabVariants("stone_brick_slab",    "stone_slab", 5);
		slabVariants("nether_brick_slab",   "stone_slab", 6);
		slabVariants("quartz_slab",         "stone_slab", 7);

		// === Wooden slabs (ID 126) ===
		slabVariants("oak_slab",        "wooden_slab", 0);
		slabVariants("spruce_slab",     "wooden_slab", 1);
		slabVariants("birch_slab",      "wooden_slab", 2);
		slabVariants("jungle_slab",     "wooden_slab", 3);
		slabVariants("acacia_slab",     "wooden_slab", 4);
		slabVariants("dark_oak_slab",   "wooden_slab", 5);

		// === Stained glass (ID 95) ===
		colorVariants("stained_glass", "stained_glass");

		// === Monster egg / Infested blocks (ID 97) ===
		meta("infested_stone",                  "monster_egg", 0);
		meta("infested_cobblestone",            "monster_egg", 1);
		meta("infested_stone_bricks",           "monster_egg", 2);
		meta("infested_mossy_stone_bricks",     "monster_egg", 3);
		meta("infested_cracked_stone_bricks",   "monster_egg", 4);
		meta("infested_chiseled_stone_bricks",  "monster_egg", 5);

		// === Stone bricks (ID 98) ===
		meta("stone_bricks",            "stonebrick", 0);
		meta("mossy_stone_bricks",      "stonebrick", 1);
		meta("cracked_stone_bricks",    "stonebrick", 2);
		meta("chiseled_stone_bricks",   "stonebrick", 3);

        // === Cauldron variants (ID 118) ===
        // In 1.17+, cauldron was split by contents. In 1.7.10: meta 0 = empty, 1-3 = water fill levels.
        meta("cauldron",            "cauldron", 0);
        metas("water_cauldron",     "cauldron", 1, 2, 3);

		// === Cobblestone wall variants (ID 139) ===
		// cobblestone_wall:0 resolves directly, only need the mossy variant
		meta("mossy_cobblestone_wall", "cobblestone_wall", 1);

		// === Flower pot (ID 140) ===
		// In 1.7.10, flower pot contents are in the tile entity.
		// In 1.13+, each potted plant is a separate block.
		rename("potted_oak_sapling",        "flower_pot");
		rename("potted_spruce_sapling",     "flower_pot");
		rename("potted_birch_sapling",      "flower_pot");
		rename("potted_jungle_sapling",     "flower_pot");
		rename("potted_acacia_sapling",     "flower_pot");
		rename("potted_dark_oak_sapling",   "flower_pot");
		rename("potted_fern",               "flower_pot");
		rename("potted_dandelion",          "flower_pot");
		rename("potted_poppy",              "flower_pot");
		rename("potted_blue_orchid",        "flower_pot");
		rename("potted_allium",             "flower_pot");
		rename("potted_azure_bluet",        "flower_pot");
		rename("potted_red_tulip",          "flower_pot");
		rename("potted_orange_tulip",       "flower_pot");
		rename("potted_white_tulip",        "flower_pot");
		rename("potted_pink_tulip",         "flower_pot");
		rename("potted_oxeye_daisy",        "flower_pot");
		rename("potted_red_mushroom",       "flower_pot");
		rename("potted_brown_mushroom",     "flower_pot");
		rename("potted_dead_bush",          "flower_pot");
		rename("potted_cactus",             "flower_pot");

		// === Anvil (ID 145) ===
		// Bits 0-1 = facing, bits 2-3 = damage level
		metas("anvil",          "anvil", 0, 1, 2, 3);
		metas("chipped_anvil",  "anvil", 4, 5, 6, 7);
		metas("damaged_anvil",  "anvil", 8, 9, 10, 11);

		// === Quartz block (ID 155) ===
		// :0 = default, :1 = chiseled, :2 = pillar (Y), :3 = pillar (X), :4 = pillar (Z)
		meta("chiseled_quartz_block",   "quartz_block", 1);
		metas("quartz_pillar",          "quartz_block", 2, 3, 4);
		// === Smooth quartz (1.14+) ===
		// No 1.7.10 equivalent; map to base quartz for compat
		meta("smooth_quartz",           "quartz_block", 0);

		// === Smooth stone (1.14+) ===
		// No 1.7.10 equivalent; closest visual match is double_stone_slab meta 8
		meta("smooth_stone", "double_stone_slab", 8);

		// === Stained terracotta (ID 159) ===
		colorVariants("terracotta", "stained_hardened_clay");

		// === Stained glass pane (ID 160) ===
		colorVariants("stained_glass_pane", "stained_glass_pane");

		// === Carpet (ID 171) ===
		colorVariants("carpet", "carpet");

		// === Double plant (ID 175) ===
		// Bottom half has type in meta 0-5; top half is always meta 8 (shared by all types).
		meta("sunflower",   "double_plant", 0);
		meta("lilac",       "double_plant", 1);
		meta("tall_grass",  "double_plant", 2);
		meta("large_fern",  "double_plant", 3);
		meta("rose_bush",   "double_plant", 4);
		meta("peony",       "double_plant", 5);

		// === Water / Lava (IDs 8-11) ===
		// In 1.13+, water/lava are single blocks. In 1.7.10, flowing variants are separate.
		// Map modern names to both still and flowing for full coverage.
		multi("water",
            entry("water"),
            entry("flowing_water"));
		multi("lava",
            entry("lava"),
            entry("flowing_lava"));

		// ==========================================
		// Blockstate property mappings
		// In modern MC, some 1.7.10 block pairs were merged into one block with a state property.
		// ==========================================

		// === Furnace (IDs 61, 62) ===
		state("furnace", "lit", "false",    entry("furnace"));
		state("furnace", "lit", "true",     entry("lit_furnace"));

		// === Redstone ore (IDs 73, 74) ===
		state("redstone_ore", "lit", "false",   entry("redstone_ore"));
		state("redstone_ore", "lit", "true",    entry("lit_redstone_ore"));

		// === Redstone lamp (IDs 123, 124) ===
		state("redstone_lamp", "lit", "false",  entry("redstone_lamp"));
		state("redstone_lamp", "lit", "true",   entry("lit_redstone_lamp"));

		// === Redstone torch (IDs 75, 76) ===
		state("redstone_torch",         "lit", "true",  entry("redstone_torch"));
		state("redstone_torch",         "lit", "false", entry("unlit_redstone_torch"));
		state("redstone_wall_torch",    "lit", "true",  entryMetas("redstone_torch",        1, 2, 3, 4));
		state("redstone_wall_torch",    "lit", "false", entryMetas("unlit_redstone_torch",  1, 2, 3, 4));

		// === Daylight detector (IDs 151, 178) ===
		state("daylight_detector", "inverted", "false", entry("daylight_detector"));
		state("daylight_detector", "inverted", "true",  entry("daylight_detector_inverted"));

		// === Repeater (IDs 93, 94) ===
		state("repeater", "powered", "false",   entry("unpowered_repeater"));
		state("repeater", "powered", "true",    entry("powered_repeater"));

		// === Comparator (IDs 149, 150) ===
		state("comparator", "powered", "false", entry("unpowered_comparator"));
		state("comparator", "powered", "true",  entry("powered_comparator"));

		// === Redstone wire (ID 55) ===
		// Modern: power=0-15. 1.7.10: meta 0-15 (same values, different syntax)
		for (int i = 0; i <= 15; i++) {
			state("redstone_wire", "power", String.valueOf(i), entryMetas("redstone_wire", i));
		}

		// === End portal frame (ID 120) ===
		// Modern: eye=true/false. 1.7.10: bit 2 of metadata (metas 4-7 have eye)
		state("end_portal_frame", "eye", "true",    entryMetas("end_portal_frame", 4, 5, 6, 7));
		state("end_portal_frame", "eye", "false",   entryMetas("end_portal_frame", 0, 1, 2, 3));

		// === Snow layers (ID 78) ===
		// Modern: snow:layers=1 through layers=8
		// 1.7.10: snow_layer with meta 0-7 (meta = layers - 1)
		state("snow", "layers", "1", entryMetas("snow_layer", 0));
		state("snow", "layers", "2", entryMetas("snow_layer", 1));
		state("snow", "layers", "3", entryMetas("snow_layer", 2));
		state("snow", "layers", "4", entryMetas("snow_layer", 3));
		state("snow", "layers", "5", entryMetas("snow_layer", 4));
		state("snow", "layers", "6", entryMetas("snow_layer", 5));
		state("snow", "layers", "7", entryMetas("snow_layer", 6));
		state("snow", "layers", "8", entryMetas("snow_layer", 7));

		// === Farmland moisture (ID 60) ===
		// Modern: moiste 0-6 = dry appearance, 7 = moist appearance
		// 1.7.10: meta 0 = dry, metas 1-7 = moist
		// Remap so shader visuals match the intended dry/moist distinction.
		state("farmland", "moisture", "0", entryMetas("farmland", 0));
		state("farmland", "moisture", "1", entryMetas("farmland", 0));
		state("farmland", "moisture", "2", entryMetas("farmland", 0));
		state("farmland", "moisture", "3", entryMetas("farmland", 0));
		state("farmland", "moisture", "4", entryMetas("farmland", 0));
		state("farmland", "moisture", "5", entryMetas("farmland", 0));
		state("farmland", "moisture", "6", entryMetas("farmland", 0));
		state("farmland", "moisture", "7", entryMetas("farmland", 1, 2, 3, 4, 5, 6, 7));

		// === Slabs: type=bottom/top/double (IDs 44, 126, 43, 125, 181, 182) ===
		// Bit 3 = top half for regular slabs. Double slabs are separate blocks.
		slabStates("stone_slab",            "stone_slab", 0,  "double_stone_slab", 0);
		slabStates("sandstone_slab",        "stone_slab", 1,  "double_stone_slab", 1);
		slabStates("petrified_oak_slab",    "stone_slab", 2,  "double_stone_slab", 2);
		slabStates("cobblestone_slab",      "stone_slab", 3,  "double_stone_slab", 3);
		slabStates("brick_slab",            "stone_slab", 4,  "double_stone_slab", 4);
		slabStates("stone_brick_slab",      "stone_slab", 5,  "double_stone_slab", 5);
		slabStates("nether_brick_slab",     "stone_slab", 6,  "double_stone_slab", 6);
		slabStates("quartz_slab",           "stone_slab", 7,  "double_stone_slab", 7);
		slabStates("oak_slab",              "wooden_slab", 0, "double_wooden_slab", 0);
		slabStates("spruce_slab",           "wooden_slab", 1, "double_wooden_slab", 1);
		slabStates("birch_slab",            "wooden_slab", 2, "double_wooden_slab", 2);
		slabStates("jungle_slab",           "wooden_slab", 3, "double_wooden_slab", 3);
		slabStates("acacia_slab",           "wooden_slab", 4, "double_wooden_slab", 4);
		slabStates("dark_oak_slab",         "wooden_slab", 5, "double_wooden_slab", 5);

		// === Mushroom blocks (IDs 99, 100) ===
		// In modern MC, mushroom_stem is separate.
		// In 1.7.10, metas 10,15 of both mushroom blocks are stem.
		// Cap metas are 0-9,11-14. We need these so modern "brown_mushroom_block"
		// doesn't claim stem metas that belong to "mushroom_stem".
		metas("brown_mushroom_block",   "brown_mushroom_block", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14);
		metas("red_mushroom_block",     "red_mushroom_block",   0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 12, 13, 14);

		// === Mushroom stem (IDs 99, 100) ===
		multi("mushroom_stem",
			entryMetas("brown_mushroom_block",  10, 15),
			entryMetas("red_mushroom_block",    10, 15));

		// === Log axis (IDs 17, 162) ===
		// 1.7.10: bits 0-1=type, bits 2-3=axis (0=Y, 1=X, 2=Z)
		logAxisStates("oak_log",                0);
		logAxisStates("spruce_log",             1);
		logAxisStates("birch_log",              2);
		logAxisStates("jungle_log",             3);
		logAxisStates("stripped_oak_log",       0);
		logAxisStates("stripped_spruce_log",    1);
		logAxisStates("stripped_birch_log",     2);
		logAxisStates("stripped_jungle_log",    3);
		log2AxisStates("acacia_log",            0);
		log2AxisStates("dark_oak_log",          1);
		log2AxisStates("stripped_acacia_log",   0);
		log2AxisStates("stripped_dark_oak_log", 1);

		// === Wood / bark blocks (IDs 17, 162) ===
		// All axes map to same meta (1.7.10 bark is all-sided)
		woodAxisStates("oak_wood",                  12);
		woodAxisStates("spruce_wood",               13);
		woodAxisStates("birch_wood",                14);
		woodAxisStates("jungle_wood",               15);
		woodAxisStates("stripped_oak_wood",         12);
		woodAxisStates("stripped_spruce_wood",      13);
		woodAxisStates("stripped_birch_wood",       14);
		woodAxisStates("stripped_jungle_wood",      15);
		wood2AxisStates("acacia_wood",              12, 14);
		wood2AxisStates("dark_oak_wood",            13, 15);
		wood2AxisStates("stripped_acacia_wood",     12, 14);
		wood2AxisStates("stripped_dark_oak_wood",   13, 15);

		// === Hay block axis (ID 170) ===
		state("hay_block", "axis", "y", entryMetas("hay_block", 0));
		state("hay_block", "axis", "x", entryMetas("hay_block", 4));
		state("hay_block", "axis", "z", entryMetas("hay_block", 8));

		// === Quartz pillar axis (ID 155) ===
		state("quartz_pillar", "axis", "y", entryMetas("quartz_block", 2));
		state("quartz_pillar", "axis", "x", entryMetas("quartz_block", 3));
		state("quartz_pillar", "axis", "z", entryMetas("quartz_block", 4));

		// === Stairs (various IDs) ===
		// 1.7.10: bits 0-1=facing (0=E,1=W,2=S,3=N), bit 2=upside_down
		stairStates("oak_stairs",           "oak_stairs");
		stairStates("cobblestone_stairs",   "stone_stairs");
		stairStates("brick_stairs",         "brick_stairs");
		stairStates("stone_brick_stairs",   "stone_brick_stairs");
		stairStates("nether_brick_stairs",  "nether_brick_stairs");
		stairStates("sandstone_stairs",     "sandstone_stairs");
		stairStates("spruce_stairs",        "spruce_stairs");
		stairStates("birch_stairs",         "birch_stairs");
		stairStates("jungle_stairs",        "jungle_stairs");
		stairStates("quartz_stairs",        "quartz_stairs");
		stairStates("acacia_stairs",        "acacia_stairs");
		stairStates("dark_oak_stairs",      "dark_oak_stairs");

		// === Doors (IDs 64, 71) ===
		// Lower half (meta 0-7): bits 0-1=facing (0=E,1=S,2=W,3=N), bit 2=open
		// Upper half (meta 8-11): bit 0=hinge, bit 1=powered
		doorStates("oak_door",  "wooden_door");
		doorStates("iron_door", "iron_door");

		// === Trapdoor (ID 96) ===
		// 1.7.10: bits 0-1=facing (0=S,1=N,2=E,3=W), bit 2=open, bit 3=top
		trapdoorStates();

		// === Fence gate (ID 107) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bit 2=open
		fenceGateStates();

		// === Pistons (IDs 29, 33) ===
		// 1.7.10: bits 0-2=facing (0=D,1=U,2=N,3=S,4=W,5=E), bit 3=extended
		sixDirectionalStates("piston",          "piston",           "extended", "false", "true");
		sixDirectionalStates("sticky_piston",   "sticky_piston",    "extended", "false", "true");

		// === Piston head (ID 34) ===
		// 1.7.10: bits 0-2=facing, bit 3=type (0=normal, 1=sticky)
		sixDirectionalStates("piston_head", "piston_head", "type", "normal", "sticky");

		// === Dispenser (ID 23), Dropper (ID 158) ===
		// 1.7.10: bits 0-2=facing, bit 3=triggered
		sixDirectionalStates("dispenser",   "dispenser",    "triggered", "false", "true");
		sixDirectionalStates("dropper",     "dropper",      "triggered", "false", "true");

		// === Hopper (ID 154) ===
		// 1.7.10: bits 0-2=facing (0=D,2=N,3=S,4=W,5=E; no up), bit 3=disabled(!enabled)
		state("hopper", "facing",  "down",  entryMetas("hopper", 0, 8));
		state("hopper", "facing",  "north", entryMetas("hopper", 2, 10));
		state("hopper", "facing",  "south", entryMetas("hopper", 3, 11));
		state("hopper", "facing",  "west",  entryMetas("hopper", 4, 12));
		state("hopper", "facing",  "east",  entryMetas("hopper", 5, 13));
		state("hopper", "enabled", "true",  entryMetas("hopper", 0, 2, 3, 4, 5));
		state("hopper", "enabled", "false", entryMetas("hopper", 8, 10, 11, 12, 13));

		// === Furnace facing (IDs 61, 62) ===
		// 1.7.10: meta 2=N, 3=S, 4=W, 5=E (same encoding for both furnace and lit_furnace)
		state("furnace", "facing", "north", entryMetas("furnace", 2), entryMetas("lit_furnace", 2));
		state("furnace", "facing", "south", entryMetas("furnace", 3), entryMetas("lit_furnace", 3));
		state("furnace", "facing", "west",  entryMetas("furnace", 4), entryMetas("lit_furnace", 4));
		state("furnace", "facing", "east",  entryMetas("furnace", 5), entryMetas("lit_furnace", 5));

		// === Chest (ID 54), Trapped chest (ID 146), Ender chest (ID 130) ===
		// 1.7.10: meta 2=N, 3=S, 4=W, 5=E
		containerFacingStates("chest",          "chest");
		containerFacingStates("trapped_chest",  "trapped_chest");
		containerFacingStates("ender_chest",    "ender_chest");

		// === Ladder (ID 65) ===
		containerFacingStates("ladder", "ladder");

		// === Lever (ID 69) ===
		// 1.7.10: 0=ceiling_EW, 1=wall_E, 2=wall_W, 3=wall_S, 4=wall_N,
		//         5=floor_NS, 6=floor_EW, 7=ceiling_NS; +8=powered
		state("lever", "face",      "floor",    entryMetas("lever", 5, 6, 13, 14));
		state("lever", "face",      "wall",     entryMetas("lever", 1, 2, 3, 4, 9, 10, 11, 12));
		state("lever", "face",      "ceiling",  entryMetas("lever", 0, 7, 8, 15));
		state("lever", "powered",   "false",    entryMetas("lever", 0, 1, 2, 3, 4, 5, 6, 7));
		state("lever", "powered",   "true",     entryMetas("lever", 8, 9, 10, 11, 12, 13, 14, 15));
		state("lever", "facing",    "east",     entryMetas("lever", 1, 6, 0, 9, 14, 8));
		state("lever", "facing",    "west",     entryMetas("lever", 2, 6, 0, 10, 14, 8));
		state("lever", "facing",    "south",    entryMetas("lever", 3, 5, 7, 11, 13, 15));
		state("lever", "facing",    "north",    entryMetas("lever", 4, 5, 7, 12, 13, 15));

		// === Buttons (IDs 77, 143) ===
		// 1.7.10: 0=ceiling, 1=wall_E, 2=wall_W, 3=wall_S, 4=wall_N, 5=floor; +8=powered
		buttonStates("stone_button",    "stone_button");
		buttonStates("oak_button",      "wooden_button");

		// === Torch wall facing (ID 50) ===
		// 1.7.10: 1=E, 2=W, 3=S, 4=N, 5=floor
		state("wall_torch", "facing", "east",  entryMetas("torch", 1));
		state("wall_torch", "facing", "west",  entryMetas("torch", 2));
		state("wall_torch", "facing", "south", entryMetas("torch", 3));
		state("wall_torch", "facing", "north", entryMetas("torch", 4));

		// === Redstone wall torch facing (IDs 75, 76) ===
		state("redstone_wall_torch", "facing", "east",
			entryMetas("redstone_torch", 1), entryMetas("unlit_redstone_torch", 1));
		state("redstone_wall_torch", "facing", "west",
			entryMetas("redstone_torch", 2), entryMetas("unlit_redstone_torch", 2));
		state("redstone_wall_torch", "facing", "south",
			entryMetas("redstone_torch", 3), entryMetas("unlit_redstone_torch", 3));
		state("redstone_wall_torch", "facing", "north",
			entryMetas("redstone_torch", 4), entryMetas("unlit_redstone_torch", 4));

		// === Repeater facing and delay (IDs 93, 94) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bits 2-3=delay (0-3, actual 1-4)
		state("repeater", "facing", "south",
			entryMetas("unpowered_repeater", 0, 4, 8, 12), entryMetas("powered_repeater", 0, 4, 8, 12));
		state("repeater", "facing", "west",
			entryMetas("unpowered_repeater", 1, 5, 9, 13), entryMetas("powered_repeater", 1, 5, 9, 13));
		state("repeater", "facing", "north",
			entryMetas("unpowered_repeater", 2, 6, 10, 14), entryMetas("powered_repeater", 2, 6, 10, 14));
		state("repeater", "facing", "east",
			entryMetas("unpowered_repeater", 3, 7, 11, 15), entryMetas("powered_repeater", 3, 7, 11, 15));
		state("repeater", "delay", "1",
			entryMetas("unpowered_repeater", 0, 1, 2, 3), entryMetas("powered_repeater", 0, 1, 2, 3));
		state("repeater", "delay", "2",
			entryMetas("unpowered_repeater", 4, 5, 6, 7), entryMetas("powered_repeater", 4, 5, 6, 7));
		state("repeater", "delay", "3",
			entryMetas("unpowered_repeater", 8, 9, 10, 11), entryMetas("powered_repeater", 8, 9, 10, 11));
		state("repeater", "delay", "4",
			entryMetas("unpowered_repeater", 12, 13, 14, 15), entryMetas("powered_repeater", 12, 13, 14, 15));

		// === Comparator facing and mode (IDs 149, 150) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bit 2=mode, bit 3=powered
		state("comparator", "facing", "south",
			entryMetas("unpowered_comparator", 0, 4, 8, 12), entryMetas("powered_comparator", 0, 4, 8, 12));
		state("comparator", "facing", "west",
			entryMetas("unpowered_comparator", 1, 5, 9, 13), entryMetas("powered_comparator", 1, 5, 9, 13));
		state("comparator", "facing", "north",
			entryMetas("unpowered_comparator", 2, 6, 10, 14), entryMetas("powered_comparator", 2, 6, 10, 14));
		state("comparator", "facing", "east",
			entryMetas("unpowered_comparator", 3, 7, 11, 15), entryMetas("powered_comparator", 3, 7, 11, 15));
		state("comparator", "mode", "compare",
			entryMetas("unpowered_comparator", 0, 1, 2, 3, 8, 9, 10, 11),
			entryMetas("powered_comparator", 0, 1, 2, 3, 8, 9, 10, 11));
		state("comparator", "mode", "subtract",
			entryMetas("unpowered_comparator", 4, 5, 6, 7, 12, 13, 14, 15),
			entryMetas("powered_comparator", 4, 5, 6, 7, 12, 13, 14, 15));

		// === End portal frame facing (ID 120) ===
		// Already has eye state; adding facing: bits 0-1 (0=S, 1=W, 2=N, 3=E)
		state("end_portal_frame", "facing", "south", entryMetas("end_portal_frame", 0, 4));
		state("end_portal_frame", "facing", "west",  entryMetas("end_portal_frame", 1, 5));
		state("end_portal_frame", "facing", "north", entryMetas("end_portal_frame", 2, 6));
		state("end_portal_frame", "facing", "east",  entryMetas("end_portal_frame", 3, 7));

		// === Beds (ID 26) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bit 2=occupied, bit 3=head part
		for (String color : COLORS) {
			bedStates(color + "_bed");
		}

		// === Rails ===
		// Normal rail (ID 66): shape 0-9 (flat, ascending, curved)
		state("rail", "shape", "north_south",       entryMetas("rail", 0));
		state("rail", "shape", "east_west",         entryMetas("rail", 1));
		state("rail", "shape", "ascending_east",    entryMetas("rail", 2));
		state("rail", "shape", "ascending_west",    entryMetas("rail", 3));
		state("rail", "shape", "ascending_north",   entryMetas("rail", 4));
		state("rail", "shape", "ascending_south",   entryMetas("rail", 5));
		state("rail", "shape", "north_east",        entryMetas("rail", 6));
		state("rail", "shape", "south_east",        entryMetas("rail", 7));
		state("rail", "shape", "south_west",        entryMetas("rail", 8));
		state("rail", "shape", "north_west",        entryMetas("rail", 9));

		// === Powered rail (ID 27), Detector rail (ID 28), Activator rail (ID 157) ===
		poweredRailStates("powered_rail",   "golden_rail");
		poweredRailStates("detector_rail",  "detector_rail");
		poweredRailStates("activator_rail", "activator_rail");

		// === Carved pumpkin / Jack-o-lantern facing (IDs 86, 91) ===
		// 1.7.10: 0=S, 1=W, 2=N, 3=E
		pumpkinFacingStates("carved_pumpkin", "pumpkin");
		pumpkinFacingStates("jack_o_lantern", "lit_pumpkin");

		// === Anvil facing (ID 145) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bits 2-3=damage
		anvilFacingStates("anvil",          0);
		anvilFacingStates("chipped_anvil",  4);
		anvilFacingStates("damaged_anvil",  8);

		// === Standing signs (ID 63) ===
		// 1.7.10: meta 0-15 = rotation
		for (String wood : WOOD_TYPES) {
			for (int i = 0; i <= 15; i++) {
				state(wood + "_sign", "rotation", String.valueOf(i), entryMetas("standing_sign", i));
			}
		}

		// === Wall signs (ID 68) ===
		// 1.7.10: meta 2=N, 3=S, 4=W, 5=E
		for (String wood : WOOD_TYPES) {
			containerFacingStates(wood + "_wall_sign", "wall_sign");
		}

		// === Wall skull facing (ID 144) ===
		// 1.7.10: 1=floor, 2=N, 3=S, 4=W, 5=E (skull type in tile entity)
		for (String skull : new String[]{
			"skeleton_wall_skull", "wither_skeleton_wall_skull",
			"zombie_wall_head", "player_wall_head", "creeper_wall_head"
		}) {
			containerFacingStates(skull, "skull");
		}

		// === Crops: wheat (ID 59), carrots (ID 141), potatoes (ID 142) ===
		// 1.7.10: meta 0-7 = growth stage
		for (int i = 0; i <= 7; i++) {
			state("wheat",      "age", String.valueOf(i), entryMetas("wheat", i));
			state("carrots",    "age", String.valueOf(i), entryMetas("carrots", i));
			state("potatoes",   "age", String.valueOf(i), entryMetas("potatoes", i));
		}

		// === Pumpkin stem (ID 104), Melon stem (ID 105) ===
		// 1.7.10: meta 0-7 = growth stage
		for (int i = 0; i <= 7; i++) {
			state("pumpkin_stem",   "age", String.valueOf(i), entryMetas("pumpkin_stem", i));
			state("melon_stem",     "age", String.valueOf(i), entryMetas("melon_stem", i));
		}

		// === Cocoa (ID 127) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bits 2-3=age (0-2)
		state("cocoa", "facing",    "south",    entryMetas("cocoa", 0, 4, 8));
		state("cocoa", "facing",    "west",     entryMetas("cocoa", 1, 5, 9));
		state("cocoa", "facing",    "north",    entryMetas("cocoa", 2, 6, 10));
		state("cocoa", "facing",    "east",     entryMetas("cocoa", 3, 7, 11));
		state("cocoa", "age",       "0",        entryMetas("cocoa", 0, 1, 2, 3));
		state("cocoa", "age",       "1",        entryMetas("cocoa", 4, 5, 6, 7));
		state("cocoa", "age",       "2",        entryMetas("cocoa", 8, 9, 10, 11));

		// === Nether wart (ID 115) ===
		// 1.7.10: meta 0-3 = age
		for (int i = 0; i <= 3; i++) {
			state("nether_wart", "age", String.valueOf(i), entryMetas("nether_wart", i));
		}

		// === Cake (ID 92) ===
		// 1.7.10: meta 0-6 = bites eaten
		for (int i = 0; i <= 6; i++) {
			state("cake", "bites", String.valueOf(i), entryMetas("cake", i));
		}

		// === Sugar cane (ID 83 -> reeds) ===
		// 1.7.10: meta 0-15 = age (internal growth counter)
		for (int i = 0; i <= 15; i++) {
			state("sugar_cane", "age", String.valueOf(i), entryMetas("reeds", i));
		}

		// === Cactus (ID 81) ===
		// 1.7.10: meta 0-15 = age (internal growth counter)
		for (int i = 0; i <= 15; i++) {
			state("cactus", "age", String.valueOf(i), entryMetas("cactus", i));
		}

		// === Fire (ID 51) ===
		// 1.7.10: meta 0-15 = age
		for (int i = 0; i <= 15; i++) {
			state("fire", "age", String.valueOf(i), entryMetas("fire", i));
		}

		// === Vines (ID 106) ===
		// 1.7.10: bitmask — bit 0=south, bit 1=west, bit 2=north, bit 3=east
		state("vine", "south",  "true",     entryMetas("vine", 1, 3, 5, 7, 9, 11, 13, 15));
		state("vine", "south",  "false",    entryMetas("vine", 0, 2, 4, 6, 8, 10, 12, 14));
		state("vine", "west",   "true",     entryMetas("vine", 2, 3, 6, 7, 10, 11, 14, 15));
		state("vine", "west",   "false",    entryMetas("vine", 0, 1, 4, 5, 8, 9, 12, 13));
		state("vine", "north",  "true",     entryMetas("vine", 4, 5, 6, 7, 12, 13, 14, 15));
		state("vine", "north",  "false",    entryMetas("vine", 0, 1, 2, 3, 8, 9, 10, 11));
		state("vine", "east",   "true",     entryMetas("vine", 8, 9, 10, 11, 12, 13, 14, 15));
		state("vine", "east",   "false",    entryMetas("vine", 0, 1, 2, 3, 4, 5, 6, 7));

		// === Tripwire hook (ID 131) ===
		// 1.7.10: bits 0-1=facing (0=S,1=W,2=N,3=E), bit 2=connected, bit 3=powered
		state("tripwire_hook", "facing",    "south",    entryMetas("tripwire_hook", 0, 4, 8, 12));
		state("tripwire_hook", "facing",    "west",     entryMetas("tripwire_hook", 1, 5, 9, 13));
		state("tripwire_hook", "facing",    "north",    entryMetas("tripwire_hook", 2, 6, 10, 14));
		state("tripwire_hook", "facing",    "east",     entryMetas("tripwire_hook", 3, 7, 11, 15));
		state("tripwire_hook", "attached",  "false",    entryMetas("tripwire_hook", 0, 1, 2, 3, 8, 9, 10, 11));
		state("tripwire_hook", "attached",  "true",     entryMetas("tripwire_hook", 4, 5, 6, 7, 12, 13, 14, 15));
		state("tripwire_hook", "powered",   "false",    entryMetas("tripwire_hook", 0, 1, 2, 3, 4, 5, 6, 7));
		state("tripwire_hook", "powered",   "true",     entryMetas("tripwire_hook", 8, 9, 10, 11, 12, 13, 14, 15));

		// === Tripwire (ID 132) ===
		// 1.7.10: bit 0=powered, bit 2=attached, bit 3=disarmed
		state("tripwire", "powered",    "true",     entryMetas("tripwire", 1, 5, 9, 13));
		state("tripwire", "powered",    "false",    entryMetas("tripwire", 0, 4, 8, 12));
		state("tripwire", "attached",   "true",     entryMetas("tripwire", 4, 5, 12, 13));
		state("tripwire", "attached",   "false",    entryMetas("tripwire", 0, 1, 8, 9));
		state("tripwire", "disarmed",   "true",     entryMetas("tripwire", 8, 9, 12, 13));
		state("tripwire", "disarmed",   "false",    entryMetas("tripwire", 0, 1, 4, 5));

		// === Brewing stand (ID 117) ===
		// 1.7.10: bit 0=has_bottle_0, bit 1=has_bottle_1, bit 2=has_bottle_2
		state("brewing_stand", "has_bottle_0", "true",  entryMetas("brewing_stand", 1, 3, 5, 7));
		state("brewing_stand", "has_bottle_0", "false", entryMetas("brewing_stand", 0, 2, 4, 6));
		state("brewing_stand", "has_bottle_1", "true",  entryMetas("brewing_stand", 2, 3, 6, 7));
		state("brewing_stand", "has_bottle_1", "false", entryMetas("brewing_stand", 0, 1, 4, 5));
		state("brewing_stand", "has_bottle_2", "true",  entryMetas("brewing_stand", 4, 5, 6, 7));
		state("brewing_stand", "has_bottle_2", "false", entryMetas("brewing_stand", 0, 1, 2, 3));

		// === Jukebox (ID 84) ===
		// 1.7.10: meta 0=empty, 1=has record
		state("jukebox", "has_record", "false", entryMetas("jukebox", 0));
		state("jukebox", "has_record", "true",  entryMetas("jukebox", 1));

		// === Leaves persistent (IDs 18, 161) ===
		// 1.7.10: bit 2=no_decay (player-placed). Maps to modern persistent=true.
		for (int type = 0; type < 4; type++) {
			String[] leafNames = {"oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves"};
			state(leafNames[type], "persistent", "true",    entryMetas("leaves", type + 4, type + 12));
			state(leafNames[type], "persistent", "false",   entryMetas("leaves", type, type + 8));
		}
		state("acacia_leaves", "persistent",    "true",     entryMetas("leaves2", 4, 12));
		state("acacia_leaves", "persistent",    "false",    entryMetas("leaves2", 0, 8));
		state("dark_oak_leaves", "persistent",  "true",     entryMetas("leaves2", 5, 13));
		state("dark_oak_leaves", "persistent",  "false",    entryMetas("leaves2", 1, 9));

		// === Cauldron level (ID 118) ===
		// Already mapped by name; adding level state for water_cauldron
		state("water_cauldron", "level", "1", entryMetas("cauldron", 1));
		state("water_cauldron", "level", "2", entryMetas("cauldron", 2));
		state("water_cauldron", "level", "3", entryMetas("cauldron", 3));

		// === Sapling stage (ID 6) ===
		// 1.7.10: bit 3 = growth stage (0=normal, 1=ready to grow)
		for (int i = 0; i < WOOD_TYPES.length; i++) {
			state(WOOD_TYPES[i] + "_sapling", "stage", "0", entryMetas("sapling", i));
			state(WOOD_TYPES[i] + "_sapling", "stage", "1", entryMetas("sapling", i + 8));
		}

		// === Double plant half (ID 175) ===
		// 1.7.10: bottom half has type in meta 0-5; top half is always meta 8
		state("sunflower",  "half", "lower", entryMetas("double_plant", 0));
		state("sunflower",  "half", "upper", entryMetas("double_plant", 8));
		state("lilac",      "half", "lower", entryMetas("double_plant", 1));
		state("lilac",      "half", "upper", entryMetas("double_plant", 8));
		state("tall_grass", "half", "lower", entryMetas("double_plant", 2));
		state("tall_grass", "half", "upper", entryMetas("double_plant", 8));
		state("large_fern", "half", "lower", entryMetas("double_plant", 3));
		state("large_fern", "half", "upper", entryMetas("double_plant", 8));
		state("rose_bush",  "half", "lower", entryMetas("double_plant", 4));
		state("rose_bush",  "half", "upper", entryMetas("double_plant", 8));
		state("peony",      "half", "lower", entryMetas("double_plant", 5));
		state("peony",      "half", "upper", entryMetas("double_plant", 8));
	}

	/**
	 * Returns the legacy BlockEntries for a modern block name + optional state properties.
	 * The name should not include the "minecraft:" namespace prefix.
	 *
	 * When multiple state properties are specified, results are intersected (AND logic):
	 * only blocks+metas that satisfy ALL specified properties are returned.
	 * Unspecified or unrecognized properties are treated as wildcards.
	 */
	public static List<BlockEntry> toLegacy(String modernName, Map<String, String> stateProperties) {
		if (stateProperties != null && !stateProperties.isEmpty()) {
			List<List<BlockEntry>> propertyResults = new ArrayList<>();

			for (Map.Entry<String, String> prop : stateProperties.entrySet()) {
				String key = modernName + "|" + prop.getKey() + "=" + prop.getValue();
				List<BlockEntry> result = STATE_MAPPINGS.get(key);
				if (result != null) {
					propertyResults.add(result);
				}
				// Unrecognized properties are ignored (wildcard)
			}

			if (propertyResults.size() == 1) {
				return propertyResults.getFirst();
			}

			if (propertyResults.size() > 1) {
				// Intersect results across all matching properties
				List<BlockEntry> combined = propertyResults.getFirst();
				for (int i = 1; i < propertyResults.size(); i++) {
					combined = intersectBlockEntries(combined, propertyResults.get(i));
					if (combined.isEmpty()) break;
				}
				if (!combined.isEmpty()) {
					return combined;
				}
			}
		}

		// Fall back to name-only mapping
		return MODERN_TO_LEGACY.get(modernName);
	}

	/**
	 * Intersects two lists of BlockEntries by block ID, then intersects their meta sets.
	 * Only blocks present in both lists survive. Empty meta set = wildcard (all metas).
	 */
	private static List<BlockEntry> intersectBlockEntries(List<BlockEntry> a, List<BlockEntry> b) {
		List<BlockEntry> result = new ArrayList<>(Math.min(a.size(), b.size()));
		for (BlockEntry entryA : a) {
			for (BlockEntry entryB : b) {
				if (!entryA.getId().equals(entryB.getId())) continue;

				Set<Integer> metasA = entryA.getMetas();
				Set<Integer> metasB = entryB.getMetas();

				if (metasA.isEmpty() && metasB.isEmpty()) {
					// Both wildcard -> wildcard
					result.add(new BlockEntry(entryA.getId(), Collections.emptySet()));
				} else if (metasA.isEmpty()) {
					// A is wildcard -> use B's constraint
					result.add(new BlockEntry(entryA.getId(), metasB));
				} else if (metasB.isEmpty()) {
					// B is wildcard -> use A's constraint
					result.add(new BlockEntry(entryA.getId(), metasA));
				} else {
					// Both have specific metas -> intersect
					Set<Integer> intersection = new IntOpenHashSet(metasA);
					intersection.retainAll(metasB);
					if (!intersection.isEmpty()) {
						result.add(new BlockEntry(entryA.getId(), intersection));
					}
				}
			}
		}
		return result;
	}

	// ==========================================
	// Helper methods
	// ==========================================

	/** Simple rename with no specific metadata (matches all metas). */
	private static void rename(String modern, String legacy) {
		MODERN_TO_LEGACY.put(modern, List.of(new BlockEntry(new NamespacedId("minecraft", legacy), Collections.emptySet())));
	}

	/** Single metadata value. */
	private static void meta(String modern, String legacy, int meta) {
		MODERN_TO_LEGACY.put(modern, List.of(new BlockEntry(new NamespacedId("minecraft", legacy), Set.of(meta))));
	}

	/** Multiple metadata values. */
	private static void metas(String modern, String legacy, int... metaValues) {
		Set<Integer> metaSet = new IntOpenHashSet();
		for (int m : metaValues) metaSet.add(m);
		MODERN_TO_LEGACY.put(modern, List.of(new BlockEntry(new NamespacedId("minecraft", legacy), metaSet)));
	}

	/** Maps a modern name to multiple legacy block entries. */
	private static void multi(String modern, BlockEntry... entries) {
		MODERN_TO_LEGACY.put(modern, List.of(entries));
	}

	/** Maps a modern name + state property value to legacy entries. */
	private static void state(String modern, String property, String value, BlockEntry... entries) {
		STATE_MAPPINGS.put(modern + "|" + property + "=" + value, List.of(entries));
	}

	/** Creates a BlockEntry with no specific metas. */
	private static BlockEntry entry(String legacyName) {
		return new BlockEntry(new NamespacedId("minecraft", legacyName), Collections.emptySet());
	}

	/** Creates a BlockEntry with specific metas. */
	private static BlockEntry entryMetas(String legacyName, int... metaValues) {
		Set<Integer> metaSet = new IntOpenHashSet();
		for (int m : metaValues) metaSet.add(m);
		return new BlockEntry(new NamespacedId("minecraft", legacyName), metaSet);
	}

	/**
	 * Log variants: log_name gets all axis metas for its type, wood_name gets the bark-only meta.
	 * Metadata: bits 0-1 = type, bits 2-3 = axis (0=Y, 1=X, 2=Z, 3=bark)
	 */
	private static void logVariants(String logName, String woodName, int typeOffset) {
		metas(logName, "log", typeOffset, typeOffset + 4, typeOffset + 8);
		meta(woodName, "log", typeOffset + 12);
	}

	/**
	 * Log2 variants: like logVariants, but log2 only has 2 real types (acacia=0, dark_oak=1).
	 * Types 2 and 3 are unused but exist in-world and render as acacia/dark_oak.
	 * Include both the real type and its phantom type (+2) to cover all metas.
	 */
	private static void log2Variants(String logName, String woodName, int typeOffset) {
		int phantom = typeOffset + 2; // Unused type that renders the same
		metas(logName, "log2", typeOffset, typeOffset + 4, typeOffset + 8, phantom, phantom + 4, phantom + 8);
		metas(woodName, "log2", typeOffset + 12, phantom + 12);
	}

	/**
	 * Leaf variants: includes all decay flag combinations for a given type.
	 * Metadata: bits 0-1 = type, bit 2 = no_decay, bit 3 = check_decay
	 */
	private static void leafVariants(String modern, String legacy, int typeOffset) {
		metas(modern, legacy, typeOffset, typeOffset + 4, typeOffset + 8, typeOffset + 12);
	}

	/**
	 * Slab name-only variants: includes both top and bottom half.
	 */
	private static void slabVariants(String modern, String legacy, int typeOffset) {
		metas(modern, legacy, typeOffset, typeOffset + 8);
	}

	/**
	 * Slab state property mappings: type=bottom/top/double.
	 * Bottom = base meta, top = base meta + 8, double = separate block.
	 */
	private static void slabStates(String modern, String slabBlock, int typeOffset, String doubleBlock, int doubleMeta) {
		state(modern, "type", "bottom", entryMetas(slabBlock, typeOffset));
		state(modern, "type", "top", entryMetas(slabBlock, typeOffset + 8));
		state(modern, "type", "double", entryMetas(doubleBlock, doubleMeta));
	}

	/**
	 * Creates 16 color variant mappings.
	 * Modern names are "{color}_{suffix}", legacy is a single block with meta 0-15.
	 */
	private static void colorVariants(String modernSuffix, String legacyName) {
		for (int i = 0; i < COLORS.length; i++) {
			meta(COLORS[i] + "_" + modernSuffix, legacyName, i);
		}
	}

	// ==========================================
	// Blockstate property mapping helpers
	// ==========================================

	/**
	 * Stair states: facing (E=0,W=1,S=2,N=3) + half (bit 2 = upside_down).
	 */
	private static void stairStates(String modern, String legacy) {
		state(modern, "facing", "east", entryMetas(legacy, 0, 4));
		state(modern, "facing", "west", entryMetas(legacy, 1, 5));
		state(modern, "facing", "south", entryMetas(legacy, 2, 6));
		state(modern, "facing", "north", entryMetas(legacy, 3, 7));
		state(modern, "half", "bottom", entryMetas(legacy, 0, 1, 2, 3));
		state(modern, "half", "top", entryMetas(legacy, 4, 5, 6, 7));
	}

	/**
	 * Door states: lower half has facing+open, upper half has hinge+powered.
	 * Lower: bits 0-1=facing (E=0,S=1,W=2,N=3), bit 2=open.
	 * Upper: bit 0=hinge (L=0,R=1), bit 1=powered.
	 */
	private static void doorStates(String modern, String legacy) {
		state(modern, "half", "lower", entryMetas(legacy, 0, 1, 2, 3, 4, 5, 6, 7));
		state(modern, "half", "upper", entryMetas(legacy, 8, 9, 10, 11));
		state(modern, "facing", "east", entryMetas(legacy, 0, 4));
		state(modern, "facing", "south", entryMetas(legacy, 1, 5));
		state(modern, "facing", "west", entryMetas(legacy, 2, 6));
		state(modern, "facing", "north", entryMetas(legacy, 3, 7));
		state(modern, "open", "false", entryMetas(legacy, 0, 1, 2, 3));
		state(modern, "open", "true", entryMetas(legacy, 4, 5, 6, 7));
		state(modern, "hinge", "left", entryMetas(legacy, 8, 10));
		state(modern, "hinge", "right", entryMetas(legacy, 9, 11));
		state(modern, "powered", "false", entryMetas(legacy, 8, 9));
		state(modern, "powered", "true", entryMetas(legacy, 10, 11));
	}

	/**
	 * Trapdoor states: facing (S=0,N=1,E=2,W=3), open (bit 2), half (bit 3).
	 */
	private static void trapdoorStates() {
		state("oak_trapdoor", "facing", "south", entryMetas("trapdoor", 0, 4, 8, 12));
		state("oak_trapdoor", "facing", "north", entryMetas("trapdoor", 1, 5, 9, 13));
		state("oak_trapdoor", "facing", "east", entryMetas("trapdoor", 2, 6, 10, 14));
		state("oak_trapdoor", "facing", "west", entryMetas("trapdoor", 3, 7, 11, 15));
		state("oak_trapdoor", "open", "false", entryMetas("trapdoor", 0, 1, 2, 3, 8, 9, 10, 11));
		state("oak_trapdoor", "open", "true", entryMetas("trapdoor", 4, 5, 6, 7, 12, 13, 14, 15));
		state("oak_trapdoor", "half", "bottom", entryMetas("trapdoor", 0, 1, 2, 3, 4, 5, 6, 7));
		state("oak_trapdoor", "half", "top", entryMetas("trapdoor", 8, 9, 10, 11, 12, 13, 14, 15));
	}

	/**
	 * Fence gate states: facing (S=0,W=1,N=2,E=3), open (bit 2).
	 */
	private static void fenceGateStates() {
		state("oak_fence_gate", "facing", "south", entryMetas("fence_gate", 0, 4));
		state("oak_fence_gate", "facing", "west", entryMetas("fence_gate", 1, 5));
		state("oak_fence_gate", "facing", "north", entryMetas("fence_gate", 2, 6));
		state("oak_fence_gate", "facing", "east", entryMetas("fence_gate", 3, 7));
		state("oak_fence_gate", "open", "false", entryMetas("fence_gate", 0, 1, 2, 3));
		state("oak_fence_gate", "open", "true", entryMetas("fence_gate", 4, 5, 6, 7));
	}

	/**
	 * 6-directional block states: facing (D=0,U=1,N=2,S=3,W=4,E=5), flag (bit 3).
	 * Used by pistons, piston heads, dispensers, droppers.
	 */
	private static void sixDirectionalStates(String modern, String legacy,
			String flagProp, String flagFalse, String flagTrue) {
		state(modern, "facing", "down", entryMetas(legacy, 0, 8));
		state(modern, "facing", "up", entryMetas(legacy, 1, 9));
		state(modern, "facing", "north", entryMetas(legacy, 2, 10));
		state(modern, "facing", "south", entryMetas(legacy, 3, 11));
		state(modern, "facing", "west", entryMetas(legacy, 4, 12));
		state(modern, "facing", "east", entryMetas(legacy, 5, 13));
		state(modern, flagProp, flagFalse, entryMetas(legacy, 0, 1, 2, 3, 4, 5));
		state(modern, flagProp, flagTrue, entryMetas(legacy, 8, 9, 10, 11, 12, 13));
	}

	/**
	 * Container-style facing: N=2, S=3, W=4, E=5.
	 * Used by chests, ender chests, ladders, wall signs.
	 */
	private static void containerFacingStates(String modern, String legacy) {
		state(modern, "facing", "north", entryMetas(legacy, 2));
		state(modern, "facing", "south", entryMetas(legacy, 3));
		state(modern, "facing", "west", entryMetas(legacy, 4));
		state(modern, "facing", "east", entryMetas(legacy, 5));
	}

	/**
	 * Powered rail-style states: shape (bits 0-2) + powered (bit 3).
	 * Used by powered rail, detector rail, activator rail.
	 */
	private static void poweredRailStates(String modern, String legacy) {
		state(modern, "shape", "north_south", entryMetas(legacy, 0, 8));
		state(modern, "shape", "east_west", entryMetas(legacy, 1, 9));
		state(modern, "shape", "ascending_east", entryMetas(legacy, 2, 10));
		state(modern, "shape", "ascending_west", entryMetas(legacy, 3, 11));
		state(modern, "shape", "ascending_north", entryMetas(legacy, 4, 12));
		state(modern, "shape", "ascending_south", entryMetas(legacy, 5, 13));
		state(modern, "powered", "false", entryMetas(legacy, 0, 1, 2, 3, 4, 5));
		state(modern, "powered", "true", entryMetas(legacy, 8, 9, 10, 11, 12, 13));
	}

	/**
	 * Button states: face, facing, powered.
	 * Meta: 0=ceiling, 1=E wall, 2=W wall, 3=S wall, 4=N wall, 5=floor; +8=powered.
	 */
	private static void buttonStates(String modern, String legacy) {
		state(modern, "face", "floor", entryMetas(legacy, 5, 13));
		state(modern, "face", "wall", entryMetas(legacy, 1, 2, 3, 4, 9, 10, 11, 12));
		state(modern, "face", "ceiling", entryMetas(legacy, 0, 8));
		state(modern, "powered", "false", entryMetas(legacy, 0, 1, 2, 3, 4, 5));
		state(modern, "powered", "true", entryMetas(legacy, 8, 9, 10, 11, 12, 13));
		state(modern, "facing", "east", entryMetas(legacy, 1, 9));
		state(modern, "facing", "west", entryMetas(legacy, 2, 10));
		state(modern, "facing", "south", entryMetas(legacy, 3, 11));
		state(modern, "facing", "north", entryMetas(legacy, 4, 12));
	}

	/**
	 * Log axis states: type in bits 0-1, axis in bits 2-3 (0=Y, 1=X, 2=Z).
	 */
	private static void logAxisStates(String modern, int typeOffset) {
		state(modern, "axis", "y", entryMetas("log", typeOffset));
		state(modern, "axis", "x", entryMetas("log", typeOffset + 4));
		state(modern, "axis", "z", entryMetas("log", typeOffset + 8));
	}

	/**
	 * Wood (bark) axis states: all axes map to same meta since 1.7.10 bark is all-sided.
	 */
	private static void woodAxisStates(String modern, int meta) {
		state(modern, "axis", "y", entryMetas("log", meta));
		state(modern, "axis", "x", entryMetas("log", meta));
		state(modern, "axis", "z", entryMetas("log", meta));
	}

	/**
	 * Wood2 (bark) axis states: includes phantom types.
	 */
	private static void wood2AxisStates(String modern, int meta, int phantomMeta) {
		state(modern, "axis", "y", entryMetas("log2", meta, phantomMeta));
		state(modern, "axis", "x", entryMetas("log2", meta, phantomMeta));
		state(modern, "axis", "z", entryMetas("log2", meta, phantomMeta));
	}

	/**
	 * Log2 axis states: includes phantom types (+2) that exist in-world.
	 */
	private static void log2AxisStates(String modern, int typeOffset) {
		int phantom = typeOffset + 2;
		state(modern, "axis", "y", entryMetas("log2", typeOffset, phantom));
		state(modern, "axis", "x", entryMetas("log2", typeOffset + 4, phantom + 4));
		state(modern, "axis", "z", entryMetas("log2", typeOffset + 8, phantom + 8));
	}

	/**
	 * Bed states: facing (S=0,W=1,N=2,E=3), occupied (bit 2), part (bit 3).
	 */
	private static void bedStates(String modern) {
		state(modern, "facing", "south", entryMetas("bed", 0, 4, 8, 12));
		state(modern, "facing", "west", entryMetas("bed", 1, 5, 9, 13));
		state(modern, "facing", "north", entryMetas("bed", 2, 6, 10, 14));
		state(modern, "facing", "east", entryMetas("bed", 3, 7, 11, 15));
		state(modern, "occupied", "false", entryMetas("bed", 0, 1, 2, 3, 8, 9, 10, 11));
		state(modern, "occupied", "true", entryMetas("bed", 4, 5, 6, 7, 12, 13, 14, 15));
		state(modern, "part", "foot", entryMetas("bed", 0, 1, 2, 3, 4, 5, 6, 7));
		state(modern, "part", "head", entryMetas("bed", 8, 9, 10, 11, 12, 13, 14, 15));
	}

	/**
	 * Pumpkin-style facing: S=0, W=1, N=2, E=3.
	 */
	private static void pumpkinFacingStates(String modern, String legacy) {
		state(modern, "facing", "south", entryMetas(legacy, 0));
		state(modern, "facing", "west", entryMetas(legacy, 1));
		state(modern, "facing", "north", entryMetas(legacy, 2));
		state(modern, "facing", "east", entryMetas(legacy, 3));
	}

	/**
	 * Anvil facing states: facing within a damage level offset.
	 * Anvil meta: bits 0-1=facing (S=0,W=1,N=2,E=3), bits 2-3=damage.
	 */
	private static void anvilFacingStates(String modern, int damageOffset) {
		state(modern, "facing", "south", entryMetas("anvil", damageOffset));
		state(modern, "facing", "west", entryMetas("anvil", damageOffset + 1));
		state(modern, "facing", "north", entryMetas("anvil", damageOffset + 2));
		state(modern, "facing", "east", entryMetas("anvil", damageOffset + 3));
	}
}
