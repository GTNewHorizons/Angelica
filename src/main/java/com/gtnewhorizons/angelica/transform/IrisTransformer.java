package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.Sets;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassVisitor;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.MethodVisitor;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.commons.Method;
import org.spongepowered.asm.lib.commons.Remapper;

import java.util.HashSet;
import java.util.Set;

public class IrisTransformer extends Remapper implements IClassTransformer {
    /*
     * Redirects a subset of GL11 calls to GLStateManager
     *  NOTE: Still need to verify compatibility with Mixins and Lwjgl3ify
     */
    public static final String GLStateTrackerClass = "com.gtnewhorizons.angelica.client.GLStateManager";
    private static final String GLStateTracker = GLStateTrackerClass.replace(".", "/");
    private static final String GL11Class = "org.lwjgl.opengl.GL11";
    private static final String GL11 = GL11Class.replace(".", "/");

    private static final Set<String> EnabledRedirects = Sets.newHashSet("glBindTexture", "glTexImage2D", "glDeleteTextures");

    public static int remaps = 0, calls = 0;

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Ignore the state tracker itself
        if (className.equals(GLStateTrackerClass)) return basicClass;

        ClassReader reader = new ClassReader(basicClass);
        Set<Method> eligibleMethods = findEligibleMethods(reader);
        if (eligibleMethods.isEmpty()) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (!eligibleMethods.contains(new Method(name, desc))) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (owner.equals(GL11) && EnabledRedirects.contains(name)) {
                            AngelicaTweaker.LOGGER.info("Redirecting call in {} from GL11.{}{} to GLStateManager.{}{}", className, name, desc, name, desc);
                            owner = GLStateTracker;
                            remaps++;
                        }
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }


    private Set<Method> findEligibleMethods(ClassReader reader) {
        Set<Method> eligibleMethods = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (owner.equals(GL11) && EnabledRedirects.contains(name)) {
                            eligibleMethods.add(new Method(methodName, methodDesc));
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return eligibleMethods;
    }

}
