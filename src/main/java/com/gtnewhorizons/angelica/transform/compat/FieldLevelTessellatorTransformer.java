package com.gtnewhorizons.angelica.transform.compat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FieldLevelTessellatorTransformer implements IClassTransformer {

    private static final Map<String, List<String>> patchMethods = ImmutableMap.of(
        "com.tierzero.stacksonstacks.util.ClientUtils", ImmutableList.of("drawQuad", "drawRectangularPrism")
    );

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
                injectLocalTessellatorAndReplaceFieldUsage(method);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }

    public static void injectLocalTessellatorAndReplaceFieldUsage(MethodNode mn) {
        // This part searches the instructions to see if there is any usage of a Tessellator field
        // which is NOT owned by Minecraft itself. This means anyone that caches it into a field will get caught
        // but not direct usage of Tessellator.instance.whatever()
        List<String> fields = new ArrayList<>();
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            if (in instanceof FieldInsnNode fin) {
                if (
                    (fin.getOpcode() == Opcodes.GETSTATIC || fin.getOpcode() == Opcodes.GETFIELD)
                        && fin.desc.equals("Lnet/minecraft/client/renderer/Tessellator;")
                        && !fin.owner.equals("net/minecraft/client/renderer/Tessellator")
                ) {
                    fields.add(fin.owner + ";" + fin.name);
                }
            }
        }

        if (fields.isEmpty()) return;

        // Injects a new local to store our new local Tessellator in
        int localIndex = mn.maxLocals;
        LabelNode startLabel = new LabelNode();
        LabelNode endLabel = new LabelNode();
        LocalVariableNode tesNode = new LocalVariableNode("tes", "Lnet/minecraft/client/renderer/Tessellator;", null, startLabel, endLabel, localIndex);
        mn.localVariables.add(tesNode);

        // Actually initialize the new local Tessellator
        InsnList list = new InsnList();
        list.add(startLabel);
        list.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/client/renderer/Tessellator", AngelicaTweaker.isObfEnv() ? "field_78398_a" : "instance", "Lnet/minecraft/client/renderer/Tessellator;"));
        list.add(new VarInsnNode(Opcodes.ASTORE, localIndex));
        mn.instructions.insert(list);

        // This searches through all the instructions and finds usage of the previously discovered fields, then it replaces them
        // with an ALOAD for our new local instead of the GETSTATIC/GETFIELD
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            if (in instanceof FieldInsnNode fin) {
                String hash = fin.owner + ";" + fin.name;
                if (
                    (fin.getOpcode() == Opcodes.GETSTATIC || fin.getOpcode() == Opcodes.GETFIELD)
                        && fin.desc.equals("Lnet/minecraft/client/renderer/Tessellator;")
                        && fields.contains(hash)
                ) {
                    mn.instructions.insertBefore(in, new VarInsnNode(Opcodes.ALOAD, localIndex));
                    mn.instructions.remove(in);
                }
            }
        }
        mn.instructions.add(endLabel);
    }

}
