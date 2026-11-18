package com.gtnewhorizons.angelica.glsm.testutil;

import com.gtnewhorizons.angelica.glsm.GlsmRedirectAgent;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* Test agent: strips ACC_FINAL from listed static finals at load to avoid JIT inlining them when tests want to mutate them */
public final class MutableStatics {

    private static final Map<String, Set<String>> FIELDS_BY_OWNER = new HashMap<>();

    static {
        add("com/gtnewhorizons/angelica/glsm/backend/BackendManager", "RENDER_BACKEND");
        add("com/gtnewhorizons/angelica/glsm/GLStateManager", "MainThread");
        add("com/gtnewhorizons/angelica/config/SystemProperties", "UNMAPPED_GL", "SDL_DISABLE_IN_PASS_CLEAR");
        add("net/coderbot/iris/Iris", "enabled");
    }

    private MutableStatics() {}

    private static void add(String owner, String... fields) {
        FIELDS_BY_OWNER.put(owner, new HashSet<>(Arrays.asList(fields)));
    }

    public static void premain(String args, Instrumentation instrumentation) {
        install(instrumentation);
    }

    public static void install(Instrumentation instrumentation) {
        GlsmRedirectAgent.addTransformer(instrumentation, (className, classfile) -> {
            final Set<String> fields = FIELDS_BY_OWNER.get(className);
            return fields == null ? null : stripFinal(classfile, fields);
        });
    }

    private static byte[] stripFinal(byte[] classfile, Set<String> fields) {
        final ClassNode node = new ClassNode();
        new ClassReader(classfile).accept(node, 0);
        int patched = 0;
        for (FieldNode field : node.fields) {
            if (fields.contains(field.name)) {
                field.access &= ~Opcodes.ACC_FINAL;
                patched++;
            }
        }
        if (patched != fields.size()) throw new IllegalStateException(node.name + " lacks one of " + fields);
        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
