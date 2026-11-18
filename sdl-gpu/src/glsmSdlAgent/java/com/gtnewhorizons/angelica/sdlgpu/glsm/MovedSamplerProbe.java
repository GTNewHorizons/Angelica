package com.gtnewhorizons.angelica.sdlgpu.glsm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MovedSamplerProbe implements AutoCloseable {
    private static final String COPIES = "com/gtnewhorizons/angelica/sdlgpu/sampler/MovedSamplerCopies";
    private static final String RESOURCES = "com/gtnewhorizons/angelica/sdlgpu/resource/ResourceManager";
    private static final String PROBE = "com/gtnewhorizons/angelica/sdlgpu/glsm/MovedSamplerProbe";
    private static volatile MovedSamplerProbe active;

    private final AtomicInteger refreshCalls = new AtomicInteger();
    private final ConcurrentHashMap<Long, AtomicInteger> writes = new ConcurrentHashMap<>();

    public MovedSamplerProbe() {
        if (active != null) throw new IllegalStateException("A moved-sampler probe is already active");
        active = this;
    }

    public int refreshCalls() {
        return refreshCalls.get();
    }

    public void watchWrites(long texture) {
        writes.putIfAbsent(texture, new AtomicInteger());
    }

    public int writesTo(long texture) {
        return writes.get(texture).get();
    }

    public static void onRefresh() {
        final MovedSamplerProbe probe = active;
        if (probe != null) probe.refreshCalls.incrementAndGet();
    }

    public static void onWrite(long texture) {
        final MovedSamplerProbe probe = active;
        if (probe == null) return;
        final AtomicInteger count = probe.writes.get(texture);
        if (count != null) count.incrementAndGet();
    }

    @Override
    public void close() {
        active = null;
    }

    static byte[] instrument(String className, byte[] classfile) {
        final boolean writes = RESOURCES.equals(className);
        if (!writes && !COPIES.equals(className)) return null;
        final String methodName = writes ? "markTextureWritten" : "refresh";
        final ClassNode node = new ClassNode();
        new ClassReader(classfile).accept(node, 0);
        final var method = node.methods.stream().filter(m -> m.name.equals(methodName)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing observed method: " + className + "." + methodName));
        final InsnList hook = new InsnList();
        if (writes) hook.add(new VarInsnNode(Opcodes.LLOAD, 1));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PROBE, writes ? "onWrite" : "onRefresh", writes ? "(J)V" : "()V", false));
        method.instructions.insert(hook);
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
