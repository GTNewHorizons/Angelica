package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.ints.Int2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.minecraft.nbt.NBTTagCompound;

import java.util.IdentityHashMap;
import java.util.Map;
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
    private static final Map<Class<?>, NamespacedId> entityNameCache = new IdentityHashMap<>();
    private static Object2IntFunction<NamespacedId> cachedEntityIdMap;

    private static final Int2LongLinkedOpenHashMap entityNbtCache = new Int2LongLinkedOpenHashMap();
    private static final int NBT_CACHE_INTERVAL_TICKS = 20;
    private static final int ENTITY_NBT_CACHE_MAX = 256;

    static {
        entityIdCache.defaultReturnValue(Integer.MIN_VALUE);
        entityNbtCache.defaultReturnValue(-1L);
    }

    private EntityIdHelper() {
    }

    public static boolean isLightningBolt(Entity entity) {
        return entity instanceof EntityLightningBolt;
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

        // Invalidate caches if the map changed (shader reload)
        if (entityIdMap != cachedEntityIdMap) {
            entityIdCache.clear();
            entityNameCache.clear();
            entityNbtCache.clear();
            cachedEntityIdMap = entityIdMap;
        }

        // Check NBT-conditional match first
        final NbtConditionalIdMap<NamespacedId> entityNbtMap = BlockRenderingSettings.INSTANCE.getEntityNbtMap();
        if (entityNbtMap != null && !entityNbtMap.isEmpty() && entity.worldObj != null) {
            final NamespacedId namespacedId = getCachedEntityName(entity);
            if (namespacedId != null && entityNbtMap.hasConditions(namespacedId)) {
                final int entityRuntimeId = entity.getEntityId();
                final long currentTick = entity.worldObj.getTotalWorldTime();
                final long cached = entityNbtCache.get(entityRuntimeId);

                final int nbtId;
                if (cached != -1L && (currentTick - (cached >>> 32)) < NBT_CACHE_INTERVAL_TICKS) {
                    nbtId = (int) cached;
                } else {
                    final NBTTagCompound nbt = new NBTTagCompound();
                    entity.writeToNBT(nbt);
                    nbtId = entityNbtMap.resolve(namespacedId, nbt);
                    final long packed = ((currentTick & 0x7FFFFFFFL) << 32) | (nbtId & 0xFFFFFFFFL);
                    entityNbtCache.put(entityRuntimeId, packed);
                    while (entityNbtCache.size() > ENTITY_NBT_CACHE_MAX) {
                        entityNbtCache.removeFirstLong();
                    }
                }

                if (nbtId != -1) {
                    return nbtId;
                }
            }
        }

        // Normal entity type lookup
        final int normalId = getNormalEntityId(entity, entityIdMap);

        // Check for special entity type overrides
        final int specialId = getSpecialEntityId(entity, entityIdMap);
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

    /**
     * Returns a cached NamespacedId for the entity's registered type.
     */
    private static NamespacedId getCachedEntityName(Entity entity) {
        Class<?> entityClass = entity.getClass();
        NamespacedId cached = entityNameCache.get(entityClass);
        if (cached != null) {
            return cached;
        }

        String entityType = EntityList.getEntityString(entity);
        if (entityType == null) {
            return null;
        }

        cached = new NamespacedId(entityType);
        entityNameCache.put(entityClass, cached);
        return cached;
    }
}
