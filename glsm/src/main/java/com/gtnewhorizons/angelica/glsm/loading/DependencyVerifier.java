package com.gtnewhorizons.angelica.glsm.loading;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.util.List;

/**
 * Verifies required classes/resources are present on the classpath at load time.
 * Fails fast with a descriptive error message if a dependency is missing.
 */
public final class DependencyVerifier {

    /**
     * A single dependency check: a classpath resource that must exist.
     * @param resourcePath  Resource path to check (e.g. "/com/example/SomeClass.class")
     * @param errorMessage  Human-readable error message if missing (include download URL)
     */
    public record Check(String resourcePath, String errorMessage) {}

    public interface FatalDialog {
        void show(String title, String message);
    }

    /**
     * Verify all checks pass. Throws RuntimeException on first failure.
     * @param anchor  Class whose classloader to use for resource lookups
     * @param checks  Dependency checks to run
     */
    public static void verify(Class<?> anchor, List<Check> checks) {
        for (Check check : checks) {
            if (anchor.getResource(check.resourcePath()) == null) {
                throw new RuntimeException(check.errorMessage());
            }
        }
    }

    public static List<Check> gtnhLibChecks(String productName) {
        return List.of(
            new Check("/it/unimi/dsi/fastutil/ints/Int2ObjectMap.class", "Missing dependency: " + productName + " requires GTNHLib! Download: https://modrinth.com/mod/gtnhlib"),
            new Check("/com/gtnewhorizon/gtnhlib/client/renderer/VertexCallbackManager.class", "GTNHLib is outdated: " + productName + " requires GTNHLib 0.10.0 or newer! Download: https://modrinth.com/mod/gtnhlib"),
            new Check("/it/unimi/dsi/fastutil/objects/ObjectBooleanBiConsumer.class", "GTNHLib is outdated: " + productName + " requires GTNHLib 0.11.19 or newer (fastutil 8.5.18+)! Download: https://modrinth.com/mod/gtnhlib"));
    }

    public static void verifyOrHalt(Class<?> anchor, String productName, List<Check> checks, Logger logger, FatalDialog sdlDialog) {
        final String message;
        try {
            verify(anchor, checks);
            return;
        } catch (RuntimeException ex) {
            message = ex.getMessage();
        }

        final String title = productName + " - Missing Dependency";
        System.err.println("FATAL: " + message);
        try {
            logger.fatal(message);
        } catch (Throwable ignored) {}

        final int lwjgl3ifyMajor = ((Integer) Launch.blackboard.getOrDefault("lwjgl3ify:major-version", Integer.MIN_VALUE));
        try {
            if (lwjgl3ifyMajor >= 3) {
                sdlDialog.show(title, message);
            } else if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
            }
        } catch (Throwable ignored) {}

        Runtime.getRuntime().halt(1);
    }

    private DependencyVerifier() {}
}
