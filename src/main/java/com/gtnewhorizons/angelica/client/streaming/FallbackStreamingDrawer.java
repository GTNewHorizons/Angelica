package com.gtnewhorizons.angelica.client.streaming;

public final class FallbackStreamingDrawer extends StreamingDrawer {

    FallbackStreamingDrawer(VAOConsumer vaoConsumer) {
        super(vaoConsumer);
    }

    @Override
    public long writeSection(int needed) {
        return 0;
    }

    @Override
    public void drawElementsInstanced(int mode, int indices_count, int type, long indices_buffer_offset) {

    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
