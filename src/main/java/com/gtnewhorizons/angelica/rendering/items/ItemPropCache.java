package com.gtnewhorizons.angelica.rendering.items;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class ItemPropCache<V> {

    private static final int EXPIRY_TICKS = 1_200;

    private final Object2ObjectLinkedOpenHashMap<ItemProp, Slot<V>> cache = new Object2ObjectLinkedOpenHashMap<>(64);
    private final ItemProp key = new ItemProp();
    private final IntSupplier capacity;
    private final Supplier<V> factory;
    private final Consumer<V> disposer;
    private int smallestExpiry;

    public ItemPropCache(IntSupplier capacity, Supplier<V> factory, Consumer<V> disposer) {
        this.capacity = capacity;
        this.factory = factory;
        this.disposer = disposer;
    }

    public V get(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
        key.set(minU, minV, maxU, maxV, widthSubdivisions, heightSubdivisions, thickness);
        if (cache.isEmpty()) return null;
        final Slot<V> slot = cache.getAndMoveToLast(key);
        if (slot == null) return null;
        final int time = elapsedTicks();
        slot.expiry = time + EXPIRY_TICKS;
        if (time > smallestExpiry && time > (smallestExpiry = cache.get(cache.firstKey()).expiry + 20)) {
            dispose(cache.removeFirst());
            if (!cache.isEmpty()) {
                smallestExpiry = cache.get(cache.firstKey()).expiry + 20;
            }
        }
        return slot.value;
    }

    public V insert() {
        final Slot<V> slot;
        if (cache.size() >= capacity.getAsInt()) {
            final ItemProp oldest = cache.firstKey();
            slot = cache.removeFirst();
            oldest.set(key);
            cache.put(oldest, slot);
        } else {
            slot = new Slot<>();
            slot.value = factory.get();
            cache.put(new ItemProp(key), slot);
        }
        slot.expiry = elapsedTicks() + EXPIRY_TICKS;
        return slot.value;
    }

    public void clear() {
        for (Slot<V> slot : cache.values()) {
            dispose(slot);
        }
        cache.clear();
        smallestExpiry = 0;
    }

    private void dispose(Slot<V> slot) {
        if (disposer != null && slot.value != null) {
            disposer.accept(slot.value);
        }
    }

    private static int elapsedTicks() {
        return Minecraft.getMinecraft().thePlayer.ticksExisted;
    }

    private static final class Slot<V> {
        private V value;
        private int expiry;
    }

    @NoArgsConstructor
    @Data
    static final class ItemProp {
        private float minU;
        private float minV;
        private float maxU;
        private float maxV;
        private int widthSubdivisions;
        private int heightSubdivisions;
        private float thickness;

        ItemProp(ItemProp old) {
            set(old);
        }

        void set(ItemProp other) {
            set(other.minU, other.minV, other.maxU, other.maxV, other.widthSubdivisions, other.heightSubdivisions, other.thickness);
        }

        void set(float minU, float minV, float maxU, float maxV, int widthSubdivisions, int heightSubdivisions, float thickness) {
            this.minU = minU;
            this.minV = minV;
            this.maxU = maxU;
            this.maxV = maxV;
            this.widthSubdivisions = widthSubdivisions;
            this.heightSubdivisions = heightSubdivisions;
            this.thickness = thickness;
        }
    }
}
