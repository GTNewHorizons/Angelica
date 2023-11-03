package com.gtnewhorizons.angelica.mixins.early.shadersmod.renderer;

import net.minecraft.client.renderer.texture.ITextureObject;

import org.spongepowered.asm.mixin.Mixin;

import com.gtnewhorizons.angelica.client.MultiTexID;

@Mixin(ITextureObject.class)
public interface MixinITextureObject {

    MultiTexID angelica$getMultiTexID();

}
