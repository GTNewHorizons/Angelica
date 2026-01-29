package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Shared utility for resolving entity IDs with support for special entities and caching.
 */
public final class EntityIdHelper {
    private static final NamespacedId CONVERTING_VILLAGER = new NamespacedId("minecraft", "zombie_villager_converting");
    private static final NamespacedId CURRENT_PLAYER = new NamespacedId("minecraft", "current_player");
    private static final NamespacedId LIGHTNING_BOLT_ID = new NamespacedId("minecraft", "lightning_bolt");

    private static final Object2IntMap<Class<?>> entityIdCache = new Object2IntOpenHashMap<>();
    private static Object2IntFunction<NamespacedId> cachedEntityIdMap;

    static {
        entityIdCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    private EntityIdHelper() {
    }

    /**
     * Get the entity ID for the given entity.
     *
     * @param entity The entity to get the ID for
     * @return The entity ID, or -1 if no mapping exists
     */
    public static int getEntityId(Entity entity) {
        Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
        if (entityIdMap == null) {
            return -1;
        }

        // Invalidate cache if the map changed (shader reload)
        if (entityIdMap != cachedEntityIdMap) {
            entityIdCache.clear();
            cachedEntityIdMap = entityIdMap;
        }

        // Normal entity type lookup
        int normalId = getNormalEntityId(entity, entityIdMap);

        // Check for special entity type overrides
        int specialId = getSpecialEntityId(entity, entityIdMap);
        if (specialId != -1) {
            return specialId;
        }

        return normalId;
    }

    /**
     * Check if the entity should use a special hardcoded entity ID.
     */
    private static int getSpecialEntityId(Entity entity, Object2IntFunction<NamespacedId> entityIdMap) {
        // Check if this is the current player (3rd person only)
        Entity cameraEntity = Minecraft.getMinecraft().renderViewEntity;
        if (entity == cameraEntity && entity instanceof EntityPlayer) {
            int currentPlayerId = entityIdMap.applyAsInt(CURRENT_PLAYER);
            if (currentPlayerId != -1) {
                return currentPlayerId;
            }
        }

        // Check if this is a converting zombie villager
        if (entity instanceof EntityZombie zombie) {
            if (zombie.isConverting()) {
                return entityIdMap.applyAsInt(CONVERTING_VILLAGER);
            }
        }

        return -1;
    }

    /**
     * Get the normal entity ID based on entity type, with caching.
     * Uses Class<?> as cache key for fast reference-equality lookups.
     */
    private static int getNormalEntityId(Entity entity, Object2IntFunction<NamespacedId> entityIdMap) {
        // Use entity class as cache key
        Class<?> entityClass = entity.getClass();

        // Check cache first
        int cached = entityIdCache.getInt(entityClass);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }

        // Cache miss, store for next lookup
        int resolvedId = resolveEntityId(entity, entityClass, entityIdMap);
        entityIdCache.put(entityClass, resolvedId);

        return resolvedId;
    }

    /**
     * Resolve entity ID for a cache miss. Try in order:
     * 1. Registered entity name        (e.g., "Zombie")
     * 2. Simple class name             (e.g., "EntityLightningBolt")
     * 3. Fully qualified class name    (e.g., "net.minecraft.entity.effect.EntityLightningBolt")
     * 4. Special hardcoded cases       (e.g., "minecraft:lightning_bolt")
     */
    private static int resolveEntityId(Entity entity, Class<?> entityClass, Object2IntFunction<NamespacedId> entityIdMap) {
        // Try registered entity name first (most common)
        String entityType = EntityList.getEntityString(entity);
        if (entityType != null) {
            return entityIdMap.applyAsInt(new NamespacedId(entityType));
        }

        String simpleClassName = entityClass.getSimpleName();
        int id = entityIdMap.applyAsInt(new NamespacedId(simpleClassName));

        if (id == -1) {
            String className = entityClass.getName();
            id = entityIdMap.applyAsInt(new NamespacedId(className));
        }

        if (id == -1 && entity instanceof EntityLightningBolt) {
            // Shaderpacks use "minecraft:lightning_bolt" so we should continue to provide it here
            // Even if we can just use EntityLightningBolt
            id = entityIdMap.applyAsInt(LIGHTNING_BOLT_ID);
        }

        return id;
    }
}
