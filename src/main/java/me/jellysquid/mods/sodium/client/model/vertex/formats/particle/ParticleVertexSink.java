package me.jellysquid.mods.sodium.client.model.vertex.formats.particle;

import com.gtnewhorizons.angelica.compat.toremove.DefaultVertexFormat;
import com.gtnewhorizons.angelica.compat.mojang.VertexFormat;
import me.jellysquid.mods.sodium.client.model.vertex.VertexSink;


public interface ParticleVertexSink extends VertexSink {
    VertexFormat VERTEX_FORMAT = DefaultVertexFormat.POSITION_TEXTURE_COLOR_LIGHT;

    /**
     * @param x The x-position of the vertex
     * @param y The y-position of the vertex
     * @param z The z-position of the vertex
     * @param u The u-texture of the vertex
     * @param v The v-texture of the vertex
     * @param color The ABGR-packed color of the vertex
     * @param light The packed light map texture coordinates of the vertex
     */
    void writeParticle(float x, float y, float z, float u, float v, int color, int light);
}
