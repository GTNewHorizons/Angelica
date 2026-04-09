package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A scoped redirector that only transforms classes from known-misbehaving (core)mod packages.
 * <p>
 * Some mods prematurely load classes that call GL functions during coremod discovery/injectData.
 * Those classes get missed by the late-registered {@link AngelicaRedirectorTransformer}, so their
 * GL calls permanently bypass GLSM.
 * <p>
 * Registered in {@code AngelicaClientTweaker} constructor. Removed by {@code AngelicaLateTweaker}
 * once the full redirector is in its proper post-mixin position.
 */
public class EarlyRedirectorTransformer implements IClassTransformer {

    private static final String[] EARLY_REDIRECTOR_TARGETS = {
        "cn.tesseract.mycelium.",
    };

    private GLSMRedirector core;
    private final String[] exclusions;

    public EarlyRedirectorTransformer() {
        final List<String> excl = new ArrayList<>(Arrays.asList(getCoreExclusions()));
        excl.add("com.gtnewhorizons.angelica.lwjgl3.");
        excl.add("com.gtnewhorizons.angelica.transform");
        exclusions = excl.toArray(new String[0]);
    }

    private GLSMRedirector getCore() {
        if (core == null) {
            core = new GLSMRedirector();
        }
        return core;
    }

    private String[] getCoreExclusions() {
        // Return hardcoded exclusions instead of calling GLSMRedirector.getCoreExclusions()
        return new String[]{
            "org.lwjgl",
            "com.gtnewhorizon.gtnhlib.asm",
            "com.gtnewhorizons.angelica.glsm.",
            "me.eigenraven.lwjgl3ify"
        };
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        boolean targeted = false;
        for (String pkg : EARLY_REDIRECTOR_TARGETS) {
            if (transformedName.startsWith(pkg)) {
                targeted = true;
                break;
            }
        }
        if (!targeted) return basicClass;

        for (String exclusion : exclusions) {
            if (transformedName.startsWith(exclusion)) return basicClass;
        }

        if (!getCore().shouldTransform(basicClass)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        final boolean changed = getCore().transformClassNode(transformedName, cn);
        if (changed) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }
}
