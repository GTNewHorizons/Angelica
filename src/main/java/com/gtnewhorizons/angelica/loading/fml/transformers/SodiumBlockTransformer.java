package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.AngelicaRedirector;
import com.gtnewhorizons.angelica.loading.shared.transformers.SodiumBlockTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class SodiumBlockTransformer implements IClassTransformer {

    private final SodiumBlockTransform inner;
    private final String[] exclusions;

    public SodiumBlockTransformer() {
        this.inner = new SodiumBlockTransform(AngelicaTweaker.isObfEnv());
        this.exclusions = inner.getTransformerExclusions();
    }

    private static final List<Pair<String, String>> BlockBoundsFields = ImmutableList.of(
        Pair.of("minX", "field_149759_B"),
        Pair.of("minY", "field_149760_C"),
        Pair.of("minZ", "field_149754_D"),
        Pair.of("maxX", "field_149755_E"),
        Pair.of("maxY", "field_149756_F"),
        Pair.of("maxZ", "field_149757_G")
    );

    /**
     * Delete the global vanilla bounding box fields off the Block object. {@link AngelicaRedirector}
     * replaces these with a thread-safe alternative.
     */
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if ("net.minecraft.block.Block".equals(transformedName)) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            cn.fields.removeIf(field -> BlockBoundsFields.stream().anyMatch(pair -> field.name.equals(pair.getLeft()) || field.name.equals(pair.getRight())));
            inner.transformClassNode(transformedName, cn);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }

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
