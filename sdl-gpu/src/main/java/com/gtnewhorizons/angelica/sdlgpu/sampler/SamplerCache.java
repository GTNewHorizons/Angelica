package com.gtnewhorizons.angelica.sdlgpu.sampler;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.locks.ReentrantLock;

import static org.lwjgl.sdl.SDLGPU.SDL_ReleaseGPUSampler;

public final class SamplerCache {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    public record Key(int minFilter, int magFilter, int wrapS, int wrapT, int wrapR, float minLod, float maxLod, float lodBias, float maxAnisotropy, int compareMode, int compareFunc) {}

    @FunctionalInterface
    public interface Factory {
        long create(Key key);
    }

    private final Object2LongOpenHashMap<Key> handles = new Object2LongOpenHashMap<>();
    private final LongArrayList allHandles = new LongArrayList();
    private final ReentrantLock lock = new ReentrantLock();

    public SamplerCache() {
        handles.defaultReturnValue(0L);
    }

    public long getOrCreate(Key key, Factory factory) {
        lock.lock();
        try {
            final long existing = handles.getLong(key);
            if (existing != 0L) return existing;
            final long fresh = factory.create(key);
            if (fresh != 0L) {
                handles.put(key, fresh);
                allHandles.add(fresh);
            }
            return fresh;
        } finally {
            lock.unlock();
        }
    }

    public void releaseAll(Device device) {
        lock.lock();
        try {
            final long dev = device.getDevice();
            if (dev == 0L) {
                handles.clear();
                allHandles.clear();
                return;
            }
            final int n = allHandles.size();
            for (int i = 0; i < n; i++) {
                final long h = allHandles.getLong(i);
                if (h != 0L) SDL_ReleaseGPUSampler(dev, h);
            }
            LOG.info("SamplerCache: released {} cached SDL samplers", n);
            handles.clear();
            allHandles.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return handles.size();
        } finally {
            lock.unlock();
        }
    }
}
