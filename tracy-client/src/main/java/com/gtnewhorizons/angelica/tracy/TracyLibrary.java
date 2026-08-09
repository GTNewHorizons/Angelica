package com.gtnewhorizons.angelica.tracy;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.SharedLibrary;

import static org.lwjgl.system.MemoryUtil.NULL;

@Lwjgl3Aware
final class TracyLibrary {
    private static final Logger LOGGER = LogManager.getLogger("Tracy");

    final long startupProfiler;
    final long shutdownProfiler;
    final long profilerStarted;
    final long connected;
    final long zoneBegin;
    final long zoneEnd;
    final long zoneText;
    final long zoneValue;
    final long frameMark;
    final long plot;
    final long plotInt;
    final long plotConfig;
    final long message;
    final long messageAppinfo;
    final long setThreadName;
    final long gpuZoneBegin;
    final long gpuZoneEnd;
    final long gpuTime;
    final long gpuTimeSync;
    final long gpuNewContext;
    final long gpuContextName;

    static TracyLibrary resolve(SharedLibrary lib) {
        try {
            return new TracyLibrary(lib);
        } catch (IllegalStateException e) {
            LOGGER.warn("Tracy: {}", e.getMessage());
            return null;
        }
    }

    private TracyLibrary(SharedLibrary lib) {
        startupProfiler = req(lib, "___tracy_startup_profiler");
        shutdownProfiler = req(lib, "___tracy_shutdown_profiler");
        profilerStarted = req(lib, "___tracy_profiler_started");
        connected = req(lib, "___tracy_connected");
        zoneBegin = req(lib, "___tracy_emit_zone_begin");
        zoneEnd = req(lib, "___tracy_emit_zone_end");
        zoneText = req(lib, "___tracy_emit_zone_text");
        zoneValue = req(lib, "___tracy_emit_zone_value");
        frameMark = req(lib, "___tracy_emit_frame_mark");
        plot = req(lib, "___tracy_emit_plot");
        plotInt = req(lib, "___tracy_emit_plot_int");
        plotConfig = req(lib, "___tracy_emit_plot_config");
        message = req(lib, "___tracy_emit_message");
        messageAppinfo = req(lib, "___tracy_emit_message_appinfo");
        setThreadName = req(lib, "___tracy_set_thread_name");
        gpuZoneBegin = req(lib, "___tracy_emit_gpu_zone_begin");
        gpuZoneEnd = req(lib, "___tracy_emit_gpu_zone_end");
        gpuTime = req(lib, "___tracy_emit_gpu_time");
        gpuTimeSync = req(lib, "___tracy_emit_gpu_time_sync");
        gpuNewContext = req(lib, "___tracy_emit_gpu_new_context");
        gpuContextName = req(lib, "___tracy_emit_gpu_context_name");
    }

    private static long req(SharedLibrary lib, String name) {
        final long address = lib.getFunctionAddress(name);
        if (address == NULL) throw new IllegalStateException("missing symbol " + name + " in " + lib.getPath());
        return address;
    }
}
