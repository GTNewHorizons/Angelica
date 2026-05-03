package net.coderbot.iris.shaderpack.materialmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps modern entity names to their 1.7.10 equivalents.
 *
 * In 1.11+, entity IDs were changed to namespaced: "minecraft:cave_spider".
 * In 1.7.10, entities are registered with names like "CaveSpider".
 * Some entities were also renamed or split into multiple types.
 *
 * This map is used at lookup time: when a modern shader pack references an entity by its
 * modern name, we resolve it to the 1.7.10 name that EntityList uses.
 */
public class EntityFlatteningMap {

	/** Maps modern entity name -> 1.7.10 entity name. */
	private static final Map<String, String> MODERN_TO_LEGACY = new HashMap<>();

	static {
		// Simple case changes
		simple("Arrow", "arrow");
		simple("Bat", "bat");
		simple("Blaze", "blaze");
		simple("Boat", "boat");
		simple("Chicken", "chicken");
		simple("Cow", "cow");
		simple("Creeper", "creeper");
		simple("Enderman", "enderman");
		simple("Ghast", "ghast");
		simple("Giant", "giant");
		simple("Item", "item");
		simple("Painting", "painting");
		simple("Pig", "pig");
		simple("Sheep", "sheep");
		simple("Silverfish", "silverfish");
		simple("Skeleton", "skeleton");
		simple("Slime", "slime");
		simple("Snowball", "snowball");
		simple("Spider", "spider");
		simple("Squid", "squid");
		simple("Villager", "villager");
		simple("Witch", "witch");
		simple("Wolf", "wolf");
		simple("Zombie", "zombie");

		// Renamed entities
		simple("CaveSpider", "cave_spider");
		simple("EnderCrystal", "end_crystal");
		simple("EnderDragon", "ender_dragon");
		simple("EntityHorse", "horse");
		simple("EyeOfEnderSignal", "eye_of_ender");
		simple("FallingSand", "falling_block");
		simple("Fireball", "fireball");
		simple("FireworksRocketEntity", "firework_rocket");
		simple("ItemFrame", "item_frame");
		simple("LavaSlime", "magma_cube");
		simple("LeashKnot", "leash_knot");
		simple("MushroomCow", "mooshroom");
		simple("Ozelot", "ocelot");
		simple("PigZombie", "zombified_piglin");
		simple("PrimedTnt", "tnt");
		simple("SmallFireball", "small_fireball");
		simple("SnowMan", "snow_golem");
		simple("ThrownEnderpearl", "ender_pearl");
		simple("ThrownExpBottle", "experience_bottle");
		simple("ThrownPotion", "potion");
		simple("VillagerGolem", "iron_golem");
		simple("WitherBoss", "wither");
		simple("WitherSkull", "wither_skull");
		simple("XPOrb", "experience_orb");

		// Minecarts
		simple("MinecartRideable", "minecart");
		simple("MinecartChest", "chest_minecart");
		simple("MinecartCommandBlock", "command_block_minecart");
		simple("MinecartFurnace", "furnace_minecart");
		simple("MinecartHopper", "hopper_minecart");
		simple("MinecartSpawner", "spawner_minecart");
		simple("MinecartTNT", "tnt_minecart");
	}

	/**
	 * Returns the 1.7.10 entity name for a modern entity name, or null if no mapping exists.
	 * The input name should not include the "minecraft:" namespace prefix.
	 */
	public static String toLegacy(String modernName) {
		return MODERN_TO_LEGACY.get(modernName);
	}

	private static void simple(String legacy, String modern) {
		MODERN_TO_LEGACY.put(modern, legacy);
	}
}
