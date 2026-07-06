package com.gtnewhorizons.angelica.glsm.loading;

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

    private DependencyVerifier() {}
}
