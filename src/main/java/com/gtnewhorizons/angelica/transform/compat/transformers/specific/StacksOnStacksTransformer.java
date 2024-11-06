package com.gtnewhorizons.angelica.transform.compat.transformers.specific;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.ClassWriter;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.FieldNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.TypeInsnNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

import java.util.List;

public class StacksOnStacksTransformer implements IClassTransformer {

    private static final String RenderTilePile = "com.tierzero.stacksonstacks.client.render.RenderTilePile";
    private static final List<String> staticRemovers = ImmutableList.of("ingotRender", "gemRender", "dustRender");

    private static final String ClientUtils = "com.tierzero.stacksonstacks.util.ClientUtils";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!transformedName.equals(RenderTilePile) && !transformedName.equals(ClientUtils)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        if (transformedName.equals(RenderTilePile)) {
            transformRenderTilePile(cn);
        } else {
            transformClientUtils(cn);
        }

        ClassWriter cw = new ClassWriter(0);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.LOGGER.info("[AngelicaCompat]Extra Transformers: Applied StacksOnStacksTransformer");
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }

    /**
     * Injects initializers for the field level PileRender objects which used to be static into the <init>
     */
    private static InsnList buildPilerRenderInitializer(String type) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        InsnList list = new InsnList();
        list.add(start);
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new TypeInsnNode(Opcodes.NEW, "com/tierzero/stacksonstacks/client/render/PileRender" + type));
        list.add(new InsnNode(Opcodes.DUP));
        list.add(
            new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "com/tierzero/stacksonstacks/client/render/PileRender" + type,
                "<init>",
                "()V"));
        list.add(
            new FieldInsnNode(
                Opcodes.PUTFIELD,
                "com/tierzero/stacksonstacks/client/render/RenderTilePile",
                type.toLowerCase() + "Render",
                "Lcom/tierzero/stacksonstacks/client/render/PileRender;"));
        list.add(end);
        return list;
    }

    private static void transformRenderTilePile(ClassNode cn) {
        MethodNode clinit = null;
        for (MethodNode mn : cn.methods) {
            // Handles injecting field initializers for the various PileRender fields which used to be static
            // but we're removing <clinit> entirely and doing the initialization in <init>
            if (mn.name.equals("<init>")) {
                AbstractInsnNode objectInsn = null;
                // This removes the early return, and gets a reference to the self initialization so we can insert after
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode ain = mn.instructions.get(i);
                    if (ain.getOpcode() == Opcodes.RETURN) mn.instructions.remove(ain);
                    if (ain.getOpcode() == Opcodes.INVOKESPECIAL) objectInsn = mn.instructions.get(i);
                }
                // Build the initializer InsnLists and then insert them in reverse order, such that the dustRender comes
                // last
                // because we are adding the return opcode back in as part of that one's InsnList
                InsnList ingotRender = buildPilerRenderInitializer("Ingot");
                InsnList gemRender = buildPilerRenderInitializer("Gem");
                InsnList dustRender = buildPilerRenderInitializer("Dust");
                dustRender.add(new InsnNode(Opcodes.RETURN));
                mn.instructions.insert(objectInsn, dustRender);
                mn.instructions.insert(objectInsn, gemRender);
                mn.instructions.insert(objectInsn, ingotRender);
            }
            // Just catching the MethodNode for <clinit> so we can remove it later after we're done iterating
            if (mn.name.equals("<clinit>")) {
                clinit = mn;
            }
            if (mn.name.equals("renderWorldBlock")) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode in = mn.instructions.get(i);
                    // This part removes the GETSTATIC for the field level PileRender objects which used to be static,
                    // and replaces them with
                    // a non-static GETFIELD
                    if (in instanceof FieldInsnNode fin) {
                        if (fin.getOpcode() == Opcodes.GETSTATIC && staticRemovers.contains(fin.name)) {
                            FieldInsnNode nonStatic = new FieldInsnNode(
                                Opcodes.GETFIELD,
                                "com/tierzero/stacksonstacks/client/render/RenderTilePile",
                                fin.name,
                                "Lcom/tierzero/stacksonstacks/client/render/PileRender;");
                            mn.instructions.insertBefore(fin, new VarInsnNode(Opcodes.ALOAD, 0));
                            mn.instructions.insertBefore(fin, nonStatic);
                            mn.instructions.remove(fin);
                        }
                    }
                }
            }
        }
        // Remove clinit after iterating. Nothing else happens in here besides the field initialization, so no reason to
        // drill down
        // into the instructions
        if (clinit != null) cn.methods.remove(clinit);

        // Remove the static access bits from the desired field names
        for (FieldNode fn : cn.fields) {
            if (staticRemovers.contains(fn.name)) {
                fn.access = fn.access & (~Opcodes.ACC_STATIC);
            }
        }
    }

    private static void transformClientUtils(ClassNode cn) {
        // Various things throughout the ISBRH call these functions which in turn call GL11.glPush/PopMatrix
        // Upon checking, these aren't called anywhere but in the ISBRH, and they're entirely unnecessary in there.
        // This just makes the methods no-op, since they aren't required by any other part of the mod to be working.
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("pushMatrix")) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode in = mn.instructions.get(i);
                    if (in.getOpcode() == Opcodes.INVOKESTATIC) {
                        mn.instructions.remove(in);
                    }
                }
            } else if (mn.name.equals("popMatrix")) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode in = mn.instructions.get(i);
                    if (in.getOpcode() == Opcodes.INVOKESTATIC) {
                        mn.instructions.remove(in);
                    }
                }
            }
        }
    }
}
