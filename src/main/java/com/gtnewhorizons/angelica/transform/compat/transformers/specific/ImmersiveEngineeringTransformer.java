package com.gtnewhorizons.angelica.transform.compat.transformers.specific;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FieldInsnNode;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.transformers.MixinClassWriter;

import java.util.ArrayList;
import java.util.List;

public class ImmersiveEngineeringTransformer implements IClassTransformer {

    private static final String BlockRenderClothDevices = "blusunrize.immersiveengineering.client.render.BlockRenderClothDevices";
    private static final String BlockRenderMetalDevices2 = "blusunrize.immersiveengineering.client.render.BlockRenderMetalDevices2";
    private static final String BlockRenderStoneDevices = "blusunrize.immersiveengineering.client.render.BlockRenderStoneDevices";
    private static final String ClientUtils = "blusunrize.immersiveengineering.client.ClientUtils";

    private static final List<String> transformedClasses = new ArrayList<>();

    private static final List<String> staticRenderPassPatches = new ArrayList<>();

    static {
        transformedClasses.add(BlockRenderClothDevices);
        transformedClasses.add(BlockRenderMetalDevices2);
        transformedClasses.add(BlockRenderStoneDevices);
        transformedClasses.add(ClientUtils);

        staticRenderPassPatches.add(BlockRenderClothDevices);
        staticRenderPassPatches.add(BlockRenderMetalDevices2);
        staticRenderPassPatches.add(BlockRenderStoneDevices);
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        if (!transformedClasses.contains(transformedName)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        if (transformedName.equals(ClientUtils)) {
            transformClientUtils(cn);
        }

        if (staticRenderPassPatches.contains(transformedName)) {
            staticRenderPassPatcher(cn);
        }

        MixinClassWriter cw = new MixinClassWriter(MixinClassWriter.COMPUTE_FRAMES);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaTweaker.LOGGER.info("[AngelicaCompat]Extra Transformers: Applied ImmersiveEngineeringTransformer");
        AngelicaTweaker.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }

    private void staticRenderPassPatcher(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("renderWorldBlock")) {
                for (int i = 0; i < mn.instructions.size(); i++) {
                    AbstractInsnNode ain = mn.instructions.get(i);
                    // So far every one of these has been named renderPass, eventually might need to make the name dynamic and possibly care about owner?
                    // Maybe this is common in other mods, possibly could generalize this transformation further if it comes up more.
                    if (ain instanceof FieldInsnNode fin && fin.getOpcode() == Opcodes.GETSTATIC && fin.name.equals("renderPass")) {
                        MethodInsnNode getRenderPass = new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraftforge/client/ForgeHooksClient", "getWorldRenderPass", "()I");
                        mn.instructions.insert(fin, getRenderPass);
                        mn.instructions.remove(fin);
                    }
                }
            }
        }
    }

    private void transformClientUtils(ClassNode cn) {
        for (MethodNode mn : cn.methods) {
            // This patches an if (world != null) check to just be if (false) in order to make the block of code not run
            // The block of code in question calls OpenGL to update the lightmap texture coordinates, which is illegal from an ISBRH
            // in Angelica. It doesn't seem to make anything not work by causing this to not happen.
            if (mn.name.equals("renderStaticWavefrontModelWithIcon") || mn.name.equals("renderStaticWavefrontModel")) {
                if (
                    mn.desc.equals("(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/client/model/obj/WavefrontObject;Lnet/minecraft/util/IIcon;Lnet/minecraft/client/renderer/Tessellator;Lblusunrize/immersiveengineering/common/util/chickenbones/Matrix4;Lblusunrize/immersiveengineering/common/util/chickenbones/Matrix4;IZFFF[Ljava/lang/String;)V")
                    || mn.desc.equals("(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraftforge/client/model/obj/WavefrontObject;Lnet/minecraft/client/renderer/Tessellator;Lblusunrize/immersiveengineering/common/util/chickenbones/Matrix4;Lblusunrize/immersiveengineering/common/util/chickenbones/Matrix4;IZFFF[Ljava/lang/String;)V")
                ) {
                    for (int i = 0; i < mn.instructions.size(); i++) {
                        AbstractInsnNode ain = mn.instructions.get(i);
                        if (ain instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFNULL) {
                            InsnNode iconst = new InsnNode(Opcodes.ICONST_0);
                            jump.setOpcode(Opcodes.IFEQ);
                            mn.instructions.insertBefore(jump, iconst);
                            break;
                        }
                    }
                }
            }
        }
    }

}
