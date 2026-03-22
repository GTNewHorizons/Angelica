package com.gtnewhorizons.angelica.mixins.early.celeritas.light;

import com.gtnewhorizons.angelica.api.ExtQuadLightData;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = QuadLightData.class, remap = false)
public class MixinQuadLightData implements ExtQuadLightData {

    @Unique private final float[] angelica$r = new float[4];
    @Unique private final float[] angelica$g = new float[4];
    @Unique private final float[] angelica$b = new float[4];
    @Unique private boolean angelica$rgbValid;
    @Unique private final float[] angelica$skyR = new float[4];
    @Unique private final float[] angelica$skyG = new float[4];
    @Unique private final float[] angelica$skyB = new float[4];
    @Unique private boolean angelica$skyRGBValid;

    @Override
    public float[] angelica$getR() {
        return angelica$r;
    }

    @Override
    public float[] angelica$getG() {
        return angelica$g;
    }

    @Override
    public float[] angelica$getB() {
        return angelica$b;
    }

    @Override
    public boolean angelica$isRGBValid() {
        return angelica$rgbValid;
    }

    @Override
    public void angelica$setRGBValid(boolean valid) {
        this.angelica$rgbValid = valid;
    }

    @Override
    public float[] angelica$getSkyR() {
        return angelica$skyR;
    }

    @Override
    public float[] angelica$getSkyG() {
        return angelica$skyG;
    }

    @Override
    public float[] angelica$getSkyB() {
        return angelica$skyB;
    }

    @Override
    public boolean angelica$isSkyRGBValid() {
        return angelica$skyRGBValid;
    }

    @Override
    public void angelica$setSkyRGBValid(boolean valid) {
        this.angelica$skyRGBValid = valid;
    }
}
