package jss.notfine.mixins.early.minecraft;

import jss.notfine.core.Settings;
import jss.notfine.render.RenderStars;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    void notFine$toggleSky(CallbackInfo ci) {
        if(!Settings.MODE_SKY.isValueBase()) ci.cancel();
    }

    /**
     * @author jss2a98aj
     * @reason Control star generation.
     */
    @Overwrite
    private void renderStars() {
        RenderStars.renderStars();
    }

}
