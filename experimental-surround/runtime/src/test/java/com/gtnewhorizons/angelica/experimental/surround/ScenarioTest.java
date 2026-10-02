package com.gtnewhorizons.angelica.experimental.surround;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.CALL;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.VOID;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.WHOLE;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.args;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.of;
import static com.gtnewhorizons.angelica.experimental.surround.MixinTestBootstrap.scenario;

class ScenarioTest {

    static Stream<Arguments> scenarios() {
        return Stream.of(
            scenario(WHOLE, "a surround encloses the body and every other injector", "compute", of(7), of(14), "enter 7 1", "head 7", "body 7", "tail 14", "exit 1"),
            scenario(WHOLE, "the narrowest catch handler wins and finally runs when the body throws", "boom", of(args()), of(IllegalStateException.class), "enterBoom 1", "boomHead", "boom", "caught 1 boom", "exitBoom 1"),
            scenario(WHOLE, "an instance monitor covers the handlers and the body", "lockedInstance", of(7), of(14), "enterInstance true", "body true", "exitInstance true"),
            scenario(WHOLE, "a unique exit handler renamed on collision still binds", "run", of(args()), of(VOID), "enter", "run", "target exit", "handler exit"),
            scenario(WHOLE, "a lower priority mixin is the inner surround", "priority", of(5), of(60), "enterHigh", "enterLow", "body 5", "returnLow 5", "exitLow", "returnHigh 6", "exitHigh"),
            scenario(WHOLE, "static wide arguments", "wideBody", of(args(2.5d, 5L)), of(7.5d), "enter 1", "body", "exit 1"),
            scenario(WHOLE, "a throwing entry handler skips the body and the exit handler", "throwingEntry", of(5), of(IllegalArgumentException.class), "enterThrows 5"),
            scenario(WHOLE, "a throwing finally replaces the return value", "throwingFinally", of(args(9, 4)), of(UnsupportedOperationException.class), "enter 1", "gt", "exitThrows 1"),
            scenario(WHOLE, "pre-existing exception handlers still fire", "caught", of(4, -4), of(4, -1), "enter 1", "inner-finally", "exit 1", "enter 2", "caught neg", "inner-finally", "exit 2"),
            scenario(WHOLE, "three kinds bind by kind, not position", "carryThree", of(7), of(14), "enter 8 107 L7", "body 7", "exit 8 107 L7"),
            scenario(WHOLE, "frame naming an unbuilt object", "carryUninitialized", of(5), of(10), "enter pos", "body 5", "exit pos"),
            scenario(WHOLE, "entry handler's own try-catch", "carryGuarded", of(-3), of(-6), "recovered", "enter -1", "body -3", "exit -1"),
            scenario(WHOLE, "assigned in both branches at the end", "carrySplitAtEnd", of(-3), of(-6), "body -3", "exit 2"),
            scenario(WHOLE, "wide carry ahead of an int across a frame", "carryWideBeforeFrame", of(3), of(6), "positive", "body 3", "exit 3 4"),
            scenario(WHOLE, "array locals", "carryArrays", of(3), of(6), "body 3", "exit n3 3"),
            scenario(WHOLE, "widths and an uncarried local between carried ones survive the handler frame", "throwWide", of(3), of(IllegalStateException.class), "enter 4 1.5 3 s3", "body 3", "caught 4 1.5 3", "exit 4 1.5 3"),
            scenario(WHOLE, "a reference carry is cast to each exit handler's type and a throwing catch handler still runs finally", "throwObject", of(3), of(UnsupportedOperationException.class), "enter o3", "body 3", "caught 2 o3", "exit 2 o3"),
            scenario(WHOLE, "a reused slot carries the local's value", "reused", of(3), of(6), "tmp 8", "enter 10", "body 3", "exit 10"),
            scenario(CALL, "a static target takes static handlers around a static callee", "staticTarget", of(4), of(8), "enterStaticTarget 4", "twiceStatic 4", "exitStaticTarget 4 8"),
            scenario(CALL, "an interface callee is typed as the interface or a supertype", "interfaceCall", of(3), of(4), "enterInterface 3 true", "apply 3", "exitInterface true"),
            scenario(CALL, "a private callee's receiver is the target itself", "privateCall", of(3), of(4), "enterPrivate true 3", "priv 3", "exitPrivate true 3"),
            scenario(CALL, "every match is surrounded separately", "all", of(2), of(18), "enterAll 2 1", "twice 2", "exitAll 2 1", "enterAll 3 2", "twice 3", "exitAll 3 2", "enterAll 4 3", "twice 4", "exitAll 4 3"),
            scenario(CALL, "a slice restricts the matches", "sliced", of(2), of(10), "twice 2", "marker", "enterSliced 3", "twice 3", "exitSliced 3"),
            scenario(CALL, "a throwing entry handler runs no exit handler and skips the call", "throwingEntry", of(args()), of(IllegalArgumentException.class), "enterThrowingEntry 1"),
            scenario(CALL, "inject before, modify arg and inject after stay outside", "composed", of(5), of(210), "before 5", "modifyArg 5", "enterComposed 105", "twice 105", "exitComposed 105", "after 5"),
            scenario(WHOLE, "a void target skips without a skipped handler", "skippedVoid", of(-1, 1), of(VOID, VOID), "enterSkippedVoid -1", "exitSkippedVoid", "enterSkippedVoid 1", "skippedVoid 1", "exitSkippedVoid"),
            scenario(WHOLE, "an entry handler may call the target directly and then skip", "reentrant", of(1), of(24), "enterReentrant 11 false", "reentrant 11", "exitReentrant 11", "enterReentrant 1 true", "exitReentrant 1"),
            scenario(WHOLE, "return does not run after a skip and a reference skipped value is returned", "skipAndReturn", of("", "a", "x"), of(null, "a!?", IllegalStateException.class), "enterSkipAndReturn ", "skippedSkipAndReturn", "exitSkipAndReturn ", "enterSkipAndReturn a", "skipAndReturn a", "returnSkipAndReturn a!", "exitSkipAndReturn a", "enterSkipAndReturn x", "skipAndReturn x", "returnSkipAndReturn x!", "exitSkipAndReturn x"),
            scenario(WHOLE, "an entry handler may catch and rethrow around a direct call", "entryCatches", of(3, -1), of(6, IllegalStateException.class), "entryCatches 3", "exitEntryCatches false", "enterEntryCatches 3", "entryCatches 3", "exitEntryCatches true", "entryCatches -1", "exitEntryCatches false", "entryRethrow negative -1"),
            scenario(CALL, "a local is selected by name between operands and carries", "byName", of(3), of(49), "enterByName 23 1", "twice 13", "exitByName 13 23 1"),
            scenario(CALL, "wide locals bind", "wideLocals", of(3), of(31L), "enterWideLocals 30 1.5", "wide 30 1.5", "exitWideLocals 1.5 30"),
            scenario(CALL, "a handling catch replaces the result over the values below", "valueBelow", of(-3, 4), of(67, 84), "enterValueBelow -3 1", "explode -3", "handleValueBelow -3 1", "exitValueBelow -3 1", "enterValueBelow 4 2", "explode 4", "exitValueBelow 4 2"),
            scenario(CALL, "a skipped handler supplies the value of a skipped call", "valueSkip", of(-3, 3), of(-300, 6), "enterValueSkip -3 1", "skippedValueSkip -3 1", "exitValueSkip 1", "enterValueSkip 3 2", "twice 3", "exitValueSkip 2"),
            scenario(CALL, "a void return handler observes and does not run after a handled exception", "observed", of(2, -1), of(VOID, VOID), "enterObserved 2", "maybeBoom 2", "observe 2", "exitObserved 2", "enterObserved -1", "maybeBoom -1", "handleObserved -1", "exitObserved -1"),
            scenario(CALL, "a handled value bypasses the return handler", "returnAndHandle", of(4, -1), of(80, 7), "enterReturnAndHandle 4", "explode 4", "returnReturnAndHandle 8", "exitReturnAndHandle", "enterReturnAndHandle -1", "explode -1", "handleReturnAndHandle", "exitReturnAndHandle"),
            scenario(CALL, "the skip path is covered by catch and finally but not by return", "skipFinallyCatch", of(2, -3, 0), of(1004, 3, IllegalStateException.class), "enterSfc 2", "explode 2", "returnSfc 4", "exitSfc 2", "enterSfc -3", "skippedSfc -3", "exitSfc -3", "enterSfc 0", "skippedSfc 0", "caughtSfc zero", "exitSfc 0"),
            scenario(CALL, "an outer skip runs no inner handler and return handlers chain inner to outer", "nestedSkip", of(-2, 2), of(-1, 50), "enterNestedOuter -2", "skippedNestedOuter", "exitNestedOuter -2", "enterNestedOuter 2", "enterNestedInner 2", "twice 2", "returnNestedInner 4", "exitNestedInner 2", "returnNestedOuter 5", "exitNestedOuter 2"),
            scenario(CALL, "a skip skips the redirect handler inside", "redirectedSkip", of(-1, 3), of(0, 7), "enterRedirectedSkip -1", "exitRedirectedSkip -1", "enterRedirectedSkip 3", "redirectSkip 3", "twice 3", "exitRedirectedSkip 3"),
            scenario(CALL, "a final skip derived from a carry computed by a unique helper binds", "helperSkip", of(-4, 4), of(-1, 8), "part -4", "enterHelper -1", "skippedHelper", "exitHelper -1", "part 4", "enterHelper 4", "twice 4", "exitHelper 4"),
            scenario(CALL, "wide operands skip and return with an int skip flag", "wideSkip", of(args(-2L, 3.0d), args(5L, 2.5d)), of(-19L, 108L), "enterWideSkip -2 3.0", "skippedWideSkip -2 1.5", "exitWideSkip -2 3.0", "enterWideSkip 5 2.5", "wide 5 2.5", "returnWideSkip 7 1.25", "exitWideSkip 5 2.5"),
            scenario(CALL, "an entry-only call surround needs no exit handler even with a skip", "entryOnlySkip", of(1, -1), of(1, -1), "enterOnlySkip 1", "enterOnlySkip -1", "render -1"),
            scenario(CALL, "an entry-only whole-method surround", "entryOnlyWhole", of(args()), of(VOID), "enterOnlyWhole", "entryOnlyWhole"),
            scenario(CALL, "a skip restores the values below the call when the entry catches", "skipBelowRestored", of(-2, 3, 200), of(980, 1006, 1400), "enterSkipBelowRestored -2", "skippedSkipBelowRestored -2", "exitSkipBelowRestored -2", "enterSkipBelowRestored 3", "twice 3", "exitSkipBelowRestored 3", "entryCaughtSkipBelowRestored 200", "twice 200", "exitSkipBelowRestored 200"),
            scenario(CALL, "an inner skip completes normally for an outer return handler", "innerSkipOuterReturn", of(-2, 3), of(-199, 7), "enterOuterReturn -2", "enterInnerSkip -2", "skippedInnerSkip -2", "exitInnerSkip -2", "returnOuterReturn -200", "exitOuterReturn -2", "enterOuterReturn 3", "enterInnerSkip 3", "twice 3", "exitInnerSkip 3", "returnOuterReturn 6", "exitOuterReturn 3"),
            scenario(CALL, "a call inside a constructor call's arguments can be skipped", "skipInsideNew", of(-2, 3), of(-20, 6), "enterSkipInsideNew -2", "skippedSkipInsideNew -2", "exitSkipInsideNew -2", "enterSkipInsideNew 3", "twice 3", "exitSkipInsideNew 3"),
            scenario(CALL, "a call surround skips inside a body moved by a whole-method surround", "movedBody", of(-2, 3), of(990, 1008), "enterMovedWhole -2", "enterMovedCall -1 -2", "skippedMovedCall -1 -2", "exitMovedCall -1 -2", "exitMovedWhole -2", "enterMovedWhole 3", "enterMovedCall 4 3", "twice 4", "exitMovedCall 4 3", "exitMovedWhole 3"),
            scenario(WHOLE, "a branching entry handler around the whole method", "branching", of(-3), of(-6), "enterBranching negative", "body -3", "exitBranching negative")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarios(String method, String target, Object[] inputs, Object[] outputs, String[] events) throws Exception {
        MixinTestBootstrap.assertScenario(target, method, inputs, outputs, events);
    }
}
