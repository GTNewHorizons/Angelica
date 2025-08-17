package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import com.gtnewhorizons.angelica.loading.fml.compat.ICompatHandler;
import com.gtnewhorizons.angelica.loading.fml.compat.CompatHandlers;
import com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic.FieldLevelTessellatorTransformer;
import com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic.HUDCachingEarlyReturnTransformer;
import com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic.ThreadSafeISBRHAnnotationTransformer;
import com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic.TileEntityNullGuardTransformer;
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

    private final Map<String, List<String>> fieldLevelTessellator = new Object2ObjectOpenHashMap<>();
    private final Map<String, List<String>> tileEntityNullGuard = new Object2ObjectOpenHashMap<>();
    private final Map<String, Boolean> threadSafeIBSRH = new Object2BooleanOpenHashMap<>();
    private final Map<String, List<String>> hudCachingEarlyReturn = new Object2ObjectOpenHashMap<>();
    private final Set<String> targetedClasses = new ObjectOpenHashSet<>();

    public GenericCompatTransformer() {
        for (ICompatHandler handler : CompatHandlers.getHandlers()) {
            registerHandler(handler);
        }
        buildTargetClassSet();
    }

    private void registerHandler(ICompatHandler handler) {
        if (handler.getFieldLevelTessellator() != null) {
            fieldLevelTessellator.putAll(handler.getFieldLevelTessellator());
        }
        if (handler.getTileEntityNullGuard() != null) {
            tileEntityNullGuard.putAll(handler.getTileEntityNullGuard());
        }
        if (handler.getThreadSafeISBRHAnnotations() != null) {
            threadSafeIBSRH.putAll(handler.getThreadSafeISBRHAnnotations());
        }
        if (handler.getHUDCachingEarlyReturn() != null) {
            hudCachingEarlyReturn.putAll(handler.getHUDCachingEarlyReturn());
        }
    }

    private void buildTargetClassSet() {
        targetedClasses.addAll(fieldLevelTessellator.keySet());
        targetedClasses.addAll(tileEntityNullGuard.keySet());
        targetedClasses.addAll(threadSafeIBSRH.keySet());
        targetedClasses.addAll(hudCachingEarlyReturn.keySet());
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!targetedClasses.contains(transformedName)) return basicClass;

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
        AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
