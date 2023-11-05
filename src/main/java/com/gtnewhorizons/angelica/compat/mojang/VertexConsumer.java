package com.gtnewhorizons.angelica.compat.mojang;

import com.gtnewhorizons.angelica.compat.forge.IForgeVertexBuilder;

import javax.annotation.Nonnull;

public interface VertexConsumer  extends IForgeVertexBuilder {

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
}
