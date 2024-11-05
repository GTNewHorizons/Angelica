package com.gtnewhorizons.angelica.transform.compat.transformers.generic;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import com.gtnewhorizons.angelica.transform.AsmUtil;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.TypeInsnNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;

import java.util.List;

public class TileEntityNullGuardTransformer {

    public static void transform(ClassNode cn, List<String> patchMethods) {
        for (MethodNode method : cn.methods) {
            if (patchMethods.contains(method.name)) {
                injectGetTileEntityNullGuard(method);
            }
        }
    }

    public static void injectGetTileEntityNullGuard(MethodNode mn) {
        /*
         * Searching for the following pattern:
         * L0 {
         * ...
         * INVOKEINTERFACE net/minecraft/world/IBlockAccess.getTileEntity(III)Lnet/minecraft/tileentity/TileEntity;
         * CHECKCAST *
         * ASTORE x
         * }
         * L1 {...
         * Then injecting the following after the ASTORE such that we get
         * L0 {
         * ASTORE x
         * ALOAD x
         * IFNONNULL L1
         * ICONST_0
         * IRETURN
         * }
         * L1 {...
         * Ultimately this converts:
         * MyTileEntity tile = (MyTileEntity) world.getTileEntity(x, y, z);
         * doSomethingWithTileEntity(tile);
         * into:
         * MyTileEntity tile = (MyTileEntity) world.getTileEntity(x, y, z);
         * if (tile == null) {
         * return false;
         * } else {
         * doSomethingWithTileEntity(tile);
         * }
         */
        for (int i = 0; i < mn.instructions.size(); i++) {
            AbstractInsnNode in = mn.instructions.get(i);
            if (in instanceof MethodInsnNode min) {
                if (min.getOpcode() == Opcodes.INVOKEINTERFACE
                    && min.name.equals(AsmUtil.obf("getTileEntity", "func_147438_o"))
                    && min.owner.equals("net/minecraft/world/IBlockAccess")) {
                    AbstractInsnNode castNodeAbstract = min.getNext();
                    if (castNodeAbstract instanceof TypeInsnNode castNodeType) {
                        if (castNodeType.getOpcode() == Opcodes.CHECKCAST) {
                            AbstractInsnNode astoreNodeAbstract = castNodeType.getNext();
                            if (astoreNodeAbstract instanceof VarInsnNode astoreNodeVar) {
                                if (astoreNodeVar.getOpcode() == Opcodes.ASTORE) {
                                    InsnList list = new InsnList();
                                    LabelNode exit = new LabelNode();
                                    list.add(new VarInsnNode(Opcodes.ALOAD, astoreNodeVar.var));
                                    list.add(new JumpInsnNode(Opcodes.IFNONNULL, exit));
                                    if (mn.desc.endsWith("Z") || mn.desc.endsWith("I")) {
                                        list.add(new InsnNode(Opcodes.ICONST_0));
                                        list.add(new InsnNode(Opcodes.IRETURN));
                                    } else if (mn.desc.endsWith("V")) {
                                        list.add(new InsnNode(Opcodes.RETURN));
                                    } else {
                                        AngelicaTweaker.LOGGER.warn("TileEntityNullGuard - Unknown Return Type: {}:{}", mn.name, mn.desc);
                                        return;
                                    }
                                    list.add(exit);
                                    mn.instructions.insert(astoreNodeVar, list);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
