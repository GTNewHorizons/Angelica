package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.GETSTATIC;


public class RenderItemTransformer implements IClassTransformer {
    private static final String classToTransform = "net.minecraft.client.renderer.entity.RenderItem";
    @Override
    public byte[] transform(String name, String transformedName, byte[] classBeingTransformed)
    {
        boolean isObfuscated = name.equals(transformedName);
        return transformedName.equals(classToTransform) ? transform(classBeingTransformed, isObfuscated) : classBeingTransformed;
    }

    private static byte[] transform(byte[] classBeingTranformed, boolean isObfuscated)
    {
        System.out.println("Transforming: " + classToTransform);
        try
        {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new ClassReader(classBeingTranformed);
            classReader.accept(classNode, 0);

            transformRenderItem(classNode, isObfuscated);

            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            classNode.accept(classWriter);
            return classWriter.toByteArray();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return classBeingTranformed;
    }

    private static void transformRenderItem(ClassNode renderItemClass, boolean isObfuscated)
    {
        final String RENDER_ITEM_INTO_GUI = "renderItemIntoGUI";
        final String RENDER_ITEM_INTO_GUI_DESC = "(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/item/ItemStack;IIZ)V";

        for (MethodNode method : renderItemClass.methods)
        {
            if (method.name.equals(RENDER_ITEM_INTO_GUI) && method.desc.equals(RENDER_ITEM_INTO_GUI_DESC))
            {
                AbstractInsnNode targetNode = null;
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (instruction.getOpcode() == GETSTATIC)
                    {
                        if (((FieldInsnNode) instruction).desc.equals("Lnet/minecraft/client/renderer/Tessellator;"))
                        {
                            targetNode = instruction;
                            break;
                        }
                    }
                }
                // This essnetially comments out the all tesselator calls completely in renderItemIntoGUI found in the
                // render item class. More lines need to be removed in this transform like redundant GLState
                // updating if these lines are commented out. This is just the inital testing to see if this
                // change is smart or stupid. I really don't know, but the tesselator calls seem
                // completely redundant.
                if (targetNode != null)
                {
                    for (int i = 0; i < 76; i++)
                    {
                        targetNode = targetNode.getNext();
                        method.instructions.remove(targetNode.getPrevious());
                    }
                }
                else
                {
                    System.out.println("Could not find the tesselator to overwrite it");
                }
            }
        }
    }
}
