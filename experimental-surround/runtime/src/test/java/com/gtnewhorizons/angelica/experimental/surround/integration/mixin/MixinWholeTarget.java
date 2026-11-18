package com.gtnewhorizons.angelica.experimental.surround.integration.mixin;

import com.gtnewhorizons.angelica.experimental.surround.Surround;
import com.gtnewhorizons.angelica.experimental.surround.integration.WholeTarget;
import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WholeTarget.class, remap = false)
public class MixinWholeTarget {

    @Surround(method = "compute(I)I", id = "compute")
    private void surround$enter(int a) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enter " + a + " " + token);
    }

    @Surround.Finally("compute")
    private void surround$exit(@Surround.Carry long token) {
        Trace.add("exit " + token);
    }

    @Inject(method = "compute(I)I", at = @At("HEAD"))
    private void surround$head(int a, CallbackInfoReturnable<Integer> cir) {
        Trace.add("head " + a);
    }

    @Inject(method = "compute(I)I", at = @At("RETURN"))
    private void surround$tail(int a, CallbackInfoReturnable<Integer> cir) {
        Trace.add("tail " + cir.getReturnValue());
    }

    @Surround(method = "boom()V", id = "boom")
    private void surround$enterBoom() {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterBoom " + token);
    }

    @Surround.Catch("boom")
    private void surround$caughtBoomBroad(Throwable e, @Surround.Carry long token) {
        Trace.add("caughtBroad " + token + " " + e.getMessage());
    }

    @Surround.Catch("boom")
    private void surround$caughtBoom(IllegalStateException e, @Surround.Carry long token) {
        Trace.add("caught " + token + " " + e.getMessage());
    }

    @Surround.Finally("boom")
    private void surround$exitBoom(@Surround.Carry long token) {
        Trace.add("exitBoom " + token);
    }

    @Inject(method = "boom()V", at = @At("HEAD"))
    private void surround$boomHead(CallbackInfo ci) {
        Trace.add("boomHead");
    }

    @Surround(method = {"alpha()V", "beta()V"}, id = "both")
    private void surround$enterBoth() {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enterBoth " + token);
    }

    @Surround.Finally("both")
    private void surround$exitBoth(@Surround.Carry long token) {
        Trace.add("exitBoth " + token);
    }

    @Surround(method = "lockedInstance(I)I", id = "instance")
    private void surround$enterInstance(int a) {
        Trace.add("enterInstance " + Thread.holdsLock(this));
    }

    @Surround.Finally("instance")
    private void surround$exitInstance() {
        Trace.add("exitInstance " + Thread.holdsLock(this));
    }

    @Surround(method = "run()V", id = "run")
    private void surround$enterRun() {
        Trace.add("enter");
    }

    @Unique
    @Surround.Finally("run")
    private void exit() {
        Trace.add("handler exit");
    }

    @Surround(method = "priority(I)I", id = "priority")
    private void surround$enterLow(int a) {
        Trace.add("enterLow");
    }

    @Surround.Return("priority")
    private int surround$returnLow(int result) {
        Trace.add("returnLow " + result);
        return result + 1;
    }

    @Surround.Finally("priority")
    private void surround$exitLow() {
        Trace.add("exitLow");
    }

    @Surround(method = "branching(I)I", id = "branching")
    private void surround$enterBranching(int a) {
        @Surround.Carry String sign = a < 0 ? "negative" : "positive";
        Trace.add("enterBranching " + sign);
    }

    @Surround.Finally("branching")
    private void surround$exitBranching(int a, @Surround.Carry String sign) {
        Trace.add("exitBranching " + sign);
    }

    @Surround(method = "skippedVoid(I)V", id = "skippedVoid")
    private void surround$enterSkippedVoid(int a) {
        @Surround.Skip boolean skip = a < 0;
        Trace.add("enterSkippedVoid " + a);
    }

    @Surround.Finally("skippedVoid")
    private void surround$exitSkippedVoid() {
        Trace.add("exitSkippedVoid");
    }

    @Surround(method = "reentrant(I)I", id = "reentrant")
    private void surround$enterReentrant(int a) {
        @Surround.Carry int direct = 0;
        @Surround.Skip boolean skip = false;
        if (!WholeTarget.guard) {
            WholeTarget.guard = true;
            direct = ((WholeTarget) (Object) this).reentrant(a + 10);
            WholeTarget.guard = false;
            skip = true;
        }
        Trace.add("enterReentrant " + a + " " + skip);
    }

    @Surround.Skipped("reentrant")
    private int surround$skippedReentrant(int a, @Surround.Carry int direct) {
        return direct * 2;
    }

    @Surround.Finally("reentrant")
    private void surround$exitReentrant(int a) {
        Trace.add("exitReentrant " + a);
    }

    @Surround(method = "skipAndReturn(Ljava/lang/String;)Ljava/lang/String;", id = "skipAndReturn")
    private void surround$enterSkipAndReturn(String a) {
        @Surround.Skip boolean skip = a.isEmpty();
        Trace.add("enterSkipAndReturn " + a);
    }

    @Surround.Skipped("skipAndReturn")
    private String surround$skippedSkipAndReturn(String a) {
        Trace.add("skippedSkipAndReturn");
        return null;
    }

    @Surround.Return("skipAndReturn")
    private String surround$returnSkipAndReturn(String result, String a) {
        Trace.add("returnSkipAndReturn " + result);
        if (result.equals("x!")) {
            throw new IllegalStateException("return");
        }
        return result + "?";
    }

    @Surround.Catch("skipAndReturn")
    private void surround$caughtSkipAndReturn(Throwable e) {
        Trace.add("caughtSkipAndReturn " + e.getMessage());
    }

    @Surround.Finally("skipAndReturn")
    private void surround$exitSkipAndReturn(String a) {
        Trace.add("exitSkipAndReturn " + a);
    }

    @Surround(method = "entryCatches(I)I", id = "entryCatches")
    private static void surround$enterEntryCatches(int a) {
        @Surround.Carry final boolean direct = !WholeTarget.guard;
        if (direct) {
            WholeTarget.guard = true;
            try {
                WholeTarget.entryCatches(a);
            } catch (Throwable t) {
                WholeTarget.guard = false;
                Trace.add("entryRethrow " + t.getMessage());
                throw t;
            }
            Trace.add("enterEntryCatches " + a);
        }
    }

    @Surround.Finally("entryCatches")
    private static void surround$exitEntryCatches(@Surround.Carry boolean direct) {
        if (direct) {
            WholeTarget.guard = false;
        }
        Trace.add("exitEntryCatches " + direct);
    }

    @Surround(method = "wideBody(DJ)D", id = "wideBody")
    private static void surround$enterWideBody(double a, long b) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enter " + token);
    }

    @Surround.Finally("wideBody")
    private static void surround$exitWideBody(@Surround.Carry long token) {
        Trace.add("exit " + token);
    }

    @Surround(method = "throwingEntry(I)V", id = "throwingEntry")
    private void surround$enterThrowingEntry(int a) {
        Trace.add("enterThrows " + a);
        if (a > 0) {
            throw new IllegalArgumentException("entry failed");
        }
    }

    @Surround.Finally("throwingEntry")
    private void surround$exitThrowingEntry() {
        Trace.add("exitNothing");
    }

    @Surround(method = "throwingFinally(II)I", id = "throwingFinally")
    private void surround$enterThrowingFinally(int a, int b) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enter " + token);
    }

    @Surround.Finally("throwingFinally")
    private void surround$exitThrowingFinally(@Surround.Carry long token) {
        Trace.add("exitThrows " + token);
        throw new UnsupportedOperationException("finally failed");
    }

    @Surround(method = "caught(I)I", id = "caught")
    private void surround$enterCaught(int a) {
        @Surround.Carry long token = Trace.nextToken();
        Trace.add("enter " + token);
    }

    @Surround.Finally("caught")
    private void surround$exitCaught(@Surround.Carry long token) {
        Trace.add("exit " + token);
    }

    @Surround(method = "carryThree(I)I", id = "carryThree")
    private void surround$enterCarryThree(int a) {
        @Surround.Carry int count = a + 1;
        @Surround.Carry long stamp = 100L + a;
        @Surround.Carry String label = "L" + a;
        Trace.add("enter " + count + " " + stamp + " " + label);
    }

    @Surround.Finally("carryThree")
    private void surround$exitCarryThree(@Surround.Carry String label, @Surround.Carry long stamp, @Surround.Carry int count) {
        Trace.add("exit " + count + " " + stamp + " " + label);
    }

    @Surround(method = "carryUninitialized(I)I", id = "carryUninitialized")
    private void surround$enterCarryUninitialized(int a) {
        @Surround.Carry String label = new String(a > 0 ? "pos" : "neg");
        Trace.add("enter " + label);
    }

    @Surround.Finally("carryUninitialized")
    private void surround$exitCarryUninitialized(@Surround.Carry String label) {
        Trace.add("exit " + label);
    }

    @Surround(method = "carryGuarded(I)I", id = "carryGuarded")
    private void surround$enterCarryGuarded(int a) {
        @Surround.Carry int state = 0;
        try {
            state = WholeTarget.risky(a);
        } catch (IllegalArgumentException e) {
            state = -1;
            Trace.add("recovered");
        }
        Trace.add("enter " + state);
    }

    @Surround.Finally("carryGuarded")
    private void surround$exitCarryGuarded(@Surround.Carry int flag) {
        Trace.add("exit " + flag);
    }

    @Surround(method = "carrySplitAtEnd(I)I", id = "carrySplitAtEnd")
    private void surround$enterCarrySplitAtEnd(int a) {
        @Surround.Carry int flag;
        if (a > 0) {
            flag = 1;
        } else {
            flag = 2;
        }
    }

    @Surround.Finally("carrySplitAtEnd")
    private void surround$exitCarrySplitAtEnd(@Surround.Carry int flag) {
        Trace.add("exit " + flag);
    }

    @Surround(method = "carryWideBeforeFrame(I)I", id = "carryWideBeforeFrame")
    private void surround$enterCarryWideBeforeFrame(int a) {
        @Surround.Carry long stamp = a;
        @Surround.Carry int count = a + 1;
        if (a > 0) {
            Trace.add("positive");
        }
    }

    @Surround.Finally("carryWideBeforeFrame")
    private void surround$exitCarryWideBeforeFrame(@Surround.Carry long stamp, @Surround.Carry int count) {
        Trace.add("exit " + stamp + " " + count);
    }

    @Surround(method = "carryArrays(I)I", id = "carryArrays")
    private void surround$enterCarryArrays(int a) {
        @Surround.Carry("elements") int[] elements = {a};
        String @Surround.Carry("names") [] names = {"n" + a};
    }

    @Surround.Finally("carryArrays")
    private void surround$exitCarryArrays(@Surround.Carry("names") String[] names, @Surround.Carry("elements") int[] elements) {
        Trace.add("exit " + names[0] + " " + elements[0]);
    }

    @Surround(method = "reused(I)I", id = "reused")
    private void surround$enterReused(int a) {
        {
            int tmp = a + 5;
            Trace.add("tmp " + tmp);
        }
        @Surround.Carry long prev = a + 7;
        Trace.add("enter " + prev);
    }

    @Surround.Finally("reused")
    private void surround$exitPrev(@Surround.Carry long prev) {
        Trace.add("exit " + prev);
    }

    @Surround(method = "throwWide(I)V", id = "throwWide")
    private void surround$enterThrowWide(int a) {
        @Surround.Carry long first = a + 1L;
        String scratch = "s" + a;
        @Surround.Carry double second = a / 2.0d;
        @Surround.Carry int third = a;
        Trace.add("enter " + first + " " + second + " " + third + " " + scratch);
    }

    @Surround.Catch("throwWide")
    private void surround$caughtThrowWide(Throwable e, @Surround.Carry long first, @Surround.Carry double second, @Surround.Carry int third) {
        Trace.add("caught " + first + " " + second + " " + third);
    }

    @Surround.Finally("throwWide")
    private void surround$exitThrowWide(@Surround.Carry double second, @Surround.Carry int third, @Surround.Carry long first) {
        Trace.add("exit " + first + " " + second + " " + third);
    }

    @Surround(method = "throwObject(I)V", id = "throwObject")
    private void surround$enterThrowObject(int a) {
        @Surround.Carry Object prev = WholeTarget.asObject("o" + a);
        Trace.add("enter " + prev);
    }

    @Surround.Catch("throwObject")
    private void surround$caughtThrowObject(IllegalStateException e, @Surround.Carry String prev) {
        Trace.add("caught " + prev.length() + " " + prev);
        throw new UnsupportedOperationException("replaced", e);
    }

    @Surround.Finally("throwObject")
    private void surround$exitThrowObject(@Surround.Carry String prev) {
        Trace.add("exit " + prev.length() + " " + prev);
    }
}
