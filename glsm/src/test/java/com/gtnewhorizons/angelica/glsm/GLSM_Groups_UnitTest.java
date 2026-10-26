package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import com.gtnewhorizons.angelica.glsm.testutil.GlsmClasses;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSM_Groups_UnitTest {

    private static final String PACKAGE = "com/gtnewhorizons/angelica/glsm/";
    private static final String CORE = PACKAGE + "GLStateManager";

    private static boolean counted(int access, String name) {
        return (access & Opcodes.ACC_SYNTHETIC) == 0 && name.indexOf('$') < 0;
    }

    private static String simple(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }

    @Test
    void memberNamesDeclaredOnce() {
        final Map<String, Set<String>> methods = new TreeMap<>();
        final Map<String, Set<String>> fields = new TreeMap<>();
        collect(GlsmClasses.classes(), methods, fields);
        final List<String> offenders = offenders(methods, fields);
        assertTrue(offenders.isEmpty(), "Member names declared in more than one class:\n  " + String.join("\n  ", offenders));
    }

    private static void collect(Collection<String> classes, Map<String, Set<String>> methods, Map<String, Set<String>> fields) {
        for (String type : classes) {
            final ClassNode node = GlsmClasses.load(type);
            for (MethodNode method : node.methods) {
                if (!counted(method.access, method.name) || method.name.equals("<init>") || method.name.equals("<clinit>")) continue;
                methods.computeIfAbsent(method.name, k -> new TreeSet<>()).add(simple(type));
            }
            for (FieldNode field : node.fields) {
                if (!counted(field.access, field.name)) continue;
                fields.computeIfAbsent(field.name, k -> new TreeSet<>()).add(simple(type));
            }
        }
    }

    private static List<String> offenders(Map<String, Set<String>> methods, Map<String, Set<String>> fields) {
        final List<String> offenders = new ArrayList<>();
        methods.forEach((name, declaring) -> {
            if (declaring.size() > 1) offenders.add("method " + name + " declared in " + declaring);
        });
        fields.forEach((name, declaring) -> {
            if (declaring.size() > 1) offenders.add("field " + name + " declared in " + declaring);
        });
        return offenders;
    }

    @Test
    void staticInitReachesOnlyOwnClassAndCore() {
        final Set<String> classes = new HashSet<>(GlsmClasses.classes());
        final List<String> failures = new ArrayList<>();
        for (String type : classes) {
            final MethodNode clinit = findMethod(GlsmClasses.load(type), "<clinit>");
            if (clinit == null) continue;
            final Set<String> visited = new HashSet<>();
            final Deque<GlsmClasses.Resolved> pending = new ArrayDeque<>();
            visited.add(type + ".<clinit>" + clinit.desc);
            pending.add(new GlsmClasses.Resolved(type, clinit));
            while (!pending.isEmpty()) {
                final GlsmClasses.Resolved current = pending.poll();
                final String currentOwner = current.owner();
                final MethodNode body = current.method();
                for (AbstractInsnNode insn : body.instructions) {
                    final String owner;
                    final String what;
                    if (insn instanceof MethodInsnNode call) {
                        owner = call.owner;
                        what = call.name;
                        if (owner.startsWith(PACKAGE)) {
                            final GlsmClasses.Resolved target = GlsmClasses.resolve(owner, call.name, call.desc);
                            if (target != null && visited.add(target.owner() + "." + call.name + call.desc)) pending.add(target);
                        }
                    } else if (insn instanceof FieldInsnNode field) {
                        owner = field.owner;
                        what = field.name;
                    } else {
                        continue;
                    }
                    if (!classes.contains(owner) || owner.equals(type)) continue;
                    if (owner.equals(CORE) && !type.equals(CORE)) continue;
                    failures.add(simple(type) + ".<clinit> via " + simple(currentOwner) + "." + body.name + ": " + simple(owner) + "." + what);
                }
            }
        }
        assertTrue(failures.isEmpty(), "Static initializers may reach only their own class and GLStateManager:\n  " + String.join("\n  ", new TreeSet<>(failures)));
    }

    @Test
    void classListMatchesPackage() {
        assertEquals(GlsmClasses.packageClasses(), new TreeSet<>(GLSMRedirector.glsmClasses()));
    }

    private static MethodNode findMethod(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)) return method;
        }
        return null;
    }
}
