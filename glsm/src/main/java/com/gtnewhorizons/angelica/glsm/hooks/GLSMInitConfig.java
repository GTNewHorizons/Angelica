package com.gtnewhorizons.angelica.glsm.hooks;

import com.gtnewhorizon.gtnhlib.client.renderer.DirectTessellator;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.streaming.StreamingUploader;
import com.gtnewhorizons.angelica.glsm.streaming.TessellatorStreamingDrawer;
import lombok.Getter;

import java.util.function.Consumer;

public final class GLSMInitConfig {
    @Getter private final boolean lwjglDebug;
    @Getter private final StreamingUploader.UploadStrategy streamingUploadStrategy;
    @Getter private final Consumer<DirectTessellator> directDrawer;
    @Getter private final Runnable streamingDrawerDestroy;
    @Getter private final int displayWidth;
    @Getter private final int displayHeight;
    @Getter private final Runnable postInitCallback;
    private final boolean dsaEnabled;
    @Getter private final boolean noErrorChecks;

    private GLSMInitConfig(Builder builder) {
        this.lwjglDebug = builder.lwjglDebug;
        this.streamingUploadStrategy = builder.streamingUploadStrategy;
        this.noErrorChecks = builder.noErrorChecks;
        this.directDrawer = builder.directDrawer;
        this.streamingDrawerDestroy = builder.streamingDrawerDestroy;
        this.displayWidth = builder.displayWidth;
        this.displayHeight = builder.displayHeight;
        this.postInitCallback = builder.postInitCallback;
        this.dsaEnabled = builder.dsaEnabled;
    }

    public boolean isDSAEnabled() {
        return dsaEnabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean lwjglDebug = SystemProperties.LWJGL_DEBUG;
        private StreamingUploader.UploadStrategy streamingUploadStrategy = StreamingUploader.UploadStrategy.BUFFER_DATA;
        private Consumer<DirectTessellator> directDrawer = TessellatorStreamingDrawer::drawDirect;
        private Runnable streamingDrawerDestroy = TessellatorStreamingDrawer::destroy;
        private int displayWidth = 0;
        private int displayHeight = 0;
        private Runnable postInitCallback = null;
        private boolean dsaEnabled = true;
        private boolean noErrorChecks;

        private Builder() {}

        public Builder lwjglDebug(boolean lwjglDebug) {
            this.lwjglDebug = lwjglDebug;
            return this;
        }

        public Builder streamingUploadStrategy(StreamingUploader.UploadStrategy strategy) {
            this.streamingUploadStrategy = strategy;
            return this;
        }

        public Builder noErrorChecks(boolean noErrorChecks) {
            this.noErrorChecks = noErrorChecks;
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

        public Builder enableDSA(boolean enableDSA) {
            this.dsaEnabled = enableDSA;
            return this;
        }

        public GLSMInitConfig build() {
            return new GLSMInitConfig(this);
        }
    }
}
