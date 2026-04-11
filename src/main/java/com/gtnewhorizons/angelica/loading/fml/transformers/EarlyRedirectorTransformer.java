package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
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
 *
 * @see EcosystemNarrowRules#EARLY_REDIRECTOR_TARGETS
 */
public class EarlyRedirectorTransformer implements IClassTransformer {

    private static final String[] DEFAULT_EARLY_REDIRECTOR_TARGETS = {
        "cn.tesseract.mycelium."
    };
    private static final String TARGETS_BLACKBOARD_KEY = "angelica.earlyRedirectorTargets";
    private static volatile String[] earlyRedirectorTargets;

    private final GLSMRedirector core = new GLSMRedirector();
    private final String[] exclusions;

    public EarlyRedirectorTransformer() {
        final List<String> excl = new ArrayList<>(Arrays.asList(core.getCoreExclusions()));
        excl.add("com.gtnewhorizons.angelica.lwjgl3.");
        excl.add("com.gtnewhorizons.angelica.transform");
        exclusions = excl.toArray(new String[0]);
    }

    private static String[] getEarlyRedirectorTargets() {
        if (earlyRedirectorTargets == null) {
            synchronized (EarlyRedirectorTransformer.class) {
                if (earlyRedirectorTargets == null) {
                    earlyRedirectorTargets = loadTargetsFromRuntimeConfig();
                }
            }
        }
        return earlyRedirectorTargets;
    }

    private static String[] loadTargetsFromRuntimeConfig() {
        final Object blackboardValue = Launch.blackboard.get(TARGETS_BLACKBOARD_KEY);
        final String[] parsedFromBlackboard = parseTargetValue(blackboardValue);
        if (parsedFromBlackboard.length > 0) return parsedFromBlackboard;
        return DEFAULT_EARLY_REDIRECTOR_TARGETS;
    }

    private static String[] parseTargetValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        }
        if (value instanceof String stringValue) {
            return parseTargetValue(stringValue);
        }
        return new String[0];
    }

    private static String[] parseTargetValue(String text) {
        if (text == null || text.isBlank()) return new String[0];
        return Arrays.stream(text.split("[,;]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        boolean targeted = false;
        for (String pkg : getEarlyRedirectorTargets()) {
            if (transformedName.startsWith(pkg)) {
                targeted = true;
                break;
            }
        }
        if (!targeted) return basicClass;

        for (String exclusion : exclusions) {
            if (transformedName.startsWith(exclusion)) return basicClass;
        }

        if (!core.shouldTransform(basicClass)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        final boolean changed = core.transformClassNode(transformedName, cn);
        if (changed) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }
}
