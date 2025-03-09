package com.prupe.mcpatcher.cit;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.mal.resource.PropertiesFile;

final class EnchantmentList {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.CUSTOM_ITEM_TEXTURES, "CIT");

    private static final float PI = (float) Math.PI;

    private static LayerMethod applyMethod;
    private static int limit;
    private static float fade;

    private final List<Layer> layers = new ArrayList<>();

    static void setProperties(PropertiesFile properties) {
        applyMethod = new Average();
        limit = 99;
        fade = 0.5f;
        if (properties != null) {
            String value = properties.getString("method", "average")
                .toLowerCase();
            if (value.equals("layered")) {
                applyMethod = new Layered();
            } else if (value.equals("cycle")) {
                applyMethod = new Cycle();
            } else if (!value.equals("average")) {
                logger.warning("%s: unknown enchantment layering method '%s'", CITUtils.CIT_PROPERTIES, value);
            }
            limit = Math.max(properties.getInt("cap", limit), 0);
            fade = Math.max(properties.getFloat("fade", fade), 0.0f);
        }
    }

    EnchantmentList(Map<Item, List<Enchantment>> enchantments, List<Enchantment> allItemEnchantments,
        ItemStack itemStack) {
        BitSet layersPresent = new BitSet();
        Map<Integer, Layer> tmpLayers = new HashMap<>();
        Item item = itemStack.getItem();
        int[] enchantmentLevels = CITUtils.getEnchantmentLevels(item, itemStack.getTagCompound());
        boolean hasEffect = itemStack.hasEffect(0);
        List<Enchantment> list = enchantments.get(item);
        if (list == null) {
            list = allItemEnchantments;
        }
        for (Enchantment enchantment : list) {
            if (enchantment.match(itemStack, enchantmentLevels, hasEffect)) {
                int level = Math.max(enchantment.lastEnchantmentLevel, 1);
                int layer = enchantment.layer;
                if (!layersPresent.get(layer)) {
                    Layer newLayer = new Layer(enchantment, level);
                    tmpLayers.put(layer, newLayer);
                    layersPresent.set(layer);
                }
            }
        }
        if (layersPresent.isEmpty()) {
            return;
        }
        while (layersPresent.cardinality() > limit) {
            int layer = layersPresent.nextSetBit(0);
            layersPresent.clear(layer);
            tmpLayers.remove(layer);
        }
        for (int i = layersPresent.nextSetBit(0); i >= 0; i = layersPresent.nextSetBit(i + 1)) {
            layers.add(tmpLayers.get(i));
        }
        applyMethod.computeIntensities(this);
    }

    boolean isEmpty() {
        return layers.isEmpty();
    }

    int size() {
        return layers.size();
    }

    Enchantment getEnchantment(int index) {
        return layers.get(index).enchantment;
    }

    float getIntensity(int index) {
        return layers.get(index).intensity;
    }

    private static final class Layer {

        final Enchantment enchantment;
        final int level;
        float intensity;

        Layer(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }

        float getEffectiveDuration() {
            return enchantment.duration + 2.0f * fade;
        }
    }

    abstract private static class LayerMethod {

        abstract void computeIntensities(EnchantmentList enchantments);

        protected void scaleIntensities(EnchantmentList enchantments, int denominator) {
            if (denominator > 0) {
                for (Layer layer : enchantments.layers) {
                    if (layer.enchantment.blendMethod.canFade()) {
                        layer.intensity = (float) layer.level / (float) denominator;
                    } else {
                        layer.intensity = layer.level > 0 ? 1.0f : 0.0f;
                    }
                }
            } else {
                for (Layer layer : enchantments.layers) {
                    layer.intensity = layer.level > 0 ? 1.0f : 0.0f;
                }
            }
        }
    }

    private static final class Average extends LayerMethod {

        @Override
        void computeIntensities(EnchantmentList enchantments) {
            int total = 0;
            for (Layer layer : enchantments.layers) {
                if (layer.enchantment.blendMethod.canFade()) {
                    total += layer.level;
                }
            }
            scaleIntensities(enchantments, total);
        }
    }

    private static final class Layered extends LayerMethod {

        @Override
        void computeIntensities(EnchantmentList enchantments) {
            int max = 0;
            for (Layer layer : enchantments.layers) {
                if (layer.enchantment.blendMethod.canFade()) {
                    // TODO: check if this is meant, there was no assignment here for some reason
                    max = Math.max(max, layer.level);
                }
            }
            scaleIntensities(enchantments, max);
        }
    }

    private static final class Cycle extends LayerMethod {

        @Override
        void computeIntensities(EnchantmentList enchantments) {
            float total = 0.0f;
            for (Layer layer : enchantments.layers) {
                if (layer.enchantment.blendMethod.canFade()) {
                    total += layer.getEffectiveDuration();
                }
            }
            float timestamp = (float) ((System.currentTimeMillis() / 1000.0) % total);
            for (Layer layer : enchantments.layers) {
                if (!layer.enchantment.blendMethod.canFade()) {
                    layer.intensity = layer.level > 0 ? 1.0f : 0.0f;
                    continue;
                }
                if (timestamp <= 0.0f) {
                    break;
                }
                float duration = layer.getEffectiveDuration();
                if (timestamp < duration) {
                    float denominator = (float) Math.sin(PI * fade / duration);
                    layer.intensity = (float) (Math.sin(PI * timestamp / duration)
                        / (denominator == 0.0f ? 1.0f : denominator));
                }
                timestamp -= duration;
            }
        }
    }
}
