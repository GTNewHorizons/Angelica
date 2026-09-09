package com.gtnewhorizons.angelica.sdlgpu.glsm;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class GlsmSdlRedirectAgent {

    private static final String GTNHLIB = "com/gtnewhorizon/gtnhlib/";
    private static final List<String> FAILURES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger TRANSFORMED_COUNT = new AtomicInteger();

    private GlsmSdlRedirectAgent() {}

    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new Redirector());
    }

    public static List<String> failures() {
        return List.copyOf(FAILURES);
    }

    public static int transformedCount() {
        return TRANSFORMED_COUNT.get();
    }

    private static final class Redirector implements ClassFileTransformer {

        private final GLSMRedirector redirector = new GLSMRedirector();

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] classfile) {
            if (className == null || !className.startsWith(GTNHLIB)) return null;
            try {
                if (!redirector.shouldTransform(classfile)) return null;
                final ClassNode node = new ClassNode();
                new ClassReader(classfile).accept(node, 0);
                if (!redirector.transformClassNode(className.replace('/', '.'), node)) return null;
                final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                TRANSFORMED_COUNT.incrementAndGet();
                return writer.toByteArray();
            } catch (Throwable t) {
                FAILURES.add(className + ": " + t);
                return null;
            }
        }
    }
}
