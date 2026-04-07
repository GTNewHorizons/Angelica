package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.glsm.loading.EcosystemNarrowRules;
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
 *
 * @see EcosystemNarrowRules#EARLY_REDIRECTOR_TARGETS
 */
public class EarlyRedirectorTransformer implements IClassTransformer {

    private final GLSMRedirector core = new GLSMRedirector();
    private final String[] exclusions;

    public EarlyRedirectorTransformer() {
        final List<String> excl = new ArrayList<>(Arrays.asList(core.getCoreExclusions()));
        excl.add("com.gtnewhorizons.angelica.lwjgl3.");
        excl.add("com.gtnewhorizons.angelica.transform");
        exclusions = excl.toArray(new String[0]);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        boolean targeted = false;
        for (String pkg : EcosystemNarrowRules.EARLY_REDIRECTOR_TARGETS) {
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
