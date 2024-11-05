package com.gtnewhorizons.angelica.transform.compat;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.compat.handlers.CompatHandler;
import com.gtnewhorizons.angelica.transform.compat.transformers.generic.FieldLevelTessellatorTransformer;
import com.gtnewhorizons.angelica.transform.compat.transformers.generic.HUDCachingEarlyReturnTransformer;
import com.gtnewhorizons.angelica.transform.compat.transformers.generic.ThreadSafeISBRHAnnotationTransformer;
import com.gtnewhorizons.angelica.transform.compat.transformers.generic.TileEntityNullGuardTransformer;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenericCompatTransformer implements IClassTransformer {

    private static final Map<String, List<String>> fieldLevelTessellator = new Object2ObjectOpenHashMap<>();
    private static final Map<String, List<String>> tileEntityNullGuard = new Object2ObjectOpenHashMap<>();
    private static final Map<String, Boolean> threadSafeIBSRH = new Object2BooleanOpenHashMap<>();
    private static final Map<String, List<String>> hudCachingEarlyReturn = new Object2ObjectOpenHashMap<>();

    private static final Set<String> transformedClasses = new ObjectOpenHashSet<>();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!transformedClasses.contains(transformedName)) return basicClass;

        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        if (fieldLevelTessellator.containsKey(transformedName)) {
            FieldLevelTessellatorTransformer.transform(cn, fieldLevelTessellator.get(transformedName));
        }

        if (tileEntityNullGuard.containsKey(transformedName)) {
            TileEntityNullGuardTransformer.transform(cn, tileEntityNullGuard.get(transformedName));
        }

        if (threadSafeIBSRH.containsKey(transformedName)) {
            ThreadSafeISBRHAnnotationTransformer.transform(cn, threadSafeIBSRH.get(transformedName));
        }

        if (hudCachingEarlyReturn.containsKey(transformedName)) {
            HUDCachingEarlyReturnTransformer.transform(cn, hudCachingEarlyReturn.get(transformedName));
        }

        MixinClassWriter cw = new MixinClassWriter(MixinClassWriter.COMPUTE_MAXS | MixinClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }

    public static void register(CompatHandler handler) {
        if (handler.getFieldLevelTessellator() != null)
            fieldLevelTessellator.putAll(handler.getFieldLevelTessellator());
        if (handler.getTileEntityNullGuard() != null)
            tileEntityNullGuard.putAll(handler.getTileEntityNullGuard());
        if (handler.getThreadSafeISBRHAnnotations() != null)
            threadSafeIBSRH.putAll(handler.getThreadSafeISBRHAnnotations());
        if (handler.getHUDCachingEarlyReturn() != null)
            hudCachingEarlyReturn.putAll(handler.getHUDCachingEarlyReturn());
    }

    public static void build() {
        transformedClasses.addAll(fieldLevelTessellator.keySet());
        transformedClasses.addAll(tileEntityNullGuard.keySet());
        transformedClasses.addAll(threadSafeIBSRH.keySet());
        transformedClasses.addAll(hudCachingEarlyReturn.keySet());
    }
}
