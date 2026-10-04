package com.gtnewhorizons.angelica.glsm.backend;

import com.gtnewhorizons.angelica.config.SystemProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public final class MoltenVK {

    public enum Source {
        ENV,
        JAR,
        SYSTEM,
        NONE
    }

    public static final String ENV_VULKAN_LIBRARY = "SDL_VULKAN_LIBRARY";
    public static final String JAR_NAME = "angelica-moltenvk";

    private static final Logger LOGGER = LogManager.getLogger("Angelica/MoltenVK");
    private static final String LIB_NAME = "libMoltenVK.dylib";
    private static final String[] SYSTEM_PATHS = { "/opt/homebrew/lib/" + LIB_NAME, "/usr/local/lib/" + LIB_NAME };
    private static final String ARCH = archName(System.getProperty("os.arch", ""));

    private static Source source;
    private static String path;
    private static boolean located;

    private MoltenVK() {}

    public static synchronized Source source() {
        if (source == null) {
            final boolean macos = System.getProperty("os.name", "").toLowerCase().contains("mac");
            source = detect(macos, System.getenv(ENV_VULKAN_LIBRARY), MoltenVK.class.getResource(bundledResource(ARCH)) != null, SYSTEM_PATHS);
        }
        return source;
    }

    public static synchronized String locate() {
        if (!located) {
            path = resolve(source(), System.getenv(ENV_VULKAN_LIBRARY), bundledResource(ARCH), new File(SystemProperties.MOLTENVK_DIR), ARCH, SYSTEM_PATHS);
            located = true;
        }
        return path;
    }

    static String archName(String osArch) {
        final String arch = osArch.toLowerCase();
        return arch.contains("aarch64") || arch.contains("arm64") ? "arm64" : "x64";
    }

    static String bundledResource(String arch) {
        return "/macos/" + arch + "/org/lwjgl/vulkan/" + LIB_NAME;
    }

    static Source detect(boolean macos, String env, boolean bundled, String[] systemPaths) {
        if (!macos) return Source.NONE;
        if (env != null && !env.isEmpty()) return Source.ENV;
        if (bundled) return Source.JAR;
        return systemPath(systemPaths) != null ? Source.SYSTEM : Source.NONE;
    }

    static String resolve(Source source, String env, String resource, File dir, String arch, String[] systemPaths) {
        return switch (source) {
            case ENV -> env;
            case JAR -> {
                final String extracted = extract(resource, dir, arch);
                yield extracted != null ? extracted : systemPath(systemPaths);
            }
            case SYSTEM -> systemPath(systemPaths);
            case NONE -> null;
        };
    }

    private static String systemPath(String[] systemPaths) {
        for (final String candidate : systemPaths) {
            if (new File(candidate).isFile()) return candidate;
        }
        return null;
    }

    private static String extract(String resource, File dir, String arch) {
        final byte[] lib;
        try (InputStream in = MoltenVK.class.getResourceAsStream(resource)) {
            if (in == null) return null;
            lib = readAll(in);
        } catch (IOException e) {
            LOGGER.warn("MoltenVK: failed reading {}: {}", resource, e.toString());
            return null;
        }

        final File target = new File(dir, "libMoltenVK-" + arch + ".dylib");
        File tmp = null;
        try {
            if (!target.isFile() || target.length() != lib.length || !Arrays.equals(Files.readAllBytes(target.toPath()), lib)) {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
                tmp = File.createTempFile("libMoltenVK", ".dylib", dir);
                Files.write(tmp.toPath(), lib);
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("MoltenVK: failed extracting to {}: {}", target, e.toString());
            if (tmp != null) tmp.delete();
            return null;
        }
        return target.getAbsolutePath();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        final byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
