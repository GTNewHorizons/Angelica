package com.gtnewhorizons.angelica.mixins.early.sodium;

import cpw.mods.fml.client.registry.ClientRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;

@Mixin(ClientRegistry.class)
public class MixinClientRegistry {

    @SuppressWarnings("unchecked")
    @Redirect(method = "bindTileEntitySpecialRenderer", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"), remap = false)
    private static Object skipPuttingNullRenderer(Map<Class<? extends TileEntity>, TileEntitySpecialRenderer> instance, Object key, Object value) {
        if(value != null && key != null) {
            return instance.put((Class<? extends TileEntity>) key, (TileEntitySpecialRenderer) value);
        } else {
            return null;
        }
    }
}
