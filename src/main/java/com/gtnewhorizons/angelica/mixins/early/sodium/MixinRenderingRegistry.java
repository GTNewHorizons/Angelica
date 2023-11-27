package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.rendering.AngelicaBlockRenderingHandler;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderingRegistry.class)
public class MixinRenderingRegistry {
    @ModifyVariable(method = "registerBlockHandler(Lcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V", at = @At("HEAD"), index = 0, argsOnly = true, remap = false)
    private static ISimpleBlockRenderingHandler getThreadSafeWrapper(ISimpleBlockRenderingHandler handler) {
        return AngelicaBlockRenderingHandler.forHandler(handler);
    }

    @ModifyVariable(method = "registerBlockHandler(ILcpw/mods/fml/client/registry/ISimpleBlockRenderingHandler;)V", at = @At("HEAD"), index = 1, argsOnly = true, remap = false)
    private static ISimpleBlockRenderingHandler getThreadSafeWrapper2(ISimpleBlockRenderingHandler handler) {
        return AngelicaBlockRenderingHandler.forHandler(handler);
    }
}
