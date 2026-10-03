package com.gtnewhorizons.angelica.glsm.shader;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import me.eigenraven.lwjgl3ify.api.Lwjgl3Aware;
import org.lwjgl.Version;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

@Lwjgl3Aware
public final class ShaderCacheIO {

    private ShaderCacheIO() {}

    public static byte[] toHeap(ByteBuffer buf) {
        final byte[] heap = new byte[buf.remaining()];
        buf.duplicate().get(heap);
        return heap;
    }

    public static ByteBuffer toNative(byte[] data) {
        final ByteBuffer buf = MemoryUtil.memAlloc(data.length);
        buf.put(data).flip();
        return buf;
    }

    public static <K, V> void lruPut(Object2ObjectLinkedOpenHashMap<K, V> map, K key, V value, int max) {
        synchronized (map) {
            map.putAndMoveToFirst(key, value);
            while (map.size() > max) map.removeLast();
        }
    }

    public static void writeStr(DataOutputStream out, String s) throws IOException {
        if (s == null) {
            out.writeInt(-1);
            return;
        }
        final byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(utf8.length);
        out.write(utf8);
    }

    public static String readStr(ByteBuffer in) {
        final int n = in.getInt();
        if (n == -1) return null;
        final int pos = in.position();
        in.position(pos + n);
        return new String(in.array(), in.arrayOffset() + pos, n, StandardCharsets.UTF_8);
    }

    public static int readCount(ByteBuffer in) {
        final int n = in.getInt();
        if (n < 0 || n > in.remaining()) throw new BufferUnderflowException();
        return n;
    }

    public static void writeBytes(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    public static byte[] readBytes(ByteBuffer in) {
        final byte[] out = new byte[readCount(in)];
        in.get(out);
        return out;
    }

    public static void writeInts(DataOutputStream out, int[] values) throws IOException {
        out.writeInt(values.length);
        for (int v : values) out.writeInt(v);
    }

    public static int[] readInts(ByteBuffer in) {
        final int n = readCount(in);
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = in.getInt();
        return out;
    }

    public static void writeSortedStrs(DataOutputStream out, Set<String> values) throws IOException {
        final Set<String> sorted = values == null ? Set.of() : new TreeSet<>(values);
        out.writeInt(sorted.size());
        for (String s : sorted) writeStr(out, s);
    }

    public static Set<String> readStrSet(ByteBuffer in) {
        final String[] out = new String[readCount(in)];
        for (int i = 0; i < out.length; i++) out[i] = readStr(in);
        return Set.copyOf(Arrays.asList(out));
    }

    public static String libraryId(SharedLibrary lib) {
        final String path = lib.getPath();
        return Version.getVersion() + '|' + lib.getName() + '|' + (path == null ? -1 : new File(path).length());
    }
}
