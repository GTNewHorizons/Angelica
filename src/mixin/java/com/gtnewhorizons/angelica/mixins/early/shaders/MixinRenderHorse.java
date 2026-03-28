package com.gtnewhorizons.angelica.mixins.early.shaders;

import com.gtnewhorizons.angelica.rendering.ModelHorseArmor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.coderbot.iris.uniforms.CapturedRenderingState;
import net.coderbot.iris.uniforms.ItemIdManager;
import net.coderbot.iris.uniforms.ItemMaterialHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderHorse;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin to set the currentRenderedItem ID on the armor portion of horse rendering.
 */
@Mixin(RenderHorse.class)
public abstract class MixinRenderHorse {
    @Unique
    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    @Unique
    private static final float ARMOR_INFLATE = 0.125F;

    @Unique
    private static ModelHorseArmor angelica$armorModel;

    /**
     * Reverse mapping from armor index to Item, built lazily by scanning the item registry
     * for items recognized by EntityHorse.func_146085_a (isHorseArmor) and invoking
     * EntityHorse.getHorseArmorIndex to determine their index.
     * Automatically picks up modded armor that hooks into the vanilla's system. (I hope)
     */
    @Unique
    private static Map<Integer, Item> angelica$armorIndexToItem;

    @Unique
    private static final Map<String, ResourceLocation> angelica$armorTextureCache = new HashMap<>();

    /**
     * Two-pass rendering with a separate inflated armor model:
     * Pass 1: Render the base horse with the composite texture, no item ID.
     * Pass 2: Render the inflated armor model with the armor-only texture and item ID set.
     *         The inflate makes armor geometry sit above the base.
     *         Alpha test discards transparent (non-armor) pixels.
     */
    @WrapOperation(
        method = "renderModel(Lnet/minecraft/entity/passive/EntityHorse;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V")
    )
    private void iris$setHorseArmorItemId(ModelBase model, net.minecraft.entity.Entity entity, float p1, float p2, float p3, float p4, float p5, float p6, Operation<Void> original, EntityHorse horse) {
        // Pass 1: Render the base horse normally
        original.call(model, entity, p1, p2, p3, p4, p5, p6);

        if (!horse.func_110259_cr()) return;

        int armorIndex = horse.func_110241_cb();
        if (armorIndex <= 0) return;

        String armorTexturePath = horse.getVariantTexturePaths()[2];
        if (armorTexturePath == null) return;

        // Pass 2: Render inflated armor model with item ID
        if (angelica$armorModel == null) {
            angelica$armorModel = new ModelHorseArmor(ARMOR_INFLATE);
        }

        Item armorItem = angelica$getArmorItem(horse, armorIndex);
        if (armorItem != null) {
            int id = ItemMaterialHelper.getMaterialId(armorItem, 0);
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(id);
        }

        // Copy animation state from the base model to the armor model
        angelica$copyRotations(model, angelica$armorModel);

        ResourceLocation armorTexture = angelica$armorTextureCache.computeIfAbsent(armorTexturePath, ResourceLocation::new);
        Minecraft.getMinecraft().getTextureManager().bindTexture(armorTexture);

        angelica$armorModel.render(horse, p1, p2, p3, p4, p5, p6);

        ItemIdManager.resetItemId();
    }

    // Copy rotation angles and positions from one model's renderers to another's
    @Unique
    private static void angelica$copyRotations(ModelBase from, ModelBase to) {
        for (int i = 0; i < from.boxList.size() && i < to.boxList.size(); i++) {
            ModelRenderer src = from.boxList.get(i);
            ModelRenderer dst = to.boxList.get(i);
            dst.rotateAngleX = src.rotateAngleX;
            dst.rotateAngleY = src.rotateAngleY;
            dst.rotateAngleZ = src.rotateAngleZ;
            dst.rotationPointX = src.rotationPointX;
            dst.rotationPointY = src.rotationPointY;
            dst.rotationPointZ = src.rotationPointZ;
        }
    }

    /**
     * Look up the item for a given armor index. On first call, builds a reverse mapping
     * by scanning the item registry for items that pass EntityHorse.func_146085_a (isHorseArmor)
     * and invoking the getHorseArmorIndex to determine their index.
     */
    @Unique
    private static Item angelica$getArmorItem(EntityHorse horse, int armorIndex) {
        if (angelica$armorIndexToItem == null) {
            angelica$armorIndexToItem = new HashMap<>();
            AccessorEntityHorse accessor = (AccessorEntityHorse) horse;
            for (Object obj : Item.itemRegistry) {
                Item item = (Item) obj;
                if (!EntityHorse.func_146085_a(item)) continue;
                int idx = accessor.invokeGetHorseArmorIndex(new ItemStack(item));
                if (idx > 0) {
                    angelica$armorIndexToItem.put(idx, item);
                }
            }
            LOGGER.debug("Built horse armor index map: {} entries", angelica$armorIndexToItem.size());
        }
        return angelica$armorIndexToItem.get(armorIndex);
    }
}
