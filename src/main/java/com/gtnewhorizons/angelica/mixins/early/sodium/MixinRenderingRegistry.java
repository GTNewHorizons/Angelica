package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.interfaces.IThreadSafeISBRH;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(value = RenderingRegistry.class, remap = false)
public class MixinRenderingRegistry implements IRenderingRegistryExt {
    @Shadow private Map<Integer, ISimpleBlockRenderingHandler> blockRenderers;

    @Override
    public ISimpleBlockRenderingHandler getISBRH(int modelId) {
        return this.blockRenderers.get(modelId);
    }

    @WrapOperation(method = { "renderWorldBlock", "renderInventoryBlock", "renderItemAsFull3DBlock" }, at = @At(value="INVOKE", target="Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getWrapped(Map<Integer, ISimpleBlockRenderingHandler> instance, Object modelId, Operation<ISimpleBlockRenderingHandler> original) {
        final ISimpleBlockRenderingHandler res = original.call(instance, modelId);
        if(res instanceof IThreadSafeISBRH isbhr) {
            return isbhr.getThreadLocal();
        }
        return res;
    }
}
