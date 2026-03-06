package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * A scoped redirector that only transforms classes from known-misbehaving (core)mod packages.
 * <p>
 * Some mods prematurely load classes that call GL functions during coremod discovery/injectData.
 * Those classes get missed by the late-registered {@link AngelicaRedirectorTransformer}, so their
 * GL calls permanently bypass GLSM.
 * <p>
 * This transformer is registered in the {@code AngelicaTweaker} constructor (before any
 * coremod's {@code injectData}) and is removed by {@code AngelicaLateTweaker} once the
 * full redirector is in its proper post-mixin position.
 * <p>
 * The mixin service will add this to its delegation list, but it no-ops on game classes
 * since it only targets specific mod packages.
 */
public class EarlyRedirectorTransformer implements IClassTransformer {

    private static final String[] TARGET_PACKAGES = {
        "cn.tesseract.mycelium.",
    };

    private final AngelicaRedirector inner = new AngelicaRedirector();
    private final String[] exclusions = inner.getTransformerExclusions();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        boolean targeted = false;
        for (String pkg : TARGET_PACKAGES) {
            if (transformedName.startsWith(pkg)) {
                targeted = true;
                break;
            }
        }
        if (!targeted) return basicClass;

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
            return cw.toByteArray();
        }
        return basicClass;
    }
}
