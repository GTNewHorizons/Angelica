package com.gtnewhorizons.umbra.loading.fml.transformers;

import com.gtnewhorizons.umbra.loading.shared.UmbraClassDump;
import com.gtnewhorizons.umbra.loading.shared.transformers.UmbraRedirector;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** IClassTransformer wrapper for {@link UmbraRedirector} */
public class UmbraRedirectorTransformer implements IClassTransformer {

    private final UmbraRedirector inner;
    private final String[] exclusions;

    public UmbraRedirectorTransformer() {
        inner = new UmbraRedirector();
        exclusions = inner.getTransformerExclusions();
    }

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        for (String exclusion : exclusions) {
            if (transformedName.startsWith(exclusion)) return basicClass;
        }

        if (!inner.shouldTransform(basicClass)) {
            return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        final boolean changed = inner.transformClassNode(transformedName, cn);
        if (changed) {
            final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            UmbraClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return basicClass;
    }
}
