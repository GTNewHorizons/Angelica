package com.gtnewhorizons.angelica.glsm;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GLSM_ExitCaptureCoverage_UnitTest {

    private static final String PACKAGE = "com/gtnewhorizons/angelica/glsm/";
    private static final String MANAGER = PACKAGE + "GLStateManager";
    private static final String RENDER_SYSTEM = PACKAGE + "RenderSystem";
    private static final String CAPTURE = PACKAGE + "ExitStateCapture";

    private static final Set<String> RECORDING = Set.of(
        "reject", "write", "writeBlend", "writeAlpha", "writeDepthColor", "capability", "material",
        "matrixChange", "pushAttrib", "popAttrib", "pushMatrix", "popMatrix",
        "requireDrawState", "requireMaterialBakeInputs", "requireVertexDefaults", "requireTextureFilter",
        "beginTextureFilter", "finishTextureFilterSave", "beginTextureFilterRestore", "finishTextureFilterRestore",
        "failTextureFilter");

    private static final Set<String> IGNORED = Set.of(
        // glBegin and glEnd reject
        "glVertex2d", "glVertex2f", "glVertex2i", "glVertex3d", "glVertex3f", "glVertex3i",
        // No render state
        "glDebugMessageCallback", "glDebugMessageControl", "glDebugMessageInsert", "glObjectLabel",
        "glPushDebugGroup", "glPopDebugGroup",
        // Others
        "glInitNames", "glPushName", "glPopName", "glLoadName", "isFramebufferEnabled", "gluUnProject", "gluErrorString");

    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Set<String> reaching = new HashSet<>();
    private final Set<String> notReaching = new HashSet<>();
    private Set<String> counted = RECORDING;

    @Test
    void everyEntryPointRejectsOrRecordsTheExitCapture() {
        final Set<String> entryPoints = new HashSet<>(GLSMRedirector.getGLPrefixMethodRedirects().values());
        GLSMRedirector.getNamedClassMethodRedirects().values().forEach(redirects -> entryPoints.addAll(redirects.values()));
        final List<String> missing = missing(MANAGER, name -> name.startsWith("gl") || entryPoints.contains(name), method -> reachesCapture(MANAGER, method));
        assertTrue(missing.isEmpty(), "Entry points that neither reject nor record an exit capture:\n  " + String.join("\n  ", missing));
    }

    // None of RenderSystem's writes are modeled. A method that drives the backend or DSA itself must reject itself, since
    // a GLSM call it also makes (glBindTexture) may only record.
    @Test
    void everyRenderSystemCallRejectsTheExitCapture() {
        counted = Set.of("reject");
        final List<String> missing = missing(RENDER_SYSTEM, name -> !name.equals("initRenderer") && !name.equals("getVRAM")
            && !name.matches("(supports|has|is|getMax|getGl|parse|probe).*"), method -> reachesCapture(RENDER_SYSTEM, method)
            && (!calls(method, owner -> owner.startsWith(PACKAGE + "backend/") || owner.startsWith(PACKAGE + "dsa/"))
                || calls(method, owner -> owner.equals(MANAGER), "rejectExitCapture", "beforeUncapturedStateChange")));
        assertTrue(missing.isEmpty(), "RenderSystem methods that do not reject an exit capture:\n  " + String.join("\n  ", missing));
    }

    private List<String> missing(String owner, Predicate<String> checked, Predicate<MethodNode> covered) {
        final List<String> missing = new ArrayList<>();
        for (MethodNode method : load(owner).methods) {
            if ((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) continue;
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            if (!checked.test(method.name) || IGNORED.contains(method.name)) continue;
            if (!covered.test(method)) missing.add(method.name + method.desc);
        }
        missing.sort(null);
        return missing;
    }

    private static boolean calls(MethodNode method, Predicate<String> owner, String... names) {
        final Set<String> wanted = Set.of(names);
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && owner.test(call.owner) && (wanted.isEmpty() || wanted.contains(call.name))) return true;
        }
        return false;
    }

    private boolean reachesCapture(String owner, MethodNode root) {
        final String key = owner + '.' + root.name + root.desc;
        if (reaching.contains(key)) return true;
        if (notReaching.contains(key)) return false;
        final Set<String> visited = new HashSet<>();
        if (search(owner, root, visited)) {
            reaching.add(key);
            return true;
        }
        notReaching.addAll(visited);
        return false;
    }

    private boolean search(String owner, MethodNode method, Set<String> visited) {
        final String key = owner + '.' + method.name + method.desc;
        if (reaching.contains(key)) return true;
        if (notReaching.contains(key) || !visited.add(key)) return false;
        for (AbstractInsnNode insn : method.instructions) {
            if (!(insn instanceof MethodInsnNode call)) continue;
            if (call.owner.equals(CAPTURE)) {
                if (counted.contains(call.name)) return true;
                continue;
            }
            if (!call.owner.startsWith(PACKAGE)) continue;
            final String[] declaring = new String[1];
            final MethodNode target = resolve(call.owner, call.name, call.desc, declaring);
            if (target != null && search(declaring[0], target, visited)) return true;
        }
        return false;
    }

    private MethodNode resolve(String owner, String name, String desc, String[] declaring) {
        for (String type = owner; type != null && type.startsWith(PACKAGE); ) {
            final ClassNode node = load(type);
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    if (method.instructions.size() == 0) return null;
                    declaring[0] = type;
                    return method;
                }
            }
            type = node.superName;
        }
        return null;
    }

    private ClassNode load(String internalName) {
        return classes.computeIfAbsent(internalName, name -> {
            try (InputStream in = GLSM_ExitCaptureCoverage_UnitTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
                if (in == null) throw new IllegalStateException("Missing class file for " + name);
                final ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
