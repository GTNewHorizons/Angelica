package com.gtnewhorizons.angelica.loading.shared;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;

import java.util.Map;

public class ClassHierarchyHelper {

    /**
     * @param cache     cache classnames -> is subclass
     * @param className the name of the class you want to inspect
     * @param target    the super class that you are looking for
     * @return Returns true if the target class is present in the class hierarchy of a class
     */
    public static boolean isSubclassOf(Map<String, Boolean> cache, String className, String target) {
        if (className.equals(target)) return true;
        Boolean cacheValue = cache.get(className);
        if (cacheValue != null) return cacheValue;
        try {
            final byte[] classBytes = Launch.classLoader.getClassBytes(className);
            final ClassReader classReader = new ClassReader(classBytes);
            final String superName = classReader.getSuperName();
            final boolean isSubclass = superName != null
                                       && !superName.equals("java/lang/Object")
                                       && isSubclassOf(cache, superName, target);
            cache.put(className, isSubclass);
            return isSubclass;
        } catch (Exception ignored) {
            cache.put(className, Boolean.FALSE);
            return false;
        }
    }
}
