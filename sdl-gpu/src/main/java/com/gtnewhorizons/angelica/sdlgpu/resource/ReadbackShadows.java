package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUBufferRegion;
import org.lwjgl.sdl.SDL_GPUTransferBufferCreateInfo;
import org.lwjgl.sdl.SDL_GPUTransferBufferLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class ReadbackShadows {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final class Slot {
        long transferBuffer;
        int capacity;
        boolean valid;
    }

    private final Device device;
    private final Long2ObjectOpenHashMap<Slot> slots = new Long2ObjectOpenHashMap<>();

    public ReadbackShadows(Device device) {
        this.device = device;
    }

    private static long key(int glId, long offset) {
        return ((long) glId << 32) | (offset & 0xFFFFFFFFL);
    }

    public boolean scheduleDownload(long copyPass, long srcHandle, long srcOffset, int dstGlId, long dstOffset, long size) {
        if (copyPass == 0 || srcHandle == 0 || size <= 0 || size > Integer.MAX_VALUE) return false;

        final Slot slot = ensureSlot(dstGlId, dstOffset, (int) size);
        if (slot == null) return false;

        try (MemoryStack stack = stackPush()) {
            final SDL_GPUBufferRegion src = SDL_GPUBufferRegion.calloc(stack)
                .buffer(srcHandle)
                .offset((int) srcOffset)
                .size((int) size);
            final SDL_GPUTransferBufferLocation dst = SDL_GPUTransferBufferLocation.calloc(stack)
                .transfer_buffer(slot.transferBuffer)
                .offset(0);
            SDL_DownloadFromGPUBuffer(copyPass, src, dst);
        }
        slot.valid = true;
        return true;
    }

    public void invalidate(int glId, long offset) {
        final Slot slot = slots.get(key(glId, offset));
        if (slot != null) slot.valid = false;
    }

    public boolean serve(int glId, long offset, ByteBuffer out, int len) {
        final Slot slot = slots.get(key(glId, offset));
        if (slot == null || !slot.valid || slot.transferBuffer == 0 || slot.capacity < len) return false;

        final long mapped = nSDL_MapGPUTransferBuffer(device.getDevice(), slot.transferBuffer, false);
        if (mapped == 0) {
            LOG.error("Failed to map readback transfer buffer for glId={} offset={}: {}", glId, offset, SDLError.SDL_GetError());
            return false;
        }
        MemoryUtil.memCopy(mapped, MemoryUtil.memAddress(out), len);
        SDL_UnmapGPUTransferBuffer(device.getDevice(), slot.transferBuffer);
        out.position(out.position() + len);
        return true;
    }

    public boolean hasSlotsFor(int glId) {
        for (long k : slots.keySet()) {
            if ((int) (k >>> 32) == glId) return true;
        }
        return false;
    }

    public void release(int glId) {
        final LongArrayList doomed = new LongArrayList();
        for (long k : slots.keySet()) {
            if ((int) (k >>> 32) == glId) doomed.add(k);
        }
        for (int i = 0; i < doomed.size(); i++) {
            final Slot slot = slots.remove(doomed.getLong(i));
            if (slot != null) releaseSlot(slot);
        }
    }

    public void dispose() {
        for (Slot slot : slots.values()) releaseSlot(slot);
        slots.clear();
    }

    private Slot ensureSlot(int glId, long offset, int size) {
        final long k = key(glId, offset);
        Slot slot = slots.get(k);
        if (slot != null && slot.capacity >= size) return slot;

        if (slot == null) {
            slot = new Slot();
            slots.put(k, slot);
        } else {
            releaseSlot(slot);
        }

        try (MemoryStack stack = stackPush()) {
            final SDL_GPUTransferBufferCreateInfo ci = SDL_GPUTransferBufferCreateInfo.calloc(stack)
                .usage(SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD)
                .size(size);
            slot.transferBuffer = SDL_CreateGPUTransferBuffer(device.getDevice(), ci);
        }
        if (slot.transferBuffer == 0) {
            LOG.error("Failed to create readback transfer buffer size={}: {}", size, SDLError.SDL_GetError());
            slots.remove(k);
            return null;
        }
        slot.capacity = size;
        slot.valid = false;
        return slot;
    }

    private void releaseSlot(Slot slot) {
        if (slot.transferBuffer != 0 && device.getDevice() != 0) {
            SDL_ReleaseGPUTransferBuffer(device.getDevice(), slot.transferBuffer);
        }
        slot.transferBuffer = 0;
        slot.capacity = 0;
    }
}
