package com.gtnewhorizons.angelica.mixins.early.angelica.models;

import com.gtnewhorizons.angelica.utils.AssetLoader;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.client.renderer.texture.IIconRegister;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockOldLeaf.class)
public abstract class MixinBlockOldLeaf {

    @Inject(method = "registerBlockIcons", at = @At(value = "TAIL"))
    public void angelica$injectTextures(IIconRegister reg, CallbackInfo ci) {
        for (String s : AssetLoader.injectTexs) {
            reg.registerIcon(s);
        }
    }
}
