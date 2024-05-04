package com.gtnewhorizons.angelica.transform.compat;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.ClassConstantPoolParser;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtraUtilsTransformer implements IClassTransformer {

    private static final String RenderBlockColor = "com.rwtema.extrautils.block.render.RenderBlockColor";
    private static final String RenderBlockConnectedTextures = "com.rwtema.extrautils.block.render.RenderBlockConnectedTextures";
    private static final String RenderBlockConnectedTexturesEthereal = "com.rwtema.extrautils.block.render.RenderBlockConnectedTexturesEthereal";
    private static final String RenderBlockFullBright = "com.rwtema.extrautils.block.render.RenderBlockFullBright";
    private static final String RenderBlockSpike = "com.rwtema.extrautils.block.render.RenderBlockSpike";

    private static final List<String> transformedClasses = Arrays.asList(RenderBlockColor, RenderBlockConnectedTextures, RenderBlockConnectedTexturesEthereal, RenderBlockFullBright, RenderBlockSpike);

    private static final Map<String, Boolean> perThreadMap = new HashMap<>();

    static {
        perThreadMap.put(RenderBlockColor, false);
        perThreadMap.put(RenderBlockConnectedTextures, true);
        perThreadMap.put(RenderBlockConnectedTexturesEthereal, true);
        perThreadMap.put(RenderBlockFullBright, false);
        perThreadMap.put(RenderBlockSpike, false);
    }

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!transformedClasses.contains(transformedName)) {
            return basicClass;
        }

        AngelicaTweaker.LOGGER.info("Adding ThreadSafeISBRH for Extra Utilities to: {}", transformedName);

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        addThreadSafeISBRHAnnotation(transformedName, cn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    public void addThreadSafeISBRHAnnotation(String transformedName, ClassNode cn) {

        AnnotationNode isbrhAnnotation = new AnnotationNode("Lcom/gtnewhorizons/angelica/api/ThreadSafeISBRH;");

        Boolean perThread = perThreadMap.get(transformedName);
        if (perThread == null) {
            perThread = false;
            AngelicaTweaker.LOGGER.info("Tried to add ThreadSafeISBRH annotation to {} which is missing from perThreadMap. Using false for perThread.", transformedName);
        }
        isbrhAnnotation.values = Arrays.asList("perThread", perThread);

        if (cn.visibleAnnotations == null) {
            cn.visibleAnnotations = new ArrayList<>();
        }
        cn.visibleAnnotations.add(isbrhAnnotation);
    }
}
