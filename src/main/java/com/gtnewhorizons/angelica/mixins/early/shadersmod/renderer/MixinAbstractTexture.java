package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.texture.AbstractTexture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.SoftOverride;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.angelica.client.MultiTexID;
import com.gtnewhorizons.angelica.client.ShadersTex;

@Mixin(AbstractTexture.class)
public class MixinAbstractTexture {

    public MultiTexID angelica$multiTex;

    @Inject(at = @At("HEAD"), method = "deleteGlTexture()V")
    private void angelica$deleteTextures(CallbackInfo ci) {
        ShadersTex.deleteTextures((AbstractTexture) (Object) this);
    }

    @SoftOverride
    public MultiTexID angelica$getMultiTexID() {
        return ShadersTex.getMultiTexID((AbstractTexture) (Object) this);
    }

}
