package com.gtnewhorizons.angelica.experimental.surround;

import org.spongepowered.asm.lib.tree.AnnotationNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;
import org.spongepowered.asm.util.Annotations;

import java.util.List;

@InjectionInfo.AnnotationType(Surround.class)
@InjectionInfo.HandlerPrefix("surround")
public final class SurroundInjectionInfo extends InjectionInfo {

    boolean callSite;

    public SurroundInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }

    @Override
    protected void readInjectionPoints() {
        final List<AnnotationNode> ats = Annotations.<AnnotationNode>getValue(this.annotation, this.atKey, false);
        this.callSite = ats != null && !ats.isEmpty();
        if (this.callSite) {
            super.readInjectionPoints();
            return;
        }
        final List<AnnotationNode> slices = Annotations.<AnnotationNode>getValue(this.annotation, "slice", false);
        if (slices != null && !slices.isEmpty()) {
            throw this.reject("declares @Surround.slice, which only applies when @Surround.at selects a call");
        }
    }

    @Override
    protected void parseInjectionPoints(List<AnnotationNode> ats) {
        if (!this.callSite) {
            this.injectionPoints.add(new SurroundInjectionPoint());
            return;
        }
        for (int i = 0, n = ats.size(); i < n; i++) {
            final AnnotationNode at = ats.get(i);
            final String code = Annotations.<String>getValue(at, "value");
            if (!selectsCall(code)) {
                throw this.reject("has @At(\"" + code + "\"), which does not select a method call");
            }
            if (Annotations.<At.Shift>getValue(at, "shift", At.Shift.class, At.Shift.NONE) != At.Shift.NONE || Annotations.<Integer>getValue(at, "by", Integer.valueOf(0)).intValue() != 0) {
                throw this.reject("has @At(\"" + code + "\") with a non-default shift or by, which moves the match off the call");
            }
        }
        super.parseInjectionPoints(ats);
    }

    private static boolean selectsCall(String atCode) {
        return switch (atCode) {
            case "HEAD", "RETURN", "TAIL", "CTOR_HEAD", "FIELD", "NEW", "CONSTANT", "JUMP", "INVOKE_ASSIGN", "LOAD", "STORE" -> false;
            default -> true;
        };
    }

    private InvalidInjectionException reject(String message) {
        return new InvalidInjectionException(this, "@Surround " + this.getMethodName() + " " + message);
    }

    @Override
    protected Injector parseInjector(AnnotationNode injectAnnotation) {
        return new SurroundInjector(this);
    }
}
