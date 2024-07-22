package com.gtnewhorizons.angelica.api;

import com.google.common.annotations.Beta;
import lombok.Getter;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;

import static java.lang.Math.toRadians;

@Beta
public class Variant {

    @Getter
    private final ResourceLocation model;
    private final float x;
    private final float y;
    private final boolean uvLock;

    public Variant(ResourceLocation model, int x, int y, boolean uvLock) {
        this.model = model;
        this.x = (float) toRadians(x);
        this.y = (float) toRadians(y);
        this.uvLock = uvLock;
    }

    public Matrix4f getAffineMatrix() {

        return new Matrix4f()
            .translation(-.5f, -.5f, -.5f)
            .rotateLocalY(x)
            .rotateLocalX(y)
            .translateLocal(.5f, .5f, .5f);
    }
}
