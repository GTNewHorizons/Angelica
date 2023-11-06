package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.Queues;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Deque;

public class MatrixStack {
    private final Deque<Entry> matrixStack;

    public MatrixStack() {
        this.matrixStack = Queues.newArrayDeque();
        matrixStack.add(new Entry(new Matrix4f().identity(), new Matrix3f().identity()));
    }

    public Entry peek() {
        return (Entry)this.matrixStack.getLast();
    }

    public void push() {
        final Entry lv = (Entry)this.matrixStack.getLast();
        this.matrixStack.addLast(new Entry(new Matrix4f(lv.model), new Matrix3f(lv.normal)));
    }

    public void pop() {
        this.matrixStack.removeLast();
    }
    public boolean clear() {
        return this.matrixStack.size() == 1;
    }

    public void translate(double d, double e, double f) {
        final Entry lv = (Entry)this.matrixStack.getLast();
        lv.model.translate((float)d, (float)e, (float)f);
    }
    public void rotateX(float f) {
        final Entry lv = (Entry)this.matrixStack.getLast();
        lv.model.rotateX(f);
        lv.normal.rotateX(f);
    }

    public void rotateY(float f) {
        final Entry lv = (Entry)this.matrixStack.getLast();
        lv.model.rotateY(f);
        lv.normal.rotateY(f);
    }

    public void rotateZ(float f) {
        final Entry lv = (Entry)this.matrixStack.getLast();
        lv.model.rotateZ(f);
        lv.normal.rotateZ(f);
    }


    public void scale(float f, float g, float h) {
        final Entry lv = (Entry)this.matrixStack.getLast();
        lv.model.scale(f, g, h);

        if (f == g && g == h) {
            if (f > 0.0F) {
                return;
            }

            lv.normal.scale(-1.0F);
        }
        float i = 1.0F / f;
        float j = 1.0F / g;
        float k = 1.0F / h;
        float l = invSqrt(i * j * k);
        lv.normal.scale(l * i, l * j, l * k);

    }


    private static float invSqrt(float x) {
        float xhalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        x = Float.intBitsToFloat(i);
        x *= (1.5f - xhalf * x * x);
        return x;
    }

    public static final class Entry {
        private final Matrix4f model;
        private final Matrix3f normal;

        private Entry(Matrix4f model, Matrix3f normal) {
            this.model = model;
            this.normal = normal;
        }

        public Matrix4f getModel() {
            return model;
        }

        public Matrix3f getNormal() {
            return normal;
        }
    }

}
