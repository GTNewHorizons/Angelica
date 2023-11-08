package me.jellysquid.mods.sodium.client.model.vertex.formats.glyph;

import com.gtnewhorizons.angelica.compat.mojang.VertexConsumer;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer.GlyphVertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.writer.GlyphVertexWriterFallback;
import me.jellysquid.mods.sodium.client.model.vertex.type.BlittableVertexType;
import me.jellysquid.mods.sodium.client.model.vertex.type.VanillaVertexType;


public class GlyphVertexType implements VanillaVertexType<GlyphVertexSink>, BlittableVertexType<GlyphVertexSink> {
    @Override
    public GlyphVertexSink createBufferWriter(VertexBufferView buffer, boolean direct) {
        return new GlyphVertexBufferWriterNio(buffer);
    }

    @Override
    public GlyphVertexSink createFallbackWriter(VertexConsumer consumer) {
        return new GlyphVertexWriterFallback(consumer);
    }

    @Override
    public VertexFormat getVertexFormat() {
        return GlyphVertexSink.VERTEX_FORMAT;
    }

    @Override
    public BlittableVertexType<GlyphVertexSink> asBlittable() {
        return this;
    }
}
