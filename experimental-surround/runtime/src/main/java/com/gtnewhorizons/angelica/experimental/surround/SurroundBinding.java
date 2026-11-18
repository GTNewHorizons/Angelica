package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.TypePath;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.FrameNode;
import org.spongepowered.asm.lib.tree.IincInsnNode;
import org.spongepowered.asm.lib.tree.LabelNode;
import org.spongepowered.asm.lib.tree.LocalVariableAnnotationNode;
import org.spongepowered.asm.lib.tree.LocalVariableNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.tree.VarInsnNode;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

final class SurroundBinding {

    private static final int TOP = -1;
    private static final String[] SUGARS = {"Local", "Share", "Cancellable"};
    static final String SUGAR_HINT = ", which a @Surround handler cannot resolve; use @Surround.Carry, or @Surround.Local on a call";

    enum Role {
        ENTRY(SurroundSpec.SURROUND), CATCH(SurroundSpec.CATCH), FINALLY(SurroundSpec.FINALLY), SKIPPED(SurroundSpec.SKIPPED), RETURN(SurroundSpec.RETURN);

        static final Role[] EXITS = {CATCH, FINALLY, SKIPPED, RETURN};

        final String annotation;

        Role(String annotation) {
            this.annotation = annotation;
        }
    }

    record Load(int slot, Type type) {}

    static final class Handler {

        final MethodNode method;
        final Role role;
        final Type[] params;
        final boolean isStatic;
        final boolean handles;
        final int leading;
        final int localStart;
        final int carryStart;
        private final String[] carryIds;
        int index;
        Load[] loads;

        Handler(MethodNode method, Role role, int leading) {
            this.method = method;
            this.role = role;
            this.params = Type.getArgumentTypes(method.desc);
            this.isStatic = Bytecode.isStatic(method);
            final AnnotationNode caught = role == Role.CATCH ? Annotations.get(method.visibleAnnotations, SurroundSpec.CATCH) : null;
            this.handles = caught != null && Annotations.<Boolean>getValue(caught, "handle", Boolean.FALSE).booleanValue();
            this.leading = leading;
            final int count = this.params.length;
            int firstLocal = count;
            int firstCarry = count;
            int section = 0;
            String[] ids = null;
            for (int i = 0; i < count; i++) {
                final AnnotationNode carry = carry(method, i);
                final boolean local = local(method, i) != null;
                if (carry != null && role == Role.ENTRY) {
                    throw this.reject("has @Surround.Carry on " + paramRef(method, i) + "; only the entry handler's own locals can be carried");
                }
                if (local && carry != null) {
                    throw this.reject(paramRef(method, i) + " cannot be both @Surround.Local and @Surround.Carry");
                }
                if ((local || carry != null) && i < leading) {
                    throw this.reject("cannot mark its " + (role == Role.CATCH ? "caught Throwable" : "result") + " parameter @Surround." + (local ? "Local" : "Carry"));
                }
                final int next = carry != null ? 2 : local ? 1 : 0;
                if (next < section) {
                    throw this.reject(paramRef(method, i) + " is out of order; declare operands first, then @Surround.Local, then @Surround.Carry parameters");
                }
                section = next;
                if (local && firstLocal == count) {
                    firstLocal = i;
                }
                if (carry != null) {
                    if (ids == null) {
                        firstCarry = i;
                        ids = new String[count - i];
                    }
                    ids[i - firstCarry] = Annotations.<String>getValue(carry, "value", "");
                }
            }
            this.localStart = Math.min(firstLocal, firstCarry);
            this.carryStart = firstCarry;
            this.carryIds = ids;
        }

        Type caught() {
            return this.params[0];
        }

        String ref() {
            return this.role == Role.ENTRY ? "entry handler" : exitRef(this.method);
        }

        SurroundRejection reject(String message) {
            return new SurroundRejection(this.ref() + " " + message);
        }
    }

    static final class Exits {

        final Handler[] handlers;
        final int catches;
        final Handler finallyHandler;
        final Handler skipped;
        final Handler returnHandler;
        final boolean handles;
        final int skipSlot;
        final Type[] carried;
        final int argsSize;

        Exits(Handler[] handlers, int skipSlot, Type[] carried) {
            this.handlers = handlers;
            int catches = 0;
            Handler finallyHandler = null;
            Handler skipped = null;
            Handler returnHandler = null;
            boolean handles = false;
            int argsSize = 0;
            for (int i = 0; i < handlers.length; i++) {
                final Handler handler = handlers[i];
                handler.index = i;
                handles |= handler.handles;
                argsSize = Math.max(argsSize, (handler.isStatic ? 0 : 1) + Bytecode.getArgsSize(handler.params));
                switch (handler.role) {
                    case CATCH -> catches++;
                    case FINALLY -> finallyHandler = handler;
                    case SKIPPED -> skipped = handler;
                    case RETURN -> returnHandler = handler;
                }
            }
            this.catches = catches;
            this.finallyHandler = finallyHandler;
            this.skipped = skipped;
            this.returnHandler = returnHandler;
            this.handles = handles;
            this.skipSlot = skipSlot;
            this.carried = carried;
            this.argsSize = argsSize;
        }
    }

    private SurroundBinding() {
    }

    static Type[] bind(MethodNode entry, int[] kinds, Handler[] handlers) {
        checkNoParameterStores(entry);
        final List<Carried> carried = carriedLocals(entry, kinds);
        final Type[] slots = new Type[entry.maxLocals];
        for (Handler exit : handlers) {
            final Load[] loads = new Load[exit.params.length - exit.carryStart];
            for (int i = exit.carryStart; i < exit.params.length; i++) {
                loads[i - exit.carryStart] = resolve(carried, slots, exit, i);
            }
            exit.loads = loads;
        }
        return slots;
    }

    static int skipSlot(MethodNode entry, int[] kinds) {
        final List<LocalVariableAnnotationNode> annotations = entry.visibleLocalVariableAnnotations;
        int slot = -1;
        for (int a = 0, n = annotations == null ? 0 : annotations.size(); a < n; a++) {
            final LocalVariableAnnotationNode annotation = annotations.get(a);
            if (!SurroundSpec.SKIP.equals(annotation.desc)) {
                continue;
            }
            if (slot != -1) {
                throw new SurroundRejection("declares two @Surround.Skip locals; there can only be one");
            }
            slot = annotatedSlot(entry, annotation, 0);
            checkLocalAnnotation(entry, kinds, "@Surround.Skip", annotation, slot, "");
            if (kinds[slot] != Type.INT) {
                throw new SurroundRejection("has a @Surround.Skip local that is " + withArticle(kinds[slot]) + "; it must be boolean or int-like");
            }
        }
        return slot;
    }

    private static AnnotationNode carry(MethodNode method, int index) {
        final List<AnnotationNode> invisible = parameter(method.invisibleParameterAnnotations, index);
        for (int i = 0, n = invisible == null ? 0 : invisible.size(); i < n; i++) {
            final String name = Type.getType(invisible.get(i).desc).getClassName();
            for (String sugar : SUGARS) {
                if (name.endsWith("mixinextras.sugar." + sugar)) {
                    throw new SurroundRejection(paramRef(method, index) + " of '" + method.name + "' is annotated @" + sugar + SUGAR_HINT);
                }
            }
        }
        return Annotations.get(parameter(method.visibleParameterAnnotations, index), SurroundSpec.CARRY);
    }

    static AnnotationNode local(MethodNode method, int index) {
        return Annotations.get(parameter(method.visibleParameterAnnotations, index), SurroundSpec.LOCAL);
    }

    private static List<AnnotationNode> parameter(List<AnnotationNode>[] table, int index) {
        return table == null || index >= table.length ? null : table[index];
    }

    static String paramRef(MethodNode method, int index) {
        final String name = paramName(method, index);
        return name == null || name.isEmpty() ? "parameter " + index : "parameter " + index + " ('" + name + "')";
    }

    static String paramName(MethodNode method, int index) {
        if (method.localVariables == null) {
            return null;
        }
        final int slot = (Bytecode.isStatic(method) ? 0 : 1) + Bytecode.getArgsSize(Type.getArgumentTypes(method.desc), 0, index);
        LocalVariableNode earliest = null;
        int earliestPos = Integer.MAX_VALUE;
        for (LocalVariableNode local : method.localVariables) {
            if (local.index != slot) {
                continue;
            }
            final int pos = method.instructions.indexOf(local.start);
            if (pos < earliestPos) {
                earliestPos = pos;
                earliest = local;
            }
        }
        return earliest == null ? null : earliest.name;
    }

    static AbstractInsnNode soleReturn(MethodNode entry) {
        AbstractInsnNode sole = null;
        AbstractInsnNode last = null;
        int returns = 0;
        for (AbstractInsnNode insn = entry.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() < 0) {
                continue;
            }
            last = insn;
            if (insn.getOpcode() == Opcodes.RETURN) {
                sole = insn;
                returns++;
            }
        }
        if (returns != 1 || sole != last) {
            throw new SurroundRejection("must reach its end to hand over to the body; restructure it so every path falls through to its end, with no early return, throw or endless loop");
        }
        return sole;
    }

    private static Load resolve(List<Carried> carried, Type[] slots, Handler exit, int index) {
        final Type type = exit.params[index];
        final String id = exit.carryIds[index - exit.carryStart];
        final int kind = kindOf(type);
        final boolean named = !id.isEmpty();
        boolean idDeclared = false;
        int matches = 0;
        Carried match = null;
        for (int i = 0, n = carried.size(); i < n; i++) {
            final Carried candidate = carried.get(i);
            if (named && !id.equals(candidate.id())) {
                continue;
            }
            idDeclared = true;
            if (candidate.kind() == kind) {
                matches++;
                match = candidate;
            }
        }
        final String param = paramRef(exit.method, index);
        if (named && !idDeclared) {
            throw exit.reject(param + " asks for the carried local '" + id + "', which the entry handler does not declare" + declaredList(carried));
        }
        if (matches == 0) {
            throw exit.reject(param + " is " + type.getClassName() + " (" + withArticle(kind) + "), which matches no carried local of that kind" + declaredList(carried));
        }
        if (matches > 1) {
            throw exit.reject(param + " is " + type.getClassName() + " (" + withArticle(kind) + "), which matches " + matches + " carried locals of that kind; name them with @Surround.Carry(\"...\") on both sides");
        }
        final Type existing = slots[match.slot()];
        if (existing != null && !existing.equals(type)) {
            throw exit.reject(param + " reads carried local " + match + " as " + type.getClassName() + ", but another exit handler reads it as " + existing.getClassName());
        }
        slots[match.slot()] = type;
        return new Load(match.slot(), type);
    }

    private static List<Carried> carriedLocals(MethodNode entry, int[] kinds) {
        final List<Carried> carried = new ArrayList<>();
        final List<LocalVariableAnnotationNode> annotations = entry.visibleLocalVariableAnnotations;
        for (int a = 0, n = annotations == null ? 0 : annotations.size(); a < n; a++) {
            final LocalVariableAnnotationNode annotation = annotations.get(a);
            if (!SurroundSpec.CARRY.equals(annotation.desc)) {
                continue;
            }
            final int slot = annotatedSlot(entry, annotation, 0);
            final String id = Annotations.<String>getValue(annotation, "value", "");
            checkLocalAnnotation(entry, kinds, "@Surround.Carry", annotation, slot, id);
            if (!id.isEmpty()) {
                for (int i = 0, count = carried.size(); i < count; i++) {
                    if (id.equals(carried.get(i).id())) {
                        throw new SurroundRejection("declares two @Surround.Carry locals named '" + id + "'");
                    }
                }
            }
            carried.add(new Carried(slot, kinds[slot], id));
        }
        return carried;
    }

    // FML's deobf remapper (ASM LocalVariablesSorter) corrupts annotation indices of long/double locals; the LVT stays right.
    private static int annotatedSlot(MethodNode entry, LocalVariableAnnotationNode annotation, int range) {
        final int raw = annotation.index.get(range).intValue();
        final LabelNode start = annotation.start.get(range);
        final LabelNode end = annotation.end.get(range);
        boolean any = false;
        boolean rawWide = false;
        boolean rawFound = false;
        int wide = -1;
        int wideCount = 0;
        for (int i = 0, n = entry.localVariables == null ? 0 : entry.localVariables.size(); i < n; i++) {
            final LocalVariableNode local = entry.localVariables.get(i);
            if (local.start != start || local.end != end) {
                continue;
            }
            any = true;
            final boolean isWide = Type.getType(local.desc).getSize() == 2;
            if (isWide) {
                wide = local.index;
                wideCount++;
            }
            if (local.index == raw) {
                rawFound = true;
                rawWide = isWide;
            }
        }
        final int slot = !any || rawFound && !(rawWide && wideCount > 1) ? raw : wideCount == 1 && !rawFound ? wide : -1;
        if (slot < 0) {
            throw new SurroundRejection("has a long or double annotated local that shares its scope with another; initialize it at its declaration");
        }
        if (slot >= entry.maxLocals) {
            throw new SurroundRejection("has an annotated local outside its method's locals (slot " + slot + " of " + entry.maxLocals + "); compile with local variable tables");
        }
        return slot;
    }

    private static void checkLocalAnnotation(MethodNode entry, int[] kinds, String what, LocalVariableAnnotationNode annotation, int slot, String id) {
        final String local = id.isEmpty() ? "in slot " + slot : "named '" + id + "'";
        final TypePath path = annotation.typePath;
        for (int i = 0, length = path == null ? 0 : path.getLength(); i < length; i++) {
            if (path.getStep(i) != TypePath.ARRAY_ELEMENT) {
                throw new SurroundRejection("has a " + what + " " + local + " inside the local's type (" + path + "); " + what + " must annotate the local itself, not part of its type");
            }
        }
        for (int i = 1, count = annotation.index.size(); i < count; i++) {
            if (annotatedSlot(entry, annotation, i) != slot) {
                throw new SurroundRejection("has a " + what + " local " + local + " recorded in several slots " + annotation.index + "; initialize it at its declaration");
            }
        }
        LabelNode end = annotation.end.get(0);
        for (int i = 1, n = annotation.end.size(); i < n; i++) {
            if (entry.instructions.indexOf(annotation.end.get(i)) > entry.instructions.indexOf(end)) {
                end = annotation.end.get(i);
            }
        }
        for (AbstractInsnNode insn = end.getNext(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() >= 0 && insn.getOpcode() != Opcodes.RETURN) {
                throw new SurroundRejection("has a " + what + " local " + local + " that goes out of scope before the entry handler ends; declare it in the entry handler's outermost block");
            }
        }
        if (kinds[slot] == TOP) {
            throw new SurroundRejection("has a " + what + " local " + local + " that is not definitely assigned when the entry handler ends; initialize it at its declaration");
        }
    }

    private static void checkNoParameterStores(MethodNode entry) {
        final int limit = Bytecode.getFirstNonArgLocalIndex(entry);
        for (AbstractInsnNode insn = entry.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            final int var;
            if (insn instanceof VarInsnNode varInsn && storeKind(insn.getOpcode()) != -1) {
                var = varInsn.var;
            } else if (insn instanceof IincInsnNode iinc) {
                var = iinc.var;
            } else {
                continue;
            }
            if (var < limit) {
                throw new SurroundRejection("must not assign its parameters; use @ModifyVariable or @WrapMethod to change arguments");
            }
        }
    }

    static int[] kindsAtEnd(MethodNode entry, AbstractInsnNode soleReturn) {
        final int maxLocals = entry.maxLocals;
        int[] vars = initialVars(entry);
        int count = vars.length;
        int[] state = toSlots(vars, count, maxLocals);
        for (AbstractInsnNode insn = entry.instructions.getFirst(); insn != soleReturn; insn = insn.getNext()) {
            if (insn instanceof FrameNode frame) {
                final int locals = frame.local == null ? 0 : frame.local.size();
                switch (frame.type) {
                    case Opcodes.F_NEW, Opcodes.F_FULL, Opcodes.F_APPEND -> {
                        if (frame.type != Opcodes.F_APPEND) {
                            count = 0;
                        }
                        if (count + locals > vars.length) {
                            vars = Arrays.copyOf(vars, count + locals);
                        }
                        for (int i = 0; i < locals; i++) {
                            vars[count++] = frameKind(frame.local.get(i));
                        }
                    }
                    case Opcodes.F_CHOP -> count -= locals;
                    default -> {
                    }
                }
                state = toSlots(vars, count, maxLocals);
            } else if (insn instanceof VarInsnNode var && storeKind(insn.getOpcode()) != -1) {
                store(state, var.var, storeKind(insn.getOpcode()));
            }
        }
        return state;
    }

    private static void store(int[] state, int var, int kind) {
        if (var > 0 && wide(state[var - 1])) {
            state[var - 1] = TOP;
        }
        state[var] = kind;
        if (wide(kind) && var + 1 < state.length) {
            state[var + 1] = TOP;
        }
    }

    private static boolean wide(int kind) {
        return kind == Type.LONG || kind == Type.DOUBLE;
    }

    private static int[] initialVars(MethodNode entry) {
        final Type[] args = Type.getArgumentTypes(entry.desc);
        final int receiver = Bytecode.isStatic(entry) ? 0 : 1;
        final int[] vars = new int[receiver + args.length];
        if (receiver != 0) {
            vars[0] = Type.OBJECT;
        }
        for (int i = 0; i < args.length; i++) {
            vars[receiver + i] = kindOf(args[i]);
        }
        return vars;
    }

    private static int frameKind(Object entry) {
        if (entry instanceof String || Opcodes.NULL.equals(entry) || Opcodes.UNINITIALIZED_THIS.equals(entry)) {
            return Type.OBJECT;
        } else if (Opcodes.INTEGER.equals(entry)) {
            return Type.INT;
        } else if (Opcodes.FLOAT.equals(entry)) {
            return Type.FLOAT;
        } else if (Opcodes.LONG.equals(entry)) {
            return Type.LONG;
        } else if (Opcodes.DOUBLE.equals(entry)) {
            return Type.DOUBLE;
        }
        return TOP;
    }

    private static int[] toSlots(int[] vars, int count, int maxLocals) {
        final int[] slots = new int[maxLocals];
        Arrays.fill(slots, TOP);
        int slot = 0;
        for (int i = 0; i < count && slot < maxLocals; i++) {
            slots[slot] = vars[i];
            slot += wide(vars[i]) ? 2 : 1;
        }
        return slots;
    }

    private static int storeKind(int opcode) {
        return switch (opcode) {
            case Opcodes.ISTORE -> Type.INT;
            case Opcodes.LSTORE -> Type.LONG;
            case Opcodes.FSTORE -> Type.FLOAT;
            case Opcodes.DSTORE -> Type.DOUBLE;
            case Opcodes.ASTORE -> Type.OBJECT;
            default -> -1;
        };
    }

    static int kindOf(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Type.INT;
            case Type.LONG -> Type.LONG;
            case Type.FLOAT -> Type.FLOAT;
            case Type.DOUBLE -> Type.DOUBLE;
            default -> Type.OBJECT;
        };
    }

    private static String kindDescription(int kind) {
        return switch (kind) {
            case Type.INT -> "int-like value";
            case Type.LONG -> "long";
            case Type.FLOAT -> "float";
            case Type.DOUBLE -> "double";
            default -> "reference";
        };
    }

    private static String withArticle(int kind) {
        return (kind == Type.INT ? "an " : "a ") + kindDescription(kind);
    }

    private static String declaredList(List<Carried> carried) {
        final StringJoiner all = new StringJoiner(", ", "; the entry handler carries ", "").setEmptyValue("; the entry handler carries nothing");
        for (Carried local : carried) {
            all.add(local.toString());
        }
        return all.toString();
    }

    static String exitRef(MethodNode exit) {
        return "exit handler '" + exit.name + "'";
    }

    private record Carried(int slot, int kind, String id) {

        @Override
        public String toString() {
            return (this.id.isEmpty() ? "an unnamed " + kindDescription(this.kind) : "'" + this.id + "', " + withArticle(this.kind)) + " in slot " + this.slot;
        }
    }
}
