package com.gtnewhorizons.angelica.glsm.testutil;

/** Child-first for the defined name, so a same-named class on the test classpath cannot shadow it. */
public final class DefiningClassLoader extends ClassLoader {

    private final String dottedName;
    private final byte[] bytes;

    private DefiningClassLoader(String dottedName, byte[] bytes) {
        super(DefiningClassLoader.class.getClassLoader());
        this.dottedName = dottedName;
        this.bytes = bytes;
    }

    public static Class<?> define(String dottedName, byte[] bytes) {
        try {
            return Class.forName(dottedName, true, new DefiningClassLoader(dottedName, bytes));
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.equals(dottedName)) return super.loadClass(name, resolve);
        synchronized (getClassLoadingLock(name)) {
            final Class<?> loaded = findLoadedClass(name);
            return loaded != null ? loaded : defineClass(name, bytes, 0, bytes.length);
        }
    }
}
