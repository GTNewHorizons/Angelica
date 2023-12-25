package com.prupe.mcpatcher.mob;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;
import com.prupe.mcpatcher.mal.biome.BiomeAPI;
import com.prupe.mcpatcher.mal.resource.TexturePackChangeHandler;

public class MobRandomizer {

    private static final MCLogger logger = MCLogger.getLogger(MCPatcherUtils.RANDOM_MOBS);
    private static final Map<String, ResourceLocation> cache = new LinkedHashMap<>();

    static {
        TexturePackChangeHandler.register(new TexturePackChangeHandler(MCPatcherUtils.RANDOM_MOBS, 2) {

            @Override
            public void beforeChange() {
                cache.clear();
            }

            @Override
            public void afterChange() {
                MobRuleList.clear();
                MobOverlay.reset();
                LineRenderer.reset();
            }
        });
    }

    public static void init() {}

    public static ResourceLocation randomTexture(EntityLivingBase entity, ResourceLocation texture) {
        if (texture == null || !texture.getResourcePath()
            .endsWith(".png")) {
            return texture;
        }
        String key = texture + ":" + entity.getEntityId();
        ResourceLocation newTexture = cache.get(key);
        if (newTexture == null) {
            ExtraInfo info = ExtraInfo.getInfo(entity);
            MobRuleList list = MobRuleList.get(texture);
            newTexture = list.getSkin(info.skin, info.origX, info.origY, info.origZ, info.origBiome);
            cache.put(key, newTexture);
            logger.finer("entity %s using %s (cache: %d)", entity, newTexture, cache.size());
            if (cache.size() > 250) {
                while (cache.size() > 200) {
                    cache.remove(
                        cache.keySet()
                            .iterator()
                            .next());
                }
            }
        }
        return newTexture;
    }

    public static ResourceLocation randomTexture(Entity entity, ResourceLocation texture) {
        if (entity instanceof EntityLivingBase) {
            return randomTexture((EntityLivingBase) entity, texture);
        } else {
            return texture;
        }
    }

    public static final class ExtraInfo {

        private static final String SKIN_TAG = "randomMobsSkin";
        private static final String ORIG_X_TAG = "origX";
        private static final String ORIG_Y_TAG = "origY";
        private static final String ORIG_Z_TAG = "origZ";

        private static final long MULTIPLIER = 0x5deece66dL;
        private static final long ADDEND = 0xbL;
        private static final long MASK = (1L << 48) - 1;

        private static final Map<Integer, ExtraInfo> allInfo = new HashMap<>();
        private static final Map<WeakReference<EntityLivingBase>, ExtraInfo> allRefs = new HashMap<>();
        private static final ReferenceQueue<EntityLivingBase> refQueue = new ReferenceQueue<>();

        private final int entityId;
        private final HashSet<WeakReference<EntityLivingBase>> references;
        private final long skin;
        private final int origX;
        private final int origY;
        private final int origZ;
        private Integer origBiome;

        ExtraInfo(EntityLivingBase entity) {
            this(entity, getSkinId(entity.getEntityId()), (int) entity.posX, (int) entity.posY, (int) entity.posZ);
        }

        ExtraInfo(EntityLivingBase entity, long skin, int origX, int origY, int origZ) {
            entityId = entity.getEntityId();
            references = new HashSet<>();
            this.skin = skin;
            this.origX = origX;
            this.origY = origY;
            this.origZ = origZ;
        }

        private void setBiome() {
            if (origBiome == null) {
                origBiome = BiomeAPI.getBiomeIDAt(BiomeAPI.getWorld(), origX, origY, origZ);
            }
        }

        @Override
        public String toString() {
            return String.format(
                "%s{%d, %d, %d, %d, %d, %s}",
                getClass().getSimpleName(),
                entityId,
                skin,
                origX,
                origY,
                origZ,
                origBiome);
        }

        private static void clearUnusedReferences() {
            synchronized (allInfo) {
                Reference<? extends EntityLivingBase> ref;
                while ((ref = refQueue.poll()) != null) {
                    ExtraInfo info = allRefs.get(ref);
                    if (info != null) {
                        info.references.remove(ref);
                        if (info.references.isEmpty()) {
                            logger.finest("removing unused ref %d", info.entityId);
                            allInfo.remove(info.entityId);
                        }
                    }
                    allRefs.remove(ref);
                }
            }
        }

        static ExtraInfo getInfo(EntityLivingBase entity) {
            ExtraInfo info;
            synchronized (allInfo) {
                clearUnusedReferences();
                info = allInfo.get(entity.getEntityId());
                if (info == null) {
                    info = new ExtraInfo(entity);
                    putInfo(entity, info);
                }
                boolean found = false;
                for (WeakReference<EntityLivingBase> ref : info.references) {
                    if (ref.get() == entity) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    WeakReference<EntityLivingBase> reference = new WeakReference<>(entity, refQueue);
                    info.references.add(reference);
                    allRefs.put(reference, info);
                    logger.finest(
                        "added ref #%d for %d (%d entities)",
                        info.references.size(),
                        entity.getEntityId(),
                        allInfo.size());
                }
                info.setBiome();
            }
            return info;
        }

        static void putInfo(EntityLivingBase entity, ExtraInfo info) {
            synchronized (allInfo) {
                allInfo.put(entity.getEntityId(), info);
            }
        }

        static void clearInfo() {
            synchronized (allInfo) {
                allInfo.clear();
            }
        }

        private static long getSkinId(int entityId) {
            long n = entityId;
            n = n ^ (n << 16) ^ (n << 32) ^ (n << 48);
            n = MULTIPLIER * n + ADDEND;
            n = MULTIPLIER * n + ADDEND;
            n &= MASK;
            return (n >> 32) ^ n;
        }

        public static void readFromNBT(EntityLivingBase entity, NBTTagCompound nbt) {
            long skin = nbt.getLong(SKIN_TAG);
            if (skin != 0L) {
                int x = nbt.getInteger(ORIG_X_TAG);
                int y = nbt.getInteger(ORIG_Y_TAG);
                int z = nbt.getInteger(ORIG_Z_TAG);
                putInfo(entity, new ExtraInfo(entity, skin, x, y, z));
            }
        }

        public static void writeToNBT(EntityLivingBase entity, NBTTagCompound nbt) {
            synchronized (allInfo) {
                ExtraInfo info = allInfo.get(entity.getEntityId());
                if (info != null) {
                    nbt.setLong(SKIN_TAG, info.skin);
                    nbt.setInteger(ORIG_X_TAG, info.origX);
                    nbt.setInteger(ORIG_Y_TAG, info.origY);
                    nbt.setInteger(ORIG_Z_TAG, info.origZ);
                }
            }
        }
    }
}
