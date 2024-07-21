package com.gtnewhorizons.angelica.transform;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;


public class HUDCachingTransformer implements IClassTransformer {
    static final String HUDCaching = "com/gtnewhorizons/angelica/hudcaching/HUDCaching$HUDCachingHooks";
    static final Set<String> ReturnEarlyMethods = ImmutableSet.of(
        "thaumcraft.client.lib.RenderEventHandler#renderOverlay"
    );

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

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

    public boolean transformClassNode(String transformedName, ClassNode cn) {
        if (cn == null) {
            return false;
        }

        boolean changed = false;
        for (MethodNode method : cn.methods) {
            if (method.visibleAnnotations != null && method.visibleAnnotations.size() > 0) {
                for (AnnotationNode anno : method.visibleAnnotations) {
                    if (anno.desc.equals("Lcpw/mods/fml/common/eventhandler/SubscribeEvent;")) {
                        if (method.desc.startsWith("(Lnet/minecraftforge/client/event/RenderGameOverlayEvent")) { // no ending to capture base and subclasses
                            String methodSig = transformedName + "#" + method.name;
                            if (ReturnEarlyMethods.contains(methodSig)) {
                                final InsnList list = new InsnList();
                                LabelNode exitLabel = new LabelNode();
                                AngelicaTweaker.LOGGER.info("Injecting HUDCaching Conditional Return: " + methodSig);
                                list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HUDCaching, "shouldReturnEarly", "()Z", false));
                                list.add(new JumpInsnNode(Opcodes.IFEQ, exitLabel));
                                if (method.desc.endsWith("Z") || method.desc.endsWith("I")) {
                                    list.add(new InsnNode(Opcodes.ICONST_0));
                                    list.add(new InsnNode(Opcodes.IRETURN));
                                } else {
                                    list.add(new InsnNode(Opcodes.RETURN));
                                }
                                list.add(exitLabel);
                                method.instructions.insert(list);

                                changed = true;
                            }
                            break; // annotation loop
                        }
                    }
                }
            }
        }

        return changed;
    }
}
