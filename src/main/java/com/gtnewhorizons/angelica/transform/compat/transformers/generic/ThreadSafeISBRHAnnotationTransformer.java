package com.gtnewhorizons.angelica.transform.compat.transformers.generic;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.compat.CompatRegistry;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class ThreadSafeISBRHAnnotationTransformer implements IClassTransformer {

    private static final Map<String, Boolean> patchClasses = CompatRegistry.INSTANCE.getThreadSafeISBRHAnnotations();

    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!patchClasses.containsKey(transformedName)) {
            return basicClass;
        }

        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        if (cn.visibleAnnotations == null) {
            cn.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode isbrhAnnotation = new AnnotationNode("Lcom/gtnewhorizons/angelica/api/ThreadSafeISBRH;");
        isbrhAnnotation.values = Arrays.asList("perThread", patchClasses.getOrDefault(transformedName, true));
        cn.visibleAnnotations.add(isbrhAnnotation);

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.LOGGER.info("[Angelica Compat]Applied ThreadSafeISBRH Annotation to {}", transformedName);
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
