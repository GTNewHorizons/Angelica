package com.gtnewhorizons.angelica.compat.mojang;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

@Deprecated
public class OverlayVertexConsumer implements VertexConsumer {

    public OverlayVertexConsumer(VertexConsumer buffer, Matrix4f model, Matrix3f normal) {}

    @Override
    public VertexConsumer vertex(double d, double e, double f) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer texture(float u, float v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer overlay(int u, int v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer light(int u, int v) {
        return null;
    }

    @NotNull
    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return null;
    }

    @Override
    public void next() {

    }
}
