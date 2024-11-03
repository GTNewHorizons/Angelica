package com.gtnewhorizons.angelica.transform.compat.transformers.generic;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.compat.CompatRegistry;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.util.List;
import java.util.Map;

public class HUDCachingEarlyReturnTransformer implements IClassTransformer {

    private static final Map<String, List<String>> patchMethods = CompatRegistry.INSTANCE.getHudCachingEarlyReturns();

    private static final String HUDCaching = "com/gtnewhorizons/angelica/hudcaching/HUDCaching$HUDCachingHooks";

    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!patchMethods.containsKey(transformedName)) {
            return basicClass;
        }

        ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        for (String targetMethod : patchMethods.get(transformedName)) {
            for (MethodNode method : cn.methods) {
                if (!method.name.equals(targetMethod)) continue;

                InsnList list = new InsnList();
                LabelNode exitLabel = new LabelNode();
                AngelicaTweaker.LOGGER
                    .info("Injecting HUDCaching Conditional Return: " + transformedName + "#" + method.name);
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HUDCaching, "shouldReturnEarly", "()Z", false));
                list.add(new JumpInsnNode(Opcodes.IFEQ, exitLabel));
                if (method.desc.endsWith("Z") || method.desc.endsWith("I")) {
                    list.add(new InsnNode(Opcodes.ICONST_0));
                    list.add(new InsnNode(Opcodes.IRETURN));
                } else if (method.desc.endsWith("V")) {
                    list.add(new InsnNode(Opcodes.RETURN));
                } else {
                    AngelicaTweaker.LOGGER.warn(
                        "HUDCaching Conditional Return - Unknown return type: " + transformedName
                            + "#"
                            + method.name
                            + ":"
                            + method.desc);
                    return basicClass;
                }
                list.add(exitLabel);
                method.instructions.insert(list);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.LOGGER.info("[Angelica Compat]Applied HUDCachingEarlyReturn fixes to {}", transformedName);
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
