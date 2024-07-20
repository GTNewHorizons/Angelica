package com.gtnewhorizons.angelica.transform;

import com.gtnewhorizons.angelica.hudcaching.HUDCaching;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


public class HUDCachingTransformer implements IClassTransformer {
    private static final String HUDCaching = "com/gtnewhorizons/angelica/hudcaching/HUDCaching";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        final boolean changed = transformClassNode(transformedName, cn);
        if (changed) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }

    public boolean transformClassNode(String transformedName, ClassNode cn)
    {
        if (cn == null) {
            return false;
        }

        boolean changed = false;
        for (MethodNode method : cn.methods) {
            if (method.visibleAnnotations != null && method.visibleAnnotations.size() > 0) {
                for (AnnotationNode anno : method.visibleAnnotations) {
                    if (anno.desc.equals("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;")) {
                        if (method.desc.startsWith("(Lnet/minecraftforge/client/event/RenderGameOverlayEvent")) { // no ending to capture base and subclasses
                            AngelicaTweaker.LOGGER.info("Injecting HUDCaching Conditional Return: " + transformedName + "#" + method.name);
                            final InsnList list = new InsnList();
                            LabelNode exitLabel = new LabelNode();
                            list.add(new LdcInsnNode(transformedName));
                            list.add(new LdcInsnNode(method.name));
                            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HUDCaching, "shouldReturnEarly", "(Ljava/lang/String;Ljava/lang/String;)Z", false));
                            list.add(new JumpInsnNode(Opcodes.IFEQ, exitLabel));
                            list.add(new InsnNode(Opcodes.RETURN));
                            method.instructions.insert(exitLabel); // label will be after the list
                            method.instructions.insert(list);

                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }
}
