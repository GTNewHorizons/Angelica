package com.gtnewhorizons.angelica.experimental.surround.integration.mixinextras;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.Callee;
import com.gtnewhorizons.angelica.experimental.surround.integration.WrapTarget;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget.TWICE;

@Mixin(value = WrapTarget.class, remap = false)
public class MixinWrapTarget {

    @Surround(method = "wrapped", id = "wrapped")
    private void surround$enterWrapped(int a) {
        Trace.add("entry " + a);
    }

    @Surround.Finally("wrapped")
    private void surround$exitWrapped() {
        Trace.add("finally");
    }

    @WrapMethod(method = "wrapped")
    private int wrapWrapped(int a, Operation<Integer> original) {
        Trace.add("wrap-before " + a);
        final int result = original.call(a);
        Trace.add("wrap-after " + result);
        return result;
    }

    @WrapOperation(method = "originalOperands", at = @At(value = "INVOKE", target = TWICE))
    private int wrapOperation$shiftArgument(Callee callee, int a, Operation<Integer> original) {
        Trace.add("wrap " + a);
        return original.call(callee, a + 100);
    }

    @Surround(method = "originalOperands", at = @At(value = "INVOKE", target = TWICE), id = "originalOperands")
    private void surround$enterOriginalOperands(Callee callee, int a) {
        Trace.add("enter " + a);
    }

    @Surround.Finally("originalOperands")
    private void surround$exitOriginalOperands(Callee callee, int a) {
        Trace.add("exit " + a);
    }

    @WrapMethod(method = "shifted")
    private int wrapShifted(int a, Operation<Integer> original, @Share("seen") LocalIntRef seen) {
        seen.set(a);
        Trace.add("wrap " + a);
        return original.call(a);
    }

    @Surround(method = "shifted", at = @At(value = "INVOKE", target = TWICE), id = "shifted")
    private void surround$enterShifted(Callee callee, int value, @Surround.Local(ordinal = 1) int local, @Surround.Local String label) {
        Trace.add("enterShifted " + value + " " + local + " " + label);
    }

    @Surround.Finally("shifted")
    private void surround$exitShifted(@Surround.Local(name = "local") int local, @Surround.Local String label) {
        Trace.add("exitShifted " + local + " " + label);
    }
}
