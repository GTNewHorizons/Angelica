package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import cpw.mods.fml.client.FMLClientHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLClientHandler.class, remap = false)
public class MixinFMLClientHandler {
    /**
     * @author mitchej123
     * @reason Remove more traces of Optifine
     */
    @Overwrite
    private void detectOptifine() {
        // Do nothing
    }

    /**
     * @author mitchej123
     * @reason Remove more traces of Optifine
     */
    @Overwrite
    public boolean hasOptifine() {
        return false;
    }

    @Inject(method = "processWindowMessages", at = @At("RETURN"))
    private void angelica$fireLoadingCheckpoint(CallbackInfo ci) {
        if (GLSMHooks.LOADING_CHECKPOINT.hasListeners()) {
            GLSMHooks.LOADING_CHECKPOINT.post(GLSMHooks.loadingCheckpointEvent);
        }
    }
}
