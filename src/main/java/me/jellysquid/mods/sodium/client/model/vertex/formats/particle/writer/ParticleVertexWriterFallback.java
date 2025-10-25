package me.jellysquid.mods.sodium.client.model.vertex.formats.particle.writer;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizons.angelica.compat.toremove.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.vertex.fallback.VertexWriterFallback;
import me.jellysquid.mods.sodium.client.model.vertex.formats.particle.ParticleVertexSink;


public class ParticleVertexWriterFallback extends VertexWriterFallback implements ParticleVertexSink {
    public ParticleVertexWriterFallback(VertexConsumer consumer) {
        super(consumer);
    }

    @Override
    public void writeParticle(float x, float y, float z, float u, float v, int color, int light) {
        VertexConsumer consumer = this.consumer;
        consumer.vertex(x, y, z);
        consumer.texture(u, v);
        consumer.color(ColorABGR.unpackRed(color), ColorABGR.unpackGreen(color), ColorABGR.unpackBlue(color), ColorABGR.unpackAlpha(color));
        consumer.light(light);
        consumer.next();
    }
}
