package com.gtnewhorizons.angelica.transform;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Transformer for {@link EntityRenderer} */
public class ACTEntityRenderer implements IClassTransformer, Opcodes {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraft.client.renderer.EntityRenderer".equals(transformedName)) {
            AngelicaTweaker.LOGGER.debug("transforming {} {}", name, transformedName);
            ClassReader cr = new ClassReader(basicClass);
            ClassNode cn = new ClassNode();
            cr.accept(cn, 0);
            for (MethodNode mn : cn.methods) {
                if (Names.entityRenderer_renderHand.equalsNameDesc(mn.name, mn.desc)) {
                    // Wraps the code from GL11.glPushMatrix() to GL11.glPopMatrix() in an if(!Shaders.isHandRendered) check
                    AngelicaTweaker.LOGGER.trace(" patching method {}.{}{}", transformedName, mn.name, mn.desc);
                    LabelNode label = new LabelNode();
                    for (AbstractInsnNode node : mn.instructions.toArray()) {
                        if (node instanceof MethodInsnNode mNode) {
                            if (Names.equals(mNode, "org/lwjgl/opengl/GL11", "glPushMatrix", "()V")) {
                                InsnList list = new InsnList();
                                list.add(new FieldInsnNode(GETSTATIC, "com/gtnewhorizons/angelica/client/Shaders", "isHandRendered", "Z"));
                                list.add(new JumpInsnNode(IFNE, label));
                                mn.instructions.insertBefore(node, list);
                            } else if (Names.equals(mNode, "org/lwjgl/opengl/GL11", "glPopMatrix", "()V")) {
                                InsnList list = new InsnList();
                                list.add(label);
                                list.add(new FrameNode(F_SAME, 0, null, 0, null));
                                mn.instructions.insert(node, list);
                            }
                        }
                    }
                }
            }
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }

}
