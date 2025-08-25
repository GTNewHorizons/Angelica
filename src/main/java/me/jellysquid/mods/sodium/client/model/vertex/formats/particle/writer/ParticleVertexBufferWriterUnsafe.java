package me.jellysquid.mods.sodium.client.model.vertex.formats.particle.writer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.ParticleVertexSink;

public class ParticleVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements ParticleVertexSink {
    public ParticleVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
        super(backingBuffer, VanillaVertexTypes.PARTICLES);
    }

    @Override
    public void writeParticle(float x, float y, float z, float u, float v, int color, int light) {
        long i = this.writePointer;

        memPutFloat(i, x);
        memPutFloat(i + 4, y);
        memPutFloat(i + 8, z);
        memPutFloat(i + 12, u);
        memPutFloat(i + 16, v);
        memPutInt(i + 20, color);
        memPutInt(i + 24, light);

        this.advance();
    }
}
