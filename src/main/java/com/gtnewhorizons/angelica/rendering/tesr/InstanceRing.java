package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.streaming.OrphanStreamingBuffer;
import com.gtnewhorizons.angelica.glsm.streaming.PersistentStreamingBuffer;

import java.nio.ByteBuffer;

public final class InstanceRing {

    private PersistentStreamingBuffer persistentRing;
    private OrphanStreamingBuffer orphanRing;
    private boolean initialized;
    private boolean lastUploadKept;
    private int bufferId;

    public long upload(ByteBuffer data, int stride) {
        if (!initialized) {
            initialized = true;
            persistentRing = PersistentStreamingBuffer.createOrNull(PersistentStreamingBuffer.DEFAULT_CAPACITY);
        }
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
        if (!lastUploadKept || persistentRing == null) return -1;
        // Once a fence covers an upload, the ring may reuse that range while a later redraw still reads it.
        return persistentRing.getFencesIssued() & Integer.MAX_VALUE;
    }

    public int bufferId() {
        return bufferId;
    }

    public void endFrame() {
        if (persistentRing != null) {
            persistentRing.endFrame();
        }
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
        lastUploadKept = false;
        bufferId = 0;
    }
}
