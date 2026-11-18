package com.gtnewhorizons.angelica.sdlgpu.device;

import com.gtnewhorizons.angelica.glsm.profiling.DebugCounters;
import org.lwjgl.system.MemoryStack;

import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.sdl.SDLGPU.SDL_QueryGPUFence;
import static org.lwjgl.sdl.SDLGPU.SDL_WaitForGPUFences;
import static org.lwjgl.sdl.SDLVersion.SDL_VERSIONNUM;

// SDL < 3.4.14 METAL_WaitForFences busy-spins on the completion flag; poll + park there instead.
public final class FenceWait {
    public static final long FOREVER = Long.MAX_VALUE;

    static final long SPIN_NS = 20_000L;
    static final long PARK_MAX_NS = 50_000L;

    interface Ops {
        boolean query(long dev, long fence);
        void waitNative(long dev, long fence);
        long nanoTime();
        void park(long nanos);
    }

    private static final class NativeOps implements Ops {
        private final boolean inverted;

        NativeOps(boolean inverted) { this.inverted = inverted; }

        @Override public boolean query(long dev, long fence) { return SDL_QueryGPUFence(dev, fence) != inverted; }

        @Override public void waitNative(long dev, long fence) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                SDL_WaitForGPUFences(dev, true, stack.pointers(fence));
            }
        }

        @Override public long nanoTime() { return System.nanoTime(); }
        @Override public void park(long nanos) { LockSupport.parkNanos(nanos); }
    }

    private static final Ops NATIVE = new NativeOps(false);
    private static final Ops NATIVE_INVERTED = new NativeOps(true);

    private FenceWait() {}

    private static Ops ops(Device device) {
        return device.isFenceQueryInverted() ? NATIVE_INVERTED : NATIVE;
    }

    public static boolean isSignaled(Device device, long fence) {
        return ops(device).query(device.getDevice(), fence);
    }

    public static boolean await(Device device, long fence, long timeoutNs) {
        final long start = System.nanoTime();
        final boolean signaled = await(device.getDevice(), fence, timeoutNs, device.isFencePollEnabled(), ops(device));
        DebugCounters.FENCE_WAIT_NANOS.add(System.nanoTime() - start);
        return signaled;
    }

    static boolean pollEnabled(String driverName, int sdlVersion) {
        return Device.isMetal(driverName) && sdlVersion < SDL_VERSIONNUM(3, 4, 14);
    }

    // timeoutNs < 0 is GL_TIMEOUT_IGNORED read as signed; treated as forever
    static boolean await(long dev, long fence, long timeoutNs, boolean pollForever, Ops ops) {
        if (ops.query(dev, fence)) return true;
        if (timeoutNs == 0) return false;
        final boolean forever = timeoutNs < 0 || timeoutNs == FOREVER;
        if (forever && !pollForever) {
            ops.waitNative(dev, fence);
            return true;
        }
        boolean interrupted = false;
        try {
            final long start = ops.nanoTime();
            while (true) {
                final long elapsed = ops.nanoTime() - start;
                if (ops.query(dev, fence)) return true;
                if (!forever && elapsed >= timeoutNs) return false;
                if (elapsed < SPIN_NS) {
                    Thread.onSpinWait();
                    continue;
                }
                if (Thread.interrupted()) interrupted = true;
                ops.park(forever ? PARK_MAX_NS : Math.min(PARK_MAX_NS, timeoutNs - elapsed));
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
