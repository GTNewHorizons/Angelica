package com.gtnewhorizons.angelica.client.streaming;

public abstract class StreamingDrawer {

    public StreamingDrawer create() {
        return new PersistentStreamingDrawer(); //TODO check caps
    }

    public abstract long getWritePointer();

    public abstract void ensureCapacity(int needed);
}
