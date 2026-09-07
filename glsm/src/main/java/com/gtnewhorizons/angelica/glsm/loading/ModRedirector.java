package com.gtnewhorizons.angelica.glsm.loading;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModRedirector {

    private final GLSMRedirector core = new GLSMRedirector();
    private final ClassDump dump;
    private final String[] targets;
    private final String[] allExclusions;

    public ModRedirector(ClassDump dump, String... extraExclusions) {
        this(dump, (String[]) null, extraExclusions);
    }

    public ModRedirector(ClassDump dump, String[] targets, String[] extraExclusions) {
        this.dump = dump;
        this.targets = targets;
        final List<String> exclusions = new ArrayList<>(Arrays.asList(core.getCoreExclusions()));
        exclusions.add("com.gtnewhorizons.angelica.lwjgl3.");
        Collections.addAll(exclusions, extraExclusions);
        this.allExclusions = exclusions.toArray(new String[0]);
    }

    public ClassDump getDump() {
        return dump;
    }

    public String[] getTransformerExclusions() {
        return allExclusions;
    }

    public boolean shouldTransform(byte[] basicClass) {
        return core.shouldTransform(basicClass);
    }

    public boolean transformClassNode(String transformedName, ClassNode cn, boolean lwjgl3Aware) {
        return core.transformClassNode(transformedName, cn, lwjgl3Aware);
    }

    public byte[] transform(String transformedName, byte[] basicClass, Object transformer) {
        if (basicClass == null) return null;

        if (targets != null && !startsWithAny(transformedName, targets)) return basicClass;
        if (startsWithAny(transformedName, allExclusions)) return basicClass;

        if (!core.shouldTransform(basicClass)) {
            return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        if (!core.transformClassNode(transformedName, cn)) {
            return basicClass;
        }
        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        dump.dumpClass(transformedName, basicClass, bytes, transformer);
        return bytes;
    }

    private static boolean startsWithAny(String name, String[] prefixes) {
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) return true;
        }
        return false;
    }
}
