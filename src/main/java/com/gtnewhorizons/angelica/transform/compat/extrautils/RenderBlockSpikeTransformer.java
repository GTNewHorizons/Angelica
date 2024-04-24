package com.gtnewhorizons.angelica.transform.compat.extrautils;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;

public class RenderBlockSpikeTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (transformedName.equals("com.rwtema.extrautils.block.render.RenderBlockSpike")) {
            final ClassReader cr = new ClassReader(basicClass);
            final ClassNode cn = new ClassNode();
            cr.accept(cn, 0);

            AnnotationNode isbrhAnnotation = new AnnotationNode("Lcom/gtnewhorizons/angelica/api/ThreadSafeISBRH;");
            isbrhAnnotation.values = Arrays.asList("perThread", false);

            if (cn.visibleAnnotations == null) {
                cn.visibleAnnotations = new ArrayList<>();
            }
            cn.visibleAnnotations.add(isbrhAnnotation);

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            basicClass = cw.toByteArray();
        }

        return basicClass;
    }
}
