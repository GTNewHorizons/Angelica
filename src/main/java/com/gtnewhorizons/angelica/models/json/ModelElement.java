package com.gtnewhorizons.angelica.models.json;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import java.util.List;
import lombok.Getter;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

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

        Vector3f applyTo(final Vector3f in) {

            final Vector3f ret = in.sub(origin, new Vector3f());

            final Matrix3d rotMat = switch (this.axis) {
                case X -> new Matrix3d(
                    1, 0, 0,
                    0, cos(angle), -sin(angle),
                    0, sin(angle), cos(angle)
                );
                case Y -> new Matrix3d(
                    cos(angle), 0, sin(angle),
                    0, 1, 0,
                    -sin(angle), 0, cos(angle)
                );
                case Z -> new Matrix3d(
                    cos(angle), -sin(angle), 0,
                    sin(angle), cos(angle), 0,
                    0, 0, 1
                );
            };

            return ret.mul(rotMat, new Vector3f()).add(origin);
        }
    }
}
