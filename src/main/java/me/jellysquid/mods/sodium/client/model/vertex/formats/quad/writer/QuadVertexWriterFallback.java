package me.jellysquid.mods.sodium.client.model.vertex.formats.quad.writer;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizons.angelica.compat.toremove.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.vertex.fallback.VertexWriterFallback;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;


public class QuadVertexWriterFallback extends VertexWriterFallback implements QuadVertexSink {
    public QuadVertexWriterFallback(VertexConsumer consumer) {
        super(consumer);
    }

    @Override
    public void writeQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int normal) {
        VertexConsumer consumer = this.consumer;
        consumer.vertex(x, y, z);
        consumer.color(ColorABGR.unpackRed(color), ColorABGR.unpackGreen(color), ColorABGR.unpackBlue(color), ColorABGR.unpackAlpha(color));
        consumer.texture(u, v);
        consumer.overlay(overlay);
        consumer.light(light);
        consumer.normal(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
        consumer.next();
    }
}
