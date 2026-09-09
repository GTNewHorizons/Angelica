package com.gtnewhorizons.angelica.debug.flyby;

import com.gtnewhorizons.angelica.config.SystemProperties;
import com.gtnewhorizons.angelica.glsm.profiling.Tracy;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Runs tracy-capture and makes .tracy files.
 */
final class TracyCaptureProcess {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final String TOOL_NAME = "tracy-capture";
    private static final long SAVE_TIMEOUT_MS = 180_000L;
    private static final int PID_POLLS = 40;
    private static final long PID_POLL_MS = 50L;

    private Boolean configured;
    private File tool;
    private File traceDir;
    private File pidFile;
    private String stamp;
    private Process process;
    private File traceFile;

    boolean isConfigured() {
        if (this.configured == null) this.configured = resolve();
        return this.configured;
    }

    private boolean resolve() {
        final String toolsDir = SystemProperties.FLYBY_TRACY_TOOLS_DIR;
        if (toolsDir.isEmpty()) return false;

        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            LOGGER.error("angelica.flyby.tracyToolsDir is set, but the Windows side isn't set up yet. Might do later.");
            return false;
        }
        if (!Tracy.ENABLED) {
            LOGGER.error("angelica.flyby.tracyToolsDir is set but the Tracy backend is off. Add -Dangelica.tracy=true.");
            return false;
        }

        final File candidate = new File(toolsDir, TOOL_NAME);
        if (!candidate.canExecute()) {
            LOGGER.error("No executable {} in '{}'.", TOOL_NAME, toolsDir);
            return false;
        }

        final File dir = resolveTraceDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            LOGGER.error("Could not create the trace folder '{}', per-run capture is off", dir);
            return false;
        }

        this.tool = candidate;
        this.traceDir = dir;
        this.stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        LOGGER.info("Capturing every run with {} into {}", candidate, dir);
        return true;
    }

    private static File resolveTraceDir() {
        final File configured = new File(SystemProperties.FLYBY_TRACE_DIR);
        return configured.isAbsolute() ? configured : new File(Minecraft.getMinecraft().mcDataDir, SystemProperties.FLYBY_TRACE_DIR);
    }

    void start(String route, int run, int runs) {
        if (this.process != null || !isConfigured()) return;

        this.traceFile = nextTraceFile(route, run, runs);
        this.pidFile = new File(this.traceDir, this.traceFile.getName() + ".pid");

        try {
            final ProcessBuilder builder = new ProcessBuilder("sh", "-c",
                "echo $$ > \"$0\"; exec \"$1\" -a 127.0.0.1 -o \"$2\" -f -m \"$3\"",
                this.pidFile.getAbsolutePath(), this.tool.getAbsolutePath(), this.traceFile.getAbsolutePath(),
                String.valueOf(SystemProperties.FLYBY_TRACY_MEMORY_PERCENT));
            builder.redirectErrorStream(true);
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(this.traceDir, TOOL_NAME + ".log")));
            this.process = builder.start();
            LOGGER.info("Capturing run {}/{} to {}", run, runs, this.traceFile.getName());
        } catch (IOException e) {
            LOGGER.error("Could not launch " + TOOL_NAME, e);
            this.configured = Boolean.FALSE;
            this.process = null;
        }
    }

    void stop() {
        final Process running = this.process;
        if (running == null) return;
        this.process = null;

        final String pid = readPid();
        if (pid == null) {
            LOGGER.error("{} never wrote its pid, so {} cannot be saved", TOOL_NAME, this.traceFile);
            running.destroy();
            return;
        }

        try {
            new ProcessBuilder("kill", "-INT", pid).start().waitFor();
            if (!running.waitFor(SAVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOGGER.error("{} did not finish saving {} within {}s", TOOL_NAME, this.traceFile, SAVE_TIMEOUT_MS / 1000L);
                running.destroyForcibly();
                return;
            }
        } catch (IOException e) {
            LOGGER.error("Could not stop " + TOOL_NAME, e);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } finally {
            if (!this.pidFile.delete()) this.pidFile.deleteOnExit();
        }

        final long bytes = this.traceFile.length();
        if (bytes == 0L) {
            LOGGER.error("{} exited but {} is empty, check {}", TOOL_NAME, this.traceFile, new File(this.traceDir, TOOL_NAME + ".log"));
        } else {
            LOGGER.info("Saved {} ({} MB)", this.traceFile.getName(), bytes >> 20);
        }
    }

    private String readPid() {
        for (int poll = 0; poll < PID_POLLS; poll++) {
            if (this.pidFile.isFile()) {
                try {
                    final String pid = Files.readString(this.pidFile.toPath()).trim();
                    if (!pid.isEmpty()) return pid;
                } catch (IOException ignored) {
                }
            }
            try {
                Thread.sleep(PID_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private File nextTraceFile(String route, int run, int runs) {
        final String base = route + "-" + this.stamp + "-run" + run + "of" + runs;
        File file = new File(this.traceDir, base + ".tracy");
        for (int copy = 2; file.exists(); copy++) {
            file = new File(this.traceDir, base + "-" + copy + ".tracy");
        }
        return file;
    }
}
