package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.*;
import org.spongepowered.asm.lib.tree.*;

/**
 * Redirects all Tessellator.instance accesses to go through our TessellatorManager.
 */
public class TessellatorTransformer implements IClassTransformer {
    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Ignore classes that are excluded from transformation
        for (String exclusion : GLStateManagerTransformer.TransformerExclusions) {
            if (className.startsWith(exclusion)) {
                return basicClass;
            }
        }

        final ClassReader reader = new ClassReader(basicClass);
        ClassNode node = new ClassNode();
        reader.accept(node, Opcodes.ASM9);
        boolean changed = false;
        for(MethodNode m : node.methods) {
            for(AbstractInsnNode insn : m.instructions) {
                if(insn.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode fNode = (FieldInsnNode)insn;
                    if(fNode.name.equals("instance") && fNode.owner.equals("net/minecraft/client/renderer/Tessellator")) {
                        MethodInsnNode getNode = new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/glsm/TessellatorManager", "get", "()Lnet/minecraft/client/renderer/Tessellator;", false);
                        m.instructions.set(fNode, getNode);
                        changed = true;
                        break;
                    }
                }
            }
        }

        if(!changed)
            return basicClass;

        ClassWriter writer = new ClassWriter(reader, 0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
