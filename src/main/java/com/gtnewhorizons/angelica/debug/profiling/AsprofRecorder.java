package com.gtnewhorizons.angelica.debug.profiling;

import com.gtnewhorizons.angelica.config.SystemProperties;
import one.profiler.AsyncProfiler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AsprofRecorder {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/Asprof");

    private static boolean availabilityChecked;
    private static boolean availabilityValue;

    private static final Session SESSION = new Session(Asprof::execute);
    private static boolean shutdownHookRegistered;

    private AsprofRecorder() {}

    public static synchronized boolean available() {
        if (!availabilityChecked) {
            availabilityChecked = true;
            availabilityValue = computeAvailable();
        }
        return availabilityValue;
    }

    private static boolean computeAvailable() {
        if (!SystemProperties.debugTooling()) return false;
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!(os.contains("mac") || os.contains("linux"))) return false;
        return AsprofRecorder.class.getResource("/one/profiler/AsyncProfiler.class") != null;
    }

    public static synchronized String start(String tag, String opts) {
        if (!available()) return "async-profiler is not available on this platform/build";
        if (SESSION.recordingId() != 0) return "already recording to " + SESSION.outputPath();

        File file = SystemProperties.PROFILE_OUTPUT.isEmpty() ? new File(SystemProperties.PROFILE_DIR, fileName(tag, System.currentTimeMillis())) : new File(SystemProperties.PROFILE_OUTPUT);
        file = file.getAbsoluteFile();
        if (file.exists()) return "output file already exists: " + file.getPath();
        final File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();

        final String command = command(opts, file.getPath());
        LOGGER.info("async-profiler: {}", command);
        try {
            SESSION.start(file.getPath(), command);
        } catch (Throwable t) {
            return handleFailure(t);
        }

        ensureShutdownHook();
        return null;
    }

    public static synchronized String stop() {
        final StopResult result = completeStop(SESSION.stop());
        if (result.status() == StopStatus.NO_MATCH) return "not recording";
        return result.error();
    }

    public static synchronized long recordingId() {
        return SESSION.recordingId();
    }

    public static synchronized StopResult stopIfRecording(long expectedId) {
        return completeStop(SESSION.stopIfRecording(expectedId));
    }

    private static StopResult completeStop(StopResult result) {
        if (result.status() == StopStatus.FAILED) {
            handleFailure(result.failure());
        } else if (result.status() == StopStatus.STOPPED) {
            LOGGER.info("Profile written: {} ({} bytes)", result.path(), new File(result.path()).length());
        }
        return result;
    }

    public static synchronized String status() {
        if (!available()) return "async-profiler is not available on this platform/build";
        try {
            return SESSION.status();
        } catch (Throwable t) {
            return handleFailure(t);
        }
    }

    public static synchronized boolean isRecording() {
        return SESSION.recordingId() != 0;
    }

    public static synchronized String outputPath() {
        return SESSION.outputPath();
    }

    private static void ensureShutdownHook() {
        if (shutdownHookRegistered) return;
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(AsprofRecorder::shutdownStop, "Asprof-Shutdown"));
    }

    private static synchronized void shutdownStop() {
        completeStop(SESSION.stop());
    }

    private static String handleFailure(Throwable t) {
        LOGGER.warn("async-profiler command failed", t);
        if (t instanceof LinkageError || t instanceof UnsupportedOperationException) {
            availabilityChecked = true;
            availabilityValue = false;
        }
        return firstLine(String.valueOf(t.getMessage()));
    }

    static String command(String opts, String file) {
        return opts.isEmpty() ? "start,jfr,file=" + file : "start,jfr," + opts + ",file=" + file;
    }

    static String fileName(String tag, long nowMillis) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);
        return "angelica-" + tag + "-" + format.format(new Date(nowMillis)) + ".jfr";
    }

    static String firstLine(String s) {
        if (s == null || s.isEmpty()) return "";
        final int newline = s.indexOf('\n');
        final String line = newline < 0 ? s : s.substring(0, newline);
        return line.trim();
    }

    public enum StopStatus { NO_MATCH, STOPPED, FAILED }

    public record StopResult(StopStatus status, String path, Throwable failure) {
        private static final StopResult NO_MATCH = new StopResult(StopStatus.NO_MATCH, null, null);

        public String error() {
            return failure == null ? null : AsprofRecorder.firstLine(String.valueOf(failure.getMessage()));
        }
    }

    static final class Session {
        @FunctionalInterface
        interface CommandExecutor {
            String execute(String command) throws Throwable;
        }

        private final CommandExecutor executor;
        private long lastId;
        private long recordingId;
        private String path;

        Session(CommandExecutor executor) {
            this.executor = executor;
        }

        synchronized long start(String path, String command) throws Throwable {
            if (recordingId != 0) throw new IllegalStateException("already recording to " + this.path);
            if (lastId == Long.MAX_VALUE) throw new IllegalStateException("recording identity exhausted");
            executor.execute(command);
            this.path = path;
            recordingId = ++lastId;
            return recordingId;
        }

        synchronized long recordingId() {
            return recordingId;
        }

        synchronized String outputPath() {
            return path;
        }

        synchronized String status() throws Throwable {
            return executor.execute("status");
        }

        synchronized StopResult stop() {
            return stopIfRecording(recordingId);
        }

        synchronized StopResult stopIfRecording(long expectedId) {
            if (expectedId == 0 || recordingId != expectedId) return StopResult.NO_MATCH;
            final String stoppedPath = path;
            Throwable failure = null;
            try {
                executor.execute("stop");
            } catch (Throwable t) {
                failure = t;
            }
            recordingId = 0;
            path = null;
            return new StopResult(failure == null ? StopStatus.STOPPED : StopStatus.FAILED, stoppedPath, failure);
        }
    }

    private static final class Asprof {
        static String execute(String cmd) throws Throwable {
            return AsyncProfiler.getInstance().execute(cmd);
        }
    }
}
