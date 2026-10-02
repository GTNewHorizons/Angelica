package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Exits;
import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Handler;
import com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.Role;
import com.llamalad7.mixinextras.utils.InjectorUtils;
import org.spongepowered.asm.lib.Opcodes;
import org.spongepowered.asm.lib.Type;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.LocalVariableAnnotationNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.modify.InvalidImplicitDiscriminatorException;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo.Traversal;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.asm.MethodNodeEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

import static com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.exitRef;
import static com.gtnewhorizons.angelica.experimental.surround.SurroundBinding.paramRef;

final class SurroundInjector extends Injector {

    private final String id;
    private final boolean callSite;
    private final MixinTargetContext mixin;
    private Handler entry;
    private Exits exits;

    SurroundInjector(SurroundInjectionInfo info) {
        super(info, "@Surround");
        this.id = info.getAnnotation().getValue("id", "");
        this.callSite = info.callSite;
        this.mixin = (MixinTargetContext) info.getMixin();
        SurroundApplicatorExtension.instance().declare(this.mixin.getTarget(), this.mixin.getClassName());
    }

    @Override
    protected void inject(Target target, InjectionNode node) {
        this.checkTargetModifiers(target, true);
        try {
            if (this.callSite) {
                this.surroundCall(target, node);
            } else {
                final Type result = Type.getReturnType(target.method.desc);
                this.prepare(target.arguments, result, target.toString());
                this.offer(target, null, target.arguments, result, target.toString());
            }
        } catch (SurroundRejection e) {
            throw error(e.getMessage());
        }
    }

    private void surroundCall(Target target, InjectionNode node) {
        final AbstractInsnNode insn = node.getOriginalTarget();
        if (!(insn instanceof MethodInsnNode call)) {
            throw error("at must select a method call, not " + Bytecode.describeNode(insn, false) + " in " + target);
        }
        final String site = "call " + call.owner + "." + call.name + call.desc + " in " + target;
        if ("<init>".equals(call.name)) {
            throw error("at must select a method call, not the constructor " + site);
        }
        final Type[] operands = SurroundCallSite.operands(call, this.classNode.name);
        final Type result = Type.getReturnType(call.desc);
        this.prepare(operands, result, site);
        final boolean insideNew = isInsideNew(call);
        if (insideNew && this.methodNode.tryCatchBlocks != null && !this.methodNode.tryCatchBlocks.isEmpty()) {
            throw error("must not catch exceptions when it surrounds a call inside a constructor call's arguments (between NEW and <init>); move the try into a method it calls");
        }
        if (insideNew && this.exits.handles) {
            throw error("has a @Surround.Catch" + this.forId() + " that declares handle = true, but the call is inside a constructor call's arguments (between NEW and <init>), where the values below it cannot be restored");
        }
        this.offer(target, node, operands, result, site);
    }

    private void offer(Target target, InjectionNode node, Type[] operands, Type result, String site) {
        final Handler[] handlers = this.exits.handlers;
        final int[][] locals = new int[handlers.length][];
        for (Handler exit : handlers) {
            locals[exit.index] = this.resolveLocals(target, node, exit);
        }
        final int[] entryLocals = this.resolveLocals(target, node, this.entry);
        this.info.addCallbackInvocation(this.methodNode);
        final String description = "@Surround " + this.mixin.getClassName() + "::" + this.info.getMethodName() + " surrounding " + site;
        SurroundApplicatorExtension.instance().offer(this.mixin.getTarget(), new CallSiteSpec(target.method, node, this.methodNode, this.methodNode.desc, operands, result, this.exits, description, entryLocals, locals));
    }

    private static boolean isInsideNew(MethodInsnNode call) {
        int constructed = 0;
        for (AbstractInsnNode insn = call.getPrevious(); insn != null; insn = insn.getPrevious()) {
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL && "<init>".equals(((MethodInsnNode) insn).name)) {
                constructed++;
            } else if (insn.getOpcode() == Opcodes.NEW) {
                if (constructed == 0) {
                    return true;
                }
                constructed--;
            }
        }
        return false;
    }

    private int[] resolveLocals(Target target, InjectionNode node, Handler handler) {
        final int from = handler.localStart;
        final int[] slots = new int[handler.carryStart - from];
        for (int i = from; i < handler.carryStart; i++) {
            final Type type = handler.params[i];
            final LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(SurroundBinding.local(handler.method, i));
            // MixinExtras 0.5.0 internal (pinned by UniMixins 0.3.1); recheck on bump
            final Context context = InjectorUtils.getOrCreateLocalContext(target, node, this.info, type, discriminator.isArgsOnly());
            String detail;
            try {
                slots[i - from] = discriminator.findLocal(context);
                detail = slots[i - from] < 0 ? "no " + discriminator.toString(context) : null;
            } catch (InvalidImplicitDiscriminatorException e) {
                detail = e.getMessage();
            }
            if (detail != null) {
                throw error(handler.ref() + " " + paramRef(handler.method, i) + " is @Surround.Local " + type.getClassName() + ", which matches no single local at the call: " + detail);
            }
        }
        return slots;
    }

    private void prepare(Type[] expected, Type result, String against) {
        if (this.exits == null) {
            this.analyze(result);
        }
        this.checkArgPrefix(this.entry, expected, against);
        for (Handler exit : this.exits.handlers) {
            if (exit.role == Role.SKIPPED || exit.role == Role.RETURN || exit.handles) {
                final Type declared = Type.getReturnType(exit.method.desc);
                if (!declared.equals(result)) {
                    throw error(exit.ref() + " must return " + result.getClassName() + " to match what it surrounds, not " + declared.getClassName());
                }
            }
            if (exit.role == Role.RETURN && exit.leading == 1 && (exit.params.length == 0 || !exit.params[0].equals(result))) {
                throw error(exit.ref() + " must declare the " + result.getClassName() + " result as its first parameter");
            }
            this.checkArgPrefix(exit, expected, against);
        }
        if (this.exits.skipped == null && this.exits.skipSlot >= 0 && result.getSize() > 0) {
            throw error("can skip a " + result.getClassName() + " result but has no @Surround.Skipped" + this.forId() + " to supply it");
        }
    }

    private void analyze(Type result) {
        if (!this.returnType.equals(Type.VOID_TYPE)) {
            throw error("must return void; carry state out with @Surround.Carry locals instead");
        }
        if (Bytecode.hasFlag(this.methodNode, Opcodes.ACC_ABSTRACT)) {
            throw error("must not be abstract");
        }
        final Handler entry = new Handler(this.methodNode, Role.ENTRY, 0);
        if (this.hasStrippedSugar()) {
            throw error("one of its parameters is annotated @Local, @Share or @Cancellable" + SurroundBinding.SUGAR_HINT);
        }
        this.checkNoLocal(entry);
        final AbstractInsnNode soleReturn = SurroundBinding.soleReturn(this.methodNode);

        final Set<String> ids = new LinkedHashSet<>();
        for (MethodNode method : this.mixin.getMixin().getClassNode(0).methods) {
            final AnnotationNode surround = Annotations.get(method.visibleAnnotations, SurroundSpec.SURROUND);
            if (surround == null) {
                continue;
            }
            final String declared = Annotations.<String>getValue(surround, "id", "");
            if (!ids.add(declared) && declared.equals(this.id)) {
                throw error("duplicate @Surround " + describeId(this.id) + "; ids must be unique per mixin");
            }
        }
        final List<Handler> handlers = new ArrayList<>();
        for (MethodNode method : this.mixin.getClassNode().methods) {
            Role role = null;
            AnnotationNode annotation = null;
            for (Role candidate : Role.EXITS) {
                final AnnotationNode found = Annotations.get(method.visibleAnnotations, candidate.annotation);
                if (found == null) {
                    continue;
                }
                if (role != null) {
                    throw error(exitRef(method) + " can be only one of @Surround.Catch, @Surround.Finally, @Surround.Skipped and @Surround.Return");
                }
                role = candidate;
                annotation = found;
            }
            if (role == null) {
                continue;
            }
            final String exitId = Annotations.<String>getValue(annotation, "value", "");
            if (!ids.contains(exitId)) {
                throw error(exitRef(method) + " " + noSurroundForId(exitId, ids));
            }
            if (this.id.equals(exitId)) {
                handlers.add(this.exitHandler(this.own(method), role, role == Role.CATCH || role == Role.RETURN && result.getSize() > 0 ? 1 : 0));
            }
        }
        handlers.sort(Comparator.comparing(handler -> handler.role));
        boolean skipped = false;
        for (int i = 0, n = handlers.size(); i < n; i++) {
            final Role role = handlers.get(i).role;
            skipped |= role == Role.SKIPPED;
            if (i > 0 && role != Role.CATCH && role == handlers.get(i - 1).role) {
                throw error("declares more than one @Surround." + role.name().charAt(0) + role.name().substring(1).toLowerCase(Locale.ROOT) + this.forId() + "; there can only be one");
            }
        }
        final Handler[] sorted = handlers.toArray(new Handler[0]);
        this.sortMostSpecificFirst(sorted);
        final List<LocalVariableAnnotationNode> annotations = this.methodNode.visibleLocalVariableAnnotations;
        final int[] kinds = annotations == null || annotations.isEmpty() ? null : SurroundBinding.kindsAtEnd(this.methodNode, soleReturn);
        final int skipSlot = SurroundBinding.skipSlot(this.methodNode, kinds);
        if (skipped && skipSlot < 0) {
            throw error("has a @Surround.Skipped" + this.forId() + " but declares no @Surround.Skip local, so nothing is ever skipped");
        }
        final Type[] carried = SurroundBinding.bind(this.methodNode, kinds, sorted);
        this.entry = entry;
        this.exits = new Exits(sorted, skipSlot, carried);
    }

    // MixinExtras strips sugar params before parse; the annotation table can then be null.
    private boolean hasStrippedSugar() {
        final List<AnnotationNode>[] invisible = this.methodNode.invisibleParameterAnnotations;
        if (invisible != null) {
            return invisible.length != this.methodArgs.length;
        }
        final ClassInfo mixin = this.mixin.getClassInfo();
        final String desc = this.methodNode.desc;
        return mixin.findMethod(MethodNodeEx.getName(this.methodNode), desc, ClassInfo.INCLUDE_ALL) == null && mixin.findMethod(this.methodNode.name, desc, ClassInfo.INCLUDE_ALL) == null;
    }

    @Override
    protected void sanityCheck(Target target, List<InjectionPoint> injectionPoints) {
        super.sanityCheck(target, injectionPoints);
        if (this.isInterface) {
            throw error("cannot target an interface method " + target);
        }
        if (target.method.name.endsWith("init>")) {
            throw error("cannot target the initializer " + target);
        }
        if (Bytecode.hasFlag(target.method, Opcodes.ACC_ABSTRACT) || Bytecode.hasFlag(target.method, Opcodes.ACC_NATIVE)) {
            throw error("cannot target the abstract or native method " + target);
        }
    }

    private Handler exitHandler(MethodNode method, Role role, int leading) {
        final Handler exit = new Handler(method, role, leading);
        if (exit.handles && !this.callSite) {
            throw error(exit.ref() + " declares handle = true, which only applies when @Surround.at selects a call");
        }
        if (Bytecode.hasFlag(method, Opcodes.ACC_ABSTRACT)) {
            throw error(exit.ref() + " must not be abstract");
        }
        if ((role == Role.FINALLY || role == Role.CATCH && !exit.handles) && !Type.getReturnType(method.desc).equals(Type.VOID_TYPE)) {
            throw error(exit.ref() + " must return void; an exit handler cannot suppress or replace anything");
        }
        if (Bytecode.isStatic(method) != this.isStatic) {
            throw error(exit.ref() + " must be " + (this.isStatic ? "static" : "non-static") + " to match the entry handler" + this.forId());
        }
        if (role == Role.CATCH) {
            if (exit.params.length == 0) {
                throw error(exit.ref() + " must declare the Throwable it catches as its first parameter");
            }
            final Type caught = exit.caught();
            if (caught.getSort() != Type.OBJECT || !SurroundSpec.THROWABLE.equals(caught.getInternalName()) && !extendsClass(caught, SurroundSpec.THROWABLE)) {
                throw error(exit.ref() + " " + paramRef(method, 0) + " catches " + caught.getClassName() + ", which is not a java.lang.Throwable subtype");
            }
        }
        this.checkNoLocal(exit);
        return exit;
    }

    private void checkNoLocal(Handler handler) {
        if (!this.callSite && handler.localStart < handler.carryStart) {
            throw error(handler.ref() + " " + paramRef(handler.method, handler.localStart) + " is @Surround.Local, which only applies when @Surround.at selects a call");
        }
    }

    private void checkArgPrefix(Handler handler, Type[] expected, String against) {
        final Type[] params = handler.params;
        if (handler.localStart - handler.leading > expected.length) {
            throw error(handler.ref() + " has more parameters than " + against);
        }
        for (int i = handler.leading, arg = 0; i < handler.localStart; i++, arg++) {
            if (params[i].equals(expected[arg]) || this.callSite && isAssignable(params[i], expected[arg])) {
                continue;
            }
            throw error(handler.ref() + " " + paramRef(handler.method, i) + " is " + params[i].getClassName() + ", which does not match the " + expected[arg].getClassName() + " operand of " + against);
        }
    }

    private static boolean isAssignable(Type param, Type operand) {
        if (param.getSort() != Type.OBJECT || operand.getSort() != Type.OBJECT && operand.getSort() != Type.ARRAY) {
            return false;
        }
        if (SurroundSpec.OBJECT.equals(param.getInternalName())) {
            return true;
        }
        if (operand.getSort() != Type.OBJECT) {
            return false;
        }
        final ClassInfo info = ClassInfo.forName(operand.getInternalName());
        return info != null && info.findSuperClass(param.getInternalName(), Traversal.ALL, true) != null;
    }

    private void sortMostSpecificFirst(Handler[] exits) {
        for (int k = 1; k < exits.length && exits[k].role == Role.CATCH; k++) {
            final Handler exit = exits[k];
            final Type caught = exit.caught();
            int at = k;
            for (int i = 0; i < k; i++) {
                final Type other = exits[i].caught();
                if (caught.equals(other)) {
                    throw error(exit.ref() + " is a second @Surround.Catch for " + caught.getClassName() + this.forId() + "; each type may be caught once");
                }
                if (at == k && extendsClass(caught, other.getInternalName())) {
                    at = i;
                }
            }
            System.arraycopy(exits, at, exits, at + 1, k - at);
            exits[at] = exit;
        }
    }

    private static boolean extendsClass(Type type, String superName) {
        final ClassInfo info = ClassInfo.forName(type.getInternalName());
        return info != null && info.hasSuperClass(superName);
    }

    private MethodNode own(MethodNode exit) {
        final MethodNode method = Bytecode.findMethod(this.classNode, exit.name, exit.desc);
        if (method == null) {
            throw new IllegalStateException(exit.name + exit.desc + " is missing from " + this.classNode.name);
        }
        if (!this.mixin.getClassName().equals(SurroundSpec.mergedBy(method))) {
            throw error(exitRef(exit) + " collides with " + exit.name + exit.desc + " in " + this.classNode.name + ", which is " + SurroundSpec.describeOwner(method));
        }
        return method;
    }

    private String forId() {
        return this.id.isEmpty() ? "" : " for id '" + this.id + "'";
    }

    private static String describeId(String id) {
        return id.isEmpty() ? "the default id" : "id '" + id + "'";
    }

    static String noSurroundForId(String id, Collection<String> declared) {
        final StringJoiner ids = new StringJoiner(", ", "; declared ids are ", "").setEmptyValue("; this mixin has no @Surround at all");
        for (String other : declared) {
            ids.add(describeId(other));
        }
        return "refers to " + describeId(id) + " but no @Surround in this mixin declares it" + ids;
    }

    private InvalidInjectionException error(String message) {
        return new InvalidInjectionException(this.info, "@Surround " + this.info.getMethodName() + " " + message);
    }
}
