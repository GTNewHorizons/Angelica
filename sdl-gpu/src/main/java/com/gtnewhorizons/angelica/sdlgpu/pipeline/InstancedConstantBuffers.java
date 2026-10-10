package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.resource.ResourceManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Metal cannot step a vertex buffer by zero bytes, and SDL cannot select Metal's constant step function, so a constant
 * attribute bound per instance reads the next 16 bytes for every instance.
 */
final class InstancedConstantBuffers {

    private static final int BYTES_PER_VALUE = 16;
    private static final int MAX_VALUES = 64;

    private final ResourceManager resources;
    private final Long2ObjectOpenHashMap<Entry> byValue = new Long2ObjectOpenHashMap<>();
    private ByteBuffer staging;

    private static final class Entry {
        final long xy, zw;
        long buffer;
        int instances;

        Entry(long xy, long zw) {
            this.xy = xy;
            this.zw = zw;
        }
    }

    InstancedConstantBuffers(ResourceManager resources) {
        this.resources = resources;
    }

    long buffer(float x, float y, float z, float w, int instances, DrawDispatch.FanUploadSink upload) {
        final long xy = Hashing.packHiLo(Float.floatToRawIntBits(x), Float.floatToRawIntBits(y));
        final long zw = Hashing.packHiLo(Float.floatToRawIntBits(z), Float.floatToRawIntBits(w));
        final long key = Hashing.fmix64(Hashing.fmix64(0L, xy), zw);
        Entry entry = byValue.get(key);
        if (entry != null && (entry.xy != xy || entry.zw != zw)) {
            resources.releaseBufferDeferred(entry.buffer);
            byValue.remove(key);
            entry = null;
        }
        if (entry != null && entry.instances >= instances) return entry.buffer;

        if (entry == null) {
            if (byValue.size() >= MAX_VALUES) clear();
            entry = new Entry(xy, zw);
            byValue.put(key, entry);
        } else {
            resources.releaseBufferDeferred(entry.buffer);
        }

        final int capacity = instances <= 1 ? 1 : Integer.highestOneBit(instances - 1) << 1;
        final int bytes = capacity * BYTES_PER_VALUE;
        entry.buffer = resources.createVertexBuffer(bytes);
        entry.instances = entry.buffer == 0 ? 0 : capacity;
        if (entry.buffer == 0) return 0;

        if (staging == null || staging.capacity() < bytes) {
            if (staging != null) MemoryUtil.memFree(staging);
            staging = MemoryUtil.memAlloc(bytes);
        }
        staging.clear();
        for (int i = 0; i < capacity; i++) {
            staging.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
        }
        staging.flip();
        upload.enqueuePreCopied(staging, entry.buffer, 0, false);
        return entry.buffer;
    }

    void clear() {
        for (Entry entry : byValue.values()) {
            resources.releaseBufferDeferred(entry.buffer);
        }
        byValue.clear();
    }
}
