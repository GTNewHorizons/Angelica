package com.gtnewhorizons.angelica.sdlgpu.shader.cross;

import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Supplier;

public final class CrossCompileCache {

    public record Output(ByteBuffer code, String entrypoint) {}

    @FunctionalInterface
    public interface Uncached {
        Output compile(ByteBuffer spirv, int glShaderType);
    }

    private static final int CACHE_MAX = 256;

    private static final class CacheKey {
        private final byte[] spirvBytes;
        private final int glShaderType;
        private final int hash;

        CacheKey(byte[] b, int t) {
            this.spirvBytes = b;
            this.glShaderType = t;
            this.hash = Arrays.hashCode(b) * 31 + t;
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object o) {
            return o instanceof CacheKey k && k.glShaderType == glShaderType && k.hash == hash && Arrays.equals(k.spirvBytes, spirvBytes);
        }
    }

    private record CacheValue(byte[] codeBytes, String entrypoint) {}

    private final Object2ObjectLinkedOpenHashMap<CacheKey, CacheValue> cache = new Object2ObjectLinkedOpenHashMap<>();
    private final String layer;
    private final Supplier<String> toolchainId;
    private final Uncached uncached;

    public CrossCompileCache(String layer, Supplier<String> toolchainId, Uncached uncached) {
        this.layer = layer;
        this.toolchainId = toolchainId;
        this.uncached = uncached;
    }

    public Output compile(ByteBuffer spirv, int glShaderType) {
        final byte[] spirvHeap = ShaderCacheIO.toHeap(spirv);
        final CacheKey key = new CacheKey(spirvHeap, glShaderType);
        final CacheValue cached;
        synchronized (cache) {
            cached = cache.getAndMoveToFirst(key);
        }
        if (cached != null) return new Output(ShaderCacheIO.toNative(cached.codeBytes), cached.entrypoint);

        final ShaderDiskCache.Key diskKey = ShaderDiskCache.isEnabled() ? diskKey(spirvHeap, glShaderType) : null;
        if (diskKey != null) {
            final ShaderDiskCache.Blob blob = ShaderDiskCache.getBlob(diskKey);
            if (blob != null) {
                ShaderCacheIO.lruPut(cache, key, new CacheValue(blob.data(), blob.tag()), CACHE_MAX);
                return new Output(ShaderCacheIO.toNative(blob.data()), blob.tag());
            }
        }

        final Output out = uncached.compile(spirv, glShaderType);
        final byte[] codeHeap = ShaderCacheIO.toHeap(out.code());
        ShaderCacheIO.lruPut(cache, key, new CacheValue(codeHeap, out.entrypoint()), CACHE_MAX);
        if (diskKey != null) ShaderDiskCache.putBlob(diskKey, out.entrypoint(), codeHeap);
        return out;
    }

    public ShaderDiskCache.Key diskKey(byte[] spirvHeap, int glShaderType) {
        return ShaderDiskCache.key(layer).str(toolchainId.get()).bytes(spirvHeap).i(glShaderType);
    }
}
