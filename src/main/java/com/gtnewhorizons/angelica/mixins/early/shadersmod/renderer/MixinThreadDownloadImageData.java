package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.ThreadDownloadImageData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.SoftOverride;

import com.gtnewhorizons.angelica.client.MultiTexID;

@Mixin(ThreadDownloadImageData.class)
public abstract class MixinThreadDownloadImageData extends MixinAbstractTexture {
    // TODO: PBR
    @Shadow
    private boolean textureUploaded;

    @Shadow
    public abstract int getGlTextureId();

    @SoftOverride
    public MultiTexID angelica$getMultiTexID() {
        if (!this.textureUploaded) {
            this.getGlTextureId();
        }
        return super.angelica$getMultiTexID();
    }

}
