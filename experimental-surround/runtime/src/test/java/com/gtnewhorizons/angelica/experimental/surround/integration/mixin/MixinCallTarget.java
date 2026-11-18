package com.gtnewhorizons.angelica.experimental.surround.integration.mixin;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Callee;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Entity;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Render;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.IntUnaryOperator;

import static com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.CALLEE;
import static com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.TWICE;

@Mixin(value = CallTarget.class, remap = false)
public class MixinCallTarget {

    private static final String WIDE = CALLEE + "wide(JD)J";
    private static final String EXPLODE = CALLEE + "explode(I)I";
    private static final String MAYBE_BOOM = CALLEE + "maybeBoom(I)V";
    private static final String DO_RENDER = "Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Render;" + "doRender(Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget$Entity;DDDFF)V";
    private static final long QUEUED = -1L;

    @Surround(method = "staticTarget", at = @At(value = "INVOKE", target = CALLEE + "twiceStatic(I)I"), id = "staticTarget")
    private static void surround$enterStaticTarget(int a) {
        @Surround.Carry int doubled = a * 2;
        Trace.add("enterStaticTarget " + a);
    }

    @Surround.Finally("staticTarget")
    private static void surround$exitStaticTarget(int a, @Surround.Carry int doubled) {
        Trace.add("exitStaticTarget " + a + " " + doubled);
    }

    @Surround(method = "interfaceCall", at = @At(value = "INVOKE", target = "Ljava/util/function/IntUnaryOperator;applyAsInt(I)I"), id = "interface")
    private void surround$enterInterface(IntUnaryOperator op, int a) {
        Trace.add("enterInterface " + a + " " + (op instanceof Callee));
    }

    @Surround.Finally("interface")
    private void surround$exitInterface(Object op) {
        Trace.add("exitInterface " + (op instanceof Callee));
    }

    @Surround(method = "privateCall", at = @At(value = "INVOKE", target = "Lcom/gtnewhorizons/angelica/experimental/surround/integration/CallTarget;priv(I)I"), id = "private")
    private void surround$enterPrivate(CallTarget self, int a) {
        Trace.add("enterPrivate " + (self == (Object) this) + " " + a);
    }

    @Surround.Finally("private")
    private void surround$exitPrivate(CallTarget self, int a) {
        Trace.add("exitPrivate " + (self == (Object) this) + " " + a);
    }

    @Surround(method = "all", at = @At(value = "INVOKE", target = TWICE), id = "all", require = 3)
    private void surround$enterAll(Callee callee, int a) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterAll " + a + " " + token);
    }

    @Surround.Finally("all")
    private void surround$exitAll(Callee callee, int a, @Surround.Carry long token) {
        Trace.add("exitAll " + a + " " + token);
    }

    @Surround(method = "sliced", at = @At(value = "INVOKE", target = TWICE), slice = @Slice(from = @At(value = "INVOKE", target = CALLEE + "marker()V")), id = "sliced", require = 1, allow = 1)
    private void surround$enterSliced(Callee callee, int a) {
        Trace.add("enterSliced " + a);
    }

    @Surround.Finally("sliced")
    private void surround$exitSliced(Callee callee, int a) {
        Trace.add("exitSliced " + a);
    }

    @Surround(method = "throwingEntry", at = @At(value = "INVOKE", target = TWICE), id = "throwingEntry")
    private void surround$enterThrowingEntry(Callee callee, int a) {
        Trace.add("enterThrowingEntry " + a);
        if (a > 0) {
            throw new IllegalArgumentException("entry");
        }
    }

    @Surround.Catch("throwingEntry")
    private void surround$caughtThrowingEntry(Throwable error) {
        Trace.add("caughtThrowingEntry");
    }

    @Surround.Finally("throwingEntry")
    private void surround$exitThrowingEntry() {
        Trace.add("exitThrowingEntry");
    }

    @Inject(method = "composed", at = @At(value = "INVOKE", target = TWICE))
    private void surround$before(int a, CallbackInfoReturnable<Integer> cir) {
        Trace.add("before " + a);
    }

    @Inject(method = "composed", at = @At(value = "INVOKE", target = TWICE, shift = At.Shift.AFTER))
    private void surround$after(int a, CallbackInfoReturnable<Integer> cir) {
        Trace.add("after " + a);
    }

    @ModifyArg(method = "composed", at = @At(value = "INVOKE", target = TWICE))
    private int surround$plusHundred(int a) {
        Trace.add("modifyArg " + a);
        return a + 100;
    }

    @Surround(method = "composed", at = @At(value = "INVOKE", target = TWICE), id = "composed")
    private void surround$enterComposed(Callee callee, int a) {
        Trace.add("enterComposed " + a);
    }

    @Surround.Finally("composed")
    private void surround$exitComposed(Callee callee, int a) {
        Trace.add("exitComposed " + a);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = DO_RENDER))
    private void surround$redirectDoRender(Render render, Entity entity, double x, double y, double z, float yaw, float partial) {
        Trace.add("redirect " + entity.name);
        render.doRender(entity, x, y, z, yaw, partial);
    }

    @Surround(method = "render", at = @At(value = "INVOKE", target = DO_RENDER), id = "render", require = 1)
    private void surround$enterDoRender(Render render, Entity entity, double x, double y, double z, float yaw, float partial) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterDoRender " + entity.name + " " + token);
    }

    @Surround.Catch("render")
    private void surround$caughtDoRender(Throwable error, Render render, Entity entity, @Surround.Carry long token) {
        Trace.add("caughtDoRender " + entity.name + " " + error.getMessage() + " " + token);
    }

    @Surround.Finally("render")
    private void surround$exitDoRender(Render render, Entity entity, @Surround.Carry long token) {
        Trace.add("exitDoRender " + entity.name + " " + token);
    }

    @Surround(method = "byName", at = @At(value = "INVOKE", target = TWICE), id = "byName")
    private void surround$enterByName(@Surround.Local(name = "second") int second) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterByName " + second + " " + token);
    }

    @Surround.Finally("byName")
    private void surround$exitByName(Callee callee, int first, @Surround.Local(name = "second") int second, @Surround.Carry long token) {
        Trace.add("exitByName " + first + " " + second + " " + token);
    }

    @Surround(method = "wideLocals", at = @At(value = "INVOKE", target = WIDE), id = "wideLocals")
    private void surround$enterWideLocals(Callee callee, long a, double b, @Surround.Local long big, @Surround.Local double half) {
        Trace.add("enterWideLocals " + big + " " + half);
    }

    @Surround.Finally("wideLocals")
    private void surround$exitWideLocals(@Surround.Local(index = 4) double half, @Surround.Local(index = 2) long big) {
        Trace.add("exitWideLocals " + half + " " + big);
    }

    @Surround(method = "valueBelow", at = @At(value = "INVOKE", target = EXPLODE), id = "valueBelow")
    private void surround$enterValueBelow(Callee callee, int a) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterValueBelow " + a + " " + token);
    }

    @Surround.Catch(value = "valueBelow", handle = true)
    private int surround$handleValueBelow(IllegalStateException error, Callee callee, int a, @Surround.Carry long token) {
        Trace.add("handleValueBelow " + a + " " + token);
        return 7;
    }

    @Surround.Finally("valueBelow")
    private void surround$exitValueBelow(Callee callee, int a, @Surround.Carry long token) {
        Trace.add("exitValueBelow " + a + " " + token);
    }

    @Surround(method = "valueSkip", at = @At(value = "INVOKE", target = TWICE), id = "valueSkip")
    private void surround$enterValueSkip(Callee callee, int a) {
        @Surround.Carry long token = Trace.nextToken();
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterValueSkip " + a + " " + token);
    }

    @Surround.Skipped("valueSkip")
    private int surround$skippedValueSkip(Callee callee, int a, @Surround.Carry long token) {
        Trace.add("skippedValueSkip " + a + " " + token);
        return a * 100;
    }

    @Surround.Finally("valueSkip")
    private void surround$exitValueSkip(@Surround.Carry long token) {
        Trace.add("exitValueSkip " + token);
    }

    @Surround(method = "observed", at = @At(value = "INVOKE", target = MAYBE_BOOM), id = "observed")
    private void surround$enterObserved(Callee callee, int a) {
        Trace.add("enterObserved " + a);
    }

    @Surround.Return("observed")
    private void surround$observe(Callee callee, int a) {
        Trace.add("observe " + a);
    }

    @Surround.Catch(value = "observed", handle = true)
    private void surround$handleObserved(IllegalStateException error, Callee callee, int a) {
        Trace.add("handleObserved " + a);
    }

    @Surround.Finally("observed")
    private void surround$exitObserved(Callee callee, int a) {
        Trace.add("exitObserved " + a);
    }

    @Surround(method = "returnAndHandle", at = @At(value = "INVOKE", target = EXPLODE), id = "returnAndHandle")
    private void surround$enterReturnAndHandle(Callee callee, int a) {
        Trace.add("enterReturnAndHandle " + a);
    }

    @Surround.Return("returnAndHandle")
    private int surround$returnReturnAndHandle(int result) {
        Trace.add("returnReturnAndHandle " + result);
        return result * 10;
    }

    @Surround.Catch(value = "returnAndHandle", handle = true)
    private int surround$handleReturnAndHandle(IllegalStateException error) {
        Trace.add("handleReturnAndHandle");
        return 7;
    }

    @Surround.Finally("returnAndHandle")
    private void surround$exitReturnAndHandle() {
        Trace.add("exitReturnAndHandle");
    }

    @Surround(method = "skipFinallyCatch", at = @At(value = "INVOKE", target = EXPLODE), id = "sfc")
    private void surround$enterSfc(Callee callee, int a) {
        @Surround.Skip boolean skip = a <= 0;
        Trace.add("enterSfc " + a);
    }

    @Surround.Skipped("sfc")
    private int surround$skippedSfc(Callee callee, int a) {
        Trace.add("skippedSfc " + a);
        if (a == 0) {
            throw new IllegalStateException("zero");
        }
        return -a;
    }

    @Surround.Return("sfc")
    private int surround$returnSfc(int result, Callee callee, int a) {
        Trace.add("returnSfc " + result);
        return result + 1000;
    }

    @Surround.Catch("sfc")
    private void surround$caughtSfc(IllegalStateException error, Callee callee, int a) {
        Trace.add("caughtSfc " + error.getMessage());
    }

    @Surround.Finally("sfc")
    private void surround$exitSfc(Callee callee, int a) {
        Trace.add("exitSfc " + a);
    }

    @Surround(method = "nestedSkip", at = @At(value = "INVOKE", target = TWICE), id = "nestedInner")
    private void surround$enterNestedInner(Callee callee, int a) {
        Trace.add("enterNestedInner " + a);
    }

    @Surround.Return("nestedInner")
    private int surround$returnNestedInner(int result) {
        Trace.add("returnNestedInner " + result);
        return result + 1;
    }

    @Surround.Finally("nestedInner")
    private void surround$exitNestedInner(Callee callee, int a) {
        Trace.add("exitNestedInner " + a);
    }

    @Surround(method = "nestedSkip", at = @At(value = "INVOKE", target = TWICE), id = "nestedOuter")
    private void surround$enterNestedOuter(Callee callee, int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterNestedOuter " + a);
    }

    @Surround.Skipped("nestedOuter")
    private int surround$skippedNestedOuter() {
        Trace.add("skippedNestedOuter");
        return -1;
    }

    @Surround.Return("nestedOuter")
    private int surround$returnNestedOuter(int result) {
        Trace.add("returnNestedOuter " + result);
        return result * 10;
    }

    @Surround.Finally("nestedOuter")
    private void surround$exitNestedOuter(Callee callee, int a) {
        Trace.add("exitNestedOuter " + a);
    }

    @Redirect(method = "redirectedSkip", at = @At(value = "INVOKE", target = TWICE))
    private int surround$redirectSkip(Callee callee, int a) {
        Trace.add("redirectSkip " + a);
        return callee.twice(a) + 1;
    }

    @Surround(method = "redirectedSkip", at = @At(value = "INVOKE", target = TWICE), id = "redirectedSkip")
    private void surround$enterRedirectedSkip(Callee callee, int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterRedirectedSkip " + a);
    }

    @Surround.Skipped("redirectedSkip")
    private int surround$skippedRedirectedSkip(Callee callee, int a) {
        return 0;
    }

    @Surround.Finally("redirectedSkip")
    private void surround$exitRedirectedSkip(Callee callee, int a) {
        Trace.add("exitRedirectedSkip " + a);
    }

    @Unique
    private static long surround$part(int a) {
        Trace.add("part " + a);
        return a < 0 ? QUEUED : a;
    }

    @Surround(method = "helperSkip", at = @At(value = "INVOKE", target = TWICE), id = "helper")
    private void surround$enterHelper(Callee callee, int a) {
        @Surround.Carry final long part = surround$part(a);
        @Surround.Skip final boolean queued = part == QUEUED;
        Trace.add("enterHelper " + part);
    }

    @Surround.Skipped("helper")
    private int surround$skippedHelper(Callee callee, int a, @Surround.Carry long part) {
        Trace.add("skippedHelper");
        return (int) part;
    }

    @Surround.Finally("helper")
    private void surround$exitHelper(@Surround.Carry long part) {
        Trace.add("exitHelper " + part);
    }

    @Surround(method = "wideSkip", at = @At(value = "INVOKE", target = WIDE), id = "wideSkip")
    private void surround$enterWideSkip(Callee callee, long a, double b) {
        @Surround.Carry double halved = b / 2;
        @Surround.Skip int skip = a < 0 ? 1 : 0;
        Trace.add("enterWideSkip " + a + " " + b);
    }

    @Surround.Skipped("wideSkip")
    private long surround$skippedWideSkip(Callee callee, long a, @Surround.Carry double halved) {
        Trace.add("skippedWideSkip " + a + " " + halved);
        return a * 10L;
    }

    @Surround.Return("wideSkip")
    private long surround$returnWideSkip(long result, Callee callee, long a, double b, @Surround.Carry double halved) {
        Trace.add("returnWideSkip " + result + " " + halved);
        return result + 100L;
    }

    @Surround.Finally("wideSkip")
    private void surround$exitWideSkip(Callee callee, long a, double b) {
        Trace.add("exitWideSkip " + a + " " + b);
    }

    @Surround(method = "entryOnlySkip", at = @At(value = "INVOKE", target = CALLEE + "render(I)V"), id = "entryOnlySkip")
    private void surround$enterOnlySkip(Callee callee, int a) {
        @Surround.Skip boolean skip = a > 0;
        Trace.add("enterOnlySkip " + a);
    }

    @Surround(method = "entryOnlyWhole", id = "entryOnlyWhole")
    private void surround$enterOnlyWhole() {
        Trace.add("enterOnlyWhole");
    }

    @Surround(method = "skipBelowRestored", at = @At(value = "INVOKE", target = TWICE), id = "skipBelowRestored")
    private void surround$enterSkipBelowRestored(Callee callee, int a) {
        @Surround.Skip boolean skip = a < 0;
        try {
            if (a > 100) {
                throw new IllegalArgumentException("big");
            }
            Trace.add("enterSkipBelowRestored " + a);
        } catch (IllegalArgumentException e) {
            Trace.add("entryCaughtSkipBelowRestored " + a);
        }
    }

    @Surround.Skipped("skipBelowRestored")
    private int surround$skippedSkipBelowRestored(Callee callee, int a) {
        Trace.add("skippedSkipBelowRestored " + a);
        return a * 10;
    }

    @Surround.Finally("skipBelowRestored")
    private void surround$exitSkipBelowRestored(Callee callee, int a) {
        Trace.add("exitSkipBelowRestored " + a);
    }

    @Surround(method = "innerSkipOuterReturn", at = @At(value = "INVOKE", target = TWICE), id = "innerSkip")
    private void surround$enterInnerSkip(Callee callee, int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterInnerSkip " + a);
    }

    @Surround.Skipped("innerSkip")
    private int surround$skippedInnerSkip(Callee callee, int a) {
        Trace.add("skippedInnerSkip " + a);
        return a * 100;
    }

    @Surround.Finally("innerSkip")
    private void surround$exitInnerSkip(Callee callee, int a) {
        Trace.add("exitInnerSkip " + a);
    }

    @Surround(method = "innerSkipOuterReturn", at = @At(value = "INVOKE", target = TWICE), id = "outerReturn")
    private void surround$enterOuterReturn(Callee callee, int a) {
        Trace.add("enterOuterReturn " + a);
    }

    @Surround.Return("outerReturn")
    private int surround$returnOuterReturn(int result) {
        Trace.add("returnOuterReturn " + result);
        return result + 1;
    }

    @Surround.Finally("outerReturn")
    private void surround$exitOuterReturn(Callee callee, int a) {
        Trace.add("exitOuterReturn " + a);
    }

    @Surround(method = "skipInsideNew", at = @At(value = "INVOKE", target = TWICE), id = "skipInsideNew")
    private void surround$enterSkipInsideNew(Callee callee, int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterSkipInsideNew " + a);
    }

    @Surround.Skipped("skipInsideNew")
    private int surround$skippedSkipInsideNew(Callee callee, int a) {
        Trace.add("skippedSkipInsideNew " + a);
        return a * 10;
    }

    @Surround.Catch("skipInsideNew")
    private void surround$caughtSkipInsideNew(Throwable error) {
        Trace.add("caughtSkipInsideNew");
    }

    @Surround.Finally("skipInsideNew")
    private void surround$exitSkipInsideNew(Callee callee, int a) {
        Trace.add("exitSkipInsideNew " + a);
    }

    @Surround(method = "movedBody", id = "movedWhole")
    private void surround$enterMovedWhole(int a) {
        Trace.add("enterMovedWhole " + a);
    }

    @Surround.Finally("movedWhole")
    private void surround$exitMovedWhole(int a) {
        Trace.add("exitMovedWhole " + a);
    }

    @Surround(method = "movedBody", at = @At(value = "INVOKE", target = TWICE), id = "movedCall")
    private void surround$enterMovedCall(Callee callee, int b, @Surround.Local(argsOnly = true) int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterMovedCall " + b + " " + a);
    }

    @Surround.Skipped("movedCall")
    private int surround$skippedMovedCall(Callee callee, int b, @Surround.Local(argsOnly = true) int a) {
        Trace.add("skippedMovedCall " + b + " " + a);
        return b * 10;
    }

    @Surround.Finally("movedCall")
    private void surround$exitMovedCall(Callee callee, int b, @Surround.Local(argsOnly = true) int a) {
        Trace.add("exitMovedCall " + b + " " + a);
    }
}
