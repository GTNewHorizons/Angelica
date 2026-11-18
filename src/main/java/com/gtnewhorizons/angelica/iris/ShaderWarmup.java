package com.gtnewhorizons.angelica.iris;

import com.gtnewhorizons.angelica.utils.AngelicaJar;
import net.coderbot.iris.Iris;
import net.coderbot.iris.parsing.IrisFunctions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prepares for the first shader pack load on a background thread while the game sits at the main menu.
 */
public final class ShaderWarmup {
    private static final Logger LOGGER = LogManager.getLogger("Angelica/ShaderWarmup");
    private static final String[] PACKAGES = { "net/coderbot/iris/", "kroppeb/stareval/" };
    private static final String SKIPPED_PACKAGE = "net/coderbot/iris/compat/dh/";

    private static boolean started;

    private ShaderWarmup() {}

    /** Main thread only */
    public static void start() {
        if (started) return;
        started = true;
        final Runnable readSelectedPack = Iris.prepareSelectedShaderPack();
        final Thread thread = new Thread(() -> {
            preloadClasses();
            if (readSelectedPack != null) readSelectedPack.run();
        }, "Angelica-Shader-Warmup");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    private static void preloadClasses() {
        final List<String> names;
        try {
            names = listClasses();
        } catch (Exception e) {
            LOGGER.debug("Could not list shader classes to preload", e);
            return;
        }
        final ClassLoader loader = Iris.class.getClassLoader();
        for (String name : names) {
            try {
                Class.forName(name, false, loader);
            } catch (Throwable ignored) {
                // A class that cannot load here fails the same way when the pipeline reaches it
            }
        }
        try {
            Class.forName(IrisFunctions.class.getName(), true, loader);
        } catch (Throwable ignored) {
        }
    }

    private static List<String> listClasses() throws Exception {
        final Path source = AngelicaJar.location();
        final List<String> names = new ArrayList<>();
        if (source == null) return names;
        if (Files.isDirectory(source)) {
            try (Stream<Path> files = Files.walk(source)) {
                files.forEach(file -> addIfShaderClass(names, source.relativize(file).toString().replace('\\', '/')));
            }
        } else {
            try (ZipFile jar = new ZipFile(source.toFile())) {
                final Enumeration<? extends ZipEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    addIfShaderClass(names, entries.nextElement().getName());
                }
            }
        }
        Collections.sort(names);
        return names;
    }

    private static void addIfShaderClass(List<String> names, String entry) {
        if (!entry.endsWith(".class") || entry.startsWith(SKIPPED_PACKAGE)) return;
        for (String packagePath : PACKAGES) {
            if (entry.startsWith(packagePath)) {
                names.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.'));
                return;
            }
        }
    }
}
