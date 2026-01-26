package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to handle material ID setting for entities rendered through renderEntityWithPosYaw.
 * This is needed for EntityPickupFX which renders entities during pickup animation
 * Entity ID mapping is handled by MixinRenderGlobal for the normal rendering path.
 */

// TODO: Find a way to de-duplicate this code.
@Mixin(RenderManager.class)
public class MixinRenderManager {
    @Unique private static final Object2IntOpenHashMap<String> angelica$entityIdCache = new Object2IntOpenHashMap<>();
    @Unique private static Object2IntFunction<NamespacedId> angelica$cachedEntityIdMap;

    static {
        angelica$entityIdCache.defaultReturnValue(Integer.MIN_VALUE);
    }

    @WrapOperation(
        method = "func_147939_a",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V")
    )
    private void iris$wrapDoRender(net.minecraft.client.renderer.entity.Render render, Entity entity, double x, double y, double z, float entityYaw, float partialTicks, Operation<Void> original) {
        // Set appropriate material ID when entities are rendered through renderEntityWithPosYaw
        // (used by EntityPickupFX during pickup animation)
        // Note: Entity ID mapping is handled by MixinRenderGlobal for the normal path
        boolean setItemId = false;
        boolean setEntityId = false;

        if (entity instanceof EntityItem) {
            EntityItem entityItem = (EntityItem) entity;
            ItemStack itemStack = entityItem.getEntityItem();
            int itemId = ItemMaterialHelper.getMaterialId(itemStack);
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemId);
            setItemId = true;
        } else {
            Object2IntFunction<NamespacedId> entityIdMap = BlockRenderingSettings.INSTANCE.getEntityIds();
            if (entityIdMap != null) {
                // Invalidate cache if the map changed (shader reload)
                if (entityIdMap != angelica$cachedEntityIdMap) {
                    angelica$entityIdCache.clear();
                    angelica$cachedEntityIdMap = entityIdMap;
                }

                int entityId = entity.getEntityId();

                String entityType = EntityList.getEntityString(entity);
                String cacheKey;

                if (entityType != null) {
                    // Use registered entity name
                    cacheKey = entityType;
                    int cached = angelica$entityIdCache.getInt(cacheKey);
                    if (cached == Integer.MIN_VALUE) {
                        cached = entityIdMap.applyAsInt(new NamespacedId(entityType));
                        angelica$entityIdCache.put(cacheKey, cached);
                    }
                    entityId = cached;
                } else {
                    // Unregistered entity - try class names
                    Class<?> entityClass = entity.getClass();
                    String className = entityClass.getName();
                    cacheKey = className;

                    int cached = angelica$entityIdCache.getInt(cacheKey);
                    if (cached == Integer.MIN_VALUE) {
                        // Try simple class name first (e.g., "EntityLightningBolt")
                        String simpleClassName = entityClass.getSimpleName();
                        cached = entityIdMap.applyAsInt(new NamespacedId(simpleClassName));

                        // If not found, try FQN (e.g., "net.minecraft.entity.effect.EntityLightningBolt")
                        if (cached == -1) {
                            cached = entityIdMap.applyAsInt(new NamespacedId(className));
                        }

                        angelica$entityIdCache.put(cacheKey, cached);
                    }
                    entityId = cached;
                }

                CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
                setEntityId = true;
            }
        }

        try {
            original.call(render, entity, x, y, z, entityYaw, partialTicks);
        } finally {
            if (setItemId) {
                CapturedRenderingState.INSTANCE.setCurrentRenderedItem(0);
            }
            if (setEntityId) {
                CapturedRenderingState.INSTANCE.setCurrentEntity(-1);
            }
        }
    }
}
