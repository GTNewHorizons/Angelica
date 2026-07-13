package com.gtnewhorizons.angelica.tracy;

import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@Lwjgl3Aware
final class TracyNativeLoader {
    private static final Logger LOGGER = LogManager.getLogger("Tracy");
    static final String TRACY_VERSION = TracyTags.TRACY_VERSION;

    private enum OS {
        WINDOWS, MACOS, LINUX;

        static OS detect(String osName) {
            if (osName.contains("windows")) return WINDOWS;
            if (osName.contains("mac")) return MACOS;
            if (osName.contains("linux")) return LINUX;
            return null;
        }
    }

    private TracyNativeLoader() {}

    static SharedLibrary load() {
        final OS os = OS.detect(System.getProperty("os.name", "").toLowerCase());
        final String arch = System.getProperty("os.arch", "").toLowerCase();
        final boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        if (os == null || (arm64 && os != OS.MACOS)) {
            LOGGER.warn("Tracy: unsupported platform {}/{}", System.getProperty("os.name"), System.getProperty("os.arch"));
            return null;
        }
        final String platform = os.name().toLowerCase() + (arm64 ? "-arm64" : "-x64");
        final String libName = switch (os) {
            case WINDOWS -> "TracyClient.dll";
            case MACOS -> "libTracyClient.dylib";
            case LINUX -> "libTracyClient.so";
        };
        final String resource = "/natives/tracy/" + platform + "/" + libName;

        final byte[] lib;
        try (InputStream in = TracyNativeLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOGGER.warn("Tracy: native resource {} not present in jar", resource);
                return null;
            }
            lib = readAll(in);
        } catch (IOException e) {
            LOGGER.warn("Tracy: failed reading native resource {}: {}", resource, e.toString());
            return null;
        }

        final String ext = libName.substring(libName.lastIndexOf('.') + 1);
        final File dir = new File(System.getProperty("angelica.tracy.dir", "angelica" + File.separator + "natives" + File.separator + "tracy"));
        final File target = new File(dir, "libTracyClient-" + TRACY_VERSION + "-" + platform + "." + ext);
        try {
            if (!target.isFile() || target.length() != lib.length) {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
                final File tmp = File.createTempFile("libTracyClient", "." + ext, dir);
                Files.write(tmp.toPath(), lib);
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Tracy: failed extracting native to {}: {}", target, e.toString());
            return null;
        }

        try {
            return APIUtil.apiCreateLibrary(target.getAbsolutePath());
        } catch (Throwable t) {
            LOGGER.warn("Tracy: failed loading {}: {}", target, t.toString());
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1 << 20);
        final byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
