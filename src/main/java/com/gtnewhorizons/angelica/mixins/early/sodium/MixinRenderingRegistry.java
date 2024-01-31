package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
import com.gtnewhorizons.angelica.rendering.ThreadLocalISBRH;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

@Mixin(value = RenderingRegistry.class, remap = false)
public class MixinRenderingRegistry implements IRenderingRegistryExt {
    @Shadow private Map<Integer, ISimpleBlockRenderingHandler> blockRenderers;

//    private Map<Class<?>, ThreadLocal<?>> THREAD_LOCAL_MAP = new Object2ObjectOpenHashMap<>();
    private ThreadLocal<Map<Class<?>, Object>> THREAD_LOCAL_MAP = ThreadLocal.withInitial(Reference2ObjectOpenHashMap::new);
    @Override
    public ISimpleBlockRenderingHandler getISBRH(int modelId) {
        return this.blockRenderers.get(modelId);
    }

    @WrapOperation(method = { "renderWorldBlock", "renderInventoryBlock", "renderItemAsFull3DBlock" }, at = @At(value="INVOKE", target="Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getWrapped(Map<Integer, ISimpleBlockRenderingHandler> instance, Object modelId, Operation<ISimpleBlockRenderingHandler> original) {
        final ISimpleBlockRenderingHandler res = original.call(instance, modelId);
        if(res.getClass().isAnnotationPresent(ThreadLocalISBRH.class)) {
            return THREAD_LOCAL_MAP.get().computeIfAbsent(res.getClass(), k -> ThreadLocal.withInitial(() -> {
                try {
                    // Won't work with non-default constructors, like EFR
                    return res.getClass().getDeclaredConstructor().newInstance();
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        return res;
    }
}
