package com.gtnewhorizons.angelica.experimental.surround;

import com.llamalad7.mixinextras.utils.MixinInternals;
import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.util.Annotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

final class SurroundApplicatorExtension implements IExtension {

    private static final String MIXINEXTRAS_PACKAGE = "com.llamalad7.mixinextras.";
    private static final Pattern HANDLER_NAME = Pattern.compile("surround\\$[a-z]{3,}[0-9a-f]{3,}\\$.*");

    private final Map<ITargetClassContext, Pending> pending = new WeakHashMap<>();

    private record Pending(List<CallSiteSpec> calls, List<CallSiteSpec> wholes, Set<String> declarers) {}

    private static final class Holder {

        static final SurroundApplicatorExtension INSTANCE = create();

        private static SurroundApplicatorExtension create() {
            final SurroundApplicatorExtension extension = new SurroundApplicatorExtension();
            // MixinExtras 0.5.0 internal (pinned by UniMixins 0.3.1); recheck on bump
            MixinInternals.registerExtension(extension, false);
            return extension;
        }
    }

    static SurroundApplicatorExtension instance() {
        return Holder.INSTANCE;
    }

    void declare(ITargetClassContext context, String mixin) {
        this.pending(context).declarers.add(mixin);
    }

    void offer(ITargetClassContext context, CallSiteSpec spec) {
        final Pending pending = this.pending(context);
        (spec.node() == null ? pending.wholes : pending.calls).add(spec);
    }

    private Pending pending(ITargetClassContext context) {
        return this.pending.computeIfAbsent(context, key -> new Pending(new ArrayList<>(), new ArrayList<>(), new HashSet<>()));
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
        // MixinExtras 0.5.0 internal (pinned by UniMixins 0.3.1); recheck on bump
        final List<IExtension> active = MixinInternals.getExtensions().getActiveExtensions();
        boolean seen = false;
        for (int i = 0, n = active.size(); i < n; i++) {
            final IExtension extension = active.get(i);
            if (extension == this) {
                seen = true;
            } else if (seen && extension.getClass().getName().startsWith(MIXINEXTRAS_PACKAGE)) {
                MixinInternals.unregisterExtension(this);
                MixinInternals.registerExtension(this, false);
                return;
            }
        }
    }

    @Override
    public void postApply(ITargetClassContext context) {
        final ClassNode targetClass = context.getClassNode();
        final Pending pending = this.pending.remove(context);
        checkHandlersRenamed(targetClass, pending == null ? Set.of() : pending.declarers);
        if (pending == null) {
            return;
        }
        final List<CallSiteSpec> calls = pending.calls;
        for (int i = 0, n = calls.size(); i < n; i++) {
            SurroundCallSite.apply(targetClass, calls.get(i));
        }
        final List<CallSiteSpec> wholes = pending.wholes;
        for (int i = 0, n = wholes.size(); i < n; i++) {
            SurroundCallSite.apply(targetClass, wholes.get(i));
        }
    }

    @Override
    public void export(MixinEnvironment env, String name, boolean force, ClassNode classNode) {
    }

    static void checkHandlersRenamed(ClassNode classNode, Set<String> declarers) {
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode annotation : method.visibleAnnotations) {
                final String desc = annotation.desc;
                if (SurroundSpec.CATCH.equals(desc) || SurroundSpec.FINALLY.equals(desc) || SurroundSpec.SKIPPED.equals(desc) || SurroundSpec.RETURN.equals(desc)) {
                    final String mixin = SurroundSpec.mergedBy(method);
                    if (mixin != null && !declarers.contains(mixin)) {
                        throw new IllegalStateException("@Surround " + SurroundBinding.exitRef(method) + " " + SurroundInjector.noSurroundForId(Annotations.<String>getValue(annotation, "value", ""), Set.of()));
                    }
                } else if (SurroundSpec.SURROUND.equals(desc) && !HANDLER_NAME.matcher(method.name).matches()) {
                    throw new IllegalStateException(method.name + method.desc + " in " + classNode.name + " (" + SurroundSpec.describeOwner(method) + ") carries @Surround but was never parsed as an injector: the InjectionInfo registry lacked @Surround when mixins were applied to this target; SurroundBootstrap.init() must run before any class a @Surround targets is transformed");
                }
            }
        }
    }
}
