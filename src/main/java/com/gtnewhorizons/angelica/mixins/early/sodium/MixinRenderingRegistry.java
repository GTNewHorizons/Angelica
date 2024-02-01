package com.gtnewhorizons.angelica.mixins.early.sodium;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.mixins.interfaces.IRenderingRegistryExt;
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

    private ThreadLocal<Map<Class<?>, Object>> THREAD_LOCAL_MAP = ThreadLocal.withInitial(Reference2ObjectOpenHashMap::new);
    @Override
    public ISimpleBlockRenderingHandler getISBRH(int modelId) {
        return this.blockRenderers.get(modelId);
    }

    @WrapOperation(method = { "renderWorldBlock", "renderInventoryBlock", "renderItemAsFull3DBlock" }, at = @At(value="INVOKE", target="Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object getWrapped(Map<Integer, ISimpleBlockRenderingHandler> instance, Object modelId, Operation<ISimpleBlockRenderingHandler> original) {
        // TODO: Move this to BlockRenderer

        // Get the main thread handler
        final ISimpleBlockRenderingHandler mainThreadHandler = original.call(instance, modelId);
        if(Thread.currentThread() != GLStateManager.getMainThread()) {
            ThreadSafeISBRH annotation = mainThreadHandler.getClass().getAnnotation(ThreadSafeISBRH.class);
            if (annotation != null && annotation.perThread()) {
                return THREAD_LOCAL_MAP.get().computeIfAbsent(mainThreadHandler.getClass(), k -> {
                    try {
                        // Won't work with non-default constructors, use ThreadSafeISBRHFactory instead
                        return mainThreadHandler.getClass().getDeclaredConstructor().newInstance();
                    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
                        throw new RuntimeException(e);
                    } catch(InvocationTargetException e) {
                        throw new RuntimeException(e.getCause());
                    }
                });
            } else if (mainThreadHandler.getClass().isInstance(ThreadSafeISBRHFactory.class)) {
                return THREAD_LOCAL_MAP.get().computeIfAbsent(mainThreadHandler.getClass(), k -> ((ThreadSafeISBRHFactory) mainThreadHandler).newInstance());
            }
        }
        return mainThreadHandler;
    }
}
