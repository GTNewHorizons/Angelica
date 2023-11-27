package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.*;

/**
 * Redirects all Tessellator.instance field accesses to go through our TessellatorManager.
 */
public class TessellatorTransformer implements IClassTransformer {

    private static final ClassConstantPoolParser parser = new ClassConstantPoolParser("net/minecraft/client/renderer/Tessellator");

    @Override
    public byte[] transform(final String className, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        // Ignore classes that are excluded from transformation
        for (String exclusion : GLStateManagerTransformer.TransformerExclusions) {
            if (className.startsWith(exclusion)) {
                return basicClass;
            }
        }

        if (!parser.find(basicClass)) {
            return basicClass;
        }

        final ClassReader cr = new ClassReader(basicClass);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        boolean changed = false;

        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode node : mn.instructions) {
                if (node.getOpcode() == Opcodes.GETSTATIC && node instanceof FieldInsnNode fNode) {
                    if ((fNode.name.equals("field_78398_a") || fNode.name.equals("instance")) && fNode.owner.equals("net/minecraft/client/renderer/Tessellator")) {
                        MethodInsnNode getNode = new MethodInsnNode(Opcodes.INVOKESTATIC, "com/gtnewhorizons/angelica/glsm/TessellatorManager", "get", "()Lnet/minecraft/client/renderer/Tessellator;", false);
                        mn.instructions.set(fNode, getNode);
                        changed = true;
                    }
                }
            }
        }

        if (changed) {
            ClassWriter cw = new ClassWriter(0);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;

    }
}
