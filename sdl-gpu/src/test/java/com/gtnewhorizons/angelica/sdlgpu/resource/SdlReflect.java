package com.gtnewhorizons.angelica.sdlgpu.resource;

import java.util.Map;
import java.util.Queue;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.sdlgpu.frame.ContextState;

public final class SdlReflect {
    private SdlReflect() {}

    public static void putTextureHandle(ResourceManager rm, int glId, long handle) {
        final Object handles = Reflect.get(rm, "textureHandles");
        Reflect.invoke(handles, "put", new Class<?>[] { int.class, long.class }, glId, handle);
    }

    public static void putComputeStandIn(ResourceManager rm, int glFormat, int glTarget, boolean readWrite, long handle) {
        final Object cache = Reflect.get(rm, "computeStandInTextures");
        Reflect.invoke(cache, "put", new Class<?>[] { long.class, long.class },
            ResourceManager.computeStandInKey(glFormat, glTarget, readWrite), handle);
    }

    static void putReadbackSlot(ReadbackShadows shadows, int glId, long offset, long handle, int capacity, boolean valid) {
        final Class<?> slotClass;
        try {
            slotClass = Class.forName("com.gtnewhorizons.angelica.sdlgpu.resource.ReadbackShadows$Slot");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
        final Object slot = Reflect.construct(slotClass);
        Reflect.setDeclared(slotClass, slot, "transferBuffer", handle);
        Reflect.setDeclared(slotClass, slot, "capacity", capacity);
        Reflect.setDeclared(slotClass, slot, "valid", valid);
        readbackSlots(shadows).put(readbackKey(glId, offset), slot);
    }

    static boolean readbackSlotValid(ReadbackShadows shadows, int glId, long offset) {
        return Reflect.get(readbackSlots(shadows).get(readbackKey(glId, offset)), "valid");
    }

    private static long readbackKey(int glId, long offset) {
        return (((long) glId) << 32) | (offset & 0xFFFFFFFFL);
    }

    private static Map<Long, Object> readbackSlots(ReadbackShadows shadows) {
        return Reflect.get(shadows, "slots");
    }

    static void recordBuffer(ResourceManager rm, int glId, long handle, long size, int usage) {
        rm.recordBufferSizeOnly(glId, size);
        putIntKeyed(rm, "bufferHandles", long.class, glId, handle);
        putIntKeyed(rm, "bufferUsages", int.class, glId, usage);
    }

    static void markUndefined(ResourceManager rm, int glId) {
        Reflect.invoke(Reflect.get(rm, "undefinedContentBuffers"), "add", new Class<?>[] { int.class }, glId);
    }

    static int pendingFreeCount(TransferThread tt) {
        return Reflect.<Queue<?>>get(tt, "pendingFrees").size();
    }

    static void forceSubmittedSeq(TransferThread tt, long seq) {
        Reflect.setDeclared(TransferThread.class, tt, "submittedSeq", seq);
        final Object lock = Reflect.get(tt, "submittedLock");
        synchronized (lock) { lock.notifyAll(); }
    }

    static PersistentMapping mapping(ResourceManager rm, int glId) {
        return rm.getPersistentMapping(glId);
    }

    private static void putIntKeyed(Object owner, String field, Class<?> valueType, int key, Object value) {
        Reflect.invoke(Reflect.get(owner, field), "put", new Class<?>[] { int.class, valueType }, key, value);
    }
}
