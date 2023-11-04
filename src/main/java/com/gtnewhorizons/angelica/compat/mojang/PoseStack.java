package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.Queues;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Deque;

public class PoseStack {
    private final Deque<Pose> poseStack;

    public PoseStack() {
        this.poseStack = Queues.newArrayDeque();
        poseStack.add(new Pose(new Matrix4f().identity(), new Matrix3f().identity()));
    }

    public Pose last() {
        return (Pose)this.poseStack.getLast();
    }

    public void pushPose() {
        final Pose lv = (Pose)this.poseStack.getLast();
        this.poseStack.addLast(new Pose(new Matrix4f(lv.pose), new Matrix3f(lv.normal)));
    }

    public void popPose() {
        this.poseStack.removeLast();
    }
    public boolean clear() {
        return this.poseStack.size() == 1;
    }

    public void translate(double d, double e, double f) {
        final Pose lv = (Pose)this.poseStack.getLast();
        lv.pose.translate((float)d, (float)e, (float)f);
    }
    public void rotateX(float f) {
        final Pose lv = (Pose)this.poseStack.getLast();
        lv.pose.rotateX(f);
        lv.normal.rotateX(f);
    }

    public void rotateY(float f) {
        final Pose lv = (Pose)this.poseStack.getLast();
        lv.pose.rotateY(f);
        lv.normal.rotateY(f);
    }

    public void rotateZ(float f) {
        final Pose lv = (Pose)this.poseStack.getLast();
        lv.pose.rotateZ(f);
        lv.normal.rotateZ(f);
    }


    public void scale(float f, float g, float h) {
        final Pose lv = (Pose)this.poseStack.getLast();
        lv.pose.scale(f, g, h);

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

    public static final class Pose {
        private final Matrix4f pose;
        private final Matrix3f normal;

        private Pose(Matrix4f pose, Matrix3f normal) {
            this.pose = pose;
            this.normal = normal;
        }

        public Matrix4f pose() {
            return pose;
        }

        public Matrix3f normal() {
            return normal;
        }
    }

}
