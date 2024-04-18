package com.gtnewhorizons.angelica.mixins.early.mcpatcherforge.base;

import net.minecraft.client.renderer.texture.AbstractTexture;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import jss.notfine.util.AbstractTextureExpansion;

@Mixin(AbstractTexture.class)
public abstract class MixinAbstractTexture implements AbstractTextureExpansion {

    @Shadow
    public int glTextureId;

    public void unloadGLTexture() {
        if (this.glTextureId >= 0) {
            GL11.glDeleteTextures(this.glTextureId);
            this.glTextureId = -1;
        }
    }

}
