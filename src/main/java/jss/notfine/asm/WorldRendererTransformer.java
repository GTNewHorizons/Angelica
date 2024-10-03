package jss.notfine.asm;

import static jss.notfine.asm.ASMUtils.matchesNodeSequence;

import com.gtnewhorizon.gtnhlib.asm.ASMUtil;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import jss.notfine.asm.mappings.Names;

public class WorldRendererTransformer implements IClassTransformer {

    public WorldRendererTransformer() {}

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraft.client.renderer.WorldRenderer".equals(transformedName)) {
            final byte[] bytes = patchWorldRenderer(basicClass);
            if (AngelicaTweaker.DUMP_CLASSES()) {
                ASMUtil.saveAsRawClassFile(basicClass, transformedName + "_PRE", this);
                ASMUtil.saveAsRawClassFile(bytes, transformedName + "_POST", this);
            }
            return bytes;
        }
        return basicClass;
    }

    /**
     * Replaces
     *
     * <pre>
     * {@code
     * if (k3 > k2)
     * {
     * flag = true;
     * }
     * }
     * </pre>
     *
     * with
     *
     * <pre>
     * {@code
     * flag = RenderPass.checkRenderPasses(block, flag);
     * }
     * </pre>
     */
    private static byte[] patchWorldRenderer(byte[] basicClass) {
        final Logger logger = LogManager.getLogger("MCPatcherForge");
        final ClassReader classReader = new ClassReader(basicClass);
        final ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        boolean beginInjected = false;
        boolean updateRendererHandled = false;

        // search sequences

        AbstractInsnNode[] sequence = getSearchSequence();

        // as the replaced bytecode is smaller than the original code we have to shift the line numbers for anything
        // after our changes
        int lineNumberShifter = 0;

        for (MethodNode methodNode : classNode.methods) {
            if (isUpdateRenderer(methodNode)) {
                logger.debug("found updateRenderer");
                for (AbstractInsnNode node : methodNode.instructions.toArray()) {

                    /*
                     * I am sorry for the person having to read this
                     * I did this by comparing diffs between generated asm of the changes needed and I don't know what
                     * each individual change does
                     */
                    if (matchesNodeSequence(node, sequence)) {
                        AbstractInsnNode trackingNode = node.getNext()
                            .getNext()
                            .getNext()
                            .getNext()
                            .getNext();

                        if (trackingNode instanceof LineNumberNode lineNumberNode) {
                            lineNumberShifter = lineNumberNode.line;
                        }

                        if (trackingNode.getNext() instanceof VarInsnNode) {
                            methodNode.instructions.remove(trackingNode.getNext());
                            methodNode.instructions.insert(trackingNode, new VarInsnNode(Opcodes.ALOAD, 24));
                        }

                        trackingNode = trackingNode.getNext()
                            .getNext();

                        if (trackingNode instanceof VarInsnNode aLoadNode) {
                            aLoadNode.var = 18;
                        }

                        InsnList list1 = new InsnList();
                        list1.add(
                            new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/prupe/mcpatcher/renderpass/RenderPass",
                                "checkRenderPasses",
                                "(" + Names.block_.desc + "Z)Z",
                                false));
                        list1.add(new VarInsnNode(Opcodes.ISTORE, 18));
                        methodNode.instructions.insert(trackingNode, list1);
                        // move cursor to last added iStore
                        trackingNode = trackingNode.getNext()
                            .getNext();
                        // remove unneeded assignment
                        methodNode.instructions.remove(
                            trackingNode.getNext()
                                .getNext()
                                .getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.remove(
                            trackingNode.getNext()
                                .getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.remove(
                            trackingNode.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.remove(
                            trackingNode.getNext()
                                .getNext());
                        methodNode.instructions.remove(trackingNode.getNext());

                        trackingNode = trackingNode.getNext()
                            .getNext();

                        methodNode.instructions.remove(trackingNode.getNext());

                        trackingNode = trackingNode.getNext()
                            .getNext()
                            .getNext()
                            .getNext()
                            .getNext()
                            .getNext()
                            .getNext();

                        methodNode.instructions.remove(trackingNode.getNext());
                        methodNode.instructions.insert(
                            trackingNode,
                            new FrameNode(Opcodes.F_APPEND, 1, new Object[] { Opcodes.INTEGER }, 0, null));

                        beginInjected = true;
                        continue;
                    }

                    // shift lineNumber for instructions that are beyond the changes, leave identical otherwise
                    if (beginInjected && node instanceof LineNumberNode lineNumberNode1
                        && lineNumberNode1.line > lineNumberShifter) {
                        lineNumberNode1.line -= 3;
                    }
                }
                updateRendererHandled = true;
                continue;
            }

            // shift line numbers for functions after updateRenderer
            if (updateRendererHandled) {
                for (AbstractInsnNode node : methodNode.instructions.toArray()) {
                    if (node instanceof LineNumberNode lineNumberNode) {
                        lineNumberNode.line -= 3;
                    }
                }
            }

        }
        final ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    private static AbstractInsnNode[] getSearchSequence() {
        FrameNode searchNode1 = new FrameNode(Opcodes.F_SAME, 0, null, 0, null);
        VarInsnNode searchNode2 = new VarInsnNode(Opcodes.ALOAD, 24);
        MethodInsnNode searchNode3 = new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            Names.block_getRenderBlockPass.clas,
            Names.block_getRenderBlockPass.name,
            Names.block_getRenderBlockPass.desc,
            false);
        VarInsnNode searchNode4 = new VarInsnNode(Opcodes.ISTORE, 25);

        return new AbstractInsnNode[] { searchNode1, searchNode2, searchNode3, searchNode4 };
    }

    private static boolean isUpdateRenderer(MethodNode methodNode) {
        return Names.worldRenderer_updateRenderer.equalsNameDesc(methodNode.name, methodNode.desc);
    }
}
