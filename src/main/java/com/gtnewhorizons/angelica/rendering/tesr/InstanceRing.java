package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.streaming.OrphanStreamingBuffer;
import com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer;

import java.nio.ByteBuffer;

public final class InstanceRing {

    private PersistentStreamingBuffer persistentRing;
    private OrphanStreamingBuffer orphanRing;
    private boolean initialized;
    private boolean usedThisFrame;
    private boolean lastUploadKept;
    private int bufferId;

    public long upload(ByteBuffer data, int stride) {
        if (!initialized) {
            initialized = true;
            persistentRing = PersistentStreamingBuffer.createOrNull(PersistentStreamingBuffer.DEFAULT_CAPACITY);
        }
        usedThisFrame = true;
        if (persistentRing != null) {
            final int index = persistentRing.upload(data, stride);
            if (index >= 0) {
                bufferId = persistentRing.getBufferId();
                lastUploadKept = true;
                return (long) index * stride;
            }
        }
        if (orphanRing == null) orphanRing = new OrphanStreamingBuffer();
        orphanRing.upload(data);
        bufferId = orphanRing.getBufferId();
        lastUploadKept = false;
        return 0;
    }

    public int keptUploadsEpoch() {
        return lastUploadKept && persistentRing != null ? persistentRing.getWraps() : -1;
    }

    public int bufferId() {
        return bufferId;
    }

    public void postDraw() {
        if (usedThisFrame && persistentRing != null) {
            persistentRing.postDraw();
        }
        usedThisFrame = false;
    }

    public void delete() {
        if (persistentRing != null) {
            persistentRing.destroy();
            persistentRing = null;
        }
        if (orphanRing != null) {
            orphanRing.destroy();
            orphanRing = null;
        }
        initialized = false;
        usedThisFrame = false;
        lastUploadKept = false;
        bufferId = 0;
    }
}
