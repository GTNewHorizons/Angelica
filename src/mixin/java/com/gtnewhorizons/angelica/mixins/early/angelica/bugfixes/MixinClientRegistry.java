package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import cpw.mods.fml.client.registry.ClientRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(ClientRegistry.class)
public class MixinClientRegistry {
    @Redirect(method = "bindTileEntitySpecialRenderer", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private static Object skipPuttingNullRenderer(Map instance, Object key, Object value) {
        if(value != null && key != null) {
            return instance.put(key, value);
        } else {
            return null;
        }
    }
}
