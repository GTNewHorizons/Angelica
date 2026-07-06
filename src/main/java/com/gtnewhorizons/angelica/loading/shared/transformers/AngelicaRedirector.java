package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mod-level wrapper around {@link GLSMRedirector}. Adds mod-specific transformer exclusions and delegates all redirect logic to GLSM.
 * <p>
 * THIS CLASS MIGHT BE LOADED ON A DIFFERENT CLASS LOADER,
 * IT SHOULD NOT CALL ANY CODE FROM THE MAIN MOD
 */
public final class AngelicaRedirector {

    private final GLSMRedirector core = new GLSMRedirector();
    private final String[] allExclusions;

    public AngelicaRedirector() {
        final List<String> exclusions = new ArrayList<>(Arrays.asList(core.getCoreExclusions()));
        exclusions.add("com.gtnewhorizons.angelica.lwjgl3.");
        exclusions.add("com.gtnewhorizons.angelica.transform");
        allExclusions = exclusions.toArray(new String[0]);
    }

    public String[] getTransformerExclusions() {
        return allExclusions;
    }

    public boolean shouldTransform(byte[] basicClass) {
        return core.shouldTransform(basicClass);
    }

    /** @return Was the class changed? */
    public boolean transformClassNode(String transformedName, ClassNode cn) {
        return core.transformClassNode(transformedName, cn);
    }
}
