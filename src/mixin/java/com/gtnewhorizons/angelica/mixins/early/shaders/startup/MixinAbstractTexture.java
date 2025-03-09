package com.gtnewhorizons.angelica.mixins.early.shaders.startup;

import com.gtnewhorizons.angelica.glsm.texture.TextureTracker;
import net.minecraft.client.renderer.texture.AbstractTexture;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractTexture.class)
public class MixinAbstractTexture {
    @Shadow
    public int glTextureId;

    // Inject after the newly-generated texture ID has been stored into the id field
    @Inject(method = "getGlTextureId()I", at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/client/renderer/texture/AbstractTexture;glTextureId:I", shift = At.Shift.AFTER))
    private void iris$afterGenerateId(CallbackInfoReturnable<Integer> cir) {
        TextureTracker.INSTANCE.trackTexture(glTextureId, (AbstractTexture) (Object) this);
    }
}
