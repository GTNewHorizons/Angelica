package com.gtnewhorizons.angelica.models.json;

import com.gtnewhorizons.angelica.compat.mojang.Axis;
import java.util.List;
import net.minecraftforge.common.util.ForgeDirection;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class ModelElement {

    private final Vector3f from;
    private final Vector3f to;
    private final Rotation rotation;
    private final boolean shade;
    private final List<Face> faces;

    ModelElement(Vector3f from, Vector3f vector3f, Rotation rotation, boolean shade, List<Face> faces) {
        this.from = from;
        to = vector3f;
        this.rotation = rotation;
        this.shade = shade;
        this.faces = faces;
    }


    static class Face {

        private final ForgeDirection name;
        private final Vector4f uv;
        private final String texture;
        private final ForgeDirection cullFace;
        private final int rotation;
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

        private final Vector3f origin;
        private final Axis axis;
        private final float angle;
        private final boolean rescale;

        Rotation(Vector3f origin, Axis axis, float angle, boolean rescale) {
            this.origin = origin;
            this.axis = axis;
            this.angle = angle;
            this.rescale = rescale;
        }
    }
}
