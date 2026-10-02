package com.gtnewhorizons.angelica.experimental.surround;

import makamys.mixingasm.api.MixinSafeTransformer;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@MixinSafeTransformer
public final class LocalSortingTransformer implements IClassTransformer {

    public static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        SEEN.add(transformedName);
        if (bytes == null || ((bytes[6] & 0xFF) << 8 | bytes[7] & 0xFF) > Opcodes.V1_8) {
            return bytes;
        }
        final ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new LocalVariablesSorter(Opcodes.ASM5, access, desc, super.visitMethod(access, name, desc, signature, exceptions)) {
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
}
