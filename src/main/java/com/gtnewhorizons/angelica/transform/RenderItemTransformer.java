package com.gtnewhorizons.angelica.transform;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
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
            System.out.println("FATAL ERROR in RenderItemTransformer");
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
                boolean tesselatorFound = false;
                boolean glDisableFound = false;
                boolean glColorMaskFound = false;
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (instruction.getOpcode() == GETSTATIC) {
                        if (!tesselatorFound && ((FieldInsnNode) instruction).desc.equals("Lnet/minecraft/client/renderer/Tessellator;")) {
                            targetNode = instruction;
                            removeTesselatorAndCleanupCallsBlock(targetNode, method);
                            tesselatorFound = true;
                            break;
                        }
                    }
                    if (instruction.getOpcode() == INVOKESTATIC)
                    {
                        if (!glDisableFound && ((MethodInsnNode) instruction).name.equals("glDisable"))
                        {
                            if (instruction.getPrevious() instanceof IntInsnNode && ((IntInsnNode) instruction.getPrevious()).operand == 3553)
                            {
                                targetNode = instruction;
                                removeGLDisable(targetNode, method);
                                glDisableFound = true;
                                continue;
                            }
                        }
                        if (!glColorMaskFound && ((MethodInsnNode) instruction).name.equals("glColorMask"))
                        {
                            targetNode = instruction;
                            removeColorMask(targetNode, method);
                            glColorMaskFound = true;
                        }
                    }
                }
            }
        }
    }

    // This essnetially comments out the all tesselator calls completely in renderItemIntoGUI found in the
    // drawScreen item class. More lines need to be removed in this transform like redundant GLState
    // updating if these lines are commented out. This is just the inital testing to see if this
    // change is smart or stupid. I really don't know, but the tesselator calls seem
    // completely redundant.

    // All of this code is removing items from this block in drawScreen item:

    //    else if (p_77015_3_.getItem().requiresMultipleRenderPasses())
    //    {
    //        GL11.glDisable(GL11.GL_LIGHTING);
    //        GL11.glEnable(GL11.GL_ALPHA_TEST);
    //        p_77015_2_.bindTexture(TextureMap.locationItemsTexture);
    //        GL11.glDisable(GL11.GL_ALPHA_TEST);
    //        GL11.glDisable(GL11.GL_TEXTURE_2D);
    //        GL11.glEnable(GL11.GL_BLEND);
    //        OpenGlHelper.glBlendFunc(0, 0, 0, 0);
    //        GL11.glColorMask(false, false, false, true);
    //        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    //        Tessellator tessellator = Tessellator.instance;
    //        tessellator.startDrawingQuads();
    //        tessellator.setColorOpaque_I(-1);
    //        tessellator.addVertex((double)(p_77015_4_ - 2), (double)(p_77015_5_ + 18), (double)this.zLevel);
    //        tessellator.addVertex((double)(p_77015_4_ + 18), (double)(p_77015_5_ + 18), (double)this.zLevel);
    //        tessellator.addVertex((double)(p_77015_4_ + 18), (double)(p_77015_5_ - 2), (double)this.zLevel);
    //        tessellator.addVertex((double)(p_77015_4_ - 2), (double)(p_77015_5_ - 2), (double)this.zLevel);
    //        tessellator.draw();
    //        GL11.glColorMask(true, true, true, true);
    //        GL11.glEnable(GL11.GL_TEXTURE_2D);
    //        GL11.glEnable(GL11.GL_ALPHA_TEST);
    //
    //        Item item = p_77015_3_.getItem();
    //        for (l = 0; l < item.getRenderPasses(k); ++l)

    // It is changed to the following:

    //    else if (p_77015_3_.getItem().requiresMultipleRenderPasses())
    //    {
    //        GL11.glDisable(GL11.GL_LIGHTING);
    //        GL11.glEnable(GL11.GL_ALPHA_TEST);
    //        p_77015_2_.bindTexture(TextureMap.locationItemsTexture);
    //        GL11.glEnable(GL11.GL_BLEND);
    //        OpenGlHelper.glBlendFunc(0, 0, 0, 0);
    //        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    //
    //        Item item = p_77015_3_.getItem();
    //        for (l = 0; l < item.getRenderPasses(k); ++l)

    private static void removeTesselatorAndCleanupCallsBlock(AbstractInsnNode targetNode, MethodNode method)
    {
        try
        {
            if (targetNode != null)
            {
                method.instructions.remove(targetNode.getPrevious());
                method.instructions.remove(targetNode.getPrevious());
                while(!(targetNode.getNext().getNext().getNext().getOpcode() == INVOKEVIRTUAL && ((MethodInsnNode) targetNode.getNext().getNext().getNext()).name.equals("getItem")))
                {
                    targetNode = targetNode.getNext();
                    method.instructions.remove(targetNode.getPrevious());
                }
            }
            else
            {
                throw new Exception();
            }
        }
        catch(Exception e)
        {
            System.out.println("Could not find the tesselator to remove");
            e.printStackTrace();
        }
    }

    private static void removeGLDisable(AbstractInsnNode targetNode, MethodNode method)
    {
        try
        {
            if (targetNode != null)
            {
                while (!(targetNode.getOpcode() == INVOKEVIRTUAL && ((MethodInsnNode) targetNode).name.equals("bindTexture")))
                {
                    targetNode = targetNode.getPrevious();
                    method.instructions.remove(targetNode.getNext());
                }
            }
            else
            {
                throw new Exception();
            }
        }
        catch(Exception e)
        {
            System.out.println("Could not find the correct GLDisable functions to remove");
            e.printStackTrace();
        }

    }

    private static void removeColorMask(AbstractInsnNode targetNode, MethodNode method)
    {
        try
        {
            if (targetNode != null)
            {
                while (!(targetNode.getOpcode() == INVOKESTATIC && ((MethodInsnNode) targetNode).name.equals("glBlendFunc")))
                {
                    targetNode = targetNode.getPrevious();
                    method.instructions.remove(targetNode.getNext());
                }
            }
            else
            {
                throw new Exception();
            }
        }
        catch(Exception e)
        {
            System.out.println("Could not find the correct colorMask function to remove");
            e.printStackTrace();
        }
    }
}
