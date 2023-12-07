package com.gtnewhorizons.angelica.compat.mojang;

import javax.annotation.Nonnull;

@Deprecated
public interface VertexConsumer {

    VertexConsumer vertex(double d, double e, double f);

    @Nonnull
    VertexConsumer color(int r, int g, int b, int a);

    @Nonnull
    VertexConsumer texture(float u, float v);

    @Nonnull
    VertexConsumer overlay(int u, int v);

    @Nonnull
    VertexConsumer light(int u, int v);

    @Nonnull
    VertexConsumer normal(float x, float y, float z);

    void next();

    default VertexConsumer overlay(int overlay) {
        return this.overlay(overlay & 0xFFFF, overlay >> 16 & 0xFFFF);
    }

    default VertexConsumer light(int light) {
        return this.light(light & 0xFFFF, light >> 16 & 0xFFFF);
    }

    default VertexConsumer color(float red, float green, float blue, float alpha) {
        return this.color((int)(red * 255.0F), (int)(green * 255.0F), (int)(blue * 255.0F), (int)(alpha * 255.0F));
    }

    default void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        this.vertex((double)x, (double)y, (double)z);
        this.color(red, green, blue, alpha);
        this.texture(u, v);
        this.overlay(overlay);
        this.light(light);
        this.normal(normalX, normalY, normalZ);
        this.next();
    }

}
