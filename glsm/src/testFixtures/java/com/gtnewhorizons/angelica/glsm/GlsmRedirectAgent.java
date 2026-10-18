package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.loading.ClassDump;
import com.gtnewhorizons.angelica.glsm.loading.ModRedirector;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.fail;

public final class GlsmRedirectAgent {

    private static final String GTNHLIB = "com/gtnewhorizon/gtnhlib/";
    private static final List<Throwable> FAILURES = new CopyOnWriteArrayList<>();
    private static final AtomicInteger TRANSFORMED = new AtomicInteger();
    private static boolean installed;

    private GlsmRedirectAgent() {}

    public static void premain(String args, Instrumentation instrumentation) {
        install(instrumentation);
    }

    public static void install(Instrumentation instrumentation) {
        final ModRedirector redirector = new ModRedirector(ClassDump.DISABLED);
        addTransformer(instrumentation, (className, classfile) -> {
            if (!className.startsWith(GTNHLIB)) return null;
            final byte[] out = redirector.transform(className.replace('/', '.'), classfile, null);
            if (out == classfile) return null;
            TRANSFORMED.incrementAndGet();
            return out;
        });
        installed = true;
    }

    public static void addTransformer(Instrumentation instrumentation, BiFunction<String, byte[], byte[]> transform) {
        instrumentation.addTransformer(new Guarded(transform));
    }

    public static void requireInstalled() {
        if (!installed) throw new IllegalStateException("GL tests need the GLSM redirect agent");
    }

    public static void assertNoFailures() {
        if (!FAILURES.isEmpty()) fail("agent transform failures: " + FAILURES, FAILURES.get(0));
    }

    public static int transformedCount() {
        return TRANSFORMED.get();
    }

    private static final class Guarded implements ClassFileTransformer {

        private final BiFunction<String, byte[], byte[]> transform;

        private Guarded(BiFunction<String, byte[], byte[]> transform) {
            this.transform = transform;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] classfile) {
            if (className == null) return null;
            try {
                return transform.apply(className, classfile);
            } catch (Throwable t) {
                FAILURES.add(new IllegalStateException("transforming " + className, t));
                return null;
            }
        }
    }
}
