package jss.notfine.asm;

import java.util.Objects;

import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Convenience methods for a neater and saner core-modding experience
 */
public class ASMUtils {

    /**
     * Check the basic fields of nodes to see if they're equal
     * DOES NOT CHECK ALL FIELDS, CHECK BEFORE USING
     */
    public static boolean abstractInsnNodeEquals(AbstractInsnNode node1, AbstractInsnNode node2) {

        if (node1.getType() != node2.getType()) {
            return false;
        }

        // early return for efficiency
        if (node1.getOpcode() != node2.getOpcode()) {
            return false;
        }

        // check what type and properties
        // we only check what we need for proper comparison
        // missing some types, as I don't expect I'll need all for now
        return switch (node1.getType()) {
            // only has opcode
            case AbstractInsnNode.INSN -> true;
            case AbstractInsnNode.INT_INSN -> {
                if (node1 instanceof IntInsnNode intInsnNode1 && node2 instanceof IntInsnNode intInsnNode2) {
                    yield intInsnNode1.operand == intInsnNode2.operand;
                }
                yield false;
            }
            case AbstractInsnNode.VAR_INSN -> {
                if (node1 instanceof VarInsnNode varInsnNode1 && node2 instanceof VarInsnNode varInsnNode2) {
                    yield varInsnNode1.var == varInsnNode2.var;
                }
                yield false;
            }
            case AbstractInsnNode.TYPE_INSN -> {
                if (node1 instanceof TypeInsnNode typeInsnNode1 && node2 instanceof TypeInsnNode typeInsnNode2) {
                    yield typeInsnNode1.desc.equals(typeInsnNode2.desc);
                }
                yield false;
            }
            case AbstractInsnNode.FIELD_INSN -> {
                if (node1 instanceof FieldInsnNode fieldInsnNode1 && node2 instanceof FieldInsnNode fieldInsnNode2) {
                    yield fieldInsnNode1.desc.equals(fieldInsnNode2.desc)
                        && fieldInsnNode1.name.equals(fieldInsnNode2.name)
                        && fieldInsnNode1.owner.equals(fieldInsnNode2.owner);
                }
                yield false;
            }
            // doesn't check itf
            case AbstractInsnNode.METHOD_INSN -> {
                if (node1 instanceof MethodInsnNode methodInsnNode1
                    && node2 instanceof MethodInsnNode methodInsnNode2) {
                    yield methodInsnNode1.desc.equals(methodInsnNode2.desc)
                        && methodInsnNode1.name.equals(methodInsnNode2.name)
                        && methodInsnNode1.owner.equals(methodInsnNode2.owner);
                }
                yield false;
            }
            // doesn't check bsm args
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                if (node1 instanceof InvokeDynamicInsnNode invokeDynamicInsnNode1
                    && node2 instanceof InvokeDynamicInsnNode invokeDynamicInsnNode2) {
                    yield invokeDynamicInsnNode1.desc.equals(invokeDynamicInsnNode2.desc)
                        && invokeDynamicInsnNode1.name.equals(invokeDynamicInsnNode2.name)
                        && invokeDynamicInsnNode1.bsm.equals(invokeDynamicInsnNode2.bsm);
                }
                yield false;
            }
            // I expect this will still fail sometimes as Label doesn't override equals
            case AbstractInsnNode.JUMP_INSN -> {
                if (node1 instanceof JumpInsnNode jumpInsnNode1 && node2 instanceof JumpInsnNode jumpInsnNode2) {
                    yield jumpInsnNode1.label.getLabel()
                        .equals(jumpInsnNode2.label.getLabel());
                }
                yield false;
            }
            // Idem ditto
            case AbstractInsnNode.LABEL -> {
                if (node1 instanceof LabelNode labelNode1 && node2 instanceof LabelNode labelNode2) {
                    yield labelNode1.getLabel()
                        .equals(labelNode2.getLabel());
                }
                yield false;
            }
            case AbstractInsnNode.LDC_INSN -> {
                if (node1 instanceof LdcInsnNode ldcInsnNode1 && node2 instanceof LdcInsnNode ldcInsnNode2) {
                    yield ldcInsnNode1.cst.equals(ldcInsnNode2.cst);
                }
                yield false;
            }
            case AbstractInsnNode.IINC_INSN -> {
                if (node1 instanceof IincInsnNode iincInsnNode1 && node2 instanceof IincInsnNode iincInsnNode2) {
                    yield iincInsnNode1.incr == iincInsnNode2.incr && iincInsnNode1.var == iincInsnNode2.var;
                }
                yield false;
            }
            case AbstractInsnNode.FRAME -> {
                if (node1 instanceof FrameNode frameNode1 && node2 instanceof FrameNode frameNode2) {
                    yield frameNode1.type == frameNode2.type && Objects.equals(frameNode1.local, frameNode2.local)
                        && Objects.equals(frameNode1.stack, frameNode2.stack);
                }
                yield false;
            }
            case AbstractInsnNode.LINE -> {
                if (node1 instanceof LineNumberNode lineNumberNode1
                    && node2 instanceof LineNumberNode lineNumberNode2) {
                    yield lineNumberNode1.line == lineNumberNode2.line && lineNumberNode1.start.getLabel()
                        .equals(lineNumberNode2.start.getLabel());
                }
                yield false;
            }
            default -> {
                AngelicaTweaker.LOGGER.warn("Unchecked node found: " + node1.getClass());
                yield node1.toString()
                    .equals(node2.toString());
            }
        };
    }

    public static boolean matchesNodeSequence(AbstractInsnNode node, AbstractInsnNode... pattern) {
        AbstractInsnNode currentNode = node;

        for (AbstractInsnNode abstractInsnNode : pattern) {
            if (!abstractInsnNodeEquals(currentNode, abstractInsnNode)) {
                return false;
            }
            currentNode = currentNode.getNext();
            if (currentNode == null) {
                return false;
            }
        }

        return true;
    }
}
