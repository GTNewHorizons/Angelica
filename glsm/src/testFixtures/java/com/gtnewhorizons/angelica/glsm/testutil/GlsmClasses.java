package com.gtnewhorizons.angelica.glsm.testutil;

import com.gtnewhorizons.angelica.glsm.redirect.GLSMRedirector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class GlsmClasses {

    private static final String PACKAGE = "com/gtnewhorizons/angelica/glsm/";
    private static final Map<String, ClassNode> CLASSES = new ConcurrentHashMap<>();

    private GlsmClasses() {}

    public static ClassNode load(String internalName) {
        return CLASSES.computeIfAbsent(internalName, name -> {
            try (InputStream in = GlsmClasses.class.getClassLoader().getResourceAsStream(name + ".class")) {
                if (in == null) throw new IllegalStateException("Missing class file for " + name);
                final ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static List<String> classes() {
        return GLSMRedirector.glsmClasses();
    }

    public static Set<String> packageClasses() {
        final URL resource = GlsmClasses.class.getClassLoader().getResource(PACKAGE + "GLStateManager.class");
        if (resource == null) throw new IllegalStateException("GLStateManager.class not on the classpath");
        final List<String> fileNames = new ArrayList<>();
        try {
            if (resource.getProtocol().equals("jar")) {
                final JarURLConnection connection = (JarURLConnection) resource.openConnection();
                connection.setUseCaches(false);
                try (JarFile jar = connection.getJarFile()) {
                    jar.stream().map(JarEntry::getName).filter(n -> n.startsWith(PACKAGE) && n.indexOf('/', PACKAGE.length()) < 0)
                        .forEach(n -> fileNames.add(n.substring(PACKAGE.length())));
                }
            } else {
                try (Stream<Path> files = Files.list(Path.of(resource.toURI()).getParent())) {
                    files.forEach(p -> fileNames.add(p.getFileName().toString()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        final Set<String> classes = new TreeSet<>();
        for (String file : fileNames) {
            if (file.equals("GLStateManager.class") || file.matches("GLSM[A-Z][A-Za-z]*\\.class")) {
                classes.add(PACKAGE + file.substring(0, file.length() - ".class".length()));
            }
        }
        return classes;
    }

    public record Resolved(String owner, MethodNode method) {}

    public static Resolved resolve(String owner, String name, String desc) {
        for (String type = owner; type != null && type.startsWith(PACKAGE); ) {
            final ClassNode node = load(type);
            for (MethodNode method : node.methods) {
                if (method.name.equals(name) && method.desc.equals(desc)) {
                    if (method.instructions.size() == 0) return null;
                    return new Resolved(type, method);
                }
            }
            type = node.superName;
        }
        return null;
    }
}
