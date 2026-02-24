package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.CeleritasBlockTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class CeleritasBlockTransformer implements IClassTransformer {

    private final CeleritasBlockTransform inner;
    private final String[] exclusions;

    public CeleritasBlockTransformer() {
        this.inner = new CeleritasBlockTransform(AngelicaTweaker.isObfEnv());
        this.exclusions = inner.getTransformerExclusions();
        this.inner.setCeleritasSetting();
    }

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link CeleritasBlockTransform}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        for (String exclusion : exclusions) {
            if (transformedName.startsWith(exclusion)) return basicClass;
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
