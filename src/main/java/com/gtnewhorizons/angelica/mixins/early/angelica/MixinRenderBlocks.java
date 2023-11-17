package com.gtnewhorizons.angelica.mixins.early.angelica;

import com.gtnewhorizons.angelica.rendering.ThreadedTesselatorHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderBlocks.class)
public class MixinRenderBlocks {

    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/Tessellator;instance:Lnet/minecraft/client/renderer/Tessellator;"))
    private Tessellator modifyTessellatorAccess() {
        return ThreadedTesselatorHelper.instance.getThreadTessellator();
    }

}
