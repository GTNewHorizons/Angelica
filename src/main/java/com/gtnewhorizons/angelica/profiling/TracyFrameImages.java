package com.gtnewhorizons.angelica.profiling;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.CaptureGate;
import com.gtnewhorizons.angelica.glsm.GLSMFramebuffer;
import com.gtnewhorizons.angelica.glsm.backend.ScreenCapture;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;

public final class TracyFrameImages {
    private static final int MAX_WIDTH = 320;
    private static final int MAX_HEIGHT = 180;
    private static final Tracy.ZoneId Z_CAPTURE = Tracy.zoneId("frameImageCapture", Tracy.COLOR_SWAP);
    private static final Tracy.ZoneId Z_EMIT = Tracy.zoneId("frameImageEmit", Tracy.COLOR_SWAP);
    private static final boolean HAS_ENABLED_OVERRIDE = SystemProperties.TRACY_FRAME_IMAGES_OVERRIDE != null;
    private static final boolean ENABLED_OVERRIDE = HAS_ENABLED_OVERRIDE && SystemProperties.TRACY_FRAME_IMAGES_OVERRIDE;
    private static final boolean HAS_RATE_OVERRIDE = SystemProperties.TRACY_FRAME_IMAGE_RATE_OVERRIDE != null;
    private static final int RATE_OVERRIDE = HAS_RATE_OVERRIDE ? Math.max(0, SystemProperties.TRACY_FRAME_IMAGE_RATE_OVERRIDE) : 0;

    private static ScreenCapture capture;
    private static long frameIndex;
    private static long lastCaptureNs;
    private static int srcW;
    private static int srcH;

    private TracyFrameImages() {}

    public static void onPostSwap(Minecraft mc) {
        if (!enabled() || !CaptureGate.tracyConnected) {
            drop();
            return;
        }
        emitReady();
        final long now = System.nanoTime();
        final int rate = rate();
        if (rate != 0 && now - lastCaptureNs < 1_000_000_000L / rate) return;
        final Framebuffer source = mc.getFramebuffer();
        final int w = source.framebufferWidth;
        final int h = source.framebufferHeight;
        if (w <= 0 || h <= 0) return;
        if (capture == null || w != srcW || h != srcH) {
            srcW = w;
            srcH = h;
            final long size = fitCaptureSize(w, h);
            final int captureW = (int) (size >>> 32);
            final int captureH = (int) size;
            if (capture == null || capture.width() != captureW || capture.height() != captureH) {
                if (capture != null) capture.delete();
                capture = GLSMFramebuffer.createScreenCapture(captureW, captureH);
            }
            return;
        }
        Tracy.beginZone(Z_CAPTURE);
        if (capture.begin(source.framebufferObject, w, h, frameIndex)) lastCaptureNs = now;
        Tracy.endZone();
    }

    static long fitCaptureSize(int srcWidth, int srcHeight) {
        final float aspect = (float) srcWidth / srcHeight;
        int width = srcWidth;
        int height = srcHeight;
        if (width > MAX_WIDTH) {
            width = MAX_WIDTH;
            height = (int) (MAX_WIDTH / aspect);
        }
        if (height > MAX_HEIGHT) {
            width = (int) (MAX_HEIGHT * aspect);
            height = MAX_HEIGHT;
        }
        return ((long) Math.max(4, width & ~3) << 32) | Math.max(4, height & ~3);
    }

    public static void onFrameMark() {
        frameIndex++;
    }

    private static void emitReady() {
        if (capture == null || capture.idle()) return;
        Tracy.beginZone(Z_EMIT);
        while (capture.poll()) {
            final long lag = frameIndex - capture.tag();
            if (lag <= 255) Tracy.frameImage(capture.address(), capture.width(), capture.height(), (int) lag, true);
            capture.release();
        }
        Tracy.endZone();
    }

    private static void drop() {
        if (capture == null) return;
        capture.delete();
        capture = null;
        srcW = srcH = 0;
    }

    private static boolean enabled() {
        return HAS_ENABLED_OVERRIDE ? ENABLED_OVERRIDE : AngelicaConfig.tracyFrameImages;
    }

    private static int rate() {
        return HAS_RATE_OVERRIDE ? RATE_OVERRIDE : AngelicaConfig.tracyFrameImageRate;
    }
}
