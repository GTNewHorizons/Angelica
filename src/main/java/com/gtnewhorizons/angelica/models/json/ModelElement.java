package com.gtnewhorizons.angelica.models.json;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import java.util.List;
import lombok.Getter;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ModelElement {

    @Getter
    private final Vector3f from;
    @Getter
    private final Vector3f to;
    @Nullable
    @Getter
    private final Rotation rotation;
    private final boolean shade;
    @Getter
    private final List<Face> faces;

    ModelElement(Vector3f from, Vector3f vector3f, @Nullable Rotation rotation, boolean shade, List<Face> faces) {
        this.from = from;
        to = vector3f;
        this.rotation = rotation;
        this.shade = shade;
        this.faces = faces;
    }


    static class Face {

        @Getter
        private final ForgeDirection name;
        @Getter
        private final Vector4f uv;
        @Getter
        private final String texture;
        @Getter
        private final ForgeDirection cullFace;
        @Getter
        private final int rotation;
        @Getter
        private final int tintIndex;

        Face(ForgeDirection name, Vector4f uv, String texture, ForgeDirection cullFace, int rotation, int tintIndex) {
            this.name = name;
            this.uv = uv;
            this.texture = texture;
            this.cullFace = cullFace;
            this.rotation = rotation;
            this.tintIndex = tintIndex;
        }
    }

    static class Rotation {

        static final Rotation NOOP = new Rotation(
            new Vector3f(0, 0, 0),
            Axis.X,
            0,
            false
        );

        private final Vector3f origin;
        private final Axis axis;
        private final float angle;
        private final boolean rescale;

        Rotation(Vector3f origin, Axis axis, float angle, boolean rescale) {
            this.origin = origin;
            this.axis = axis;
            this.angle = (float) Math.toRadians(angle);
            this.rescale = rescale;
        }

        Matrix4f getAffineMatrix() {

            // Subtract origin
            final Matrix4f ret = new Matrix4f().translation(-this.origin.x, -this.origin.y, -this.origin.z);

            // Rotate
            switch (this.axis) {
                case X -> ret.rotateLocalX(angle);
                case Y -> ret.rotateLocalY(angle);
                case Z -> ret.rotateLocalZ(angle);
            }

            // Add the origin back in
            return ret.translateLocal(this.origin.x, this.origin.y, this.origin.z);
        }
    }
}
