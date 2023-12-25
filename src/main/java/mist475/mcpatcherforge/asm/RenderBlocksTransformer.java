package mist475.mcpatcherforge.asm;

import static mist475.mcpatcherforge.asm.ASMUtils.matchesNodeSequence;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import mist475.mcpatcherforge.asm.mappings.Names;

public class RenderBlocksTransformer implements IClassTransformer {

    public RenderBlocksTransformer() {}

    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if ("net.minecraft.client.renderer.RenderBlocks".equals(transformedName)) {
            return patchRenderBlocks(basicClass);
        }

        return basicClass;
    }

    private static byte[] patchRenderBlocks(byte[] basicClass) {
        final Logger logger = LogManager.getLogger("MCPatcherForge");
        final ClassReader classReader = new ClassReader(basicClass);
        final ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);

        // counters

        int ifStartsHandled = 0;
        int ifEndsHandled = 0;
        boolean secondWrappedIfHandled = false;

        int ifStartsHandledPartial = 0;
        int ifEndsHandledPartial = 0;
        boolean secondWrappedIfHandledPartial = false;

        // search sequences

        AbstractInsnNode ifSequence1 = new VarInsnNode(Opcodes.ILOAD, 13);
        AbstractInsnNode[] ifSequence2 = getStartIfSequence2();
        AbstractInsnNode[] endIfSequence = getEndIfSequence();

        // code to inject
        Pair<InsnList, InsnList> ifWrapper1 = getRenderBlocksIfWrapper(0, 4601, 4626, false);
        Pair<InsnList, InsnList> ifWrapper2 = getRenderBlocksIfWrapper(1, 4715, 4730, true);
        Pair<InsnList, InsnList> ifWrapper3 = getRenderBlocksIfWrapper(2, 4822, 4847, false);
        Pair<InsnList, InsnList> ifWrapper4 = getRenderBlocksIfWrapper(3, 4956, 4981, false);
        Pair<InsnList, InsnList> ifWrapper5 = getRenderBlocksIfWrapper(4, 5090, 5115, false);
        Pair<InsnList, InsnList> ifWrapper6 = getRenderBlocksIfWrapper(5, 5224, 5249, false);

        Pair<InsnList, InsnList> ifWrapper7 = getRenderBlocksIfWrapper(0, 5405, 5419, false);
        Pair<InsnList, InsnList> ifWrapper8 = getRenderBlocksIfWrapper(1, 5519, 5523, true);
        Pair<InsnList, InsnList> ifWrapper9 = getRenderBlocksIfWrapper(2, 5509, 5523, false);
        Pair<InsnList, InsnList> ifWrapper10 = getRenderBlocksIfWrapper(3, 5642, 5655, false);
        Pair<InsnList, InsnList> ifWrapper11 = getRenderBlocksIfWrapper(4, 5784, 5798, false);
        Pair<InsnList, InsnList> ifWrapper12 = getRenderBlocksIfWrapper(5, 5926, 5940, false);

        for (MethodNode methodNode : classNode.methods) {
            if (isRenderStandardBlockWithAmbientOcclusion(methodNode)) {
                logger.debug("found renderStandardBlockWithAmbientOcclusion");
                for (AbstractInsnNode node : methodNode.instructions.toArray()) {

                    // start if-statements

                    if (ifStartsHandled == 0 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper1.getLeft());
                        ifStartsHandled++;
                        continue;
                    }

                    if (!secondWrappedIfHandled && matchesNodeSequence(node, ifSequence2)) {
                        methodNode.instructions.insertBefore(node, ifWrapper2.getLeft());
                        secondWrappedIfHandled = true;
                        continue;
                    }

                    if (ifStartsHandled == 1 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper3.getLeft());
                        ifStartsHandled++;
                        continue;
                    }

                    if (ifStartsHandled == 2 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper4.getLeft());
                        ifStartsHandled++;
                        continue;
                    }

                    if (ifStartsHandled == 3 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper5.getLeft());
                        ifStartsHandled++;
                        continue;
                    }

                    if (ifStartsHandled == 4 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper6.getLeft());
                        ifStartsHandled++;
                        continue;
                    }

                    // end if-statements

                    if (ifEndsHandled == 0 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper1.getRight());
                        ifEndsHandled++;
                        continue;
                    }

                    if (ifEndsHandled == 1 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper2.getRight());
                        ifEndsHandled++;
                        continue;
                    }

                    if (ifEndsHandled == 2 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper3.getRight());
                        ifEndsHandled++;
                        continue;
                    }

                    if (ifEndsHandled == 3 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper4.getRight());
                        ifEndsHandled++;
                        continue;
                    }

                    if (ifEndsHandled == 4 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper5.getRight());
                        ifEndsHandled++;
                        continue;
                    }

                    if (ifEndsHandled == 5 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper6.getRight());
                        ifEndsHandled++;
                        break;
                    }
                }
            }

            else if (isRenderStandardBlockWithAmbientOcclusionPartial(methodNode)) {
                logger.debug("found renderStandardBlockWithAmbientOcclusionPartial");
                for (AbstractInsnNode node : methodNode.instructions.toArray()) {

                    // start if-statements

                    if (ifStartsHandledPartial == 0 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper7.getLeft());
                        ifStartsHandledPartial++;
                        continue;
                    }

                    if (!secondWrappedIfHandledPartial && matchesNodeSequence(node, ifSequence2)) {
                        methodNode.instructions.insertBefore(node, ifWrapper8.getLeft());
                        secondWrappedIfHandledPartial = true;
                        continue;
                    }

                    if (ifStartsHandledPartial == 1 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper9.getLeft());
                        ifStartsHandledPartial++;
                        continue;
                    }

                    if (ifStartsHandledPartial == 2 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper10.getLeft());
                        ifStartsHandledPartial++;
                        continue;
                    }

                    if (ifStartsHandledPartial == 3 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper11.getLeft());
                        ifStartsHandledPartial++;
                        continue;
                    }

                    if (ifStartsHandledPartial == 4 && matchesNodeSequence(node, ifSequence1)) {
                        methodNode.instructions.insertBefore(node, ifWrapper12.getLeft());
                        ifStartsHandledPartial++;
                        continue;
                    }

                    // end if-statements

                    if (ifEndsHandledPartial == 0 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper7.getRight());
                        ifEndsHandledPartial++;
                        continue;
                    }

                    if (ifEndsHandledPartial == 1 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper8.getRight());
                        ifEndsHandledPartial++;
                        continue;
                    }

                    if (ifEndsHandledPartial == 2 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper9.getRight());
                        ifEndsHandledPartial++;
                        continue;
                    }

                    if (ifEndsHandledPartial == 3 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper10.getRight());
                        ifEndsHandledPartial++;
                        continue;
                    }

                    if (ifEndsHandledPartial == 4 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper11.getRight());
                        ifEndsHandledPartial++;
                        continue;
                    }

                    if (ifEndsHandledPartial == 5 && matchesNodeSequence(node, endIfSequence)) {
                        methodNode.instructions.remove(
                            node.getNext()
                                .getNext()
                                .getNext());
                        methodNode.instructions.insert(
                            node.getNext()
                                .getNext(),
                            ifWrapper12.getRight());
                        ifEndsHandledPartial++;
                        break;
                    }
                }
            }

        }
        final ClassWriter classWriter = new ClassWriter(0);
        classNode.accept(classWriter);
        return classWriter.toByteArray();
    }

    // faces 1, 3-6 only differ on a single node
    private static Pair<InsnList, InsnList> getRenderBlocksIfWrapper(int face, int lineNumber1, int lineNumber2,
        boolean second) {
        int iConst = switch (face) {
            case 0 -> Opcodes.ICONST_0;
            case 1 -> Opcodes.ICONST_1;
            case 2 -> Opcodes.ICONST_2;
            case 3 -> Opcodes.ICONST_3;
            case 4 -> Opcodes.ICONST_4;
            case 5 -> Opcodes.ICONST_5;
            default -> 0;
        };

        final InsnList ifStart = new InsnList();
        ifStart.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ifStart.add(new VarInsnNode(Opcodes.ALOAD, 1));
        ifStart.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ifStart.add(
            new FieldInsnNode(
                Opcodes.GETFIELD,
                Names.renderBlocks_blockAccess.clas,
                Names.renderBlocks_blockAccess.name,
                Names.renderBlocks_blockAccess.desc));
        ifStart.add(new VarInsnNode(Opcodes.ILOAD, 2));
        ifStart.add(new VarInsnNode(Opcodes.ILOAD, 3));
        ifStart.add(new VarInsnNode(Opcodes.ILOAD, 4));
        ifStart.add(new InsnNode(iConst));
        ifStart.add(new VarInsnNode(Opcodes.FLOAD, 9));
        ifStart.add(new VarInsnNode(Opcodes.FLOAD, 10));
        ifStart.add(new VarInsnNode(Opcodes.FLOAD, 11));
        ifStart.add(new VarInsnNode(Opcodes.FLOAD, 12));
        ifStart.add(
            new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/prupe/mcpatcher/cc/ColorizeBlock",
                "setupBlockSmoothing",
                "(" + Names.renderBlocks_.desc + Names.block_.desc + Names.iBlockAccess_.desc + "IIIIFFFF)Z",
                false));
        Label label1 = new Label();
        ifStart.add(new JumpInsnNode(Opcodes.IFNE, new LabelNode(label1)));
        Label label2 = new Label();
        ifStart.add(new LabelNode(label2));
        ifStart.add(new LineNumberNode(lineNumber1, new LabelNode(label2)));

        final InsnList ifEnd = new InsnList();
        ifEnd.add(new LabelNode(label1));
        ifEnd.add(new LineNumberNode(lineNumber2, new LabelNode(label1)));
        if (second) {
            ifEnd.add(new FrameNode(Opcodes.F_APPEND, 1, new Object[] { Opcodes.FLOAT }, 0, null));
        } else {
            ifEnd.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        }

        return Pair.of(ifStart, ifEnd);
    }

    private static AbstractInsnNode[] getStartIfSequence2() {
        VarInsnNode searchNode1 = new VarInsnNode(Opcodes.ALOAD, 0);
        VarInsnNode searchNode2 = new VarInsnNode(Opcodes.ALOAD, 0);
        VarInsnNode searchNode3 = new VarInsnNode(Opcodes.ALOAD, 0);
        VarInsnNode searchNode4 = new VarInsnNode(Opcodes.ALOAD, 0);
        VarInsnNode searchNode5 = new VarInsnNode(Opcodes.FLOAD, 5);
        InsnNode searchNode6 = new InsnNode(Opcodes.DUP_X1);

        return new AbstractInsnNode[] { searchNode1, searchNode2, searchNode3, searchNode4, searchNode5, searchNode6 };
    }

    private static AbstractInsnNode[] getEndIfSequence() {
        VarInsnNode searchNode1 = new VarInsnNode(Opcodes.FLOAD, 12);
        InsnNode searchNode2 = new InsnNode(Opcodes.FMUL);
        FieldInsnNode searchNode3 = new FieldInsnNode(
            Opcodes.PUTFIELD,
            Names.renderBlocks_colorBlueTopRight.clas,
            Names.renderBlocks_colorBlueTopRight.name,
            Names.renderBlocks_colorBlueTopRight.desc);
        return new AbstractInsnNode[] { searchNode1, searchNode2, searchNode3 };
    }

    private static boolean isRenderStandardBlockWithAmbientOcclusion(MethodNode methodNode) {
        return Names.renderBlocks_renderStandardBlockWithAmbientOcclusion
            .equalsNameDesc(methodNode.name, methodNode.desc);
    }

    private static boolean isRenderStandardBlockWithAmbientOcclusionPartial(MethodNode methodNode) {
        return Names.renderBlocks_renderStandardBlockWithAmbientOcclusionPartial
            .equalsNameDesc(methodNode.name, methodNode.desc);
    }
}
