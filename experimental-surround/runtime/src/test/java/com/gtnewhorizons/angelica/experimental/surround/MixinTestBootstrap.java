package com.gtnewhorizons.angelica.experimental.surround;

import com.gtnewhorizons.angelica.experimental.surround.targets.Trace;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.lib.util.Textifier;
import org.spongepowered.asm.lib.util.TraceClassVisitor;
import org.spongepowered.asm.mixin.EnvironmentStateTweaker;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public final class MixinTestBootstrap {

    private MixinTestBootstrap() {
    }

    private static final class Holder {

        static final IMixinTransformer TRANSFORMER = boot();
    }

    public static IMixinTransformer transformer() {
        return Holder.TRANSFORMER;
    }

    private static final Map<String, byte[]> TRANSFORMED = new HashMap<>();
    private static final Map<String, Class<?>> DEFINED = new HashMap<>();

    public static byte[] transformedBytes(String binaryName) {
        return TRANSFORMED.computeIfAbsent(binaryName, name -> transformer().transformClassBytes(name, name, read(name)));
    }

    public static Class<?> transform(String binaryName) {
        return DEFINED.computeIfAbsent(binaryName, name -> define(name, transformedBytes(name)));
    }

    public static Object call(String target, String name, Object... args) throws Exception {
        final Class<?> owner = transform(target);
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                final boolean isStatic = Modifier.isStatic(method.getModifiers());
                return method.invoke(isStatic ? null : owner.getDeclaredConstructor().newInstance(), args);
            }
        }
        throw new AssertionError("no " + name + " in " + target);
    }

    static final String WHOLE = "com.gtnewhorizons.angelica.experimental.surround.integration.WholeTarget";
    static final String CALL = "com.gtnewhorizons.angelica.experimental.surround.integration.CallTarget";
    static final String WRAP = "com.gtnewhorizons.angelica.experimental.surround.integration.WrapTarget";

    static ClassNode transformedNode(String target) {
        return parse(transformedBytes(target));
    }

    static ClassNode originalNode(String binaryName) {
        return parse(read(binaryName));
    }

    private static ClassNode parse(byte[] bytes) {
        final ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    static MethodNode method(ClassNode node, String name) {
        for (MethodNode method : node.methods) {
            if (method.name.equals(name)) {
                return method;
            }
        }
        throw new AssertionError("no method " + name + " in " + node.name);
    }

    static void assertGolden(String target, String method, String golden) {
        final ClassNode full = transformedNode(target);
        final ClassNode slim = new ClassNode();
        slim.visit(full.version, full.access, full.name, null, full.superName, null);
        slim.methods.add(method(full, method));
        for (MethodNode candidate : full.methods) {
            if (candidate.name.startsWith(method + "$surround")) {
                slim.methods.add(candidate);
            }
        }
        final StringWriter out = new StringWriter();
        slim.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(out)));
        final String stripped = out.toString().replaceAll("(?m)^ *LINENUMBER \\d+ L\\d+\\r?\\n", "");
        final String actual = normalize(stripped.replaceAll("\\b([a-z]+)\\$[a-z]{3,}[0-9a-f]{3,}\\$", "$1\\$\\$").replaceAll("\\bmd[0-9a-f]+\\$jvmdowngrader\\$", "md\\$jvmdowngrader\\$"));
        final Path file = Paths.get(System.getProperty("surround.golden.dir")).resolve(golden + ".asm");
        try {
            if (!Files.exists(file)) {
                Files.write(file, actual.getBytes(StandardCharsets.UTF_8));
                fail("Created golden " + file + ". Inspect it, then re-run.");
            }
            assertEquals(normalize(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)), actual, "output changed; inspect the diff before updating " + file);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static String normalize(String text) {
        final StringBuilder result = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            result.append(line.replaceAll("\\s+$", "").replaceAll("(\\$jvmdowngrader\\$concat\\$[\\w$]*?\\$\\d+)\\$[0-9a-f]+\\b", "$1")).append('\n');
        }
        return result.toString().trim() + "\n";
    }

    static final Object VOID = null;

    static Arguments scenario(String target, String name, String method, Object[] inputs, Object[] outputs, String... events) {
        return Arguments.of(Named.of(name, method), target, inputs, outputs, events);
    }

    static Object[] of(Object... values) {
        return values;
    }

    static List<Object> args(Object... values) {
        return Arrays.asList(values);
    }

    static void assertScenario(String target, String method, Object[] inputs, Object[] outputs, String[] events) throws Exception {
        Trace.reset();
        for (int i = 0; i < inputs.length; i++) {
            final Object[] args = inputs[i] instanceof List<?> list ? list.toArray() : new Object[]{inputs[i]};
            if (outputs[i] instanceof Class<?> type && Throwable.class.isAssignableFrom(type)) {
                final InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> call(target, method, args));
                assertEquals(outputs[i], thrown.getCause().getClass());
            } else {
                assertEquals(outputs[i], call(target, method, args));
            }
        }
        Trace.assertEvents(events);
    }

    private static final boolean MIXINEXTRAS = Boolean.getBoolean("surround.test.mixinextras");
    private static final boolean ADDITIONS_FIRST = Boolean.getBoolean("surround.test.additionsFirst");
    private static final boolean OBF = Boolean.getBoolean("surround.test.obf");

    private static IMixinTransformer boot() {
        Launch.blackboard = new HashMap<>();
        Launch.classLoader = new LaunchClassLoader(classpath());
        if (OBF) {
            Launch.classLoader.addTransformerExclusion(LocalSortingTransformer.class.getName());
            Launch.classLoader.registerTransformer(LocalSortingTransformer.class.getName());
            if (Launch.classLoader.getTransformers().stream().noneMatch(t -> t.getClass().getName().equals(LocalSortingTransformer.class.getName()))) {
                throw new IllegalStateException("LocalSortingTransformer was not registered");
            }
        }

        if (!MIXINEXTRAS) {
            SurroundBootstrap.init();
        }
        MixinBootstrap.init();
        if (MIXINEXTRAS) {
            if (ADDITIONS_FIRST) {
                SurroundBootstrap.init();
            }
            MixinExtrasBootstrap.init();
            if (!ADDITIONS_FIRST) {
                SurroundBootstrap.init();
            }
        }
        Mixins.addConfiguration("mixins.surround.test.json");
        if (!OBF) {
            Mixins.addConfiguration(RejectionCases.compileConfig(MIXINEXTRAS));
        }
        if (MIXINEXTRAS) {
            Mixins.addConfiguration("mixins.surround.mixinextras.test.json");
        }

        final EnvironmentStateTweaker tweaker = new EnvironmentStateTweaker();
        tweaker.injectIntoClassLoader(Launch.classLoader);
        tweaker.getLaunchArguments();

        return active();
    }

    private static IMixinTransformer active() {
        final Object transformer = MixinEnvironment.getCurrentEnvironment().getActiveTransformer();
        if (!(transformer instanceof IMixinTransformer mixinTransformer)) {
            throw new IllegalStateException("no active mixin transformer, got " + transformer);
        }
        return mixinTransformer;
    }

    private static URL[] classpath() {
        final List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            try {
                urls.add(new File(entry).toURI().toURL());
            } catch (MalformedURLException e) {
                throw new IllegalStateException(entry, e);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    static byte[] read(String binaryName) {
        try {
            final byte[] bytes = Launch.classLoader.getClassBytes(binaryName);
            if (bytes == null) {
                throw new IllegalStateException("missing " + binaryName);
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException(binaryName, e);
        }
    }

    private static Class<?> define(String name, byte[] bytes) {
        try {
            return new DefiningClassLoader(name, bytes).loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {

        private final String name;
        private final byte[] bytes;

        DefiningClassLoader(String name, byte[] bytes) {
            super(MixinTestBootstrap.class.getClassLoader());
            this.name = name;
            this.bytes = bytes;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!this.name.equals(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = defineClass(name, this.bytes, 0, this.bytes.length);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
