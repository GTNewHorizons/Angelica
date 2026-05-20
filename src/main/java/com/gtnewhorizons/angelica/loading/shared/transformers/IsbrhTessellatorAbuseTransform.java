package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class IsbrhTessellatorAbuseTransform {

    public static final String ISBRH = "cpw/mods/fml/client/registry/ISimpleBlockRenderingHandler";

    private static final Logger LOGGER = LogManager.getLogger("IsbrhTessellatorAbuse");
    private static final ClassConstantPoolParser CST_POOL_PARSER = new ClassConstantPoolParser(ISBRH);
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";
    private static final String RENDER_WORLD_BLOCK_DESC = "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z";

    public boolean shouldTransform(byte[] classBytes) {
        return CST_POOL_PARSER.find(classBytes);
    }

    public boolean transformClassNode(ClassNode cn, boolean isObf) {
        MethodNode target = null;
        for (int i = 0, n = cn.methods.size(); i < n; i++) {
            final MethodNode mn = cn.methods.get(i);
            if ("renderWorldBlock".equals(mn.name) && RENDER_WORLD_BLOCK_DESC.equals(mn.desc)) {
                target = mn;
                break;
            }
        }
        if (target == null) return false;

        final String draw = isObf ? "func_78381_a" : "draw";
        final String startDrawingQuads = isObf ? "func_78382_b" : "startDrawingQuads";
        final String startDrawing = isObf ? "func_78371_b" : "startDrawing";

        boolean changed = false;
        AbstractInsnNode insn = target.instructions.getFirst();
        while (insn != null) {
            final AbstractInsnNode next = insn.getNext();
            if (insn instanceof MethodInsnNode min && min.getOpcode() == Opcodes.INVOKEVIRTUAL && TESSELLATOR.equals(min.owner)) {

                final int popOp;
                final boolean pushIntResult;
                if (min.name.equals(draw) && "()I".equals(min.desc)) {
                    popOp = Opcodes.POP;
                    pushIntResult = true;
                } else if (min.name.equals(startDrawingQuads) && "()V".equals(min.desc)) {
                    popOp = Opcodes.POP;
                    pushIntResult = false;
                } else if (min.name.equals(startDrawing) && "(I)V".equals(min.desc)) {
                    popOp = Opcodes.POP2;
                    pushIntResult = false;
                } else {
                    insn = next;
                    continue;
                }

                target.instructions.insertBefore(insn, new InsnNode(popOp));
                if (pushIntResult) target.instructions.insertBefore(insn, new InsnNode(Opcodes.ICONST_0));
                target.instructions.remove(insn);
                LOGGER.info("Stripped Tessellator.{} from {}#renderWorldBlock", min.name, cn.name);
                changed = true;
            }
            insn = next;
        }
        return changed;
    }
}
