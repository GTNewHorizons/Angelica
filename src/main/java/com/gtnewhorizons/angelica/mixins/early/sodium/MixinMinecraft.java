package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.IRenderGlobalExt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Shadow
    public RenderGlobal renderGlobal;

    @Inject(method="resize", at=@At("TAIL"))
    private void sodium$resize(int width, int height, CallbackInfo ci) {
        if(this.renderGlobal != null) {
            ((IRenderGlobalExt)this.renderGlobal).scheduleTerrainUpdate();
        }
    }

}
