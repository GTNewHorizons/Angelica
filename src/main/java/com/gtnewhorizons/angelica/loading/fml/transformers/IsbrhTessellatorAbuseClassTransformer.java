package com.gtnewhorizons.angelica.loading.fml.transformers;

import com.gtnewhorizons.angelica.loading.AngelicaClientTweaker;
import com.gtnewhorizons.angelica.loading.shared.AngelicaClassDump;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class IsbrhTessellatorAbuseClassTransformer implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger("IsbrhTessellatorAbuse");
    private static final String ISBRH = "cpw/mods/fml/client/registry/ISimpleBlockRenderingHandler";
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";
    private static final String RENDER_WORLD_BLOCK_DESC = "(Lnet/minecraft/world/IBlockAccess;IIILnet/minecraft/block/Block;ILnet/minecraft/client/renderer/RenderBlocks;)Z";

    private final String draw;
    private final String startDrawingQuads;
    private final String startDrawing;

    public IsbrhTessellatorAbuseClassTransformer() {
        this(AngelicaClientTweaker.isObfEnv());
    }

    public IsbrhTessellatorAbuseClassTransformer(boolean isObf) {
        this.draw = isObf ? "func_78381_a" : "draw";
        this.startDrawingQuads = isObf ? "func_78382_b" : "startDrawingQuads";
        this.startDrawing = isObf ? "func_78371_b" : "startDrawing";
    }

    public boolean shouldTransform(byte[] classBytes) {
        final ClassReader cr = new ClassReader(classBytes);
        for (String iface : cr.getInterfaces()) {
            if (ISBRH.equals(iface)) return true;
        }
        return false;
    }

    public boolean transformClassNode(String transformedName, ClassNode cn) {
        boolean changed = false;
        for (MethodNode mn : cn.methods) {
            if (!"renderWorldBlock".equals(mn.name) || !RENDER_WORLD_BLOCK_DESC.equals(mn.desc)) continue;

            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode min) || min.getOpcode() != Opcodes.INVOKEVIRTUAL || !TESSELLATOR.equals(min.owner)) continue;

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
                    continue;
                }

                mn.instructions.insertBefore(insn, new InsnNode(popOp));
                if (pushIntResult) mn.instructions.insertBefore(insn, new InsnNode(Opcodes.ICONST_0));
                mn.instructions.remove(insn);
                LOGGER.info("Stripped Tessellator.{} from {}#renderWorldBlock", min.name, cn.name);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !shouldTransform(basicClass)) return basicClass;

        final ClassReader cr = new ClassReader(basicClass);
        final ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        if (!transformClassNode(transformedName, cn)) return basicClass;

        final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        final byte[] bytes = cw.toByteArray();
        AngelicaClassDump.dumpClass(transformedName, basicClass, bytes, this);
        return bytes;
    }
}
