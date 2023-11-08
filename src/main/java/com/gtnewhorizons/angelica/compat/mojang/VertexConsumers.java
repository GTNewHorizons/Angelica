package com.gtnewhorizons.angelica.compat.mojang;

public class VertexConsumers {
    public static VertexConsumer union(VertexConsumer first, VertexConsumer second) {
        return new Dual(first, second);
    }

    static class Dual implements VertexConsumer {
        private final VertexConsumer first;
        private final VertexConsumer second;

        public Dual(VertexConsumer first, VertexConsumer second) {
            if (first == second) {
                throw new IllegalArgumentException("Duplicate delegates");
            } else {
                this.first = first;
                this.second = second;
            }
        }
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.first.vertex(x, y, z);
            this.second.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.first.color(red, green, blue, alpha);
            this.second.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.first.texture(u, v);
            this.second.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.first.overlay(u, v);
            this.second.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            this.first.light(u, v);
            this.second.light(u, v);
            return this;
        }
        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.first.normal(x, y, z);
            this.second.normal(x, y, z);
            return this;
        }
        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
            this.first.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
            this.second.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }

        public void next() {
            this.first.next();
            this.second.next();
        }
    }
}
