package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;

import java.util.function.Consumer;

public final class GLSMInitConfig {
    private final boolean lwjglDebug;
    private final StreamingUploader.UploadStrategy streamingUploadStrategy;
    private final boolean framebufferSupported;
    private final boolean fboEnabled;
    private final Consumer<DirectTessellator> directDrawer;
    private final Runnable streamingDrawerDestroy;
    private final int displayWidth;
    private final int displayHeight;
    private final Runnable postInitCallback;

    private GLSMInitConfig(Builder builder) {
        this.lwjglDebug = builder.lwjglDebug;
        this.streamingUploadStrategy = builder.streamingUploadStrategy;
        this.framebufferSupported = builder.framebufferSupported;
        this.fboEnabled = builder.fboEnabled;
        this.directDrawer = builder.directDrawer;
        this.streamingDrawerDestroy = builder.streamingDrawerDestroy;
        this.displayWidth = builder.displayWidth;
        this.displayHeight = builder.displayHeight;
        this.postInitCallback = builder.postInitCallback;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isLwjglDebug() { return lwjglDebug; }
    public StreamingUploader.UploadStrategy getStreamingUploadStrategy() { return streamingUploadStrategy; }
    public boolean isFramebufferSupported() { return framebufferSupported; }
    public boolean isFboEnabled() { return fboEnabled; }
    public Consumer<DirectTessellator> getDirectDrawer() { return directDrawer; }
    public Runnable getStreamingDrawerDestroy() { return streamingDrawerDestroy; }
    public int getDisplayWidth() { return displayWidth; }
    public int getDisplayHeight() { return displayHeight; }
    public Runnable getPostInitCallback() { return postInitCallback; }

    public static final class Builder {
        private boolean lwjglDebug = false;
        private StreamingUploader.UploadStrategy streamingUploadStrategy = StreamingUploader.UploadStrategy.BUFFER_DATA;
        private boolean framebufferSupported = false;
        private boolean fboEnabled = false;
        private Consumer<DirectTessellator> directDrawer = null;
        private Runnable streamingDrawerDestroy = null;
        private int displayWidth = 0;
        private int displayHeight = 0;
        private Runnable postInitCallback = null;

        private Builder() {}

        public Builder lwjglDebug(boolean lwjglDebug) {
            this.lwjglDebug = lwjglDebug;
            return this;
        }

        public Builder streamingUploadStrategy(StreamingUploader.UploadStrategy strategy) {
            this.streamingUploadStrategy = strategy;
            return this;
        }

        public Builder framebufferSupported(boolean framebufferSupported) {
            this.framebufferSupported = framebufferSupported;
            return this;
        }

        public Builder fboEnabled(boolean fboEnabled) {
            this.fboEnabled = fboEnabled;
            return this;
        }

        public Builder directDrawer(Consumer<DirectTessellator> directDrawer) {
            this.directDrawer = directDrawer;
            return this;
        }

        public Builder streamingDrawerDestroy(Runnable streamingDrawerDestroy) {
            this.streamingDrawerDestroy = streamingDrawerDestroy;
            return this;
        }

        public Builder displaySize(int width, int height) {
            this.displayWidth = width;
            this.displayHeight = height;
            return this;
        }

        public Builder postInitCallback(Runnable callback) {
            this.postInitCallback = callback;
            return this;
        }

        public GLSMInitConfig build() {
            return new GLSMInitConfig(this);
        }
    }
}
