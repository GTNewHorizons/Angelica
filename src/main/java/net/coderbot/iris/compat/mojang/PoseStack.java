package net.coderbot.iris.compat.mojang;

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
