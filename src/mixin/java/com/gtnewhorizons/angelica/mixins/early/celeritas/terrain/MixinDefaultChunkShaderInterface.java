package com.gtnewhorizons.angelica.mixins.early.celeritas.terrain;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities;
import com.gtnewhorizons.angelica.rendering.celeritas.SectionAgeMath;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloatArray;
import org.embeddedt.embeddium.impl.render.chunk.shader.DefaultChunkShaderInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.WeakHashMap;

/**  Skips section-age uniform uploads that cannot have changed; see {@link SectionAgeMath} */
@Mixin(value = DefaultChunkShaderInterface.class, remap = false)
public abstract class MixinDefaultChunkShaderInterface {

    @Shadow @Final private GlUniformFloatArray uniformChunkAges;

    /** Per-region {@link SectionAgeMath} skip state. */
    @Unique private final WeakHashMap<long[], long[]> angelica$ageState = new WeakHashMap<>();

    @Unique private FloatBuffer angelica$ageScratch;

    public void setSectionAges(long timestamp, long[] loadTimes, long newestLoadTime) {
        final int n = loadTimes.length;
        if (this.uniformChunkAges == null || n == 0) return;

        final long tsQ = SectionAgeMath.quantize(timestamp);
        long[] state = angelica$ageState.get(loadTimes);
        if (state != null && SectionAgeMath.canSkip(state, newestLoadTime, tsQ)) {
            return;
        }

        final boolean allSaturated = angelica$buildAndUpload(tsQ, loadTimes, n);

        if (state == null) {
            state = SectionAgeMath.newState();
            angelica$ageState.put(loadTimes, state);
        }
        SectionAgeMath.record(state, newestLoadTime, tsQ, allSaturated);
    }

    /**
     * @author Angelica
     * @reason reused direct buffer
     */
    @Overwrite
    public void setSectionAges(long timestamp, long[] loadTimes) {
        final int n = loadTimes.length;
        if (this.uniformChunkAges == null || n == 0) return;
        angelica$buildAndUpload(timestamp, loadTimes, n);
    }

    @Unique
    private boolean angelica$buildAndUpload(long timestamp, long[] loadTimes, int n) {
        FloatBuffer buf = angelica$ageScratch;
        if (buf == null || buf.capacity() < n) {
            buf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            angelica$ageScratch = buf;
        }
        long ptr = MemoryUtilities.memAddress(buf);
        boolean allSaturated = true;
        for (int i = 0; i < n; i++) {
            final long ageMs = (timestamp - loadTimes[i]) / 1_000_000L;
            final float v;
            if (ageMs >= 30000L) {
                v = 30000f;
            } else {
                v = (float) ageMs;
                allSaturated = false;
            }
            MemoryUtilities.memPutFloat(ptr, v);
            ptr += 4;
        }
        buf.position(0).limit(n);
        this.uniformChunkAges.set(buf);
        return allSaturated;
    }
}
