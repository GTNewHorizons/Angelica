package com.gtnewhorizons.angelica.glsm.testutil;

import com.gtnewhorizon.gtnhlib.reflect.Fields;
import com.gtnewhorizon.gtnhlib.reflect.Fields.LookupType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Reflect {

    private Reflect() {}

    public static Field field(Class<?> owner, String name) {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new AssertionError("no field " + name + " on " + owner.getName() + " or its supertypes");
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object target, String name) {
        try {
            return (T) field(target.getClass(), name).get(target);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStatic(Class<?> owner, String name) {
        try {
            return (T) field(owner, name).get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static void set(Object target, String name, Object value) {
        try {
            field(target.getClass(), name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static void setStatic(Class<?> owner, String name, Object value) {
        try {
            field(owner, name).set(null, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    public static <F> void setStaticFinal(Class<?> owner, String name, Class<F> type, F value) {
        Fields.ofClass(owner).getField(LookupType.DECLARED_IN_HIERARCHY, name, type).setValue(null, value);
    }

    public static void setDeclared(Class<?> owner, Object target, String name, Object value) {
        try {
            field(owner, name).set(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invoke(Object target, String name, Class<?>[] signature, Object... args) {
        try {
            final Method m = resolve(target.getClass(), name, signature);
            return (T) m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeStatic(Class<?> owner, String name, Class<?>[] signature, Object... args) {
        try {
            return (T) resolve(owner, name, signature).invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T construct(Class<?> owner) {
        try {
            final Constructor<?> ctor = owner.getDeclaredConstructor();
            ctor.setAccessible(true);
            return (T) ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Method resolve(Class<?> owner, String name, Class<?>[] signature) throws NoSuchMethodException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                final Method m = c.getDeclaredMethod(name, signature);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(name + " on " + owner.getName() + " or its supertypes");
    }
}
