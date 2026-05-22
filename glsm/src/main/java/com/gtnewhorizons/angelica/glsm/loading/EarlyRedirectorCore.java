package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class EarlyRedirectorCore {

    private final GLSMRedirector core;
    private final String[] targets;
    private final String[] exclusions;

    public EarlyRedirectorCore(String... extraExclusions) {
        this.core = new GLSMRedirector();
        this.targets = EcosystemNarrowRules.EARLY_REDIRECTOR_TARGETS.clone();

        final List<String> excl = new ArrayList<>(Arrays.asList(core.getCoreExclusions()));
        if (extraExclusions != null && extraExclusions.length > 0) {
            Collections.addAll(excl, extraExclusions);
        }
        this.exclusions = excl.toArray(new String[0]);
    }

    public byte[] transform(String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!startsWithAny(transformedName, targets)) return basicClass;
        if (startsWithAny(transformedName, exclusions)) return basicClass;
        if (!core.shouldTransform(basicClass)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        if (core.transformClassNode(transformedName, cn)) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String p : prefixes) {
            if (name.startsWith(p)) return true;
        }
        return false;
    }
}
