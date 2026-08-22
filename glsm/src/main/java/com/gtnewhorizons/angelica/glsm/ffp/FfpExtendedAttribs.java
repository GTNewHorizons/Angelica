package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizon.gtnhlib.client.renderer.cel.api.util.NormI8;
import com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFormatElement.Usage;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.ImmediateExtendedAttribHandler;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAddress0;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memAlloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memCalloc;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memFree;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetByte;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;
import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetShort;
import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * Supplies {@code mc_midTexCoord}/{@code at_tangent} for mod geometry drawn through the FFPclient-array path
 * ({@code glVertexPointer}/{@code glTexCoordPointer} + {@code glDrawArrays}), which bypasses the Tessellator
 * and display-list ext-injection paths.
 *
 * <p>The ext buffer is built from a one-time GPU readback of the source VBO, cached per VBO handle and range so a
 * redraw of the same geometry is a cache hit. Entries are dropped when the source VBO is deleted or respecified.
 *
 * <p>Tangents are reconstructed from the flat face normal (handler's {@code normalOffset < 0} path).
 */
public final class FfpExtendedAttribs {

    private FfpExtendedAttribs() {}

    private static final int POSITION_LOC = Usage.POSITION.getAttributeLocation();
    private static final int UV_LOC = Usage.PRIMARY_UV.getAttributeLocation();
    private static final int NORMAL_LOC = Usage.NORMAL.getAttributeLocation();
    private static final int SRC_TEX_OFFSET = 12;
    private static final int SRC_NORMAL_OFFSET = 20;
    private static final int SRC_STRIDE_FLAT = 20;
    private static final int SRC_STRIDE_SMOOTH = 24;
    private static final int MAX_CACHE = 4096;

    private static final HashMap<Key, Integer> cache = new HashMap<>();
    private static final Key lookup = new Key();
    private static boolean purging = false;

    private static final HashMap<EboKey, Boolean> eboPatternCache = new HashMap<>();
    private static final EboKey eboLookup = new EboKey();

    private static final Int2ObjectMap<ObjectSet<Key>> keysBySource = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectMap<ObjectSet<EboKey>> eboKeysBySource = new Int2ObjectOpenHashMap<>();

    public static boolean isEmpty() {
        return cache.isEmpty() && eboPatternCache.isEmpty();
    }

    private static int internalDrawDepth = 0;

    public static void beginInternalDraw() { internalDrawDepth++; }

    public static void endInternalDraw() { if (internalDrawDepth > 0) internalDrawDepth--; }

    private static boolean isNotConventionalTexturedArray(VAOManager.Attrib a) {
        return a == null || !a.enabled || a.genericPointer || a.vboId == 0 || a.size < 2;
    }

    public static boolean maybeBind(int mode, int first, int count) {
        final ImmediateExtendedAttribHandler h = GLSMHooks.immediateExtendedHandler;
        if (h == null) return false;
        if (internalDrawDepth > 0) return false;
        final int extPrim = ImmediateExtendedAttribHandler.extPrimVerts(mode, count);
        if (extPrim == 0 || first < 0) return false;
        if (!h.wantsExtended()) return false;

        final VAOManager.Attrib pos = VAOManager.get(POSITION_LOC);
        final VAOManager.Attrib uv = VAOManager.get(UV_LOC);
        if (isNotConventionalTexturedArray(pos) || isNotConventionalTexturedArray(uv)) return false;

        VAOManager.Attrib normal = extPrim == 3 ? VAOManager.get(NORMAL_LOC) : null;
        if (normal != null && (!normal.enabled || normal.genericPointer || normal.vboId == 0 || normal.size < 3)) {
            normal = null;
        }

        return bindExt(h, mode, first, count, extPrim, pos, uv, normal);
    }

    public static boolean maybeBindIndexed(int mode, int indexCount, int indexType, long indicesOffset) {
        final ImmediateExtendedAttribHandler h = GLSMHooks.immediateExtendedHandler;
        if (h == null) return false;
        if (internalDrawDepth > 0) return false;
        if (mode != GL11.GL_TRIANGLES || indexCount <= 0 || indexCount % 6 != 0) return false;
        if (!h.wantsExtended()) return false;

        final VAOManager.Attrib pos = VAOManager.get(POSITION_LOC);
        final VAOManager.Attrib uv = VAOManager.get(UV_LOC);
        if (isNotConventionalTexturedArray(pos) || isNotConventionalTexturedArray(uv)) return false;

        final int indexSize = indexTypeBytes(indexType);
        if (indexSize == 0) return false;
        if (indicesOffset % (6L * indexSize) != 0) return false;
        final int first = (int) (indicesOffset / indexSize) / 6 * 4;
        final int count = indexCount / 6 * 4;

        if (!isQuadPatternEbo(VAOManager.boundEBO, indicesOffset, indexCount, indexType, indexSize, first)) return false;

        // Build/bind as GL_QUADS (the real primitive): face-normal tangents, 4-vertex tiles, no per-vertex normal.
        return bindExt(h, GL11.GL_QUADS, first, count, 4, pos, uv, null);
    }

    private static boolean isQuadPatternEbo(int ebo, long offset, int indexCount, int indexType, int indexSize, int firstVertex) {
        if (ebo == 0) return false;
        eboLookup.set(ebo, offset, indexCount, indexType);
        final Boolean cached = eboPatternCache.get(eboLookup);
        if (cached != null) return cached;
        if (eboPatternCache.size() >= MAX_CACHE) {
            eboPatternCache.clear();
            eboKeysBySource.clear();
        }

        final ByteBuffer buf = memCalloc(indexCount, indexSize);
        RENDER_BACKEND.getBufferSubData(GL15.GL_ELEMENT_ARRAY_BUFFER, offset, buf);
        final long addr = memAddress0(buf);
        boolean match = true;
        for (int q = 0, quadCount = indexCount / 6; q < quadCount; q++) {
            final int base = firstVertex + q * 4;
            final long p = addr + (long) q * 6 * indexSize;
            if (readIndex(p, 0, indexSize) != base
                || readIndex(p, 1, indexSize) != base + 1
                || readIndex(p, 2, indexSize) != base + 2
                || readIndex(p, 3, indexSize) != base + 2
                || readIndex(p, 4, indexSize) != base + 3
                || readIndex(p, 5, indexSize) != base) {
                match = false;
                break;
            }
        }
        memFree(buf);
        final EboKey stored = eboLookup.copy();
        eboPatternCache.put(stored, match);
        ObjectSet<EboKey> ebos = eboKeysBySource.get(stored.ebo);
        if (ebos == null) eboKeysBySource.put(stored.ebo, ebos = new ObjectOpenHashSet<>());
        ebos.add(stored);
        return match;
    }

    private static long readIndex(long base, int i, int indexSize) {
        return switch (indexSize) {
            case 1 -> memGetByte(base + i) & 0xFFL;
            case 2 -> memGetShort(base + (long) i * 2) & 0xFFFFL;
            default -> memGetInt(base + (long) i * 4) & 0xFFFFFFFFL;
        };
    }

    private static int indexTypeBytes(int indexType) {
        return switch (indexType) {
            case GL11.GL_UNSIGNED_BYTE -> 1;
            case GL11.GL_UNSIGNED_SHORT -> 2;
            case GL11.GL_UNSIGNED_INT -> 4;
            default -> 0;
        };
    }

    private static boolean bindExt(ImmediateExtendedAttribHandler h, int mode, int first, int count, int extPrim,
                                   VAOManager.Attrib pos, VAOManager.Attrib uv, VAOManager.Attrib normal) {
        final int extVbo = getOrBuild(h, pos, uv, normal, first, count, extPrim, mode);
        if (extVbo == 0) return false;

        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
        ImmediateExtendedAttribHandler.setupExtAttribPointers(0L, ImmediateExtendedAttribHandler.EXT_STRIDE);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
        return true;
    }

    public static void unbind() {
        GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_MID_TEX);
        GLStateManager.glDisableVertexAttribArray(ImmediateExtendedAttribHandler.LOC_TANGENT);
    }

    public static void onDeleteBuffer(int vboId) {
        if (purging || vboId == 0) return;
        dropDerivedFrom(vboId);
    }

    public static void onBufferRespecified(int vboId) {
        if (purging || vboId == 0) return;
        dropDerivedFrom(vboId);
    }

    private static void dropDerivedFrom(int srcId) {
        final ObjectSet<EboKey> ebos = eboKeysBySource.remove(srcId);
        if (ebos != null) {
            for (EboKey k : ebos) eboPatternCache.remove(k);
        }
        final ObjectSet<Key> keys = keysBySource.get(srcId);
        if (keys == null || keys.isEmpty()) return;
        IntArrayList toFree = null;
        for (Key k : keys.toArray(new Key[0])) {
            final Integer ext = cache.remove(k);
            if (ext != null) {
                if (toFree == null) toFree = new IntArrayList();
                toFree.add(ext.intValue());
            }
            unindexKey(k);
        }
        freeExtVbos(toFree);
    }

    private static void indexKey(Key k) {
        addToBucket(k.posVBO, k);
        if (k.uvVBO != k.posVBO) addToBucket(k.uvVBO, k);
        if (k.normalVBO != k.posVBO && k.normalVBO != k.uvVBO) addToBucket(k.normalVBO, k);
    }

    private static void unindexKey(Key k) {
        removeFromBucket(k.posVBO, k);
        if (k.uvVBO != k.posVBO) removeFromBucket(k.uvVBO, k);
        if (k.normalVBO != k.posVBO && k.normalVBO != k.uvVBO) removeFromBucket(k.normalVBO, k);
    }

    private static void addToBucket(int src, Key k) {
        if (src == 0) return;
        ObjectSet<Key> set = keysBySource.get(src);
        if (set == null) keysBySource.put(src, set = new ObjectOpenHashSet<>());
        set.add(k);
    }

    private static void removeFromBucket(int src, Key k) {
        if (src == 0) return;
        final ObjectSet<Key> set = keysBySource.get(src);
        if (set != null && set.remove(k) && set.isEmpty()) keysBySource.remove(src);
    }

    private static int getOrBuild(ImmediateExtendedAttribHandler h, VAOManager.Attrib pos, VAOManager.Attrib uv,
                                  VAOManager.Attrib normal, int first, int count, int extPrim, int mode) {
        final int nVbo = normal != null ? normal.vboId : 0;
        final long nOff = normal != null ? normal.offset : 0L;
        final int nStride = normal != null ? normal.effectiveStride() : 0;
        lookup.set(pos.vboId, pos.offset, pos.effectiveStride(), uv.vboId, uv.offset, uv.effectiveStride(),
            nVbo, nOff, nStride, first, count, mode);
        final Integer hit = cache.get(lookup);
        if (hit != null) return hit;

        if (cache.size() >= MAX_CACHE) invalidateAll();

        final int extVbo = buildExtVbo(h, pos, uv, normal, first, count, extPrim);
        if (extVbo != 0) {
            final Key stored = lookup.copy();
            cache.put(stored, extVbo);
            indexKey(stored);
        }
        return extVbo;
    }

    private static int buildExtVbo(ImmediateExtendedAttribHandler h, VAOManager.Attrib pos, VAOManager.Attrib uv,
                                   VAOManager.Attrib normal, int first, int count, int extPrim) {
        final int posStride = pos.effectiveStride();
        final int uvStride = uv.effectiveStride();
        final boolean smooth = normal != null;
        final int normalStride = smooth ? normal.effectiveStride() : 0;
        final int srcStride = smooth ? SRC_STRIDE_SMOOTH : SRC_STRIDE_FLAT;
        final int srcNormalOffset = smooth ? SRC_NORMAL_OFFSET : -1;

        final ByteBuffer posBuf = readRange(pos, first, count, posStride);
        final ByteBuffer uvBuf = readRange(uv, first, count, uvStride);
        final ByteBuffer normalBuf = smooth ? readRange(normal, first, count, normalStride) : null;

        final ByteBuffer src = memAlloc(count * srcStride).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            final int pb = i * posStride, ub = i * uvStride, d = i * srcStride;
            src.putFloat(d, pos.readComponent(posBuf, pb, 0));
            src.putFloat(d + 4, pos.readComponent(posBuf, pb, 1));
            src.putFloat(d + 8, pos.size >= 3 ? pos.readComponent(posBuf, pb, 2) : 0f);
            src.putFloat(d + SRC_TEX_OFFSET, uv.readComponent(uvBuf, ub, 0));
            src.putFloat(d + SRC_TEX_OFFSET + 4, uv.readComponent(uvBuf, ub, 1));
            if (smooth) {
                final int nb = i * normalStride;
                src.putInt(d + SRC_NORMAL_OFFSET, NormI8.pack(
                    normal.readComponent(normalBuf, nb, 0),
                    normal.readComponent(normalBuf, nb, 1),
                    normal.readComponent(normalBuf, nb, 2)));
            }
        }

        final int extStride = ImmediateExtendedAttribHandler.EXT_STRIDE;
        final ByteBuffer ext = memCalloc(count, extStride);
        h.buildPacked(memAddress0(src), srcStride, 0, SRC_TEX_OFFSET, srcNormalOffset, count, extPrim, memAddress0(ext), extStride);

        final int extVbo = GLStateManager.glGenBuffers();
        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, extVbo);
        GLStateManager.glBufferData(GL15.GL_ARRAY_BUFFER, (long) (first + count) * extStride, GL15.GL_STATIC_DRAW);
        ext.position(0).limit(count * extStride);
        GLStateManager.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) first * extStride, ext);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);

        memFree(posBuf);
        memFree(uvBuf);
        if (normalBuf != null) memFree(normalBuf);
        memFree(src);
        memFree(ext);
        return extVbo;
    }

    private static ByteBuffer readRange(VAOManager.Attrib a, int first, int count, int stride) {
        final int elementBytes = a.size * a.typeSizeBytes();
        final int bytes = (count - 1) * stride + elementBytes;
        final ByteBuffer dst = memAlloc(bytes).order(ByteOrder.nativeOrder());
        dst.position(0).limit(bytes);
        final int savedVBO = GLStateManager.getBoundVBO();
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, a.vboId);
        RENDER_BACKEND.getBufferSubData(GL15.GL_ARRAY_BUFFER, a.offset + (long) first * stride, dst);
        GLStateManager.glBindBuffer(GL15.GL_ARRAY_BUFFER, savedVBO);
        dst.position(0).limit(bytes);
        return dst;
    }

    private static void invalidateAll() {
        if (cache.isEmpty()) return;
        final IntArrayList toFree = new IntArrayList(cache.values());
        cache.clear();
        keysBySource.clear();
        freeExtVbos(toFree);
    }

    private static void freeExtVbos(IntArrayList vbos) {
        if (vbos == null || vbos.isEmpty()) return;
        purging = true;
        try {
            for (int i = 0; i < vbos.size(); i++) {
                GLStateManager.glDeleteBuffers(vbos.getInt(i));
            }
        } finally {
            purging = false;
        }
    }

    private static final class EboKey {
        int ebo, count, type;
        long offset;

        void set(int ebo, long offset, int count, int type) {
            this.ebo = ebo;
            this.offset = offset;
            this.count = count;
            this.type = type;
        }

        EboKey copy() {
            final EboKey k = new EboKey();
            k.set(ebo, offset, count, type);
            return k;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EboKey k)) return false;
            return ebo == k.ebo && count == k.count && type == k.type && offset == k.offset;
        }

        @Override
        public int hashCode() {
            int h = ebo;
            h = h * 31 + count;
            h = h * 31 + type;
            h = h * 31 + Long.hashCode(offset);
            return h;
        }
    }

    private static final class Key {
        int posVBO, uvVBO, normalVBO, posStride, uvStride, normalStride, first, count, mode;
        long posOffset, uvOffset, normalOffset;

        void set(int posVBO, long posOffset, int posStride, int uvVBO, long uvOffset, int uvStride,
                 int normalVBO, long normalOffset, int normalStride, int first, int count, int mode) {
            this.posVBO = posVBO; this.posOffset = posOffset; this.posStride = posStride;
            this.uvVBO = uvVBO; this.uvOffset = uvOffset; this.uvStride = uvStride;
            this.normalVBO = normalVBO; this.normalOffset = normalOffset; this.normalStride = normalStride;
            this.first = first; this.count = count; this.mode = mode;
        }

        Key copy() {
            final Key k = new Key();
            k.set(posVBO, posOffset, posStride, uvVBO, uvOffset, uvStride,
                normalVBO, normalOffset, normalStride, first, count, mode);
            return k;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return posVBO == k.posVBO && uvVBO == k.uvVBO && normalVBO == k.normalVBO
                && posStride == k.posStride && uvStride == k.uvStride && normalStride == k.normalStride
                && first == k.first && count == k.count && mode == k.mode
                && posOffset == k.posOffset && uvOffset == k.uvOffset && normalOffset == k.normalOffset;
        }

        @Override
        public int hashCode() {
            int h = posVBO;
            h = h * 31 + uvVBO;
            h = h * 31 + normalVBO;
            h = h * 31 + posStride;
            h = h * 31 + uvStride;
            h = h * 31 + normalStride;
            h = h * 31 + first;
            h = h * 31 + count;
            h = h * 31 + mode;
            h = h * 31 + Long.hashCode(posOffset);
            h = h * 31 + Long.hashCode(uvOffset);
            h = h * 31 + Long.hashCode(normalOffset);
            return h;
        }
    }
}
