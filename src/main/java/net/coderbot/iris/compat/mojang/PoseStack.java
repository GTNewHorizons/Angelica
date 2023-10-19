package net.coderbot.iris.compat.mojang;

import org.joml.Matrix4f;

public class PoseStack {

    public Pose last() {
        return null;
    }

    public void pushPose() {}

    public void popPose() {}

    public void translate(double v, double v1, double v2) {}

    public static final class Pose {

        public Matrix4f pose() {
            return null;
        }

        public Matrix4f normal() {
            return null;
        }
    }

}
