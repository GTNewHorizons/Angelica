package com.gtnewhorizons.angelica.transform.compat.transformers.generic;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;

import java.util.List;

public class HUDCachingEarlyReturnTransformer {

    private static final String HUDCaching = "com/gtnewhorizons/angelica/hudcaching/HUDCaching$HUDCachingHooks";

    public static void transform(ClassNode cn, List<String> patchMethods) {
        for (MethodNode method : cn.methods) {
            if (patchMethods.contains(method.name)) {
                InsnList list = new InsnList();
                LabelNode exitLabel = new LabelNode();
                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HUDCaching, "shouldReturnEarly", "()Z", false));
                list.add(new JumpInsnNode(Opcodes.IFEQ, exitLabel));
                if (method.desc.endsWith("Z") || method.desc.endsWith("I")) {
                    list.add(new InsnNode(Opcodes.ICONST_0));
                    list.add(new InsnNode(Opcodes.IRETURN));
                } else if (method.desc.endsWith("V")) {
                    list.add(new InsnNode(Opcodes.RETURN));
                } else {
                    AngelicaTweaker.LOGGER.warn(
                        "HUDCaching Conditional Return - Unknown return type: " + cn.name
                            + "#"
                            + method.name
                            + ":"
                            + method.desc);
                    return;
                }
                list.add(exitLabel);
                method.instructions.insert(list);
            }
        }
    }
}
