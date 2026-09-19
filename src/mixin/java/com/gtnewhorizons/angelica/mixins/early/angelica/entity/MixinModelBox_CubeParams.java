package com.gtnewhorizons.angelica.mixins.early.angelica.entity;

import com.gtnewhorizons.angelica.glsm.ffp.CubeParams;
import com.gtnewhorizons.angelica.mixins.interfaces.ModelBoxData;
import com.gtnewhorizons.angelica.rendering.tesr.ModelBoxCapture;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.TexturedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBox.class)
public abstract class MixinModelBox_CubeParams implements ModelBoxData {

    @Shadow
    private TexturedQuad[] quadList;

    @Unique
    private CubeParams angelica$cubeParams;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void angelica$captureCubeParams(ModelRenderer part, int texU, int texV, float x, float y, float z, int sizeX, int sizeY, int sizeZ, float inflate, CallbackInfo ci) {
        if (((Object) this).getClass() != ModelBox.class) return;
        this.angelica$cubeParams = ModelBoxCapture.capture(this.quadList, part.textureWidth, part.textureHeight, part.mirror, texU, texV, x, y, z, sizeX, sizeY, sizeZ, inflate);
    }

    @Override
    public CubeParams angelica$cubeParams() {
        return this.angelica$cubeParams;
    }
}
