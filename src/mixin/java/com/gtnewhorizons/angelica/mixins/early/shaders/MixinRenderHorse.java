package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderHorse;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to set the currentRenderedItem ID when rendering horse armor.
 * This allows shaders to identify and apply different materials to horse armor.
 */
@Mixin(RenderHorse.class)
public abstract class MixinRenderHorse {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    /**
     * Set the item ID when rendering the horse model if it has armor.
     * Wraps the mainModel.render() call to set the material ID only around the actual render.
     */
    @WrapOperation(
        method = "renderModel(Lnet/minecraft/entity/passive/EntityHorse;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void iris$setHorseArmorItemId(ModelBase model, net.minecraft.entity.Entity entity, float p1, float p2, float p3, float p4, float p5, float p6, Operation<Void> original, EntityHorse horse) {
        // Only set ID if horse has armor equipped
        boolean hasArmor = false;

        // Check if horse type == 0 (regular horse)
        if (horse.func_110259_cr()) {
            // Get armor index from DataWatcher (synced from server)
            int armorIndex = horse.func_110241_cb();

            if (armorIndex > 0) {
                // Map armor index to vanilla items
                Item armorItem = null;
                switch (armorIndex) {
                    case 1:
                        armorItem = Items.iron_horse_armor;
                        break;
                    case 2:
                        armorItem = Items.golden_horse_armor;
                        break;
                    case 3:
                        armorItem = Items.diamond_horse_armor;
                        break;
                    default:
                        // Unknown armor index - likely from a mod
                        // Warn once per unique index to avoid spam
                        if (!ItemMaterialHelper.WARNED_UNKNOWN_ARMOR_INDICES.contains(armorIndex)) {
                            ItemMaterialHelper.WARNED_UNKNOWN_ARMOR_INDICES.add(armorIndex);
                            LOGGER.warn("Unknown horse armor index detected: {}. " +
                                "This is likely from a mod. Vanilla horse armor uses indices 1-3 (iron/gold/diamond). " +
                                "Modded horse armor material IDs are not currently supported.", armorIndex);
                        }
                        break;
                }

                if (armorItem != null) {
                    // Set material ID using ItemIdManager
                    ItemStack armorStack = new ItemStack(armorItem);
                    ItemIdManager.setItemId(armorStack);
                    hasArmor = true;
                }
            }
        }

        // Call original render
        original.call(model, entity, p1, p2, p3, p4, p5, p6);

        // Reset only if we set it
        if (hasArmor) {
            ItemIdManager.resetItemId();
        }
    }
}
