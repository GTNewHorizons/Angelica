package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassVisitor;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.MethodVisitor;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.commons.Method;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GLStateManagerTransformer implements IClassTransformer {
    /*
     * Redirects a subset of GL11 calls to GLStateManager
     *  NOTE: Still need to verify compatibility with Mixins and Lwjgl3ify
     */
    public static final String GLStateTracker = "com/gtnewhorizons/angelica/glsm/GLStateManager";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL14 = "org/lwjgl/opengl/GL14";
    private static final String ExtBlendFunc = "org/lwjgl/opengl/EXTBlendFuncSeparate";

    public static final Map<String, Set<String>> EnabledRedirects = ImmutableMap.of(
         GL11, Sets.newHashSet("glBindTexture", "glTexImage2D", "glDeleteTextures", "glEnable", "glDisable", "glDepthFunc", "glDepthMask",
            "glColorMask", "glAlphaFunc")
        ,GL14, Sets.newHashSet("glBlendFuncSeparate")
        ,ExtBlendFunc, Sets.newHashSet("glBlendFuncSeparate")
    );


    private static final List<String> TransformerExclusions = new ArrayList<>(Arrays.asList(
        "org.lwjgl", "com.gtnewhorizons.angelica.glsm.", "com.gtnewhorizons.angelica.transform", "me.eigenraven.lwjgl3ify")
    );
    public static int remaps = 0, calls = 0;

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;


        // Ignore classes that are excluded from transformation - Doesn't seem to fully work without the TransformerExclusions
        for (String exclusion : TransformerExclusions) {
            if (className.startsWith(exclusion)) {
                return basicClass;
            }
        }

        final ClassReader reader = new ClassReader(basicClass);
        Set<Method> eligibleMethods = findEligibleMethods(reader);
        if (eligibleMethods.isEmpty()) {
            return basicClass;
        }

        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if (!eligibleMethods.contains(new Method(name, desc))) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        final Set<String> redirects = EnabledRedirects.get(owner);
                        if (redirects != null && redirects.contains(name)) {
                            final String shortOwner = owner.substring(owner.lastIndexOf("/")+1);
                            AngelicaTweaker.LOGGER.info("Redirecting call in {} from {}.{}{} to GLStateManager.{}{}", className, shortOwner, name, desc, name, desc);
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
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String methodDesc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        final Set<String> redirects = EnabledRedirects.get(owner);
                        if(redirects != null && redirects.contains(name)) {
                            eligibleMethods.add(new Method(methodName, methodDesc));
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return eligibleMethods;
    }

}
