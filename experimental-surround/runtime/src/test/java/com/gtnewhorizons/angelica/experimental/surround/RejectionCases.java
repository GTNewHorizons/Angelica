package com.gtnewhorizons.angelica.experimental.surround;

import net.minecraft.launchwrapper.Launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RejectionCases {

    static final String PACKAGE = "rejection";
    static final String CONFIG = "mixins.surround.rejection.test.json";
    static final String ABS = "at = @At(value = \"INVOKE\", target = \"Ljava/lang/Math;abs(I)I\")";
    static final String WHOLE_ENTRY = "@Surround(method = \"run\") private void invalid$enter(int a) {}";
    static final String FINALLY = " @Surround.Finally private void invalid$always() {}";
    static final String RUN = cls("public int run(int a) { return Math.abs(a); }");
    static final String IN_NEW = cls("public int run(int a) { return new StringBuilder(Math.abs(a)).length(); }");

    static final Case[] CASES = {
        new Case("Initializer", "@Surround invalid$enter cannot target the initializer", RUN, mixin("@Surround(method = \"<init>()V\") private void invalid$enter() {}")),
        new Case("Staticness", "must be non-static to match the entry handler", RUN, mixin(WHOLE_ENTRY + " @Surround.Finally private static void invalid$always() {}")),
        new Case("CarriedThrowable", "cannot mark its caught Throwable parameter @Surround.Carry", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) {" + " @Surround.Carry Throwable state = null; }" + " @Surround.Catch private void invalid$caught(@Surround.Carry Throwable e) {}")),
        new Case("LocalCall", "is @Surround.Local java.lang.String, which matches no single local at the call", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a, @Surround.Local String s) {}")),
        new Case("HandleInsideNew", "declares handle = true, but the call is inside a constructor call's arguments", IN_NEW, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {}" + " @Surround.Catch(handle = true) private int invalid$caught(Throwable error) { return 0; }")),
        new Case("SkippedRequired", "can skip a int result but has no @Surround.Skipped", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {" + " @Surround.Skip boolean skip = a > 0; }")),
        new Case("Shift", "@Surround invalid$enter has @At(\"INVOKE\") with a non-default shift or by, which moves the match off the call", RUN, mixin("@Surround(method = \"run\", at = @At(value = \"INVOKE\", target = \"Ljava/lang/Math;abs(I)I\", shift = At.Shift.AFTER)) private void invalid$enter() {}")),
        new Case("AbstractTarget", "cannot target the abstract or native method", "public abstract class $T { public abstract int run(int a); }", "@Mixin(value = $T.class, remap = false) public abstract class $M {" + " @Surround(method = \"run\") private void invalid$enter(int a) {} }"),
        new Case("InitAt", "at must select a method call, not the constructor call java/lang/StringBuilder.<init>()V", cls("public Object run(int a) { return new StringBuilder(); }"), mixin("@Surround(method = \"run\", at = @At(value = \"INVOKE\", target = \"Ljava/lang/StringBuilder;<init>()V\")) private void invalid$enter() {}")),
        new Case("CatchingEntry", "must not catch exceptions when it surrounds a call inside a constructor call's arguments", IN_NEW, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {" + " try { Math.abs(a); } catch (RuntimeException e) { Math.abs(0); } }" + FINALLY)),
        new Case("TooManyOperands", "entry handler has more parameters than call java/lang/Math.abs(I)I", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a, int b) {}")),
        new Case("SkipNotAssigned", "has a @Surround.Skip local in slot 2 that is not definitely assigned", cls("public void run(int a) {}"), mixin("@Surround(method = \"run\") private void invalid$enter(int a) {" + " @Surround.Skip boolean skip; if (a > 0) { skip = true; String.valueOf(a); } }")),
        new Case("Collision", "exit handler 'surround$exit' collides with surround$exit()V in " + PACKAGE + "/Collision, which is merged from " + PACKAGE + ".mixin.CollisionMixin2", RUN, "@Mixin(value = $T.class, priority = 900, remap = false) public class $M {" + " @Surround(method = \"run\") private void surround$enter(int a) {}" + " @Surround.Finally private void surround$exit() {} }", "@Mixin(value = $T.class, priority = 1100, remap = false) public class $M {" + " private void surround$exit() {} }"),
        new Case("MultipleRoles", "can be only one of @Surround.Catch, @Surround.Finally, @Surround.Skipped and @Surround.Return", RUN, mixin(WHOLE_ENTRY + " @Surround.Catch @Surround.Finally private void invalid$exit(Throwable e) {}")),
        new Case("DuplicateId", "duplicate @Surround the default id; ids must be unique per mixin", RUN, mixin(WHOLE_ENTRY + " @Surround(method = \"run\") private void invalid$enter2(int a) {}")),
        new Case("OrphanExit", "no @Surround in this mixin declares it", RUN, mixin(WHOLE_ENTRY + " @Surround.Finally(\"typo\") private void invalid$always() {}")),
        new Case("CarryOnEntryParameter", "only the entry handler's own locals can be carried", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(@Surround.Carry int a) {}")),
        new Case("CarryIdNotDeclared", "asks for the carried local 'frist'", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry(\"first\") int first = 1; }" + " @Surround.Finally private void invalid$always(@Surround.Carry(\"frist\") int typo) {}")),
        new Case("NoCarryOfKind", "matches no carried local of that kind", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry int count = 1; }" + " @Surround.Finally private void invalid$always(@Surround.Carry float missing) {}")),
        new Case("AmbiguousCarry", "matches 2 carried locals of that kind", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry int first = 1; @Surround.Carry int second = 2; }" + " @Surround.Finally private void invalid$always(@Surround.Carry int either) {}")),
        new Case("CarryReadAsDifferentType", "but another exit handler reads it as java.lang.String", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry Object value = null; }" + " @Surround.Catch private void invalid$caught(java.io.IOException e, @Surround.Carry String value) {}" + " @Surround.Finally private void invalid$always(@Surround.Carry Integer value) {}")),
        new Case("AmbiguousWideLocal", "shares its scope with another", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry long first; long second; try { first = a; second = a + 1L; } catch (RuntimeException e) { throw e; } }")),
        new Case("DuplicateCarryId", "declares two @Surround.Carry locals named 'x'", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Carry(\"x\") int first = 1; @Surround.Carry(\"x\") long second = 2L; }")),
        new Case("CarryInsideType", "@Surround.Carry must annotate the local itself, not part of its type", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { java.util.List<@Surround.Carry String> l = null; }")),
        new Case("DuplicateSkip", "declares two @Surround.Skip locals", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Skip boolean first = a > 0; @Surround.Skip boolean second = a < 0; }")),
        new Case("SkipNotIntLike", "it must be boolean or int-like", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { @Surround.Skip long skip = a; }")),
        new Case("SkippedWithoutSkip", "but declares no @Surround.Skip local, so nothing is ever skipped", RUN, mixin(WHOLE_ENTRY + " @Surround.Skipped private int invalid$skipped(int a) { return 0; }")),
        new Case("CarryScopedEarly", "goes out of scope before the entry handler ends", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) {" + " { @Surround.Carry int scoped = a; String.valueOf(scoped); } int reused = a + 1; String.valueOf(reused); }" + " @Surround.Finally private void invalid$always(@Surround.Carry int value) {}")),
        new Case("EarlyReturn", "every path falls through to its end", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) {" + " if (a > 0) { return; } String.valueOf(a); }" + FINALLY)),
        new Case("PartialAfterInt", "is not definitely assigned when the entry handler ends", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) {" + " { int tmp = a + 5; String.valueOf(tmp); }" + " @Surround.Carry int flag; if (a > 0) { flag = 1; String.valueOf(flag); } }" + " @Surround.Finally private void invalid$always(@Surround.Carry int flag) {}")),
        new Case("AssignsParameter", "must not assign its parameters", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { a = a + 1; }" + FINALLY)),
        new Case("LocalOnWholeMethod", "is @Surround.Local, which only applies when @Surround.at selects a call", RUN, mixin(WHOLE_ENTRY + " @Surround.Finally private void invalid$always(@Surround.Local int b) {}")),
        new Case("IncrementsParameter", "must not assign its parameters", RUN, mixin("@Surround(method = \"run\") private void invalid$enter(int a) { a++; }" + FINALLY)),
        new Case("NonCallAt", "@Surround invalid$enter has @At(\"HEAD\"), which does not select a method call", RUN, mixin("@Surround(method = \"run\", at = @At(\"HEAD\")) private void invalid$enter() {}")),
        new Case("CancellableExit", "parameter 0 ('ci') of 'invalid$always' is annotated @Cancellable, which a @Surround handler cannot resolve", RUN, mixin(WHOLE_ENTRY + " @Surround.Finally private void invalid$always(@Cancellable Object ci) {}")),
        new Case("DuplicateFinally", "declares more than one @Surround.Finally", RUN, mixin(WHOLE_ENTRY + FINALLY + " @Surround.Finally private void invalid$always2() {}")),
        new Case("ReturnWrongType", "must return java.lang.String to match what it surrounds, not int", cls("public String run(String s) { return s.trim(); }"), mixin("@Surround(method = \"run\", at = @At(value = \"INVOKE\", target = \"Ljava/lang/String;trim()Ljava/lang/String;\")) private void invalid$enter(String s) {}" + " @Surround.Return private int invalid$ret(int result) { return result; }")),
        new Case("ReturnResultNotFirst", "must declare the int result as its first parameter", RUN, mixin(WHOLE_ENTRY + " @Surround.Return private int invalid$ret(String wrong) { return 0; }")),
        new Case("ReturnCarriedResult", "cannot mark its result parameter @Surround.Carry", RUN, mixin(WHOLE_ENTRY + " @Surround.Return private int invalid$ret(@Surround.Carry int result) { return result; }")),
        new Case("NonVoidFinally", "must return void; an exit handler cannot suppress or replace anything", RUN, mixin(WHOLE_ENTRY + " @Surround.Finally private int invalid$always() { return 1; }")),
        new Case("NonVoidEntry", "must return void; carry state out with @Surround.Carry locals instead", RUN, mixin("@Surround(method = \"run\") private long invalid$enter(int a) { return 0L; }")),
        new Case("CatchNotThrowable", "parameter 0 ('notAnError') catches java.lang.String, which is not a java.lang.Throwable subtype", RUN, mixin(WHOLE_ENTRY + " @Surround.Catch private void invalid$caught(String notAnError) {}")),
        new Case("DuplicateCatch", "is a second @Surround.Catch for java.io.IOException", RUN, mixin(WHOLE_ENTRY + " @Surround.Catch private void invalid$first(java.io.IOException e) {} @Surround.Catch private void invalid$second(java.io.IOException e) {}")),
        new Case("CatchWithoutParameters", "must declare the Throwable it catches as its first parameter", RUN, mixin(WHOLE_ENTRY + " @Surround.Catch private void invalid$caught() {}")),
        new Case("AbstractEntry", "must not be abstract", RUN, "@Mixin(value = $T.class, remap = false) public abstract class $M { @Surround(method = \"run\") protected abstract void invalid$enter(int a); }"),
        new Case("AbstractCatch", "must not be abstract", RUN, "@Mixin(value = $T.class, remap = false) public abstract class $M { " + WHOLE_ENTRY + " @Surround.Catch protected abstract void invalid$caught(Throwable t); }"),
        new Case("MisorderedLocalAfterCarry", "parameter 1 ('y') is out of order; declare operands first, then @Surround.Local, then @Surround.Carry parameters", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {}" + " @Surround.Finally private void invalid$always(@Surround.Carry int x, @Surround.Local int y) {}")),
        new Case("LocalAndCarry", "parameter 0 ('a') cannot be both @Surround.Local and @Surround.Carry", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {}" + " @Surround.Finally private void invalid$always(@Surround.Local @Surround.Carry int a) {}")),
        new Case("LocalCaught", "cannot mark its caught Throwable parameter @Surround.Local", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int a) {}" + " @Surround.Catch private void invalid$caught(@Surround.Local Throwable e) {}")),
        new Case("HandleWrongReturn", "must return java.lang.String to match what it surrounds, not int", cls("public String run(String s) { return s.trim(); }"), mixin("@Surround(method = \"run\", at = @At(value = \"INVOKE\", target = \"Ljava/lang/String;trim()Ljava/lang/String;\")) private void invalid$enter(String s) {}" + " @Surround.Catch(handle = true) private int invalid$caught(Throwable e, String s) { return 0; }")),
        new Case("HandleWithoutAt", "declares handle = true, which only applies when @Surround.at selects a call", RUN, mixin(WHOLE_ENTRY + " @Surround.Catch(handle = true) private void invalid$caught(Throwable e) {}")),
        new Case("SliceWithoutAt", "declares @Surround.slice, which only applies when @Surround.at selects a call", RUN, mixin("@Surround(method = \"run\", slice = @Slice(from = @At(\"HEAD\"))) private void invalid$enter(int a) {}")),
        new Case("EntryOperandMismatch", "parameter 0 ('notAnInt') is java.lang.String, which does not match the int operand of call java/lang/Math.abs(I)I", RUN, mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(String notAnInt) {}")),
        new Case("OrphanWithoutSurround", "@Surround exit handler 'invalid$always' refers to the default id but no @Surround in this mixin declares it; this mixin has no @Surround at all", RUN, mixin(WHOLE_ENTRY), mixin("@Surround.Finally private void invalid$always() {}"))
    };

    static final Case SUGAR = new Case("Sugar", "@Surround invalid$enter one of its parameters is annotated @Local, @Share or @Cancellable, which a @Surround handler cannot resolve", cls("public int run(int a) { int local = a + 1; return Math.abs(local); }"), mixin("@Surround(method = \"run\", " + ABS + ") private void invalid$enter(int local, @Local(ordinal = 0) int a) {}"));

    static final Case REQUIRE_ZERO_MISS = new Case("RequireZeroMiss", "", RUN, mixin("@Surround(method = \"run\", require = 0, at = @At(value = \"INVOKE\", target = \"Ljava/lang/Math;max(II)I\")) private void invalid$enter(int a, int b) {}" + FINALLY));

    static String compileConfig(boolean mixinExtras) {
        final List<Case> cases = new ArrayList<>();
        if (mixinExtras) {
            cases.add(SUGAR);
        } else {
            cases.addAll(Arrays.asList(CASES));
            cases.add(REQUIRE_ZERO_MISS);
        }
        final List<Unit> units = new ArrayList<>();
        final List<String> mixins = new ArrayList<>();
        for (Case c : cases) {
            units.add(new Unit(PACKAGE + "." + c.name, "package " + PACKAGE + "; " + c.target.replace("$T", c.name)));
            for (int i = 0; i < c.mixins.length; i++) {
                final String name = c.name + "Mixin" + (i + 1);
                mixins.add('"' + name + '"');
                units.add(new Unit(PACKAGE + ".mixin." + name, "package " + PACKAGE + ".mixin; import " + PACKAGE + ".*;" + " import com.gtnewhorizons.angelica.experimental.surround.Surround;" + " import com.llamalad7.mixinextras.sugar.Cancellable;" + " import com.llamalad7.mixinextras.sugar.Local;" + " import org.spongepowered.asm.mixin.Mixin;" + " import org.spongepowered.asm.mixin.injection.At;" + " import org.spongepowered.asm.mixin.injection.Slice;" + c.mixins[i].replace("$T", c.name).replace("$M", name)));
            }
        }
        try {
            final Path dir = Paths.get("rejection-classes").toAbsolutePath();
            if (Files.exists(dir)) {
                try (Stream<Path> stale = Files.walk(dir)) {
                    stale.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            final Path sources = Files.createDirectories(dir.resolve("src"));
            final List<String> command = new ArrayList<>(Arrays.asList(System.getProperty("surround.javac"), "-classpath", System.getProperty("java.class.path"), "-d", dir.toString(), "-proc:none", "-g", "--release", "8", "-Xlint:-options"));
            for (Unit unit : units) {
                final Path file = sources.resolve(unit.className().replace('.', '/') + ".java");
                Files.createDirectories(file.getParent());
                Files.write(file, unit.source().getBytes(StandardCharsets.UTF_8));
                command.add(file.toString());
            }
            final Process javac = new ProcessBuilder(command).redirectErrorStream(true).start();
            final String output = new String(javac.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (javac.waitFor() != 0) {
                throw new IllegalStateException(output);
            }
            Files.write(dir.resolve(CONFIG), ("{\"required\": true, \"minVersion\": \"0.8\", \"package\": \"" + PACKAGE + ".mixin\"," + " \"compatibilityLevel\": \"JAVA_8\", \"mixins\": " + mixins + "}").getBytes(StandardCharsets.UTF_8));
            Launch.classLoader.addURL(dir.toUri().toURL());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return CONFIG;
    }

    private static String cls(String members) {
        return "public class $T { " + members + " }";
    }

    private static String mixin(String members) {
        return "@Mixin(value = $T.class, remap = false) public class $M { " + members + " }";
    }

    private record Unit(String className, String source) {
    }

    static final class Case {

        final String name;
        final String expected;
        final String target;
        final String[] mixins;

        Case(String name, String expected, String target, String... mixins) {
            this.name = name;
            this.expected = expected;
            this.target = target;
            this.mixins = mixins;
        }

        void assertRejected() {
            final String messages = causeChain(transformExpectingFailure(PACKAGE + "." + this.name));
            assertTrue(messages.contains(this.expected), messages);
        }

        private static Throwable transformExpectingFailure(String binaryName) {
            try {
                MixinTestBootstrap.transformer().transformClassBytes(binaryName, binaryName, MixinTestBootstrap.read(binaryName));
            } catch (Throwable t) {
                return t;
            }
            throw new AssertionError(binaryName + " transformed without the expected failure");
        }

        private static String causeChain(Throwable thrown) {
            final StringBuilder all = new StringBuilder();
            for (Throwable t = thrown; t != null; t = t.getCause()) {
                all.append(t).append('\n');
            }
            return all.toString();
        }
    }
}
