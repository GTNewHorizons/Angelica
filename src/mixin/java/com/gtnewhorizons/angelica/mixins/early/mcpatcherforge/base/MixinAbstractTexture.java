package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.base;

import com.gtnewhorizons.angelica.glsm.managers.GLTextureManager;
import net.minecraft.client.renderer.texture.AbstractTexture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import jss.notfine.util.AbstractTextureExpansion;

@Mixin(AbstractTexture.class)
public abstract class MixinAbstractTexture implements AbstractTextureExpansion {

    @Shadow
    public int glTextureId;

    public void unloadGLTexture() {
        if (this.glTextureId >= 0) {
            GLTextureManager.glDeleteTextures(this.glTextureId);
            this.glTextureId = -1;
        }
    }

}
