package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import com.gtnewhorizons.angelica.loading.shared.transformers.SodiumBlockTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class SodiumBlockTransformer implements IClassTransformer {

    private final SodiumBlockTransform inner;
    private final String[] exclusions;

    public SodiumBlockTransformer() {
        this.inner = new SodiumBlockTransform(AngelicaTweaker.isObfEnv());
        this.exclusions = inner.getTransformerExclusions();
    }

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link AngelicaRedirector}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
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
            AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return basicClass;
    }

}
