package com.gtnewhorizons.angelica.loading.fml.compat.transformers.generic;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.LocalVariableNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

public class FieldLevelTessellatorTransformer {

    public static void transform(ClassNode cn, List<String> patchMethods) {
        for (MethodNode method : cn.methods) {
            if (patchMethods.contains(method.name)) {
                injectLocalTessellatorAndReplaceFieldUsage(method);
            }
        }
    }

    private static void injectLocalTessellatorAndReplaceFieldUsage(MethodNode mn) {
        // This part searches the instructions to see if there is any usage of a Tessellator field
        // which is NOT owned by Minecraft itself. This means anyone that caches it into a field will get caught
        // but not direct usage of Tessellator.instance.whatever()
        List<String> fields = new ArrayList<>();
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            if (in instanceof FieldInsnNode fin) {
                if ((fin.getOpcode() == Opcodes.GETSTATIC || fin.getOpcode() == Opcodes.GETFIELD)
                    && fin.desc.equals("Lnet/minecraft/client/renderer/Tessellator;")
                    && !fin.owner.equals("net/minecraft/client/renderer/Tessellator")) {
                    fields.add(fin.owner + ";" + fin.name);
                }
            }
        }

        if (fields.isEmpty()) return;

        // Injects a new local to store our new local Tessellator in
        int localIndex = mn.maxLocals;
        LabelNode startLabel = new LabelNode();
        LabelNode endLabel = new LabelNode();
        LocalVariableNode tesNode = new LocalVariableNode(
            "tes",
            "Lnet/minecraft/client/renderer/Tessellator;",
            null,
            startLabel,
            endLabel,
            localIndex);
        mn.localVariables.add(tesNode);

        // Actually initialize the new local Tessellator
        InsnList list = new InsnList();
        list.add(startLabel);
        list.add(
            new FieldInsnNode(
                Opcodes.GETSTATIC,
                "net/minecraft/client/renderer/Tessellator",
                AngelicaTweaker.obf("instance", "field_78398_a"),
                "Lnet/minecraft/client/renderer/Tessellator;"));
        list.add(new VarInsnNode(Opcodes.ASTORE, localIndex));
        mn.instructions.insert(list);

        // This searches through all the instructions and finds usage of the previously discovered fields, then it
        // replaces them
        // with an ALOAD for our new local instead of the GETSTATIC/GETFIELD
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            if (in instanceof FieldInsnNode fin) {
                String hash = fin.owner + ";" + fin.name;
                if ((fin.getOpcode() == Opcodes.GETSTATIC || fin.getOpcode() == Opcodes.GETFIELD)
                    && fin.desc.equals("Lnet/minecraft/client/renderer/Tessellator;")
                    && fields.contains(hash)) {
                    mn.instructions.insertBefore(in, new VarInsnNode(Opcodes.ALOAD, localIndex));
                    mn.instructions.remove(in);
                }
            }
        }
        mn.instructions.add(endLabel);
    }

}
