package com.gtnewhorizons.angelica.sdlgpu.resource;

import com.gtnewhorizons.angelica.sdlgpu.device.Device;
import com.gtnewhorizons.angelica.sdlgpu.frame.FenceTracker;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLError;
import org.lwjgl.sdl.SDL_GPUBufferRegion;
import org.lwjgl.sdl.SDL_GPUTransferBufferLocation;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.sdl.SDLGPU.*;
import static org.lwjgl.system.MemoryStack.stackPush;

public final class ReadbackShadows {

    private static final Logger LOG = LogManager.getLogger("Angelica-SDLGPU");

    private static final class Slot {
        long transferBuffer;
        int capacity;
        int validLen;
        boolean valid;
        long sync;
    }

    private final Device device;
    private final ResourceManager resourceManager;
    private final FenceTracker fenceTracker;
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<Slot>> byBuffer = new Int2ObjectOpenHashMap<>();

    public ReadbackShadows(Device device, ResourceManager resourceManager, FenceTracker fenceTracker) {
        this.device = device;
        this.resourceManager = resourceManager;
        this.fenceTracker = fenceTracker;
    }

    public void scheduleDownload(long copyPass, long srcHandle, long srcOffset, int dstGlId, long dstOffset, long size) {
        if (copyPass == 0 || srcHandle == 0 || size <= 0 || size > Integer.MAX_VALUE) return;

        final Slot slot = ensureSlot(dstGlId, dstOffset, (int) size);
        if (slot == null) return;

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
        dropSync(slot);
        slot.sync = fenceTracker.fenceSync();
        slot.validLen = (int) size;
        slot.valid = true;
    }

    public void invalidateAll(int glId) {
        if (glId == 0) return;
        final Long2ObjectOpenHashMap<Slot> slots = byBuffer.get(glId);
        if (slots == null) return;
        for (Slot slot : slots.values()) {
            slot.valid = false;
            dropSync(slot);
        }
    }

    public boolean serve(int glId, long offset, ByteBuffer out, int len) {
        final Long2ObjectOpenHashMap<Slot> slots = byBuffer.get(glId);
        final Slot slot = slots != null ? slots.get(offset) : null;
        if (slot == null || !slot.valid || slot.transferBuffer == 0 || slot.validLen < len) return false;

        if (slot.sync != 0) {
            final int wait = fenceTracker.clientWaitSync(slot.sync, GL32.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
            if (wait != GL32.GL_ALREADY_SIGNALED && wait != GL32.GL_CONDITION_SATISFIED) return false;
            dropSync(slot);
        }

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
        return byBuffer.containsKey(glId);
    }

    public void release(int glId) {
        final Long2ObjectOpenHashMap<Slot> slots = byBuffer.remove(glId);
        if (slots == null) return;
        for (Slot slot : slots.values()) releaseSlot(slot);
    }

    public void dispose() {
        for (Long2ObjectOpenHashMap<Slot> slots : byBuffer.values()) {
            for (Slot slot : slots.values()) releaseSlot(slot);
        }
        byBuffer.clear();
    }

    private Slot ensureSlot(int glId, long offset, int size) {
        Long2ObjectOpenHashMap<Slot> slots = byBuffer.get(glId);
        Slot slot = slots != null ? slots.get(offset) : null;
        if (slot != null && slot.capacity >= size) return slot;

        if (slot == null) {
            if (slots == null) {
                slots = new Long2ObjectOpenHashMap<>();
                byBuffer.put(glId, slots);
            }
            slot = new Slot();
            slots.put(offset, slot);
        } else {
            releaseSlot(slot);
        }

        slot.transferBuffer = resourceManager.createTransferBuffer(size, SDL_GPU_TRANSFERBUFFERUSAGE_DOWNLOAD);
        if (slot.transferBuffer == 0) {
            slots.remove(offset);
            if (slots.isEmpty()) byBuffer.remove(glId);
            return null;
        }
        slot.capacity = size;
        slot.validLen = 0;
        slot.valid = false;
        return slot;
    }

    private void dropSync(Slot slot) {
        if (slot.sync == 0) return;
        fenceTracker.deleteSync(slot.sync);
        slot.sync = 0;
    }

    private void releaseSlot(Slot slot) {
        dropSync(slot);
        if (slot.transferBuffer != 0 && device.getDevice() != 0) {
            resourceManager.releaseTransferBuffer(slot.transferBuffer);
        }
        slot.transferBuffer = 0;
        slot.capacity = 0;
        slot.validLen = 0;
    }
}
