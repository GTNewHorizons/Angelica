package com.gtnewhorizons.angelica.rendering;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import com.gtnewhorizons.angelica.api.ThreadSafeISBRH;
import com.gtnewhorizons.angelica.api.ThreadSafeISBRHFactory;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.world.IBlockAccess;

public final class IsbrhDispatch {

    private static final byte KIND_MISSING = 0;
    private static final byte KIND_SHARED = 1;
    private static final byte KIND_PER_THREAD_CTOR = 2;
    private static final byte KIND_FACTORY = 3;

    private static final int DENSE_LIMIT = 4096;

    private static final class Entry {

        final ISimpleBlockRenderingHandler handler;
        final byte kind;

        Entry(ISimpleBlockRenderingHandler handler, byte kind) {
            this.handler = handler;
            this.kind = kind;
        }
    }

    private static final Entry MISSING = new Entry(null, KIND_MISSING);
    private static final Object CLASSIFY_LOCK = new Object();

    private static volatile Entry[] entries = new Entry[0];
    private static volatile int generation;

    private static final class PerThread {

        int gen = -1;
        int worldRenderDepth;
        ISimpleBlockRenderingHandler[] byId = new ISimpleBlockRenderingHandler[0];
        final Reference2ObjectOpenHashMap<Class<?>, ISimpleBlockRenderingHandler> byClass = new Reference2ObjectOpenHashMap<>();
    }

    private static final ThreadLocal<PerThread> PER_THREAD = ThreadLocal.withInitial(PerThread::new);

    private IsbrhDispatch() {}

    public static boolean isRenderingWorldBlock() {
        return PER_THREAD.get().worldRenderDepth != 0;
    }

    public static boolean renderWorldBlock(ISimpleBlockRenderingHandler handler, RenderBlocks renderer,
        IBlockAccess world, int x, int y, int z, Block block, int modelId) {
        final PerThread state = PER_THREAD.get();
        final int previousDepth = state.worldRenderDepth;
        state.worldRenderDepth = previousDepth + 1;
        try {
            return handler.renderWorldBlock(world, x, y, z, block, modelId, renderer);
        } finally {
            state.worldRenderDepth = previousDepth;
        }
    }

    public static ISimpleBlockRenderingHandler resolve(Map<Integer, ISimpleBlockRenderingHandler> map, int modelId) {
        return resolve(map, modelId, Thread.currentThread() == GLStateManager.getMainThread());
    }

    public static ISimpleBlockRenderingHandler resolve(Map<Integer, ISimpleBlockRenderingHandler> map, int modelId,
        boolean onMainThread) {
        if (modelId < 0 || modelId >= DENSE_LIMIT) {
            ISimpleBlockRenderingHandler handler = map.get(modelId);
            return handler == null ? null : adaptForCurrentThread(handler, onMainThread);
        }

        final int gen = generation;
        Entry e = classify(map, modelId);
        if (e.kind == KIND_MISSING) return null;
        if (onMainThread || e.kind == KIND_SHARED) return e.handler;

        PerThread pt = PER_THREAD.get();
        if (pt.gen != gen) {
            pt.gen = gen;
            pt.byId = new ISimpleBlockRenderingHandler[DENSE_LIMIT];
        }

        ISimpleBlockRenderingHandler cached = pt.byId[modelId];
        if (cached != null) return cached;

        ISimpleBlockRenderingHandler adapted = adapt(e.handler, e.kind);
        pt.byId[modelId] = adapted;
        return adapted;
    }

    private static ISimpleBlockRenderingHandler adaptForCurrentThread(ISimpleBlockRenderingHandler main, boolean onMainThread) {
        if (onMainThread || main == null) return main;
        return adapt(main, classifyKind(main));
    }

    public static void invalidate() {
        synchronized (CLASSIFY_LOCK) {
            generation++;
            entries = new Entry[entries.length];
        }
    }

    private static ISimpleBlockRenderingHandler adapt(ISimpleBlockRenderingHandler main, byte kind) {
        if (kind == KIND_SHARED) return main;

        Class<?> cls = main.getClass();
        PerThread pt = PER_THREAD.get();
        ISimpleBlockRenderingHandler obj = pt.byClass.get(cls);
        if (obj != null) return obj;

        ISimpleBlockRenderingHandler created;
        if (kind == KIND_FACTORY) {
            created = (ISimpleBlockRenderingHandler) ((ThreadSafeISBRHFactory) main).newInstance();
        } else {
            try {
                // Won't work with non-default constructors, use ThreadSafeISBRHFactory instead
                created = (ISimpleBlockRenderingHandler) cls.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException ex) {
                throw new RuntimeException(ex);
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(ex.getCause());
            }
        }
        pt.byClass.put(cls, created);
        return created;
    }

    private static byte classifyKind(ISimpleBlockRenderingHandler handler) {
        ThreadSafeISBRH annotation = handler.getClass().getAnnotation(ThreadSafeISBRH.class);
        if (annotation != null && annotation.perThread()) return KIND_PER_THREAD_CTOR;
        if (handler instanceof ThreadSafeISBRHFactory) return KIND_FACTORY;
        return KIND_SHARED;
    }

    private static Entry classify(Map<Integer, ISimpleBlockRenderingHandler> map, int modelId) {
        Entry[] arr = entries;
        if (modelId < arr.length) {
            Entry e = arr[modelId];
            if (e != null) return e;
        }

        synchronized (CLASSIFY_LOCK) {
            arr = entries;
            if (modelId >= arr.length) {
                Entry[] grown = new Entry[DENSE_LIMIT];
                System.arraycopy(arr, 0, grown, 0, arr.length);
                arr = grown;
            }
            Entry e = arr[modelId];
            if (e == null) {
                ISimpleBlockRenderingHandler handler = map.get(modelId);
                e = handler == null ? MISSING : new Entry(handler, classifyKind(handler));
                arr[modelId] = e;
            }
            entries = arr;
            return e;
        }
    }
}
