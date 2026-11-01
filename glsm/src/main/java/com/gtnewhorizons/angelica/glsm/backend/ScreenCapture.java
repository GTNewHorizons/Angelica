package com.gtnewhorizons.angelica.glsm.backend;

import com.gtnewhorizons.angelica.glsm.GLSMBuffers;
import org.lwjgl.opengl.GL32;

/** Async downscaled RGBA8 copy of a framebuffer's color. */
public abstract class ScreenCapture {

    public static final int RING = RenderBackend.MAX_FRAMES_IN_FLIGHT + 2;

    private final int width;
    private final int height;
    private final long[] tags = new long[RING];
    private final long[] syncs = new long[RING];
    private int head;
    private int tail;
    private long mapped;
    private boolean deleted;

    protected ScreenCapture(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("capture size " + width + "x" + height);
        this.width = width;
        this.height = height;
    }

    public final int width() { return width; }
    public final int height() { return height; }

    public final boolean idle() { return head == tail; }

    public final boolean begin(int srcFramebuffer, int srcWidth, int srcHeight, long tag) {
        if (deleted || head - tail == RING || srcWidth <= 0 || srcHeight <= 0) return false;
        final int slot = head % RING;
        if (!start(slot, srcFramebuffer, srcWidth, srcHeight)) return false;
        final long sync = GLSMBuffers.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        if (sync == 0L) return false;
        syncs[slot] = sync;
        tags[slot] = tag;
        head++;
        return true;
    }

    public final boolean poll() {
        if (mapped != 0L) throw new IllegalStateException("poll() before release() of the previous capture");
        while (tail != head) {
            final int slot = tail % RING;
            final int status = GLSMBuffers.glClientWaitSync(syncs[slot], 0, 0L);
            if (status == GL32.GL_TIMEOUT_EXPIRED) return false;
            dropSync(slot);
            if (status != GL32.GL_WAIT_FAILED) {
                mapped = map(slot);
                if (mapped != 0L) return true;
            }
            tail++;
        }
        return false;
    }

    public final long address() {
        if (mapped == 0L) throw new IllegalStateException("no polled capture");
        return mapped;
    }

    public final long tag() {
        if (mapped == 0L) throw new IllegalStateException("no polled capture");
        return tags[tail % RING];
    }

    public final void release() {
        if (mapped == 0L) throw new IllegalStateException("no polled capture");
        mapped = 0L;
        unmap(tail % RING);
        tail++;
    }

    public final void delete() {
        if (deleted) return;
        if (mapped != 0L) release();
        deleted = true;
        for (int slot = 0; slot < RING; slot++) {
            dropSync(slot);
            deleteSlot(slot);
        }
        head = tail = 0;
    }

    private void dropSync(int slot) {
        if (syncs[slot] == 0L) return;
        GLSMBuffers.glDeleteSync(syncs[slot]);
        syncs[slot] = 0L;
    }

    /** Returns false when nothing was recorded; the slot then stays free. */
    protected abstract boolean start(int slot, int srcFramebuffer, int srcWidth, int srcHeight);

    protected abstract long map(int slot);

    protected abstract void unmap(int slot);

    /** Called for every slot, including ones never started. */
    protected abstract void deleteSlot(int slot);
}
