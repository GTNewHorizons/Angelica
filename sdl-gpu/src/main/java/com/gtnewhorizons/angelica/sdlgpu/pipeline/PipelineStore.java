package com.gtnewhorizons.angelica.sdlgpu.pipeline;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager;
import com.gtnewhorizons.angelica.sdlgpu.shader.UscaledRetype;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToLongFunction;
import java.util.function.LongSupplier;

import static org.lwjgl.sdl.SDLGPU.SDL_ReleaseGPUGraphicsPipeline;

public final class PipelineStore {
    static final long BAD_PIPELINE_SENTINEL = -1L;

    private final Device device;
    private final Long2LongOpenHashMap map = new Long2LongOpenHashMap();
    private volatile IntToLongFunction bufferHandleResolver;

    final Set<Long> loggedMismatch = ConcurrentHashMap.newKeySet();
    final Set<Long> loggedTypeZeroAttrib = ConcurrentHashMap.newKeySet();
    final Set<Long> loggedDeadBufferAttrib = ConcurrentHashMap.newKeySet();
    final Set<Long> loggedFormatSubstitution = ConcurrentHashMap.newKeySet();

    public PipelineStore(Device device) {
        this.device = device;
        map.defaultReturnValue(0L);
    }

    Device device() { return device; }

    public void setBufferHandleResolver(IntToLongFunction resolver) { this.bufferHandleResolver = resolver; }
    IntToLongFunction bufferHandleResolver() { return bufferHandleResolver; }

    public interface VertexVariantResolver {
        ShaderManager.VertexVariant resolve(int program, long key, List<UscaledRetype.Attrib> attribs);
    }

    private volatile VertexVariantResolver vertexVariantResolver;

    public void setVertexVariantResolver(VertexVariantResolver resolver) { this.vertexVariantResolver = resolver; }
    VertexVariantResolver vertexVariantResolver() { return vertexVariantResolver; }

    public long getOrBuild(long key, LongSupplier builder) {
        synchronized (this) {
            final long hit = map.get(key);
            if (hit == BAD_PIPELINE_SENTINEL) return 0L;
            if (hit != 0L) return hit;
            final long built = builder.getAsLong();
            if (built == BAD_PIPELINE_SENTINEL) { map.put(key, BAD_PIPELINE_SENTINEL); return 0L; }
            if (built != 0L) map.put(key, built);
            return built;
        }
    }

    public void shutdown() {
        synchronized (this) {
            final LongIterator it = map.values().iterator();
            while (it.hasNext()) {
                final long v = it.nextLong();
                if (v != BAD_PIPELINE_SENTINEL) SDL_ReleaseGPUGraphicsPipeline(device.getDevice(), v);
            }
            map.clear();
        }
        loggedMismatch.clear();
        loggedTypeZeroAttrib.clear();
        loggedDeadBufferAttrib.clear();
        loggedFormatSubstitution.clear();
    }
}
