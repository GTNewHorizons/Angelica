package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Exits;
import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Handler;
import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Load;
import com.llamalad7.mixinextras.utils.ASMUtils;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.FrameNode;
import org.spongepowered.asm.lib.tree.IincInsnNode;
import org.spongepowered.asm.lib.tree.InsnList;
import org.spongepowered.asm.lib.tree.InsnNode;
import org.spongepowered.asm.lib.tree.JumpInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.LocalVariableNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.TryCatchBlockNode;
import org.spongepowered.asm.lib.tree.TypeInsnNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;
import org.spongepowered.asm.lib.tree.analysis.Analyzer;
import org.spongepowered.asm.lib.tree.analysis.AnalyzerException;
import org.spongepowered.asm.lib.tree.analysis.BasicInterpreter;
import org.spongepowered.asm.lib.tree.analysis.BasicValue;
import org.spongepowered.asm.lib.tree.analysis.Frame;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.Bytecode.Visibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SurroundCallSite {

    private static final String REGION = "surround:region";
    private static final Type[] NOTHING = new Type[0];

    record Region(LabelNode start, LabelNode end) {}

    private SurroundCallSite() {
    }

    static Type[] operands(MethodInsnNode call, String targetClass) {
        final Type[] args = Type.getArgumentTypes(call.desc);
        if (call.getOpcode() == Opcodes.INVOKESTATIC) {
            return args;
        }
        final Type[] all = new Type[args.length + 1];
        all[0] = Type.getObjectType(call.getOpcode() == Opcodes.INVOKESPECIAL ? targetClass : call.owner);
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }

    static void apply(ClassNode classNode, CallSiteSpec spec) {
        final InjectionNode node = spec.node();
        if (node == null) {
            final MethodNode target = spec.target();
            final MethodNode body = relocate(classNode, target);
            final MethodInsnNode call = (MethodInsnNode) ASMUtils.getInvokeInstruction(classNode, body);
            target.instructions.add(call);
            target.instructions.add(new InsnNode(spec.result().getOpcode(Opcodes.IRETURN)));
            final Type[] args = operands(call, classNode.name);
            final int[] argSlots = new int[args.length];
            for (int i = 1; i < args.length; i++) {
                argSlots[i] = argSlots[i - 1] + args[i - 1].getSize();
            }
            emit(classNode, spec, target, call, null, Bytecode.isStatic(target) ? 0 : 1, argSlots);
            return;
        }
        final AbstractInsnNode champion = node.getCurrentTarget();
        final Region inner = node.<Region>getDecoration(REGION);
        final MethodNode owner = champion == null ? null : owner(classNode, inner == null ? champion : inner.start());
        if (owner == null) {
            MixinService.getService().getLogger("mixin").warn(spec.description() + " skipped: the surrounded call was removed by another injector");
            return;
        }
        if (!spec.entryDesc().equals(spec.entry().desc)) {
            throw cannotFollow(spec, "the entry handler's descriptor changed from " + spec.entryDesc() + " to " + spec.entry().desc + " after injection", null);
        }
        if (!(champion instanceof MethodInsnNode call)) {
            throw cannotFollow(spec, "the call was replaced by " + Bytecode.describeNode(champion, false) + ", which is not a method call", null);
        }
        final int offset = node.isReplaced() && call.getOpcode() != Opcodes.INVOKESTATIC ? 1 : 0;
        checkOperands(spec, call, operands(call, classNode.name), offset);
        final Exits exits = spec.exits();
        final Type result = spec.result();
        final Type actual = Type.getReturnType(call.desc);
        if ((exits.handles || exits.skipSlot >= 0 || exits.returnHandler != null) && (actual.getSize() != result.getSize() || actual.getSize() > 0 && SurroundBinding.kindOf(actual) != SurroundBinding.kindOf(result))) {
            throw cannotFollow(spec, "the call was replaced by " + Bytecode.describeNode(call, false) + ", which returns " + actual.getClassName() + " where the surround supplies or replaces a " + result.getClassName(), null);
        }
        emit(classNode, spec, owner, call, inner, offset, null);
    }

    private static void emit(ClassNode classNode, CallSiteSpec spec, MethodNode owner, MethodInsnNode call, Region inner, int offset, int[] argSlots) {
        final boolean slots = argSlots != null;
        final Type[] args = operands(call, classNode.name);
        final Exits exits = spec.exits();
        final boolean skips = exits.skipSlot >= 0;
        final Handler returnHandler = exits.returnHandler;
        final Type result = spec.result();
        final Type actual = Type.getReturnType(call.desc);

        final MethodNode entry = spec.entry();
        final Type[] operands = spec.operands();
        final int argBase = Bytecode.isStatic(spec.target()) ? 0 : 1;
        final Type[] entryParams = Type.getArgumentTypes(entry.desc);
        final int entryOperands = entryParams.length - spec.entryLocals().length;
        final int firstNonArg = argBase + Bytecode.getArgsSize(entryParams);
        final MethodNode inlined = inlineable(entry);

        final boolean restoresBelow = exits.handles || !inlined.tryCatchBlocks.isEmpty();
        final boolean relocated = owner != spec.target();
        owner.maxLocals = Math.max(Math.max(owner.maxLocals, spec.target().maxLocals), usedLocals(owner));
        int next = owner.maxLocals;
        final AbstractInsnNode anchor = inner == null ? call : inner.start();
        final Frame<BasicValue> frame = !slots && (restoresBelow || relocated && hasLocals(spec)) ? frameAt(owner, anchor, spec) : null;
        final int shift = relocated ? Bytecode.getFirstNonArgLocalIndex(owner) - Bytecode.getFirstNonArgLocalIndex(spec.target()) : 0;

        final Type[] below = restoresBelow ? below(spec, frame, args.length) : NOTHING;
        final int spillFrom = slots || restoresBelow || skips ? 0 : offset;
        final boolean spill = slots || restoresBelow || skips || args.length > offset && (entryOperands > 0 || prefixed(spec));
        final int[] spillSlots = slots ? argSlots : new int[args.length];
        if (spill && !slots) {
            for (int i = spillFrom; i < args.length; i++) {
                spillSlots[i] = next;
                next += args[i].getSize();
            }
        }
        final int[] belowSlots = new int[below.length];
        for (int i = 0; i < below.length; i++) {
            belowSlots[i] = next;
            next += below[i].getSize();
        }
        final int entryBase = next;
        final int delta = entryBase - firstNonArg;
        final int excSlot = entryBase + Math.max(0, inlined.maxLocals - firstNonArg);
        final int resSlot = excSlot + 1;
        final boolean storesResult = returnHandler != null && result.getSize() > 0;

        final int[] entryLocals = localSlots(spec, owner, anchor, frame, shift, spec.entryLocals(), entryParams, entryOperands);
        remapLocals(inlined, argBase, firstNonArg, entryParams, entryOperands, spillSlots, offset, entryLocals, delta);
        final Handler[] handlers = exits.handlers;
        final int[][] locals = new int[handlers.length][];
        for (Handler exit : handlers) {
            locals[exit.index] = localSlots(spec, owner, anchor, frame, shift, spec.locals()[exit.index], exit.params, exit.localStart);
        }
        final Emitter emitter = new Emitter(classNode, exits, operands, Arrays.copyOfRange(spillSlots, offset, offset + operands.length), locals, delta);

        final LabelNode sStart = new LabelNode();
        final LabelNode tStart = inner == null || skips ? new LabelNode() : inner.start();
        final LabelNode tEnd = inner == null ? new LabelNode() : inner.end();
        final LabelNode sEnd = new LabelNode();
        final LabelNode join = skips ? new LabelNode() : null;

        final InsnList head = new InsnList();
        head.add(sStart);
        if (spill && !slots) {
            for (int i = args.length - 1; i >= spillFrom; i--) {
                final int j = i - offset;
                if (j >= 0 && j < operands.length && operands[j].getSort() >= Type.ARRAY && !args[i].equals(operands[j])) {
                    head.add(new TypeInsnNode(Opcodes.CHECKCAST, operands[j].getInternalName()));
                }
                head.add(new VarInsnNode(args[i].getOpcode(Opcodes.ISTORE), spillSlots[i]));
            }
            for (int i = below.length - 1; i >= 0; i--) {
                head.add(new VarInsnNode(below[i].getOpcode(Opcodes.ISTORE), belowSlots[i]));
            }
        }
        head.add(inlined.instructions);
        if (skips) {
            final LabelNode callPath = new LabelNode();
            head.add(new VarInsnNode(Opcodes.ILOAD, exits.skipSlot + delta));
            head.add(new JumpInsnNode(Opcodes.IFEQ, callPath));
            head.add(tStart);
            loadBelow(head, below, belowSlots);
            emitter.callSkipped(head);
            head.add(new JumpInsnNode(Opcodes.GOTO, join));
            head.add(callPath);
        }
        if (spill) {
            loadBelow(head, below, belowSlots);
            for (int i = spillFrom; i < args.length; i++) {
                head.add(new VarInsnNode(args[i].getOpcode(Opcodes.ILOAD), spillSlots[i]));
            }
        }
        if (inner == null && !skips) {
            head.add(tStart);
        }
        owner.instructions.insertBefore(anchor, head);

        final InsnList tail = new InsnList();
        if (inner == null) {
            tail.add(tEnd);
        }
        final LabelNode retStart = returnHandler == null ? null : new LabelNode();
        final LabelNode retEnd = returnHandler == null ? null : new LabelNode();
        if (returnHandler != null) {
            tail.add(retStart);
            if (result.getSize() > 0) {
                if (result.getSort() >= Type.ARRAY && !actual.equals(result)) {
                    tail.add(new TypeInsnNode(Opcodes.CHECKCAST, result.getInternalName()));
                }
                tail.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE), resSlot));
            }
            emitter.callReturn(tail, result, resSlot);
            tail.add(retEnd);
        }
        if (skips) {
            tail.add(join);
        }
        emitter.callFinally(tail);
        tail.add(new JumpInsnNode(Opcodes.GOTO, sEnd));
        final AbstractInsnNode beforeHandlers = tail.getLast();

        final List<TryCatchBlockNode> blocks = new ArrayList<>(inlined.tryCatchBlocks);
        emitter.emitHandlers(tail, blocks, tStart, tEnd, retStart, retEnd, excSlot, below, belowSlots, sEnd);
        final AbstractInsnNode firstHandler = beforeHandlers.getNext();
        tail.add(sEnd);
        owner.instructions.insert(inner == null ? call : inner.end(), tail);

        owner.tryCatchBlocks.addAll(insertionIndex(owner, sStart, sEnd), blocks);
        owner.maxLocals = Math.max(owner.maxLocals, storesResult ? resSlot + result.getSize() : excSlot + 1);
        owner.maxStack = Math.max(owner.maxStack, spec.target().maxStack) + Math.max(inlined.maxStack, exits.argsSize + 2);
        if (slots) {
            final LabelNode end = new LabelNode();
            owner.instructions.add(end);
            final List<LocalVariableNode> names = new ArrayList<>();
            nameParameters(names, classNode, spec, argBase, entryOperands, sStart, end);
            nameEntryLocals(names, inlined, owner.instructions, exits.carried, firstNonArg, delta, end);
            if (firstHandler != null) {
                names.add(new LocalVariableNode("surround$error", SurroundSpec.THROWABLE_DESC, null, (LabelNode) firstHandler, end, excSlot));
            }
            if (storesResult) {
                names.add(new LocalVariableNode("surround$result", result.getDescriptor(), null, retStart, retEnd, resSlot));
            }
            owner.localVariables = names;
        } else {
            spec.node().decorate(REGION, new Region(sStart, sEnd));
        }
    }

    private static void nameParameters(List<LocalVariableNode> names, ClassNode classNode, CallSiteSpec spec, int argBase, int entryOperands, LabelNode start, LabelNode end) {
        final Type[] params = spec.operands();
        final MethodNode entry = spec.entry();
        if (argBase > 0) {
            names.add(new LocalVariableNode("this", Type.getObjectType(classNode.name).getDescriptor(), null, start, end, 0));
        }
        for (int i = 0, slot = argBase; i < params.length; slot += params[i++].getSize()) {
            final String name = i < entryOperands ? SurroundBinding.paramName(entry, i) : null;
            names.add(new LocalVariableNode(name != null ? name : "arg" + i, params[i].getDescriptor(), null, start, end, slot));
        }
    }

    private static void nameEntryLocals(List<LocalVariableNode> names, MethodNode inlined, InsnList insns, Type[] carried, int firstNonArg, int delta, LabelNode end) {
        final LocalVariableNode[] furthest = new LocalVariableNode[carried.length];
        for (LocalVariableNode local : inlined.localVariables) {
            if (!isCarried(carried, local.index)) {
                continue;
            }
            final LocalVariableNode current = furthest[local.index];
            if (current == null || insns.indexOf(local.end) > insns.indexOf(current.end)) {
                furthest[local.index] = local;
            }
        }
        for (LocalVariableNode local : inlined.localVariables) {
            if (local.index < firstNonArg) {
                continue;
            }
            if (isCarried(carried, local.index) && local == furthest[local.index]) {
                final Type type = carried[local.index];
                names.add(new LocalVariableNode(local.name, type.getSort() >= Type.ARRAY ? local.desc : type.getDescriptor(), null, local.start, end, local.index + delta));
            } else {
                local.index += delta;
                names.add(local);
            }
        }
    }

    private static boolean isCarried(Type[] carried, int slot) {
        return slot < carried.length && carried[slot] != null;
    }

    private static MethodNode inlineable(MethodNode entry) {
        final MethodNode copy = new MethodNode(entry.access, entry.name, entry.desc, null, null);
        entry.accept(copy);
        for (AbstractInsnNode insn = copy.instructions.getFirst(); insn != null; ) {
            final AbstractInsnNode next = insn.getNext();
            if (insn instanceof FrameNode) {
                copy.instructions.remove(insn);
            }
            insn = next;
        }
        AbstractInsnNode last = copy.instructions.getLast();
        while (last.getOpcode() < 0) {
            last = last.getPrevious();
        }
        copy.instructions.remove(last);
        return copy;
    }

    private static MethodNode relocate(ClassNode classNode, MethodNode target) {
        final MethodNode body = new MethodNode(target.access & ~Opcodes.ACC_SYNCHRONIZED, unique(classNode, target.name + "$surround"), target.desc, null, null);
        Bytecode.setVisibility(body, Visibility.PRIVATE);
        body.instructions = target.instructions;
        body.instructions.resetLabels();
        body.tryCatchBlocks = target.tryCatchBlocks;
        body.localVariables = target.localVariables;
        body.visibleLocalVariableAnnotations = target.visibleLocalVariableAnnotations;
        body.invisibleLocalVariableAnnotations = target.invisibleLocalVariableAnnotations;
        body.maxStack = target.maxStack;
        body.maxLocals = target.maxLocals;

        target.instructions = new InsnList();
        target.tryCatchBlocks = new ArrayList<>();
        target.visibleLocalVariableAnnotations = null;
        target.invisibleLocalVariableAnnotations = null;
        target.maxLocals = target.maxStack = Bytecode.getFirstNonArgLocalIndex(target);
        classNode.methods.add(body);
        return body;
    }

    private static String unique(ClassNode classNode, String base) {
        String name = base;
        for (int i = 1; ; name = base + "$" + i++) {
            final String candidate = name;
            if (classNode.methods.stream().noneMatch(method -> method.name.equals(candidate))) {
                return name;
            }
        }
    }

    private static boolean hasLocals(CallSiteSpec spec) {
        if (spec.entryLocals().length > 0) {
            return true;
        }
        for (int[] locals : spec.locals()) {
            if (locals.length > 0) {
                return true;
            }
        }
        return false;
    }

    private static Frame<BasicValue> frameAt(MethodNode owner, AbstractInsnNode anchor, CallSiteSpec spec) {
        final int index = owner.instructions.indexOf(anchor);
        for (owner.maxStack = Math.max(owner.maxStack, spec.target().maxStack) + 16; ; owner.maxStack <<= 1) {
            try {
                return new Analyzer<>(new BasicInterpreter()).analyze(owner.name, owner)[index];
            } catch (AnalyzerException e) {
                if (!(e.getCause() instanceof IndexOutOfBoundsException) || owner.maxStack > 0xFFFF) {
                    throw cannotFollow(spec, "cannot analyze " + owner.name + owner.desc, e);
                }
            }
        }
    }

    private static Type[] below(CallSiteSpec spec, Frame<BasicValue> frame, int args) {
        final int count = frame == null ? 0 : frame.getStackSize() - args;
        if (count <= 0) {
            return NOTHING;
        }
        final Type[] below = new Type[count];
        for (int i = 0; i < count; i++) {
            final BasicValue value = frame.getStack(i);
            final Type type = value.getType();
            if (type == null || type.getSort() == Type.VOID) {
                throw cannotFollow(spec, "stack value " + i + " below the call is " + value + ", which cannot be restored", null);
            }
            below[i] = value.isReference() ? Type.getObjectType(SurroundSpec.OBJECT) : type;
        }
        return below;
    }

    private static int[] localSlots(CallSiteSpec spec, MethodNode owner, AbstractInsnNode anchor, Frame<BasicValue> frame, int shift, int[] resolved, Type[] params, int first) {
        if (resolved.length == 0 || owner == spec.target()) {
            return resolved;
        }
        final int firstNonArg = Bytecode.getFirstNonArgLocalIndex(spec.target());
        final int[] slots = new int[resolved.length];
        for (int i = 0; i < resolved.length; i++) {
            final int slot = resolved[i] < firstNonArg ? resolved[i] : resolved[i] + shift;
            checkLocal(spec, owner, anchor, frame, slot, params[first + i], resolved[i]);
            slots[i] = slot;
        }
        return slots;
    }

    private static void checkLocal(CallSiteSpec spec, MethodNode owner, AbstractInsnNode anchor, Frame<BasicValue> frame, int slot, Type type, int resolved) {
        String found = null;
        if (owner.localVariables != null) {
            final int at = owner.instructions.indexOf(anchor);
            for (LocalVariableNode local : owner.localVariables) {
                if (local.index == slot && owner.instructions.indexOf(local.start) <= at && at < owner.instructions.indexOf(local.end) && !type.getDescriptor().equals(local.desc)) {
                    found = Type.getType(local.desc).getClassName() + " '" + local.name + "'";
                }
            }
        }
        if (found == null && frame != null) {
            final BasicValue value = slot < frame.getLocals() ? frame.getLocal(slot) : null;
            final Type actual = value == null ? null : value.getType();
            if (actual == null || actual.getSort() == Type.VOID || actual.getSize() != type.getSize() || SurroundBinding.kindOf(actual) != SurroundBinding.kindOf(type)) {
                found = value == null ? "nothing" : String.valueOf(value);
            }
        }
        if (found != null) {
            throw cannotFollow(spec, "@Surround.Local " + type.getClassName() + " resolved to local " + resolved + " at injection, but after the body moved to " + owner.name + owner.desc + " local " + slot + " holds " + found, null);
        }
    }

    private static MethodNode owner(ClassNode classNode, AbstractInsnNode insn) {
        AbstractInsnNode first = insn;
        while (first.getPrevious() != null) {
            first = first.getPrevious();
        }
        final List<MethodNode> methods = classNode.methods;
        for (int i = 0, n = methods.size(); i < n; i++) {
            if (methods.get(i).instructions.getFirst() == first) {
                return methods.get(i);
            }
        }
        return null;
    }

    private static void checkOperands(CallSiteSpec spec, MethodInsnNode call, Type[] args, int offset) {
        final Type[] operands = spec.operands();
        if (args.length - offset < operands.length) {
            throw cannotFollow(spec, "the call was replaced by " + Bytecode.describeNode(call, false) + ", which takes fewer arguments than the surrounded call", null);
        }
        for (int i = 0; i < operands.length; i++) {
            if (SurroundBinding.kindOf(args[offset + i]) != SurroundBinding.kindOf(operands[i])) {
                throw cannotFollow(spec, "the call was replaced by " + Bytecode.describeNode(call, false) + ", whose argument " + (offset + i) + " is " + args[offset + i].getClassName() + " where the surrounded call passed " + operands[i].getClassName(), null);
            }
        }
    }

    private static IllegalStateException cannotFollow(CallSiteSpec spec, String detail, Throwable cause) {
        return new IllegalStateException(spec.description() + " cannot be applied, another transformation left the site in a shape it cannot follow: " + detail, cause);
    }

    private static boolean prefixed(CallSiteSpec spec) {
        for (Handler exit : spec.exits().handlers) {
            if (exit.localStart > exit.leading) {
                return true;
            }
        }
        return false;
    }

    private static int usedLocals(MethodNode owner) {
        int max = Bytecode.getFirstNonArgLocalIndex(owner);
        for (AbstractInsnNode insn = owner.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof VarInsnNode varInsn) {
                final int opcode = insn.getOpcode();
                final boolean wide = opcode == Opcodes.LLOAD || opcode == Opcodes.DLOAD || opcode == Opcodes.LSTORE || opcode == Opcodes.DSTORE;
                max = Math.max(max, varInsn.var + (wide ? 2 : 1));
            } else if (insn instanceof IincInsnNode iinc) {
                max = Math.max(max, iinc.var + 1);
            }
        }
        return max;
    }

    private static void remapLocals(MethodNode inlined, int argBase, int firstNonArg, Type[] entryParams, int entryOperands, int[] spillSlots, int offset, int[] entryLocals, int delta) {
        final int[] paramSlots = new int[firstNonArg];
        for (int i = 0, slot = argBase; i < entryParams.length; i++) {
            paramSlots[slot] = i < entryOperands ? spillSlots[offset + i] : entryLocals[i - entryOperands];
            slot += entryParams[i].getSize();
        }
        for (AbstractInsnNode insn = inlined.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof VarInsnNode var) {
                var.var = remap(var.var, argBase, firstNonArg, paramSlots, delta);
            } else if (insn instanceof IincInsnNode iinc) {
                iinc.var = remap(iinc.var, argBase, firstNonArg, paramSlots, delta);
            }
        }
    }

    private static int remap(int var, int argBase, int firstNonArg, int[] paramSlots, int delta) {
        if (var < argBase) {
            return var;
        }
        if (var < firstNonArg) {
            return paramSlots[var];
        }
        return var + delta;
    }

    private static int insertionIndex(MethodNode owner, LabelNode start, LabelNode end) {
        final List<TryCatchBlockNode> blocks = owner.tryCatchBlocks;
        final InsnList insns = owner.instructions;
        final int from = insns.indexOf(start);
        final int to = insns.indexOf(end);
        for (int i = 0, n = blocks.size(); i < n; i++) {
            final TryCatchBlockNode block = blocks.get(i);
            final int s = insns.indexOf(block.start);
            final int e = insns.indexOf(block.end);
            if (s < to && e > from && (s < from || e > to)) {
                return i;
            }
        }
        return blocks.size();
    }

    private static void loadBelow(InsnList insns, Type[] below, int[] belowSlots) {
        for (int i = 0; i < below.length; i++) {
            insns.add(new VarInsnNode(below[i].getOpcode(Opcodes.ILOAD), belowSlots[i]));
        }
    }

    private record Emitter(ClassNode classNode, Exits exits, Type[] operands, int[] operandSlots, int[][] locals, int delta) {

        private void invoke(InsnList insns, Handler exit, Type leading, int leadingSlot) {
            final int[] locals = this.locals[exit.index];
            if (!exit.isStatic) {
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
            }
            if (leading != null) {
                insns.add(new VarInsnNode(leading.getOpcode(Opcodes.ILOAD), leadingSlot));
            }
            for (int i = 0, n = exit.localStart - exit.leading; i < n; i++) {
                insns.add(new VarInsnNode(this.operands[i].getOpcode(Opcodes.ILOAD), this.operandSlots[i]));
            }
            for (int i = exit.localStart; i < exit.carryStart; i++) {
                insns.add(new VarInsnNode(exit.params[i].getOpcode(Opcodes.ILOAD), locals[i - exit.localStart]));
            }
            for (Load load : exit.loads) {
                final Type type = load.type();
                insns.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), load.slot() + this.delta));
                if (type.getSort() >= Type.ARRAY && !SurroundSpec.OBJECT.equals(type.getInternalName())) {
                    insns.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
                }
            }
            // MixinExtras 0.5.0 internal (pinned by UniMixins 0.3.1); recheck on bump
            insns.add(ASMUtils.getInvokeInstruction(this.classNode, exit.method));
        }

        void callFinally(InsnList insns) {
            if (this.exits.finallyHandler != null) {
                this.invoke(insns, this.exits.finallyHandler, null, 0);
            }
        }

        void callSkipped(InsnList insns) {
            if (this.exits.skipped != null) {
                this.invoke(insns, this.exits.skipped, null, 0);
            }
        }

        void callReturn(InsnList insns, Type result, int resSlot) {
            this.invoke(insns, this.exits.returnHandler, result.getSize() > 0 ? result : null, resSlot);
        }

        void emitHandlers(InsnList tail, List<TryCatchBlockNode> blocks, LabelNode start, LabelNode end, LabelNode retStart, LabelNode retEnd, int excSlot, Type[] below, int[] belowSlots, LabelNode handledExit) {
            final Handler[] handlers = this.exits.handlers;
            final boolean hasFinally = this.exits.finallyHandler != null;
            final LabelNode catchAll = hasFinally ? new LabelNode() : null;
            final List<TryCatchBlockNode> catchAlls = hasFinally ? new ArrayList<>() : null;
            for (int k = 0; k < this.exits.catches; k++) {
                final Handler exit = handlers[k];
                final LabelNode handler = new LabelNode();
                final LabelNode exitStart = new LabelNode();
                final LabelNode exitEnd = new LabelNode();

                tail.add(handler);
                tail.add(new VarInsnNode(Opcodes.ASTORE, excSlot));
                if (exit.handles) {
                    loadBelow(tail, below, belowSlots);
                }
                tail.add(exitStart);
                this.invoke(tail, exit, exit.caught(), excSlot);
                tail.add(exitEnd);

                this.callFinally(tail);
                if (exit.handles) {
                    tail.add(new JumpInsnNode(Opcodes.GOTO, handledExit));
                } else {
                    tail.add(new VarInsnNode(Opcodes.ALOAD, excSlot));
                    tail.add(new InsnNode(Opcodes.ATHROW));
                }

                blocks.add(new TryCatchBlockNode(start, end, handler, exit.caught().getInternalName()));
                if (hasFinally) {
                    catchAlls.add(new TryCatchBlockNode(exitStart, exitEnd, catchAll, null));
                }
            }
            if (hasFinally) {
                tail.add(catchAll);
                tail.add(new VarInsnNode(Opcodes.ASTORE, excSlot));
                this.callFinally(tail);
                tail.add(new VarInsnNode(Opcodes.ALOAD, excSlot));
                tail.add(new InsnNode(Opcodes.ATHROW));

                if (retStart != null) {
                    catchAlls.add(new TryCatchBlockNode(retStart, retEnd, catchAll, null));
                }
                catchAlls.add(new TryCatchBlockNode(start, end, catchAll, null));
                blocks.addAll(catchAlls);
            }
        }
    }
}
