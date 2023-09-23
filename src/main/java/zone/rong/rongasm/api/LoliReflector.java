package zone.rong.rongasm.api;

import com.google.common.base.Preconditions;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.Type;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Helper class for Reflection nonsense.
 */
public class LoliReflector {

    public static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle classLoader$DefineClass = resolveMethod(ClassLoader.class, "defineClass", String.class, byte[].class, int.class, int.class);
    private static final CaptureSet<String> transformerExclusions;

    static {
        CaptureSet<String> captureSet = new CaptureSet<>();
        try {
            captureSet = new CaptureSet<>(((Set<String>) resolveFieldGetter(LaunchClassLoader.class, "transformerExceptions").invokeExact(Launch.classLoader)));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        transformerExclusions = captureSet;
        try {
            resolveFieldSetter(LaunchClassLoader.class, "transformerExceptions").invoke(Launch.classLoader, transformerExclusions);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static Class defineMixinClass(String className, byte[] classBytes) {
        try {
            // defineClass(Launch.classLoader, className, classBytes);
            Map<String, byte[]> resourceCache = (Map<String, byte[]>) resolveFieldGetter(LaunchClassLoader.class, "resourceCache").invoke(Launch.classLoader);
            if (resourceCache instanceof ResourceCache) {
                ((ResourceCache) resourceCache).add(className, classBytes);
            } else {
                resourceCache.put(className, classBytes);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    public static <CL extends ClassLoader> Class defineClass(CL classLoader, Class clazz) {
        String name = Type.getInternalName(clazz);
        InputStream byteStream = clazz.getResourceAsStream('/' + name + ".class");
        try {
            byte[] classBytes = new byte[byteStream.available()];
            final int bytesRead = byteStream.read(classBytes);
            Preconditions.checkState(bytesRead == classBytes.length);
            return (Class) classLoader$DefineClass.invokeExact(classLoader, name.replace('/', '.'), classBytes, 0, classBytes.length);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return clazz;
    }

    public static <CL extends ClassLoader> Class defineClass(CL classLoader, String name, byte[] classBytes) {
        try {
            return (Class) classLoader$DefineClass.invokeExact(classLoader, name, classBytes, 0, classBytes.length);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        return null;
    }

    public static MethodHandle resolveCtor(Class<?> clazz, Class<?>... args) {
        try {
            Constructor<?> ctor = clazz.getDeclaredConstructor(args);
            ctor.setAccessible(true);
            return LOOKUP.unreflectConstructor(ctor);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> Constructor<T> getCtor(Class<T> clazz, Class<?>... args) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor(args);
            ctor.setAccessible(true);
            return ctor;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveMethod(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, args);
            method.setAccessible(true);
            return LOOKUP.unreflect(method);
        } catch (IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... args) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, args);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static MethodHandle resolveMethod(Class<?> clazz, String methodName, String obfMethodName, Class<?>... args) {
        try {
            return LOOKUP.unreflect(ReflectionHelper.findMethod((Class)clazz, null, new String[] { methodName, obfMethodName }, args));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Method getMethod(Class<?> clazz, String methodName, String obfMethodName, Class<?>... args) {
        return ReflectionHelper.findMethod((Class)clazz, null, new String[] { methodName, obfMethodName }, args);
    }

    public static MethodHandle resolveFieldGetter(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            /*
            if (LoliLoadingPlugin.isVMOpenJ9) {
                fixOpenJ9PrivateStaticFinalRestraint(field);
            }
            */
            return LOOKUP.unreflectGetter(field);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldSetter(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            /*
            if (LoliLoadingPlugin.isVMOpenJ9) {
                fixOpenJ9PrivateStaticFinalRestraint(field);
            }
            */
            return LOOKUP.unreflectSetter(field);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldGetter(Class<?> clazz, String fieldName, String obfFieldName) {
        try {
            return LOOKUP.unreflectGetter(ReflectionHelper.findField(clazz, fieldName, obfFieldName));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static MethodHandle resolveFieldSetter(Class<?> clazz, String fieldName, String obfFieldName) {
        try {
            return LOOKUP.unreflectSetter(ReflectionHelper.findField(clazz, fieldName, obfFieldName));
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            /*
            if (LoliLoadingPlugin.isVMOpenJ9) {
                fixOpenJ9PrivateStaticFinalRestraint(field);
            }
            */
            return field;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName, String obfFieldName) {
        return ReflectionHelper.findField(clazz, fieldName, obfFieldName);
    }

    public static boolean doesClassExist(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) { }
        return false;
    }

    public static boolean doesTweakExist(String tweakName) {
        return ((List<String>) Launch.blackboard.get("TweakClasses")).contains(tweakName);
    }

    public static Optional<Class<?>> getClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException ignored) { }
        return Optional.empty();
    }

    public static void removeTransformerExclusion(String transformerExclusion) {
        if (!transformerExclusions.remove(transformerExclusion)) {
            transformerExclusions.addCapture(transformerExclusion);
        }
    }

    public static void addTransformerExclusion(String transformerExclusion) {
        transformerExclusions.put(transformerExclusion);
    }

    private static void fixOpenJ9PrivateStaticFinalRestraint(Field field) throws Throwable {
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        LOOKUP.unreflectSetter(modifiers).invokeExact(field, field.getModifiers() & ~Modifier.FINAL);
    }

}
