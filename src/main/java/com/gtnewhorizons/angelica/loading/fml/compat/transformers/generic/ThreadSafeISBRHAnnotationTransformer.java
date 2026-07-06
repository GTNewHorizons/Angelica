package com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic;

import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;

public class ThreadSafeISBRHAnnotationTransformer {

    public static void transform(ClassNode cn, boolean perThread) {
        if (cn.visibleAnnotations == null) {
            cn.visibleAnnotations = new ArrayList<>();
        }

        AnnotationNode isbrhAnnotation = new AnnotationNode("Lcom/gtnewhorizons/angelica/api/ThreadSafeISBRH;");
        isbrhAnnotation.values = Arrays.asList("perThread", perThread);
        cn.visibleAnnotations.add(isbrhAnnotation);
    }
}
