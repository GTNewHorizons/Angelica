package ca.fxco.memoryleakfix;

import org.embeddedt.archaicfix.ArchaicLogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

public class MemoryLeakFix {
    public static void forceLoadAllMixinsAndClearSpongePoweredCache() {
        ArchaicLogger.LOGGER.info("Forceloading mixins and clearing mixin cache");
        try {
            Field noGroupField;
            try {
                noGroupField = InjectorGroupInfo.Map.class.getDeclaredField("NO_GROUP");
            } catch(NoSuchFieldException e) {
                noGroupField = null;
            }
            if(noGroupField == null || !Modifier.isStatic(noGroupField.getModifiers())) {
                ArchaicLogger.LOGGER.info("No need to forceload, this mixin version doesn't have the memory leak");
                return;
            }
            MixinEnvironment.getCurrentEnvironment().audit();
            noGroupField.setAccessible(true);
            Object noGroup = noGroupField.get(null);
            Field membersField = noGroup.getClass().getDeclaredField("members");
            membersField.setAccessible(true);
            ((List<?>) membersField.get(noGroup)).clear(); // Clear spongePoweredCache
            emptyClassInfo();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        ArchaicLogger.LOGGER.info("Finished clearing mixin cache");
    }

    private static final String OBJECT = "java/lang/Object";

    private static void emptyClassInfo() throws NoSuchFieldException, IllegalAccessException {
        Field cacheField = ClassInfo.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ClassInfo> cache = ((Map<String, ClassInfo>)cacheField.get(null));
        ClassInfo jlo = cache.get(OBJECT);
        cache.clear();
        cache.put(OBJECT, jlo);
    }
}
