package com.gtnewhorizons.angelica.mixins.early.angelica.thaumcraft;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.client.lib.ClientTickEventsFML;

@Mixin(ClientTickEventsFML.class)
public class MixinClientTickEventsFML {

    /**
     * Thaumcraft disables lighting and only re-enables it if actually drawing an aspect(hovering over an item that
     * you can see the aspect for and draws it). This shoves in a lighting re-enable at the end so that it is always
     * re-enabled regardless.
     */
    @Inject(method = "renderAspectsInGui", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glPopAttrib()V"), remap = false)
    private void angelica$lightingFix(CallbackInfo ci) {
        GL11.glEnable(GL11.GL_LIGHTING);
    }
}
