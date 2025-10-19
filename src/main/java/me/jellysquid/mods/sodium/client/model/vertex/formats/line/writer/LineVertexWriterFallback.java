package me.jellysquid.mods.sodium.client.model.vertex.formats.line.writer;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.ColorABGR;
import com.gtnewhorizons.angelica.compat.toremove.VertexConsumer;
import me.jellysquid.mods.sodium.client.model.vertex.fallback.VertexWriterFallback;
import me.jellysquid.mods.sodium.client.model.vertex.formats.line.LineVertexSink;


public class LineVertexWriterFallback extends VertexWriterFallback implements LineVertexSink {
    public LineVertexWriterFallback(VertexConsumer consumer) {
        super(consumer);
    }

    @Override
    public void vertexLine(float x, float y, float z, int color) {
        VertexConsumer consumer = this.consumer;
        consumer.vertex(x, y, z);
        consumer.color(ColorABGR.unpackRed(color), ColorABGR.unpackGreen(color), ColorABGR.unpackBlue(color), ColorABGR.unpackAlpha(color));
        consumer.next();
    }
}
