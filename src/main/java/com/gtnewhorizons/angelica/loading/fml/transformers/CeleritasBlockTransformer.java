package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.shared.transformers.CeleritasBlockTransform;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** IClassTransformer wrapper for {@link CeleritasBlockTransform} */
public class CeleritasBlockTransformer implements IClassTransformer {

    private final CeleritasBlockTransform inner;
    private final String[] exclusions;

    public CeleritasBlockTransformer() {
        this.inner = new CeleritasBlockTransform(AngelicaClientTweaker.isObfEnv());
        this.exclusions = inner.getTransformerExclusions();
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
        inner.trackBlockSubclasses(cr.getClassName(), cr.getSuperName());

        if (!inner.shouldTransform(basicClass)) {
            return basicClass;
        }

        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        final boolean changed = inner.transformClassNode(transformedName, cn);
        if (changed) {
            final SafeClassWriter cw = new SafeClassWriter(SafeClassWriter.COMPUTE_FRAMES | SafeClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            final byte[] bytes = cw.toByteArray();
            AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
            return bytes;
        }
        return basicClass;
    }

    private static final class SafeClassWriter extends ClassWriter {
        private static final String OBJECT = "java/lang/Object";
        private static final Map<String, String> superNameCache = new ConcurrentHashMap<>();

        SafeClassWriter(int flags) {
            super(flags);
        }

        private static String getSuperName(String type) {
            if (OBJECT.equals(type)) return null;
            return superNameCache.computeIfAbsent(type, t -> {
                try {
                    return new ClassReader(t).getSuperName();
                } catch (IOException e) {
                    return OBJECT;
                }
            });
        }

        @Override
        protected String getCommonSuperClass(final String type1, final String type2) {
            final Set<String> hierarchy1 = new HashSet<>();
            hierarchy1.add(type1);
            String s = getSuperName(type1);
            while (s != null && !s.equals(OBJECT)) {
                hierarchy1.add(s);
                s = getSuperName(s);
            }
            hierarchy1.add(OBJECT);
            if (hierarchy1.contains(type2)) return type2;
            String s2 = getSuperName(type2);
            while (s2 != null && !s2.equals(OBJECT)) {
                if (hierarchy1.contains(s2)) return s2;
                s2 = getSuperName(s2);
            }
            return OBJECT;
        }
    }

}
