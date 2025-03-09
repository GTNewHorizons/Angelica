package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(TileEntityRendererDispatcher.class)
public class MixinTileEntityRendererDispatcher {
    @Shadow
    public Map<Class<? extends TileEntity>, TileEntitySpecialRenderer> mapSpecialRenderers;

    /**
     * @author embeddedt
     * @reason accessed concurrently by our chunk meshers
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void useConcurrentMap(CallbackInfo ci) {
        this.mapSpecialRenderers = new ConcurrentHashMap<>(this.mapSpecialRenderers);
    }

    /**
     * @author embeddedt
     * @reason CHM doesn't allow putting null values
     */
    @SuppressWarnings("unchecked")
    @Redirect(method = "getSpecialRendererByClass", at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object skipPuttingNullRenderer(Map<Class<? extends TileEntity>, TileEntitySpecialRenderer> instance, Object key, Object value) {
        if(value != null && key != null) {
            return instance.put((Class<? extends TileEntity>) key, (TileEntitySpecialRenderer) value);
        } else {
            return null;
        }
    }
}
