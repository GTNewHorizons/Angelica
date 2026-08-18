package com.gtnewhorizons.angelica.loading.shared.transformers;

import com.gtnewhorizon.gtnhlib.asm.ClassConstantPoolParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marks TileEntity subclasses that declare getDescriptionPacket, shouldRenderInPass or getDistanceFrom with a
 * corresponding interface, so runtime code can test with instanceof instead of reflection.
 */
public final class TileEntityMarkerTransform {

    public static final int MARK_DESCRIPTION_PACKET = 1 << 0;
    public static final int MARK_SHOULD_RENDER_IN_PASS = 1 << 1;
    public static final int MARK_GET_DISTANCE_FROM = 1 << 2;

    private static final String SENDS_DESCRIPTION_PACKET = "com/gtnewhorizons/angelica/mixins/interfaces/SendsDescriptionPacket";
    private static final String OVERRIDES_SHOULD_RENDER_IN_PASS = "com/gtnewhorizons/angelica/mixins/interfaces/OverridesShouldRenderInPass";
    private static final String OVERRIDES_GET_DISTANCE_FROM = "com/gtnewhorizons/angelica/mixins/interfaces/OverridesGetDistanceFrom";

    private static final String TILE_ENTITY = "net/minecraft/tileentity/TileEntity";
    private static final int SCAN_FLAGS = ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

    private record Marker(int bit, String method, String desc, String iface) {}

    private final Marker[] markers;
    private final ClassConstantPoolParser prefilter;
    private final Set<String> tileEntities = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TileEntityMarkerTransform(boolean isObf) {
        this.markers = new Marker[] {
            new Marker(MARK_DESCRIPTION_PACKET, isObf ? "func_145844_m" : "getDescriptionPacket", "()Lnet/minecraft/network/Packet;", SENDS_DESCRIPTION_PACKET),
            new Marker(MARK_SHOULD_RENDER_IN_PASS, "shouldRenderInPass", "(I)Z", OVERRIDES_SHOULD_RENDER_IN_PASS),
            new Marker(MARK_GET_DISTANCE_FROM, isObf ? "func_145835_a" : "getDistanceFrom", "(DDD)D", OVERRIDES_GET_DISTANCE_FROM) };

        final String[] methods = new String[markers.length];
        for (int i = 0; i < markers.length; i++) methods[i] = markers[i].method();
        this.prefilter = new ClassConstantPoolParser(methods);

    }

    private boolean isTileEntity(String className) {
        return className != null && (className.startsWith(TILE_ENTITY) || tileEntities.contains(className));
    }

    public void track(String className, String superClassName) {
        if (className != null && isTileEntity(superClassName)) tileEntities.add(className);
    }

    public int markersFor(String className, byte[] classBytes) {
        if (TILE_ENTITY.equals(className) || !isTileEntity(className) || !prefilter.find(classBytes)) return 0;

        final MarkerScanner scanner = new MarkerScanner();
        new ClassReader(classBytes).accept(scanner, SCAN_FLAGS);
        return scanner.declared & ~scanner.alreadyPresent;
    }

    public byte[] addMarkers(byte[] classBytes, int markers) {
        final ClassReader cr = new ClassReader(classBytes);
        final ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {

            @Override
            public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
                final String[] added = interfacesFor(markers);
                if (interfaces == null) {
                    super.visit(version, access, name, sig, superName, added);
                    return;
                }
                final String[] merged = Arrays.copyOf(interfaces, interfaces.length + added.length);
                System.arraycopy(added, 0, merged, interfaces.length, added.length);
                super.visit(version, access, name, sig, superName, merged);
            }
        }, 0);
        return cw.toByteArray();
    }

    public void addMarkers(ClassNode cn, int markers) {
        Collections.addAll(cn.interfaces, interfacesFor(markers));
    }

    private int bitOf(int access, String name, String desc) {
        if ((access & Opcodes.ACC_PUBLIC) == 0 || (access & Opcodes.ACC_STATIC) != 0) return 0;
        for (Marker m : markers) if (m.method().equals(name) && m.desc().equals(desc)) return m.bit();
        return 0;
    }

    private int bitOf(String interfaceName) {
        for (Marker m : markers) if (m.iface().equals(interfaceName)) return m.bit();
        return 0;
    }

    private String[] interfacesFor(int wanted) {
        final String[] names = new String[Integer.bitCount(wanted)];
        int next = 0;
        for (Marker m : markers) if ((wanted & m.bit()) != 0) names[next++] = m.iface();
        return names;
    }

    private final class MarkerScanner extends ClassVisitor {

        private int declared;
        private int alreadyPresent;

        MarkerScanner() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String sig, String superName, String[] interfaces) {
            if (interfaces == null) return;
            for (String iface : interfaces) alreadyPresent |= bitOf(iface);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
            declared |= bitOf(access, name, desc);
            return null;
        }
    }
}
